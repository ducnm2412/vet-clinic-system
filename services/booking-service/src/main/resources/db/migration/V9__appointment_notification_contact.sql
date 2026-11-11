-- Email khách và tên bé chép sang lịch lúc khách gửi yêu cầu đặt lịch.
--
-- Lúc admin xếp bác sĩ hay đề xuất giờ khác, người gọi API là admin chứ không phải khách, nên không
-- còn token của khách để lấy email. Chép sẵn lúc gửi (cùng cách email xác nhận ở CN-43 lấy email
-- từ token khách) để gửi được thư báo mà không phải gọi ngược sang auth/profile-service.
-- Để trống với lịch cũ và lịch tạo hộ tại quầy: những lịch đó không gửi thư báo.
ALTER TABLE appointments ADD COLUMN customer_email VARCHAR(255);
ALTER TABLE appointments ADD COLUMN pet_name VARCHAR(100);
