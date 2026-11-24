package com.vetclinic.chat.intent;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nhận ra khách chê / sửa câu trả lời trước ("sai rồi", "không phải, ý mình là…", "lạc đề").
 * Đây KHÔNG phải một nhãn {@link Intent} (bộ nhãn giữ nguyên 12 nhãn của mô hình ML) mà là một cờ
 * riêng: khi bật, ChatService không trả câu soạn sẵn mà để Gemini xin lỗi ngắn và trả lời lại.
 *
 * So khớp trên chữ đã bỏ dấu. "không phải" / "không đúng" rất dễ bắt nhầm ("không phải bác sĩ nào cũng…",
 * "không phải trả thêm phí chứ?", "chó không đứng được") nên chỉ tính khi đứng đầu tin và ngay sau là dấu câu,
 * hết câu, hoặc một từ đệm / cụm sửa lại rõ ràng.
 */
public final class CorrectionDetector {

    private CorrectionDetector() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    private static final String NO = "(khong|ko|k|hong|hok|kh)";
    private static final String ME = "(minh|toi|em|tui|to|anh|chi)";

    /** So trên dạng {@link TextNormalizer#normalize} (bỏ dấu, bỏ dấu câu). */
    private static final List<Pattern> NORMALIZED = List.of(
            // sai rồi, trả lời sai, nói sai rồi, nhầm rồi, hiểu nhầm/sai
            p("\\b(sai roi|sai bet|sai het( roi)?|tra loi sai|noi sai|ban sai|bot sai)\\b"),
            p("\\b(nham roi|nham lan roi|hieu nham|hieu sai|hieu lam)\\b"),
            p("\\b(lac de|lac chu de|tra loi lac|lac mach)\\b"),
            // ý mình là, ý tôi là, ý của mình là, mình hỏi là, ý mình muốn nói
            p("\\by (cua )?" + ME + " (la|muon noi|muon hoi|hoi la|noi la)\\b"),
            // không phải cái đó / không phải vậy / không phải điều mình hỏi — ở bất kỳ đâu
            p("\\b" + NO + " phai (cai|dieu|cau|y|thu) (do|nay|minh|toi|em)\\b"),
            p("\\b" + NO + " phai (vay|the|nhu vay|nhu the)\\b"),
            // "không phải" / "không đúng" đứng đầu: cả tin, hoặc theo sau là từ đệm / cụm sửa lại
            p("^" + NO + " (phai|dung)( (roi|dau|a|ak|nha|nhe|ban|ban oi|bot|ad|ma|nhe ban))*$"),
            p("^" + NO + " phai( (roi|dau|a|nha|nhe|ban|ban oi))* (y (" + ME + "|la)|cai (do|nay|kia)|"
                    + ME + " (hoi|muon hoi|muon noi|can|noi))\\b"),
            // trả lời chưa đúng / không đúng ý / chưa đúng câu hỏi
            p("\\b(tra loi|noi) (" + NO + "|chua) dung\\b"),
            p("\\b(" + NO + "|chua) dung (y|cau hoi|dieu) (" + ME + "|can|hoi)\\b")
    );

    /** So trên chữ bỏ dấu nhưng còn dấu câu: "không phải, …" / "không đúng. …" đứng đầu tin. */
    private static final Pattern LEADING_NEGATION_WITH_PUNCT =
            p("^\\s*" + NO + " (phai|dung)( (roi|dau|a|nha|nhe|ban))*\\s*[,.!;:…-]");

    public static boolean isCorrection(String message) {
        String text = TextNormalizer.normalize(message);
        if (text.isEmpty()) {
            return false;
        }
        for (Pattern pattern : NORMALIZED) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return LEADING_NEGATION_WITH_PUNCT.matcher(stripMarks(message)).find();
    }

    /** Bỏ dấu tiếng Việt, chữ thường, giữ dấu câu. */
    private static String stripMarks(String input) {
        if (input == null) {
            return "";
        }
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
