/*
  Trang giới thiệu — bố cục theo ảnh mẫu (đầu trang + bảng 4 thông tin nhanh; khối "Năng lực"
  hai cột với lĩnh vực và 3 điểm mạnh đánh số; "Phương châm"; "Đội ngũ" với 4 tổ chuyên môn và
  hai ảnh), phong cách cam – xanh rêu của site. Nội dung giới thiệu là nội dung tạm, thay bằng
  thông tin thật của phòng khám khi triển khai.
*/

import Image from "next/image";
import Link from "next/link";
import { ArrowRight, PawPrint } from "lucide-react";
import { CLINIC } from "@/config/clinic";
import { Leaf, Paw } from "@/components/site/Decor";
import { CareBanner } from "@/components/site/CareBanner";

const ORANGE = "#f57c1f";
const GREEN = "#1f4a3c";

export const metadata = { title: "Giới thiệu | Vet Clinic" };

const FACTS = [
  { label: "Năm thành lập", value: "2018" },
  { label: "Phòng khám", value: CLINIC.address },
  { label: "Đội ngũ", value: "12 bác sĩ & kỹ thuật viên" },
  { label: "Đã chăm sóc", value: "Hơn 15.000 thú cưng" },
];

const FIELDS = [
  { title: "Khám & điều trị", body: "Khám tổng quát, nội khoa, cấp cứu 24/7" },
  { title: "Phẫu thuật", body: "Triệt sản, tiểu phẫu, đại phẫu vô trùng" },
  { title: "Phòng bệnh", body: "Tiêm phòng, tẩy giun, tư vấn dinh dưỡng" },
  { title: "Spa & làm đẹp", body: "Tắm sấy, cắt tỉa, vệ sinh tai móng" },
];

const STRENGTHS = [
  { title: "Bác sĩ có chứng chỉ hành nghề", body: "Mỗi ca khám đều do bác sĩ thú y được cấp phép đảm nhận, có hồ sơ chuyên môn minh bạch." },
  { title: "Thiết bị chẩn đoán hiện đại", body: "Máy xét nghiệm máu, siêu âm, X-quang cho kết quả nhanh ngay tại phòng khám." },
  { title: "Bệnh án điện tử trọn đời", body: "Toàn bộ lịch sử khám, đơn thuốc, lịch tiêm được lưu lại — lần sau không phải kể lại từ đầu." },
];

const TEAMS = [
  { count: 5, title: "Bác sĩ thú y", body: "Khám, chẩn đoán và điều trị nội – ngoại khoa cho chó mèo." },
  { count: 3, title: "Kỹ thuật viên", body: "Xét nghiệm, siêu âm, hỗ trợ phẫu thuật và chăm sóc hậu phẫu." },
  { count: 2, title: "Chuyên viên spa", body: "Tắm sấy, cắt tỉa tạo kiểu, vệ sinh tai móng cho thú cưng." },
  { count: 2, title: "Chăm sóc khách hàng", body: "Tư vấn, đặt lịch, theo dõi và nhắc lịch tái khám, tiêm phòng." },
];

