package com.vetclinic.pet.messaging;

import com.vetclinic.pet.service.PetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Tài khoản khách bị xoá thì hồ sơ thú cưng của họ đi theo.
 *
 * Hồi thú cưng còn nằm trong profile_db, việc này do khoá ngoại ON DELETE CASCADE lo. Sang
 * database riêng thì không còn khoá ngoại xuyên service nữa, nên phải nghe sự kiện.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedListener {

    private final PetService petService;

    @RabbitListener(queues = RabbitMQConfig.USER_DELETED_QUEUE)
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Nhận sự kiện user.deleted cho userId={}", event.userId());
        petService.deleteAllOf(event.userId());
    }
}
