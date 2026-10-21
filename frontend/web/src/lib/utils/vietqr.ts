/**
 * Dựng dữ liệu mã QR chuyển khoản VietQR (chuẩn EMVCo/Napas 247) có sẵn số tiền và nội dung, từ
 * mã QR tĩnh của chính chủ tài khoản (ngân hàng hoặc ví như MoMo đều xuất được). Mã tĩnh đã chứa
 * sẵn ngân hàng/ví nhận và số tài khoản nên không phải tự ghép từng trường; ở đây chỉ đổi nó
 * thành mã động: thêm số tiền (tag 54), nội dung chuyển khoản (tag 62.08) rồi tính lại CRC (tag 63).
 */

interface Tlv {
  tag: string;
  value: string;
}

function parseTlv(data: string): Tlv[] | null {
  const out: Tlv[] = [];
  let i = 0;
  while (i < data.length) {
    if (i + 4 > data.length) return null;
    const tag = data.slice(i, i + 2);
    const len = Number(data.slice(i + 2, i + 4));
    if (!Number.isInteger(len) || i + 4 + len > data.length) return null;
    out.push({ tag, value: data.slice(i + 4, i + 4 + len) });
    i += 4 + len;
  }
  return out;
}

function encodeTlv(items: Tlv[]): string {
  return items.map(({ tag, value }) => `${tag}${String(value.length).padStart(2, "0")}${value}`).join("");
}

/** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) như chuẩn EMVCo yêu cầu, in hoa 4 ký tự hex. */
function crc16(text: string): string {
  let crc = 0xffff;
  for (let i = 0; i < text.length; i++) {
    crc ^= text.charCodeAt(i) << 8;
    for (let bit = 0; bit < 8; bit++) {
      crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, "0");
}

/** Nội dung chuyển khoản: chỉ chữ/số/khoảng trắng không dấu, tối đa 25 ký tự theo chuẩn VietQR. */
export function sanitizeTransferContent(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .replace(/[^A-Za-z0-9 ]/g, "")
    .trim()
    .slice(0, 25);
}

/**
 * Trả về chuỗi để vẽ thành mã QR, hoặc null khi mã tĩnh cấu hình sai / số tiền không hợp lệ
 * (khi đó giao diện chỉ hiện thông tin chuyển khoản bằng chữ, không vẽ mã).
 */
export function buildTransferQrPayload(template: string, amount: number, content: string): string | null {
  if (!Number.isInteger(amount) || amount <= 0) return null;

  const items = parseTlv(template.trim());
  if (!items || items.length === 0 || items[0].tag !== "00") return null;

  const next: Tlv[] = [];
  for (const item of items) {
    // CRC cũ và số tiền cũ (nếu mã tĩnh đã có) bỏ đi, dựng lại bên dưới.
    if (item.tag === "63" || item.tag === "54") continue;
    if (item.tag === "01") {
      // 11 = mã dùng nhiều lần, 12 = mã dùng cho một giao dịch (có số tiền).
      next.push({ tag: "01", value: "12" });
    } else if (item.tag === "62") {
      // Giữ các trường phụ sẵn có (vd. nhãn tham chiếu của MoMo), chỉ đặt lại nội dung (08).
      const sub = (parseTlv(item.value) ?? []).filter((s) => s.tag !== "08");
      const cleaned = sanitizeTransferContent(content);
      if (cleaned) sub.push({ tag: "08", value: cleaned });
      sub.sort((a, b) => a.tag.localeCompare(b.tag));
      next.push({ tag: "62", value: encodeTlv(sub) });
    } else {
      next.push(item);
    }
    // Số tiền (54) nằm ngay sau đơn vị tiền tệ (53).
    if (item.tag === "53") next.push({ tag: "54", value: String(amount) });
  }

  if (!next.some((t) => t.tag === "62")) {
    const cleaned = sanitizeTransferContent(content);
    if (cleaned) next.push({ tag: "62", value: encodeTlv([{ tag: "08", value: cleaned }]) });
  }

  const body = encodeTlv(next) + "6304";
  return body + crc16(body);
}

/** Phục vụ kiểm thử: tính lại CRC của một chuỗi VietQR có sẵn, trả về true nếu CRC đúng. */
export function hasValidCrc(payload: string): boolean {
  if (payload.length < 8) return false;
  return crc16(payload.slice(0, -4)) === payload.slice(-4).toUpperCase();
}
