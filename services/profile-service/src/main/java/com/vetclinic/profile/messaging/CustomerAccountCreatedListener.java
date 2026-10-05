package com.vetclinic.profile.messaging;

import com.vetclinic.profile.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * CN-19: nhân viên mở tài khoản cho khách tại quầy và nhập số điện thoại luôn.
 *
 * Tạo sẵn hồ sơ kèm số điện thoại, thay vì đợi khách tự mở trang hồ sơ để điền — khách vãng lai
 * gần như không bao giờ làm việc đó, mà phòng khám thì cần gọi lại được.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerAccountCreatedListener {

    private final CustomerProfileService customerProfileService;

    @RabbitListener(queues = RabbitMQConfig.CUSTOMER_CREATED_QUEUE)
    public void onCustomerAccountCreated(CustomerAccountCreatedEvent event) {
        customerProfileService.ensureProfileWithPhone(event.userId(), event.phone());
        log.info("Đã tạo hồ sơ khách cho userId={} (mở tại quầy)", event.userId());
    }
}
