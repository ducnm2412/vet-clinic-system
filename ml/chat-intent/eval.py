"""Đánh giá mô hình đã xuất (đọc model/intent-model.json, suy luận thuần Python giống Java).

So sánh rule / ml / hybrid. hybrid: luật ra EMERGENCY/OFF_TOPIC → giữ nhãn luật; ngược lại ML conf ≥ threshold → nhãn ML;
ngược lại → nhãn luật.

Nếu có `intent-model-a2.json` cạnh mô hình A1 và cache embedding (embed.py) của split, đánh giá thêm A2 và chính sách
A1+A2 giống `CompositeIntentClassifier` (xem `composite_a2`), ghi `final.md` cạnh file `--out`.

    python eval.py                  # val
    python eval.py --test           # val + test (chỉ chạy một lần ở bước cuối)
    python eval.py --test --latency 20   # + đo độ trễ embedContent thật 20 lần (tốn 20 yêu cầu hạn mức)
"""
from __future__ import annotations

import argparse
import json
import time
from collections import Counter
from pathlib import Path

import numpy as np

import rules
from common import (DATA_DIR, MODEL_DIR, RESULTS_DIR, format_report, load_model, predict_a2_from_json,
                    predict_from_json, read_split, summarize)
from rules import ALL_LABELS, RULE_LABELS, to_rule_space
from textnorm import normalize
from train import hybrid

# Giống services/chat-service/src/main/resources/application.yml (app.chat.intent.a2.*)
A2_TRIGGER_BELOW = 0.6
A2_MIN_WORDS = 3


def a2_accepts(text: str, min_words: int = A2_MIN_WORDS) -> bool:
    """Giống EmbeddingIntentClassifier.accepts: câu chuẩn hoá không rỗng và đủ số từ thì mới gọi A2."""
    n = normalize(text)
    return bool(n) and len(n.split(" ")) >= min_words


def composite_a2(rule_label: str, a1_label: str, a1_conf: float, a1_threshold: float,
                 a2_pred: tuple[str, float] | None, a2_threshold: float,
                 trigger_below: float = A2_TRIGGER_BELOW) -> tuple[str, str]:
    """Chính sách CompositeIntentClassifier (chế độ hybrid, có A2). Trả (nhãn, nguồn).

    1. luật EMERGENCY/OFF_TOPIC → nhãn luật;
    2. A1 conf < trigger_below và A2 có kết quả (None = không gọi: câu ngắn/lỗi) với conf ≥ a2_threshold → nhãn A2;
    3. A1 conf ≥ a1_threshold → nhãn A1; còn lại → nhãn luật.
    """
    if rule_label in ("EMERGENCY", "OFF_TOPIC"):
        return rule_label, "rule-override"
    if a1_conf < trigger_below and a2_pred is not None and a2_pred[1] >= a2_threshold:
        return a2_pred[0], "a2"
    if a1_conf >= a1_threshold:
        return a1_label, "a1"
    return rule_label, "rule"


def timed(fn, texts):
    t0 = time.perf_counter()
    out = [fn(t) for t in texts]
    ms = (time.perf_counter() - t0) * 1000 / max(1, len(texts))
    return out, ms


