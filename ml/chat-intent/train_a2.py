"""A2: embedding Gemini (cache từ embed.py) + bộ phân loại nhỏ.

Với mỗi model embedding:
  (a) LogisticRegression đa lớp, chọn C trên val;
  (b) MLPClassifier 1 lớp ẩn (ReLU, early_stopping, seed cố định), chọn kích thước lớp ẩn + alpha trên val;
  (c) A1+A2: p = (1 - w) * p_A1 + w * p_A2, chọn w trên val (A1 đọc từ model/intent-model.json).
Tiêu chí chọn giống A1: recall EMERGENCY cao nhất, rồi macro-F1 (12 nhãn).

Đo độ trễ online thật (embedContent ~30 lần/model + thời gian phân loại thuần Python), xuất mô hình A2 tốt
nhất ra model/intent-model-a2.json + model/parity-a2.json, và ghi results/a2.md (CHỈ val; không dùng test).

    python train_a2.py                 # gồm đo độ trễ (gọi API ~30 lần/model)
    python train_a2.py --no-latency    # không gọi API (dùng số đo cũ trong model/training-log-a2.json nếu có)
"""
from __future__ import annotations

import argparse
import json
import random
import statistics
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.neural_network import MLPClassifier

import embed
import rules
from common import (DATA_DIR, MODEL_DIR, RESULTS_DIR, format_report, load_model, predict_a2_from_json,
                    predict_from_json, read_split, summarize)
from rules import ALL_LABELS
from train import choose_threshold, hybrid, write_json

LOGREG_C = [0.1, 0.3, 1.0, 3.0, 10.0, 30.0, 100.0]
MLP_HIDDEN = [64, 128, 256]
MLP_ALPHA = [1e-4, 1e-3, 1e-2, 1e-1]
BLEND_W = [round(0.1 * i, 1) for i in range(11)]  # trọng số của A2
N_LATENCY = 30
N_PARITY = 20
PARITY_TOL = 1e-4


# ------------------------------------------------------------------------------------------ models

def fit_logreg(X, y, C: float, seed: int = 42):
    clf = LogisticRegression(C=C, class_weight="balanced", solver="lbfgs", max_iter=5000, random_state=seed)
    return clf.fit(X, y)


def fit_mlp(X, y, hidden: int, alpha: float, seed: int = 42):
    clf = MLPClassifier(hidden_layer_sizes=(hidden,), activation="relu", alpha=alpha, solver="adam",
                        learning_rate_init=1e-3, max_iter=1000, early_stopping=True, validation_fraction=0.1,
                        n_iter_no_change=20, random_state=seed)
    return clf.fit(X, y)


def score(gold, pred) -> dict:
    m = summarize(gold, pred, ALL_LABELS)
    e = m["emergency"]
    return {"accuracy": m["accuracy"], "macro_f1": m["macro_f1"], "em_recall": e["recall"],
            "em_precision": e["precision"], "em_fp": e["false_alarms"], "em_tp": e["tp"], "em_fn": e["fn"]}


def select(rows: list[dict], tie_key) -> dict:
    """recall EMERGENCY cao nhất → macro-F1 (4 chữ số) cao nhất → tie_key nhỏ nhất."""
    best_rec = max(r["em_recall"] for r in rows)
    cands = [r for r in rows if r["em_recall"] >= best_rec - 1e-9]
    return sorted(cands, key=lambda r: (-round(r["macro_f1"], 4), tie_key(r)))[0]


def proba_in(clf, X, labels) -> np.ndarray:
    """Xác suất sắp theo `labels` (đề phòng classes_ khác thứ tự)."""
    P = clf.predict_proba(X)
    idx = [list(clf.classes_).index(lab) for lab in labels]
    return P[:, idx]


def argmax_labels(P, labels) -> list[str]:
    return [labels[i] for i in np.asarray(P).argmax(1)]


# ------------------------------------------------------------------------------------------ export

def _r(x, nd=6):
    return round(float(x), nd)


