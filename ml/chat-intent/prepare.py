"""Gộp data/raw/*.csv → làm sạch → chia phân tầng 70/15/15 (seed 42) → data/{train,val,test}.csv.

Chống rò rỉ biến thể: câu `variant` không ghi lại câu gốc, nên ta gom nhóm xấp xỉ — hai câu CÙNG NHÃN có
độ giống Jaccard trên tập 3-gram ký tự (của câu đã chuẩn hoá) ≥ --group-sim thì vào cùng nhóm (union-find).
Cả nhóm rơi trọn vào một tập. Biến thể chỉ khác dấu thì sau chuẩn hoá trùng hẳn và đã bị bỏ ở bước khử trùng.
Tắt bằng --no-group (chia phân tầng theo từng câu).
"""
from __future__ import annotations

import argparse
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

import pandas as pd

from common import DATA_DIR, label_counts
from rules import ALL_LABELS
from textnorm import normalize

ALIASES = {
    "HOURS": "OPENING_HOURS", "OPENINGHOURS": "OPENING_HOURS", "OPEN_HOURS": "OPENING_HOURS",
    "SERVICE": "SERVICE_INFO", "SERVICEINFO": "SERVICE_INFO",
    "CARE": "CARE_ADVICE", "CAREADVICE": "CARE_ADVICE", "ADVICE": "CARE_ADVICE",
    "OFFTOPIC": "OFF_TOPIC", "GREET": "GREETING", "THANK": "THANKS", "PRODUCTS": "PRODUCT",
    "EMERGENCIES": "EMERGENCY", "BOOK": "BOOKING",
}


def canon_label(raw: str) -> str | None:
    lab = str(raw).strip().upper().replace("-", "_").replace(" ", "_")
    lab = ALIASES.get(lab, lab)
    return lab if lab in ALL_LABELS else None


def load_raw(raw_dir: Path) -> pd.DataFrame:
    files = sorted(raw_dir.glob("*.csv"))
    if not files:
        sys.exit(f"Không có file CSV nào trong {raw_dir}")
    frames = []
    for f in files:
        df = pd.read_csv(f, dtype=str, keep_default_na=False, encoding="utf-8-sig")
        df.columns = [c.strip().lower() for c in df.columns]
        if "text" not in df.columns or "label" not in df.columns:
            print(f"[bỏ qua] {f.name}: thiếu cột text/label")
            continue
        if "source" not in df.columns:
            df["source"] = ""
        df = df[["text", "label", "source"]].copy()
        df["file"] = f.name
        frames.append(df)
        print(f"Đọc {f.name}: {len(df)} dòng")
    return pd.concat(frames, ignore_index=True)


