package com.vetclinic.chat.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /** Múi giờ của phòng khám: "hôm nay", "ngày mai" và trần theo ngày đều tính theo giờ Việt Nam. */
    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(CLINIC_ZONE);
    }

    /**
     * RestClient gọi booking-service / product-service theo tên service qua Eureka
     * (http://booking-service/...). Timeout ngắn: chatbot không được treo chờ service khác.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder internalRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return RestClient.builder().requestFactory(factory);
    }
}
