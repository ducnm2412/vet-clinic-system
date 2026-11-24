"""A2 bước 1: lấy embedding Gemini cho train/val/test, cache vào data/embeddings/.

    python embed.py                                  # cả hai model, cả ba split
    python embed.py --models gemini-embedding-001 --splits train val

Cache mỗi (model, split): `{model}-{split}.npy` (float32, n x dim, đã chuẩn hoá L2, hàng i ứng câu i của CSV)
+ `{model}-{split}.texts.json` (danh sách câu đúng thứ tự, để kiểm tra khớp). Chạy lại chỉ gọi API cho câu chưa có
trong cache (tra theo nội dung câu, không theo vị trí).

Khoá API: biến môi trường GEMINI_API_KEY, nếu không có thì đọc từ `.env` ở gốc repo. Khoá chỉ đi qua header
`x-goog-api-key`; không bao giờ in/ghi ra file. Thông báo lỗi HTTP chỉ in mã trạng thái + `error.status/message`
của Google (không chứa khoá).
"""
from __future__ import annotations

import argparse
import json
import os
import random
import time
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np

from common import DATA_DIR, ROOT, read_split

API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
MODELS = ["gemini-embedding-001", "gemini-embedding-2"]
TASK_TYPE = "CLASSIFICATION"
DIM = 768
EMB_DIR = DATA_DIR / "embeddings"
BATCH_SIZE = 50
PAUSE_S = 0.7            # nghỉ giữa hai lần gọi (hạn mức miễn phí)
MAX_RETRIES = 6
RETRY_STATUS = {429, 500, 502, 503, 504}

CALLS = {"embed": 0, "batch": 0, "retry": 0}   # đếm số lần gọi HTTP thật (để báo cáo quota)


# ----------------------------------------------------------------------------------------------- key

def load_api_key(env_path: Path | None = None) -> str:
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if key:
        return key
    env_path = env_path or ROOT.parents[1] / ".env"
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("GEMINI_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"').strip("'")
                if key:
                    return key
    raise SystemExit("Thiếu GEMINI_API_KEY (biến môi trường hoặc .env ở gốc repo)")


# ------------------------------------------------------------------------------------------- HTTP

class ApiError(RuntimeError):
    def __init__(self, status: int, detail: str, daily: bool = False):
        super().__init__(f"HTTP {status}: {detail}")
        self.status = status
        self.daily = daily  # 429 do hạn mức theo NGÀY → thử lại vô ích


def _post(url: str, body: dict, key: str, timeout: float = 60.0) -> dict:
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"), method="POST",
                                 headers={"Content-Type": "application/json", "x-goog-api-key": key})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        quota_ids: list[str] = []
        try:
            err = json.loads(e.read().decode("utf-8")).get("error", {})
            for d in err.get("details", []) or []:
                for v in d.get("violations", []) or []:
                    if v.get("quotaId"):
                        quota_ids.append(str(v["quotaId"]))
            detail = f"{err.get('status', '')} {str(err.get('message', ''))[:120]}"
            if quota_ids:
                detail += f" [quota: {', '.join(quota_ids)}]"
        except Exception:  # noqa: BLE001 — thân lỗi không phải JSON
            detail = e.reason
        raise ApiError(e.code, str(detail), daily=any("PerDay" in q for q in quota_ids)) from None
    except (urllib.error.URLError, TimeoutError) as e:
        raise ApiError(0, f"mạng: {getattr(e, 'reason', e)}") from None


def post_with_backoff(url: str, body: dict, key: str, kind: str) -> dict:
    """Gọi POST; 429/5xx/lỗi mạng → chờ 2^i giây (+ nhiễu), tối đa MAX_RETRIES lần thử lại."""
    for attempt in range(MAX_RETRIES + 1):
        CALLS[kind] += 1
        try:
            return _post(url, body, key)
        except ApiError as e:
            if (e.status not in RETRY_STATUS and e.status != 0) or e.daily or attempt == MAX_RETRIES:
                raise
            CALLS["retry"] += 1
            wait = min(60.0, 2.0 ** (attempt + 1)) + random.uniform(0, 1)
            print(f"  {e} → thử lại sau {wait:.1f}s ({attempt + 1}/{MAX_RETRIES})")
            time.sleep(wait)
    raise AssertionError("unreachable")


def _req_body(model: str, text: str, dim: int, task: str) -> dict:
    return {"content": {"parts": [{"text": text}]}, "taskType": task, "outputDimensionality": dim}


def embed_one(text: str, model: str, key: str, dim: int = DIM, task: str = TASK_TYPE) -> np.ndarray:
    data = post_with_backoff(f"{API_BASE}/{model}:embedContent", _req_body(model, text, dim, task), key, "embed")
    return np.asarray(data["embedding"]["values"], dtype=np.float64)


def embed_batch(texts: list[str], model: str, key: str, dim: int = DIM, task: str = TASK_TYPE) -> np.ndarray:
    reqs = [dict(model=f"models/{model}", **_req_body(model, t, dim, task)) for t in texts]
    data = post_with_backoff(f"{API_BASE}/{model}:batchEmbedContents", {"requests": reqs}, key, "batch")
    vecs = [e["values"] for e in data["embeddings"]]
    if len(vecs) != len(texts):
        raise ApiError(200, f"batch trả {len(vecs)} vector cho {len(texts)} câu")
    return np.asarray(vecs, dtype=np.float64)


