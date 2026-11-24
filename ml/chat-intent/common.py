"""Tiện ích dùng chung: đọc dữ liệu, đo chỉ số, và suy luận thuần Python từ intent-model.json."""
from __future__ import annotations

import json
import math
import sys
from collections import Counter
from pathlib import Path

import pandas as pd
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_recall_fscore_support

from textnorm import normalize

# Console Windows mặc định cp125x → in tiếng Việt sẽ lỗi; ép UTF-8 cho mọi script.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
MODEL_DIR = ROOT / "model"
RESULTS_DIR = ROOT / "results"


def read_split(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path, dtype=str, keep_default_na=False, encoding="utf-8")
    return df


# --------------------------------------------------------------------------------------------- metrics

def summarize(y_true, y_pred, labels: list[str]) -> dict:
    """accuracy, macro-F1 (trên các nhãn `labels`), precision/recall/F1 từng nhãn, ma trận nhầm lẫn."""
    p, r, f, s = precision_recall_fscore_support(y_true, y_pred, labels=labels, zero_division=0)
    per = {lab: {"precision": p[i], "recall": r[i], "f1": f[i], "support": int(s[i])} for i, lab in enumerate(labels)}
    present = [lab for lab in labels if per[lab]["support"] > 0]
    macro = f1_score(y_true, y_pred, labels=present, average="macro", zero_division=0) if present else 0.0
    out = {
        "n": len(y_true),
        "accuracy": accuracy_score(y_true, y_pred) if len(y_true) else 0.0,
        "macro_f1": macro,
        "per_label": per,
        "labels": labels,
        "confusion": confusion_matrix(y_true, y_pred, labels=labels) if len(y_true) else None,
    }
    if "EMERGENCY" in labels:
        yt = list(y_true)
        yp = list(y_pred)
        tp = sum(1 for a, b in zip(yt, yp) if a == "EMERGENCY" and b == "EMERGENCY")
        fn = sum(1 for a, b in zip(yt, yp) if a == "EMERGENCY" and b != "EMERGENCY")
        fp = sum(1 for a, b in zip(yt, yp) if a != "EMERGENCY" and b == "EMERGENCY")
        out["emergency"] = {
            "recall": tp / (tp + fn) if tp + fn else 0.0,
            "precision": tp / (tp + fp) if tp + fp else 0.0,
            "tp": tp, "fn": fn, "false_alarms": fp,
        }
    return out


def format_report(m: dict, title: str) -> str:
    lines = [f"### {title}", "",
             f"n = {m['n']}  |  accuracy = {m['accuracy']:.4f}  |  macro-F1 = {m['macro_f1']:.4f}", ""]
    if "emergency" in m:
        e = m["emergency"]
        lines += [f"EMERGENCY: recall = {e['recall']:.4f} ({e['tp']}/{e['tp'] + e['fn']}), "
                  f"precision = {e['precision']:.4f}, báo nhầm (FP) = {e['false_alarms']}", ""]
    lines += ["| nhãn | precision | recall | F1 | support |", "|---|---:|---:|---:|---:|"]
    for lab in m["labels"]:
        d = m["per_label"][lab]
        lines.append(f"| {lab} | {d['precision']:.3f} | {d['recall']:.3f} | {d['f1']:.3f} | {d['support']} |")
    if m["confusion"] is not None:
        short = [lab[:6] for lab in m["labels"]]
        lines += ["", "Ma trận nhầm lẫn (hàng = thật, cột = dự đoán):", "", "```",
                  "thật\\dự".ljust(14) + "".join(s.rjust(7) for s in short)]
        for lab, row in zip(m["labels"], m["confusion"]):
            lines.append(lab[:13].ljust(14) + "".join(str(v).rjust(7) for v in row))
        lines.append("```")
    return "\n".join(lines) + "\n"


def label_counts(series) -> str:
    c = Counter(series)
    return ", ".join(f"{k}={v}" for k, v in sorted(c.items()))


# ------------------------------------------------------------------------- pure-Python JSON inference

