"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, bookingApi, medicationApi, orderManageApi, paymentApi } from "@/lib/api";
import { PAYMENT_STATUS } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { Payment, PaymentItem } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import { ProductAddOn, type ProductLine } from "@/components/counter/ProductAddOn";
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
 * Phiếu thu tại quầy. Bác sĩ lưu bệnh án → nhân viên xác nhận tiền dịch vụ và nhập số lượng thuốc (đơn giá
 * điền sẵn) → mở hoá đơn để thêm sản phẩm (nếu có) rồi thu một lần.
 * Tổng thu = tiền dịch vụ + tiền thuốc (nếu có) + tiền sản phẩm mua thêm (nếu có). Cả ba nhập ngay trong phiếu;
 * khi chốt, dịch vụ và thuốc thành khoản khám của phiếu, sản phẩm được ghi vào hoá đơn quầy của khoản đó.
 * Không thu tiền ngay trên phiếu này: thu ở hoá đơn quầy.
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
        description="Xác nhận tiền dịch vụ, số lượng thuốc và sản phẩm khách mua thêm; hệ thống tự cộng tổng."
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
  if (!payment) return null;
  // key theo phiếu: chọn phiếu khác thì các ô đang gõ bắt đầu lại từ dữ liệu của phiếu đó.
  return <PaymentDialogBody key={payment.id} payment={payment} onClose={onClose} />;
}

