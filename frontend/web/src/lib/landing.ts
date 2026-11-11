import { attendanceApi } from "@/lib/api";
import { homeFor, isNextAllowedForRoles } from "@/config/nav";
import type { Role } from "@/types";

/**
 * Trang đưa người dùng tới ngay sau khi đăng nhập.
 *
 * - Đang dở một trang (`next`, ví dụ phiên hết hạn giữa chừng) thì quay lại đúng chỗ đó.
 * - Bác sĩ và nhân viên quầy mà hôm nay chưa chấm công thì vào thẳng trang chấm công, để việc đầu
 *   tiên trong ngày là bấm "Vào ca". Chấm công rồi thì về trang chính như thường.
 * - Vai trò khác (ADMIN, khách) giữ nguyên: ADMIN không có chấm công cá nhân trong menu.
 *
 * Theo "chưa chấm công hôm nay" chứ không theo "lần đăng nhập đầu trong ngày": cách đó đúng cả
 * khi đổi máy hay xoá dữ liệu trình duyệt, và không cần lưu thêm gì ở máy người dùng. Đổi lại,
 * đăng nhập lại lúc chưa chấm công thì vẫn được nhắc.
 *
 * Không tra được trạng thái chấm công (mạng, staff-service chưa sẵn sàng) thì về trang chính —
 * không để việc phụ này chặn đăng nhập.
 */
export async function landingFor(roles: Role[], next: string | null): Promise<string> {
  if (next && isNextAllowedForRoles(next, roles)) return next;

  const home = homeFor(roles);
  if (home !== "/doctor" && home !== "/staff") return home;

  try {
    const today = await attendanceApi.today();
    return today ? home : `${home}/attendance`;
  } catch {
    return home;
  }
}
