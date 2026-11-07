-- Gioi tinh cua khach hang (MALE, FEMALE, OTHER). Cho phep de trong: ho so cu va khach khong khai deu giu NULL.
ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS gender VARCHAR(10);
ALTER TABLE customer_profiles ADD CONSTRAINT chk_customer_profiles_gender
    CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'));
