"use client";

import { useEffect, useState } from "react";
import { http } from "@/lib/api";

/**
 * Ảnh cần đăng nhập mới xem được (thú cưng, khách hàng). Thẻ img không tự gửi Bearer token
 * nên phải tải bằng http.getBlob — dùng lại cơ chế token và làm mới phiên của các request khác —
 * rồi đưa blob URL cho img. Trả về null khi chưa có ảnh, đang tải, hoặc tải lỗi để chỗ gọi rơi về
 * hình thay thế.
 *
 * `path` có kèm ?v=<phiên bản> nên đổi ảnh là đổi path, tự tải lại.
 */
export function useAuthedImage(path: string | null | undefined): string | null {
  const [loaded, setLoaded] = useState<{ path: string; src: string } | null>(null);

  useEffect(() => {
    if (!path) return;

    let cancelled = false;
    let objectUrl: string | null = null;

    http
      .getBlob(path)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setLoaded({ path, src: objectUrl });
      })
      .catch(() => {
        if (!cancelled) setLoaded(null);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path]);

  // Path đã đổi mà ảnh mới chưa về thì coi như chưa có, tránh hiện nhầm ảnh cũ.
  return path && loaded?.path === path ? loaded.src : null;
}
