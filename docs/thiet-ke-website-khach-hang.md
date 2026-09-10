# Website khách hàng — bản thiết kế

Tài liệu này là bước "plan" bắt buộc trước khi code, theo skill `frontend-design`.
Nó ghi lại **vì sao** chọn như vậy, để lần sau sửa còn biết cái nào là quyết định
có chủ đích, cái nào chỉ là mặc định.

## Đối tượng và việc cần làm

Người xem: chủ nuôi chó mèo ở thành phố, phần lớn mở bằng điện thoại, phần lớn
chưa từng đến phòng khám này. Việc chính của trang: khiến họ **đặt lịch khám**.
Việc phụ: **bán đồ cho thú cưng**.

Đây không phải công cụ nội bộ. Bác sĩ, nhân viên và quản trị vẫn dùng dashboard
có sidebar như cũ — website này là mặt tiền, hai thứ cố tình khác nhau.

## Màu — lấy từ chính logo của dự án

`assets/logo/vet_clinic_icon.svg` đã có sẵn nhận diện: dấu chân trắng trên nền
xanh mòng két, kèm chữ thập y tế màu san hô.

Nên không bịa bảng màu mới. Toàn bộ hệ màu rút ra từ đó:

| Token | Hex | Vai trò |
|---|---|---|
| `--ink` | `#10322F` | Chữ chính. Xanh mòng két rất sẫm, không phải đen ám màu |
| `--pine` | `#134E4A` | Nền các dải tối (lấy nguyên từ logo) |
| `--teal` | `#0E9B8E` | Màu thương hiệu, chrome, biểu tượng (lấy nguyên từ logo) |
| `--coral` | `#FF6B5E` | Điểm nhấn ấm (lấy nguyên từ logo) |
| `--coral-deep` | `#D8442C` | Nút chính — bản đủ tương phản để chữ trắng đọc được |
| `--peach` | `#FFF3EF` | Nền dải ấm |
| `--mint` | `#F2FAF9` | Nền dải mát (lấy nguyên từ logo) |
| `--mist` | `#DCEFEC` | Đường kẻ, viền (lấy nguyên từ logo) |
| `--stone` | `#5C6F6B` | Chữ phụ |

Nền chủ đạo là **trắng**, không phải kem. Dải kem ấm là dấu hiệu dễ nhận của
giao diện máy sinh ra, và trắng cũng làm ảnh thú cưng nổi hơn.

Nhiệt độ chia việc rõ ràng: **mòng két = thương hiệu và sự tin cậy**,
**san hô = hành động**. Nút "Đặt lịch khám" là thứ duy nhất mang màu san hô đậm
trên mỗi màn hình, nên mắt luôn tìm thấy nó ngay.

Quan hệ với dashboard: dashboard dùng xanh rêu `#2D4739`. Cùng họ xanh sẫm với
`--pine`, nên hai mặt của hệ thống vẫn là một thương hiệu — chỉ khác nhiệt độ.

## Chữ

**Fraunces** cho tiêu đề. Serif mềm, có trục `SOFT` và `WONK` làm chữ hơi lệch
một cách có duyên — ấm, không nghiêm nghị kiểu bệnh viện, cũng không phải
Playfair mà ai cũng dùng. Đã kiểm chứng có bộ ký tự tiếng Việt.

**Be Vietnam Pro** cho nội dung. Do người Việt thiết kế, dấu đặt chuẩn ở mọi cỡ.
Dự án đã tải sẵn nên không tốn thêm request nào.

Thang cỡ chữ, bước khoảng 1.25:

```
display   clamp(2.75rem, 6vw, 4.5rem)   lh 1.02   ls -0.02em
h2        clamp(1.75rem, 3vw, 2.5rem)   lh 1.12
h3        1.375rem                      lh 1.25
body      1.0625rem                     lh 1.7
small     0.9375rem                     lh 1.6
```

Body 17px chứ không phải 16px: dấu tiếng Việt chồng hai tầng (ế, ộ, ữ) cần thêm
chỗ thở. Cùng lý do, line-height 1.7 rộng hơn mức thường dùng cho sans.

Độ dài dòng tối đa 68 ký tự.

Ba lối mòn cố tình tránh: không bôi màu một chữ trong tiêu đề, không nhãn
IN HOA giãn chữ phía trên mỗi mục, không chuỗi meta nối bằng dấu chấm giữa.

## Bố cục

Khung 1200px, lề 24px (điện thoại 20px). Nhịp dọc giữa các dải: 96px trên điện
thoại, 128px trên máy để bàn. Căn trái toàn bộ; chỉ dải kêu gọi cuối trang căn giữa.

### Mô-típ: mái vòm

Mọi khung ảnh bo tròn nửa trên thành hình vòm — `border-radius: 999px 999px 0 0`.

Vòm là cửa, là mái che. Nói đúng thứ phòng khám muốn nói: chỗ trú an toàn cho
con vật. Nó cũng giải quyết một việc rất thực tế — sản phẩm trong cơ sở dữ liệu
phần lớn chưa có ảnh, mà một mái vòm màu phẳng có dấu chân ở giữa thì trông vẫn
cố ý, còn khung chữ nhật rỗng thì trông như hỏng.

Đây là chỗ duy nhất được phép mạnh tay. Mọi thứ còn lại giữ im: nền phẳng, một
loại đổ bóng duy nhất và chỉ khi rê chuột, viền càng ít càng tốt.

