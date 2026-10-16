"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/DashboardShell";
import { CounterInvoiceView } from "@/components/counter/CounterInvoiceView";
import { Spinner } from "@/components/ui";

/**
 * Hoá đơn tại quầy. `?payment=<id>` mở hoá đơn chờ của khoản khám (thêm sản phẩm rồi thu).
 * Không có tham số thì lập hoá đơn khách lẻ như trước.
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
          description="Mở hoá đơn chờ của khoản khám để thêm sản phẩm rồi thu, hoặc lập hoá đơn khách lẻ."
        />
      </div>
      <Suspense fallback={<Spinner />}>
        <CounterBody />
      </Suspense>
    </>
  );
}
