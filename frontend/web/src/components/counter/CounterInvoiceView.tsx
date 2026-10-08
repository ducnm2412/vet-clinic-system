"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Minus, Plus, Printer, Search, Trash2 } from "lucide-react";
import { ApiError, orderManageApi, paymentApi, productApi } from "@/lib/api";
import { ORDER_PAYMENT_METHOD } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { CounterInvoiceRequest, Order, Product } from "@/types";
import {
  Button,
  EmptyState,
  IconButton,
  Spinner,
  TextAreaField,
  TextField,
  useToast,
} from "@/components/ui";

type CounterMethod = CounterInvoiceRequest["paymentMethod"];

interface Line {
  product: Product;
  quantity: number;
}

const METHODS: Array<{ value: CounterMethod; label: string; hint: string }> = [
  { value: "CASH", label: "Tiền mặt", hint: "Nhận tiền trực tiếp tại quầy" },
  { value: "BANK_TRANSFER", label: "Chuyển khoản", hint: "Đối chiếu với app ngân hàng rồi ghi mã giao dịch" },
];

/**
 * Hoá đơn gộp tại quầy: một khách có thể vừa trả tiền khám/thuốc vừa mua đồ trong cùng một lần.
 *
 * Lập và thu trong một lần bấm — không có trạng thái "đã lập, chưa thu". Mọi con số (giá hàng,
 * tồn kho, tiền khám, chủ khoản khám) do server tự lấy; màn hình này chỉ chọn "khoản nào, món
 * nào, bao nhiêu" và hiện tổng để nhân viên đọc cho khách, nên tổng ở đây chỉ là tạm tính.
 */
