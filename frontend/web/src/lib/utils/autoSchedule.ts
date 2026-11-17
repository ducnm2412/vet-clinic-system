/**
 * Xếp ca tự động theo tuần: tham lam theo công bằng. Hàm thuần (không gọi mạng, không đụng giao diện) để
 * kiểm tra riêng được; trang Lịch làm việc chỉ lấy kết quả làm bản xem trước cho admin sửa rồi mới áp dụng.
 *
 * Với từng ngày chưa qua, đếm số người đã có ca (kể cả ca admin đã xếp tay) và bù cho đủ `perDay`. Người được
 * chọn theo thứ tự ưu tiên:
 *   1. ít ca nhất trong tuần này (chia đều trong tuần),
 *   2. ít ca nhất trong các tuần trước (bù cho người trực ít),
 *   3. hôm trước không đi làm (tránh làm liền nhiều ngày),
 *   4. nếu là cuối tuần: ít ca cuối tuần nhất trong các tuần trước,
 *   5. tên (để kết quả luôn giống nhau với cùng dữ liệu).
 * Mỗi người tối đa `maxPerWeek` ngày một tuần, nên luôn còn ít nhất một ngày nghỉ. Ngày đã báo nghỉ thì không xếp.
 */

export const AUTO_SCHEDULE_PER_DAY = 2;
export const AUTO_SCHEDULE_MAX_PER_WEEK = 5;

export interface SchedulePerson {
  id: string;
  name: string;
}

export interface AutoScheduleInput {
  /** Bảy ngày của tuần, dạng yyyy-MM-dd. */
  days: string[];
  /** Hôm nay (yyyy-MM-dd); ngày đã qua không xếp. */
  today: string;
  people: SchedulePerson[];
  /** Ca đã có trong tuần này, dạng "userId|ngày". Tính vào số người của ngày và số ngày của người. */
  existing: ReadonlySet<string>;
  /** Ca của các tuần trước, dạng "userId|ngày", để chia công bằng về lâu dài. */
  history: ReadonlySet<string>;
  /** Ngày nghỉ đã ghi, dạng "userId|ngày": người đó không được xếp vào ngày này. */
  unavailable?: ReadonlySet<string>;
  perDay?: number;
  maxPerWeek?: number;
}

export interface AutoScheduleResult {
  /** Ca MỚI đề xuất, dạng "userId|ngày" (không gồm ca đã có). */
  assigned: string[];
  /** Ngày không đủ người dù đã xếp hết mức cho phép. */
  shortfalls: { date: string; missing: number }[];
}

function parse(dateIso: string): Date {
  const [y, m, d] = dateIso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function previousDay(dateIso: string): string {
  const date = parse(dateIso);
  date.setDate(date.getDate() - 1);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}

function isWeekend(dateIso: string): boolean {
  const day = parse(dateIso).getDay();
  return day === 0 || day === 6;
}

export function autoSchedule(input: AutoScheduleInput): AutoScheduleResult {
  const perDay = input.perDay ?? AUTO_SCHEDULE_PER_DAY;
  const maxPerWeek = input.maxPerWeek ?? AUTO_SCHEDULE_MAX_PER_WEEK;

  // Tổng ca và ca cuối tuần của từng người ở các tuần trước.
  const historyTotal = new Map<string, number>();
  const historyWeekend = new Map<string, number>();
  for (const key of input.history) {
    const [userId, date] = key.split("|");
    historyTotal.set(userId, (historyTotal.get(userId) ?? 0) + 1);
    if (isWeekend(date)) historyWeekend.set(userId, (historyWeekend.get(userId) ?? 0) + 1);
  }

  // Trạng thái đang xây: ai có ca ngày nào (gồm ca đã có và ca vừa đề xuất).
  const working = new Set(input.existing);
  const weekCount = new Map<string, number>();
  for (const p of input.people) {
    weekCount.set(p.id, input.days.filter((d) => working.has(`${p.id}|${d}`)).length);
  }
  const worked = (userId: string, date: string) => working.has(`${userId}|${date}`) || input.history.has(`${userId}|${date}`);

  const assigned: string[] = [];
  const shortfalls: { date: string; missing: number }[] = [];

  for (const day of input.days) {
    if (day < input.today) continue;

    const have = input.people.filter((p) => working.has(`${p.id}|${day}`)).length;
    let need = perDay - have;
    if (need <= 0) continue;

    const eve = previousDay(day);
    const weekend = isWeekend(day);
    const candidates = input.people
      .filter(
        (p) =>
          !working.has(`${p.id}|${day}`) &&
          !input.unavailable?.has(`${p.id}|${day}`) &&
          (weekCount.get(p.id) ?? 0) < maxPerWeek,
      )
      .sort(
        (a, b) =>
          (weekCount.get(a.id) ?? 0) - (weekCount.get(b.id) ?? 0) ||
          (historyTotal.get(a.id) ?? 0) - (historyTotal.get(b.id) ?? 0) ||
          Number(worked(a.id, eve)) - Number(worked(b.id, eve)) ||
          (weekend ? (historyWeekend.get(a.id) ?? 0) - (historyWeekend.get(b.id) ?? 0) : 0) ||
          a.name.localeCompare(b.name, "vi"),
      );

    for (const person of candidates) {
      if (need === 0) break;
      const key = `${person.id}|${day}`;
      working.add(key);
      assigned.push(key);
      weekCount.set(person.id, (weekCount.get(person.id) ?? 0) + 1);
      need -= 1;
    }
    if (need > 0) shortfalls.push({ date: day, missing: need });
  }

  return { assigned, shortfalls };
}
