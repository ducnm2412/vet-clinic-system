package com.vetclinic.chat.intent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectionDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // có dấu
            "sai rồi",
            "Sai rồi bạn ơi",
            "bạn trả lời sai rồi",
            "không phải, ý mình là tiêm cho mèo",
            "Không phải. Mình hỏi giá cho mèo",
            "không phải",
            "không phải đâu",
            "không phải cái đó",
            "mình hỏi giá mà, không phải cái đó",
            "ý mình là giá tắm cho mèo",
            "ý tôi là chi nhánh khác",
            "lạc đề rồi",
            "bạn trả lời lạc đề quá",
            "không đúng",
            "không đúng rồi",
            "trả lời không đúng",
            "chưa đúng ý mình",
            "nhầm rồi",
            "bạn hiểu nhầm rồi",
            "không phải vậy",
            "không phải ý mình hỏi",
            // không dấu / viết tắt
            "sai roi",
            "khong phai, y minh la tiem cho meo",
            "ko phai cai do",
            "k phai dau",
            "y minh la gia tam",
            "y toi la cho con",
            "lac de roi",
            "khong dung",
            "tra loi sai",
            "nham roi ban"
    })
    void detectsCorrections(String message) {
        assertThat(CorrectionDetector.isCorrection(message)).as(message).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "không phải bác sĩ nào cũng khám mèo đúng không?",
            "không phải lúc nào cũng có lịch trống à?",
            "không phải trả thêm phí chứ?",
            "khong phai tra them phi chu",
            "khong phai bac si nao cung kham duoc",
            "có phải đặt cọc không?",
            "chó nhà mình không đứng được",
            "mèo không dùng được thuốc này",
            "không dùng cái vòng cổ này được không",
            "giá tiêm phòng cho mèo bao nhiêu",
            "có",
            "ok cảm ơn",
            "đúng rồi",
            "ý kiến của bác sĩ thế nào",
            "chó bị sai khớp chân",
            "nhầm lịch hẹn thì đổi được không"
    })
    void ignoresOrdinaryMessages(String message) {
        assertThat(CorrectionDetector.isCorrection(message)).as(message).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "?!"})
    void blankIsNotACorrection(String message) {
        assertThat(CorrectionDetector.isCorrection(message)).isFalse();
    }
}
