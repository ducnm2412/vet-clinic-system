from textnorm import char_terms, normalize, word_terms


def test_none_and_empty():
    assert normalize(None) == ""
    assert normalize("") == ""
    assert normalize("   \t\n ") == ""


def test_d_stroke():
    assert normalize("Đường đi") == "duong di"
    assert normalize("ĐĐđđ") == "dddd"


def test_precomposed_and_combining_marks_equal():
    nfc = "Mèo bị nôn ra máu"
    nfd = "Mèo bị nôn ra máu"
    assert normalize(nfc) == normalize(nfd) == "meo bi non ra mau"
    assert normalize("ỡ ặ ữ ơ ư") == "o a u o u"


def test_uppercase():
    assert normalize("TIÊM PHÒNG CHO CHÓ") == "tiem phong cho cho"


def test_emoji_and_symbols_become_space():
    assert normalize("mèo 🐱 bệnh!!!") == "meo benh"
    assert normalize("🐶🐱❤️🔥") == ""
    assert normalize("chào👋shop") == "chao shop"
    assert normalize("a.b,c?d") == "a b c d"


def test_numbers_kept():
    assert normalize("0901-234 567") == "0901 234 567"
    assert normalize("tiêm 2 mũi, 300k") == "tiem 2 mui 300k"
    assert normalize("x²") == "x²"  # \p{N} gồm cả No như Java


def test_whitespace_collapse():
    assert normalize("  chó   bị\t\tốm \n ") == "cho bi om"


def test_word_terms():
    assert word_terms("Chó bị ốm") == ["cho", "bi", "om", "cho bi", "bi om"]
    assert word_terms("chó") == ["cho"]
    assert word_terms("!!!") == []


def test_char_terms():
    assert char_terms("ab") == [" a", "ab", "b ", " ab", "ab ", " ab "]
    # token 1 ký tự: w = " x " dài 3 → n=2,3; n=4,5 bị bỏ
    assert char_terms("x") == [" x", "x ", " x "]
    assert char_terms("") == []
    # từng token riêng, không vắt qua dấu cách giữa hai token
    terms = char_terms("ab cd")
    assert "b c" not in terms and " cd " in terms
