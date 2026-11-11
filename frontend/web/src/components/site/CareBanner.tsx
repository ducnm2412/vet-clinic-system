import Image from "next/image";
import Link from "next/link";
import { ArrowRight, Heart } from "lucide-react";
import { Leaf, Paw } from "./Decor";

const ORANGE = "#f57c1f";
const FADE = "linear-gradient(to right, transparent, #000 6%, #000 94%, transparent)";

/**
 * Banner cam sát footer (mép sóng footer đè lên phần đệm dưới). Nền, dấu chân, lá cây vẽ bằng
 * CSS; ảnh hiển thị tối đa đúng cỡ gốc để không vỡ nét.
 */
export function CareBanner({
  title,
  subtitle,
  cta,
  image,
  extra,
}: {
  title: React.ReactNode;
  subtitle: string;
  cta: { href: string; label: string };
  /** displayWidth: cỡ hiển thị tối đa; mặc định = width gốc. Ảnh nhỏ có thể cho to hơn chút. */
  image: { src: string; alt: string; width: number; height: number; displayWidth?: number };
  extra?: React.ReactNode;
}) {
  return (
    <section className="relative overflow-hidden bg-[linear-gradient(100deg,#fffaf4_0%,#fff0e2_45%,#ffe2c6_100%)] pb-12 pt-6 md:pb-16 md:pt-10">
      <div aria-hidden className="absolute -bottom-16 -left-16 size-56 rounded-full bg-[radial-gradient(circle,#ffc894,#ffb36b)] opacity-70" />
      <div aria-hidden className="absolute -right-24 -top-24 size-72 rounded-full bg-[radial-gradient(circle,#ffd7ad,transparent_70%)]" />
      <div
        aria-hidden
        className="absolute inset-0 opacity-60"
        style={{
          backgroundImage: "radial-gradient(#f7b37a 1.2px, transparent 1.4px)",
          backgroundSize: "22px 22px",
          maskImage: "linear-gradient(100deg, transparent 0%, #000 45%, #000 100%)",
          WebkitMaskImage: "linear-gradient(100deg, transparent 0%, #000 45%, #000 100%)",
        }}
      />
      <Paw className="left-[4%] top-8 size-8 -rotate-12 text-[#f9a55c]" />
      <Paw className="right-[3%] bottom-16 hidden size-10 -rotate-12 text-white/90 md:block" />
      <Paw className="right-[45%] top-[30%] hidden size-6 rotate-[25deg] text-[#f9a55c]/80 lg:block" />
      <Paw className="left-[40%] top-6 hidden size-9 rotate-12 text-[#f9a55c] md:block" />
      <Paw className="left-[36%] bottom-20 hidden size-5 rotate-45 text-[#fbb983] md:block" />
      <Heart aria-hidden className="absolute left-[40%] top-[58%] hidden size-12 md:block" strokeWidth={1.75} style={{ color: ORANGE }} />

      <div className="relative mx-auto flex max-w-[1440px] flex-col items-center gap-6 px-4 sm:px-6 md:flex-row md:justify-between">
        <div className="relative z-10 py-10 md:pl-20">
          <h2 className="text-[30px] font-extrabold leading-tight text-[#2b1d14] sm:text-[36px]">{title}</h2>
          <p className="mt-3 text-sm text-[#3b2a20]">{subtitle}</p>
          {extra && <div>{extra}</div>}
          <Link
            href={cta.href}
            className="mt-6 inline-flex h-10 items-center gap-2 rounded-full px-5 text-sm font-semibold text-white shadow-[0_6px_14px_-6px_rgba(245,124,31,0.8)]"
            style={{ background: ORANGE }}
          >
            {cta.label} <ArrowRight className="size-4" />
          </Link>
        </div>

        <div className="relative w-full shrink-0 md:mr-16" style={{ maxWidth: image.displayWidth ?? image.width }}>
          {/* Mảng cam hữu cơ + vòng nét đứt phía sau ảnh */}
          <div aria-hidden className="absolute -inset-x-10 -inset-y-8 rounded-[58%_42%_55%_45%/48%_60%_40%_52%] bg-[linear-gradient(135deg,#ffcf9f,#f7a65a)] opacity-80" />
          <div aria-hidden className="absolute -right-14 -top-12 size-32 rounded-full border-2 border-dashed border-[#f57c1f]/50" />
          <div aria-hidden className="absolute -bottom-8 -left-12 size-20 rounded-full bg-white/70" />
          <Leaf className="-right-6 -top-6 h-24 w-10 rotate-[35deg] bg-[linear-gradient(135deg,#7cc36b,#3f8f3a)]" />
          <Leaf className="-right-10 top-12 h-16 w-7 rotate-[70deg] bg-[linear-gradient(135deg,#f6a35a,#e2702a)]" />
          <Leaf className="-left-5 bottom-2 h-14 w-6 -rotate-[40deg] bg-[linear-gradient(135deg,#8fd07c,#4b9a45)]" />
          <Image
            src={image.src}
            alt={image.alt}
            width={image.width}
            height={image.height}
            unoptimized
            className="relative h-auto w-full rounded-[28px] shadow-[0_18px_40px_-18px_rgba(160,80,20,0.55)]"
            style={{ maskImage: FADE, WebkitMaskImage: FADE }}
          />
        </div>
      </div>
    </section>
  );
}
