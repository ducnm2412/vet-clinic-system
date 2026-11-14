"use client";

/* Các section bổ sung cho trang chủ: banner khuyến mãi đôi, quy trình khám, banner cấp cứu, câu hỏi thường gặp. */

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  ArrowRight,
  CalendarCheck,
  ChevronDown,
  ClipboardList,
  HeartPulse,
  Phone,
  PawPrint,
  Siren,
  Stethoscope,
  Syringe,
  ShoppingBag,
} from "lucide-react";
import { CLINIC } from "@/config/clinic";
import { Paw } from "@/components/site/Decor";

const ORANGE = "#f57c1f";
const GREEN = "#1f4a3c";

function Heading({ eyebrow, title, accent, center }: { eyebrow: string; title: string; accent?: string; center?: boolean }) {
  return (
    <div className={center ? "mx-auto max-w-2xl text-center" : ""}>
      <span className="inline-flex items-center gap-2 rounded-full bg-[#ffe1c7] px-4 py-1.5 text-sm font-medium text-[#3b2a20]">
        <PawPrint className="size-4" style={{ color: ORANGE }} /> {eyebrow}
      </span>
      <h2 className="mt-4 text-[28px] font-extrabold leading-[1.2] sm:text-[34px]" style={{ color: GREEN }}>
        {title} {accent && <span style={{ color: ORANGE }}>{accent}</span>}
      </h2>
    </div>
  );
}

/* ---------- Banner đôi ---------- */

export function PromoBanners() {
  return (
    <section aria-label="Ưu đãi" data-stagger className="grid gap-5 md:grid-cols-2">
      <Link
        href="/services/tiem-phong"
        className="group relative flex min-h-[220px] overflow-hidden rounded-[28px] bg-[linear-gradient(120deg,#1f4a3c_0%,#2f6b56_100%)] p-7 text-white"
      >
        <Paw className="float-amb right-[42%] top-5 size-8 rotate-12 text-white/20" />
        <div className="relative z-10 max-w-[58%]">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold">
            <Syringe className="size-3.5" /> Tiêm phòng – tẩy giun
          </span>
          <p className="mt-3 text-2xl font-extrabold leading-tight">
            Gói tiêm phòng <span className="text-[#ffb36b]">trọn năm</span>
          </p>
          <p className="mt-2 text-sm text-white/80">Sổ tiêm điện tử, nhắc lịch tự động từng mũi.</p>
          <span className="mt-5 inline-flex h-10 items-center gap-2 rounded-full bg-white px-5 text-sm font-semibold" style={{ color: GREEN }}>
            Đặt lịch tiêm <ArrowRight className="size-4 transition group-hover:translate-x-0.5" />
          </span>
        </div>
        <div className="absolute -bottom-6 -right-6 aspect-square w-[50%] overflow-hidden rounded-full border-[6px] border-white/20">
          <Image src="/img/cho-con.jpg" alt="" fill sizes="(max-width:768px) 50vw, 25vw" className="object-cover transition duration-500 group-hover:scale-105" />
        </div>
      </Link>

      <Link
        href="/products"
        className="group relative flex min-h-[220px] overflow-hidden rounded-[28px] bg-[linear-gradient(120deg,#ffe9d6_0%,#ffc995_100%)] p-7"
      >
        <Paw className="float-amb right-[42%] top-5 size-8 -rotate-12 text-white/70" />
        <div className="relative z-10 max-w-[58%]">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/70 px-3 py-1 text-xs font-semibold" style={{ color: ORANGE }}>
            <ShoppingBag className="size-3.5" /> Cửa hàng
          </span>
          <p className="mt-3 text-2xl font-extrabold leading-tight" style={{ color: GREEN }}>
            Thức ăn & phụ kiện <span style={{ color: ORANGE }}>chính hãng</span>
          </p>
          <p className="mt-2 text-sm text-[#4a3a30]">Bác sĩ tư vấn chọn đúng loại hợp với bé.</p>
          <span className="mt-5 inline-flex h-10 items-center gap-2 rounded-full px-5 text-sm font-semibold text-white" style={{ background: ORANGE }}>
            Mua sắm ngay <ArrowRight className="size-4 transition group-hover:translate-x-0.5" />
          </span>
        </div>
        <div
          className="absolute bottom-0 right-0 h-[90%] w-[50%]"
          style={{ maskImage: "radial-gradient(ellipse 60% 65% at 55% 60%, #000 55%, transparent 100%)", WebkitMaskImage: "radial-gradient(ellipse 60% 65% at 55% 60%, #000 55%, transparent 100%)" }}
        >
          <Image src="/img/banner-shop.png" alt="" fill sizes="(max-width:768px) 50vw, 25vw" className="object-cover transition duration-500 group-hover:scale-105" />
        </div>
      </Link>
    </section>
  );
}

/* ---------- Quy trình khám ---------- */

const STEPS = [
  { icon: CalendarCheck, title: "Đặt lịch online", body: "Chọn dịch vụ, ngày và khung giờ còn trống chỉ trong 1 phút." },
  { icon: ClipboardList, title: "Tiếp nhận & hỏi bệnh", body: "Nhân viên ghi nhận thông tin, cân đo và tình trạng của bé." },
  { icon: Stethoscope, title: "Bác sĩ thăm khám", body: "Khám, xét nghiệm khi cần, giải thích rõ và báo giá trước." },
  { icon: HeartPulse, title: "Theo dõi sau khám", body: "Bệnh án lưu trong tài khoản, nhắc lịch tái khám tự động." },
];

