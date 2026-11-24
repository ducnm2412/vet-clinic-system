"""Chuẩn hoá chữ + tách đặc trưng — phải giống hệt TextNormalizer.java và MODEL_FORMAT.md mục 1-2."""
from __future__ import annotations

import re
import unicodedata

CHAR_NGRAM_MIN = 2
CHAR_NGRAM_MAX = 5

_SPACES = re.compile(r" +")


def _is_letter_or_digit(ch: str) -> bool:
    # Java \p{L} và \p{N}: mọi category L* và N*.
    cat = unicodedata.category(ch)
    return cat[0] == "L" or cat[0] == "N"


def normalize(text: str | None) -> str:
    """Port từng bước của TextNormalizer.normalize (Java)."""
    if text is None:
        return ""
    decomposed = unicodedata.normalize("NFD", str(text))
    # \p{M}+ -> ""
    no_marks = "".join(ch for ch in decomposed if not unicodedata.category(ch).startswith("M"))
    no_marks = no_marks.replace("đ", "d").replace("Đ", "D")
    lowered = no_marks.lower()
    # [^\p{L}\p{N}]+ -> " "
    out = []
    prev_space = False
    for ch in lowered:
        if _is_letter_or_digit(ch):
            out.append(ch)
            prev_space = False
        elif not prev_space:
            out.append(" ")
            prev_space = True
    # trim + \s+ -> " " (sau bước trên chỉ còn dấu cách)
    return _SPACES.sub(" ", "".join(out).strip())


def tokens(normalized: str) -> list[str]:
    return normalized.split(" ") if normalized else []


def word_terms(text: str | None) -> list[str]:
    """Khối word: unigram + bigram trên câu đã chuẩn hoá. Nhận câu thô, tự chuẩn hoá."""
    toks = tokens(normalize(text))
    terms = list(toks)
    terms.extend(toks[i] + " " + toks[i + 1] for i in range(len(toks) - 1))
    return terms


def char_terms(text: str | None, nmin: int = CHAR_NGRAM_MIN, nmax: int = CHAR_NGRAM_MAX) -> list[str]:
    """Khối char: n-gram ký tự trong từng token có đệm cách hai đầu (giống char_wb)."""
    terms: list[str] = []
    for t in tokens(normalize(text)):
        w = " " + t + " "
        for n in range(nmin, nmax + 1):
            if len(w) < n:
                continue
            terms.extend(w[i:i + n] for i in range(len(w) - n + 1))
    return terms
