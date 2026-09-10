import { HeartPulse, Scissors, ShieldCheck, Stethoscope, type LucideIcon } from "lucide-react";

/*
  Nội dung thương hiệu của website khách hàng.

  Đây là lời giới thiệu do phòng khám tự viết, không phải dữ liệu lấy từ backend — nên để
  ở một chỗ, sửa một chỗ. Phần địa chỉ và số điện thoại là thông tin tạm của đồ án; khi
  triển khai thật thì thay bằng thông tin thật của phòng khám.

  TODO(backend): chưa service nào quản lý danh mục dịch vụ. `AppointmentRequest` cũng
  không có trường dịch vụ, nên khách chọn dịch vụ ở đây cũng không gửi đi đâu được —
  xem VD-21 trong docs/van-de-ton-dong.md.
*/

export const CLINIC = {
  address: "128 Nguyễn Văn Cừ, phường An Hoà, Thành phố Cần Thơ",
  phone: "0292 3838 686",
  hours: "Thứ Hai đến Chủ Nhật, 7:30 – 20:00",
} as const;

export interface Service {
  icon: LucideIcon;
  name: string;
  description: string;
}

export const SERVICES: Service[] = [
  {
    icon: Stethoscope,
    name: "Khám tổng quát",
    description:
      "Bác sĩ nghe tim phổi, kiểm tra răng miệng, da lông và cân nặng, rồi ghi lại thành bệnh án để lần sau còn đối chiếu.",
  },
  {
    icon: ShieldCheck,
    name: "Tiêm phòng",
    description:
      "Vắc xin dại, care, parvo và các mũi nhắc theo lịch. Chúng tôi giữ lịch giúp bạn và nhắc trước ngày đến hạn.",
  },
  {
    icon: HeartPulse,
    name: "Phẫu thuật",
    description:
      "Triệt sản, lấy dị vật, xử lý vết thương. Có phòng mổ riêng, gây mê theo cân nặng và theo dõi đến khi bé tỉnh hẳn.",
  },
  {
    icon: Scissors,
    name: "Chăm sóc và làm đẹp",
    description:
      "Tắm, cắt tỉa, vệ sinh tai và cắt móng. Bé nào sợ nước thì làm chậm, không ép, không nhốt chờ cả buổi.",
  },
];

/** Vì sao chọn chúng tôi — ba điều nói được bằng sự thật, không phải khẩu hiệu. */
export const REASONS: Array<{ title: string; body: string }> = [
  {
    title: "Bác sĩ có chứng chỉ hành nghề",
    body: "Mỗi bác sĩ trong phòng khám đều khai báo chứng chỉ và chuyên môn trong hệ thống. Bạn xem được ai đang phụ trách ca của bé.",
  },
  {
    title: "Bệnh án lưu lại, không mất",
    body: "Mỗi lần khám là một bệnh án có chẩn đoán, cách điều trị và đơn thuốc. Lần sau bác sĩ mở ra là thấy hết, không phải hỏi lại từ đầu.",
  },
  {
    title: "Thuốc và đồ dùng có nguồn gốc",
    body: "Kho của phòng khám ghi vết từng lần nhập và xuất. Hết hàng thì báo hết, không bán vống lên rồi hẹn.",
  },
];

/**
 * Cam kết chăm sóc — đặt ở chỗ mà một trang thương mại thường để lời khen của khách.
 *
 * Cố tình không viết đánh giá kèm tên người: backend chưa có API đánh giá, nên mọi câu
 * khen ở đây đều là bịa, và bịa lời của người không có thật thì tệ hơn là để trống.
 * Khi có API đánh giá thì thay dải này.
 */
export const COMMITMENTS: Array<{ title: string; body: string }> = [
  {
    title: "Báo giá trước khi làm",
    body: "Chi phí nói rõ trước khi bắt đầu. Phát sinh giữa chừng thì gọi hỏi bạn, không tự quyết rồi tính tiền sau.",
  },
  {
    title: "Không kê thuốc thừa",
    body: "Đơn thuốc chỉ có thứ bé cần. Bạn xem được từng loại, liều dùng và số ngày ngay trong hồ sơ của mình.",
  },
  {
    title: "Trả lời khi bạn lo",
    body: "Sau khi khám, bé có gì bất thường thì gọi cho phòng khám. Không phải chờ đến lịch tái khám mới hỏi được.",
  },
];

/** Quy trình đặt lịch — đây thật sự là một chuỗi tuần tự, nên đánh số là đúng chức năng. */
export const BOOKING_STEPS: Array<{ title: string; body: string }> = [
  {
    title: "Thêm hồ sơ thú cưng",
    body: "Tên, loài, giống và ngày sinh. Bác sĩ cần biết khám cho bé nào trước khi bạn chọn giờ.",
  },
  {
    title: "Chọn ngày",
    body: "Lịch mở theo ngày. Ngày nào kín chỗ sẽ hiện rõ để bạn khỏi mất công thử.",
  },
  {
    title: "Chọn giờ còn trống",
    body: "Chỉ những khung giờ thật sự còn chỗ mới hiện ra. Hệ thống tự xếp bác sĩ đang rảnh cho ca của bạn.",
  },
  {
    title: "Ghi lý do rồi gửi",
    body: "Bé có biểu hiện gì thì ghi vào. Bác sĩ đọc trước nên khi bạn đến là vào việc luôn.",
  },
];
