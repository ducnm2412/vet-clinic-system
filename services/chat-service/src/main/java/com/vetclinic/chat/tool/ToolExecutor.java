package com.vetclinic.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vetclinic.chat.service.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Chạy công cụ Gemini yêu cầu. Chỉ có 4 công cụ chỉ đọc; tham số do mô hình sinh ra luôn được kiểm
 * tra lại ở đây, vì mô hình có thể bị dụ (prompt injection) gửi tham số bậy. Không bao giờ ném lỗi:
 * lỗi trả về thành {"error": "..."} để Gemini giải thích lại cho khách.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    static final int MAX_KEYWORD_LENGTH = 100;
    static final int MAX_DATE_AHEAD_DAYS = 60;
    private static final int MAX_SERVICES = 20;
    private static final int MAX_PRODUCTS = 8;
    private static final int MAX_CATEGORIES = 30;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ToolExecutor(RestClient.Builder internalRestClientBuilder, ObjectMapper mapper, Clock clock) {
        this.client = internalRestClientBuilder.build();
        this.mapper = mapper;
        this.clock = clock;
    }

    public ObjectNode execute(String name, JsonNode args, Caller caller) {
        if (name == null || !ToolDefinitions.NAMES.contains(name)) {
            return error("Công cụ không tồn tại.");
        }
        JsonNode safeArgs = args == null || args.isNull() ? mapper.createObjectNode() : args;
        try {
            return switch (name) {
                case ToolDefinitions.LIST_CLINIC_SERVICES -> listClinicServices();
                case ToolDefinitions.GET_CLINIC_HOURS -> getClinicHours(safeArgs, caller);
                case ToolDefinitions.SEARCH_PRODUCTS -> searchProducts(safeArgs);
                default -> listCategories();
            };
        } catch (RestClientResponseException e) {
            log.warn("Công cụ {} lỗi HTTP {}", name, e.getStatusCode().value());
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return loginRequired();
            }
            return error("Không lấy được dữ liệu lúc này.");
        } catch (RuntimeException e) {
            log.warn("Công cụ {} lỗi {}", name, e.getClass().getSimpleName());
            return error("Không lấy được dữ liệu lúc này.");
        }
    }

    // ---------- list_clinic_services ----------

    private ObjectNode listClinicServices() {
        JsonNode body = client.get().uri("http://booking-service/booking/services")
                .retrieve().body(JsonNode.class);
        ArrayNode services = mapper.createArrayNode();
        for (JsonNode item : asItems(body)) {
            if (services.size() >= MAX_SERVICES) {
                break;
            }
            if (item.has("active") && !item.path("active").asBoolean(true)) {
                continue;
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("name", item.path("name").asText(""));
            out.put("description", item.path("description").asText(""));
            out.set("durationMinutes", item.path("durationMinutes"));
            out.set("referencePriceVnd", item.path("referencePrice"));
            String slug = item.path("slug").asText("");
            if (!slug.isBlank()) {
                out.put("link", "/services/" + slug);
            }
            services.add(out);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("services", services);
        return result;
    }

    // ---------- get_clinic_hours ----------

    private ObjectNode getClinicHours(JsonNode args, Caller caller) {
        // clinic-hours của booking-service yêu cầu đăng nhập; khách vãng lai thì hướng dẫn đăng nhập.
        if (caller == null || !caller.loggedIn() || caller.bearerToken() == null) {
            return loginRequired();
        }
        LocalDate date;
        try {
            date = LocalDate.parse(args.path("date").asText(""));
        } catch (DateTimeParseException e) {
            return error("Ngày không hợp lệ, cần định dạng yyyy-MM-dd.");
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DATE_AHEAD_DAYS))) {
            return error("Chỉ xem được giờ trống từ hôm nay đến " + MAX_DATE_AHEAD_DAYS + " ngày tới.");
        }

        JsonNode body = client.get()
                .uri("http://booking-service/booking/clinic-hours?date={date}", date.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.bearerToken())
                .retrieve().body(JsonNode.class);

        ArrayNode available = mapper.createArrayNode();
        for (JsonNode slot : asItems(body)) {
            if (slot.path("available").asBoolean(false)) {
                ObjectNode out = mapper.createObjectNode();
                out.put("time", trimSeconds(slot.path("startTime").asText("")));
                out.set("remaining", slot.path("remaining"));
                available.add(out);
            }
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("date", date.toString());
        result.set("availableTimes", available);
        if (available.isEmpty()) {
            result.put("note", "Không còn giờ trống, hoặc ngày này ngoài thời gian nhận đặt lịch.");
        }
        result.put("bookingLink", "/appointments/create");
        return result;
    }

    // ---------- search_products ----------

    private ObjectNode searchProducts(JsonNode args) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("http://product-service/products")
                .queryParam("size", MAX_PRODUCTS);

        String keyword = args.path("keyword").asText("").trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            keyword = keyword.substring(0, MAX_KEYWORD_LENGTH);
        }
        if (!keyword.isEmpty()) {
            uri.queryParam("keyword", keyword);
        }

        String categoryId = args.path("categoryId").asText("").trim();
        if (!categoryId.isEmpty()) {
            try {
                uri.queryParam("categoryId", UUID.fromString(categoryId));
            } catch (IllegalArgumentException e) {
                return error("categoryId không hợp lệ.");
            }
        }

        if (args.hasNonNull("maxPrice")) {
            BigDecimal maxPrice = args.path("maxPrice").decimalValue();
            if (maxPrice.signum() < 0) {
                return error("maxPrice không hợp lệ.");
            }
            uri.queryParam("maxPrice", maxPrice.toPlainString());
        }

        URI target = uri.build().encode(StandardCharsets.UTF_8).toUri();
        JsonNode body = client.get().uri(target).retrieve().body(JsonNode.class);

        ArrayNode products = mapper.createArrayNode();
        for (JsonNode item : asItems(body)) {
            if (products.size() >= MAX_PRODUCTS) {
                break;
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("name", item.path("name").asText(""));
            out.put("category", item.path("categoryName").asText(""));
            out.set("priceVnd", item.path("price"));
            out.put("unit", item.path("unit").asText(""));
            out.put("inStock", item.path("stockQuantity").asInt(0) > 0);
            String id = item.path("id").asText("");
            if (!id.isBlank()) {
                out.put("link", "/products/" + id);
            }
            products.add(out);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("products", products);
        result.put("totalMatches", body == null ? 0 : body.path("totalElements").asLong(products.size()));
        if (products.isEmpty()) {
            result.put("note", "Không tìm thấy sản phẩm phù hợp.");
        }
        return result;
    }

    // ---------- list_categories ----------

    private ObjectNode listCategories() {
        JsonNode body = client.get().uri("http://product-service/categories")
                .retrieve().body(JsonNode.class);
        ArrayNode categories = mapper.createArrayNode();
        for (JsonNode item : asItems(body)) {
            if (categories.size() >= MAX_CATEGORIES) {
                break;
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("id", item.path("id").asText(""));
            out.put("name", item.path("name").asText(""));
            categories.add(out);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("categories", categories);
        return result;
    }

    // ---------- tiện ích ----------

    /** Phản hồi có thể là mảng trần hoặc trang {content: [...]}. */
    private static Iterable<JsonNode> asItems(JsonNode body) {
        if (body == null || body.isNull()) {
            return java.util.List.of();
        }
        return body.isArray() ? body : body.path("content");
    }

    private static String trimSeconds(String time) {
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }

    private ObjectNode loginRequired() {
        ObjectNode result = mapper.createObjectNode();
        result.put("error", "login_required");
        result.put("message", "Xem giờ khám còn trống cần đăng nhập. Hãy hướng dẫn khách đăng nhập rồi vào trang đặt lịch.");
        result.put("bookingLink", "/appointments/create");
        return result;
    }

    private ObjectNode error(String message) {
        ObjectNode result = mapper.createObjectNode();
        result.put("error", message);
        return result;
    }
}
