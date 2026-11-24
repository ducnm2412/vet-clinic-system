"""A1: TF-IDF (khối word + khối char, analyzer tự viết trong textnorm) + LogisticRegression đa lớp.

- Chọn C và có/không khối char trên val: ưu tiên recall EMERGENCY cao nhất, rồi macro-F1, rồi C nhỏ hơn.
- Chọn threshold trên val bằng mô phỏng hybrid (xem choose_threshold).
- Xuất model/intent-model.json (đúng MODEL_FORMAT.md) + model/parity.json + model/training-log.json.

Mô hình cuối chỉ học trên train (không gộp val) để threshold chọn trên val vẫn còn ý nghĩa.
"""
from __future__ import annotations

import argparse
import json
import random
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from scipy.sparse import hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

import rules
from common import DATA_DIR, MODEL_DIR, predict_from_json, read_split, summarize
from textnorm import CHAR_NGRAM_MAX, CHAR_NGRAM_MIN, char_terms, word_terms

C_GRID = [0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0]
THRESHOLD_GRID = [round(0.20 + 0.05 * i, 2) for i in range(15)]  # 0.20 .. 0.90
CHAR_MIN_DF = 2
PARITY_TOL = 1e-3

SPECIAL_PARITY = [
    "",
    "    ",
    "!!! ??? ... ###",
    "🐶🐱❤️🔥",
    "Mèo nhà em đang co giật, sùi bọt mép",
    "meo nha em dang co giat sui bot mep",
    "MÈO NHÀ EM ĐANG CO GIẬT",
    "ĐẶT LỊCH KHÁM CHO CHÓ NGÀY MAI",
    "dat lich kham cho cho ngay mai",
    "Mèo bị nôn ra máu",  # dấu tổ hợp (NFD sẵn)
    "chào shop 👋",
    "Phòng khám ở đâu vậy???",
    "triệt sản mèo cái giá bao nhiêu",
    "cảm ơn nhiều nha 😊😊",
    "mấy giờ mở cửa vậy ad",
    "có hạt royal canin cho mèo không",
    "viết giúp mình đoạn code python",
    "0901 234 567",
    "Đ đ Đường đi",
    "chó rụng lông nhiều phải làm sao",
]


def make_vectorizer(block: str) -> TfidfVectorizer:
    analyzer = word_terms if block == "word" else char_terms
    return TfidfVectorizer(analyzer=analyzer, sublinear_tf=True, smooth_idf=True, use_idf=True, norm="l2",
                           min_df=1 if block == "word" else CHAR_MIN_DF, dtype=np.float64)


def fit(texts, labels, C: float, use_char: bool, seed: int = 42):
    vecs = [("word", make_vectorizer("word"))]
    if use_char:
        vecs.append(("char", make_vectorizer("char")))
    X = hstack([v.fit_transform(texts) for _, v in vecs]).tocsr()
    clf = LogisticRegression(C=C, class_weight="balanced", solver="lbfgs", max_iter=5000, random_state=seed)
    clf.fit(X, labels)
    return vecs, clf


def transform(vecs, texts):
    return hstack([v.transform(texts) for _, v in vecs]).tocsr()


def sk_predict(vecs, clf, texts):
    proba = clf.predict_proba(transform(vecs, texts))
    return [clf.classes_[i] for i in proba.argmax(1)], proba


def export_model(vecs, clf, threshold: float) -> dict:
    blocks, offset = [], 0
    for name, v in vecs:
        vocab = {t: int(i) for t, i in v.vocabulary_.items()}
        b = {"name": name, "offset": offset, "vocab": vocab, "idf": [float(x) for x in v.idf_]}
        if name == "char":
            b["ngram_min"], b["ngram_max"] = CHAR_NGRAM_MIN, CHAR_NGRAM_MAX
        blocks.append(b)
        offset += len(vocab)
    coef = clf.coef_
    if coef.shape[0] == 1:  # chỉ 2 lớp: sklearn lưu 1 hàng → đổi về dạng softmax 2 hàng tương đương
        coef = np.vstack([-coef[0] / 2, coef[0] / 2])
        intercept = np.array([-clf.intercept_[0] / 2, clf.intercept_[0] / 2])
    else:
        intercept = clf.intercept_
    return {
        "version": 1,
        "kind": "tfidf-logreg",
        "trained_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "labels": [str(c) for c in clf.classes_],
        "threshold": float(threshold),
        "blocks": blocks,
        "coef": [[round(float(x), 6) for x in row] for row in coef],
        "intercept": [round(float(x), 6) for x in intercept],
    }