def load_model(path: Path | str) -> dict:
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def _block_terms(block: dict, normalized: str) -> list[str]:
    toks = normalized.split(" ") if normalized else []
    if block["name"] == "word":
        return toks + [toks[i] + " " + toks[i + 1] for i in range(len(toks) - 1)]
    if block["name"] == "char":
        out = []
        for t in toks:
            w = " " + t + " "
            for n in range(block["ngram_min"], block["ngram_max"] + 1):
                if len(w) < n:
                    continue
                out.extend(w[i:i + n] for i in range(len(w) - n + 1))
        return out
    raise ValueError(f"khối lạ: {block['name']}")


def features_from_json(model: dict, text: str | None) -> dict[int, float]:
    """Vector thưa {chỉ số toàn cục: giá trị} đúng MODEL_FORMAT.md mục 2."""
    normalized = normalize(text)
    x: dict[int, float] = {}
    if not normalized:
        return x
    for block in model["blocks"]:
        vocab = block["vocab"]
        idf = block["idf"]
        tf: dict[int, int] = {}
        for term in _block_terms(block, normalized):
            j = vocab.get(term)
            if j is not None:
                tf[j] = tf.get(j, 0) + 1
        vals = {j: (1.0 + math.log(c)) * idf[j] for j, c in tf.items()}
        norm = math.sqrt(sum(v * v for v in vals.values()))
        off = block["offset"]
        for j, v in vals.items():
            x[off + j] = v / norm if norm > 0 else v
    return x


def predict_from_json(model: dict, text: str | None) -> tuple[str, float, list[float]]:
    """Trả (nhãn, confidence, xác suất theo thứ tự model['labels']). Chỉ dùng JSON + thư viện chuẩn.

    Câu chuẩn hoá rỗng → nhãn OTHER (xác suất vẫn là softmax(intercept), tức vector đặc trưng 0).
    """
    x = features_from_json(model, text)
    logits = []
    for k, row in enumerate(model["coef"]):
        s = model["intercept"][k]
        for j, v in x.items():
            s += row[j] * v
        logits.append(s)
    mx = max(logits)
    exps = [math.exp(z - mx) for z in logits]
    tot = sum(exps)
    probs = [e / tot for e in exps]
    k = max(range(len(probs)), key=lambda i: probs[i])
    if not normalize(text):
        return "OTHER", probs[k], probs
    return model["labels"][k], probs[k], probs


# --------------------------------------------------------------------- A2: embedding + logreg / MLP

def _softmax(z: list[float]) -> list[float]:
    mx = max(z)
    exps = [math.exp(v - mx) for v in z]
    tot = sum(exps)
    return [e / tot for e in exps]


def predict_a2_from_json(model: dict, embedding) -> tuple[str, float, list[float]]:
    """Suy luận A2 thuần Python (MODEL_FORMAT.md mục A2). `embedding` = vector embedContent thô hoặc đã chuẩn hoá;
    hàm luôn chuẩn hoá L2 lại (vô hại nếu đã chuẩn hoá). Trả (nhãn, confidence, xác suất theo model['labels'])."""
    x = [float(v) for v in embedding]
    dim = model["embedding"]["output_dimensionality"]
    if len(x) != dim:
        raise ValueError(f"embedding {len(x)} chiều, mô hình cần {dim}")
    norm = math.sqrt(sum(v * v for v in x))
    if norm > 0:
        x = [v / norm for v in x]
    if model["kind"] == "embedding-logreg":
        logits = [b + sum(w * v for w, v in zip(row, x)) for row, b in zip(model["coef"], model["intercept"])]
        probs = _softmax(logits)
    elif model["kind"] == "embedding-mlp":
        h = x
        for layer in model["layers"]:
            z = [b + sum(w * v for w, v in zip(row, h)) for row, b in zip(layer["weights"], layer["bias"])]
            act = layer["activation"]
            if act == "relu":
                h = [v if v > 0 else 0.0 for v in z]
            elif act == "tanh":
                h = [math.tanh(v) for v in z]
            elif act == "identity":
                h = z
            elif act == "softmax":
                h = _softmax(z)
            else:
                raise ValueError(f"hàm kích hoạt lạ: {act}")
        probs = h
    else:
        raise ValueError(f"kind lạ: {model['kind']}")
    k = max(range(len(probs)), key=lambda i: probs[i])
    return model["labels"][k], probs[k], probs