/** Bỏ dấu, chữ thường — cùng cách backend chuẩn hoá tên thuốc để khớp danh mục. */
function normalizeName(s: string): string {
  return s
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

/** Chuỗi rỗng = chưa nhập; khác với số 0. */
function toNumber(value: string): number | null {
  if (value.trim() === "") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/** Ô đang gõ. `unitPrice` null = chưa gõ: dùng giá có sẵn (của phiếu hoặc danh mục) thay vì chuỗi rỗng. */
interface LineDraft {
  unitPrice: string | null;
  quantity: string;
}

function lineAmountOf(d: LineDraft, defaultPrice: string): number | null {
  const price = toNumber(d.unitPrice ?? defaultPrice);
  const qty = toNumber(d.quantity);
  if (price === null || qty === null || price < 0 || !Number.isInteger(qty) || qty < 1) return null;
  return price * qty;
}

function PaymentDialogBody({ payment, onClose }: { payment: Payment; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();

  const needsAmount = payment.status === "PENDING_AMOUNT";

  // Phiếu tạo trước khi có tính tiền dịch vụ thì chưa mang tiền dịch vụ: hỏi lại lịch hẹn để điền sẵn.
  const needsServiceLookup = needsAmount && payment.serviceAmount === null;
  const appointment = useQuery({
    queryKey: ["appointments", payment.appointmentId],
    queryFn: () => bookingApi.byId(payment.appointmentId),
    enabled: needsServiceLookup,
    staleTime: 60_000,
  });
  const serviceName = payment.serviceName ?? appointment.data?.billingServiceName ?? null;
  const defaultServiceAmount =
    payment.serviceAmount !== null
      ? String(payment.serviceAmount)
      : appointment.data?.billingServicePrice != null
        ? String(appointment.data.billingServicePrice)
        : "";
  const [serviceDraft, setServiceDraft] = useState<string | null>(null);
  const serviceText = serviceDraft ?? defaultServiceAmount;
  // Để trống = không tính tiền dịch vụ (lượt khám không có dịch vụ).
  const serviceAmount = serviceText.trim() === "" ? 0 : toNumber(serviceText);
  const serviceValid = serviceAmount !== null && serviceAmount >= 0;

  // Dòng thuốc thiếu giá (phiếu cũ, hoặc lúc kê thuốc chưa có giá): tra giá hiện tại trong danh mục theo tên.
  const lookups = useQueries({
    queries: payment.items.map((i: PaymentItem) => ({
      queryKey: ["medication-price", i.medicationName],
      enabled: needsAmount && i.unitPrice === null,
      staleTime: 60_000,
      queryFn: async () => {
        const found = await medicationApi.search(i.medicationName, { limit: 10 });
        const wanted = normalizeName(i.medicationName);
        return found.find((m) => normalizeName(m.name) === wanted)?.unitPrice ?? null;
      },
    })),
  });
  /** Giá mặc định của dòng: giá trên phiếu, không có thì giá danh mục, không có nữa thì để trống. */
  const defaultPrice = (idx: number): string => {
    const item = payment.items[idx];
    if (item.unitPrice !== null) return String(item.unitPrice);
    const fromCatalog = lookups[idx]?.data;
    return fromCatalog == null ? "" : String(fromCatalog);
  };

  const [drafts, setDrafts] = useState<Record<string, LineDraft>>(() =>
    Object.fromEntries(
      payment.items.map((i: PaymentItem) => [i.id, { unitPrice: null, quantity: String(i.quantity ?? 1) }]),
    ),
  );
  function setDraft(id: string, patch: Partial<LineDraft>) {
    setDrafts((d) => ({ ...d, [id]: { ...d[id], ...patch } }));
  }

  // Sản phẩm cửa hàng khách mua thêm (nếu có): cộng vào tổng thu, ghi vào hoá đơn quầy lúc chốt.
  const [productLines, setProductLines] = useState<ProductLine[]>([]);
  const productsTotal = productLines.reduce((sum, l) => sum + l.product.price * l.quantity, 0);

  const lineAmounts = payment.items.map((i, idx) => lineAmountOf(drafts[i.id], defaultPrice(idx)));
  const drugsValid = lineAmounts.every((a) => a !== null);
  const drugsTotal = lineAmounts.reduce<number>((sum, a) => sum + (a ?? 0), 0);
  // Tiền khám (dịch vụ + thuốc) là thứ phiếu thu lưu; sản phẩm cộng thêm ở hoá đơn quầy.
  const examTotal = (serviceValid ? (serviceAmount ?? 0) : 0) + drugsTotal;
  const grandTotal = examTotal + productsTotal;
  const canSave = serviceValid && drugsValid && examTotal > 0;

  /**
   * Chốt tiền khám, rồi ghi sản phẩm (nếu có) vào hoá đơn quầy của khoản khám. Chốt xong thì tiền khám đã
   * lưu dù bước sản phẩm có lỗi: khi đó báo rõ và quầy thêm lại ở hoá đơn, không phải làm lại từ đầu.
   * `goCollect` = chuyển luôn sang hoá đơn quầy để thu tiền.
   */
  const save = useMutation({
    mutationFn: async (goCollect: boolean) => {
      await paymentApi.setItems(payment.id, {
        items: payment.items.map((i, idx) => ({
          itemId: i.id,
          unitPrice: Number(drafts[i.id].unitPrice ?? defaultPrice(idx)),
          quantity: Number(drafts[i.id].quantity),
        })),
        serviceAmount: serviceAmount ?? 0,
        serviceName: serviceName ?? undefined,
      });
      let productsSaved = true;
      if (productLines.length > 0) {
        try {
          const invoice = await orderManageApi.openExamInvoice(payment.id);
          await orderManageApi.replaceCounterItems(
            invoice.id,
            productLines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
          );
        } catch {
          productsSaved = false;
        }
      }
      return { goCollect, productsSaved };
    },
    onSuccess: ({ goCollect, productsSaved }) => {
      qc.invalidateQueries({ queryKey: ["payments"] });
      qc.invalidateQueries({ queryKey: ["exam-invoice"] });
      if (productsSaved) {
        toast.success("Đã chốt số tiền");
      } else {
        toast.error("Đã chốt tiền khám nhưng chưa lưu được sản phẩm. Hãy thêm lại ở hoá đơn quầy.");
      }
      onClose();
      if (goCollect) router.push(`/staff/counter?payment=${payment.id}`);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được số tiền."),
  });

  return (
    <Dialog
      open
      onClose={onClose}
      title="Phiếu thu tiền khám"
      description={`Kê lúc ${formatDateTime(payment.createdAt)}`}
      footer={
        needsAmount ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Đóng
            </Button>
            <Button
              variant="secondary"
              disabled={!canSave || save.isPending}
              onClick={() => save.mutate(false)}
            >
              Chốt số tiền
            </Button>
            <Button disabled={!canSave} loading={save.isPending} onClick={() => save.mutate(true)}>
              Chốt và thu tiền
            </Button>
          </>
        ) : payment.status === "PENDING_PAYMENT" ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Đóng
            </Button>
            <Button onClick={() => router.push(`/staff/counter?payment=${payment.id}`)}>
              Mở hoá đơn / thêm sản phẩm
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

        {needsAmount ? (
          <>
            <section>
              <h3 className="text-sm font-medium text-ink">Dịch vụ</h3>
              <p className="mt-0.5 text-sm text-bark">{serviceName ?? "Không chọn dịch vụ"}</p>
              <div className="mt-2 max-w-[14rem]">
                <TextField
                  label="Tiền dịch vụ"
                  type="number"
                  min={0}
                  step={1000}
                  inputMode="numeric"
                  placeholder="0"
                  hint={
                    payment.serviceAmount === null && appointment.data?.billingServicePrice != null
                      ? "Giá tham khảo của dịch vụ, sửa được"
                      : "Sửa được nếu khác giá tham khảo"
                  }
                  error={serviceValid ? undefined : "Tiền dịch vụ không hợp lệ"}
                  value={serviceText}
                  onChange={(e) => setServiceDraft(e.target.value)}
                />
              </div>
            </section>

            <section>
              <h3 className="text-sm font-medium text-ink">Thuốc</h3>
              {payment.items.length === 0 ? (
                <p className="mt-1 text-sm text-bark">Lượt khám này không kê thuốc.</p>
              ) : (
                <ul className="divide-y divide-line text-sm">
                  {payment.items.map((item, idx) => {
                    const d = drafts[item.id];
                    const line = lineAmounts[idx];
                    const noPrice = defaultPrice(idx) === "" && d.unitPrice === null;
                    return (
                      <li key={item.id} className="py-3">
                        <p className="font-medium text-ink">{item.medicationName}</p>
                        <p className="mt-0.5 text-bark">
                          {item.dosage} · {item.frequency}
                          {item.durationDays ? ` · ${item.durationDays} ngày` : ""}
                        </p>
                        {/*
                          Căn theo đỉnh, dòng gợi ý nằm NGOÀI hàng: để gợi ý trong ô đơn giá thì ô đó cao
                          hơn ô số lượng và hai ô lệch nhau. Thành tiền đẩy xuống ngang hai ô nhập (chiều
                          cao nhãn + ô).
                        */}
                        <div className="mt-2 grid grid-cols-[1fr_1fr_6rem] items-start gap-2">
                          <TextField
                            label="Số lượng"
                            type="number"
                            min={1}
                            step={1}
                            inputMode="numeric"
                            value={d.quantity}
                            onChange={(e) => setDraft(item.id, { quantity: e.target.value })}
                          />
                          <TextField
                            label="Đơn giá"
                            type="number"
                            min={0}
                            step={500}
                            inputMode="numeric"
                            placeholder={noPrice ? "Nhập giá" : undefined}
                            value={d.unitPrice ?? defaultPrice(idx)}
                            onChange={(e) => setDraft(item.id, { unitPrice: e.target.value })}
                          />
                          <p className="tnum mt-[1.625rem] flex h-9 items-center justify-end text-ink">
                            {line === null ? "—" : formatPrice(line)}
                          </p>
                        </div>
                        {noPrice && !lookups[idx]?.isLoading && (
                          <p className="mt-1 text-xs text-bark">Thuốc chưa có giá trong danh mục, nhập đơn giá.</p>
                        )}
                        {item.unitPrice === null && !noPrice && d.unitPrice === null && (
                          <p className="mt-1 text-xs text-bark">Đơn giá lấy từ danh mục thuốc hiện tại.</p>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>

            <section>
              <h3 className="text-sm font-medium text-ink">Sản phẩm mua thêm</h3>
              <p className="mt-0.5 mb-2 text-sm text-bark">
                Khách mua thêm đồ ở cửa hàng thì thêm vào đây. Giá và tồn kho lấy từ kho thật, chưa trừ kho cho
                đến lúc thu.
              </p>
              <ProductAddOn lines={productLines} onChange={setProductLines} />
            </section>

            <Breakdown
              service={serviceValid ? (serviceAmount ?? 0) : null}
              drugs={drugsValid ? drugsTotal : null}
              products={productsTotal}
              total={serviceValid && drugsValid ? grandTotal : null}
            />
          </>
        ) : (
          <>
            {payment.serviceAmount !== null && (
              <p className="text-sm text-bark">
                Dịch vụ{payment.serviceName ? ` ${payment.serviceName}` : ""}:{" "}
                <span className="tnum text-ink">{formatPrice(payment.serviceAmount)}</span>
              </p>
            )}
            <ul className="divide-y divide-line text-sm">
              {payment.items.map((item) => (
                <li key={item.id} className="py-2.5">
                  <p className="font-medium text-ink">{item.medicationName}</p>
                  <p className="mt-0.5 text-bark">
                    {item.dosage} · {item.frequency}
                    {item.durationDays ? ` · ${item.durationDays} ngày` : ""}
                  </p>
                  {item.lineAmount !== null && (
                    <p className="mt-0.5 text-bark tnum">
                      {item.quantity} × {formatPrice(item.unitPrice)} = {formatPrice(item.lineAmount)}
                    </p>
                  )}
                </li>
              ))}
              {payment.items.length === 0 && <li className="py-3 text-sm text-bark">Không kê thuốc.</li>}
            </ul>
            <div className="rounded-[var(--radius-control)] bg-paper px-3 py-2.5">
              <p className="text-sm text-bark">Tiền khám (dịch vụ + thuốc)</p>
              <p className="font-[family-name:var(--font-display)] text-[25px] text-ink tnum">
                {formatPrice(payment.amount)}
              </p>
              {payment.paidAt && (
                <p className="mt-1 text-sm text-bark">Đã thu lúc {formatDateTime(payment.paidAt)}</p>
              )}
              {payment.status === "PENDING_PAYMENT" && (
                <p className="mt-1 text-xs text-bark">Sản phẩm mua thêm (nếu có) được cộng ở hoá đơn quầy.</p>
              )}
            </div>
          </>
        )}
      </div>
    </Dialog>
  );
}

/** Tách rõ từng khoản để quầy đối chiếu với khách: dịch vụ, thuốc, tổng; sản phẩm cộng sau ở hoá đơn. */
function Breakdown({
  service,
  drugs,
  products,
  total,
}: {
  service: number | null;
  drugs: number | null;
  products: number;
  total: number | null;
}) {
  const money = (v: number | null) => (v === null ? "—" : formatPrice(v));
  return (
    <div className="rounded-[var(--radius-control)] bg-paper px-3 py-2.5 text-sm">
      <div className="flex justify-between text-bark">
        <span>Tiền dịch vụ</span>
        <span className="tnum text-ink">{money(service)}</span>
      </div>
      <div className="mt-1 flex justify-between text-bark">
        <span>Tiền thuốc</span>
        <span className="tnum text-ink">{money(drugs)}</span>
      </div>
      <div className="mt-1 flex justify-between text-bark">
        <span>Tiền sản phẩm</span>
        <span className="tnum text-ink">{money(products)}</span>
      </div>
      <div className="mt-2 flex items-baseline justify-between border-t border-line pt-2">
        <span className="text-bark">Tổng thu</span>
        <span className="font-[family-name:var(--font-display)] text-[25px] text-ink tnum">{money(total)}</span>
      </div>
    </div>
  );
}
