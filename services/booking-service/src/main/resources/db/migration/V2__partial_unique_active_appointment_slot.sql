-- UNIQUE(slot_id) cứng chặn việc đặt lại 1 slot sau khi appointment cũ trên slot đó bị CANCELLED
-- (slot_id vẫn tồn tại vĩnh viễn trong bảng dù appointment đã huỷ). Đổi sang partial unique index
-- chỉ áp dụng cho appointment CHƯA cancelled — vẫn chặn double-booking đồng thời, nhưng cho phép
-- đặt lại đúng slot đó sau khi huỷ.
ALTER TABLE appointments DROP CONSTRAINT appointments_slot_id_key;

CREATE UNIQUE INDEX uq_appointments_active_slot ON appointments (slot_id) WHERE status <> 'CANCELLED';
