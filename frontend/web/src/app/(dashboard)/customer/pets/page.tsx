"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { ApiError, customerApi } from "@/lib/api";
import { speciesStripe } from "@/lib/utils/status";
import { formatAge, formatDate } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import type { Pet } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  IconButton,
  SelectField,
  Skeleton,
  TextField,
  useToast,
} from "@/components/ui";

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

type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

export default function CustomerPetsPage() {
  const [editing, setEditing] = useState<Pet | null>(null);
  const [open, setOpen] = useState(false);

  const qc = useQueryClient();
  const toast = useToast();

  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });

  const remove = useMutation({
    mutationFn: (id: string) => customerApi.deletePet(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pets"] });
      toast.success("Đã xoá thú cưng");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không xoá được."),
  });

  return (
    <>
      <PageHeader
        title="Thú cưng"
        description="Hồ sơ này dùng khi đặt lịch khám và khi bác sĩ lập bệnh án."
        actions={
          <Button
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            <Plus aria-hidden className="size-4" />
            Thêm thú cưng
          </Button>
        }
      />

      {pets.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
      ) : pets.isError ? (
        <ErrorState message="Không tải được danh sách thú cưng." onRetry={() => pets.refetch()} />
      ) : (pets.data ?? []).length === 0 ? (
        <div className="rounded-[var(--radius-control)] border border-line bg-surface">
          <EmptyState
            title="Chưa có thú cưng nào"
            description="Thêm thú cưng để đặt lịch khám cho bé."
            action={
              <Button
                size="sm"
                onClick={() => {
                  setEditing(null);
                  setOpen(true);
                }}
              >
                Thêm thú cưng
              </Button>
            }
          />
        </div>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(pets.data ?? []).map((pet) => (
            <li
              key={pet.id}
              className="flex overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface"
            >
              {/* Dải màu định danh loài — mượn từ tab bìa hồ sơ bệnh án giấy. */}
              <span aria-hidden className={cn("w-1.5 shrink-0", speciesStripe(pet.species))} />
              <div className="min-w-0 flex-1 p-4">
                <div className="flex items-start gap-2">
                  <div className="min-w-0 flex-1">
                    <h2 className="truncate font-medium text-ink">{pet.name}</h2>
                    <p className="mt-0.5 text-sm text-bark">
                      {pet.species}
                      {pet.breed ? ` ${pet.breed}` : ""}
                    </p>
                  </div>
                  <IconButton
                    label={`Sửa ${pet.name}`}
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setEditing(pet);
                      setOpen(true);
                    }}
                  >
                    <Pencil aria-hidden className="size-4" />
                  </IconButton>
                  <IconButton
                    label={`Xoá ${pet.name}`}
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      if (confirm(`Xoá hồ sơ của ${pet.name}?`)) remove.mutate(pet.id);
                    }}
                  >
                    <Trash2 aria-hidden className="size-4" />
                  </IconButton>
                </div>

                <dl className="mt-3 space-y-1 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-bark">Tuổi</dt>
                    <dd>{formatAge(pet.dateOfBirth)}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Giới tính</dt>
                    <dd>
                      {pet.gender === "MALE" ? "Đực" : pet.gender === "FEMALE" ? "Cái" : "Chưa rõ"}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Cân nặng</dt>
                    <dd className="tnum">{pet.weightKg ? `${pet.weightKg} kg` : "—"}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Ngày sinh</dt>
                    <dd>{formatDate(pet.dateOfBirth)}</dd>
                  </div>
                </dl>
              </div>
            </li>
          ))}
        </ul>
      )}

      <PetFormDialog open={open} onClose={() => setOpen(false)} editing={editing} />
    </>
  );
}

function PetFormDialog({
  open,
  onClose,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  editing: Pet | null;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { gender: "UNKNOWN", species: SPECIES[0] },
  });

  useEffect(() => {
    if (!open) return;
    reset(
      editing
        ? {
            name: editing.name,
            species: editing.species,
            breed: editing.breed ?? "",
            gender: editing.gender,
            dateOfBirth: editing.dateOfBirth ?? "",
            weightKg: editing.weightKg ?? undefined,
          }
        : { gender: "UNKNOWN", species: SPECIES[0], name: "", breed: "" },
    );
  }, [open, editing, reset]);

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const body = {
        ...values,
        breed: values.breed || undefined,
        dateOfBirth: values.dateOfBirth || undefined,
      };
      return editing ? customerApi.updatePet(editing.id, body) : customerApi.addPet(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pets"] });
      toast.success(editing ? "Đã lưu hồ sơ" : "Đã thêm thú cưng");
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
    <Dialog
      open={open}
      onClose={onClose}
      title={editing ? `Sửa hồ sơ ${editing.name}` : "Thêm thú cưng"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="pet-form" type="submit" loading={save.isPending}>
            {editing ? "Lưu thay đổi" : "Thêm thú cưng"}
          </Button>
        </>
      }
    >
      <form
        id="pet-form"
        onSubmit={handleSubmit((v) => save.mutate(v))}
        className="space-y-4"
        noValidate
      >
        <TextField label="Tên" required error={errors.name?.message} {...register("name")} />

        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="species" className="mb-1.5 block text-sm font-medium text-ink">
              Loài <span className="text-danger">*</span>
            </label>
            <input
              id="species"
              list="species-list"
              required
              className="h-9 w-full rounded-[var(--radius-control)] border border-line-strong bg-surface px-3 text-sm outline-none focus:border-moss"
              {...register("species")}
            />
            <datalist id="species-list">
              {SPECIES.map((s) => (
                <option key={s} value={s} />
              ))}
            </datalist>
            {errors.species && (
              <p className="mt-1 text-xs text-danger">{errors.species.message}</p>
            )}
          </div>
          <TextField label="Giống" placeholder="Poodle, Anh lông ngắn…" error={errors.breed?.message} {...register("breed")} />
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <SelectField label="Giới tính" {...register("gender")}>
            <option value="UNKNOWN">Chưa rõ</option>
            <option value="MALE">Đực</option>
            <option value="FEMALE">Cái</option>
          </SelectField>
          <TextField label="Ngày sinh" type="date" error={errors.dateOfBirth?.message} {...register("dateOfBirth")} />
          <TextField
            label="Cân nặng (kg)"
            type="number"
            step="0.1"
            min={0}
            error={errors.weightKg?.message}
            {...register("weightKg")}
          />
        </div>
      </form>
    </Dialog>
  );
}
