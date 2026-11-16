/**
 * Số lượng thuốc gợi ý cho phiếu thu, suy ra từ đơn bác sĩ kê: số lần uống mỗi ngày × số ngày.
 *
 * Đơn lưu tần suất và số ngày dạng chữ/số tự do nên đây chỉ là GỢI Ý điền sẵn — nhân viên quầy vẫn
 * sửa được. Không suy ra được thì trả 1 (như trước đây) chứ không đoán bừa.
 *
 *   "1 lần/ngày" · 16 ngày  → 16
 *   "2 lần / ngày" · 7 ngày → 14
 *   "1-2 lần/ngày" · 16 ngày → 16   (khoảng thì lấy cận dưới: lượng tối thiểu đủ dùng theo chỉ định)
 *   "1 lần" (liều đơn)      → 1     (không có "ngày" nên không nhân với số ngày)
 *   "Theo chỉ định của bác sĩ" → 1
 */
export function suggestQuantity(frequency: string, durationDays: number | null): number {
  const text = frequency.toLowerCase();
  const days = durationDays && durationDays > 0 ? durationDays : 1;

  // "mỗi 8 giờ" → 24/8 = 3 lần mỗi ngày.
  const everyHours = /m[ỗo]i\s*(\d+)\s*gi/.exec(text);
  if (everyHours) {
    const hours = Number(everyHours[1]);
    if (hours > 0 && hours <= 24) return Math.max(1, Math.floor(24 / hours)) * days;
  }

  // "2 lần", "1-2 lần", "1 – 2 lần": lấy số đầu tiên (cận dưới nếu là khoảng).
  const times = /(\d+)(?:\s*[-–]\s*\d+)?\s*lần/.exec(text);
  if (!times) return 1;
  const perDay = Number(times[1]);
  if (perDay < 1) return 1;
  // Không nói "mỗi ngày" thì là tổng số lần cả đợt (vd. "1 lần"), không nhân với số ngày.
  return text.includes("ngày") ? perDay * days : perDay;
}
