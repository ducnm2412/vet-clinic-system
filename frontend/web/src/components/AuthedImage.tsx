"use client";

import type { ReactNode } from "react";
import { useAuthedImage } from "@/lib/useAuthedImage";

/**
 * Ảnh cần đăng nhập để xem (thú cưng, khách hàng). Chưa có ảnh, đang tải hoặc tải lỗi thì hiện
 * `fallback` — xem useAuthedImage.
 */
export function AuthedImage({
  path,
  alt,
  className,
  fallback,
}: {
  path: string | null | undefined;
  alt: string;
  className?: string;
  fallback: ReactNode;
}) {
  const src = useAuthedImage(path);
  if (!src) return <>{fallback}</>;
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={alt} className={className} />;
}
