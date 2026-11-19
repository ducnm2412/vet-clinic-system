-- Ca quên bấm "Ra ca" được job cuối ngày tự đóng; cờ này để phân biệt giờ ra thật với giờ hệ thống điền.
ALTER TABLE attendance_records ADD COLUMN auto_closed BOOLEAN NOT NULL DEFAULT FALSE;
