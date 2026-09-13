"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, authApi } from "@/lib/api";
import { Button, Dialog, ErrorState, useToast } from "@/components/ui";
import type { CurrentUser } from "@/types";

/** Backend trả câu tiếng Anh cho 409; đổi sang lời admin đọc được. */
function lockErrorText(err: unknown): string {
  if (err instanceof ApiError && err.status === 409) {
    return /own account/i.test(err.message)
      ? "Không tự khoá được tài khoản đang đăng nhập."
      : "Đây là tài khoản quản trị cuối cùng còn hoạt động — khoá nữa thì không ai quản trị được hệ thống.";
  }
  return err instanceof ApiError ? err.message : "Không đổi được trạng thái tài khoản.";
}

/**
 * Xác nhận khoá / mở khoá. Nói rõ hệ quả trước khi bấm: khoá là việc ảnh hưởng người khác
 * (bác sĩ ngừng nhận lịch), không phải thao tác thử cho vui.
 */
export function LockAccountDialog({ user, onClose }: { user: CurrentUser | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const locking = user?.status !== "LOCKED";
  const name = user ? `${user.firstName} ${user.lastName}`.trim() : "";
  const isDoctor = user?.roles.includes("DOCTOR");

  const change = useMutation({
    mutationFn: (u: CurrentUser) => (locking ? authApi.lockUser(u.id) : authApi.unlockUser(u.id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      toast.success(locking ? `Đã khoá tài khoản ${name}` : `Đã mở khoá tài khoản ${name}`);
      close();
    },
  });

  function close() {
    change.reset();
    onClose();
  }

  return (
    <Dialog
      open={user !== null}
      onClose={close}
      title={locking ? `Khoá tài khoản ${name}?` : `Mở khoá tài khoản ${name}?`}
      description={user?.email}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Huỷ
          </Button>
          <Button
            variant={locking ? "danger" : "primary"}
            loading={change.isPending}
            onClick={() => user && change.mutate(user)}
          >
            {locking ? "Khoá tài khoản" : "Mở khoá"}
          </Button>
        </>
      }
    >
      {locking ? (
        <ul className="list-disc space-y-1.5 pl-5 text-sm text-ink-soft">
          <li>Không đăng nhập được nữa; phiên đang mở bị thoát trong tối đa 15 phút.</li>
          {isDoctor && <li>Ngừng nhận lịch khám mới. Lịch đã đặt vẫn giữ — nhân viên cần liên hệ khách để đổi.</li>}
          <li>Lịch sử khám, bệnh án và đơn hàng giữ nguyên. Mở khoá lại bất cứ lúc nào.</li>
        </ul>
      ) : (
        <p className="text-sm text-ink-soft">
          Người này đăng nhập lại được{isDoctor ? " và nhận lịch khám trở lại" : ""}. Tài khoản chưa xác minh email vẫn
          phải xác minh trước khi đăng nhập.
        </p>
      )}
      {change.isError && <ErrorState className="mt-4" message={lockErrorText(change.error)} />}
    </Dialog>
  );
}
