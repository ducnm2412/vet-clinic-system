"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, paymentApi } from "@/lib/api";
import { PAYMENT_STATUS } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { Payment } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  StatusTag,
  TableFrame,
  TableSkeleton,
  TextField,
  useToast,
  type Column,
} from "@/components/ui";

/**
 * Thu tiền mặt cho đơn thuốc tại quầy. Luồng: bác sĩ kê đơn → payment-service tạo phiếu
 * (chưa có số tiền) → nhân viên nhập số tiền → khách trả → xác nhận đã thu, lúc đó
 * payment-service phát `payment.completed` để booking mở đơn thuốc cho khách nhận.
 */
export default function StaffPaymentsPage() {
  const [selected, setSelected] = useState<Payment | null>(null);

  const payments = useQuery({ queryKey: ["payments", "pending"], queryFn: paymentApi.pending });

  const columns: Column<Payment>[] = [
    {
      key: "items",
      header: "Đơn thuốc",
      cell: (p) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">
            {p.items.length > 0 ? p.items.map((i) => i.medicationName).join(", ") : "Chưa có thuốc"}
          </p>
          <p className="text-xs text-bark">{p.items.length} loại thuốc</p>
        </div>
      ),
    },
    {
      key: "created",
      header: "Kê lúc",
      hideBelow: "lg",
      cell: (p) => <span className="text-bark">{formatDateTime(p.createdAt)}</span>,
    },
    { key: "status", header: "Trạng thái", cell: (p) => <StatusTag status={PAYMENT_STATUS[p.status]} /> },
    {
      key: "amount",
      header: "Số tiền",
      numeric: true,
      cell: (p) =>
        p.amount === null ? <span className="text-bark">Chưa nhập</span> : formatPrice(p.amount),
    },
  ];

  return (
    <>
      <PageHeader
        title="Thu tiền đơn thuốc"
        description="Nhập số tiền theo đơn bác sĩ kê, rồi xác nhận khi khách đã trả."
      />

      <TableFrame title="Phiếu chờ xử lý" count={payments.data?.length}>
        {payments.isLoading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : payments.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                payments.error instanceof ApiError ? payments.error.message : "Không tải được phiếu thu."
              }
              onRetry={() => payments.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Phiếu thu tiền đơn thuốc đang chờ"
            rows={payments.data ?? []}
            keyOf={(p) => p.id}
            columns={columns}
            onRowClick={setSelected}
            empty={
              <EmptyState
                title="Không còn phiếu nào chờ xử lý"
                description="Phiếu mới sẽ hiện ở đây ngay khi bác sĩ kê đơn."
              />
            }
          />
        )}
      </TableFrame>

      <PaymentDialog payment={selected} onClose={() => setSelected(null)} />
    </>
  );
}

function PaymentDialog({ payment, onClose }: { payment: Payment | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [amount, setAmount] = useState("");

  const setAmountMutation = useMutation({
    mutationFn: () => paymentApi.setAmount(payment!.id, { amount: Number(amount) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["payments"] });
      toast.success("Đã lưu số tiền");
      setAmount("");
      onClose();
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được số tiền."),
  });

  const confirmMutation = useMutation({
    mutationFn: () => paymentApi.confirmCash(payment!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["payments"] });
      qc.invalidateQueries({ queryKey: ["medical-records"] });
      toast.success("Đã thu tiền, khách có thể nhận thuốc");
      onClose();
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không xác nhận được."),
  });

  if (!payment) return null;

  const needsAmount = payment.status === "PENDING_AMOUNT";
  const parsed = Number(amount);
  const amountValid = amount !== "" && Number.isFinite(parsed) && parsed > 0;

  return (
    <Dialog
      open
      onClose={onClose}
      title="Phiếu thu tiền thuốc"
      description={`Kê lúc ${formatDateTime(payment.createdAt)}`}
      footer={
        needsAmount ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Đóng
            </Button>
            <Button
              disabled={!amountValid}
              loading={setAmountMutation.isPending}
              onClick={() => setAmountMutation.mutate()}
            >
              Lưu số tiền
            </Button>
          </>
        ) : payment.status === "PENDING_PAYMENT" ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Đóng
            </Button>
            <Button loading={confirmMutation.isPending} onClick={() => confirmMutation.mutate()}>
              Đã nhận tiền
            </Button>
          </>
        ) : (
          <Button variant="secondary" onClick={onClose}>
            Đóng
          </Button>
        )
      }
    >
      <div className="space-y-4">
        <StatusTag status={PAYMENT_STATUS[payment.status]} />

        <ul className="divide-y divide-line text-sm">
          {payment.items.map((item) => (
            <li key={item.id} className="py-2.5">
              <p className="font-medium text-ink">{item.medicationName}</p>
              <p className="mt-0.5 text-bark">
                {item.dosage} · {item.frequency}
                {item.durationDays ? ` · ${item.durationDays} ngày` : ""}
              </p>
            </li>
          ))}
          {payment.items.length === 0 && (
            <li className="py-3 text-sm text-bark">Phiếu này không có dòng thuốc nào.</li>
          )}
        </ul>

        {needsAmount ? (
          <TextField
            label="Số tiền cần thu"
            type="number"
            min={1}
            step={1000}
            required
            hint="Tính theo đơn thuốc bác sĩ kê"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        ) : (
          <div className="rounded-[var(--radius-control)] bg-paper px-3 py-2.5">
            <p className="text-sm text-bark">Số tiền</p>
            <p className="font-[family-name:var(--font-display)] text-[25px] text-ink tnum">
              {formatPrice(payment.amount)}
            </p>
            {payment.paidAt && (
              <p className="mt-1 text-sm text-bark">Đã thu lúc {formatDateTime(payment.paidAt)}</p>
            )}
          </div>
        )}
      </div>
    </Dialog>
  );
}