export default function AboutPage() {
  return (
    <div className="bg-white">
      {/* ---------- Đầu trang ---------- */}
      <div className="mx-auto max-w-[1440px] px-4 pt-4 sm:px-6">
        <section className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] px-6 py-12 sm:px-12 md:py-16">
          <Paw className="left-[44%] top-10 size-9 -rotate-12 text-[#f9a55c]" />
          <Leaf className="-bottom-4 left-[48%] hidden h-24 w-10 rotate-[35deg] bg-[linear-gradient(135deg,#7cc36b,#3f8f3a)] md:block" />
          <div className="relative grid items-center gap-10 lg:grid-cols-[1.1fr_1fr]">
            <div>
              <nav className="flex items-center gap-2 text-sm text-[#6b5a4e]">
                <Link href="/" className="hover:underline">Trang chủ</Link>
                <span>/</span>
                <span className="font-semibold" style={{ color: ORANGE }}>Về chúng tôi</span>
              </nav>
              <h1 className="mt-5 text-[44px] font-extrabold leading-none sm:text-[64px]" style={{ color: GREEN }}>
                Về Vet Clinic<span style={{ color: ORANGE }}>.</span>
              </h1>
              <p className="mt-5 max-w-xl text-[16px] leading-relaxed text-[#3b2a20]">
                Phòng khám thú y dành cho chó mèo tại Cần Thơ. Chúng tôi kết hợp chuyên môn y khoa, thiết bị hiện đại và
                sự tận tâm để mỗi chuyến thăm khám của bé đều nhẹ nhàng và an toàn.
              </p>
            </div>
            <dl className="grid grid-cols-2 overflow-hidden rounded-3xl bg-white/80 shadow-[0_8px_30px_-12px_rgba(160,80,20,0.35)]">
              {FACTS.map((f, i) => (
                <div key={f.label} className={`p-5 ${i % 2 === 0 ? "border-r" : ""} ${i < 2 ? "border-b" : ""} border-[#f1e6dc]`}>
                  <dt className="text-[11px] font-semibold uppercase tracking-[0.15em] text-[#9a8b80]">{f.label}</dt>
                  <dd className="mt-2 font-bold leading-snug" style={{ color: GREEN }}>{f.value}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>
      </div>

      {/* ---------- Năng lực ---------- */}
      <section className="mx-auto max-w-[1200px] px-4 py-16 sm:px-6">
        <Eyebrow>Năng lực</Eyebrow>
        <h2 className="mt-3 max-w-4xl text-[30px] font-extrabold leading-tight sm:text-[40px]" style={{ color: GREEN }}>
          Vet Clinic – chăm sóc toàn diện <span style={{ color: ORANGE }}>từ phòng khám đến tận nhà</span>
        </h2>

        <div className="mt-10 grid gap-12 lg:grid-cols-2">
          <div>
            <p className="text-[15px] leading-relaxed text-[#3b2a20]">
              Vet Clinic được thành lập năm 2018, hoạt động trong các lĩnh vực:
            </p>
            <ul className="mt-5 grid grid-cols-2 gap-3">
              {FIELDS.map((f) => (
                <li key={f.title} className="rounded-2xl border border-[#f1e6dc] bg-[#fffaf4] p-4">
                  <p className="flex items-center gap-2 text-sm font-bold" style={{ color: GREEN }}>
                    <PawPrint className="size-4" style={{ color: ORANGE }} /> {f.title}
                  </p>
                  <p className="mt-1 text-xs text-[#6b5a4e]">{f.body}</p>
                </li>
              ))}
            </ul>
            <p className="mt-5 text-[15px] leading-relaxed text-[#3b2a20]">
              Đội ngũ bác sĩ, kỹ thuật viên và chuyên viên chăm sóc làm việc theo quy trình chuẩn, thường xuyên cập nhật
              kiến thức mới và luôn đặt sức khoẻ, sự thoải mái của thú cưng lên hàng đầu.
            </p>
            <div className="mt-6 grid grid-cols-[1.4fr_1fr] gap-3">
              <div className="relative aspect-[4/3] overflow-hidden rounded-2xl">
                <Image src="/img/cham-soc.jpg" alt="Thú cưng vui vẻ sau khi khám" fill sizes="(max-width:1024px) 60vw, 30vw" className="object-cover" />
              </div>
              <div className="relative aspect-[3/4] overflow-hidden rounded-2xl">
                <Image src="/img/meo.jpg" alt="Mèo tại phòng khám" fill sizes="(max-width:1024px) 40vw, 20vw" className="object-cover" />
              </div>
            </div>
            <p className="mt-2 text-xs text-[#9a8b80]">Những vị khách nhỏ quen thuộc của Vet Clinic.</p>
          </div>

          <ol className="divide-y divide-[#f1e6dc] border-y border-[#f1e6dc] self-start">
            {STRENGTHS.map((s, i) => (
              <li key={s.title} className="flex gap-5 py-6">
                <span className="text-3xl font-extrabold" style={{ color: ORANGE }}>{String(i + 1).padStart(2, "0")}</span>
                <div>
                  <p className="text-lg font-extrabold uppercase" style={{ color: GREEN }}>{s.title}</p>
                  <p className="mt-1 text-sm leading-relaxed text-[#6b5a4e]">{s.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ---------- Phương châm ---------- */}
      <section className="mx-auto grid max-w-[1200px] gap-8 px-4 pb-16 sm:px-6 lg:grid-cols-2">
        <div>
          <Eyebrow>Phương châm hoạt động</Eyebrow>
          <h2 className="mt-3 text-[30px] font-extrabold leading-tight sm:text-[36px]" style={{ color: GREEN }}>
            Tận tâm <span style={{ color: ORANGE }}>–</span> Chuyên nghiệp <span style={{ color: ORANGE }}>–</span> Minh bạch
          </h2>
        </div>
        <div className="space-y-4 text-[15px] leading-relaxed text-[#3b2a20]">
          <p>
            Đây là phương châm xuyên suốt trong mọi hoạt động của Vet Clinic. Sự an tâm của chủ nuôi và sức khoẻ của
            thú cưng chính là thước đo thành công của chúng tôi mỗi ngày.
          </p>
          <p>
            Hướng tới mục tiêu trở thành phòng khám thú y đáng tin cậy nhất khu vực, Vet Clinic không ngừng đầu tư
            thiết bị, đào tạo đội ngũ và số hoá quy trình — từ đặt lịch online, bệnh án điện tử đến nhắc lịch tiêm
            phòng — để việc chăm sóc thú cưng trở nên dễ dàng hơn cho mọi gia đình.
          </p>
        </div>
      </section>

      {/* ---------- Đội ngũ ---------- */}
      <section className="relative overflow-hidden bg-[linear-gradient(180deg,#fffaf4,#fff0e2)] py-16">
        <Paw className="right-[6%] top-10 size-10 rotate-12 text-[#f9a55c]" />
        <div className="relative mx-auto max-w-[1200px] px-4 sm:px-6">
          <Eyebrow>Đội ngũ nhân lực</Eyebrow>
          <h2 className="mt-3 text-[30px] font-extrabold leading-tight sm:text-[40px]" style={{ color: GREEN }}>
            12 nhân sự, <span style={{ color: ORANGE }}>bốn tổ chuyên môn</span>
          </h2>
          <p className="mt-4 max-w-2xl text-[15px] leading-relaxed text-[#3b2a20]">
            Quy trình phối hợp khép kín giữa các bộ phận giúp mỗi ca khám, phẫu thuật hay chăm sóc đều được theo dõi
            chặt chẽ từ lúc tiếp nhận đến khi bé về nhà.
          </p>

          <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {TEAMS.map((t) => (
              <li key={t.title} className="rounded-3xl bg-white p-6 shadow-[0_4px_24px_-10px_rgba(0,0,0,0.15)]">
                <p className="flex items-start gap-1">
                  <span className="text-5xl font-extrabold" style={{ color: ORANGE }}>{t.count}</span>
                  <span className="mt-1 text-xs text-[#9a8b80]">người</span>
                </p>
                <p className="mt-4 font-extrabold uppercase" style={{ color: GREEN }}>{t.title}</p>
                <p className="mt-2 text-sm leading-relaxed text-[#6b5a4e]">{t.body}</p>
              </li>
            ))}
          </ul>

          <div className="mt-6 grid gap-4 md:grid-cols-2">
            <div className="relative aspect-[16/9] overflow-hidden rounded-3xl">
              <Image src="/img/cho-con.jpg" alt="Chú chó khoẻ mạnh" fill sizes="(max-width:768px) 100vw, 50vw" className="object-cover" />
            </div>
            <div className="relative aspect-[16/9] overflow-hidden rounded-3xl">
              <Image src="/img/hero.jpg" alt="Chú chó vui vẻ" fill sizes="(max-width:768px) 100vw, 50vw" className="object-cover" />
            </div>
          </div>

          <div className="mt-10 flex flex-wrap gap-3">
            <Link href="/services" className="inline-flex h-12 items-center gap-2 rounded-full px-6 text-sm font-semibold text-white" style={{ background: ORANGE }}>
              Xem dịch vụ <ArrowRight className="size-4" />
            </Link>
            <Link href="/contact" className="inline-flex h-12 items-center gap-2 rounded-full border-2 px-6 text-sm font-semibold" style={{ borderColor: GREEN, color: GREEN }}>
              Liên hệ với chúng tôi
            </Link>
          </div>
        </div>
      </section>

      <CareBanner
        title={<>Chăm sóc thú cưng<br />là yêu thương</>}
        subtitle="Đồng hành cùng bạn và thú cưng mỗi ngày"
        cta={{ href: "/appointments/create", label: "Đặt lịch khám" }}
        image={{ src: "/img/cho-meo-ngu.png", alt: "Chú chó con và mèo con ngủ cạnh nhau", width: 672, height: 230 }}
      />
    </div>
  );
}

function Eyebrow({ children }: { children: React.ReactNode }) {
  return (
    <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.2em]" style={{ color: ORANGE }}>
      <span className="h-px w-8" style={{ background: ORANGE }} /> {children}
    </p>
  );
}
