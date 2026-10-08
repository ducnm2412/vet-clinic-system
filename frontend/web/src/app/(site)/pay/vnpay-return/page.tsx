"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleCheck, CircleX } from "lucide-react";
import { ApiError, orderApi } from "@/lib/api";
import { CLINIC } from "@/config/clinic";
import { ButtonLink, Container } from "@/components/site/primitives";

/*
  Trang VNPAY đưa khách về sau khi trả tiền (hoặc huỷ). Trang không tự quyết kết quả: nó gửi nguyên các tham
  số vnp_* (có chữ ký) lên backend, backend kiểm chữ ký rồi ghi nhận và trả kết quả. Nên trang này không đặt
  sau lớp đăng nhập — kết quả không phụ thuộc phiên đăng nhập, chỉ nút "Xem đơn hàng" mới cần.
*/

export default function VnpayReturnPage() {
  return (
    <Suspense fallback={<Container className="py-20">{null}</Container>}>
      <VnpayReturnBody />
    </Suspense>
  );
}

/** Lý do VNPAY báo khi giao dịch không thành công. Chỉ để giải thích cho khách, không dùng để quyết định gì. */
function failureReason(code: string | null): string {
  switch (code) {
    case "24":
      return "Bạn đã huỷ thanh toán.";
    case "11":
      return "Đã hết thời gian thanh toán.";
    case "51":
      return "Tài khoản không đủ số dư.";
    case "65":
      return "Tài khoản đã vượt hạn mức giao dịch trong ngày.";
    case "75":
      return "Ngân hàng đang bảo trì.";
    case "10":
    case "13":
      return "Xác thực không thành công.";
    default:
      return "Giao dịch không thành công.";
  }
}

function VnpayReturnBody() {
  const search = useSearchParams();
  const qc = useQueryClient();

  const params: Record<string, string> = {};
  search.forEach((value, key) => {
    if (key.startsWith("vnp_")) params[key] = value;
  });
  const hasResult = "vnp_SecureHash" in params && "vnp_TxnRef" in params;

  const result = useQuery({
    // Mỗi bộ tham số chỉ xử lý một lần dù trang render lại; backend cũng không trùng lặp nếu có gọi hai lần.
    queryKey: ["vnpay-return", JSON.stringify(params)],
    queryFn: async () => {
      const data = await orderApi.vnpayReturn(params);
      qc.invalidateQueries({ queryKey: ["orders"] });
      return data;
    },
    enabled: hasResult,
    retry: false,
    staleTime: Infinity,
  });

  if (!hasResult) {
    return (
      <Notice
        ok={false}
        title="Không có kết quả thanh toán"
        body="Trang này chỉ mở được sau khi bạn thanh toán trên VNPAY. Mở đơn của bạn để xem tình trạng."
        orderId={null}
      />
    );
  }

  if (result.isLoading) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Đang xác nhận thanh toán…</h1>
        <p className="mt-4 text-stone">Vui lòng chờ trong giây lát, đừng đóng trang.</p>
      </Container>
    );
  }

  if (result.isError || !result.data) {
    const detail =
      result.error instanceof ApiError && result.error.status === 400
        ? "Không xác minh được kết quả này."
        : "Chưa xác nhận được kết quả.";
    return (
      <Notice
        ok={false}
        title="Chưa xác nhận được thanh toán"
        body={`${detail} Nếu tiền đã bị trừ, đừng lo: hệ thống sẽ tự cập nhật đơn khi VNPAY báo về. Gọi ${CLINIC.phone} nếu đơn vẫn chưa được ghi nhận sau ít phút.`}
        orderId={null}
      />
    );
  }

  const { outcome, orderId, orderCode } = result.data;

  if (outcome === "PAID" || outcome === "ALREADY_PAID") {
    return (
      <Notice
        ok
        title="Thanh toán thành công"
        body={`Phòng khám đã nhận được tiền cho đơn ${orderCode ?? ""}. Phòng khám sẽ xác nhận và giao hàng cho bạn.`}
        orderId={orderId}
      />
    );
  }

  if (outcome === "FAILED") {
    return (
      <Notice
        ok={false}
        title="Chưa thanh toán"
        body={`${failureReason(search.get("vnp_ResponseCode"))} Đơn ${orderCode ?? ""} vẫn được giữ, bạn thanh toán lại ở trang đơn trước khi hết hạn.`}
        orderId={orderId}
      />
    );
  }

  if (outcome === "AMOUNT_MISMATCH") {
    return (
      <Notice
        ok={false}
        title="Số tiền không khớp"
        body={`Số tiền VNPAY ghi nhận khác với đơn nên chưa được tính là đã thanh toán. Gọi ${CLINIC.phone} để phòng khám kiểm tra giúp bạn.`}
        orderId={orderId}
      />
    );
  }

  return (
    <Notice
      ok={false}
      title="Không tìm thấy giao dịch"
      body="Link thanh toán này có thể đã được thay bằng link mới. Mở đơn của bạn và bấm “Thanh toán ngay” để lấy link mới."
      orderId={null}
    />
  );
}

function Notice({
  ok,
  title,
  body,
  orderId,
}: {
  ok: boolean;
  title: string;
  body: string;
  orderId: string | null;
}) {
  const Icon = ok ? CircleCheck : CircleX;
  return (
    <Container className="py-20 text-center md:py-28">
      <Icon aria-hidden className={ok ? "mx-auto size-16 text-teal" : "mx-auto size-16 text-coral-deep"} />
      <h1 className="t-h2 mt-8">{title}</h1>
      <p className="measure mx-auto mt-4 text-stone">{body}</p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <ButtonLink href={orderId ? `/orders/${orderId}` : "/orders"}>
          {orderId ? "Xem đơn hàng" : "Đơn hàng của bạn"}
        </ButtonLink>
        <ButtonLink href="/products" variant="outline">
          Tiếp tục mua sắm
        </ButtonLink>
      </div>
    </Container>
  );
}