def export_a2(clf, emb_model: str, threshold: float, extra: dict | None = None) -> dict:
    out = {
        "version": 1,
        "trained_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "embedding": {"provider": "gemini", "model": emb_model, "endpoint": "embedContent",
                      "task_type": embed.TASK_TYPE, "output_dimensionality": embed.DIM, "normalize": "l2"},
        "labels": [str(c) for c in clf.classes_],
        "threshold": float(threshold),
    }
    if isinstance(clf, LogisticRegression):
        out["kind"] = "embedding-logreg"
        out["coef"] = [[_r(x) for x in row] for row in clf.coef_]
        out["intercept"] = [_r(x) for x in clf.intercept_]
    else:
        out["kind"] = "embedding-mlp"
        acts = [clf.activation] * (len(clf.coefs_) - 1) + ["softmax"]
        out["layers"] = [{"weights": [[_r(x) for x in row] for row in W.T], "bias": [_r(b) for b in B],
                          "activation": a} for W, B, a in zip(clf.coefs_, clf.intercepts_, acts)]
    if extra:
        out.update(extra)
    return out


def build_parity(model_json: dict, clf, texts: list[str], X: np.ndarray, seed: int = 42):
    rng = random.Random(seed)
    idx = list(range(len(texts)))
    rng.shuffle(idx)
    idx = idx[:N_PARITY]
    sk = proba_in(clf, X[idx], model_json["labels"])
    out, max_diff = [], 0.0
    for j, i in enumerate(idx):
        vec = [float(np.float32(v)) for v in X[i]]  # đúng giá trị float32 trong cache
        label, _, probs = predict_a2_from_json(model_json, vec)
        diff = float(np.max(np.abs(np.array(probs) - sk[j])))
        max_diff = max(max_diff, diff)
        assert diff < PARITY_TOL, f"JSON A2 lệch sklearn {diff:.2e} ở câu {texts[i]!r}"
        out.append({"text": texts[i], "label": label, "probs": [round(p, 10) for p in probs], "embedding": vec})
    return out, max_diff


# ----------------------------------------------------------------------------------------- latency

def measure_latency(emb_model: str, texts: list[str], key: str) -> dict:
    """Gọi embedContent tuần tự; hết quota giữa chừng thì vẫn trả các lần đo đã có (None nếu chưa có lần nào)."""
    ms = []
    for i, t in enumerate(texts):
        t0 = time.perf_counter()
        try:
            embed.embed_one(t, emb_model, key)
        except embed.ApiError as e:
            print(f"Dừng đo độ trễ {emb_model} sau {len(ms)} lần: {e}")
            break
        ms.append((time.perf_counter() - t0) * 1000)
        if i + 1 < len(texts):
            time.sleep(embed.PAUSE_S)
    if not ms:
        return None
    ms.sort()
    p95 = ms[min(len(ms) - 1, int(round(0.95 * (len(ms) - 1))))]
    return {"n": len(ms), "median_ms": statistics.median(ms), "p95_ms": p95, "min_ms": ms[0], "max_ms": ms[-1]}


def classify_ms(model_json: dict, X: np.ndarray, reps: int = 3) -> float:
    vecs = [list(map(float, x)) for x in X]
    t0 = time.perf_counter()
    for _ in range(reps):
        for v in vecs:
            predict_a2_from_json(model_json, v)
    return (time.perf_counter() - t0) * 1000 / (reps * len(vecs))


# -------------------------------------------------------------------------------------------- main

