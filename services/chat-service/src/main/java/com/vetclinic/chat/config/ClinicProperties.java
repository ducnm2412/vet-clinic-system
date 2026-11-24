package com.vetclinic.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thông tin phòng khám cho câu trả lời soạn sẵn và system prompt. */
@ConfigurationProperties(prefix = "app.clinic")
public record ClinicProperties(
        String name,
        String address,
        String phone,
        String email,
        String hours,
        String zalo
) {
}
