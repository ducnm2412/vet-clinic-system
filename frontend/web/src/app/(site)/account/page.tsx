"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MapPin, Pencil, Plus, Trash2 } from "lucide-react";
import { ApiError, customerApi } from "@/lib/api";
import { formatAddress, formatDate } from "@/lib/utils/format";
import type { Address, AddressRequest } from "@/types";
import { useAuth } from "@/lib/auth/context";
import { useToast } from "@/components/ui";
import { Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { SiteDialog } from "@/components/site/SiteDialog";
import { SiteInput, SiteTextarea } from "@/components/site/fields";

/**
 * CN-09, CN-10: hồ sơ của chính khách và sổ địa chỉ.
 *
 * Backend có từ đầu nhưng chưa có màn hình nào gọi tới, nên lúc thanh toán khách phải gõ lại
 * địa chỉ mỗi lần. Trang này là chỗ khai một lần rồi dùng lại.
 */
export default function AccountPage() {
  return (
    <CustomerOnly>
      <AccountBody />
    </CustomerOnly>
  );
}

function AccountBody() {
  const { email } = useAuth();

  return (
    <>
      <div className="bg-mint">
        <Container className="py-12 md:py-16">
          <h1 className="t-h1">Tài khoản của bạn</h1>
          <p className="t-lede mt-3 text-stone">{email}</p>
        </Container>
      </div>

      <Container className="grid gap-14 py-14 md:py-20 lg:grid-cols-[1fr_1.2fr] lg:gap-20">
        <ProfileSection />
        <AddressSection />
      </Container>
    </>
  );
}

/** Điện thoại và ngày sinh. Hồ sơ được tạo sẵn ở lần đọc đầu tiên nên không có trạng thái "chưa có". */
function ProfileSection() {
  const qc = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ phone: "", dateOfBirth: "" });

  const profile = useQuery({ queryKey: ["customer-profile"], queryFn: customerApi.me });

  const save = useMutation({
    mutationFn: () =>
      customerApi.updateMe({
        phone: form.phone || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["customer-profile"] });
      toast.success("Đã lưu hồ sơ");
      setEditing(false);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được hồ sơ."),
  });

  function openEdit() {
    setForm({
      phone: profile.data?.phone ?? "",
      dateOfBirth: profile.data?.dateOfBirth ?? "",
    });
    setEditing(true);
  }

  return (
    <section>
      <h2 className="t-h2">Thông tin liên lạc</h2>
      <p className="measure mt-3 text-stone">
        Phòng khám gọi số này khi cần báo bạn về lịch khám hoặc đơn hàng.
      </p>

      {profile.isLoading ? (
        <div className="mt-8 h-24 animate-pulse rounded-[var(--radius-card)] bg-mint" aria-hidden />
      ) : profile.isError ? (
        <p className="mt-8 text-stone">Không tải được hồ sơ. Thử lại sau.</p>
      ) : (
        <>
          <dl className="mt-8 max-w-md divide-y divide-mist border-y border-mist">
            <Row label="Số điện thoại" value={profile.data?.phone || "Chưa khai"} />
            <Row
              label="Ngày sinh"
              value={profile.data?.dateOfBirth ? formatDate(profile.data.dateOfBirth) : "Chưa khai"}
            />
          </dl>

          <div className="mt-6">
            <SiteButton variant="outline" onClick={openEdit}>
              <Pencil aria-hidden className="size-4" />
              Sửa thông tin
            </SiteButton>
          </div>
        </>
      )}

      <SiteDialog
        open={editing}
        onClose={() => setEditing(false)}
        title="Sửa thông tin liên lạc"
        description="Chỉ bạn và phòng khám thấy những thông tin này."
        footer={
          <>
            <SiteButton variant="outline" onClick={() => setEditing(false)}>
              Huỷ
            </SiteButton>
            <SiteButton loading={save.isPending} onClick={() => save.mutate()}>
              Lưu
            </SiteButton>
          </>
        }
      >
        <div className="space-y-4">
          <SiteInput
            label="Số điện thoại"
            inputMode="numeric"
            placeholder="0912345678"
            value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
          />
          <SiteInput
            label="Ngày sinh"
            type="date"
            value={form.dateOfBirth}
            onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
          />
        </div>
      </SiteDialog>
    </section>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-wrap justify-between gap-4 py-4">
      <dt className="text-stone">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

/** CN-10: sổ địa chỉ. Một địa chỉ mặc định, dùng sẵn khi thanh toán. */
function AddressSection() {
  const qc = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState<Address | null>(null);
  const [adding, setAdding] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<Address | null>(null);

  const addresses = useQuery({ queryKey: ["addresses"], queryFn: customerApi.addresses });

  const remove = useMutation({
    mutationFn: (id: string) => customerApi.deleteAddress(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["addresses"] });
      toast.success("Đã xoá địa chỉ");
      setConfirmDelete(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không xoá được địa chỉ."),
  });

  const list = addresses.data ?? [];

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="t-h2">Sổ địa chỉ</h2>
        <SiteButton variant="outline" onClick={() => setAdding(true)}>
          <Plus aria-hidden className="size-4" />
          Thêm địa chỉ
        </SiteButton>
      </div>
      <p className="measure mt-3 text-stone">
        Khai sẵn ở đây thì lúc đặt hàng chỉ cần chọn, không phải gõ lại.
      </p>

      {addresses.isLoading ? (
        <div className="mt-8 h-32 animate-pulse rounded-[var(--radius-card)] bg-mint" aria-hidden />
      ) : list.length === 0 ? (
        <div className="mt-8 rounded-[var(--radius-card)] bg-mint px-6 py-12 text-center">
          <MapPin aria-hidden className="mx-auto size-7 text-teal" />
          <p className="t-h3 mt-4">Chưa có địa chỉ nào</p>
          <p className="measure mx-auto mt-2 text-stone">
            Thêm địa chỉ giao hàng để lần sau đặt đơn nhanh hơn.
          </p>
        </div>
      ) : (
        <ul className="mt-8 divide-y divide-mist border-y border-mist">
          {list.map((a) => (
            <li key={a.id} className="flex flex-wrap items-start gap-4 py-5">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{formatAddress(a)}</p>
                {a.isDefault && <p className="mt-1 text-[15px] text-teal">Địa chỉ mặc định</p>}
              </div>
              <div className="flex gap-2">
                <SiteButton variant="outline" onClick={() => setEditing(a)}>
                  <Pencil aria-hidden className="size-4" />
                  Sửa
                </SiteButton>
                <SiteButton variant="outline" onClick={() => setConfirmDelete(a)}>
                  <Trash2 aria-hidden className="size-4" />
                  Xoá
                </SiteButton>
              </div>
            </li>
          ))}
        </ul>
      )}

      {(adding || editing) && (
        <AddressDialog
          address={editing}
          hasAny={list.length > 0}
          onClose={() => {
            setAdding(false);
            setEditing(null);
          }}
        />
      )}

      <SiteDialog
        open={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        title="Xoá địa chỉ này?"
        description="Đơn hàng cũ không bị ảnh hưởng — mỗi đơn đã chụp lại địa chỉ tại thời điểm đặt."
        footer={
          <>
            <SiteButton variant="outline" onClick={() => setConfirmDelete(null)}>
              Giữ lại
            </SiteButton>
            <SiteButton
              loading={remove.isPending}
              onClick={() => confirmDelete && remove.mutate(confirmDelete.id)}
            >
              Xoá
            </SiteButton>
          </>
        }
      >
        <p className="text-stone">{confirmDelete && formatAddress(confirmDelete)}</p>
      </SiteDialog>
    </section>
  );
}

function AddressDialog({
  address,
  hasAny,
  onClose,
}: {
  address: Address | null;
  hasAny: boolean;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState<AddressRequest>({
    line1: address?.line1 ?? "",
    line2: address?.line2 ?? "",
    ward: address?.ward ?? "",
    city: address?.city ?? "",
    // Địa chỉ đầu tiên mặc định luôn: không có lý do bắt người ta tự đánh dấu.
    isDefault: address?.isDefault ?? !hasAny,
  });

  const save = useMutation({
    mutationFn: () => {
      const body: AddressRequest = {
        line1: form.line1,
        line2: form.line2 || undefined,
        ward: form.ward || undefined,
        city: form.city || undefined,
        isDefault: form.isDefault,
      };
      return address ? customerApi.updateAddress(address.id, body) : customerApi.addAddress(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["addresses"] });
      toast.success(address ? "Đã lưu địa chỉ" : "Đã thêm địa chỉ");
      onClose();
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được địa chỉ."),
  });

  return (
    <SiteDialog
      open
      onClose={onClose}
      title={address ? "Sửa địa chỉ" : "Thêm địa chỉ"}
      description="Ghi đủ số nhà và tên đường để người giao tìm được."
      footer={
        <>
          <SiteButton variant="outline" onClick={onClose}>
            Huỷ
          </SiteButton>
          <SiteButton
            loading={save.isPending}
            disabled={!form.line1.trim()}
            onClick={() => save.mutate()}
          >
            Lưu địa chỉ
          </SiteButton>
        </>
      }
    >
      <div className="space-y-4">
        <SiteTextarea
          label="Số nhà, tên đường"
          required
          rows={2}
          value={form.line1}
          onChange={(e) => setForm({ ...form, line1: e.target.value })}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SiteInput
            label="Phường, xã"
            value={form.ward ?? ""}
            onChange={(e) => setForm({ ...form, ward: e.target.value })}
          />
          <SiteInput
            label="Tỉnh, thành phố"
            value={form.city ?? ""}
            onChange={(e) => setForm({ ...form, city: e.target.value })}
          />
        </div>
        <SiteInput
          label="Toà nhà, ghi chú thêm"
          value={form.line2 ?? ""}
          onChange={(e) => setForm({ ...form, line2: e.target.value })}
        />
        <label className="flex items-center gap-2.5 text-[15px]">
          <input
            type="checkbox"
            className="size-4 accent-[var(--color-teal)]"
            checked={form.isDefault ?? false}
            onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
          />
          Dùng làm địa chỉ mặc định
        </label>
      </div>
    </SiteDialog>
  );
}
