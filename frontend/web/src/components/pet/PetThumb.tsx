"use client";

import { PawPrint } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { AuthedImage } from "@/components/AuthedImage";
import type { Pet } from "@/types";

/** Ảnh tròn nhỏ của thú cưng cho các trang nội bộ (bác sĩ, quản trị); chưa có ảnh thì hiện dấu chân. */
export function PetThumb({ pet, className }: { pet: Pick<Pet, "name" | "photoUrl">; className?: string }) {
  return (
    <span
      className={cn("grid shrink-0 place-items-center overflow-hidden rounded-full bg-mist text-bark", className)}
    >
      <AuthedImage
        path={pet.photoUrl}
        alt={`Ảnh của ${pet.name}`}
        className="size-full object-cover"
        fallback={<PawPrint aria-hidden className="size-1/2" />}
      />
    </span>
  );
}
