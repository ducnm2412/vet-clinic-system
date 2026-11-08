"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, authApi } from "@/lib/api";
import { Button, Dialog, ErrorState, useToast } from "@/components/ui";
import type { CurrentUser } from "@/types";

function deleteErrorText(err: unknown): string {
  if (err instanceof ApiError && err.status === 409) return "Chỉ xoá được tài khoản Bác sĩ hoặc Nhân viên.";
  return err instanceof ApiError ? err.message : "Không xoá được tài khoản.";
}

/** Xoá hẳn tài khoản Bác sĩ / Nhân viên. Không hoàn tác được nên nói rõ hệ quả trước. */
export function DeleteAccountDialog({ user, onClose }: { user: CurrentUser | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const name = user ? `${user.firstName} ${user.lastName}`.trim() : "";
  const isDoctor = user?.roles.includes("DOCTOR");

  const remove = useMutation({
    mutationFn: (u: CurrentUser) => authApi.deleteUser(u.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      toast.success(`Đã xoá tài khoản ${name}`);
      close();
    },
  });

  function close() {
    remove.reset();
    onClose();
  }

  return (
    <Dialog
      open={user !== null}
      onClose={close}
      title={`Xoá tài khoản ${name}?`}
      description={user?.email}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Huỷ
          </Button>
          <Button variant="danger" loading={remove.isPending} onClick={() => user && remove.mutate(user)}>
            Xoá vĩnh viễn
          </Button>
        </>
      }
    >
      <ul className="list-disc space-y-1.5 pl-5 text-sm text-ink-soft">
        <li>Tài khoản và hồ sơ {isDoctor ? "bác sĩ" : "nhân viên"} bị xoá, không khôi phục được.</li>
        {isDoctor && <li>Lịch khám đã đặt với bác sĩ này vẫn còn — nhân viên cần liên hệ khách để chuyển.</li>}
        <li>Chỉ muốn tạm ngừng? Dùng Khoá thay vì Xoá.</li>
      </ul>
      {remove.isError && <ErrorState className="mt-4" message={deleteErrorText(remove.error)} />}
    </Dialog>
  );
}
