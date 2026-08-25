-- CN-27: danh mục sản phẩm (thức ăn, thuốc, phụ kiện...)
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- CN-28, CN-29, CN-30: sản phẩm + tồn kho hiện tại
CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id         UUID NOT NULL REFERENCES categories (id),
    sku                 VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    price               NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    unit                VARCHAR(30) NOT NULL,
    image_url           VARCHAR(500),
    -- Tồn kho hiện tại giữ ngay tại đây để truy vấn/lọc nhanh; mọi thay đổi đều
    -- được ghi vết sang stock_movements nên vẫn đối soát lại được.
    stock_quantity      INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    low_stock_threshold INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Không xoá được danh mục còn hàng: FK ở trên cố tình KHÔNG dùng ON DELETE CASCADE.
CREATE INDEX idx_products_category ON products (category_id);
-- Lọc theo tên: dùng lower(name) vì tìm kiếm không phân biệt hoa thường.
CREATE INDEX idx_products_name_lower ON products (lower(name));

-- CN-30, CN-31: lịch sử biến động kho — nhập, điều chỉnh tay, trừ khi bán, hoàn khi huỷ đơn
CREATE TABLE stock_movements (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    type           VARCHAR(20) NOT NULL,
    -- Âm khi trừ kho, dương khi nhập. quantity_after chốt lại tồn sau thao tác
    -- để đối soát mà không phải cộng dồn toàn bộ lịch sử.
    quantity_change INTEGER NOT NULL,
    quantity_after  INTEGER NOT NULL CHECK (quantity_after >= 0),
    note           VARCHAR(500),
    -- Với type=SALE/RETURN đây là orderId; giúp chống xử lý trùng khi RabbitMQ
    -- giao lại message (at-least-once delivery).
    reference_id   UUID,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);
-- Chặn trừ kho hai lần cho cùng một đơn hàng và cùng một sản phẩm.
CREATE UNIQUE INDEX idx_stock_movements_reference
    ON stock_movements (product_id, reference_id, type)
    WHERE reference_id IS NOT NULL;
