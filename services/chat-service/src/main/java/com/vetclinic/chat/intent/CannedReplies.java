package com.vetclinic.chat.intent;

import com.vetclinic.chat.config.ClinicProperties;
import org.springframework.stereotype.Component;

/**
 * Câu trả lời soạn sẵn. Chỉ dùng chữ thuần và liên kết dạng [chữ](/đường-dẫn) — widget không
 * hiển thị markdown nào khác (không in đậm, không HTML).
 */
@Component
public class CannedReplies {

    private final ClinicProperties clinic;

    public CannedReplies(ClinicProperties clinic) {
        this.clinic = clinic;
    }

    /**
     * Câu soạn sẵn cho ý định có {@link Intent#hasCannedReply()}. Các ý định đi Gemini không có câu
     * soạn sẵn riêng (ChatService không gọi hàm này cho chúng); nếu bị gọi thì trả câu dự phòng thay vì ném lỗi.
     */
    public String forIntent(Intent intent) {
        return switch (intent) {
            case EMERGENCY -> emergency();
            case OFF_TOPIC -> offTopic();
            case GREETING -> greeting();
            case THANKS -> thanks();
            case CONTACT -> contact();
            case OPENING_HOURS -> openingHours();
            case PRICE, SERVICE_INFO, PRODUCT, BOOKING, CARE_ADVICE, OTHER -> fallback();
        };
    }

    public String emergency() {
        return "Dấu hiệu bạn mô tả có thể rất nguy hiểm cho bé, và mình không thể chẩn đoán qua tin nhắn. "
                + "Hãy đưa bé đến phòng khám ngay, hoặc gọi hotline cấp cứu 24/7: " + clinic.phone() + ".\n\n"
                + "Địa chỉ: " + clinic.address() + ".\n\n"
                + "Trên đường đi, giữ bé yên tĩnh và ấm, không tự cho uống thuốc hay ép ăn uống. "
                + "Nên gọi trước để phòng khám chuẩn bị đón bé.";
    }

    public String offTopic() {
        return "Mình là trợ lý của " + clinic.name() + ", chỉ hỗ trợ các câu hỏi về phòng khám thú y: "
                + "dịch vụ, giá tham khảo, sản phẩm, đặt lịch và cách chăm sóc thú cưng cơ bản. "
                + "Bạn cần hỏi gì về bé nhà mình không?";
    }

    public String greeting() {
        return "Xin chào, mình là trợ lý của " + clinic.name() + ". Mình có thể giúp bạn xem dịch vụ và giá tham khảo, "
                + "tìm sản phẩm, hỏi giờ khám còn trống hoặc hướng dẫn đặt lịch. Bạn cần hỗ trợ gì ạ?";
    }

    public String thanks() {
        return "Rất vui được giúp bạn! Cần gì thêm cứ nhắn cho mình nhé.";
    }

    public String contact() {
        return "Thông tin liên hệ của " + clinic.name() + ":\n"
                + "- Địa chỉ: " + clinic.address() + "\n"
                + "- Hotline: " + clinic.phone() + " (cấp cứu 24/7 qua hotline)\n"
                + "- Giờ mở cửa: " + clinic.hours() + "\n"
                + "- Email: " + clinic.email() + "\n"
                + "- Zalo: " + clinic.zalo() + "\n\n"
                + "Xem thêm tại [trang Liên hệ](/contact) hoặc [đặt lịch khám online](/appointments/create).";
    }

    public String openingHours() {
        return clinic.name() + " mở cửa " + clinic.hours() + ". Cấp cứu 24/7 qua hotline " + clinic.phone() + ".\n\n"
                + "Muốn đặt lịch, bạn vào [trang đặt lịch](/appointments/create) để chọn ngày và giờ còn trống.";
    }

    /** Dùng khi Gemini không phản hồi được: vẫn chỉ cho khách đường đi tiếp thay vì báo lỗi trơ trọi. */
    public String fallback() {
        return "Xin lỗi, hiện mình chưa trả lời được câu này. Bạn có thể xem [dịch vụ](/services), "
                + "[sản phẩm](/products), [đặt lịch khám](/appointments/create), hoặc gọi hotline "
                + clinic.phone() + " để được hỗ trợ trực tiếp.";
    }

    /** Gemini từ chối trả lời (bộ lọc an toàn) hoặc trả rỗng. */
    public String blocked() {
        return "Mình chưa thể trả lời nội dung này. Bạn thử hỏi cách khác, hoặc gọi hotline "
                + clinic.phone() + " để được hỗ trợ trực tiếp nhé.";
    }
}
