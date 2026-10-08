package com.vetclinic.order.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Cấu hình order.vnpay.* trong application.yml thật sự bật đúng một cổng VNPAY (không kéo theo cổng giả lập). */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "order.vnpay.enabled=true",
        "order.vnpay.tmn-code=TESTTMN1",
        "order.vnpay.hash-secret=bi-mat-chi-de-test",
        "order.vnpay.return-url=http://127.0.0.1:3000/pay/vnpay-return"
})
class VnpayWiringTest {

    @Autowired private OnlinePaymentGateway gateway;

    @Test
    void enablingVnpay_wiresTheVnpayGatewayAsTheOnlyGateway() {
        assertThat(gateway).isInstanceOf(VnpayGateway.class);
    }
}
