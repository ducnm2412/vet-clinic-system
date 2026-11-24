"""Đánh giá mô hình đã xuất (đọc model/intent-model.json, suy luận thuần Python giống Java).

So sánh rule / ml / hybrid. hybrid: luật ra EMERGENCY/OFF_TOPIC → giữ nhãn luật; ngược lại ML conf ≥ threshold → nhãn ML;
ngược lại → nhãn luật.

    python eval.py          # val
    python eval.py --test   # val + test (chỉ chạy một lần ở bước cuối)
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import rules
from common import DATA_DIR, MODEL_DIR, RESULTS_DIR, format_report, load_model, predict_from_json, read_split, summarize
from rules import ALL_LABELS, RULE_LABELS, to_rule_space
from train import hybrid


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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--test", action="store_true", help="đánh giá thêm trên data/test.csv")
    ap.add_argument("--model", type=Path, default=MODEL_DIR / "intent-model.json")
    ap.add_argument("--data", type=Path, default=DATA_DIR)
    ap.add_argument("--out", type=Path, default=RESULTS_DIR / "a1.md")
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


if __name__ == "__main__":
    main()
