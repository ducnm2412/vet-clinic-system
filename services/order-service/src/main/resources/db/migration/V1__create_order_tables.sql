-- CN-32: giỏ hàng. Mỗi user một giỏ, giữ trong DB chứ không phải session/Redis
-- để khách quay lại (kể cả máy khác) vẫn còn hàng đã chọn.
CREATE TABLE carts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id    UUID NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    -- Không có khoá ngoại sang product_db: mỗi service một database, tham chiếu
    -- chéo database là vi phạm Database per Service.
    product_id UUID NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Thêm cùng một sản phẩm hai lần thì cộng dồn số lượng, không tạo dòng mới.
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);

-- CN-33, CN-35, CN-36: đơn hàng
CREATE TABLE orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Mã cho khách đọc/tra cứu, dễ hơn UUID khi gọi điện hỏi đơn.
    order_code  VARCHAR(20) NOT NULL UNIQUE,
    user_id     UUID NOT NULL,
    status      VARCHAR(20) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,

    -- Thông tin giao hàng chụp lại tại thời điểm đặt. Khách sửa sổ địa chỉ về sau
    -- thì đơn cũ vẫn giữ nguyên nơi đã giao.
    recipient_name    VARCHAR(200) NOT NULL,
    recipient_phone   VARCHAR(20) NOT NULL,
    shipping_address  VARCHAR(500) NOT NULL,
    note              VARCHAR(500),

    subtotal     NUMERIC(14, 2) NOT NULL CHECK (subtotal >= 0),
    shipping_fee NUMERIC(14, 2) NOT NULL CHECK (shipping_fee >= 0),
    total        NUMERIC(14, 2) NOT NULL CHECK (total >= 0),

    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancel_reason VARCHAR(500)
);

CREATE INDEX idx_orders_user ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders (status, created_at DESC);

CREATE TABLE order_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   UUID NOT NULL,
    -- SKU, tên và giá chụp lại lúc đặt: shop đổi giá hay đổi tên sau này thì
    -- đơn cũ không đổi theo, và vẫn đọc được cả khi sản phẩm đã bị xoá.
    sku          VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    line_total   NUMERIC(14, 2) NOT NULL CHECK (line_total >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- CN-35: vết chuyển trạng thái, để trả lời được "ai đổi, lúc nào, vì sao".
CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_by  UUID,
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, created_at);
