"use client";

import { useState } from "react";
import { InventoryView, LowStockBanner } from "./InventoryView";
import { MedicationInventoryView, MedicationLowStockBanner } from "./MedicationInventoryView";

type Tab = "PRODUCTS" | "MEDICATIONS";

const TABS: Array<{ value: Tab; label: string }> = [
  { value: "PRODUCTS", label: "Sản phẩm" },
  { value: "MEDICATIONS", label: "Thuốc" },
];

/** Màn tồn kho chung cho quản trị và nhân viên: hàng bán ở cửa hàng và thuốc kê đơn, hai kho riêng. */
export function InventoryTabs() {
  const [tab, setTab] = useState<Tab>("PRODUCTS");

  return (
    <>
      <div role="tablist" aria-label="Loại kho" className="mb-4 flex gap-1 border-b border-line">
        {TABS.map((t) => (
          <button
            key={t.value}
            role="tab"
            aria-selected={tab === t.value}
            onClick={() => setTab(t.value)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm transition-colors ${
              tab === t.value
                ? "border-moss font-medium text-ink"
                : "border-transparent text-bark hover:text-ink"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "PRODUCTS" ? (
        <>
          <LowStockBanner />
          <InventoryView />
        </>
      ) : (
        <>
          <MedicationLowStockBanner />
          <MedicationInventoryView />
        </>
      )}
    </>
  );
}
