"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, orderApi } from "@/lib/api";
import { ORDER_STATUS } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import {
  Button,
  ErrorState,
  Spinner,
  StatusTag,
  TextAreaField,
  useToast,
} from "@/components/ui";

export default function CustomerOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const toast = useToast();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");

  const order = useQuery({ queryKey: ["orders", id], queryFn: () => orderApi.byId(id) });
  const history = useQuery({
    queryKey: ["orders", id, "history"],
    // Lịch sử là phần phụ; hỏng thì vẫn xem được đơn.
    queryFn: () => orderApi.history(id).catch(() => []),
  });

  const cancel = useMutation({
    mutationFn: () => orderApi.cancelMine(id, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orders"] });
      toast.success("Đã huỷ đơn");
      setCancelling(false);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không huỷ được đơn."),
  });

  if (order.isLoading) return <Spinner label="Đang tải đơn hàng" />;
  if (order.isError || !order.data) {
    return <ErrorState message="Không mở được đơn này." onRetry={() => order.refetch()} />;
  }

  const o = order.data;
  // Khách chỉ huỷ được khi đơn chưa xác nhận; sau đó phải nhờ nhân viên.
  const canCancel = o.status === "PENDING";

  return (
    <>
      <Link href="/customer/orders" className="text-sm text-moss underline underline-offset-2">
        ← Về danh sách đơn
      </Link>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="font-[family-name:var(--font-display)] text-[25px] text-ink">{o.orderCode}</h1>
        <StatusTag status={ORDER_STATUS[o.status]} />
      </div>
      <p className="mt-1 text-sm text-bark">Đặt lúc {formatDateTime(o.createdAt)}</p>

      <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_20rem]">
        <div className="space-y-5">
          <section className="overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface">
            <h2 className="border-b border-line px-4 py-3 font-medium text-ink">Sản phẩm</h2>
            <ul className="divide-y divide-line">
              {o.items.map((it) => (
                <li key={it.productId} className="flex flex-wrap gap-x-4 gap-y-1 px-4 py-3 text-sm">
                  <div className="min-w-40 flex-1">
                    <p className="text-ink">{it.productName}</p>
                    <p className="text-xs text-bark">{it.sku}</p>
                  </div>
                  <span className="tnum text-bark">
                    {formatPrice(it.unitPrice)} × {it.quantity}
                  </span>
                  <span className="tnum w-28 text-right font-medium">{formatPrice(it.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <dl className="space-y-1 border-t border-line px-4 py-3 text-sm">
              <div className="flex justify-between">
                <dt className="text-bark">Tạm tính</dt>
                <dd className="tnum">{formatPrice(o.subtotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-bark">Phí giao hàng</dt>
                <dd className="tnum">{formatPrice(o.shippingFee)}</dd>
              </div>
              <div className="flex justify-between border-t border-line pt-1 font-medium">
                <dt>Tổng cộng</dt>
                <dd className="tnum">{formatPrice(o.total)}</dd>
              </div>
            </dl>
          </section>

          {(history.data ?? []).length > 0 && (
            <section className="overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface">
              <h2 className="border-b border-line px-4 py-3 font-medium text-ink">
                Đơn đã đi qua những bước nào
              </h2>
              <ol className="divide-y divide-line">
                {(history.data ?? []).map((h, i) => (
                  <li key={i} className="flex flex-wrap items-center gap-3 px-4 py-2.5 text-sm">
                    <span className="text-bark">{formatDateTime(h.createdAt)}</span>
                    <StatusTag status={ORDER_STATUS[h.toStatus]} />
                    {h.note && <span className="text-bark">{h.note}</span>}
                  </li>
                ))}
              </ol>
            </section>
          )}
        </div>

        <aside className="space-y-4">
          <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4 text-sm">
            <h2 className="mb-2 font-medium text-ink">Giao tới</h2>
            <p className="font-medium text-ink">{o.recipientName}</p>
            <p className="text-ink-soft">{o.recipientPhone}</p>
            <p className="mt-1 text-ink-soft">{o.shippingAddress}</p>
            {o.note && <p className="mt-2 text-bark">Ghi chú: {o.note}</p>}
            <p className="mt-3 border-t border-line pt-2 text-bark">
              Thanh toán khi nhận hàng
            </p>
          </section>

          {o.status === "CANCELLED" && o.cancelReason && (
            <p className="rounded-[var(--radius-control)] border border-danger/30 bg-danger-wash px-4 py-3 text-sm">
              Lý do huỷ: {o.cancelReason}
            </p>
          )}

          {canCancel && !cancelling && (
            <div>
              <Button variant="danger" className="w-full" onClick={() => setCancelling(true)}>
                Huỷ đơn
              </Button>
              <p className="mt-1 text-xs text-bark">
                Sau khi phòng khám xác nhận thì cần gọi cho nhân viên để huỷ.
              </p>
            </div>
          )}

          {cancelling && (
            <div className="rounded-[var(--radius-control)] border border-danger/30 bg-surface p-4">
              <TextAreaField
                label="Vì sao bạn muốn huỷ"
                rows={2}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
              <div className="mt-3 flex justify-end gap-2">
                <Button variant="secondary" size="sm" onClick={() => setCancelling(false)}>
                  Quay lại
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  disabled={reason.trim().length === 0}
                  loading={cancel.isPending}
                  onClick={() => cancel.mutate()}
                >
                  Xác nhận huỷ
                </Button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}