def evaluate_split(model: dict, name: str, df) -> tuple[str, list[str]]:
    texts, gold = df["text"].tolist(), df["label"].tolist()
    thr = model["threshold"]
    rule_pred, rule_ms = timed(rules.classify, texts)
    ml_out, ml_ms = timed(lambda t: predict_from_json(model, t), texts)
    ml_pred = [o[0] for o in ml_out]
    conf = [o[1] for o in ml_out]
    hyb_pred = [hybrid(r, m, c, thr) for r, m, c in zip(rule_pred, ml_pred, conf)]
    hyb_ms = rule_ms + ml_ms  # hybrid chạy cả hai

    rows = []
    metrics = {}
    for sysname, pred, ms in (("rule", rule_pred, rule_ms), ("ml", ml_pred, ml_ms), ("hybrid", hyb_pred, hyb_ms)):
        m12 = summarize(gold, pred, ALL_LABELS)
        m7 = summarize([to_rule_space(g) for g in gold], [to_rule_space(p) for p in pred], RULE_LABELS)
        metrics[sysname] = m12
        e = m12["emergency"]
        rows.append(f"| {sysname} | {m12['accuracy']:.4f} | {m12['macro_f1']:.4f} | {m7['accuracy']:.4f} | "
                    f"{m7['macro_f1']:.4f} | {e['precision']:.4f} | {e['recall']:.4f} ({e['tp']}/{e['tp'] + e['fn']}) | "
                    f"{e['false_alarms']} | {ms:.3f} |")
    table = "\n".join([
        f"## {name} (n = {len(df)}, threshold = {thr})", "",
        "| hệ | acc (12) | macro-F1 (12) | acc (7) | macro-F1 (7) | EMERG precision | EMERG recall | EMERG báo nhầm | ms/câu |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|", *rows, "",
        "(7) = gộp PRICE/SERVICE_INFO/PRODUCT/BOOKING/CARE_ADVICE → OTHER ở cả nhãn thật lẫn dự đoán.",
        f"Độ phủ ML (conf ≥ threshold): {sum(c >= thr for c in conf) / max(1, len(conf)):.3f}", ""])

    wrong = [(t, g, h, r, m, c) for t, g, h, r, m, c in zip(texts, gold, hyb_pred, rule_pred, ml_pred, conf) if g != h]
    wl = [f"Hybrid sai {len(wrong)}/{len(df)} câu (tối đa 20):", "",
          "| câu | thật | hybrid | luật | ML (conf) |", "|---|---|---|---|---|"]
    for t, g, h, r, m, c in wrong[:20]:
        wl.append(f"| {t.replace('|', '/')} | {g} | {h} | {r} | {m} ({c:.2f}) |")
    detail = format_report(metrics["ml"], f"ML chi tiết — {name}") + "\n" + \
        format_report(metrics["hybrid"], f"Hybrid chi tiết — {name}")
    section = table + "\n" + "\n".join(wl) + "\n\n" + detail
    return section, [table]


# ------------------------------------------------------------------------- A1 + A2 (báo cáo cuối)

FINAL_MARKER = "<!-- NHẬN XÉT -->"
COMP_NAME = "A1+A2 (chính sách CompositeIntentClassifier)"


def per_sentence_ms(fn, items, reps: int = 5) -> float:
    t0 = time.perf_counter()
    for _ in range(reps):
        for x in items:
            fn(x)
    return (time.perf_counter() - t0) * 1000 / max(1, reps * len(items))


def bootstrap_macro_f1(gold, preds: dict, n: int = 1000, seed: int = 42) -> dict:
    """Mẫu bootstrap (theo câu, cùng chỉ số cho mọi hệ) của macro-F1 12 nhãn → {hệ: mảng n giá trị}."""
    rng = np.random.default_rng(seed)
    gold = np.asarray(gold)
    arr = {k: np.asarray(v) for k, v in preds.items()}
    out = {k: [] for k in preds}
    for _ in range(n):
        idx = rng.integers(0, len(gold), len(gold))
        for k, v in arr.items():
            out[k].append(summarize(gold[idx].tolist(), v[idx].tolist(), ALL_LABELS)["macro_f1"])
    return {k: np.asarray(v) for k, v in out.items()}


def run_systems(a1: dict, a2: dict, split: str, df, emb_dir: Path, extra: list | None = None) -> dict:
    """Dự đoán của mọi hệ trên một split. A2 dùng embedding trong cache (cùng tham số Java gửi tới API)."""
    import embed
    texts, gold = df["text"].tolist(), df["label"].tolist()
    rule_pred = [rules.classify(t) for t in texts]
    a1_out = [predict_from_json(a1, t) for t in texts]
    X = embed.load_embeddings(a2["embedding"]["model"], split, texts, emb_dir)
    # Câu chuẩn hoá rỗng → OTHER, không gọi API (MODEL_FORMAT mục A2).
    a2_out = [("OTHER", 1.0) if not normalize(t) else predict_a2_from_json(a2, x)[:2] for t, x in zip(texts, X)]
    a1_thr, a2_thr = a1["threshold"], a2["threshold"]
    comp = [composite_a2(r, m, c, a1_thr, (a2o if a2_accepts(t) else None), a2_thr)
            for t, r, (m, c, _), a2o in zip(texts, rule_pred, a1_out, a2_out)]
    called = [r not in ("EMERGENCY", "OFF_TOPIC") and c < A2_TRIGGER_BELOW and a2_accepts(t)
              for t, r, (_, c, _) in zip(texts, rule_pred, a1_out)]
    systems = {
        "rule (luật)": rule_pred,
        "A1 (TF-IDF+logreg)": [o[0] for o in a1_out],
        f"A1-hybrid (t={a1_thr})": [hybrid(r, m, c, a1_thr) for r, (m, c, _) in zip(rule_pred, a1_out)],
        f"A2 [{a2['embedding']['model']}, logreg]": [o[0] for o in a2_out],
        COMP_NAME: [c[0] for c in comp],
    }
    for name, pred in (extra or []):
        systems[name] = pred
    return {"texts": texts, "gold": gold, "rule": rule_pred, "a1": a1_out, "a2": a2_out, "X": X,
            "comp": comp, "called": called, "systems": systems}


