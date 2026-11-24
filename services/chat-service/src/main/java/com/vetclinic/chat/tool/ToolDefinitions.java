package com.vetclinic.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/** Khai báo 4 công cụ chỉ đọc cho Gemini (function calling). Không có công cụ nào ghi dữ liệu. */
public final class ToolDefinitions {

    public static final String LIST_CLINIC_SERVICES = "list_clinic_services";
    public static final String GET_CLINIC_HOURS = "get_clinic_hours";
    public static final String SEARCH_PRODUCTS = "search_products";
    public static final String LIST_CATEGORIES = "list_categories";

    public static final Set<String> NAMES =
            Set.of(LIST_CLINIC_SERVICES, GET_CLINIC_HOURS, SEARCH_PRODUCTS, LIST_CATEGORIES);

    private ToolDefinitions() {
    }

    /** Phần "tools" của yêu cầu generateContent. */
    public static ArrayNode declarations(ObjectMapper mapper) {
        ArrayNode functions = mapper.createArrayNode();

        functions.add(function(mapper, LIST_CLINIC_SERVICES,
                "Lấy danh sách dịch vụ của phòng khám đang cung cấp, kèm mô tả, thời lượng và giá tham khảo (VND).",
                mapper.createObjectNode()));

        ObjectNode hoursProps = mapper.createObjectNode();
        hoursProps.set("date", property(mapper, "STRING",
                "Ngày cần xem giờ khám còn trống, định dạng yyyy-MM-dd (đổi 'ngày mai', 'thứ bảy' sang ngày cụ thể dựa trên ngày hôm nay)."));
        functions.add(function(mapper, GET_CLINIC_HOURS,
                "Xem các mốc giờ khám còn chỗ trong một ngày. Chỉ dùng được khi khách đã đăng nhập.",
                schema(mapper, hoursProps, "date")));

        ObjectNode productProps = mapper.createObjectNode();
        productProps.set("keyword", property(mapper, "STRING", "Từ khoá tên sản phẩm, ví dụ 'hạt cho mèo', 'sữa tắm'."));
        productProps.set("categoryId", property(mapper, "STRING", "ID danh mục (lấy từ list_categories), nếu khách muốn lọc theo danh mục."));
        productProps.set("maxPrice", property(mapper, "NUMBER", "Giá tối đa (VND), nếu khách có ngân sách."));
        functions.add(function(mapper, SEARCH_PRODUCTS,
                "Tìm sản phẩm đang bán (thức ăn, đồ dùng, vệ sinh...) kèm giá và tình trạng còn hàng.",
                schema(mapper, productProps)));

        functions.add(function(mapper, LIST_CATEGORIES,
                "Lấy danh sách danh mục sản phẩm.",
                mapper.createObjectNode()));

        ObjectNode tool = mapper.createObjectNode();
        tool.set("functionDeclarations", functions);
        ArrayNode tools = mapper.createArrayNode();
        tools.add(tool);
        return tools;
    }

    private static ObjectNode function(ObjectMapper mapper, String name, String description, ObjectNode parameters) {
        ObjectNode function = mapper.createObjectNode();
        function.put("name", name);
        function.put("description", description);
        // Hàm không tham số thì bỏ hẳn "parameters" (Gemini không nhận object rỗng không có properties).
        if (parameters.has("properties")) {
            function.set("parameters", parameters);
        }
        return function;
    }

    private static ObjectNode schema(ObjectMapper mapper, ObjectNode properties, String... required) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "OBJECT");
        schema.set("properties", properties);
        if (required.length > 0) {
            ArrayNode requiredNode = mapper.createArrayNode();
            for (String name : required) {
                requiredNode.add(name);
            }
            schema.set("required", requiredNode);
        }
        return schema;
    }

    private static JsonNode property(ObjectMapper mapper, String type, String description) {
        ObjectNode property = mapper.createObjectNode();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}
