// MOCK-TẠM: nội dung minh hoạ cho trang chi tiết dịch vụ (backend chưa lưu bài viết, quy trình, FAQ).
import type { ArticleBlock } from "@/lib/api/mock-data";

export interface ServiceExtra {
  highlights: string[];
  steps: Array<{ title: string; text: string }>;
  faqs: Array<{ q: string; a: string }>;
}

/** Bài viết cho các dịch vụ chưa có trong mockServiceDetails. */
export const extraServiceArticles: Record<string, { title: string; blocks: ArticleBlock[] }> = {
  "cap-cuu-24-7": {
    title: "Cấp cứu thú cưng 24/7 — có bác sĩ trực cả đêm lẫn lễ Tết",
    blocks: [
      ["img", "/img/cham-soc.jpg"],
      ["p", "Tai nạn và bệnh cấp tính không chọn giờ. Phòng khám luôn có ít nhất một bác sĩ và một kỹ thuật viên trực xuyên đêm, phòng cấp cứu được chuẩn bị sẵn oxy, máy truyền dịch và thuốc chống sốc."],
      ["h2", "Khi nào cần đưa bé đi cấp cứu ngay?"],
      ["li", "Khó thở, thở há miệng, lưỡi tím tái."],
      ["li", "Nôn hoặc tiêu chảy liên tục, có máu."],
      ["li", "Co giật, lả đi, không đứng dậy được."],
      ["li", "Bị xe tông, ngã cao, chó cắn, chảy máu không cầm."],
      ["li", "Nghi ăn phải thuốc chuột, sô-cô-la, nho, hành tỏi hoặc dị vật."],
      ["h2", "Bạn nên làm gì trên đường tới phòng khám?"],
      ["p", "Gọi trước cho đường dây nóng để đội trực chuẩn bị. Giữ bé nằm yên, quấn khăn nếu bé run, không tự cho uống thuốc người. Nếu bé ăn phải chất lạ, mang theo bao bì để bác sĩ biết hoạt chất."],
      ["h2", "Chi phí"],
      ["p", "Phí tiếp nhận cấp cứu từ 300.000đ, chưa gồm xét nghiệm, thuốc và lưu viện. Bác sĩ sẽ báo dự toán trước mỗi bước điều trị để gia đình quyết định."],
    ],
  },
  "kham-dieu-tri": {
    title: "Khám & điều trị bệnh nội khoa cho chó mèo",
    blocks: [
      ["img", "/img/meo.jpg"],
      ["p", "Khi bé biếng ăn, ho, ngứa ngáy hay đi ngoài bất thường, bác sĩ sẽ khám lâm sàng, hỏi kỹ lịch sử và chỉ định xét nghiệm vừa đủ để tìm đúng nguyên nhân — không làm thừa, không đoán mò."],
      ["h2", "Các nhóm bệnh thường gặp"],
      ["li", "Tiêu hoá: viêm ruột, Parvo, giun sán, viêm tuỵ."],
      ["li", "Hô hấp: ho cũi chó, viêm phổi, hen mèo."],
      ["li", "Da liễu: nấm, ve rận, viêm da dị ứng."],
      ["li", "Tiết niệu: sỏi bàng quang, viêm bàng quang ở mèo."],
      ["h2", "Theo dõi sau điều trị"],
      ["p", "Mỗi ca đều có hồ sơ điện tử. Bạn xem lại đơn thuốc, kết quả xét nghiệm và lịch tái khám ngay trong tài khoản; phòng khám nhắn nhắc trước ngày tái khám một hôm."],
    ],
  },
  "xet-nghiem": {
    title: "Xét nghiệm máu, nước tiểu & ký sinh trùng tại chỗ",
    blocks: [
      ["img", "/img/hero.jpg"],
      ["p", "Phòng xét nghiệm nội bộ trả phần lớn kết quả trong 15–30 phút, giúp bác sĩ quyết định điều trị ngay trong buổi khám thay vì hẹn hôm sau."],
      ["h2", "Danh mục xét nghiệm"],
      ["li", "Công thức máu tổng quát (CBC) — phát hiện thiếu máu, nhiễm trùng."],
      ["li", "Sinh hoá gan thận — trước gây mê và cho bé lớn tuổi."],
      ["li", "Test nhanh Care, Parvo, FIV/FeLV, giun tim."],
      ["li", "Soi da, soi phân, soi tai tìm nấm và ký sinh trùng."],
      ["h2", "Chuẩn bị trước khi xét nghiệm"],
      ["p", "Nên nhịn ăn 6–8 tiếng với xét nghiệm sinh hoá máu; vẫn cho uống nước bình thường. Nếu lấy nước tiểu, đừng cho bé đi vệ sinh ngay trước khi tới."],
    ],
  },
  "sieu-am": {
    title: "Siêu âm & chụp X-quang chẩn đoán hình ảnh",
    blocks: [
      ["img", "/img/cho-con.jpg"],
      ["p", "Máy siêu âm Doppler màu và X-quang kỹ thuật số giúp nhìn rõ nội tạng, xương khớp và thai mà không cần can thiệp xâm lấn. Bé không đau, phần lớn không cần gây mê."],
      ["h2", "Khi nào bác sĩ chỉ định siêu âm?"],
      ["li", "Nghi dị vật, tắc ruột, sỏi bàng quang."],
      ["li", "Theo dõi thai kỳ và đếm số con."],
      ["li", "Đánh giá gan, thận, tim ở bé lớn tuổi."],
      ["h2", "Kết quả"],
      ["p", "Hình ảnh được lưu vào hồ sơ thú cưng, kèm kết luận của bác sĩ. Bạn có thể xin bản sao để mang đi hội chẩn nơi khác."],
    ],
  },
};

