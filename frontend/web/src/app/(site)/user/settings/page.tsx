"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CalendarCheck2, Eye, EyeOff, KeyRound, MapPin, Package, Pencil, Plus, Tag, Trash2 } from "lucide-react";
import { ApiError, customerApi } from "@/lib/api";
import { MOCK_ACCOUNT_ENABLED } from "@/lib/api/mock-account"; // MOCK-TẠM
import { formatAddress } from "@/lib/utils/format";
import type { Address, AddressRequest, CustomerGender } from "@/types";
import { useAuth } from "@/lib/auth/context";
import { useToast } from "@/components/ui";
import { PhotoUploadButton } from "@/components/PhotoUploadButton";
import { SiteButton } from "@/components/site/primitives";
import { SiteDialog } from "@/components/site/SiteDialog";
import { SiteInput, SiteTextarea } from "@/components/site/fields";
import { ComingSoon } from "@/components/user/EmptyState";
import { PageHeader } from "@/components/user/PageHeader";
import { Tabs } from "@/components/user/Tabs";
import { CustomerAvatar, useDisplayName } from "@/components/user/UserSidebar";
import { btnPrimary, card, field, fieldLabel } from "@/components/user/ui";
import { cn } from "@/lib/utils/cn";

const GENDER_LABEL: Record<CustomerGender, string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
  OTHER: "Khác",
};

type Tab = "profile" | "address" | "password" | "notify";
const TABS: Array<{ key: Tab; label: string }> = [
  { key: "profile", label: "Thông tin cá nhân" },
  { key: "address", label: "Sổ địa chỉ" },
  { key: "password", label: "Đổi mật khẩu" },
  { key: "notify", label: "Thông báo" },
];

/**
 * CN-09, CN-10: hồ sơ của chính khách, sổ địa chỉ, đổi mật khẩu, cài đặt thông báo.
 * Mở thẳng một tab bằng ?tab=password (dropdown ở header dùng).
 */
export default function AccountSettingsPage() {
  const [tab, setTab] = useState<Tab>(() => {
    const t = typeof window === "undefined" ? null : new URLSearchParams(window.location.search).get("tab");
    return TABS.find((x) => x.key === t)?.key ?? "profile";
  });

  return (
    <>
      <PageHeader
        title="Cài đặt tài khoản"
        description="Quản lý thông tin cá nhân, địa chỉ giao hàng và bảo mật tài khoản."
        crumbs={[{ label: "Cài đặt tài khoản" }]}
      />
      <div className={cn(card, "p-5 sm:p-7")}>
        <Tabs<Tab> tabs={TABS} value={tab} onChange={setTab} className="mb-6" />
        {tab === "profile" && <ProfileSection />}
        {tab === "address" && <AddressSection />}
        {tab === "password" && <PasswordSection />}
        {tab === "notify" && <NotifySection />}
      </div>
    </>
  );
}