def l2_normalize(X: np.ndarray) -> np.ndarray:
    X = np.asarray(X, dtype=np.float64)
    if X.ndim == 1:
        n = np.linalg.norm(X)
        return X / n if n > 0 else X
    n = np.linalg.norm(X, axis=1, keepdims=True)
    n[n == 0] = 1.0
    return X / n


# ------------------------------------------------------------------------------------------ cache

def cache_paths(model: str, split: str, emb_dir: Path = EMB_DIR) -> tuple[Path, Path]:
    return emb_dir / f"{model}-{split}.npy", emb_dir / f"{model}-{split}.texts.json"


def load_cache(model: str, split: str, emb_dir: Path = EMB_DIR) -> dict[str, np.ndarray]:
    """{câu: vector} từ cache (rỗng nếu chưa có / hỏng chiều dài)."""
    npy, idx = cache_paths(model, split, emb_dir)
    if not (npy.exists() and idx.exists()):
        return {}
    X = np.load(npy)
    texts = json.loads(idx.read_text(encoding="utf-8"))
    if len(texts) != len(X):
        print(f"  cache {npy.name} lệch số dòng ({len(texts)} câu / {len(X)} vector) → bỏ")
        return {}
    return {t: X[i] for i, t in enumerate(texts)}


def save_cache(model: str, split: str, texts: list[str], X: np.ndarray, emb_dir: Path = EMB_DIR):
    npy, idx = cache_paths(model, split, emb_dir)
    emb_dir.mkdir(parents=True, exist_ok=True)
    np.save(npy, np.asarray(X, dtype=np.float32))
    idx.write_text(json.dumps(texts, ensure_ascii=False, indent=0), encoding="utf-8")


def load_embeddings(model: str, split: str, texts: list[str], emb_dir: Path = EMB_DIR) -> np.ndarray:
    """Ma trận embedding đúng thứ tự `texts`; lỗi nếu cache không khớp (phải chạy embed.py trước)."""
    npy, idx = cache_paths(model, split, emb_dir)
    if not (npy.exists() and idx.exists()):
        raise FileNotFoundError(f"thiếu {npy} — chạy embed.py trước")
    cached = json.loads(idx.read_text(encoding="utf-8"))
    if cached != list(texts):
        raise ValueError(f"{idx.name} không khớp {split}.csv — chạy lại embed.py")
    return np.load(npy).astype(np.float64)


def ensure_embeddings(model: str, split: str, texts: list[str], embed_fn, emb_dir: Path = EMB_DIR,
                      batch_size: int = BATCH_SIZE, pause_s: float = PAUSE_S) -> tuple[np.ndarray, int]:
    """Bổ sung vector còn thiếu bằng `embed_fn(list[str]) -> ndarray` rồi ghi cache. Trả (X, số câu mới gọi API).

    Ghi cache sau mỗi lô để có ngắt giữa chừng cũng không mất phần đã trả tiền/quota.
    """
    have = load_cache(model, split, emb_dir)
    missing = list(dict.fromkeys(t for t in texts if t not in have))
    for s in range(0, len(missing), batch_size):
        chunk = missing[s:s + batch_size]
        V = l2_normalize(embed_fn(chunk))
        for t, v in zip(chunk, V):
            have[t] = v
        done = [t for t in texts if t in have]
        save_cache(model, split, done, np.stack([have[t] for t in done]), emb_dir)
        print(f"  {model}/{split}: {min(s + batch_size, len(missing))}/{len(missing)} câu mới")
        if pause_s and s + batch_size < len(missing):
            time.sleep(pause_s)
    X = np.stack([have[t] for t in texts]) if texts else np.zeros((0, DIM))
    save_cache(model, split, list(texts), X, emb_dir)
    return X.astype(np.float64), len(missing)


def make_embed_fn(model: str, key: str, dim: int = DIM, task: str = TASK_TYPE):
    """Ưu tiên batchEmbedContents; nếu model không hỗ trợ (400/404) thì gọi từng câu."""
    state = {"batch_ok": True}

    def fn(texts: list[str]) -> np.ndarray:
        if state["batch_ok"]:
            try:
                return embed_batch(texts, model, key, dim, task)
            except ApiError as e:
                if e.status not in (400, 404):
                    raise
                print(f"  {model}: batchEmbedContents không dùng được ({e}) → gọi từng câu")
                state["batch_ok"] = False
        out = []
        for i, t in enumerate(texts):
            out.append(embed_one(t, model, key, dim, task))
            if i + 1 < len(texts):
                time.sleep(PAUSE_S)
        return np.stack(out)

    return fn


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", nargs="+", default=MODELS)
    ap.add_argument("--splits", nargs="+", default=["train", "val", "test"])
    ap.add_argument("--data", type=Path, default=DATA_DIR)
    args = ap.parse_args()

    key = load_api_key()
    emb_dir = args.data / "embeddings"
    for model in args.models:
        fn = make_embed_fn(model, key)
        for split in args.splits:
            texts = read_split(args.data / f"{split}.csv")["text"].tolist()
            try:
                X, n_new = ensure_embeddings(model, split, texts, fn, emb_dir)
            except ApiError as e:
                print(f"{model}/{split}: DỪNG ({e}); phần đã lấy vẫn nằm trong cache, chạy lại sau để bổ sung")
                break  # sang model kế tiếp (hạn mức tính riêng theo model)
            print(f"{model}/{split}: {X.shape}, mới gọi API cho {n_new} câu")
    print(f"Số lần gọi HTTP: batchEmbedContents={CALLS['batch']}, embedContent={CALLS['embed']}, "
          f"trong đó thử lại={CALLS['retry']}")


if __name__ == "__main__":
    main()
