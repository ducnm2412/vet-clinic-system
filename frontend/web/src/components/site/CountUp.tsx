"use client";

import { useEffect, useRef, useState } from "react";

/** Đếm số từ 0 lên giá trị đích khi lọt vào màn hình (1.2s, ease-out). Giữ nguyên tiền tố/hậu tố: "15.000+", "24/7". */
export function CountUp({ value, duration = 1200 }: { value: string; duration?: number }) {
  const m = value.match(/^([^\d]*)([\d.]+)(.*)$/);
  const target = m ? Number(m[2].replace(/\./g, "")) : 0;
  const ref = useRef<HTMLSpanElement>(null);
  const [n, setN] = useState<number | null>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || !m || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let raf = 0;
    const io = new IntersectionObserver(([e]) => {
      if (!e.isIntersecting) return;
      io.disconnect();
      const t0 = performance.now();
      const tick = (t: number) => {
        const p = Math.min(1, (t - t0) / duration);
        setN(Math.round(target * (1 - Math.pow(1 - p, 3))));
        if (p < 1) raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
    });
    io.observe(el);
    return () => {
      io.disconnect();
      cancelAnimationFrame(raf);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, duration]);
  if (!m || value.includes("/")) return <span ref={ref}>{value}</span>;
  return (
    <span ref={ref} className="tabular-nums">
      {m[1]}
      {(n ?? target).toLocaleString("vi-VN")}
      {m[3]}
    </span>
  );
}