/** Điện thoại, ngày sinh, giới tính — sửa ngay trên form, không mở hộp thoại. */
function ProfileSection() {
  const qc = useQueryClient();
  const toast = useToast();
  const { email } = useAuth();
  const name = useDisplayName();
  const profile = useQuery({ queryKey: ["customer-profile"], queryFn: customerApi.me });
  const addresses = useQuery({ queryKey: ["addresses"], queryFn: customerApi.addresses });
  const defaultAddress = (addresses.data ?? []).find((a) => a.isDefault) ?? addresses.data?.[0];
  const [form, setForm] = useState<{ phone: string; dateOfBirth: string; gender: CustomerGender | "" } | null>(null);

  const data = profile.data;
  const value = form ?? {
    phone: data?.phone ?? "",
    dateOfBirth: data?.dateOfBirth ?? "",
    gender: data?.gender ?? "",
  };

  const save = useMutation({
    mutationFn: () =>
      customerApi.updateMe({
        phone: value.phone || undefined,
        dateOfBirth: value.dateOfBirth || undefined,
        gender: value.gender || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["customer-profile"] });
      toast.success("Đã lưu thay đổi");
      setForm(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được hồ sơ."),
  });

  if (profile.isLoading) return <div className="h-72 animate-pulse rounded-[20px] bg-[#fff5ec]" aria-hidden />;
  if (profile.isError) return <p className="text-[#777777]">Không tải được hồ sơ. Thử lại sau.</p>;

  return (
    <div className="grid gap-6 md:grid-cols-[250px_minmax(0,1fr)]">
      <div className="flex h-fit flex-col items-center rounded-[20px] border border-[#eeeeee] bg-[#fffcf8] px-5 py-8 text-center">
        <CustomerAvatar className="size-28 ring-4 ring-white" />
        <p className="mt-4 text-lg font-bold text-[#1f4a3c]">{name}</p>
        <p className="mt-0.5 max-w-full truncate text-sm text-[#777777]">{email}</p>
        <div className="mt-5">
          <PhotoUploadButton
            variant="site"
            label={data?.photoUrl ? "Thay đổi ảnh" : "Thêm ảnh"}
            upload={customerApi.uploadMyPhoto}
            onUploaded={() => qc.invalidateQueries({ queryKey: ["customer-profile"] })}
          />
        </div>
      </div>

      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className={fieldLabel}>Họ và tên</span>
            <input className={field} value={name} disabled />
          </label>
          <label className="block">
            <span className={fieldLabel}>Email</span>
            <input className={field} value={email ?? ""} disabled />
          </label>
        </div>
        <label className="block">
          <span className={fieldLabel}>Số điện thoại</span>
          <input
            className={field}
            inputMode="numeric"
            placeholder="0912345678"
            value={value.phone}
            onChange={(e) => setForm({ ...value, phone: e.target.value })}
          />
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className={fieldLabel}>Ngày sinh</span>
            <input
              type="date"
              className={field}
              value={value.dateOfBirth}
              onChange={(e) => setForm({ ...value, dateOfBirth: e.target.value })}
            />
          </label>
          <label className="block">
            <span className={fieldLabel}>Giới tính</span>
            <select
              className={field}
              value={value.gender}
              onChange={(e) => setForm({ ...value, gender: e.target.value as CustomerGender | "" })}
            >
              <option value="">Chưa chọn</option>
              <option value="MALE">{GENDER_LABEL.MALE}</option>
              <option value="FEMALE">{GENDER_LABEL.FEMALE}</option>
              <option value="OTHER">{GENDER_LABEL.OTHER}</option>
            </select>
          </label>
        </div>
        <div>
          <span className={fieldLabel}>Địa chỉ mặc định</span>
          <p className={cn(field, "flex h-auto min-h-12 items-center py-3", !defaultAddress && "text-[#999999]")}>
            {defaultAddress ? formatAddress(defaultAddress) : "Chưa có địa chỉ — thêm ở tab Sổ địa chỉ"}
          </p>
        </div>
        <button type="submit" disabled={save.isPending || form === null} className={cn(btnPrimary, "mt-2 h-12 w-full")}>
          {save.isPending ? "Đang lưu…" : "Lưu thay đổi"}
        </button>
      </form>
    </div>
  );
}

/* MOCK-TẠM: backend chưa có API đổi mật khẩu khi đang đăng nhập — form chỉ chạy thử ở dev. */
function PasswordSection() {
  const toast = useToast();
  const [show, setShow] = useState(false);
  const [f, setF] = useState({ current: "", next: "", confirm: "" });
  if (!MOCK_ACCOUNT_ENABLED) return <ComingSoon icon={KeyRound} title="Đổi mật khẩu sắp ra mắt" />;

  const tooShort = f.next.length > 0 && f.next.length < 8;
  const mismatch = f.confirm.length > 0 && f.confirm !== f.next;
  const ok = !!f.current && f.next.length >= 8 && f.next === f.confirm;
  const input = (key: keyof typeof f, label: string, hint?: string | false) => (
    <label className="block">
      <span className={fieldLabel}>{label}</span>
      <span className="relative block">
        <input
          type={show ? "text" : "password"}
          autoComplete={key === "current" ? "current-password" : "new-password"}
          className={cn(field, "pr-12", hint && "border-[#e5484d]")}
          value={f[key]}
          onChange={(e) => setF({ ...f, [key]: e.target.value })}
        />
        <button
          type="button"
          onClick={() => setShow(!show)}
          aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          className="absolute right-3 top-1/2 grid size-8 -translate-y-1/2 place-items-center rounded-full text-[#777777] hover:bg-[#fff1e3]"
        >
          {show ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
        </button>
      </span>
      {hint && <span className="mt-1 block text-xs text-[#e5484d]">{hint}</span>}
    </label>
  );

  return (
    <form
      className="max-w-lg space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        toast.success("Demo: đã đổi mật khẩu (chưa kết nối backend).");
        setF({ current: "", next: "", confirm: "" });
      }}
    >
      {input("current", "Mật khẩu hiện tại")}
      {input("next", "Mật khẩu mới", tooShort && "Mật khẩu cần ít nhất 8 ký tự.")}
      {input("confirm", "Xác nhận mật khẩu mới", mismatch && "Mật khẩu xác nhận chưa khớp.")}
      <button type="submit" disabled={!ok} className={cn(btnPrimary, "mt-2 h-12 w-full")}>
        Cập nhật mật khẩu
      </button>
    </form>
  );
}

