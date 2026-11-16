"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Boxes, History, SlidersHorizontal } from "lucide-react";
import { ApiError, medicationApi } from "@/lib/api";
import { STOCK_STATUS } from "@/lib/utils/status";
import { formatDateTime } from "@/lib/utils/format";
import { medicationStockStatusOf, type Medication, type MedicationStockMovement } from "@/types";
import { StockDialog } from "@/components/product/StockDialog";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  IconButton,
  Spinner,
  StatusTag,
  TableFrame,
  TableSkeleton,
  TextField,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";

type StatusFilter = "ALL" | "LOW_STOCK" | "OUT_OF_STOCK" | "IN_STOCK";

const FILTERS: Array<{ value: StatusFilter; label: string }> = [
  { value: "ALL", label: "Tất cả" },
  { value: "LOW_STOCK", label: "Sắp hết" },
  { value: "OUT_OF_STOCK", label: "Hết hàng" },
  { value: "IN_STOCK", label: "Còn đủ" },
];

/**
 * Kho thuốc cho nhân viên và quản trị: nhập kho, điều chỉnh sau kiểm kê, đặt ngưỡng báo sắp hết và
 * tra lịch sử. Tồn tự trừ khi quầy thu tiền đơn thuốc (xem MedicationService.applyDispense) — kho thiếu
 * thì vẫn thu được, tồn về 0 và phần thiếu hiện trong lịch sử.
 *
 * Chỉ vài trăm thuốc nên lấy hết một lần rồi lọc tên/tình trạng ở giao diện, không phân trang.
 */
