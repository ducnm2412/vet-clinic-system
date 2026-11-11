"use client";

import { Cat, Dog, PawPrint } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { AuthedImage } from "@/components/AuthedImage";

/** Ảnh thú cưng: ảnh tĩnh (mẫu) dùng img thường; ảnh khách tải lên đi qua AuthedImage; không có thì icon theo loài. */
export function PetPhoto({
  photoUrl,
  species,
  name,
  className,
}: {
  photoUrl: string | null | undefined;
  species: string | null | undefined;
  name?: string;
  className?: string;
}) {
  const s = species?.trim().toLowerCase();
  const Icon = s === "chó" ? Dog : s === "mèo" ? Cat : PawPrint;
  const fallback = (
    <span className="grid size-full place-items-center bg-[#fff1e3] text-[#f57c1f]">
      <Icon aria-hidden className="size-1/3 min-h-6 min-w-6" strokeWidth={1.6} />
    </span>
  );
  const isStatic = !!photoUrl && (photoUrl.startsWith("/img/") || photoUrl.startsWith("/mock-images/"));
  return (
    <span className={cn("block overflow-hidden bg-[#fff1e3]", className)}>
      {isStatic ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={photoUrl!} alt={name ?? ""} className="size-full object-cover" />
      ) : (
        <AuthedImage path={photoUrl} alt={name ?? ""} className="size-full object-cover" fallback={fallback} />
      )}
    </span>
  );
}
