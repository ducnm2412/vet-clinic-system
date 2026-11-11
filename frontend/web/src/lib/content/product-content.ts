// MOCK-TẠM: nội dung minh hoạ cho trang chi tiết sản phẩm (backend chỉ có một dòng mô tả).
import type { Product } from "@/types";

export interface ProductContent {
  intro: string;
  highlights: string[];
  usage: string[];
  specs: Array<[string, string]>;
  reviews: Array<{ name: string; stars: number; date: string; text: string }>;
}

type Kind = "food" | "treat" | "milk" | "toy" | "wear" | "health" | "hygiene" | "gear" | "home" | "pet";

function kindOf(category: string): Kind {
  const c = category.toLowerCase();
  if (c === "chó cảnh" || c === "mèo cảnh") return "pet";
  if (c.includes("bánh thưởng") || c.includes("pate")) return "treat";
  if (c.includes("sữa tắm")) return "hygiene";
  if (c.includes("sữa")) return "milk";
  if (c.includes("thức ăn")) return "food";
  if (c.includes("đồ chơi")) return "toy";
  if (c.includes("quần áo")) return "wear";
  if (c.includes("thuốc") || c.includes("chức năng")) return "health";
  if (c.includes("bỉm") || c.includes("cát")) return "hygiene";
  if (c.includes("chuồng") || c.includes("đệm") || c.includes("nệm")) return "home";
  return "gear";
}

const BY_KIND: Record<Kind, Omit<ProductContent, "intro" | "specs" | "reviews"> & { origin: string; extra: Array<[string, string]> }> = {
  food: {
    highlights: ["Đạm động vật là thành phần đầu tiên", "Bổ sung Omega 3 & 6 cho lông bóng mượt", "Hạt vừa miệng, dễ tiêu hoá", "Không phẩm màu nhân tạo"],
    usage: ["Chia khẩu phần ngày thành 2–3 bữa theo bảng định lượng trên bao bì.", "Khi đổi loại hạt, trộn dần với hạt cũ trong 7 ngày.", "Luôn để sẵn nước sạch cạnh bát ăn.", "Bảo quản nơi khô ráo, kẹp kín miệng túi sau khi mở."],
    origin: "Thái Lan", extra: [["Thành phần chính", "Thịt gà, gạo, ngô, dầu cá, vitamin & khoáng chất"], ["Độ tuổi", "Mọi lứa tuổi"]],
  },
  treat: {
    highlights: ["Thơm ngon, kích thích bé ăn", "Dùng làm phần thưởng khi huấn luyện", "Ít béo, không gây tăng cân", "Đóng gói tiện lợi"],
    usage: ["Không vượt quá 10% năng lượng mỗi ngày của bé.", "Pate có thể trộn với hạt để tăng hương vị.", "Bảo quản lạnh và dùng hết trong 2 ngày sau khi mở."],
    origin: "Hàn Quốc", extra: [["Thành phần chính", "Thịt gà, cá ngừ, gan, chất xơ tự nhiên"]],
  },
  milk: {
    highlights: ["Công thức gần giống sữa mẹ", "Không chứa lactose, không gây tiêu chảy", "Bổ sung DHA và kháng thể"],
    usage: ["Pha với nước ấm khoảng 40°C, không dùng nước sôi.", "Bé sơ sinh: bú 6–8 lần/ngày bằng bình chuyên dụng.", "Sữa đã pha dùng trong 1 giờ, phần thừa bỏ đi."],
    origin: "Úc", extra: [["Độ tuổi", "Sơ sinh đến 3 tháng, bé ốm cần phục hồi"]],
  },
  toy: {
    highlights: ["Nhựa/cao su an toàn, không độc hại", "Giảm stress, hạn chế cắn phá đồ đạc", "Màu sắc dễ nhận biết", "Dễ vệ sinh"],
    usage: ["Chọn kích thước lớn hơn miệng bé để tránh nuốt.", "Kiểm tra đồ chơi định kỳ, thay khi bị rách.", "Chơi cùng bé 10–15 phút mỗi ngày."],
    origin: "Trung Quốc", extra: [["Chất liệu", "Cao su tự nhiên, cotton"]],
  },
  wear: {
    highlights: ["Vải cotton co giãn, thoáng mát", "Đường may chắc chắn, không cấn da", "Nhiều size từ XS đến XL"],
    usage: ["Đo vòng ngực và chiều dài lưng trước khi chọn size.", "Giặt tay bằng nước lạnh, phơi nơi thoáng.", "Không mặc liên tục quá lâu vào ngày nóng."],
    origin: "Việt Nam", extra: [["Chất liệu", "Cotton 95%, spandex 5%"], ["Size", "XS – XL"]],
  },
  health: {
    highlights: ["Theo chỉ định của bác sĩ thú y", "Hàng chính hãng, còn hạn dài", "Có hướng dẫn liều theo cân nặng"],
    usage: ["Dùng đúng liều theo cân nặng của bé.", "Hỏi bác sĩ trước khi dùng cho bé mang thai hoặc đang điều trị bệnh khác.", "Ngừng dùng và đưa bé đi khám nếu có dấu hiệu bất thường."],
    origin: "Pháp", extra: [["Lưu ý", "Để xa tầm tay trẻ em"]],
  },
  hygiene: {
    highlights: ["Khử mùi hiệu quả", "Dịu nhẹ, độ pH phù hợp da thú cưng", "Thấm hút nhanh", "Hương thơm dễ chịu, lưu lâu"],
    usage: ["Sữa tắm: làm ướt lông, xoa đều, để 3–5 phút rồi xả sạch.", "Cát/bỉm: thay mới thường xuyên, dọn chất thải mỗi ngày.", "Tránh để sản phẩm dính vào mắt bé."],
    origin: "Nhật Bản", extra: [["Phù hợp", "Da nhạy cảm"]],
  },
  gear: {
    highlights: ["Chất liệu bền, chịu lực tốt", "Thiết kế gọn nhẹ, dễ mang theo", "Khoá chắc chắn, an toàn khi di chuyển"],
    usage: ["Chọn kích cỡ theo cân nặng của bé.", "Kiểm tra khoá và dây trước mỗi lần dùng.", "Vệ sinh bằng khăn ẩm, phơi khô."],
    origin: "Trung Quốc", extra: [["Chất liệu", "Nylon, nhựa ABS"]],
  },
  home: {
    highlights: ["Êm ái, giữ ấm tốt", "Vỏ tháo rời giặt được", "Đáy chống trượt"],
    usage: ["Đặt ở nơi khô ráo, tránh gió lùa.", "Giặt vỏ mỗi 1–2 tuần.", "Phơi nắng định kỳ để diệt khuẩn."],
    origin: "Việt Nam", extra: [["Chất liệu", "Vải nỉ, bông PP"]],
  },
  pet: {
    highlights: ["Đã tiêm phòng & tẩy giun", "Có sổ sức khoẻ theo bé", "Bảo hành sức khoẻ 7 ngày", "Tư vấn chăm sóc trọn đời"],
    usage: ["Tuần đầu cho ăn đúng loại thức ăn bé đang dùng.", "Hạn chế tắm trong 7 ngày đầu về nhà mới.", "Đưa bé tái khám miễn phí sau 7 ngày."],
    origin: "Trại giống đối tác", extra: [["Độ tuổi", "2–3 tháng"], ["Giấy tờ", "Sổ tiêm, hợp đồng bảo hành"]],
  },
};