def row_md(name: str, s: dict, lat: str) -> str:
    return (f"| {name} | {s['accuracy']:.4f} | {s['macro_f1']:.4f} | {s['em_precision']:.4f} | "
            f"{s['em_recall']:.4f} ({s['em_tp']}/{s['em_tp'] + s['em_fn']}) | {s['em_fp']} | {lat} |")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=Path, default=DATA_DIR)
    ap.add_argument("--out", type=Path, default=MODEL_DIR)
    ap.add_argument("--a1", type=Path, default=MODEL_DIR / "intent-model.json")
    ap.add_argument("--report", type=Path, default=RESULTS_DIR / "a2.md")
    ap.add_argument("--models", nargs="+", default=embed.MODELS)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--no-latency", action="store_true")
    args = ap.parse_args()

    tr = read_split(args.data / "train.csv")
    va = read_split(args.data / "val.csv")
    ytr, yva, tva = tr["label"].tolist(), va["label"].tolist(), va["text"].tolist()
    emb_dir = args.data / "embeddings"
    labels = ALL_LABELS
    assert sorted(set(ytr)) == labels, "train phải có đủ 12 nhãn"

    # --- baseline: luật, A1, A1-hybrid (rules.py + train.hybrid hiện hành)
    a1 = load_model(args.a1)
    assert a1["labels"] == labels
    t0 = time.perf_counter()
    rule_pred = [rules.classify(t) for t in tva]
    rule_ms = (time.perf_counter() - t0) * 1000 / len(tva)
    t0 = time.perf_counter()
    a1_out = [predict_from_json(a1, t) for t in tva]
    a1_ms = (time.perf_counter() - t0) * 1000 / len(tva)
    P_a1 = np.array([o[2] for o in a1_out])
    a1_pred = [o[0] for o in a1_out]
    a1_hyb = [hybrid(r, m, c, a1["threshold"]) for r, (m, c, _) in zip(rule_pred, a1_out)]
    results = [("rule (luật)", score(yva, rule_pred), f"{rule_ms:.3f} ms"),
               ("A1 (TF-IDF+logreg)", score(yva, a1_pred), f"{a1_ms:.3f} ms"),
               (f"A1-hybrid (t={a1['threshold']})", score(yva, a1_hyb), f"{rule_ms + a1_ms:.3f} ms")]

    log = {"models": {}, "train_size": len(tr), "val_size": len(va)}
    old_log_path = args.out / "training-log-a2.json"
    old_log = json.loads(old_log_path.read_text(encoding="utf-8")) if old_log_path.exists() else {}
    key = None if args.no_latency else embed.load_api_key()
    candidates = []  # (score-row, clf, emb_model, P_val)

    for emb_model in args.models:
        Xtr = embed.load_embeddings(emb_model, "train", tr["text"].tolist(), emb_dir)
        Xva = embed.load_embeddings(emb_model, "val", tva, emb_dir)
        print(f"\n=== {emb_model}: train {Xtr.shape}, val {Xva.shape}")
        mlog = {"logreg_grid": [], "mlp_grid": []}

        print(f"{'C':>7} {'acc':>7} {'macroF1':>8} {'EM-rec':>7} {'EM-FP':>6}")
        for C in LOGREG_C:
            s = score(yva, fit_logreg(Xtr, ytr, C, args.seed).predict(Xva))
            s["C"] = C
            mlog["logreg_grid"].append(s)
            print(f"{C:>7} {s['accuracy']:.4f} {s['macro_f1']:>8.4f} {s['em_recall']:>7.4f} {s['em_fp']:>6}")
        best_lr = select(mlog["logreg_grid"], lambda r: r["C"])

        print(f"{'hidden':>7} {'alpha':>7} {'acc':>7} {'macroF1':>8} {'EM-rec':>7} {'EM-FP':>6} {'iter':>5}")
        for h in MLP_HIDDEN:
            for a in MLP_ALPHA:
                clf = fit_mlp(Xtr, ytr, h, a, args.seed)
                s = score(yva, clf.predict(Xva))
                s.update(hidden=h, alpha=a, n_iter=int(clf.n_iter_))
                mlog["mlp_grid"].append(s)
                print(f"{h:>7} {a:>7} {s['accuracy']:.4f} {s['macro_f1']:>8.4f} {s['em_recall']:>7.4f} "
                      f"{s['em_fp']:>6} {clf.n_iter_:>5}")
        best_mlp = select(mlog["mlp_grid"], lambda r: (r["hidden"], -r["alpha"]))  # nhỏ & điều chuẩn mạnh hơn
        print(f"Chọn logreg C={best_lr['C']}; MLP hidden={best_mlp['hidden']} alpha={best_mlp['alpha']}")

        lr = fit_logreg(Xtr, ytr, best_lr["C"], args.seed)
        mlp = fit_mlp(Xtr, ytr, best_mlp["hidden"], best_mlp["alpha"], args.seed)
        mlog["selected_logreg"], mlog["selected_mlp"] = best_lr, best_mlp

        a2_best = None
        for kind, clf, desc in (("logreg", lr, f"C={best_lr['C']}"),
                                ("mlp", mlp, f"h={best_mlp['hidden']}, α={best_mlp['alpha']}")):
            P = proba_in(clf, Xva, labels)
            s = score(yva, argmax_labels(P, labels))
            candidates.append({"score": s, "clf": clf, "emb_model": emb_model, "kind": kind, "P": P, "desc": desc,
                               "Xva": Xva})
            if a2_best is None or select([a2_best["score"], s], lambda r: 0) is s:
                a2_best = candidates[-1]

        # (c) A1 + A2 (A2 = bản tốt hơn trong logreg/MLP của model này)
        blend = []
        for w in BLEND_W:
            P = (1 - w) * P_a1 + w * a2_best["P"]
            s = score(yva, argmax_labels(P, labels))
            s["w_a2"] = w
            blend.append(s)
        best_b = select(blend, lambda r: abs(r["w_a2"] - 0.5))  # hoà → trộn cân bằng
        mlog["blend_grid"], mlog["selected_blend"] = blend, {**best_b, "a2_kind": a2_best["kind"]}
        print("w_a2: " + "  ".join(f"{b['w_a2']}:{b['macro_f1']:.4f}/{b['em_recall']:.2f}" for b in blend))
        P_bl = (1 - best_b["w_a2"]) * P_a1 + best_b["w_a2"] * a2_best["P"]
        bl_pred = argmax_labels(P_bl, labels)
        bl_conf = P_bl.max(1).tolist()
        thr_b, _ = choose_threshold(yva, bl_pred, bl_conf, rule_pred, labels)
        bl_hyb = [hybrid(r, m, c, thr_b) for r, m, c in zip(rule_pred, bl_pred, bl_conf)]
        mlog["blend_threshold"] = thr_b

        # độ trễ
        lat = None
        if key:
            try:
                lat = measure_latency(emb_model, tva[:N_LATENCY], key)
            except embed.ApiError as e:
                print(f"Không đo được độ trễ {emb_model}: {e}")
        if lat is None:
            lat = old_log.get("models", {}).get(emb_model, {}).get("latency")
        mlog["latency"] = lat
        tmp_lr, tmp_mlp = export_a2(lr, emb_model, 0.5), export_a2(mlp, emb_model, 0.5)
        mlog["classify_ms"] = {"logreg": classify_ms(tmp_lr, Xva), "mlp": classify_ms(tmp_mlp, Xva)}
        lat_s = (lambda c: f"{lat['median_ms']:.0f} ms (p95 {lat['p95_ms']:.0f}) + {c:.2f} ms") if lat else \
            (lambda c: f"API chưa đo + {c:.2f} ms")
        results += [
            (f"A2-logreg [{emb_model}] ({candidates[-2]['desc']})", candidates[-2]["score"],
             lat_s(mlog["classify_ms"]["logreg"])),
            (f"A2-MLP [{emb_model}] ({candidates[-1]['desc']})", candidates[-1]["score"],
             lat_s(mlog["classify_ms"]["mlp"])),
            (f"A1+A2 [{emb_model}, {a2_best['kind']}, w_A2={best_b['w_a2']}]", score(yva, bl_pred),
             lat_s(a1_ms + mlog["classify_ms"][a2_best["kind"]])),
            (f"A1+A2-hybrid [{emb_model}] (t={thr_b})", score(yva, bl_hyb),
             lat_s(a1_ms + rule_ms + mlog["classify_ms"][a2_best["kind"]])),
        ]
        log["models"][emb_model] = mlog

    # --- mô hình A2 tốt nhất (thuần A2) → JSON
    best = None
    for c in candidates:
        if best is None or select([best["score"], c["score"]], lambda r: 0) is c["score"]:
            best = c
    pred = argmax_labels(best["P"], labels)
    conf = best["P"].max(1).tolist()
    thr, ttable = choose_threshold(yva, pred, conf, rule_pred, labels)
    a2_hyb = [hybrid(r, m, c, thr) for r, m, c in zip(rule_pred, pred, conf)]
    s_hyb = score(yva, a2_hyb)
    lat = log["models"][best["emb_model"]]["latency"]
    cms = log["models"][best["emb_model"]]["classify_ms"][best["kind"]]
    results.append((f"A2-hybrid (tốt nhất: {best['kind']} [{best['emb_model']}], t={thr})", s_hyb,
                    (f"{lat['median_ms']:.0f} ms (p95 {lat['p95_ms']:.0f}) + {cms + rule_ms:.2f} ms" if lat
                     else f"API chưa đo + {cms + rule_ms:.2f} ms")))
    sel_blend = log["models"][best["emb_model"]]["selected_blend"]
    model_json = export_a2(best["clf"], best["emb_model"], thr, extra={
        "a1_blend": {"w_a2": sel_blend["w_a2"], "a2_kind": sel_blend["a2_kind"],
                     "note": "tham khảo: p = (1-w_a2)*p_A1 + w_a2*p_A2 (chỉ áp dụng nếu cùng loại A2)"}})
    path = args.out / "intent-model-a2.json"
    write_json(model_json, path)
    model_json = load_model(path)
    parity, max_diff = build_parity(model_json, best["clf"], tva, best["Xva"], args.seed)
    write_json(parity, args.out / "parity-a2.json", compact=False)
    print(f"\nĐã ghi {path} ({model_json['kind']}, {best['emb_model']}, {path.stat().st_size / 1024:.0f} KB), "
          f"parity-a2.json ({len(parity)} câu, lệch tối đa {max_diff:.2e})")
    log.update(best={"emb_model": best["emb_model"], "kind": best["kind"], "desc": best["desc"],
                     "score": best["score"]}, threshold=thr, threshold_table=ttable, parity_max_diff=max_diff,
               api_calls=dict(embed.CALLS))
    write_json(log, args.out / "training-log-a2.json", compact=False)

    # --- báo cáo
    lines = ["# A2 — embedding Gemini + bộ phân loại nhỏ (VAL)", "",
             f"Dữ liệu: train {len(tr)}, val {len(va)} (test chỉ được embed sẵn, **không** dùng để chọn hay báo cáo). "
             f"Embedding: `embedContent`, taskType `{embed.TASK_TYPE}`, outputDimensionality {embed.DIM}, chuẩn hoá L2.",
             "Siêu tham số chọn trên val (recall EMERGENCY → macro-F1) rồi báo cáo cũng trên val ⇒ số liệu hơi lạc quan "
             "cho mọi hệ (giống A1).", "",
             "| hệ | acc (12) | macro-F1 (12) | EMERG precision | EMERG recall | EMERG báo nhầm | độ trễ/câu |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    lines += [row_md(n, s, l) for n, s, l in results]
    lines += ["", "Độ trễ A2 = thời gian gọi `embedContent` thật (trung vị, p95 trên "
              f"{N_LATENCY} lần, mạng hiện tại) + thời gian phân loại thuần Python từ JSON.", ""]
    for emb_model, mlog in log["models"].items():
        lat = mlog["latency"]
        lines += [f"## {emb_model}", ""]
        if lat:
            lines.append(f"- embedContent: trung vị {lat['median_ms']:.0f} ms, p95 {lat['p95_ms']:.0f} ms, "
                         f"min {lat['min_ms']:.0f}, max {lat['max_ms']:.0f} (n={lat['n']})")
        lines.append(f"- phân loại: logreg {mlog['classify_ms']['logreg']:.3f} ms, MLP {mlog['classify_ms']['mlp']:.3f} ms")
        lines += ["", "Lưới logreg: C | acc | macro-F1 | EM recall | EM FP", "", "```"]
        lines += [f"{g['C']:>6} | {g['accuracy']:.4f} | {g['macro_f1']:.4f} | {g['em_recall']:.4f} | {g['em_fp']}"
                  for g in mlog["logreg_grid"]]
        lines += ["```", "", "Lưới MLP: hidden | alpha | acc | macro-F1 | EM recall | EM FP | số epoch", "", "```"]
        lines += [f"{g['hidden']:>4} | {g['alpha']:<7} | {g['accuracy']:.4f} | {g['macro_f1']:.4f} | "
                  f"{g['em_recall']:.4f} | {g['em_fp']} | {g['n_iter']}" for g in mlog["mlp_grid"]]
        lines += ["```", "", f"Trộn A1+A2 ({mlog['selected_blend']['a2_kind']}): w_A2 | acc | macro-F1 | EM recall | EM FP",
                  "", "```"]
        lines += [f"{g['w_a2']:.1f} | {g['accuracy']:.4f} | {g['macro_f1']:.4f} | {g['em_recall']:.4f} | {g['em_fp']}"
                  for g in mlog["blend_grid"]]
        lines += ["```", ""]
    lines += ["## Mô hình A2 xuất ra", "",
              f"`model/intent-model-a2.json`: {model_json['kind']} trên `{best['emb_model']}` ({best['desc']}), "
              f"threshold {thr}; `model/parity-a2.json`: {len(parity)} câu, lệch JSON↔sklearn tối đa {max_diff:.1e}.", "",
              format_report(summarize(yva, pred, labels), f"A2 tốt nhất chi tiết — val ({best['kind']} [{best['emb_model']}])")]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    marker = "<!-- NHẬN XÉT -->"
    old = args.report.read_text(encoding="utf-8") if args.report.exists() else ""
    notes = old[old.index(marker):] if marker in old else marker + "\n\n## Nhận xét\n\n(viết tay)\n"
    args.report.write_text("\n".join(lines) + "\n" + notes, encoding="utf-8")
    print(f"Đã ghi {args.report}")
    print("\n".join(row_md(n, s, l) for n, s, l in results))
    print(f"Gọi HTTP lần này: {embed.CALLS}")


if __name__ == "__main__":
    main()
