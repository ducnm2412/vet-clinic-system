"""Port của RuleIntentClassifier.java — dùng đúng các regex trong file Java.

Luật chỉ biết 7 nhãn; các nhãn PRICE, SERVICE_INFO, PRODUCT, BOOKING, CARE_ADVICE không có luật nên
luật trả OTHER cho chúng.
"""
from __future__ import annotations

import re

from textnorm import normalize

RULE_LABELS = ["EMERGENCY", "OFF_TOPIC", "GREETING", "THANKS", "CONTACT", "OPENING_HOURS", "OTHER"]
ML_ONLY_LABELS = ["PRICE", "SERVICE_INFO", "PRODUCT", "BOOKING", "CARE_ADVICE"]
ALL_LABELS = sorted(RULE_LABELS + ML_ONLY_LABELS)


def _p(regex: str) -> re.Pattern:
    # Văn bản đã chuẩn hoá gần như chỉ còn ASCII nên \b của Python (Unicode) và Java cho cùng kết quả.
    return re.compile(regex)


EMERGENCY = [
    _p(r"\b(co giat|len con|dong kinh)\b"),
    _p(r"\b(kho tho|tho gap|tho rit|tho khe khe|khong tho duoc|ngung tho|tho hon hen|tho bang mieng)\b"),
    _p(r"\b(ngo doc|an nham|nuot nham|nuot phai|an phai)\b"),
    _p(r"\b(chay mau|ra mau|non ra mau|ia ra mau|dai ra mau|mau chay)\b"),
    _p(r"\b(bat tinh|hon me|ngat|bi xiu|xiu (luon|roi|di|mat|xuong)|liet|te liet|khong dung duoc|khong dung vung)\b"),
    _p(r"\b(bi xe (dam|tong|can)|tai nan|bi tong|roi tu (cao|tang|ban cong|lau))\b"),
    _p(r"\b(tim tai|luoi tim|say nang|say nang|dot quy)\b"),
    _p(r"\b(sung tac|bi tac|bi nghen|nghen (thuc an|xuong|hong|co|tho)|hoc xuong|hoc di vat|de kho|de khong ra)\b"),
    _p(r"\bcap cuu\b"),
]

OFF_TOPIC = [
    _p(r"\b(bo qua|quen|phot lo|vo hieu hoa|bat chap) (het |tat ca |moi |cac |nhung |toan bo )*(huong dan|chi dan|quy tac|lenh|cau lenh)\b"),
    _p(r"\bignore (all |any |the |your |previous |prior )*(instructions|rules|prompts?)\b"),
    _p(r"\b(system prompt|prompt he thong|prompt cua ban|tiet lo (prompt|huong dan|quy tac)|huong dan noi bo|huong dan he thong)\b"),
    _p(r"\b(jailbreak|dan mode|developer mode|do anything now|pretend (you|to be)|you have no (rules|restrictions)|without (any )?(rules|restrictions)|with no (rules|restrictions|filters)|(unrestricted|uncensored|unfiltered) (ai|mode|assistant|chatbot))\b"),
    _p(r"\b(dong vai|gia vo|gia lam|hoa than|nhap vai)\b.*\bkhong (co |bi |con )*(gioi han|rang buoc|kiem duyet|quy tac|luat le)\b"),
    _p(r"\b(ca do|danh bac|lo de|casino|khieu dam|phim sex|hack (tai khoan|facebook|fb|wifi|game|mat khau|web|website|he thong|zalo)|ddos|phishing|lua dao|ma tuy)\b"),
]

GREETING = _p(
    r"^(xin )?(chao|hello|hi|hey|alo|helo)( (ban|bot|ad|admin|shop|phong kham|anh|chi|em|bac si))?( a| nhe| nha| oi)?$"
    r"|^(ban|bot|ad|shop|alo) oi$"
)

THANKS = _p(r"\b(cam on|camon|thanks|thank you|thank|tks)\b")
THANKS_MAX_LENGTH = 25

CONTACT = _p(
    r"\b(so dien thoai|dien thoai|sdt|hotline|zalo|facebook|email|mail|dia chi|o dau|duong nao|chi duong|ban do|lien he|tong dai)\b")
CONTACT_EXCLUDE = _p(
    r"\b(gia|bao nhieu|dat lich|dat hen|san pham|mua|ship|giao hang|dich vu|thuoc|tai khoan|dang nhap|mat khau|don hang)\b")

HOURS = _p(
    r"\b(gio lam viec|gio mo cua|gio dong cua|mo cua|dong cua|may gio (mo|dong|lam)|lam viec (den|toi|tu)|thoi gian lam viec|co lam viec|truc 24|truc dem)\b")
HOURS_EXCLUDE = _p(r"\b(trong|con cho|con slot|con lich|dat lich|dat hen|hen kham|lich kham|bac si nao)\b")


def _java_length(s: str) -> int:
    """String.length() của Java = số đơn vị UTF-16."""
    return len(s.encode("utf-16-le")) // 2


def classify_normalized(text: str) -> str:
    if not text:
        return "OTHER"
    if any(p.search(text) for p in EMERGENCY):
        return "EMERGENCY"
    if any(p.search(text) for p in OFF_TOPIC):
        return "OFF_TOPIC"
    if GREETING.search(text):
        return "GREETING"
    if _java_length(text) <= THANKS_MAX_LENGTH and THANKS.search(text):
        return "THANKS"
    if CONTACT.search(text) and not CONTACT_EXCLUDE.search(text):
        return "CONTACT"
    if HOURS.search(text) and not HOURS_EXCLUDE.search(text):
        return "OPENING_HOURS"
    return "OTHER"


def classify(message: str | None) -> str:
    return classify_normalized(normalize(message))


def to_rule_space(label: str) -> str:
    """Gộp 12 nhãn về 7 nhãn của luật (5 nhãn mới → OTHER)."""
    return "OTHER" if label in ML_ONLY_LABELS else label
