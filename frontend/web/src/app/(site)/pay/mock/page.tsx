"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, orderApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { useToast } from "@/components/ui";
import type { MockPaymentSubmit } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";

/*
  Trang cổng thanh toán GIẢ LẬP — dùng để chạy thử luồng trả tiền online khi chưa có tài khoản
  merchant của cổng thật. Không trừ tiền thật. Khi có cổng thật, link thanh toán sẽ trỏ ra ngoài
  và trang này không còn được dùng.
*/

export default function MockGatewayPage() {
  return (
    <CustomerOnly>
      <Suspense fallback={<Container className="py-20">{null}</Container>}>
        <MockGatewayBody />
      </Suspense>
    </CustomerOnly>
  );
}

function MockGatewayBody() {
  const params = useSearchParams();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();

  const txnRef = params.get("txnRef");
  const orderCode = params.get("orderCode");
  const amount = params.get("amount");
  const signature = params.get("signature");
  const complete = txnRef && orderCode && amount && signature;

  const pay = useMutation({
    mutationFn: (outcome: MockPaymentSubmit["outcome"]) =>
      orderApi.submitMockPayment({
        txnRef: txnRef!,
        orderCode: orderCode!,
        amount: amount!,
        signature: signature!,
        outcome,
      }),
    onSuccess: (result, outcome) => {
      qc.invalidateQueries({ queryKey: ["orders"] });
      if (!result.orderId) {
        toast.error("Không tìm thấy giao dịch này.");
        return;
      }
      if (result.outcome === "PAID" || result.outcome === "ALREADY_PAID") {
        toast.success("Thanh toán thành công");
      } else if (result.outcome === "AMOUNT_MISMATCH") {
        toast.error("Số tiền không khớp, chưa ghi nhận thanh toán.");
      } else {
        toast.error(outcome === "CANCELLED" ? "Bạn đã huỷ thanh toán." : "Thanh toán không thành công.");
      }
      router.replace(`/orders/${result.orderId}`);
    },
    onError: (err) => {
      toast.error(
        err instanceof ApiError
          ? err.message
          : "Không hoàn tất được thanh toán. Mở lại đơn rồi bấm \"Thanh toán ngay\" để lấy link mới.",
      );
    },
  });

  if (!complete) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Link thanh toán không hợp lệ</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Link bị thiếu thông tin. Mở lại đơn của bạn và bấm &ldquo;Thanh toán ngay&rdquo; để lấy link mới.
        </p>
        <div className="mt-8">
          <ButtonLink href="/orders" variant="outline">
            Về danh sách đơn
          </ButtonLink>
        </div>
      </Container>
    );
  }

  return (
    <Container className="py-14 md:py-20">
      <div className="mx-auto max-w-md rounded-[var(--radius-card)] border border-mist p-8">
        <p className="rounded-[var(--radius-card)] bg-mint px-4 py-3 text-[15px] text-pine">
          Đây là cổng thanh toán chạy thử. Bấm nút nào cũng không trừ tiền thật.
        </p>

        <h1 className="t-h2 mt-6">Thanh toán đơn</h1>
        <dl className="mt-5 space-y-3">
          <div className="flex justify-between">
            <dt className="text-stone">Mã đơn</dt>
            <dd className="font-medium">{orderCode}</dd>
          </div>
          <div className="flex justify-between border-t border-mist pt-3">
            <dt className="text-stone">Số tiền</dt>
            <dd className="tnum text-[21px] font-semibold">{formatPrice(Number(amount))}</dd>
          </div>
        </dl>

        <div className="mt-8 space-y-3">
          <SiteButton
            size="lg"
            className="w-full"
            loading={pay.isPending && pay.variables === "SUCCESS"}
            disabled={pay.isPending}
            onClick={() => pay.mutate("SUCCESS")}
          >
            Thanh toán thành công
          </SiteButton>
          <SiteButton
            variant="outline"
            className="w-full"
            loading={pay.isPending && pay.variables === "FAILED"}
            disabled={pay.isPending}
            onClick={() => pay.mutate("FAILED")}
          >
            Giả lập thanh toán thất bại
          </SiteButton>
          <SiteButton
            variant="outline"
            className="w-full"
            loading={pay.isPending && pay.variables === "CANCELLED"}
            disabled={pay.isPending}
            onClick={() => pay.mutate("CANCELLED")}
          >
            Huỷ thanh toán
          </SiteButton>
        </div>
      </div>
    </Container>
  );
}
