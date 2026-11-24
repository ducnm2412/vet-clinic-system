"""Chính sách A1+A2 trong eval.py phải giống CompositeIntentClassifier (chế độ hybrid, có A2)."""
import importlib

import pytest

ev = importlib.import_module("eval")  # tên module trùng hàm built-in eval() nên nạp kiểu này

A1_T, A2_T = 0.3, 0.4


@pytest.mark.parametrize("rule,a1,conf,a2,expected", [
    # luật EMERGENCY / OFF_TOPIC luôn ghi đè, kể cả khi A1/A2 rất chắc
    ("EMERGENCY", "PRICE", 0.99, ("PRICE", 0.99), ("EMERGENCY", "rule-override")),
    ("OFF_TOPIC", "BOOKING", 0.2, ("BOOKING", 0.9), ("OFF_TOPIC", "rule-override")),
    # A1 không chắc (< 0.6) và A2 đủ tin cậy → A2 (kể cả A2 ra EMERGENCY)
    ("OTHER", "PRICE", 0.55, ("SERVICE_INFO", 0.7), ("SERVICE_INFO", "a2")),
    ("OTHER", "CARE_ADVICE", 0.1, ("EMERGENCY", 0.4), ("EMERGENCY", "a2")),
    # A2 không chắc → logic cũ: A1 nếu ≥ ngưỡng A1, ngược lại nhãn luật
    ("OTHER", "PRICE", 0.5, ("SERVICE_INFO", 0.39), ("PRICE", "a1")),
    ("GREETING", "OFF_TOPIC", 0.25, ("THANKS", 0.2), ("GREETING", "rule")),
    # không gọi A2 (câu ngắn / lỗi) → logic cũ
    ("OTHER", "PRICE", 0.35, None, ("PRICE", "a1")),
    ("GREETING", "OTHER", 0.2, None, ("GREETING", "rule")),
    # A1 đủ chắc (≥ 0.6) → không xét A2
    ("OTHER", "PRICE", 0.6, ("SERVICE_INFO", 0.99), ("PRICE", "a1")),
])
def test_composite_a2(rule, a1, conf, a2, expected):
    assert ev.composite_a2(rule, a1, conf, A1_T, a2, A2_T) == expected


@pytest.mark.parametrize("text,ok", [
    ("", False), ("!!!", False), ("hi shop", False), ("chó bị nôn", True), ("  mèo   ăn   gì ", True),
])
def test_a2_accepts_min_words(text, ok):
    assert ev.a2_accepts(text) is ok