export function MedicationInventoryView() {
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");

  const [stockFor, setStockFor] = useState<Medication | null>(null);
  const [historyFor, setHistoryFor] = useState<Medication | null>(null);
  const [thresholdFor, setThresholdFor] = useState<Medication | null>(null);

  const medications = useQuery({
    queryKey: ["medication-inventory"],
    queryFn: () => medicationApi.inventory(),
  });

  const wanted = keyword.trim().toLowerCase();
  const rows = (medications.data ?? []).filter(
    (m) =>
      (statusFilter === "ALL" || medicationStockStatusOf(m) === statusFilter) &&
      (wanted === "" || m.name.toLowerCase().includes(wanted)),
  );

  const columns: Column<Medication>[] = [
    {
      key: "name",
      header: "Thuốc",
      cell: (m) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">{m.name}</p>
          <p className="text-xs text-bark">{m.unit || "chưa có đơn vị"}</p>
        </div>
      ),
    },
    {
      key: "stock",
      header: "Tồn hiện tại",
      numeric: true,
      cell: (m) => (
        <span className="whitespace-nowrap font-medium">
          {m.stockQuantity} {m.unit}
        </span>
      ),
    },
    {
      key: "threshold",
      header: "Ngưỡng",
      numeric: true,
      hideBelow: "lg",
      cell: (m) => <span className="text-bark">{m.lowStockThreshold}</span>,
    },
    {
      key: "status",
      header: "Tình trạng",
      cell: (m) => <StatusTag status={STOCK_STATUS[medicationStockStatusOf(m)]} />,
    },
    {
      key: "actions",
      header: "Thao tác",
      cell: (m) => (
        <div className="flex justify-end gap-1">
          <IconButton label={`Nhập kho ${m.name}`} variant="ghost" size="sm" onClick={() => setStockFor(m)}>
            <Boxes aria-hidden className="size-4" />
          </IconButton>
          <IconButton
            label={`Đặt ngưỡng sắp hết ${m.name}`}
            variant="ghost"
            size="sm"
            onClick={() => setThresholdFor(m)}
          >
            <SlidersHorizontal aria-hidden className="size-4" />
          </IconButton>
          <IconButton
            label={`Lịch sử kho ${m.name}`}
            variant="ghost"
            size="sm"
            onClick={() => setHistoryFor(m)}
          >
            <History aria-hidden className="size-4" />
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-52 flex-1">
          <label htmlFor="med-inv-kw" className="mb-1.5 block text-sm font-medium">
            Tìm theo tên
          </label>
          <input
            id="med-inv-kw"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tên thuốc"
            className={`${controlClass} h-9`}
          />
        </div>
      </div>

      <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="Lọc theo tình trạng tồn">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setStatusFilter(f.value)}
            aria-pressed={statusFilter === f.value}
            className={`rounded-[var(--radius-control)] border px-3 py-1.5 text-sm transition-colors ${
              statusFilter === f.value
                ? "border-moss bg-moss-wash font-medium text-ink"
                : "border-line-strong bg-surface text-ink-soft hover:bg-paper"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      <TableFrame title="Tồn kho thuốc" count={rows.length}>
        {medications.isLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : medications.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                medications.error instanceof ApiError ? medications.error.message : "Không tải được kho thuốc."
              }
              onRetry={() => medications.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Tồn kho theo thuốc"
            rows={rows}
            keyOf={(m) => m.id}
            columns={columns}
            empty={
              <EmptyState
                title={
                  statusFilter === "ALL" && wanted === ""
                    ? "Chưa có thuốc nào trong danh mục"
                    : "Không có thuốc nào khớp"
                }
              />
            }
          />
        )}
      </TableFrame>

      <StockDialog
        product={stockFor ? { id: stockFor.id, name: stockFor.name, unit: stockFor.unit ?? "", stockQuantity: stockFor.stockQuantity } : null}
        onClose={() => setStockFor(null)}
        adjust={medicationApi.adjustStock}
        invalidateKeys={["medication-inventory", "medication-low-stock"]}
      />
      <ThresholdDialog medication={thresholdFor} onClose={() => setThresholdFor(null)} />
      <MedicationHistoryDialog medication={historyFor} onClose={() => setHistoryFor(null)} />
    </>
  );
}

const MOVEMENT_LABEL: Record<MedicationStockMovement["type"], string> = {
  IMPORT: "Nhập kho",
  ADJUSTMENT: "Điều chỉnh",
  SALE: "Phát thuốc",
  RETURN: "Hoàn kho",
};

function MedicationHistoryDialog({
  medication,
  onClose,
}: {
  medication: Medication | null;
  onClose: () => void;
}) {
  const movements = useQuery({
    queryKey: ["stock-movements", "medication", medication?.id],
    queryFn: () => medicationApi.stockMovements(medication!.id),
    enabled: medication !== null,
  });

  return (
    <Dialog
      open={medication !== null}
      onClose={onClose}
      title="Lịch sử biến động kho thuốc"
      description={medication ? medication.name : undefined}
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      {movements.isLoading ? (
        <Spinner />
      ) : movements.isError ? (
        <ErrorState message="Không tải được lịch sử." onRetry={() => movements.refetch()} />
      ) : (movements.data ?? []).length === 0 ? (
        <p className="py-6 text-center text-sm text-bark">Thuốc này chưa có biến động nào.</p>
      ) : (
        <ol className="divide-y divide-line text-sm">
          {(movements.data ?? []).map((m) => (
            <li key={m.id} className="flex flex-wrap items-baseline gap-x-3 gap-y-1 py-2.5">
              <span className="font-medium text-ink">{MOVEMENT_LABEL[m.type]}</span>
              <span className={`tnum font-medium ${m.quantityChange > 0 ? "text-moss" : "text-danger"}`}>
                {m.quantityChange > 0 ? "+" : ""}
                {m.quantityChange}
              </span>
              <span className="tnum text-bark">còn {m.quantityAfter}</span>
              {m.shortfall > 0 && (
                <span className="tnum rounded-[var(--radius-control)] bg-danger-wash px-1.5 py-0.5 text-xs text-ink">
                  kho thiếu {m.shortfall}
                </span>
              )}
              <span className="ml-auto text-xs text-bark">{formatDateTime(m.createdAt)}</span>
              {m.note && <p className="w-full text-xs text-bark">{m.note}</p>}
            </li>
          ))}
        </ol>
      )}
    </Dialog>
  );
}

function ThresholdDialog({ medication, onClose }: { medication: Medication | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [value, setValue] = useState("0");

  useEffect(() => {
    if (medication) setValue(String(medication.lowStockThreshold));
  }, [medication]);

  const parsed = Number(value);
  const valid = value.trim() !== "" && Number.isInteger(parsed) && parsed >= 0;

  const save = useMutation({
    mutationFn: () => medicationApi.setLowStockThreshold(medication!.id, parsed),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["medication-inventory"] });
      qc.invalidateQueries({ queryKey: ["medication-low-stock"] });
      toast.success("Đã lưu ngưỡng báo sắp hết");
      onClose();
    },
  });

  return (
    <Dialog
      open={medication !== null}
      onClose={onClose}
      title="Ngưỡng báo sắp hết"
      description={medication ? medication.name : undefined}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!valid}>
            Lưu
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <TextField
          label={`Báo sắp hết khi tồn từ mức này trở xuống${medication?.unit ? ` (${medication.unit})` : ""}`}
          type="number"
          min={0}
          step={1}
          inputMode="numeric"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          hint="0 = chỉ báo khi hết hẳn."
          error={valid ? undefined : "Nhập số nguyên từ 0 trở lên"}
        />
        {save.error && (
          <ErrorState
            message={save.error instanceof ApiError ? save.error.message : "Không lưu được ngưỡng."}
          />
        )}
      </div>
    </Dialog>
  );
}

/** Ô cảnh báo thuốc sắp hết hoặc đã hết, dùng ở đầu tab Thuốc. */
export function MedicationLowStockBanner() {
  const low = useQuery({
    queryKey: ["medication-low-stock"],
    queryFn: () => medicationApi.inventory({ lowOnly: true }),
  });
  const count = low.data?.length ?? 0;
  if (low.isLoading || count === 0) return null;
  const shown = (low.data ?? []).slice(0, 4);

  return (
    <div className="mb-4 rounded-[var(--radius-control)] border border-amber/40 bg-amber-wash px-4 py-3 text-sm">
      <strong className="font-medium">{count} loại thuốc</strong> sắp hết hoặc đã hết:{" "}
      {shown.map((m) => `${m.name} (${m.stockQuantity} ${m.unit ?? ""})`.trim()).join(", ")}
      {count > 4 && `, và ${count - 4} loại khác`}
    </div>
  );
}
