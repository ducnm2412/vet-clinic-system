"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useRef, useState } from "react";
import { LocateFixed } from "lucide-react";
import type { Map as LeafletMap, Marker } from "leaflet";
import { SiteButton } from "@/components/site/primitives";

// Trung tâm TP.HCM — chỉ là điểm bắt đầu nhìn bản đồ, không phải vị trí của khách.
const START_CENTER: [number, number] = [10.7769, 106.7009];

const PIN_HTML =
  '<svg viewBox="0 0 32 40" width="32" height="40" aria-hidden="true">' +
  '<path d="M16 39C16 39 3 24 3 15a13 13 0 0 1 26 0c0 9-13 24-13 24z" style="fill:var(--color-teal)" stroke="#fff" stroke-width="2"/>' +
  '<circle cx="16" cy="15" r="5" fill="#fff"/></svg>';

/**
 * Bản đồ OpenStreetMap để khách bấm hoặc kéo ghim vào đúng chỗ nhà mình. Chỉ báo toạ độ lên qua
 * `onPick`; việc đổi toạ độ thành địa chỉ do nơi dùng quyết định.
 *
 * Leaflet đụng `window` ngay khi nạp, nên chỉ import bên trong effect (chạy ở trình duyệt) chứ không
 * import đầu file — nếu không, bước dựng trang phía server sẽ lỗi.
 */
export function MapPinPicker({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  const box = useRef<HTMLDivElement>(null);
  const place = useRef<((lat: number, lng: number, zoomIn: boolean) => void) | null>(null);
  const pick = useRef(onPick);
  const [notice, setNotice] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    pick.current = onPick;
  });

  useEffect(() => {
    let disposed = false;
    let map: LeafletMap | null = null;

    (async () => {
      const mod = await import("leaflet");
      const L = ((mod as unknown as { default?: typeof import("leaflet") }).default ?? mod) as typeof import("leaflet");
      if (disposed || !box.current) return;

      map = L.map(box.current, { center: START_CENTER, zoom: 12 });
      const tiles = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }).addTo(map);
      tiles.once("tileerror", () =>
        setNotice("Không tải được bản đồ — kiểm tra kết nối mạng, hoặc chọn Tỉnh/Phường bằng ô phía trên."),
      );

      const icon = L.divIcon({ className: "", html: PIN_HTML, iconSize: [32, 40], iconAnchor: [16, 40] });
      let marker: Marker | null = null;
      const drop = (lat: number, lng: number, zoomIn: boolean) => {
        if (!map) return;
        if (marker) {
          marker.setLatLng([lat, lng]);
        } else {
          marker = L.marker([lat, lng], { icon, draggable: true }).addTo(map);
          marker.on("dragend", () => {
            const p = marker!.getLatLng();
            pick.current(p.lat, p.lng);
          });
        }
        if (zoomIn) map.setView([lat, lng], Math.max(map.getZoom(), 17));
        pick.current(lat, lng);
      };

      map.on("click", (e) => drop(e.latlng.lat, e.latlng.lng, false));
      place.current = drop;
      // Hộp thoại vừa mở nên khung bản đồ có thể chưa có kích thước thật lúc khởi tạo.
      setTimeout(() => map?.invalidateSize(), 0);
    })();

    return () => {
      disposed = true;
      place.current = null;
      map?.remove();
      map = null;
    };
  }, []);

  function locate() {
    if (!navigator.geolocation) {
      setNotice("Trình duyệt này không hỗ trợ định vị.");
      return;
    }
    setNotice(null);
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        place.current?.(pos.coords.latitude, pos.coords.longitude, true);
      },
      () => {
        setLocating(false);
        setNotice("Không lấy được vị trí của bạn — hãy cho phép định vị hoặc bấm thẳng lên bản đồ.");
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  return (
    <div className="space-y-2">
      <div
        ref={box}
        className="isolate h-64 w-full overflow-hidden rounded-xl border border-mist bg-mint sm:h-72"
      />
      <div className="flex flex-wrap items-center gap-3">
        <SiteButton variant="outline" size="sm" type="button" loading={locating} onClick={locate}>
          <LocateFixed aria-hidden className="size-4" />
          Dùng vị trí của tôi
        </SiteButton>
        <p className="text-sm text-stone">Bấm lên bản đồ hoặc kéo ghim đến đúng nhà bạn.</p>
      </div>
      {notice && <p className="text-sm text-coral-deep">{notice}</p>}
    </div>
  );
}