def write_json(obj, path: Path, compact: bool = True):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        if compact:
            json.dump(obj, fh, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(obj, fh, ensure_ascii=False, indent=1)


def hybrid(rule_label: str, ml_label: str, conf: float, threshold: float) -> str:
    # Giống CompositeIntentClassifier.java: luật EMERGENCY và OFF_TOPIC ghi đè ML.
    if rule_label in ("EMERGENCY", "OFF_TOPIC"):
        return rule_label
    return ml_label if conf >= threshold else rule_label


def choose_threshold(gold, ml_pred, conf, rule_pred, labels):
    """Với mỗi ngưỡng t: độ phủ, accuracy của ML trên câu conf ≥ t và conf < t, macro-F1 hybrid.

    Chọn t có macro-F1 hybrid cao nhất; nếu nhiều t trong phạm vi 0.005 so với tốt nhất thì lấy t LỚN
    nhất (chỉ tin ML khi chắc hơn, phần còn lại về luật/Gemini). Lý do: dưới t, ML đúng ít hơn hẳn chính
    nó ở trên t và không hơn luật → giao cho luật không làm giảm (thường làm tăng) chất lượng tổng.
    """
    table = []
    for t in THRESHOLD_GRID:
        above = [i for i, c in enumerate(conf) if c >= t]
        below = [i for i, c in enumerate(conf) if c < t]
        acc_above = np.mean([ml_pred[i] == gold[i] for i in above]) if above else float("nan")
        acc_below = np.mean([ml_pred[i] == gold[i] for i in below]) if below else float("nan")
        rule_below = np.mean([rule_pred[i] == gold[i] for i in below]) if below else float("nan")
        hyb = [hybrid(r, m, c, t) for r, m, c in zip(rule_pred, ml_pred, conf)]
        hm = summarize(gold, hyb, labels)
        table.append({"t": t, "coverage": len(above) / len(conf), "acc_above": float(acc_above),
                      "acc_below_ml": float(acc_below), "acc_below_rule": float(rule_below),
                      "n_below": len(below), "hybrid_macro_f1": hm["macro_f1"], "hybrid_acc": hm["accuracy"]})
    best = max(r["hybrid_macro_f1"] for r in table)
    chosen = max(r["t"] for r in table if r["hybrid_macro_f1"] >= best - 0.005)
    return chosen, table


def build_parity(model: dict, vecs, clf, pool_texts, n_total: int = 50, seed: int = 42):
    rng = random.Random(seed)
    pool = [t for t in pool_texts if t not in SPECIAL_PARITY]
    rng.shuffle(pool)
    texts = SPECIAL_PARITY + pool[: max(0, n_total - len(SPECIAL_PARITY))]
    _, sk_proba = sk_predict(vecs, clf, texts)
    out, max_diff = [], 0.0
    for t, skp in zip(texts, sk_proba):
        label, _, probs = predict_from_json(model, t)
        diff = float(np.max(np.abs(np.array(probs) - skp)))
        max_diff = max(max_diff, diff)
        assert diff < PARITY_TOL, f"JSON lệch sklearn {diff:.2e} ở câu {t!r}"
        out.append({"text": t, "label": label, "probs": [round(p, 10) for p in probs]})
    return out, max_diff


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=Path, default=DATA_DIR)
    ap.add_argument("--out", type=Path, default=MODEL_DIR)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    tr = read_split(args.data / "train.csv")
    va = read_split(args.data / "val.csv")
    te_path = args.data / "test.csv"
    te = read_split(te_path) if te_path.exists() else tr.iloc[:0]
    labels = sorted(set(tr["label"]))
    print(f"train={len(tr)} val={len(va)} nhãn={labels}")

    gold = va["text"].tolist(), va["label"].tolist()
    grid = []
    print(f"\n{'C':>6} {'char':>5} {'acc':>7} {'macroF1':>8} {'EM-rec':>7} {'EM-FP':>6}")
    for use_char in (False, True):
        for C in C_GRID:
            vecs, clf = fit(tr["text"].tolist(), tr["label"].tolist(), C, use_char, args.seed)
            pred, _ = sk_predict(vecs, clf, gold[0])
            m = summarize(gold[1], pred, labels)
            em = m.get("emergency", {"recall": 0.0, "false_alarms": 0})
            grid.append({"C": C, "use_char": use_char, "accuracy": m["accuracy"], "macro_f1": m["macro_f1"],
                         "em_recall": em["recall"], "em_fp": em["false_alarms"]})
            print(f"{C:>6} {str(use_char):>5} {m['accuracy']:.4f} {m['macro_f1']:>8.4f} {em['recall']:>7.4f} "
                  f"{em['false_alarms']:>6}")

    best_rec = max(g["em_recall"] for g in grid)
    cands = [g for g in grid if g["em_recall"] >= best_rec - 1e-9]
    best = sorted(cands, key=lambda g: (-round(g["macro_f1"], 4), g["C"]))[0]
    print(f"\nChọn: C={best['C']} char={best['use_char']} (recall EMERGENCY={best['em_recall']:.4f}, "
          f"macro-F1={best['macro_f1']:.4f})")

    vecs, clf = fit(tr["text"].tolist(), tr["label"].tolist(), best["C"], best["use_char"], args.seed)
    ml_pred, proba = sk_predict(vecs, clf, gold[0])
    conf = proba.max(1).tolist()
    rule_pred = [rules.classify(t) for t in gold[0]]
    threshold, ttable = choose_threshold(gold[1], ml_pred, conf, rule_pred, labels)
    print(f"\n{'t':>5} {'phủ':>6} {'accML≥t':>8} {'accML<t':>8} {'accLuật<t':>10} {'n<t':>5} {'hybF1':>7}")
    for r in ttable:
        print(f"{r['t']:>5} {r['coverage']:>6.3f} {r['acc_above']:>8.3f} {r['acc_below_ml']:>8.3f} "
              f"{r['acc_below_rule']:>10.3f} {r['n_below']:>5} {r['hybrid_macro_f1']:>7.4f}")
    print(f"Chọn threshold = {threshold}")

    model = export_model(vecs, clf, threshold)
    write_json(model, args.out / "intent-model.json")
    model = json.loads((args.out / "intent-model.json").read_text(encoding="utf-8"))  # đọc lại file đã xuất
    pool = te["text"].tolist() or va["text"].tolist()
    if len(pool) < 50:
        pool = pool + va["text"].tolist() + tr["text"].tolist()
    parity, max_diff = build_parity(model, vecs, clf, pool, seed=args.seed)
    write_json(parity, args.out / "parity.json", compact=False)
    n_feat = sum(len(b["vocab"]) for b in model["blocks"])
    size_kb = (args.out / "intent-model.json").stat().st_size / 1024
    print(f"\nĐã ghi {args.out / 'intent-model.json'} ({n_feat} đặc trưng, {size_kb:.0f} KB) "
          f"và parity.json ({len(parity)} câu, lệch tối đa so với sklearn {max_diff:.2e})")

    write_json({"selected": best, "grid": grid, "threshold": threshold, "threshold_table": ttable,
                "n_features": n_feat, "parity_max_diff": max_diff, "train_size": len(tr), "val_size": len(va)},
               args.out / "training-log.json", compact=False)


if __name__ == "__main__":
    main()
