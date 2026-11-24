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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String BEARER = "Bearer ";
    private static final int MAX_IP_LENGTH = 45;
    /** IPv4 dạng a.b.c.d hoặc chuỗi IPv6 (có ":") — InetAddress.getByName coi là literal, không tra DNS. */
    private static final Pattern IP_LITERAL =
            Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}|[0-9A-Fa-f.]*:[0-9A-Fa-f.:]*");

    private final ChatService chatService;
    private final ChatProperties properties;
    private final int trustedProxyHops;

    /**
     * @param trustedProxyHops số proxy tin cậy đứng trước chat-service và mỗi proxy NỐI thêm IP nó thấy vào cuối
     *                         X-Forwarded-For (Spring Cloud Gateway làm vậy mặc định). Mặc định 1 = chỉ có api-gateway:
     *                         lấy phần tử cuối, tức IP của bên kết nối TCP tới gateway — khách không giả được. 0 = lấy
     *                         phần tử đầu (khách tự đặt được, chỉ dùng khi biết rõ phía trước đã xoá header của khách).
     *                         Đặt qua app.chat.rate-limit.trusted-proxy-hops (env CHAT_TRUSTED_PROXY_HOPS).
     */
    public ChatController(ChatService chatService, ChatProperties properties,
                          @Value("${app.chat.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.chatService = chatService;
        this.properties = properties;
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
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

    private Caller callerOf(Authentication authentication, HttpServletRequest http) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            String header = http.getHeader(HttpHeaders.AUTHORIZATION);
            String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
            return Caller.user(user.userId().toString(), token);
        }
        return Caller.guest(clientIp(http.getHeader("X-Forwarded-For"), http.getRemoteAddr(), trustedProxyHops));
    }

    /**
     * IP dùng làm khoá hạn mức khách vãng lai.
     * <p>
     * Chỉ tin X-Forwarded-For khi bên kết nối TCP tới chat-service (remoteAddr) là địa chỉ nội bộ — loopback hoặc
     * dải riêng 10/8, 172.16/12, 192.168/16, tức api-gateway trong mạng Docker. Ai gọi thẳng cổng 8092 từ Internet
     * thì remoteAddr là IP công khai của họ: bỏ qua header (họ tự đặt được) và dùng luôn remoteAddr.
     * <p>
     * Khi tin: hops = N lấy phần tử thứ N từ phải (do proxy tin cậy xa nhất ghi); header ít phần tử hơn N (không qua
     * đủ proxy) thì lấy phần tử đầu. hops = 0 lấy phần tử đầu — khách giả được. Lưu ý: rewrite /api của Next.js
     * KHÔNG ghi IP trình duyệt vào X-Forwarded-For (chỉ chuyển nguyên header khách gửi), nên với hops = 1 mọi khách
     * vãng lai đi qua web dùng chung một khoá là IP container frontend; trần theo ngày vẫn là lớp chặn cuối.
     */
    static String clientIp(String forwarded, String remoteAddr, int trustedProxyHops) {
        String ip = remoteAddr == null ? "" : remoteAddr.trim();
        String[] hops = forwarded == null ? new String[0] : forwarded.split(",");
        if (hops.length > 0 && isInternalPeer(ip)) { // "," cho mảng rỗng: trước đây lỗi ArrayIndexOutOfBounds → HTTP 500
            int index = trustedProxyHops <= 0 ? 0 : Math.max(0, hops.length - trustedProxyHops);
            String candidate = hops[index].trim();
            if (!candidate.isEmpty()) {
                ip = candidate;
            }
        }
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }

    /** Loopback hoặc dải IPv4 riêng (RFC 1918). Không tra DNS: chuỗi không phải IP literal thì coi là không tin. */
    static boolean isInternalPeer(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isEmpty() || !IP_LITERAL.matcher(remoteAddr).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
