package com.vetclinic.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vetclinic.chat.service.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ToolExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    // Hôm nay = 2026-11-24 (giờ Việt Nam)
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private MockRestServiceServer server;
    private ToolExecutor executor;

    private final Caller guest = Caller.guest("1.2.3.4");
    private final Caller user = Caller.user("u1", "tok123");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        executor = new ToolExecutor(builder, mapper, clock);
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void unknownToolIsRejectedWithoutCallingAnyService() throws Exception {
        ObjectNode result = executor.execute("delete_everything", args("{}"), user);

        assertThat(result.path("error").asText()).contains("không tồn tại");
        server.verify();
    }

    @Test
    void listServicesKeepsActiveOnesAndAddsLinks() throws Exception {
        server.expect(requestTo("http://booking-service/booking/services")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":"1","slug":"tiem-phong","name":"Tiêm phòng","description":"Vắc xin","durationMinutes":20,"referencePrice":150000,"active":true},
                         {"id":"2","slug":"cu","name":"Dịch vụ cũ","description":"","durationMinutes":10,"referencePrice":1,"active":false}]
                        """, MediaType.APPLICATION_JSON));

        ObjectNode result = executor.execute(ToolDefinitions.LIST_CLINIC_SERVICES, args("{}"), guest);

        assertThat(result.path("services")).hasSize(1);
        JsonNode service = result.path("services").get(0);
        assertThat(service.path("name").asText()).isEqualTo("Tiêm phòng");
        assertThat(service.path("referencePriceVnd").asInt()).isEqualTo(150000);
        assertThat(service.path("link").asText()).isEqualTo("/services/tiem-phong");
        server.verify();
    }

    @Test
    void clinicHoursRequireLogin() throws Exception {
        ObjectNode result = executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{\"date\":\"2026-11-25\"}"), guest);

        assertThat(result.path("error").asText()).isEqualTo("login_required");
        server.verify(); // không có request nào được gửi
    }

    @Test
    void clinicHoursRejectBadAndOutOfRangeDates() throws Exception {
        assertThat(executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{\"date\":\"mai\"}"), user)
                .path("error").asText()).contains("không hợp lệ");
        assertThat(executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{\"date\":\"2026-11-20\"}"), user)
                .path("error").asText()).contains("từ hôm nay");
        assertThat(executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{\"date\":\"2027-06-01\"}"), user)
                .path("error").asText()).contains("từ hôm nay");
        assertThat(executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{}"), user)
                .path("error").asText()).contains("không hợp lệ");
        server.verify();
    }

    @Test
    void clinicHoursForwardTheCustomersTokenAndReturnOnlyAvailableSlots() throws Exception {
        server.expect(requestTo("http://booking-service/booking/clinic-hours?date=2026-11-25"))
                .andExpect(header("Authorization", "Bearer tok123"))
                .andRespond(withSuccess("""
                        [{"startTime":"08:00:00","endTime":"09:00:00","available":true,"remaining":2},
                         {"startTime":"09:00:00","endTime":"10:00:00","available":false,"remaining":0}]
                        """, MediaType.APPLICATION_JSON));

        ObjectNode result = executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{\"date\":\"2026-11-25\"}"), user);

        assertThat(result.path("availableTimes")).hasSize(1);
        assertThat(result.path("availableTimes").get(0).path("time").asText()).isEqualTo("08:00");
        assertThat(result.path("availableTimes").get(0).path("remaining").asInt()).isEqualTo(2);
        server.verify();
    }

    @Test
    void clinicHoursUnauthorizedFromBookingBecomesLoginRequired() throws Exception {
        server.expect(requestTo("http://booking-service/booking/clinic-hours?date=2026-11-25"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ObjectNode result = executor.execute(ToolDefinitions.GET_CLINIC_HOURS, args("{\"date\":\"2026-11-25\"}"), user);

        assertThat(result.path("error").asText()).isEqualTo("login_required");
    }

    @Test
    void searchProductsEncodesKeywordAndMapsResult() throws Exception {
        server.expect(requestTo(containsString("http://product-service/products?size=8&keyword=h%E1%BA%A1t%20m%C3%A8o")))
                .andRespond(withSuccess("""
                        {"content":[{"id":"abc","name":"Hạt Royal Canin","categoryName":"Thức ăn","price":250000,"unit":"túi","stockQuantity":5},
                                    {"id":"def","name":"Hạt hết hàng","categoryName":"Thức ăn","price":100000,"unit":"túi","stockQuantity":0}],
                         "totalElements":2}
                        """, MediaType.APPLICATION_JSON));

        ObjectNode result = executor.execute(ToolDefinitions.SEARCH_PRODUCTS, args("{\"keyword\":\"hạt mèo\"}"), guest);

        assertThat(result.path("products")).hasSize(2);
        assertThat(result.path("products").get(0).path("inStock").asBoolean()).isTrue();
        assertThat(result.path("products").get(0).path("link").asText()).isEqualTo("/products/abc");
        assertThat(result.path("products").get(1).path("inStock").asBoolean()).isFalse();
        server.verify();
    }

    @Test
    void searchProductsTruncatesLongKeyword() throws Exception {
        String longKeyword = "a".repeat(500);
        server.expect(requestTo(containsString("keyword=" + "a".repeat(ToolExecutor.MAX_KEYWORD_LENGTH))))
                .andRespond(withSuccess("{\"content\":[],\"totalElements\":0}", MediaType.APPLICATION_JSON));

        ObjectNode result = executor.execute(ToolDefinitions.SEARCH_PRODUCTS,
                args("{\"keyword\":\"" + longKeyword + "\"}"), guest);

        assertThat(result.path("products")).isEmpty();
        assertThat(result.path("note").asText()).contains("Không tìm thấy");
        server.verify();
    }

    @Test
    void searchProductsRejectsBadCategoryIdAndNegativePrice() throws Exception {
        assertThat(executor.execute(ToolDefinitions.SEARCH_PRODUCTS,
                args("{\"categoryId\":\"1; DROP TABLE\"}"), guest).path("error").asText()).contains("categoryId");
        assertThat(executor.execute(ToolDefinitions.SEARCH_PRODUCTS,
                args("{\"maxPrice\":-5}"), guest).path("error").asText()).contains("maxPrice");
        server.verify();
    }

    @Test
    void listCategoriesMapsArray() throws Exception {
        server.expect(requestTo("http://product-service/categories"))
                .andRespond(withSuccess("[{\"id\":\"c1\",\"name\":\"Thức ăn\"},{\"id\":\"c2\",\"name\":\"Đồ chơi\"}]",
                        MediaType.APPLICATION_JSON));

        ObjectNode result = executor.execute(ToolDefinitions.LIST_CATEGORIES, args("{}"), guest);

        assertThat(result.path("categories")).hasSize(2);
        assertThat(result.path("categories").get(1).path("name").asText()).isEqualTo("Đồ chơi");
    }

    @Test
    void downstreamServerErrorBecomesGenericErrorNotException() throws Exception {
        server.expect(requestTo("http://product-service/categories"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ObjectNode result = executor.execute(ToolDefinitions.LIST_CATEGORIES, args("{}"), guest);

        assertThat(result.path("error").asText()).contains("Không lấy được dữ liệu");
    }
}
