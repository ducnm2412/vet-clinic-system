"""A2 phần không cần mạng: cache embedding, khoá API không lọt ra ngoài, predict_a2_from_json khớp sklearn."""
import json

import numpy as np
import pytest

import embed
import train_a2
from common import load_model, predict_a2_from_json
from train import write_json

LABELS = ["A", "B", "C"]


def fake_embed_factory(dim=8, calls=None):
    def fn(texts):
        if calls is not None:
            calls.extend(texts)
        rng = [np.random.default_rng(abs(hash(t)) % (2 ** 32)) for t in texts]
        return np.stack([r.normal(size=dim) * 5 for r in rng])
    return fn


def test_cache_roundtrip_and_incremental(tmp_path):
    calls = []
    fn = fake_embed_factory(calls=calls)
    texts = ["mèo co giật", "chào shop", "giá triệt sản", "chào shop"]  # có câu trùng
    X, n_new = embed.ensure_embeddings("m", "train", texts, fn, tmp_path, batch_size=2, pause_s=0)
    assert X.shape == (4, 8) and n_new == 3 and len(calls) == 3
    assert np.allclose(np.linalg.norm(X, axis=1), 1.0, atol=1e-6)  # đã chuẩn hoá L2
    assert np.allclose(X[1], X[3])

    # chạy lại: không gọi API
    X2, n_new2 = embed.ensure_embeddings("m", "train", texts, fn, tmp_path, pause_s=0)
    assert n_new2 == 0 and len(calls) == 3 and np.allclose(X, X2, atol=1e-6)

    # thêm câu mới: chỉ gọi cho câu mới, thứ tự theo danh sách mới
    texts3 = ["câu mới", *texts]
    X3, n_new3 = embed.ensure_embeddings("m", "train", texts3, fn, tmp_path, pause_s=0)
    assert n_new3 == 1 and calls[-1] == "câu mới" and np.allclose(X3[1:], X, atol=1e-6)

    assert np.allclose(embed.load_embeddings("m", "train", texts3, tmp_path), X3, atol=1e-6)
    with pytest.raises(ValueError):
        embed.load_embeddings("m", "train", texts, tmp_path)  # chỉ mục không khớp
    with pytest.raises(FileNotFoundError):
        embed.load_embeddings("m", "val", texts, tmp_path)


def test_corrupt_cache_is_ignored(tmp_path):
    npy, idx = embed.cache_paths("m", "val", tmp_path)
    tmp_path.mkdir(exist_ok=True)
    np.save(npy, np.zeros((2, 8), dtype=np.float32))
    idx.write_text(json.dumps(["chỉ một câu"]), encoding="utf-8")
    assert embed.load_cache("m", "val", tmp_path) == {}


def test_api_key_from_env_file(tmp_path, monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    env = tmp_path / ".env"
    env.write_text("OTHER=1\nGEMINI_API_KEY=\"abc123\"\n", encoding="utf-8")
    assert embed.load_api_key(env) == "abc123"
    monkeypatch.setenv("GEMINI_API_KEY", "fromenv")
    assert embed.load_api_key(env) == "fromenv"


def test_key_goes_in_header_not_url(monkeypatch):
    seen = {}

    class Resp:
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

        def read(self):
            return json.dumps({"embedding": {"values": [3.0, 4.0]}}).encode()

    def fake_urlopen(req, timeout=None):
        seen["url"], seen["headers"] = req.full_url, dict(req.header_items())
        seen["body"] = json.loads(req.data)
        return Resp()

    monkeypatch.setattr(embed.urllib.request, "urlopen", fake_urlopen)
    v = embed.embed_one("xin chào", "gemini-embedding-001", "SECRET", dim=2)
    assert list(v) == [3.0, 4.0]
    assert "SECRET" not in seen["url"] and seen["url"].endswith("gemini-embedding-001:embedContent")
    assert seen["headers"].get("X-goog-api-key") == "SECRET"
    assert seen["body"]["taskType"] == "CLASSIFICATION" and seen["body"]["outputDimensionality"] == 2


def _toy_data(dim=16, n_per=30, seed=0):
    rng = np.random.default_rng(seed)
    centers = rng.normal(size=(len(LABELS), dim)) * 2
    X = np.vstack([c + rng.normal(size=(n_per, dim)) for c in centers])
    y = [lab for lab in LABELS for _ in range(n_per)]
    return embed.l2_normalize(X), y


@pytest.mark.parametrize("kind", ["logreg", "mlp"])
def test_json_predict_matches_sklearn(tmp_path, monkeypatch, kind):
    dim = 16
    monkeypatch.setattr(embed, "DIM", dim)
    X, y = _toy_data(dim)
    clf = train_a2.fit_logreg(X, y, 1.0) if kind == "logreg" else train_a2.fit_mlp(X, y, 8, 1e-3)
    path = tmp_path / "m.json"
    write_json(train_a2.export_a2(clf, "fake-emb", 0.4), path)
    model = load_model(path)
    assert model["kind"] == f"embedding-{kind}" and model["embedding"]["output_dimensionality"] == dim
    sk = clf.predict_proba(X)
    for i in range(0, len(X), 7):
        raw = X[i] * 3.7  # chưa chuẩn hoá: hàm tự chuẩn hoá L2
        label, conf, probs = predict_a2_from_json(model, raw)
        assert np.max(np.abs(np.array(probs) - sk[i])) < 1e-4
        assert label == clf.classes_[sk[i].argmax()] and conf == max(probs)
    with pytest.raises(ValueError):
        predict_a2_from_json(model, [0.1] * (dim + 1))


def test_build_parity(tmp_path, monkeypatch):
    monkeypatch.setattr(embed, "DIM", 16)
    X, y = _toy_data(16)
    X = X.astype(np.float32).astype(np.float64)
    clf = train_a2.fit_mlp(X, y, 8, 1e-3)
    model = json.loads(json.dumps(train_a2.export_a2(clf, "fake-emb", 0.4)))
    texts = [f"câu {i}" for i in range(len(X))]
    parity, max_diff = train_a2.build_parity(model, clf, texts, X)
    assert len(parity) == train_a2.N_PARITY and max_diff < 1e-4
    assert len(parity[0]["embedding"]) == 16 and len(parity[0]["probs"]) == len(LABELS)
