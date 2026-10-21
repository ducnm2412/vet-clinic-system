"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { formatPrice } from "@/lib/utils/format";
import { buildTransferQrPayload, sanitizeTransferContent } from "@/lib/utils/vietqr";

// NEXT_PUBLIC_* được Next dựng thẳng vào bundle lúc build (xem frontend/Dockerfile).
const TEMPLATE = process.env.NEXT_PUBLIC_TRANSFER_QR_TEMPLATE ?? "";
const ACCOUNT_NAME = process.env.NEXT_PUBLIC_TRANSFER_ACCOUNT_NAME ?? "";
const ACCOUNT_NUMBER = process.env.NEXT_PUBLIC_TRANSFER_ACCOUNT_NUMBER ?? "";

/**
 * Mã QR chuyển khoản cho khách quét bằng app ngân hàng/ví: đã điền sẵn số tiền và nội dung.
 * Không cấu hình tài khoản nhận (NEXT_PUBLIC_TRANSFER_QR_TEMPLATE) thì không hiện gì, thu ngân
 * vẫn đối chiếu thủ công như trước.
 */
export function TransferQr({
  amount,
  content,
  large = false,
}: {
  amount: number;
  content: string;
  /** Trang khách quét bằng điện thoại: vẽ mã to hơn so với khung thu ngân. */
  large?: boolean;
}) {
  const [svg, setSvg] = useState<string | null>(null);
  const payload = TEMPLATE ? buildTransferQrPayload(TEMPLATE, amount, content) : null;

  useEffect(() => {
    if (!payload) return;
    let cancelled = false;
    QRCode.toString(payload, { type: "svg", margin: 1, errorCorrectionLevel: "M" })
      .then((s) => {
        if (!cancelled) setSvg(s);
      })
      .catch(() => {
        if (!cancelled) setSvg(null);
      });
    return () => {
      cancelled = true;
    };
  }, [payload]);

  if (!TEMPLATE) return null;

  return (
    <div className="mt-4 rounded-[var(--radius-control)] border border-line bg-paper p-4 text-center">
      {payload && svg ? (
        // SVG do thư viện qrcode tự sinh từ dữ liệu của mình, không có nội dung từ người dùng.
        <div
          role="img"
          aria-label="Mã QR chuyển khoản"
          className={`mx-auto rounded-[var(--radius-control)] bg-white [&>svg]:size-full ${large ? "size-60" : "size-44"}`}
          dangerouslySetInnerHTML={{ __html: svg }}
        />
      ) : (
        <p className="text-xs text-bark">
          {payload ? "Đang tạo mã QR…" : "Chưa có số tiền để tạo mã QR."}
        </p>
      )}
      <dl className="mt-3 space-y-0.5 text-left text-xs text-bark">
        {ACCOUNT_NAME && (
          <div className="flex justify-between gap-3">
            <dt>Người nhận</dt>
            <dd className="text-right font-medium text-ink">{ACCOUNT_NAME}</dd>
          </div>
        )}
        {ACCOUNT_NUMBER && (
          <div className="flex justify-between gap-3">
            <dt>Tài khoản</dt>
            <dd className="tnum text-right font-medium text-ink">{ACCOUNT_NUMBER}</dd>
          </div>
        )}
        <div className="flex justify-between gap-3">
          <dt>Số tiền</dt>
          <dd className="tnum text-right font-medium text-ink">{formatPrice(amount)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt>Nội dung</dt>
          <dd className="text-right font-medium text-ink">{sanitizeTransferContent(content)}</dd>
        </div>
      </dl>
    </div>
  );
}
