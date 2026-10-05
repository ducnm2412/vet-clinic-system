package com.vetclinic.profile;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * VD-12: chặn test chạy vào database thật.
 *
 * Một số test dọn dẹp bằng deleteAll() và không nằm trong transaction. Ngày 13/09/2026 chạy
 * nhầm bộ test của booking-service vào `booking_db` thật đã xoá sạch lịch hẹn, phải khôi phục
 * từ bản sao lưu. Tên database phải kết thúc bằng `_test`, nếu không thì dừng ngay — cả hai
 * mốc kiểm đều xảy ra TRƯỚC khi Spring mở kết nối đầu tiên:
 *
 * - {@code ApplicationEnvironmentPreparedEvent}: bắt cấu hình từ application.yml, biến môi trường
 *   và system property (pom.xml khai DB_NAME ở đây).
 * - {@code ApplicationPreparedEvent}: bắt thêm properties khai ngay trên {@code @SpringBootTest},
 *   loại này chỉ được nạp vào môi trường sau mốc trên.
 *
 * Đăng ký trong src/test/resources/META-INF/spring.factories nên áp dụng cho MỌI @SpringBootTest
 * của service, không phải nhớ thêm annotation nào.
 */
public class TestDatabaseGuard implements ApplicationListener<ApplicationEvent> {

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent e) {
            check(e.getEnvironment());
        } else if (event instanceof ApplicationPreparedEvent e) {
            check(e.getApplicationContext().getEnvironment());
        }
    }

    private static void check(Environment environment) {
        checkUrl(environment.getProperty("spring.datasource.url"));
    }

    /** Package-private để test được; url null nghĩa là test này không dùng database. */
    static void checkUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String database = url.replaceAll("[?;].*$", "");
        database = database.substring(database.lastIndexOf('/') + 1);
        if (!database.endsWith("_test")) {
            throw new IllegalStateException(
                    "TU CHOI CHAY TEST: dang tro vao database '" + database + "'. Test cua du an nay"
                            + " co the xoa sach du lieu (VD-12), nen chi chay voi database ket thuc bang"
                            + " '_test'. Vi du: DB_NAME=profile_db_test. Tao database test:"
                            + " bash scripts/create-test-databases.sh");
        }
    }
}
