package com.vetclinic.chat.intent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tầng rule đứng trước Gemini: câu khẩn cấp, chào hỏi, hỏi địa chỉ/hotline/giờ mở cửa, lạc đề được
 * nhận ra bằng regex trên chữ đã bỏ dấu và trả bằng câu soạn sẵn. Nhanh, không tốn hạn mức Gemini,
 * và quan trọng nhất: câu khẩn cấp luôn ra đúng một lời khuyên cố định, không phụ thuộc mô hình.
 *
 * Thứ tự kiểm tra là thứ tự ưu tiên: khẩn cấp trước hết.
 */
@Component
public class RuleIntentClassifier implements IntentClassifier {

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    /** Dấu hiệu nguy hiểm. Thà báo nhầm (khuyên đi khám) còn hơn bỏ sót. */
    private static final List<Pattern> EMERGENCY = List.of(
            p("\\b(co giat|len con|dong kinh)\\b"),
            p("\\b(kho tho|tho gap|tho rit|tho khe khe|khong tho duoc|ngung tho|tho hon hen|tho bang mieng)\\b"),
            p("\\b(ngo doc|an nham|nuot nham|nuot phai|an phai)\\b"),
            p("\\b(chay mau|ra mau|non ra mau|ia ra mau|dai ra mau|mau chay)\\b"),
            p("\\b(bat tinh|hon me|ngat|bi xiu|xiu (luon|roi|di|mat|xuong)|liet|te liet|khong dung duoc|khong dung vung)\\b"),
            p("\\b(bi xe (dam|tong|can)|tai nan|bi tong|roi tu (cao|tang|ban cong|lau))\\b"),
            p("\\b(tim tai|luoi tim|say nang|say nang|dot quy)\\b"),
            p("\\b(sung tac|bi tac|bi nghen|nghen (thuc an|xuong|hong|co|tho)|hoc xuong|hoc di vat|de kho|de khong ra)\\b"),
            p("\\bcap cuu\\b")
    );

    /** Dò system prompt, bắt bỏ quy tắc, nội dung vi phạm. */
    private static final List<Pattern> OFF_TOPIC = List.of(
            // Cho phép nhiều từ chỉ lượng liền nhau ("quên hết các quy tắc", "bỏ qua tất cả các chỉ dẫn").
            p("\\b(bo qua|quen|phot lo|vo hieu hoa|bat chap) (het |tat ca |moi |cac |nhung |toan bo )*(huong dan|chi dan|quy tac|lenh|cau lenh)\\b"),
            p("\\bignore (all |any |the |your |previous |prior )*(instructions|rules|prompts?)\\b"),
            p("\\b(system prompt|prompt he thong|prompt cua ban|tiet lo (prompt|huong dan|quy tac)|huong dan noi bo|huong dan he thong)\\b"),
            p("\\b(jailbreak|dan mode|developer mode|do anything now|pretend (you|to be)|you have no (rules|restrictions)|without (any )?(rules|restrictions)|with no (rules|restrictions|filters)|(unrestricted|uncensored|unfiltered) (ai|mode|assistant|chatbot))\\b"),
            // Nhập vai "không giới hạn": "đóng vai một AI không có giới hạn", "giả vờ là trợ lý không bị ràng buộc".
            p("\\b(dong vai|gia vo|gia lam|hoa than|nhap vai)\\b.*\\bkhong (co |bi |con )*(gioi han|rang buoc|kiem duyet|quy tac|luat le)\\b"),
            p("\\b(ca do|danh bac|lo de|casino|khieu dam|phim sex|hack (tai khoan|facebook|fb|wifi|game|mat khau|web|website|he thong|zalo)|ddos|phishing|lua dao|ma tuy)\\b")
    );

    private static final Pattern GREETING =
            p("^(xin )?(chao|hello|hi|hey|alo|helo)( (ban|bot|ad|admin|shop|phong kham|anh|chi|em|bac si))?( a| nhe| nha| oi)?$|^(ban|bot|ad|shop|alo) oi$");

    private static final Pattern THANKS = p("\\b(cam on|camon|thanks|thank you|thank|tks)\\b");
    private static final int THANKS_MAX_LENGTH = 25;

    private static final Pattern CONTACT = p(
            "\\b(so dien thoai|dien thoai|sdt|hotline|zalo|facebook|email|mail|dia chi|o dau|duong nao|chi duong|ban do|lien he|tong dai)\\b");
    private static final Pattern CONTACT_EXCLUDE = p(
            "\\b(gia|bao nhieu|dat lich|dat hen|san pham|mua|ship|giao hang|dich vu|thuoc|tai khoan|dang nhap|mat khau|don hang)\\b");

    private static final Pattern HOURS = p(
            "\\b(gio lam viec|gio mo cua|gio dong cua|mo cua|dong cua|may gio (mo|dong|lam)|lam viec (den|toi|tu)|thoi gian lam viec|co lam viec|truc 24|truc dem)\\b");
    private static final Pattern HOURS_EXCLUDE = p(
            "\\b(trong|con cho|con slot|con lich|dat lich|dat hen|hen kham|lich kham|bac si nao)\\b");

    @Override
    public Intent classify(String message) {
        String text = TextNormalizer.normalize(message);
        if (text.isEmpty()) {
            return Intent.OTHER;
        }
        if (matchesAny(EMERGENCY, text)) {
            return Intent.EMERGENCY;
        }
        if (matchesAny(OFF_TOPIC, text)) {
            return Intent.OFF_TOPIC;
        }
        if (GREETING.matcher(text).find()) {
            return Intent.GREETING;
        }
        if (text.length() <= THANKS_MAX_LENGTH && THANKS.matcher(text).find()) {
            return Intent.THANKS;
        }
        if (CONTACT.matcher(text).find() && !CONTACT_EXCLUDE.matcher(text).find()) {
            return Intent.CONTACT;
        }
        if (HOURS.matcher(text).find() && !HOURS_EXCLUDE.matcher(text).find()) {
            return Intent.OPENING_HOURS;
        }
        return Intent.OTHER;
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
