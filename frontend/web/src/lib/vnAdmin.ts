/**
 * Danh sách đơn vị hành chính Việt Nam sau sáp nhập: 34 tỉnh/thành, phường/xã nằm thẳng dưới tỉnh
 * (không còn cấp quận/huyện). Nguồn provinces.open-api.vn v2, đã nhúng sẵn vào public/data để
 * form địa chỉ không phụ thuộc mạng ngoài và không dính CORS.
 */
export type AdminData = Array<[province: string, wards: string[]]>;

let cache: Promise<AdminData> | null = null;

/** Tải một lần cho cả phiên; lỗi thì bỏ cache để lần mở form sau thử lại. */
export function loadAdminData(): Promise<AdminData> {
  cache ??= fetch("/data/vn-admin.json")
    .then((res) => {
      if (!res.ok) throw new Error(`vn-admin.json: ${res.status}`);
      return res.json() as Promise<AdminData>;
    })
    .catch((err) => {
      cache = null;
      throw err;
    });
  return cache;
}

const PREFIXES = ["thanh pho ", "tinh ", "phuong ", "xa ", "dac khu ", "thi tran ", "quan ", "huyen ", "thi xa "];

/** Bỏ dấu, hạ chữ thường, gộp khoảng trắng: "Bến Nghé" và "ben nghe" cho cùng một khoá tìm kiếm. */
export function fold(name: string): string {
  return name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "d")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

/** Thêm bước cắt tiền tố hành chính để "TP. Hồ Chí Minh" khớp "Thành phố Hồ Chí Minh". */
function normalize(name: string): string {
  let s = fold(name).replace(/\btp\.?\s/g, "thanh pho ");
  for (const p of PREFIXES) {
    if (s.startsWith(p)) {
      s = s.slice(p.length);
      break;
    }
  }
  return s;
}

/** Tên tỉnh trong danh sách khớp với một trong các tên ứng viên; không có thì undefined. */
export function matchProvince(data: AdminData, candidates: Array<string | undefined>): string | undefined {
  const wanted = candidates.filter((c): c is string => !!c).map(normalize);
  for (const w of wanted) {
    const hit = data.find(([name]) => normalize(name) === w);
    if (hit) return hit[0];
  }
  return undefined;
}

export function matchWard(data: AdminData, province: string, candidates: Array<string | undefined>): string | undefined {
  const wards = data.find(([name]) => name === province)?.[1];
  if (!wards) return undefined;
  const wanted = candidates.filter((c): c is string => !!c).map(normalize);
  for (const w of wanted) {
    const hit = wards.find((name) => normalize(name) === w);
    if (hit) return hit;
  }
  return undefined;
}
