"""BASELINE: chạy lại logic RuleIntentClassifier (port Python) trên một tập dữ liệu.

(a) 12 nhãn — luật không có PRICE/SERVICE_INFO/PRODUCT/BOOKING/CARE_ADVICE nên trả OTHER cho chúng.
(b) gộp về 7 nhãn của luật (5 nhãn mới → OTHER) để so công bằng.

    python eval_rules.py                 # trên data/val.csv
    python eval_rules.py --split test
    python eval_rules.py --csv path.csv
"""
from __future__ import annotations

import argparse
from pathlib import Path

from common import DATA_DIR, RESULTS_DIR, format_report, read_split, summarize
from rules import ALL_LABELS, RULE_LABELS, classify, to_rule_space


def evaluate(texts, gold) -> tuple[dict, dict]:
    pred = [classify(t) for t in texts]
    m12 = summarize(gold, pred, ALL_LABELS)
    m7 = summarize([to_rule_space(g) for g in gold], pred, RULE_LABELS)
    return m12, m7


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--split", default="val", choices=["train", "val", "test"])
    ap.add_argument("--csv", type=Path, help="đánh giá trên file CSV bất kỳ (cột text,label)")
    ap.add_argument("--no-write", action="store_true")
    args = ap.parse_args()

    path = args.csv or DATA_DIR / f"{args.split}.csv"
    df = read_split(path)
    m12, m7 = evaluate(df["text"].tolist(), df["label"].tolist())
    name = path.stem
    report = (f"# Baseline luật (RuleIntentClassifier) — {name}\n\n"
              + format_report(m12, "(a) 12 nhãn (nhãn luật không có → luật trả OTHER)") + "\n"
              + format_report(m7, "(b) 7 nhãn của luật (PRICE/SERVICE_INFO/PRODUCT/BOOKING/CARE_ADVICE → OTHER)"))
    print(report)
    if not args.no_write:
        RESULTS_DIR.mkdir(exist_ok=True)
        out = RESULTS_DIR / f"rules_{name}.md"
        out.write_text(report, encoding="utf-8")
        print(f"Đã ghi {out}")


if __name__ == "__main__":
    main()
