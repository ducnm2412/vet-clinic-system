package com.vetclinic.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.dto.ChatMessage;
import com.vetclinic.chat.dto.ChatResponse;
import com.vetclinic.chat.exception.GeminiUnavailableException;
import com.vetclinic.chat.gemini.GeminiClient;
import com.vetclinic.chat.intent.CannedReplies;
import com.vetclinic.chat.intent.ClinicKeywords;
import com.vetclinic.chat.intent.CorrectionDetector;
import com.vetclinic.chat.intent.Intent;
import com.vetclinic.chat.intent.IntentClassifier;
import com.vetclinic.chat.intent.RuleIntentClassifier;
import com.vetclinic.chat.intent.TextNormalizer;
import com.vetclinic.chat.tool.ToolDefinitions;
import com.vetclinic.chat.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Luồng xử lý một lượt chat:
 * 1. Phân loại ý định (CompositeIntentClassifier: luật + ML). Khẩn cấp, chào hỏi, cảm ơn, liên hệ,
 *    giờ mở cửa, lạc đề → câu soạn sẵn, không tốn hạn mức.
 * 2. Còn lại (PRICE, SERVICE_INFO, PRODUCT, BOOKING, CARE_ADVICE, OTHER) cần Gemini; nhãn được đưa vào
 *    system prompt làm gợi ý. Gemini: chưa có API key thì trả câu dự phòng, nếu có thì trừ hạn mức, rồi vòng lặp "Gemini yêu cầu công cụ →
 *    chạy công cụ → trả kết quả" tối đa maxToolRounds vòng; vòng cuối không cho dùng công cụ để
 *    buộc Gemini trả lời bằng chữ.
 * 3. Gemini lỗi hoặc không ra chữ → câu dự phòng, không để khách nhìn thấy lỗi kỹ thuật.
 *
 * Ngoại lệ ở bước 1 (EMERGENCY luôn thắng cả hai):
 * - Bảo vệ ngữ cảnh: tin ≤ 4 từ ngay sau tin của bot ("có", "ở đâu", "còn mèo thì sao?") chỉ được trả soạn
 *   sẵn khi luật nhận OFF_TOPIC hoặc THANKS; còn lại đi Gemini KHÔNG kèm gợi ý nhãn, để Gemini hiểu theo lịch sử.
 * - Khách chê / sửa ({@link CorrectionDetector}): không trả soạn sẵn (trừ OFF_TOPIC theo luật); prompt thêm dòng
 *   yêu cầu xin lỗi ngắn rồi trả lời lại.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Lý do Gemini dừng vì bộ lọc an toàn — khác với "lỗi", trả câu từ chối nhẹ nhàng. */
    private static final Set<String> BLOCKED_FINISH_REASONS =
            Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION");

    /** Tin của khách tối đa bao nhiêu từ thì coi là "tin ngắn" cho bảo vệ ngữ cảnh. */
    static final int SHORT_REPLY_MAX_WORDS = 4;

    private final IntentClassifier intentClassifier;
    private final RuleIntentClassifier ruleClassifier;
    private final CannedReplies cannedReplies;
    private final RateLimiter rateLimiter;
    private final GeminiClient gemini;
    private final ToolExecutor toolExecutor;
    private final PromptBuilder promptBuilder;
    private final ChatProperties properties;
    private final ObjectMapper mapper;

    public ChatService(IntentClassifier intentClassifier, RuleIntentClassifier ruleClassifier,
                       CannedReplies cannedReplies, RateLimiter rateLimiter,
                       GeminiClient gemini, ToolExecutor toolExecutor, PromptBuilder promptBuilder,
                       ChatProperties properties, ObjectMapper mapper) {
        this.intentClassifier = intentClassifier;
        this.ruleClassifier = ruleClassifier;
        this.cannedReplies = cannedReplies;
        this.rateLimiter = rateLimiter;
        this.gemini = gemini;
        this.toolExecutor = toolExecutor;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Đã cấu hình Gemini chưa. Chưa có thì chỉ trả lời được các câu soạn sẵn. */
    public boolean isAiEnabled() {
        return gemini.isEnabled();
    }

    /** @param messages hội thoại theo thứ tự thời gian; tin cuối cùng phải là của khách (controller đã kiểm) */
    public ChatResponse reply(List<ChatMessage> messages, Caller caller) {
        String latest = messages.get(messages.size() - 1).content();

        boolean shortReplyToBot = isShortReplyToBot(messages, latest);
        // Tin ngắn ngay sau tin bot (B1): nhãn chỉ còn dùng để bắt EMERGENCY (luật + A1 đã lo) và THANKS (phải được
        // luật xác nhận), nên không gọi tầng A2 qua mạng — tránh chờ Gemini embedContent vô ích.
        Intent intent = intentClassifier.classify(latest, !shortReplyToBot);
        // Khẩn cấp luôn thắng, kể cả khi tin ngắn hay khách đang chê câu trả lời trước.
        if (intent == Intent.EMERGENCY) {
            return new ChatResponse(cannedReplies.forIntent(intent), ChatResponse.RULE);
        }

        boolean correction = CorrectionDetector.isCorrection(latest);
        Intent hint = intent;
        if (correction || shortReplyToBot) {
            // Nhãn của tin ngắn / tin sửa lại không đáng tin: chỉ giữ câu soạn sẵn mà luật xác nhận
            // (OFF_TOPIC; THANKS khi không phải tin sửa lại), còn lại để Gemini hiểu theo lịch sử.
            Intent ruleIntent = ruleClassifier.classify(latest);
            if (ruleIntent == Intent.OFF_TOPIC) {
                return new ChatResponse(cannedReplies.forIntent(ruleIntent), ChatResponse.RULE);
            }
            if (!correction && intent == Intent.THANKS && ruleIntent == Intent.THANKS) {
                return new ChatResponse(cannedReplies.forIntent(intent), ChatResponse.RULE);
            }
            if (shortReplyToBot) {
                hint = null;
            }
        } else if (intent == Intent.OFF_TOPIC && ruleClassifier.classify(latest) != Intent.OFF_TOPIC
                && ClinicKeywords.mentions(latest)) {
            // OFF_TOPIC chỉ do mô hình ML đoán (luật regex dò prompt/vi phạm không xác nhận) mà câu lại nói về
            // phòng khám ("bên bạn có dịch vụ nào?"): để Gemini trả lời thay vì chặn nhầm.
            hint = null;
        } else if (intent.hasCannedReply()) {
            return new ChatResponse(cannedReplies.forIntent(intent), ChatResponse.RULE);
        }

        if (!gemini.isEnabled()) {
            // Chưa cấu hình GEMINI_API_KEY: chatbot vẫn mở ở chế độ cơ bản (chỉ có câu soạn sẵn).
            return new ChatResponse(cannedReplies.fallback(), ChatResponse.FALLBACK);
        }
        rateLimiter.acquire(caller);

        try {
            return askGemini(messages, caller, hint, correction);
        } catch (GeminiUnavailableException e) {
            log.warn("Gemini không phản hồi, trả câu dự phòng");
            return new ChatResponse(cannedReplies.fallback(), ChatResponse.FALLBACK);
        }
    }

    /**
     * Tin mới của khách ngắn (≤ {@value #SHORT_REPLY_MAX_WORDS} từ sau khi chuẩn hoá) và ngay trước đó là tin
     * của bot: nhiều khả năng là câu trả lời theo mạch ("có", "còn mèo thì sao?", "ở đâu").
     */
    static boolean isShortReplyToBot(List<ChatMessage> messages, String latest) {
        if (messages.size() < 2 || messages.get(messages.size() - 2).fromUser()) {
            return false;
        }
        String text = TextNormalizer.normalize(latest);
        return !text.isEmpty() && text.split(" ").length <= SHORT_REPLY_MAX_WORDS;
    }

    private ChatResponse askGemini(List<ChatMessage> messages, Caller caller, Intent hint, boolean correction) {
        String systemPrompt = promptBuilder.build(hint, correction, caller.loggedIn());
        ArrayNode contents = toContents(messages);
        ArrayNode tools = ToolDefinitions.declarations(mapper);
        int maxRounds = properties.maxToolRounds();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.replyTimeoutSeconds());

        for (int round = 0; round <= maxRounds; round++) {
            if (System.nanoTime() > deadline) {
                log.warn("Quá hạn chót trả lời, trả câu dự phòng");
                break;
            }
            boolean allowTools = round < maxRounds;
            JsonNode response = gemini.generate(buildBody(systemPrompt, contents, allowTools ? tools : null));

            if (response.path("promptFeedback").hasNonNull("blockReason")) {
                return new ChatResponse(cannedReplies.blocked(), ChatResponse.FALLBACK);
            }
            JsonNode candidate = response.path("candidates").path(0);
            JsonNode content = candidate.path("content");
            List<JsonNode> calls = functionCalls(content);

            if (calls.isEmpty()) {
                String text = extractText(content);
                if (!text.isBlank()) {
                    return new ChatResponse(text, ChatResponse.AI);
                }
                boolean blocked = BLOCKED_FINISH_REASONS.contains(candidate.path("finishReason").asText(""));
                return new ChatResponse(blocked ? cannedReplies.blocked() : cannedReplies.fallback(),
                        ChatResponse.FALLBACK);
            }

            if (!allowTools) {
                // Đã hết vòng mà Gemini vẫn đòi công cụ.
                break;
            }

            // Giữ nguyên lượt trả lời của mô hình (gồm cả thoughtSignature nếu có) rồi nối kết quả công cụ.
            ObjectNode modelTurn = content.deepCopy();
            modelTurn.put("role", "model");
            contents.add(modelTurn);

            ArrayNode responseParts = mapper.createArrayNode();
            for (JsonNode call : calls) {
                String name = call.path("name").asText("");
                ObjectNode result = toolExecutor.execute(name, call.path("args"), caller);
                ObjectNode functionResponse = mapper.createObjectNode();
                functionResponse.put("name", name);
                functionResponse.set("response", mapper.createObjectNode().set("result", result));
                responseParts.add(mapper.createObjectNode().set("functionResponse", functionResponse));
            }
            ObjectNode userTurn = mapper.createObjectNode();
            userTurn.put("role", "user");
            userTurn.set("parts", responseParts);
            contents.add(userTurn);
        }
        return new ChatResponse(cannedReplies.fallback(), ChatResponse.FALLBACK);
    }

    private ObjectNode buildBody(String systemPrompt, ArrayNode contents, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode system = mapper.createObjectNode();
        system.set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", systemPrompt)));
        body.set("systemInstruction", system);
        body.set("contents", contents);
        if (tools != null) {
            body.set("tools", tools);
        }
        ObjectNode generation = mapper.createObjectNode();
        generation.put("temperature", 0.4);
        generation.put("maxOutputTokens", 2048);
        body.set("generationConfig", generation);
        return body;
    }

    /**
     * Đổi lịch sử của frontend sang "contents" của Gemini: chỉ lấy tin gần nhất, user/model xen kẽ
     * (gộp các tin liền nhau cùng vai), và phải bắt đầu bằng tin của khách.
     */
    ArrayNode toContents(List<ChatMessage> messages) {
        int from = Math.max(0, messages.size() - properties.maxHistoryMessages());
        ArrayNode contents = mapper.createArrayNode();
        String lastRole = null;

        for (ChatMessage message : messages.subList(from, messages.size())) {
            String role = message.fromUser() ? "user" : "model";
            if (contents.isEmpty() && "model".equals(role)) {
                continue;
            }
            if (role.equals(lastRole)) {
                ObjectNode part = (ObjectNode) contents.get(contents.size() - 1).path("parts").get(0);
                part.put("text", part.path("text").asText() + "\n" + message.content());
            } else {
                ObjectNode turn = mapper.createObjectNode();
                turn.put("role", role);
                turn.set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", message.content())));
                contents.add(turn);
                lastRole = role;
            }
        }
        return contents;
    }

    private static List<JsonNode> functionCalls(JsonNode content) {
        List<JsonNode> calls = new java.util.ArrayList<>();
        for (JsonNode part : content.path("parts")) {
            if (part.has("functionCall")) {
                calls.add(part.path("functionCall"));
            }
        }
        return calls;
    }

    private static String extractText(JsonNode content) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            if (part.has("text") && !part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString().trim();
    }
}
