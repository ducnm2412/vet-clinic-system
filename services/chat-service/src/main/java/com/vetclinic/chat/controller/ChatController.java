package com.vetclinic.chat.controller;

import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.dto.ChatMessage;
import com.vetclinic.chat.dto.ChatRequest;
import com.vetclinic.chat.dto.ChatResponse;
import com.vetclinic.chat.dto.ChatStatusResponse;
import com.vetclinic.chat.security.jwt.AuthenticatedUser;
import com.vetclinic.chat.service.Caller;
import com.vetclinic.chat.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String BEARER = "Bearer ";
    private static final int MAX_IP_LENGTH = 45;

    private final ChatService chatService;
    private final ChatProperties properties;

    public ChatController(ChatService chatService, ChatProperties properties) {
        this.chatService = chatService;
        this.properties = properties;
    }

    /** Frontend gọi để biết có hiện nút chat không, và Gemini đã được cấu hình chưa. */
    @GetMapping("/status")
    public ChatStatusResponse status() {
        return new ChatStatusResponse(true, chatService.isAiEnabled());
    }

    @PostMapping("/messages")
    public ChatResponse send(@Valid @RequestBody ChatRequest request, Authentication authentication,
                             HttpServletRequest http) {
        List<ChatMessage> messages = request.messages();
        validate(messages);
        return chatService.reply(messages, callerOf(authentication, http));
    }

    private void validate(List<ChatMessage> messages) {
        if (!messages.get(messages.size() - 1).fromUser()) {
            throw new IllegalArgumentException("Tin nhắn cuối cùng phải là của khách.");
        }
        int maxChars = properties.maxMessageChars();
        for (ChatMessage message : messages) {
            if (message.content().length() > maxChars) {
                throw new IllegalArgumentException("Mỗi tin nhắn tối đa " + maxChars + " ký tự.");
            }
        }
    }

    private static Caller callerOf(Authentication authentication, HttpServletRequest http) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            String header = http.getHeader(HttpHeaders.AUTHORIZATION);
            String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
            return Caller.user(user.userId().toString(), token);
        }
        return Caller.guest(clientIp(http));
    }

    /**
     * Trong Docker, remoteAddr luôn là gateway, nên lấy IP khách từ X-Forwarded-For (phần tử đầu).
     * Header này giả được; trần tổng số lượt mỗi ngày trong RateLimiter là lớp chặn cuối cùng.
     */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : http.getRemoteAddr();
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }
}