def clean(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["text"] = df["text"].astype(str).str.strip()
    df["source"] = df["source"].astype(str).str.strip().str.lower()
    df["label_raw"] = df["label"]
    df["label"] = df["label"].map(canon_label)
    bad = df[df["label"].isna()]
    if len(bad):
        print(f"[cảnh báo] bỏ {len(bad)} dòng nhãn lạ: {Counter(bad['label_raw']).most_common(10)}")
    df = df[df["label"].notna()]
    df["norm"] = df["text"].map(normalize)
    empty = (df["norm"] == "").sum()
    if empty:
        print(f"[cảnh báo] bỏ {empty} dòng rỗng sau chuẩn hoá")
    df = df[df["norm"] != ""]

    # trùng mà khác nhãn → bỏ tất cả
    labels_per_norm = df.groupby("norm")["label"].nunique()
    conflicts = labels_per_norm[labels_per_norm > 1].index
    for n in conflicts:
        rows = df[df["norm"] == n]
        print(f"[cảnh báo] mâu thuẫn nhãn, bỏ: {n!r} -> {sorted(set(rows['label']))} ({sorted(set(rows['file']))})")
    df = df[~df["norm"].isin(set(conflicts))]
    before = len(df)
    df = df.drop_duplicates(subset="norm", keep="first")
    print(f"Bỏ {before - len(df)} câu trùng (cùng nhãn), {len(conflicts)} câu mâu thuẫn nhãn")
    return df.reset_index(drop=True)


def _grams(s: str) -> set[str]:
    s = f" {s} "
    return {s[i:i + 3] for i in range(len(s) - 2)} or {s}


def assign_groups(df: pd.DataFrame, sim: float) -> list[int]:
    parent = list(range(len(df)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    grams = [_grams(n) for n in df["norm"]]
    for _, idx in df.groupby("label").groups.items():
        idx = list(idx)
        for a_pos, a in enumerate(idx):
            ga = grams[a]
            for b in idx[a_pos + 1:]:
                gb = grams[b]
                inter = len(ga & gb)
                if inter and inter / (len(ga) + len(gb) - inter) >= sim:
                    ra, rb = find(a), find(b)
                    if ra != rb:
                        parent[rb] = ra
    return [find(i) for i in range(len(df))]


def split(df: pd.DataFrame, seed: int, ratios=(0.70, 0.15, 0.15)) -> pd.Series:
    """Phân tầng theo nhãn, đơn vị chia là nhóm; trả cột 'split'."""
    rng = random.Random(seed)
    out = pd.Series("", index=df.index)
    for lab in sorted(df["label"].unique()):
        sub = df[df["label"] == lab]
        groups = defaultdict(list)
        for i, g in zip(sub.index, sub["group"]):
            groups[g].append(i)
        keys = sorted(groups)
        rng.shuffle(keys)
        n = len(sub)
        t_train, t_val = ratios[0] * n, (ratios[0] + ratios[1]) * n
        done = 0
        for k in keys:
            members = groups[k]
            # đặt nhóm vào tập mà "điểm giữa" của nhóm rơi vào → tỉ lệ sát 70/15/15 dù nhóm to nhỏ khác nhau
            mid = done + len(members) / 2
            name = "train" if mid < t_train else ("val" if mid < t_val else "test")
            out[members] = name
            done += len(members)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", type=Path, default=DATA_DIR / "raw")
    ap.add_argument("--out", type=Path, default=DATA_DIR)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--group-sim", type=float, default=0.6, help="ngưỡng Jaccard 3-gram để gom biến thể")
    ap.add_argument("--no-group", action="store_true", help="không gom nhóm biến thể")
    args = ap.parse_args()

    df = clean(load_raw(args.raw))
    if args.no_group:
        df["group"] = range(len(df))
    else:
        df["group"] = assign_groups(df, args.group_sim)
        sizes = Counter(df["group"])
        multi = [s for s in sizes.values() if s > 1]
        print(f"Gom nhóm biến thể (Jaccard≥{args.group_sim}): {len(sizes)} nhóm, "
              f"{len(multi)} nhóm có >1 câu (gồm {sum(multi)} câu), nhóm lớn nhất {max(sizes.values())} câu")
    df["split"] = split(df, args.seed)

    args.out.mkdir(parents=True, exist_ok=True)
    cols = ["text", "label", "source"]
    for name in ("train", "val", "test"):
        part = df[df["split"] == name].sample(frac=1.0, random_state=args.seed)
        part[cols].to_csv(args.out / f"{name}.csv", index=False, encoding="utf-8")

    print(f"\nTổng sau làm sạch: {len(df)} câu")
    tab = pd.crosstab(df["label"], df["split"]).reindex(columns=["train", "val", "test"], fill_value=0)
    tab["total"] = tab.sum(axis=1)
    tab.loc["TOTAL"] = tab.sum()
    print(tab.to_string())
    print("\nTheo source:", label_counts(df["source"]))
    missing = [lab for lab in ALL_LABELS if lab not in set(df["label"])]
    if missing:
        print(f"[cảnh báo] thiếu nhãn: {missing}")


if __name__ == "__main__":
    main()
