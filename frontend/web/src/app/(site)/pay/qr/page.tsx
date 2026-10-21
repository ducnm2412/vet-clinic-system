"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { orderApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { useToast } from "@/components/ui";
import { ButtonLink, Container } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { TransferQr } from "@/components/payment/TransferQr";

/*
  Trang chuyển khoản QR: hiện mã QR (đã điền sẵn số tiền và mã đơn) để khách quét bằng app ngân hàng/ví.
  Hệ thống không tự biết tiền đã vào; phòng khám đối chiếu rồi nhân viên xác nhận. Trang hỏi lại đơn mỗi
  vài giây và tự chuyển sang trang đơn khi đơn đã được ghi nhận thanh toán.
*/

export default function QrPaymentPage() {
  return (
    <CustomerOnly>
      <Suspense fallback={<Container className="py-20">{null}</Container>}>
        <QrPaymentBody />
      </Suspense>
    </CustomerOnly>
  );
}

const POLL_MS = 4000;

/** Giây còn lại tới hạn; null khi không có hạn. */
function useSecondsLeft(iso: string | null | undefined): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!iso) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [iso]);
  if (!iso) return null;
  return Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
}

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function QrPaymentBody() {
  const params = useSearchParams();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const orderId = params.get("orderId");

  const order = useQuery({
    queryKey: ["orders", "qr", orderId],
    queryFn: () => orderApi.byId(orderId!),
    enabled: orderId !== null,
    // Còn chờ thanh toán thì hỏi lại để biết lúc nhân viên xác nhận; xong hoặc huỷ thì dừng.
    refetchInterval: (q) =>
      q.state.data && q.state.data.paymentStatus === "UNPAID" && q.state.data.status === "PENDING"
        ? POLL_MS
        : false,
  });

  const data = order.data;
  const left = useSecondsLeft(data?.paymentExpiresAt);
  const expired = left !== null && left <= 0;

  const paid = data?.paymentStatus === "PAID";
  useEffect(() => {
    if (!paid || !data) return;
    qc.invalidateQueries({ queryKey: ["orders"] });
    toast.success("Phòng khám đã nhận được tiền");
    router.replace(`/orders/${data.id}`);
  }, [paid, data, qc, router, toast]);

  if (!orderId || order.isError || (order.isSuccess && data?.paymentMethod !== "ONLINE")) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Không mở được trang thanh toán</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Link bị thiếu hoặc đơn không tồn tại. Mở lại đơn của bạn và bấm &ldquo;Thanh toán ngay&rdquo;.
        </p>
        <div className="mt-8">
          <ButtonLink href="/orders" variant="outline">
            Về danh sách đơn
          </ButtonLink>
        </div>
      </Container>
    );
  }

  if (!data) {
    return <Container className="py-20 text-center text-stone">Đang tải…</Container>;
  }

  const cancelled = data.status === "CANCELLED";

  return (
    <Container className="py-14 md:py-20">
      <div className="mx-auto max-w-md rounded-[var(--radius-card)] border border-mist p-8">
        <h1 className="t-h2">Chuyển khoản qua mã QR</h1>
        <dl className="mt-5 space-y-3">
          <div className="flex justify-between">
            <dt className="text-stone">Mã đơn</dt>
            <dd className="font-medium">{data.orderCode}</dd>
          </div>
          <div className="flex justify-between border-t border-mist pt-3">
            <dt className="text-stone">Số tiền</dt>
            <dd className="tnum text-[21px] font-semibold">{formatPrice(data.total)}</dd>
          </div>
        </dl>

        {cancelled || expired ? (
          <p className="mt-6 rounded-[var(--radius-card)] bg-mint px-4 py-3 text-[15px] text-coral-deep">
            {cancelled
              ? "Đơn đã bị huỷ nên không thanh toán được nữa."
              : "Đơn đã hết hạn thanh toán và sẽ được huỷ tự động trong ít phút."}
          </p>
        ) : (
          <>
            <TransferQr amount={Math.round(Number(data.total))} content={data.orderCode} large />
            <p className="mt-4 text-[15px] text-stone">
              Mở app ngân hàng hoặc ví, quét mã và chuyển <strong>đúng số tiền</strong>, giữ nguyên nội dung.
              Phòng khám xác nhận sau khi nhận được tiền, thường trong ít phút; trang này sẽ tự cập nhật.
              {left !== null && (
                <>
                  {" "}Còn <span className="tnum font-medium text-pine">{formatCountdown(left)}</span> để
                  thanh toán, quá hạn đơn sẽ tự huỷ.
                </>
              )}
            </p>
          </>
        )}

        <div className="mt-8">
          <ButtonLink href={`/orders/${data.id}`} variant="outline" className="w-full">
            Xem đơn hàng
          </ButtonLink>
        </div>
      </div>
    </Container>
  );
}