const COMMON_FAQ = [
  { q: "Tôi có cần đặt lịch trước không?", a: "Nên đặt trước để bác sĩ chuẩn bị và bạn không phải chờ. Khách đến trực tiếp vẫn được tiếp nhận theo thứ tự." },
  { q: "Phòng khám nhận thanh toán những hình thức nào?", a: "Tiền mặt, chuyển khoản, quẹt thẻ và ví điện tử. Hoá đơn được lưu trong tài khoản của bạn." },
];

const EXTRA: Record<string, Omit<ServiceExtra, "faqs"> & { faqs?: ServiceExtra["faqs"] }> = {
  "cap-cuu-24-7": {
    highlights: ["Trực 24/7 cả lễ Tết", "Tiếp nhận trong 5 phút", "Phòng hồi sức riêng"],
    steps: [
      { title: "Gọi đường dây nóng", text: "Mô tả triệu chứng để đội trực chuẩn bị." },
      { title: "Phân loại & ổn định", text: "Đo dấu hiệu sinh tồn, thở oxy, truyền dịch nếu cần." },
      { title: "Chẩn đoán", text: "Xét nghiệm nhanh, siêu âm, X-quang tại chỗ." },
      { title: "Điều trị & theo dõi", text: "Lưu viện theo dõi, cập nhật cho gia đình từng giờ." },
    ],
    faqs: [{ q: "Ban đêm có phụ phí không?", a: "Có phụ phí ngoài giờ 100.000đ từ 21h đến 7h sáng." }],
  },
  "spa-grooming": {
    highlights: ["Mỹ phẩm chuyên dụng", "Sấy êm, không ồn", "Kiểm tra da lông miễn phí"],
    steps: [
      { title: "Kiểm tra da lông", text: "Phát hiện ve rận, nấm trước khi tắm." },
      { title: "Tắm & vệ sinh", text: "Vắt tuyến hôi, vệ sinh tai, cắt móng." },
      { title: "Sấy & cắt tỉa", text: "Tạo kiểu theo yêu cầu của gia đình." },
      { title: "Bàn giao", text: "Gửi ảnh trước/sau và lời khuyên chăm lông." },
    ],
    faqs: [{ q: "Bé hung dữ có làm được không?", a: "Được, kỹ thuật viên có kinh nghiệm xử lý; trường hợp quá căng thẳng bác sĩ có thể cho an thần nhẹ." }],
  },
  "tiem-phong": {
    highlights: ["Vắc-xin nhập khẩu chính hãng", "Sổ tiêm điện tử", "Nhắc lịch tự động"],
    steps: [
      { title: "Khám sàng lọc", text: "Đo nhiệt độ, nghe tim phổi, đảm bảo bé đủ khoẻ." },
      { title: "Tiêm", text: "Tiêm đúng mũi theo độ tuổi và lịch sử." },
      { title: "Theo dõi 15 phút", text: "Quan sát phản ứng phụ trước khi về." },
      { title: "Hẹn mũi kế tiếp", text: "Lịch nhắc gửi về tài khoản và tin nhắn." },
    ],
    faqs: [{ q: "Sau tiêm có được tắm không?", a: "Nên đợi 5–7 ngày sau mũi tiêm mới tắm cho bé." }],
  },
  "phau-thuat": {
    highlights: ["Gây mê hô hấp an toàn", "Phòng mổ vô trùng", "Theo dõi hậu phẫu"],
    steps: [
      { title: "Khám & xét nghiệm tiền mê", text: "Đánh giá gan thận, tim phổi." },
      { title: "Nhịn ăn", text: "Nhịn ăn 8 tiếng, nhịn uống 2 tiếng trước mổ." },
      { title: "Phẫu thuật", text: "Gây mê hô hấp, theo dõi monitor suốt ca." },
      { title: "Hồi tỉnh & tái khám", text: "Về trong ngày hoặc lưu viện, cắt chỉ sau 10 ngày." },
    ],
    faqs: [{ q: "Triệt sản ở độ tuổi nào là tốt nhất?", a: "Thường từ 6 tháng tuổi; bác sĩ sẽ tư vấn theo giống và thể trạng." }],
  },
};

const DEFAULT_EXTRA: Omit<ServiceExtra, "faqs"> = {
  highlights: ["Bác sĩ nhiều năm kinh nghiệm", "Báo giá trước khi làm", "Hồ sơ điện tử trọn đời"],
  steps: [
    { title: "Đặt lịch", text: "Chọn dịch vụ và khung giờ online hoặc gọi điện." },
    { title: "Tiếp nhận", text: "Lễ tân tra hồ sơ, cân và đo nhiệt độ cho bé." },
    { title: "Thực hiện", text: "Bác sĩ giải thích từng bước trước khi làm." },
    { title: "Theo dõi", text: "Nhận kết quả, đơn thuốc và lịch tái khám trong tài khoản." },
  ],
};

export function serviceExtra(slug: string): ServiceExtra {
  const e = EXTRA[slug] ?? DEFAULT_EXTRA;
  return { ...DEFAULT_EXTRA, ...e, faqs: [...(e.faqs ?? []), ...COMMON_FAQ] };
}