export function CounterInvoiceView({ initialPaymentId }: { initialPaymentId?: string }) {
  const qc = useQueryClient();
  const toast = useToast();

  const [examId, setExamId] = useState(initialPaymentId ?? "");
  const [lines, setLines] = useState<Line[]>([]);
  const [keyword, setKeyword] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [method, setMethod] = useState<CounterMethod>("CASH");
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [receipt, setReceipt] = useState<Order | null>(null);

  const pending = useQuery({ queryKey: ["payments", "pending"], queryFn: paymentApi.pending });
  const payable = (pending.data ?? []).filter((p) => p.status === "PENDING_PAYMENT" && p.amount !== null);
  const awaitingAmount = (pending.data ?? []).filter((p) => p.status === "PENDING_AMOUNT").length;
  // Id từ đường dẫn có thể đã được thu rồi hoặc không còn trong hàng đợi: coi như chưa chọn.
  const exam = payable.find((p) => p.id === examId) ?? null;

  const debounced = useDebounced(keyword.trim(), 300);
  const found = useQuery({
    queryKey: ["products", "counter-search", debounced],
    queryFn: () => productApi.search({ keyword: debounced, size: 8 }),
    enabled: debounced.length >= 2,
  });
  // Tìm kiếm công khai có thể trả cả hàng đã ẩn; ở quầy chỉ bán hàng đang bán.
  const results = (found.data?.content ?? []).filter((p) => p.active);

  const examAmount = exam?.amount ?? 0;
  const productsTotal = lines.reduce((sum, l) => sum + l.product.price * l.quantity, 0);
  const total = examAmount + productsTotal;

  const phoneValid = /^(0\d{9})?$/.test(customerPhone.trim());
  const canSubmit = (exam !== null || lines.length > 0) && phoneValid;

  const submit = useMutation({
    mutationFn: () =>
      orderManageApi.createCounterInvoice({
        examPaymentId: exam?.id,
        customerName: customerName.trim() || undefined,
        customerPhone: customerPhone.trim() || undefined,
        items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        paymentMethod: method,
        transferReference: method === "BANK_TRANSFER" ? reference.trim() || undefined : undefined,
        note: note.trim() || undefined,
      }),
    onSuccess: (order) => {
      setReceipt(order);
      qc.invalidateQueries({ queryKey: ["payments"] });
      qc.invalidateQueries({ queryKey: ["orders"] });
      // Bán hàng trừ kho; thu khoản khám làm bệnh án chuyển sang đã nhận thuốc.
      qc.invalidateQueries({ queryKey: ["products"] });
      qc.invalidateQueries({ queryKey: ["medical-records"] });
      toast.success("Đã thu tiền và lập hoá đơn");
    },
    onError: (err) => {
      // Thiếu hàng, khoản khám đã nằm trong hoá đơn khác… backend trả lời bằng câu đọc được.
      toast.error(err instanceof ApiError ? err.message : "Không lập được hoá đơn.");
      qc.invalidateQueries({ queryKey: ["payments"] });
    },
  });

  function addProduct(product: Product) {
    setLines((current) => {
      const existing = current.find((l) => l.product.id === product.id);
      if (!existing) return [...current, { product, quantity: 1 }];
      return current.map((l) =>
        l.product.id === product.id
          ? { ...l, quantity: Math.min(l.quantity + 1, product.stockQuantity) }
          : l,
      );
    });
  }

  function setQuantity(productId: string, quantity: number) {
    setLines((current) =>
      current.map((l) =>
        l.product.id === productId
          ? { ...l, quantity: Math.max(1, Math.min(Math.floor(quantity) || 1, l.product.stockQuantity)) }
          : l,
      ),
    );
  }

  function reset() {
    setExamId("");
    setLines([]);
    setKeyword("");
    setCustomerName("");
    setCustomerPhone("");
    setMethod("CASH");
    setReference("");
    setNote("");
    setReceipt(null);
  }

  if (receipt) {
    return <Receipt order={receipt} onNew={reset} />;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
      <div className="min-w-0 space-y-6">
        <Section title="1. Tiền khám / thuốc" description="Chọn khoản đang chờ thu nếu khách vừa khám xong.">
          {pending.isLoading ? (
            <Spinner />
          ) : payable.length === 0 ? (
            <p className="text-sm text-bark">
              Không có khoản khám nào đang chờ thu.
              {awaitingAmount > 0 && (
                <>
                  {" "}
                  Còn {awaitingAmount} phiếu chưa nhập số tiền —{" "}
                  <Link href="/staff/payments" className="text-moss underline">
                    nhập số tiền trước
                  </Link>
                  .
                </>
              )}
            </p>
          ) : (
            <ul className="space-y-2" role="radiogroup" aria-label="Khoản khám cần thu">
              <li>
                <RadioCard checked={exam === null} onSelect={() => setExamId("")}>
                  <span className="text-sm text-ink">Không gộp tiền khám (khách chỉ mua đồ)</span>
                </RadioCard>
              </li>
              {payable.map((p) => (
                <li key={p.id}>
                  <RadioCard checked={exam?.id === p.id} onSelect={() => setExamId(p.id)}>
                    <span className="flex flex-wrap items-baseline justify-between gap-x-4">
                      <span className="min-w-0 text-sm text-ink">
                        {p.items.length > 0
                          ? p.items.map((i) => i.medicationName).join(", ")
                          : "Phiếu khám không có thuốc"}
                        <span className="block text-xs text-bark">Kê lúc {formatDateTime(p.createdAt)}</span>
                      </span>
                      <span className="tnum font-medium text-ink">{formatPrice(p.amount)}</span>
                    </span>
                  </RadioCard>
                </li>
              ))}
            </ul>
          )}
        </Section>

        <Section title="2. Sản phẩm khách mua thêm" description="Giá và tồn kho lấy từ kho thật.">
          <div className="relative">
            <Search aria-hidden className="pointer-events-none absolute top-[2.15rem] left-3 size-4 text-bark" />
            <TextField
              label="Tìm sản phẩm"
              placeholder="Tên hoặc mã SKU, ít nhất 2 ký tự"
              className="pl-9"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>

          {debounced.length >= 2 && (
            <div className="mt-2 rounded-[var(--radius-control)] border border-line">
              {found.isLoading ? (
                <div className="p-3">
                  <Spinner />
                </div>
              ) : results.length === 0 ? (
                <p className="p-3 text-sm text-bark">Không tìm thấy sản phẩm nào đang bán.</p>
              ) : (
                <ul className="divide-y divide-line">
                  {results.map((p) => {
                    const out = p.stockQuantity <= 0;
                    return (
                      <li key={p.id} className="flex items-center gap-3 px-3 py-2">
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-ink">{p.name}</p>
                          <p className="text-xs text-bark">
                            {p.sku} · còn {p.stockQuantity} {p.unit}
                          </p>
                        </div>
                        <span className="tnum text-sm">{formatPrice(p.price)}</span>
                        <Button size="sm" variant="secondary" disabled={out} onClick={() => addProduct(p)}>
                          {out ? "Hết hàng" : "Thêm"}
                        </Button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          )}

          <div className="mt-4">
            {lines.length === 0 ? (
              <EmptyState title="Chưa có sản phẩm nào" description="Tìm và thêm món khách muốn mua." />
            ) : (
              <ul className="divide-y divide-line rounded-[var(--radius-control)] border border-line">
                {lines.map((l) => (
                  <li key={l.product.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-3 py-2.5">
                    <div className="min-w-40 flex-1">
                      <p className="text-sm font-medium text-ink">{l.product.name}</p>
                      <p className="text-xs text-bark">
                        {formatPrice(l.product.price)} / {l.product.unit}
                      </p>
                    </div>
                    <div className="flex items-center gap-1">
                      <IconButton
                        label="Giảm số lượng"
                        size="sm"
                        variant="secondary"
                        disabled={l.quantity <= 1}
                        onClick={() => setQuantity(l.product.id, l.quantity - 1)}
                      >
                        <Minus aria-hidden className="size-3.5" />
                      </IconButton>
                      <input
                        aria-label={`Số lượng ${l.product.name}`}
                        type="number"
                        min={1}
                        max={l.product.stockQuantity}
                        value={l.quantity}
                        onChange={(e) => setQuantity(l.product.id, Number(e.target.value))}
                        className="tnum h-8 w-14 rounded-[var(--radius-control)] border border-line-strong bg-surface text-center text-sm"
                      />
                      <IconButton
                        label="Tăng số lượng"
                        size="sm"
                        variant="secondary"
                        disabled={l.quantity >= l.product.stockQuantity}
                        onClick={() => setQuantity(l.product.id, l.quantity + 1)}
                      >
                        <Plus aria-hidden className="size-3.5" />
                      </IconButton>
                    </div>
                    <span className="tnum w-28 text-right text-sm font-medium">
                      {formatPrice(l.product.price * l.quantity)}
                    </span>
                    <IconButton
                      label={`Bỏ ${l.product.name}`}
                      size="sm"
                      variant="ghost"
                      onClick={() => setLines((c) => c.filter((x) => x.product.id !== l.product.id))}
                    >
                      <Trash2 aria-hidden className="size-4" />
                    </IconButton>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Section>

        <Section
          title="3. Thông tin in trên hoá đơn"
          description="Không bắt buộc. Khách có tài khoản được gắn theo khoản khám nên hoá đơn tự hiện trong lịch sử đơn của họ."
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Tên khách"
              maxLength={200}
              value={customerName}
              onChange={(e) => setCustomerName(e.target.value)}
            />
            <TextField
              label="Số điện thoại"
              inputMode="tel"
              maxLength={10}
              error={phoneValid ? undefined : "Gồm 10 chữ số và bắt đầu bằng 0"}
              value={customerPhone}
              onChange={(e) => setCustomerPhone(e.target.value)}
            />
          </div>
          <div className="mt-4">
            <TextAreaField
              label="Ghi chú"
              rows={2}
              maxLength={500}
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
        </Section>
      </div>

      <aside className="lg:sticky lg:top-4 lg:self-start">
        <div className="rounded-[var(--radius-overlay)] border border-line bg-surface p-5">
          <h2 className="font-medium text-ink">Thu tiền</h2>

          <dl className="mt-4 space-y-1.5 text-sm">
            <div className="flex justify-between">
              <dt className="text-bark">Tiền khám / thuốc</dt>
              <dd className="tnum">{formatPrice(examAmount)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-bark">Tiền hàng ({lines.reduce((n, l) => n + l.quantity, 0)} món)</dt>
              <dd className="tnum">{formatPrice(productsTotal)}</dd>
            </div>
            <div className="flex items-baseline justify-between border-t border-line pt-2">
              <dt className="font-medium">Khách cần trả</dt>
              <dd className="font-[family-name:var(--font-display)] text-[25px] text-ink tnum">
                {formatPrice(total)}
              </dd>
            </div>
          </dl>

          <fieldset className="mt-5">
            <legend className="mb-2 text-sm font-medium text-ink">Cách thu</legend>
            <div className="space-y-2" role="radiogroup">
              {METHODS.map((m) => (
                <RadioCard key={m.value} checked={method === m.value} onSelect={() => setMethod(m.value)}>
                  <span className="block text-sm font-medium text-ink">{m.label}</span>
                  <span className="block text-xs text-bark">{m.hint}</span>
                </RadioCard>
              ))}
            </div>
          </fieldset>

          {method === "BANK_TRANSFER" && (
            <div className="mt-4">
              <TextField
                label="Mã giao dịch ngân hàng"
                hint="Không bắt buộc, nhưng nên ghi để đối soát sau"
                maxLength={100}
                value={reference}
                onChange={(e) => setReference(e.target.value)}
              />
            </div>
          )}

          <Button
            size="lg"
            className="mt-5 w-full"
            disabled={!canSubmit}
            loading={submit.isPending}
            onClick={() => submit.mutate()}
          >
            Thu {formatPrice(total)}
          </Button>
          {!canSubmit && (
            <p className="mt-2 text-xs text-bark">Chọn khoản khám hoặc thêm ít nhất một sản phẩm.</p>
          )}
        </div>
      </aside>
    </div>
  );
}

function Section({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-[var(--radius-overlay)] border border-line bg-surface p-5">
      <h2 className="font-medium text-ink">{title}</h2>
      {description ? <p className="mt-0.5 mb-4 text-sm text-bark">{description}</p> : <div className="mb-4" />}
      {children}
    </section>
  );
}

/** Thẻ chọn một trong nhiều — dùng radio thật bên trong để bàn phím và trình đọc màn hình hoạt động. */
function RadioCard({
  checked,
  onSelect,
  children,
}: {
  checked: boolean;
  onSelect: () => void;
  children: React.ReactNode;
}) {
  return (
    <label
      className={`flex cursor-pointer items-start gap-3 rounded-[var(--radius-control)] border px-3 py-2.5 transition-colors ${
        checked ? "border-moss bg-moss-wash" : "border-line-strong bg-surface hover:bg-paper"
      }`}
    >
      <input type="radio" checked={checked} onChange={onSelect} className="mt-1 accent-[var(--moss)]" />
      <span className="min-w-0 flex-1">{children}</span>
    </label>
  );
}

/** Hoá đơn vừa lập. Phần `print-area` là phần duy nhất được in (xem globals.css). */
function Receipt({ order, onNew }: { order: Order; onNew: () => void }) {
  return (
    <div className="mx-auto max-w-xl">
      <div className="print-area rounded-[var(--radius-overlay)] border border-line bg-surface p-6">
        <div className="text-center">
          <p className="font-[family-name:var(--font-display)] text-[22px] text-ink">Hoá đơn thanh toán</p>
          <p className="mt-1 text-sm text-bark">
            {order.orderCode} · {formatDateTime(order.paidAt ?? order.createdAt)}
          </p>
        </div>

        <dl className="mt-5 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-bark">Khách hàng</dt>
          <dd className="text-right">
            {order.recipientName}
            {order.recipientPhone ? ` · ${order.recipientPhone}` : ""}
          </dd>
          <dt className="text-bark">Hình thức thu</dt>
          <dd className="text-right">
            {order.paymentMethod ? ORDER_PAYMENT_METHOD[order.paymentMethod] : "—"}
          </dd>
          {order.transferReference && (
            <>
              <dt className="text-bark">Mã giao dịch</dt>
              <dd className="text-right">{order.transferReference}</dd>
            </>
          )}
        </dl>

        <ul className="mt-5 divide-y divide-line border-y border-line text-sm">
          {order.examAmount > 0 && (
            <li className="flex justify-between py-2">
              <span>Tiền khám / thuốc</span>
              <span className="tnum">{formatPrice(order.examAmount)}</span>
            </li>
          )}
          {order.items.map((it) => (
            <li key={it.productId} className="flex flex-wrap justify-between gap-x-3 py-2">
              <span className="min-w-0 flex-1">{it.productName}</span>
              <span className="tnum text-bark">
                {formatPrice(it.unitPrice)} × {it.quantity}
              </span>
              <span className="tnum w-28 text-right">{formatPrice(it.lineTotal)}</span>
            </li>
          ))}
        </ul>

        <div className="mt-4 flex items-baseline justify-between">
          <span className="font-medium">Tổng đã thu</span>
          <span className="font-[family-name:var(--font-display)] text-[25px] tnum">
            {formatPrice(order.total)}
          </span>
        </div>
        {order.note && <p className="mt-3 text-sm text-bark">Ghi chú: {order.note}</p>}
      </div>

      <div className="mt-4 flex flex-wrap justify-center gap-2">
        <Button variant="secondary" onClick={() => window.print()}>
          <Printer aria-hidden className="size-4" />
          In hoá đơn
        </Button>
        <Button onClick={onNew}>Lập hoá đơn mới</Button>
      </div>
    </div>
  );
}

function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(t);
  }, [value, delayMs]);
  return debounced;
}