def refit_reference_a2(emb_model: str, C: float, data: Path, split: str, df) -> list | None:
    """A2 tham khảo trên model embedding khác (không xuất): logreg với C ĐÃ CHỌN TRÊN VAL (training-log-a2.json),
    khớp lại trên train (cùng seed → cùng mô hình lúc chọn). Không chỉnh gì theo test. None nếu thiếu cache."""
    import embed
    import train_a2
    emb_dir = data / "embeddings"
    tr = read_split(data / "train.csv")
    try:
        Xtr = embed.load_embeddings(emb_model, "train", tr["text"].tolist(), emb_dir)
        X = embed.load_embeddings(emb_model, split, df["text"].tolist(), emb_dir)
    except (FileNotFoundError, ValueError):
        return None
    return list(train_a2.fit_logreg(Xtr, tr["label"].tolist(), C).predict(X))


def _metrics_table(r: dict, lat_of=None) -> list[str]:
    head = "| hệ | acc | macro-F1 (12) | EMERG recall | EMERG precision | EMERG báo nhầm | OFF_TOPIC recall |"
    sep = "|---|---:|---:|---:|---:|---:|---:|"
    if lat_of:
        head, sep = head + " độ trễ/câu |", sep + "---:|"
    rows = [head, sep]
    for name, pred in r["systems"].items():
        m = summarize(r["gold"], pred, ALL_LABELS)
        e = m["emergency"]
        row = (f"| {name} | {m['accuracy']:.4f} | {m['macro_f1']:.4f} | {e['recall']:.4f} ({e['tp']}/{e['tp'] + e['fn']}) "
               f"| {e['precision']:.4f} | {e['false_alarms']} | {m['per_label']['OFF_TOPIC']['recall']:.4f} |")
        rows.append(row + (f" {lat_of(name)} |" if lat_of else ""))
    return rows


def _per_class_table(r: dict) -> list[str]:
    names = list(r["systems"])
    ms = {n: summarize(r["gold"], r["systems"][n], ALL_LABELS) for n in names}
    short = ["rule", "A1", "A1-hybrid", "A2", "A1+A2"] + [f"tham khảo {i + 1}" for i in range(len(names) - 5)]
    rows = ["| nhãn (support) | " + " | ".join(short) + " |", "|---|" + "---:|" * len(names)]
    for lab in ALL_LABELS:
        rows.append(f"| {lab} ({ms[names[0]]['per_label'][lab]['support']}) | " +
                    " | ".join(f"{ms[n]['per_label'][lab]['f1']:.3f}" for n in names) + " |")
    return rows


