"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Minus, Plus, Printer, Search, Trash2 } from "lucide-react";
import { ApiError, orderManageApi, paymentApi, productApi } from "@/lib/api";
import { ORDER_PAYMENT_METHOD } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { CounterInvoiceRequest, Order, OrderItem, Product } from "@/types";
import {
  Button,
  EmptyState,
  ErrorState,
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
 * Hai lối vào: khách lẻ (lập và thu một lần) và hoá đơn chờ của khoản khám (mở sẵn, thêm sản phẩm,
 * rồi thu). `?payment=` từ phiếu thu thuốc đi vào lối thứ hai.
 */
export function CounterInvoiceView({ initialPaymentId }: { initialPaymentId?: string }) {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const syncGen = useRef(0);
  const appliedInvoice = useRef<string | null>(null);

  const [examId, setExamId] = useState(initialPaymentId ?? "");
  const [draft, setDraft] = useState<Order | null>(null);
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
  const exam = payable.find((p) => p.id === examId) ?? null;
  const fromPaymentLink = Boolean(initialPaymentId);

  const opened = useQuery({
    queryKey: ["exam-invoice", initialPaymentId],
    queryFn: () => orderManageApi.openExamInvoice(initialPaymentId!),
    enabled: Boolean(initialPaymentId) && receipt === null,
    retry: false,
    refetchOnWindowFocus: false,
  });

  const debounced = useDebounced(keyword.trim(), 300);
  const found = useQuery({
    queryKey: ["products", "counter-search", debounced],
    queryFn: () => productApi.search({ keyword: debounced, size: 8 }),
    enabled: debounced.length >= 2,
  });
  const results = (found.data?.content ?? []).filter((p) => p.active);

  const examAmount = draft?.examAmount ?? exam?.amount ?? 0;
  const productsTotal = lines.reduce((sum, l) => sum + l.product.price * l.quantity, 0);
  const total = examAmount + productsTotal;

  const phoneValid = /^(0\d{9})?$/.test(customerPhone.trim());
  const canSubmit = (draft !== null || exam !== null || lines.length > 0) && phoneValid;

  async function hydrateLines(order: Order): Promise<Line[]> {
    return Promise.all(order.items.map(async (it) => {
      try {
        const product = await productApi.byId(it.productId);
        return { product, quantity: it.quantity };
      } catch {
        return { product: productFromItem(it), quantity: it.quantity };
      }
    }));
  }

  async function applyInvoice(order: Order, opts: { keepLocalLines: boolean }) {
    if (order.paymentStatus === "PAID" || order.status === "COMPLETED") {
      setReceipt(order);
      setDraft(null);
      return;
    }
    setDraft(order);
    setExamId(order.examPaymentId ?? examId);
    if (order.recipientName && order.recipientName !== "Khách hàng") {
      setCustomerName(order.recipientName);
    }
    if (order.recipientPhone) setCustomerPhone(order.recipientPhone);
    if (order.note) setNote(order.note);
    if (opts.keepLocalLines) {
      await syncItems(order.id, lines);
    } else {
      setLines(await hydrateLines(order));
    }
  }

  // Chỉ nạp lại khi hoá đơn từ server đổi (id/trạng thái); applyInvoice đọc state hiện tại nên
  // không đưa vào deps, kẻo mỗi lần gõ phím lại nạp đè sản phẩm đang chọn.
  useEffect(() => {
    const order = opened.data;
    if (!order) return;
    const stamp = `${order.id}:${order.status}:${order.paymentStatus}`;
    if (appliedInvoice.current === stamp) return;
    appliedInvoice.current = stamp;
    void applyInvoice(order, { keepLocalLines: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened.data]);

  async function syncItems(orderId: string, next: Line[]) {
    const n = ++syncGen.current;
    try {
      const updated = await orderManageApi.replaceCounterItems(
        orderId,
        next.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
      );
      if (n === syncGen.current) setDraft(updated);
    } catch (err) {
      if (n !== syncGen.current) return;
      toast.error(err instanceof ApiError ? err.message : "Không lưu được sản phẩm trên hoá đơn.");
    }
  }

  function commitLines(next: Line[]) {
    setLines(next);
    if (draft?.status === "PENDING") void syncItems(draft.id, next);
  }

  const pickExam = useMutation({
    mutationFn: (id: string) => orderManageApi.openExamInvoice(id),
    onSuccess: (order) => {
      void applyInvoice(order, { keepLocalLines: lines.length > 0 && order.items.length === 0 });
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không mở được hoá đơn của khoản khám."),
  });

  const collect = useMutation({
    mutationFn: async () => {
      const payload = {
        paymentMethod: method,
        customerName: customerName.trim() || undefined,
        customerPhone: customerPhone.trim() || undefined,
        transferReference: method === "BANK_TRANSFER" ? reference.trim() || undefined : undefined,
        note: note.trim() || undefined,
      };
      if (draft) {
        await orderManageApi.replaceCounterItems(
          draft.id,
          lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        );
        return orderManageApi.collectCounterInvoice(draft.id, payload);
      }
      return orderManageApi.createCounterInvoice({
        examPaymentId: exam?.id,
        items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        ...payload,
      });
    },
    onSuccess: (order) => {
      setReceipt(order);
      setDraft(null);
      qc.invalidateQueries({ queryKey: ["payments"] });
      qc.invalidateQueries({ queryKey: ["orders"] });
      qc.invalidateQueries({ queryKey: ["products"] });
      qc.invalidateQueries({ queryKey: ["medical-records"] });
      // Hoá đơn đã thu: bản lưu cũ (còn chờ) không được dùng lại nếu mở lại đúng đường dẫn này.
      qc.removeQueries({ queryKey: ["exam-invoice"] });
      toast.success("Đã thu tiền và lập hoá đơn");
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : "Không thu được hoá đơn.");
      qc.invalidateQueries({ queryKey: ["payments"] });
      qc.invalidateQueries({ queryKey: ["products"] });
    },
  });

  function addProduct(product: Product) {
    commitLines((() => {
      const existing = lines.find((l) => l.product.id === product.id);
      if (!existing) return [...lines, { product, quantity: 1 }];
      return lines.map((l) =>
        l.product.id === product.id
          ? { ...l, quantity: Math.min(l.quantity + 1, product.stockQuantity) }
          : l,
      );
    })());
  }

  function setQuantity(productId: string, quantity: number) {
    commitLines(
      lines.map((l) =>
        l.product.id === productId
          ? { ...l, quantity: Math.max(1, Math.min(Math.floor(quantity) || 1, l.product.stockQuantity)) }
          : l,
      ),
    );
  }

  function reset() {
    setExamId("");
    setDraft(null);
    setLines([]);
    setKeyword("");
    setCustomerName("");
    setCustomerPhone("");
    setMethod("CASH");
    setReference("");
    setNote("");
    setReceipt(null);
    appliedInvoice.current = null;
    qc.removeQueries({ queryKey: ["exam-invoice"] });
    if (fromPaymentLink) router.replace("/staff/counter");
  }

  if (fromPaymentLink && opened.isLoading) {
    return <Spinner />;
  }

  if (fromPaymentLink && opened.isError) {
    return (
      <ErrorState
        message={
          opened.error instanceof ApiError
            ? opened.error.message
            : "Không mở được hoá đơn của khoản khám này."
        }
        onRetry={() => opened.refetch()}
      />
    );
  }

  if (receipt) {
    return <Receipt order={receipt} onNew={reset} />;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
      <div className="min-w-0 space-y-6">
        <Section
          title="1. Tiền khám / thuốc"
          description={
            draft
              ? `Hoá đơn ${draft.orderCode} đang chờ thu.`
              : "Chọn khoản đang chờ thu nếu khách vừa khám xong."
          }
        >
          {draft && fromPaymentLink ? (
            <div className="rounded-[var(--radius-control)] border border-moss bg-moss-wash px-3 py-2.5 text-sm">
              <p className="font-medium text-ink">
                {exam?.items.length
                  ? exam.items.map((i) => i.medicationName).join(", ")
                  : "Tiền khám / thuốc"}
              </p>
              <p className="mt-0.5 text-bark">
                {formatPrice(examAmount)}
                {exam ? ` · kê lúc ${formatDateTime(exam.createdAt)}` : ""}
              </p>
            </div>
          ) : pending.isLoading || pickExam.isPending ? (
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
                <RadioCard
                  checked={exam === null && draft === null}
                  onSelect={() => {
                    setExamId("");
                    setDraft(null);
                  }}
                >
                  <span className="text-sm text-ink">Không gộp tiền khám (khách chỉ mua đồ)</span>
                </RadioCard>
              </li>
              {payable.map((p) => (
                <li key={p.id}>
                  <RadioCard
                    checked={(draft?.examPaymentId ?? examId) === p.id}
                    onSelect={() => {
                      setExamId(p.id);
                      pickExam.mutate(p.id);
                    }}
                  >
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

        <Section title="2. Sản phẩm khách mua thêm" description="Giá và tồn kho lấy từ kho thật. Chưa trừ kho cho đến lúc thu.">
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
                      onClick={() => commitLines(lines.filter((x) => x.product.id !== l.product.id))}
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
          description={
            draft?.userId
              ? "Tên và số điện thoại được lấy từ hồ sơ khách; sửa nếu khách muốn in thông tin khác. Hoá đơn đã hiện trong lịch sử đơn của khách."
              : "Không bắt buộc. Khách lẻ không có tài khoản thì nhập tên và số điện thoại nếu muốn in trên hoá đơn."
          }
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
            loading={collect.isPending}
            onClick={() => collect.mutate()}
          >
            Thu {formatPrice(total)}
          </Button>
          {!canSubmit && !draft && (
            <p className="mt-2 text-xs text-bark">Chọn khoản khám hoặc thêm ít nhất một sản phẩm.</p>
          )}
        </div>
      </aside>
    </div>
  );
}

function productFromItem(it: OrderItem): Product {
  return {
    id: it.productId,
    categoryId: "",
    categoryName: "",
    sku: it.sku,
    name: it.productName,
    description: null,
    price: it.unitPrice,
    unit: "sp",
    imageUrl: null,
    stockQuantity: it.quantity,
    lowStockThreshold: null,
    lowStock: false,
    active: true,
    createdAt: "",
    updatedAt: "",
  };
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
              <dd className="tnum text-right">{order.transferReference}</dd>
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
