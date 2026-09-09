-- Payment giờ được tạo ngay khi nhận prescription.created (status PENDING_AMOUNT), lúc đó
-- chưa biết amount (staff nhập tay sau) -> bỏ NOT NULL để cho phép NULL tạm thời.
ALTER TABLE payments ALTER COLUMN amount DROP NOT NULL;