def final_report(a1: dict, a2: dict, a2_path: Path, data: Path, out: Path, latency_n: int):
    import embed
    emb_dir = data / "embeddings"
    log_path = a2_path.parent / "training-log-a2.json"
    ref = []
    if log_path.exists():
        log = json.loads(log_path.read_text(encoding="utf-8"))
        ref = [(m, d["selected_logreg"]["C"]) for m, d in log.get("models", {}).items()
               if m != a2["embedding"]["model"] and "selected_logreg" in d]

    res = {}
    for split in ("val", "test"):
        df = read_split(data / f"{split}.csv")
        extra = []
        for m, C in ref:
            pred = refit_reference_a2(m, C, data, split, df)
            if pred is not None:
                extra.append((f"A2 tham khảo [{m}, logreg C={C}]", pred))
        res[split] = run_systems(a1, a2, split, df, emb_dir, extra)

    t = res["test"]
    rule_ms = per_sentence_ms(rules.classify, t["texts"])
    a1_ms = per_sentence_ms(lambda x: predict_from_json(a1, x), t["texts"])
    a2_cls_ms = per_sentence_ms(lambda x: predict_a2_from_json(a2, x), list(t["X"]))
    lat = None
    if latency_n > 0:
        import train_a2
        lat = train_a2.measure_latency(a2["embedding"]["model"], res["val"]["texts"][:latency_n], embed.load_api_key())
    api_s = f"{lat['median_ms']:.0f} ms (p95 {lat['p95_ms']:.0f})" if lat else "API chưa đo"
    frac = sum(t["called"]) / len(t["called"])

    def lat_of(name: str) -> str:
        if name.startswith("rule"):
            return f"{rule_ms:.3f} ms"
        if name.startswith("A1 ("):
            return f"{a1_ms:.3f} ms"
        if name.startswith("A1-hybrid"):
            return f"{rule_ms + a1_ms:.3f} ms"
        if name.startswith("A2 ["):
            return f"{api_s} + {a2_cls_ms:.2f} ms"
        if name.startswith("A1+A2"):
            return f"{rule_ms + a1_ms:.2f} ms, {frac:.0%} tin thêm API"
        return "—"

    src = Counter(s for _, s in t["comp"])
    hyb_name = f"A1-hybrid (t={a1['threshold']})"
    a2_name = f"A2 [{a2['embedding']['model']}, logreg]"
    boot = bootstrap_macro_f1(t["gold"], {k: t["systems"][k] for k in ("rule (luật)", hyb_name, a2_name, COMP_NAME)})
    diff = boot[COMP_NAME] - boot[hyb_name]

    lines = [
        "# Đánh giá cuối trên tập TEST (bước C5)", "",
        f"Tập test: {len(t['gold'])} câu, để riêng từ lúc chia dữ liệu, **chạy đúng một lần** bằng các mô hình đã xuất "
        f"(`intent-model.json` trained_at {a1['trained_at']}, `{a2_path.name}` trained_at {a2['trained_at']}); "
        "không chỉnh siêu tham số/ngưỡng nào sau khi thấy số test. Sinh bởi `python eval.py --test`.", "",
        f"Chính sách A1+A2 giống `CompositeIntentClassifier` + `application.yml`: luật EMERGENCY/OFF_TOPIC ghi đè; "
        f"A1 conf < {A2_TRIGGER_BELOW} và câu ≥ {A2_MIN_WORDS} từ → gọi A2, A2 conf ≥ {a2['threshold']} → nhãn A2; "
        f"ngược lại A1 conf ≥ {a1['threshold']} → nhãn A1; còn lại → nhãn luật. A2 dùng embedding trong cache "
        "(`embed.py`, cùng tham số Java gửi).", "",
        "## Bảng chính — TEST", "", *_metrics_table(t, lat_of), "",
        f"Độ trễ đo bằng Python thuần (trung bình {len(t['texts'])} câu × 5 lượt, máy dev); Java (`MlIntentClassifier`) "
        "sẽ khác (thường nhanh hơn sau khi JIT khởi động). Cột A1+A2: phần cục bộ + tỷ lệ tin phải gọi thêm API.",
        (f"`embedContent` thật ({a2['embedding']['model']}, {lat['n']} lần tuần tự, mạng hiện tại): trung vị "
         f"{lat['median_ms']:.0f} ms, p95 {lat['p95_ms']:.0f} ms, min {lat['min_ms']:.0f}, max {lat['max_ms']:.0f}."
         if lat else "`embedContent`: chưa đo."), "",
        "Nguồn nhãn của A1+A2 trên test: " + ", ".join(f"{k} {v}" for k, v in sorted(src.items())) +
        f"; gọi API {sum(t['called'])}/{len(t['called'])} câu.", "",
        "Khoảng tin cậy 95% của macro-F1 (bootstrap 1000 lần theo câu, seed 42): " +
        "; ".join(f"{k}: [{np.percentile(v, 2.5):.3f}, {np.percentile(v, 97.5):.3f}]" for k, v in boot.items()) +
        f". Chênh A1+A2 − A1-hybrid: trung vị {np.median(diff):+.3f}, 95% [{np.percentile(diff, 2.5):+.3f}, "
        f"{np.percentile(diff, 97.5):+.3f}].", "",
        "### F1 từng nhãn — TEST", "", *_per_class_table(t), "",
        "### Cùng bảng trên VAL (cùng mô hình, cùng chính sách)", "", *_metrics_table(res["val"]), "",
        "Val đã dùng để chọn siêu tham số/ngưỡng nên số val lạc quan hơn; chênh val − test ước lượng mức quá khớp.", "",
    ]
    for name in (COMP_NAME, a2_name):
        lines.append(format_report(summarize(t["gold"], t["systems"][name], ALL_LABELS), f"{name} chi tiết — test"))
    wrong = [i for i, (g, (p, _)) in enumerate(zip(t["gold"], t["comp"])) if g != p]
    lines += [f"### A1+A2 sai {len(wrong)}/{len(t['gold'])} câu test", "",
              "| # | câu | thật | A1+A2 (nguồn) | luật | A1 (conf) | A2 (conf) |", "|---:|---|---|---|---|---|---|"]
    for k, i in enumerate(wrong, 1):
        p, s = t["comp"][i]
        m, c, _ = t["a1"][i]
        lines.append(f"| {k} | {t['texts'][i].replace('|', '/')} | {t['gold'][i]} | {p} ({s}) | {t['rule'][i]} | "
                     f"{m} ({c:.2f}) | {t['a2'][i][0]} ({t['a2'][i][1]:.2f}) |")
    lines.append("")

    old = out.read_text(encoding="utf-8") if out.exists() else ""
    notes = old[old.index(FINAL_MARKER):] if FINAL_MARKER in old else FINAL_MARKER + "\n\n## Nhận xét\n\n(viết tay)\n"
    out.write_text("\n".join(lines) + "\n" + notes, encoding="utf-8")
    print("\n".join(lines))
    print(f"Đã ghi {out}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--test", action="store_true", help="đánh giá thêm trên data/test.csv")
    ap.add_argument("--model", type=Path, default=MODEL_DIR / "intent-model.json")
    ap.add_argument("--data", type=Path, default=DATA_DIR)
    ap.add_argument("--out", type=Path, default=RESULTS_DIR / "a1.md")
    ap.add_argument("--a2-model", type=Path, default=None, help="mặc định: intent-model-a2.json cạnh --model")
    ap.add_argument("--latency", type=int, default=0, help="số lần gọi embedContent thật để đo độ trễ A2 (0 = không)")
    args = ap.parse_args()

    model = load_model(args.model)
    parts = [f"# A1 — TF-IDF + hồi quy logistic\n\nMô hình: `{args.model.name}` (trained_at {model['trained_at']}), "
             f"{len(model['labels'])} nhãn, khối {[b['name'] for b in model['blocks']]}, "
             f"{sum(len(b['vocab']) for b in model['blocks'])} đặc trưng, threshold {model['threshold']}.\n"]
    log_path = args.model.parent / "training-log.json"
    if log_path.exists():
        log = json.loads(log_path.read_text(encoding="utf-8"))
        s = log["selected"]
        parts.append(f"Chọn trên val: C = {s['C']}, khối char = {s['use_char']} (macro-F1 val {s['macro_f1']:.4f}, "
                     f"recall EMERGENCY {s['em_recall']:.4f}).\n")
        parts.append("Bảng chọn threshold (val, mô hình cuối): t | phủ | acc ML ≥ t | acc ML < t | acc luật < t | "
                     "macro-F1 hybrid\n\n```")
        for r in log["threshold_table"]:
            parts.append(f"{r['t']:.2f} | {r['coverage']:.3f} | {r['acc_above']:.3f} | {r['acc_below_ml']:.3f} | "
                         f"{r['acc_below_rule']:.3f} | {r['hybrid_macro_f1']:.4f}")
        parts.append("```\n")

    splits = [("val", args.data / "val.csv")]
    if args.test:
        splits.append(("test", args.data / "test.csv"))
    for name, path in splits:
        section, tables = evaluate_split(model, name, read_split(path))
        print(section)
        parts.append(section)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(parts), encoding="utf-8")
    print(f"Đã ghi {args.out}")

    a2_path = args.a2_model or args.model.parent / "intent-model-a2.json"
    if args.test and a2_path.exists():
        try:
            final_report(model, load_model(a2_path), a2_path, args.data, args.out.with_name("final.md"), args.latency)
        except (FileNotFoundError, ValueError) as e:
            print(f"Bỏ qua báo cáo A1+A2 (thiếu cache embedding?): {e}")


if __name__ == "__main__":
    main()
