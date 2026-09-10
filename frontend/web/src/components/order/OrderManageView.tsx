"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, nextActions, orderManageApi } from "@/lib/api";
import { ORDER_STATUS } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { Order, OrderStatus, OrderSummary } from "@/types";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  Pagination,
  Spinner,
  StatusTag,
  TableFrame,
  TableSkeleton,
  TextAreaField,
  useToast,
  type Column,
} from "@/components/ui";

const TABS: Array<{ value: OrderStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "Tất cả" },
  { value: "PENDING", label: "Chờ xác nhận" },
  { value: "CONFIRMED", label: "Đã xác nhận" },
  { value: "SHIPPING", label: "Đang giao" },
  { value: "COMPLETED", label: "Hoàn tất" },
  { value: "CANCELLED", label: "Đã huỷ" },
];

const ACTION_LABEL = {
  confirm: "Xác nhận",
  ship: "Giao hàng",
  complete: "Hoàn tất",
  cancel: "Huỷ đơn",
} as const;

/** Quản lý đơn cho nhân viên và quản trị — hai vai trò dùng chung endpoint `/orders/manage`. */
export function OrderManageView() {
  const [status, setStatus] = useState<OrderStatus | "ALL">("PENDING");
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);

  const orders = useQuery({
    queryKey: ["orders", "manage", { status, page }],
    queryFn: () =>
      orderManageApi.list({ status: status === "ALL" ? undefined : status, page, size: 20 }),
  });

  const columns: Column<OrderSummary>[] = [
    {
      key: "code",
      header: "Mã đơn",
      cell: (o) => <span className="font-medium text-ink">{o.orderCode}</span>,
    },
    { key: "recipient", header: "Người nhận", cell: (o) => o.recipientName },
    {
      key: "created",
      header: "Đặt lúc",
      hideBelow: "lg",
      cell: (o) => <span className="text-bark">{formatDateTime(o.createdAt)}</span>,
    },
    { key: "items", header: "Số món", numeric: true, hideBelow: "lg", cell: (o) => o.totalItems },
    { key: "status", header: "Trạng thái", cell: (o) => <StatusTag status={ORDER_STATUS[o.status]} /> },
    { key: "total", header: "Tổng tiền", numeric: true, cell: (o) => formatPrice(o.total) },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="Lọc theo trạng thái đơn">
        {TABS.map((t) => (
          <button
            key={t.value}
            onClick={() => {
              setStatus(t.value);
              setPage(0);
            }}
            aria-pressed={status === t.value}
            className={`rounded-[var(--radius-control)] border px-3 py-1.5 text-sm transition-colors ${
              status === t.value
                ? "border-moss bg-moss-wash font-medium text-ink"
                : "border-line-strong bg-surface text-ink-soft hover:bg-paper"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <TableFrame title="Đơn hàng">
        {orders.isLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : orders.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                orders.error instanceof ApiError ? orders.error.message : "Không tải được đơn hàng."
              }
              onRetry={() => orders.refetch()}
            />
          </div>
        ) : (
          <>
            <DataTable
              caption="Danh sách đơn hàng"
              rows={orders.data?.content ?? []}
              keyOf={(o) => o.id}
              columns={columns}
              onRowClick={(o) => setOpenId(o.id)}
              empty={
                <EmptyState
                  title={
                    status === "ALL" ? "Chưa có đơn hàng nào" : "Không có đơn nào ở trạng thái này"
                  }
                />
              }
            />
            {orders.data && (
              <Pagination
                page={orders.data.page}
                totalPages={orders.data.totalPages}
                totalElements={orders.data.totalElements}
                unitLabel="đơn hàng"
                onChange={setPage}
              />
            )}
          </>
        )}
      </TableFrame>

      <OrderDetailDialog orderId={openId} onClose={() => setOpenId(null)} />
    </>
  );
}

function OrderDetailDialog({ orderId, onClose }: { orderId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");

  const order = useQuery({
    queryKey: ["orders", "manage", orderId],
    queryFn: () => orderManageApi.byId(orderId!),
    enabled: orderId !== null,
  });

  const act = useMutation({
    mutationFn: async (action: keyof typeof ACTION_LABEL) => {
      if (!orderId) throw new Error("Chưa chọn đơn");
      if (action === "cancel") return orderManageApi.cancel(orderId, reason);
      return orderManageApi[action](orderId);
    },
    onSuccess: (_data, action) => {
      qc.invalidateQueries({ queryKey: ["orders"] });
      // Xác nhận đơn phát order.completed để trừ kho; huỷ đơn đã xác nhận thì hoàn kho.
      // Làm mới cả tồn kho để con số trên màn hình kho khớp ngay.
      qc.invalidateQueries({ queryKey: ["products"] });
      toast.success(
        action === "confirm"
          ? "Đã xác nhận đơn, kho đã trừ hàng"
          : action === "cancel"
            ? "Đã huỷ đơn"
            : action === "ship"
              ? "Đã chuyển sang đang giao"
              : "Đã hoàn tất đơn",
      );
      setCancelling(false);
      setReason("");
      onClose();
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không thực hiện được thao tác."),
  });

  const data = order.data;
  const actions = data ? nextActions(data.status) : [];

  return (
    <Dialog
      open={orderId !== null}
      onClose={onClose}
      title={data ? data.orderCode : "Chi tiết đơn"}
      description={data ? `Đặt lúc ${formatDateTime(data.createdAt)}` : undefined}
      footer={
        data && actions.length > 0 ? (
          <>
            {actions
              .filter((a) => a !== "cancel")
              .map((a) => (
                <Button key={a} loading={act.isPending} onClick={() => act.mutate(a)}>
                  {ACTION_LABEL[a]}
                </Button>
              ))}
            {actions.includes("cancel") && (
              <Button variant="danger" onClick={() => setCancelling(true)}>
                Huỷ đơn
              </Button>
            )}
          </>
        ) : (
          <Button variant="secondary" onClick={onClose}>
            Đóng
          </Button>
        )
      }
    >
      {order.isLoading ? (
        <Spinner />
      ) : order.isError || !data ? (
        <ErrorState message="Không tải được chi tiết đơn." onRetry={() => order.refetch()} />
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <StatusTag status={ORDER_STATUS[data.status]} />
            <span className="text-sm text-bark">Thanh toán khi nhận hàng</span>
          </div>

          <div className="rounded-[var(--radius-control)] bg-paper px-3 py-2.5 text-sm">
            <p className="font-medium text-ink">{data.recipientName}</p>
            <p className="text-ink-soft">{data.recipientPhone}</p>
            <p className="mt-0.5 text-ink-soft">{data.shippingAddress}</p>
            {data.note && <p className="mt-1 text-bark">Ghi chú: {data.note}</p>}
          </div>

          <ul className="divide-y divide-line text-sm">
            {data.items.map((it) => (
              <li key={it.productId} className="flex flex-wrap gap-x-3 py-2">
                <span className="min-w-0 flex-1">
                  <span className="block text-ink">{it.productName}</span>
                  <span className="text-xs text-bark">{it.sku}</span>
                </span>
                <span className="tnum text-bark">
                  {formatPrice(it.unitPrice)} × {it.quantity}
                </span>
                <span className="tnum w-24 text-right font-medium">{formatPrice(it.lineTotal)}</span>
              </li>
            ))}
          </ul>

          <dl className="space-y-1 border-t border-line pt-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-bark">Tạm tính</dt>
              <dd className="tnum">{formatPrice(data.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-bark">Phí giao hàng</dt>
              <dd className="tnum">{formatPrice(data.shippingFee)}</dd>
            </div>
            <div className="flex justify-between border-t border-line pt-1 font-medium">
              <dt>Tổng cộng</dt>
              <dd className="tnum">{formatPrice(data.total)}</dd>
            </div>
          </dl>

          {data.status === "CANCELLED" && data.cancelReason && (
            <p className="rounded-[var(--radius-control)] bg-danger-wash px-3 py-2 text-sm">
              Lý do huỷ: {data.cancelReason}
            </p>
          )}

          {cancelling && (
            <div className="rounded-[var(--radius-control)] border border-danger/30 p-3">
              <TextAreaField
                label="Lý do huỷ"
                rows={2}
                required
                hint={
                  data.status === "PENDING"
                    ? "Đơn chưa xác nhận nên kho chưa bị trừ."
                    : "Đơn đã trừ kho — huỷ sẽ hoàn hàng về kho."
                }
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
                  loading={act.isPending}
                  onClick={() => act.mutate("cancel")}
                >
                  Xác nhận huỷ
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </Dialog>
  );
}