const NAMES = ["Chị Lan (Q.3)", "Anh Minh", "Hà Phương", "Bố của Mochi", "Tuấn Anh", "Mẹ Bơ", "Ngọc Trâm", "Anh Khoa"];
const TEXTS = [
  "Bé nhà mình rất thích, giao hàng nhanh, đóng gói cẩn thận.",
  "Chất lượng tốt so với giá, sẽ ủng hộ tiếp.",
  "Được bác sĩ tư vấn kỹ trước khi mua, rất yên tâm.",
  "Dùng được 2 tuần thấy bé khoẻ hơn hẳn.",
  "Sản phẩm đúng mô tả, nhân viên nhiệt tình.",
  "Hơi lâu mới giao nhưng hàng ổn, bé dùng hợp.",
];

function seed(id: string) {
  let h = 0;
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h;
}

export function productContent(p: Product): ProductContent {
  const kind = kindOf(p.categoryName);
  const k = BY_KIND[kind];
  const s = seed(p.id);
  const forWhom = p.categoryName.toLowerCase().startsWith("mèo") ? "các bé mèo" : "các bé chó";
  const intro =
    kind === "pet"
      ? `${p.name} được chăm sóc tại trại đối tác của phòng khám, khám sức khoẻ kỹ trước khi về nhà mới. Bé hiền, đã quen ăn hạt và đi vệ sinh đúng chỗ.`
      : `${p.name} là lựa chọn được nhiều gia đình tin dùng cho ${forWhom}. Sản phẩm được đội ngũ bác sĩ của phòng khám chọn lọc, nhập trực tiếp từ nhà phân phối chính hãng.`;
  const reviews = Array.from({ length: 3 }, (_, i) => ({
    name: NAMES[(s + i * 3) % NAMES.length],
    stars: i === 2 && s % 2 ? 4 : 5,
    date: `${((s + i) % 27) + 1}/${((s >> 3) + i) % 9 + 1}/2026`,
    text: TEXTS[(s + i * 5) % TEXTS.length],
  }));
  return {
    intro,
    highlights: k.highlights,
    usage: k.usage,
    specs: [["Xuất xứ", k.origin], ...k.extra, ["Bảo quản", "Nơi khô ráo, tránh ánh nắng trực tiếp"]],
    reviews,
  };
}
