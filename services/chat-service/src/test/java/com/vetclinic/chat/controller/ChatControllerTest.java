package com.vetclinic.chat.controller;

import com.vetclinic.chat.dto.ChatResponse;
import com.vetclinic.chat.exception.ChatDisabledException;
import com.vetclinic.chat.exception.RateLimitExceededException;
import com.vetclinic.chat.service.Caller;
import com.vetclinic.chat.service.ChatService;
import com.vetclinic.chat.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final String BODY = "{\"messages\":[{\"role\":\"user\",\"content\":\"giá tắm spa\"}]}";

    @Test
    void statusIsPublicChatbotIsAlwaysOpenAndReportsAiConfiguration() throws Exception {
        when(chatService.isAiEnabled()).thenReturn(false);

        mockMvc.perform(get("/chat/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.aiEnabled").value(false));
    }

    @Test
    void guestCanChatWithoutToken() throws Exception {
        when(chatService.reply(any(), any())).thenReturn(new ChatResponse("Xin chào", ChatResponse.AI));

        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Forwarded-For", "9.9.9.9, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Xin chào"))
                .andExpect(jsonPath("$.source").value("ai"));

        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(chatService).reply(any(), caller.capture());
        assertThat(caller.getValue().loggedIn()).isFalse();
        assertThat(caller.getValue().rateKey()).isEqualTo("ip:9.9.9.9");
        assertThat(caller.getValue().bearerToken()).isNull();
    }

    @Test
    void loggedInCustomerIsRecognisedAndTokenForwardedToService() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtSupport.token(jwtSecret, userId, List.of("CUSTOMER"));
        when(chatService.reply(any(), any())).thenReturn(new ChatResponse("ok", ChatResponse.AI));

        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(chatService).reply(any(), caller.capture());
        assertThat(caller.getValue().loggedIn()).isTrue();
        assertThat(caller.getValue().rateKey()).isEqualTo("user:" + userId);
        assertThat(caller.getValue().bearerToken()).isEqualTo(token);
    }

    @Test
    void invalidTokenIsTreatedAsGuestNotRejected() throws Exception {
        when(chatService.reply(any(), any())).thenReturn(new ChatResponse("ok", ChatResponse.AI));

        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer rac-roi.khong-hop-le.token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(chatService).reply(any(), caller.capture());
        assertThat(caller.getValue().loggedIn()).isFalse();
    }

    @Test
    void lastMessageMustBeFromTheCustomer() throws Exception {
        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"a\"},{\"role\":\"assistant\",\"content\":\"b\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tin nhắn cuối cùng phải là của khách."));
        verify(chatService, never()).reply(any(), any());
    }

    @Test
    void tooLongMessageIsRejected() throws Exception {
        String longText = "a".repeat(1001);
        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"" + longText + "\"}]}"))
                .andExpect(status().isBadRequest());
        verify(chatService, never()).reply(any(), any());
    }

    @Test
    void emptyOrMalformedBodiesAreBadRequests() throws Exception {
        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"system\",\"content\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content("khong phai json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitBecomes429WithRetryAfter() throws Exception {
        when(chatService.reply(any(), any())).thenThrow(new RateLimitExceededException("Thử lại sau 30 giây.", 30));

        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.message").value("Thử lại sau 30 giây."));
    }

    @Test
    void disabledChatbotBecomes503() throws Exception {
        when(chatService.reply(any(), any())).thenThrow(new ChatDisabledException());

        mockMvc.perform(post("/chat/messages").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
