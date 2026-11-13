package com.vetclinic.notification.controller;

import com.vetclinic.notification.repository.NotificationRepository;
import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.support.TestJwtSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NotificationService service;
    @Autowired
    private NotificationRepository repository;
    @Value("${jwt.secret}")
    private String secret;

    private final UUID me = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private String bearer(UUID user) {
        return "Bearer " + TestJwtSupport.token(secret, user, List.of("CUSTOMER"));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyMyNotifications_newestFirst_withUnreadCount() throws Exception {
        service.create(me, "CONFIRMED", "Cũ", "m", "/appointments/1");
        service.create(me, "PROPOSAL", "Mới", "m", "/appointments/2");
        service.create(someoneElse, "CONFIRMED", "Của người khác", "m", null);

        mvc.perform(get("/notifications").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Mới"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].link").value("/appointments/2"));
    }

    @Test
    void markRead_dropsTheUnreadCount() throws Exception {
        var n = service.create(me, "CONFIRMED", "A", "m", null);
        service.create(me, "CONFIRMED", "B", "m", null);

        mvc.perform(put("/notifications/" + n.getId() + "/read").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mvc.perform(get("/notifications").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void cannotMarkSomeoneElsesNotification() throws Exception {
        var theirs = service.create(someoneElse, "CONFIRMED", "A", "m", null);

        mvc.perform(put("/notifications/" + theirs.getId() + "/read").header("Authorization", bearer(me)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/notifications").header("Authorization", bearer(someoneElse)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void readAll_marksOnlyMine() throws Exception {
        service.create(me, "CONFIRMED", "A", "m", null);
        service.create(me, "PROPOSAL", "B", "m", null);
        service.create(someoneElse, "CONFIRMED", "C", "m", null);

        mvc.perform(put("/notifications/read-all").header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/notifications").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/notifications").header("Authorization", bearer(someoneElse)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void listIsCappedAtThirty() throws Exception {
        for (int i = 0; i < NotificationService.MAX_LIST + 5; i++) {
            service.create(me, "CONFIRMED", "T" + i, "m", null);
        }

        mvc.perform(get("/notifications").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.items.length()").value(NotificationService.MAX_LIST))
                .andExpect(jsonPath("$.unreadCount").value(NotificationService.MAX_LIST + 5));
    }
}
