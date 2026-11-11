import { redirect } from "next/navigation";

// Trang "Lịch chờ xếp" đã gộp vào Lịch khám; giữ đường dẫn cũ để link đã lưu vẫn dùng được.
export default function Page() {
  redirect("/admin/appointments");
}