/* MOCK-TẠM: backend chưa lưu cài đặt thông báo — ở dev chỉ giữ trong bộ nhớ trang. */
function NotifySection() {
  const toast = useToast();
  const [prefs, setPrefs] = useState({ appointment: true, order: true, promo: false });
  if (!MOCK_ACCOUNT_ENABLED) return <ComingSoon icon={Bell} title="Cài đặt thông báo sắp ra mắt" />;

  const rows = [
    { key: "appointment" as const, icon: CalendarCheck2, title: "Thông báo lịch khám", desc: "Nhắc trước buổi khám, khi lịch được xác nhận hoặc thay đổi." },
    { key: "order" as const, icon: Package, title: "Thông báo đơn hàng", desc: "Cập nhật khi đơn được xác nhận, đang giao và đã giao." },
    { key: "promo" as const, icon: Tag, title: "Thông báo khuyến mãi", desc: "Ưu đãi sản phẩm, gói tiêm phòng và dịch vụ spa." },
  ];
  return (
    <div className="max-w-2xl">
      <ul className="divide-y divide-[#eeeeee] rounded-2xl border border-[#eeeeee]">
        {rows.map((r) => (
          <li key={r.key} className="flex items-center gap-4 p-4">
            <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-[#fff1e3] text-[#f57c1f]">
              <r.icon aria-hidden className="size-5" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-[#242424]">{r.title}</p>
              <p className="text-[13px] text-[#777777]">{r.desc}</p>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={prefs[r.key]}
              aria-label={r.title}
              onClick={() => setPrefs({ ...prefs, [r.key]: !prefs[r.key] })}
              className={cn(
                "relative h-7 w-12 shrink-0 rounded-full transition-colors duration-200",
                prefs[r.key] ? "bg-[#f57c1f]" : "bg-[#dddddd]",
              )}
            >
              <span
                className={cn(
                  "absolute left-0 top-1 size-5 rounded-full bg-white shadow transition-transform duration-200",
                  prefs[r.key] ? "translate-x-6" : "translate-x-1",
                )}
              />
            </button>
          </li>
        ))}
      </ul>
      <button type="button" onClick={() => toast.success("Demo: đã lưu cài đặt thông báo.")} className={cn(btnPrimary, "mt-5")}>
        Lưu cài đặt
      </button>
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
        <h2 className="text-lg font-bold text-[#1f4a3c]">Sổ địa chỉ</h2>
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