export function CareProcess() {
  return (
    <section>
      <Heading eyebrow="Quy trình" title="Khám cho bé chỉ với" accent="4 bước đơn giản" center />
      <ol data-stagger className="relative mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <span aria-hidden className="absolute left-[12%] right-[12%] top-9 hidden border-t-2 border-dashed border-[#f3c9a1] lg:block" />
        {STEPS.map(({ icon: Icon, title, body }, i) => (
          <li key={title} className="lift relative flex flex-col items-center rounded-3xl bg-white px-5 pb-6 pt-2 text-center">
            <span className="pop-in relative grid size-[72px] place-items-center rounded-full bg-[#fff1e3] ring-8 ring-white" style={{ color: ORANGE }}>
              <Icon className="size-8" />
              <span className="absolute -right-1 -top-1 grid size-7 place-items-center rounded-full text-xs font-bold text-white" style={{ background: GREEN }}>
                {i + 1}
              </span>
            </span>
            <p className="mt-4 font-bold text-[#2b1d14]">{title}</p>
            <p className="mt-1.5 text-sm text-[#6b5a4e]">{body}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}

/* ---------- Banner cấp cứu ---------- */

export function EmergencyBanner() {
  const tel = CLINIC.phone.replace(/\s/g, "");
  return (
    <section className="relative overflow-hidden rounded-[28px] bg-[linear-gradient(100deg,#f2661b_0%,#f57c1f_55%,#ffa24d_100%)] px-6 py-8 text-white sm:px-10">
      <Paw className="float-amb right-[30%] top-4 size-10 rotate-12 text-white/20" />
      <Paw className="float-amb bottom-3 left-[45%] size-7 -rotate-12 text-white/20" />
      <div className="relative flex flex-col items-start gap-6 md:flex-row md:items-center">
        <span className="grid size-16 shrink-0 animate-pulse place-items-center rounded-2xl bg-white/20 motion-reduce:animate-none">
          <Siren className="size-8" />
        </span>
        <div className="flex-1">
          <p className="text-2xl font-extrabold">Bé gặp tình huống khẩn cấp?</p>
          <p className="mt-1 text-sm text-white/90">
            Bác sĩ trực 24/7, kể cả lễ Tết. Gọi trước để đội trực chuẩn bị sẵn phòng cấp cứu.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <a href={`tel:${tel}`} className="inline-flex h-12 items-center gap-2 rounded-full bg-white px-6 text-base font-bold" style={{ color: ORANGE }}>
            <Phone className="size-5" /> {CLINIC.phone}
          </a>
          <Link href="/services/cap-cuu-24-7" className="inline-flex h-12 items-center gap-2 rounded-full border-2 border-white/80 px-6 text-sm font-semibold">
            Tìm hiểu thêm <ArrowRight className="size-4" />
          </Link>
        </div>
      </div>
    </section>
  );
}

/* ---------- Câu hỏi thường gặp ---------- */

const FAQS = [
  { q: "Tôi có cần đặt lịch trước không?", a: "Nên đặt trước để đến nơi là được khám ngay. Trường hợp cấp cứu bạn cứ đưa bé đến, phòng khám luôn có bác sĩ trực." },
  { q: "Tôi có được chọn bác sĩ không?", a: "Hệ thống tự xếp bác sĩ đang rảnh vào khung giờ bạn chọn. Tất cả bác sĩ đều có chứng chỉ hành nghề." },
  { q: "Chi phí khám được báo thế nào?", a: "Giá tham khảo hiển thị ở từng dịch vụ. Trước khi xét nghiệm hay điều trị thêm, bác sĩ sẽ báo giá để bạn đồng ý." },
  { q: "Bé cần tiêm phòng những mũi nào?", a: "Chó thường tiêm dại, care, parvo; mèo tiêm giảm bạch cầu, dại. Bác sĩ sẽ lên lịch phù hợp với tuổi và tình trạng của bé." },
  { q: "Tôi xem lại bệnh án của bé ở đâu?", a: "Đăng nhập, vào Tài khoản → Thú cưng → chọn bé để xem lịch sử khám và đơn thuốc." },
];

export function Faq() {
  const [open, setOpen] = useState(0);
  return (
    <section className="grid gap-10 lg:grid-cols-[0.9fr_1.1fr]">
      <div>
        <Heading eyebrow="Hỏi đáp" title="Câu hỏi" accent="thường gặp" />
        <p className="mt-4 max-w-md text-[15px] text-[#4a3a30]">Chưa thấy câu trả lời bạn cần? Gọi hotline, đội ngũ của chúng tôi luôn sẵn sàng hỗ trợ.</p>
        <div className="relative mt-8 hidden aspect-[4/3] max-w-md overflow-hidden rounded-[28px] lg:block">
          <Image src="/img/cho-meo-ngu.png" alt="Chó và mèo nằm ngủ" fill sizes="30vw" className="object-cover" />
        </div>
      </div>
      <ul className="space-y-3">
        {FAQS.map((f, i) => {
          const on = open === i;
          return (
            <li key={f.q} className={`rounded-2xl border transition-colors ${on ? "border-[#f9c08d] bg-[#fff8f1]" : "border-[#eee5dc] bg-white"}`}>
              <button
                type="button"
                aria-expanded={on}
                onClick={() => setOpen(on ? -1 : i)}
                className="flex w-full items-center gap-3 px-5 py-4 text-left font-semibold text-[#2b1d14]"
              >
                <span className="flex-1">{f.q}</span>
                <ChevronDown className={`size-5 shrink-0 transition-transform ${on ? "rotate-180" : ""}`} style={{ color: ORANGE }} />
              </button>
              <div className={`grid transition-all duration-300 ${on ? "grid-rows-[1fr]" : "grid-rows-[0fr]"}`}>
                <p className="overflow-hidden px-5 text-sm leading-relaxed text-[#4a3a30]">
                  <span className="block pb-4">{f.a}</span>
                </p>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
