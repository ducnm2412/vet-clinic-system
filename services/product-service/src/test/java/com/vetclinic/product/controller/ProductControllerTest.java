package com.vetclinic.product.controller;

import com.vetclinic.product.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        // Test HTTP/phân quyền không cần broker; tắt listener để không phải dựng RabbitMQ
        // mới chạy được test. Luồng trừ kho theo sự kiện đã có ProductServiceTest phủ.
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String tokenFor(String role) {
        return TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of(role));
    }

    /** Tạo sẵn một danh mục và trả về id — nhiều test cần có danh mục trước khi tạo sản phẩm. */
    private String createCategory(String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Thuc an\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // ---------- CN-29: tra cứu công khai ----------

    @Test
    void search_withoutToken_isPublic() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listCategories_withoutToken_isPublic() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void search_withAllFilters_runsWithoutTypeError() throws Exception {
        // Từng lỗi ở đây: bind null vào lower() khiến PostgreSQL báo lower(bytea).
        // Test đi qua đủ cả 4 bộ lọc để bắt lại lỗi suy kiểu tham số nếu tái diễn.
        mockMvc.perform(get("/products")
                        .param("keyword", "hat")
                        .param("categoryId", UUID.randomUUID().toString())
                        .param("minPrice", "1000")
                        .param("maxPrice", "500000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void search_byKeyword_filtersResult() throws Exception {
        String categoryId = createCategory("thuc-an-search");

        mockMvc.perform(post("/products")
                .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId":"%s","sku":"SKU-SEARCH","name":"Pate ca ngu","price":25000,"unit":"lon"}
                        """.formatted(categoryId)));

        mockMvc.perform(get("/products").param("keyword", "pate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == 'SKU-SEARCH')]").exists());

        mockMvc.perform(get("/products").param("keyword", "khong-ton-tai-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------- CN-27, CN-28: quản trị ----------

    @Test
    void createCategory_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Thuc an\",\"slug\":\"thuc-an\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCategory_withCustomerToken_returns403() throws Exception {
        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + tokenFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Thuc an\",\"slug\":\"thuc-an-2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_withAdminToken_succeeds() throws Exception {
        String categoryId = createCategory("thuc-an-3");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"SKU-T1","name":"Hat cho cho",
                                 "price":150000,"unit":"goi","initialStock":10,"lowStockThreshold":3}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-T1"))
                .andExpect(jsonPath("$.stockQuantity").value(10))
                .andExpect(jsonPath("$.lowStock").value(false));
    }

    @Test
    void createProduct_responseIncludesTimestamps() throws Exception {
        // Từng lỗi ở đây: save() chưa flush nên @CreationTimestamp còn null và response
        // của POST trả createdAt/updatedAt rỗng, dù DB đã ghi đúng.
        String categoryId = createCategory("thuc-an-ts");

        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"SKU-TS","name":"Hat","price":1000,"unit":"goi"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createCategory_responseIncludesTimestamps() throws Exception {
        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Phu kien\",\"slug\":\"phu-kien-ts\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createProduct_duplicateSku_returns409() throws Exception {
        String categoryId = createCategory("thuc-an-4");
        String body = """
                {"categoryId":"%s","sku":"SKU-DUP","name":"Hat","price":1000,"unit":"goi"}
                """.formatted(categoryId);

        mockMvc.perform(post("/products").header("Authorization", "Bearer " + tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        mockMvc.perform(post("/products").header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createProduct_unknownCategory_returns404() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"SKU-T2","name":"Hat","price":1000,"unit":"goi"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_withStaffToken_returns403() throws Exception {
        // STAFF được sửa kho nhưng không được tạo sản phẩm mới.
        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + tokenFor("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"SKU-T3","name":"Hat","price":1000,"unit":"goi"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    // ---------- CN-30: tồn kho ----------

    @Test
    void lowStock_withCustomerToken_returns403() throws Exception {
        mockMvc.perform(get("/products/low-stock")
                        .header("Authorization", "Bearer " + tokenFor("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lowStock_withStaffToken_returns200() throws Exception {
        mockMvc.perform(get("/products/low-stock")
                        .header("Authorization", "Bearer " + tokenFor("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void adjustStock_withStaffToken_updatesQuantity() throws Exception {
        String categoryId = createCategory("thuc-an-5");

        MvcResult created = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"SKU-T4","name":"Hat","price":1000,
                                 "unit":"goi","initialStock":5,"lowStockThreshold":2}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn();

        String productId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/products/" + productId + "/stock")
                        .header("Authorization", "Bearer " + tokenFor("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IMPORT\",\"quantityChange\":7,\"note\":\"Nhap kho\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(12));

        // Lịch sử phải có 2 dòng: tồn khởi tạo và lần nhập vừa rồi.
        mockMvc.perform(get("/products/" + productId + "/stock-movements")
                        .header("Authorization", "Bearer " + tokenFor("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void adjustStock_beyondAvailable_returns409() throws Exception {
        String categoryId = createCategory("thuc-an-6");

        MvcResult created = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","sku":"SKU-T5","name":"Hat","price":1000,
                                 "unit":"goi","initialStock":2}
                                """.formatted(categoryId)))
                .andReturn();

        String productId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/products/" + productId + "/stock")
                        .header("Authorization", "Bearer " + tokenFor("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantityChange\":-10}"))
                .andExpect(status().isConflict());
    }
}
