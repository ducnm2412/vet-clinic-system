import { Bird, Cat, Dog, Fish, PawPrint, Rabbit, Rat, Turtle, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils/cn";

/**
 * Ảnh đại diện thú cưng.
 *
 * TODO(backend, VD-22): `PetResponse` không có ảnh, cũng không có dị ứng hay ghi chú.
 * Nên mỗi bé nhận một mái vòm màu theo loài kèm hình con vật tương ứng — đủ để phân biệt
 * các bé với nhau trong danh sách mà không phải bịa ra ảnh không tồn tại.
 * Cần bổ sung: photoUrl, allergies, notes trong PetRequest/PetResponse.
 */
const BY_SPECIES: Record<string, { icon: LucideIcon; skin: string }> = {
  chó: { icon: Dog, skin: "bg-[#0e9b8e]/12 text-[#0a7b70]" },
  mèo: { icon: Cat, skin: "bg-[#ff6b5e]/14 text-[#ce3f26]" },
  chim: { icon: Bird, skin: "bg-[#f2a93b]/16 text-[#9a6512]" },
  thỏ: { icon: Rabbit, skin: "bg-[#9b7b8a]/16 text-[#71566a]" },
  hamster: { icon: Rat, skin: "bg-[#a8813f]/16 text-[#7a5c26]" },
  "bò sát": { icon: Turtle, skin: "bg-[#5d7a5a]/16 text-[#41603f]" },
  cá: { icon: Fish, skin: "bg-[#3a6b8a]/14 text-[#2b5570]" },
};

function look(species: string | null | undefined) {
  const key = species?.trim().toLowerCase() ?? "";
  return BY_SPECIES[key] ?? { icon: PawPrint, skin: "bg-mist text-pine/55" };
}

export function PetAvatar({
  species,
  className,
  small,
}: {
  species: string | null | undefined;
  className?: string;
  small?: boolean;
}) {
  const { icon: Icon, skin } = look(species);
  return (
    <span
      aria-hidden
      className={cn(
        small ? "arch-sm" : "arch",
        "grid shrink-0 place-items-center",
        skin,
        className,
      )}
    >
      <Icon className={small ? "size-7" : "size-12"} strokeWidth={1.5} />
    </span>
  );
}