```
HERO
+--------------------------------------------------+
| logo   Trang chu  Dich vu  San pham   gio  [nut]  |
+--------------------------------------------------+
|                                                  |
|  Cho dua cua ban khi         .---------.         |
|  be nha minh tro benh.       |  .---.  |         |
|                              |  |     | |        |
|  Phong kham thu y ...        |  | anh | |        |
|                              |  |     | |        |
|  ( Dat lich kham ) (San pham)|  '---'  |         |
|                              '----+----'         |
|  ---------+---------+------       |  vom tran     |
|  7:30-20h | 6 bac si| ...         |  xuong dai sau|
+-----------------------------------+--------------+
```

Vòm tràn qua ranh giới dải bên dưới. Rẻ, mà nhớ được.

### Các dải trên trang chủ

1. Hero — như trên
2. Giới thiệu — hai cột, ảnh vòm trái, chữ phải
3. Dịch vụ — **không phải lưới thẻ**. Hàng có đường kẻ ngăn, biểu tượng vòm nhỏ,
   tên dịch vụ, một dòng mô tả. Máy để bàn xếp 2×2
4. Vì sao chọn chúng tôi — dải nền `--pine`, chữ trắng, ba ý bố cục lệch
5. Đội ngũ bác sĩ — vòm mòng két, ghi chuyên môn và số năm (API **không** trả tên)
6. Sản phẩm nổi bật — vòm sản phẩm, 4 món lấy từ API thật
7. Đặt lịch thế nào — bốn bước đánh số. Đây thật sự là một chuỗi tuần tự nên
   đánh số là đúng chức năng, không phải trang trí
8. Cam kết chăm sóc — thay cho mục đánh giá, lý do ở phần dưới
9. Kêu gọi cuối — dải san hô nhạt, căn giữa
10. Chân trang — nền `--pine`, bốn cột

## Chuyển động

Một khoảnh khắc duy nhất: lúc mở trang chủ, tiêu đề rồi mô tả rồi nút rồi vòm
hiện lần lượt, tổng khoảng 600ms. Ngoài ra chỉ có phản hồi khi người dùng chạm
vào: thẻ nhấc lên 2px, ảnh phóng 1.03, đầu trang từ trong suốt chuyển sang đặc
khi cuộn.

Cố tình **không** làm hiệu ứng trôi lên cho từng dải khi cuộn — đó là mặc định
của giao diện máy sinh, và nó làm trang đọc chậm hơn chứ không đẹp hơn.

`prefers-reduced-motion` tắt sạch.

## Điện thoại

Không phải bản thu nhỏ của máy để bàn:

- Ngăn kéo menu trượt từ phải, mục cao 48px
- Trang sản phẩm và giỏ hàng có thanh hành động dính đáy màn hình
- Bước chọn giờ khám dùng nút bấm to, không dùng ô chọn xổ xuống
- Chỉnh số lượng trong giỏ bằng hai nút tròn 40px, không phải ô nhập số
- Lưới sản phẩm 2 cột từ 380px trở lên, 1 cột dưới mức đó

## Chỗ backend chưa có — không bịa

| Mục trong đề bài | Trạng thái | Cách xử lý |
|---|---|---|
| Tên và ảnh bác sĩ | `GET /profile/doctors` chỉ trả chuyên môn, số năm, giới thiệu | Thẻ bác sĩ hiện chuyên môn thay cho tên. Ghi VD-20 |
| Chọn bác sĩ khi đặt lịch | Backend tự gán slot (VD-16) | Bỏ hẳn bước này, nói rõ với khách là hệ thống tự xếp |
| Chọn dịch vụ khi đặt lịch | `AppointmentRequest` không có trường dịch vụ | Gợi ý viết vào ô lý do khám. Ghi VD-21 |
| Đánh giá của khách | Không có API nào | Thay bằng "Cam kết chăm sóc" — lời của phòng khám, không phải lời khách bịa ra |
| Dị ứng, ghi chú thú cưng | `PetRequest` không có hai trường này | Không hiện. Ghi VD-22 |
| Lịch sử khám của thú cưng | Chỉ tra được theo từng lịch hẹn (VD-17) | Trang thú cưng lọc từ danh sách lịch hẹn của chính khách |
| Chi tiết lịch hẹn | `GET /booking/appointments/{id}` chặn CUSTOMER | Lấy từ danh sách `mine()` thay vì gọi endpoint bị chặn |
| Phí giao hàng trước khi đặt | Chỉ biết sau khi tạo đơn | Nói thật là phí cộng khi tạo đơn, không đoán số |

**Vì sao đổi mục đánh giá.** Viết sẵn mấy lời khen kèm tên người rồi trình bày
như đánh giá thật là bịa dữ liệu — đúng thứ mà quy tắc của dự án cấm. Nên chỗ đó
đặt cam kết của phòng khám: vẫn tạo niềm tin, mà không dựng người không có thật.
Khi backend có API đánh giá thì thay vào.

## Ảnh

Ba tấm tải về `public/img/` theo giấy phép Unsplash, dùng ở hero, dải giới thiệu
và dải kêu gọi cuối. Không nhúng thẳng đường dẫn ngoài — mất mạng là trang vỡ.

Ảnh sản phẩm dùng `product.imageUrl` khi có. Không có thì rơi về mái vòm màu
suy ra từ tên danh mục kèm dấu chân. Không bao giờ để ảnh vỡ.

Không có ảnh chân dung bác sĩ. Lấy ảnh người lạ gán tên bác sĩ của phòng khám
là dựng chuyện.
