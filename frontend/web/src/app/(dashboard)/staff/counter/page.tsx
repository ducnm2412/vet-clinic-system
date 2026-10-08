"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/DashboardShell";
import { CounterInvoiceView } from "@/components/counter/CounterInvoiceView";
import { Spinner } from "@/components/ui";

/**
 * Hoá đơn gộp tại quầy. `?payment=<id>` chọn sẵn khoản khám — trang thu tiền thuốc dẫn sang đây
 * khi nhân viên muốn gộp thêm sản phẩm khách mua.
 */
function CounterBody() {
  const payment = useSearchParams().get("payment") ?? undefined;
  return <CounterInvoiceView initialPaymentId={payment} />;
}

export default function StaffCounterPage() {
  return (
    <>
      <div className="print:hidden">
        <PageHeader
          title="Hoá đơn tại quầy"
          description="Gộp tiền khám/thuốc và sản phẩm khách mua thành một hoá đơn, thu một lần."
        />
      </div>
      <Suspense fallback={<Spinner />}>
        <CounterBody />
      </Suspense>
    </>
  );
}
