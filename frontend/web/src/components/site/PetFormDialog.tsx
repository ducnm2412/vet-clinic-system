"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ApiError, customerApi } from "@/lib/api";
import { useToast } from "@/components/ui";
import type { Pet } from "@/types";
import { SiteButton } from "./primitives";
import { SiteDialog } from "./SiteDialog";
import { SiteInput, SiteSelect } from "./fields";

// Loài phổ biến ở phòng khám thú y Việt Nam; vẫn cho gõ tự do vì danh sách không thể đủ.
const SPECIES = ["Chó", "Mèo", "Chim", "Thỏ", "Hamster", "Bò sát", "Cá"];

const schema = z.object({
  name: z.string().min(1, "Nhập tên thú cưng").max(100),
  species: z.string().min(1, "Chọn hoặc nhập loài").max(50),
  breed: z.string().max(100).optional(),
  gender: z.enum(["MALE", "FEMALE", "UNKNOWN"]),
  dateOfBirth: z.string().optional(),
  weightKg: z.coerce.number().positive("Cân nặng phải lớn hơn 0").optional(),
});

// Zod 4 đổi kiểu đầu vào và đầu ra của `coerce`, nên phải khai cả hai cho React Hook Form.
type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

export function PetFormDialog({
  open,
  pet,
  onClose,
}: {
  open: boolean;
  /** Có thì là sửa, không có thì là thêm mới. */
  pet: Pet | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: pet?.name ?? "",
      species: pet?.species ?? "",
      breed: pet?.breed ?? "",
      gender: pet?.gender ?? "UNKNOWN",
      dateOfBirth: pet?.dateOfBirth ?? "",
      weightKg: pet?.weightKg ?? undefined,
    },
  });

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body = {
        name: v.name,
        species: v.species,
        breed: v.breed || undefined,
        gender: v.gender,
        dateOfBirth: v.dateOfBirth || undefined,
        weightKg: v.weightKg,
      };
      return pet ? customerApi.updatePet(pet.id, body) : customerApi.addPet(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pets"] });
      toast.success(pet ? "Đã lưu hồ sơ" : "Đã thêm thú cưng");
      onClose();
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field in schema.shape) setError(field as keyof FormInput, { message });
        }
      } else {
        toast.error(err instanceof ApiError ? err.message : "Không lưu được hồ sơ.");
      }
    },
  });

  return (
    <SiteDialog
      open={open}
      onClose={onClose}
      title={pet ? `Sửa hồ sơ ${pet.name}` : "Thêm thú cưng"}
      description="Bác sĩ đọc hồ sơ này trước mỗi lần khám, nên điền được bao nhiêu tốt bấy nhiêu."
      footer={
        <>
          <SiteButton variant="outline" onClick={onClose}>
            Huỷ
          </SiteButton>
          <SiteButton
            form="pet-form"
            type="submit"
            loading={save.isPending}
          >
            {pet ? "Lưu thay đổi" : "Thêm thú cưng"}
          </SiteButton>
        </>
      }
    >
      <form id="pet-form" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="space-y-4">
        <SiteInput label="Tên gọi ở nhà" required error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <SiteInput
            label="Loài"
            required
            list="species-options"
            placeholder="Chó, Mèo…"
            error={errors.species?.message}
            {...register("species")}
          />
          <datalist id="species-options">
            {SPECIES.map((s) => (
              <option key={s} value={s} />
            ))}
          </datalist>

          <SiteInput
            label="Giống"
            placeholder="Poodle, Anh lông ngắn…"
            error={errors.breed?.message}
            {...register("breed")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <SiteSelect label="Giới tính" error={errors.gender?.message} {...register("gender")}>
            <option value="UNKNOWN">Chưa rõ</option>
            <option value="MALE">Đực</option>
            <option value="FEMALE">Cái</option>
          </SiteSelect>

          <SiteInput
            label="Ngày sinh"
            type="date"
            error={errors.dateOfBirth?.message}
            {...register("dateOfBirth")}
          />

          <SiteInput
            label="Cân nặng (kg)"
            type="number"
            step="0.1"
            min="0"
            inputMode="decimal"
            error={errors.weightKg?.message}
            {...register("weightKg")}
          />
        </div>
      </form>
    </SiteDialog>
  );
}
