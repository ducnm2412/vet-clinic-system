/**
 * Tra địa chỉ từ toạ độ (reverse geocoding) cho ô chọn vị trí trên bản đồ.
 *
 * Nominatim của OpenStreetMap là nguồn chính; lỗi hoặc không trả được thì thử Photon. Cả hai miễn
 * phí, không cần khoá, nhưng giới hạn tần suất (~1 request/giây): chỉ gọi khi khách thả ghim.
 * Dữ liệu OSM ở Việt Nam thiếu số nhà và có thể còn tên hành chính cũ, nên kết quả chỉ là gợi ý.
 */
export interface GeocodeResult {
  /** "Số nhà + tên đường"; rỗng khi OSM không có tên đường ở điểm đó. */
  line1: string;
  wardCandidates: string[];
  provinceCandidates: string[];
}

type Raw = Record<string, string | undefined>;

function compact(values: Array<string | undefined>): string[] {
  return values.filter((v): v is string => !!v && v.trim() !== "");
}

async function viaNominatim(lat: number, lng: number, signal: AbortSignal): Promise<GeocodeResult | null> {
  const url =
    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18" +
    `&accept-language=vi&lat=${lat}&lon=${lng}`;
  const res = await fetch(url, { signal });
  if (!res.ok) return null;
  const body = (await res.json()) as { address?: Raw };
  const a = body.address;
  if (!a) return null;
  return {
    line1: compact([a.house_number, a.road]).join(" "),
    wardCandidates: compact([
      a.suburb, a.quarter, a.neighbourhood, a.village, a.town,
      a.city_district, a.district, a.municipality, a.county, a.city,
    ]),
    provinceCandidates: compact([a.state, a.city, a.province, a.region, a.county]),
  };
}

async function viaPhoton(lat: number, lng: number, signal: AbortSignal): Promise<GeocodeResult | null> {
  const res = await fetch(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lng}`, { signal });
  if (!res.ok) return null;
  const body = (await res.json()) as { features?: Array<{ properties?: Raw }> };
  const p = body.features?.[0]?.properties;
  if (!p) return null;
  return {
    line1: compact([p.housenumber, p.street]).join(" "),
    wardCandidates: compact([p.district, p.locality, p.name, p.county, p.city]),
    provinceCandidates: compact([p.state, p.city, p.county]),
  };
}

/** null khi cả hai nguồn đều không trả được (mất mạng, bị chặn, hoặc điểm nằm ngoài dữ liệu). */
export async function reverseGeocode(lat: number, lng: number, signal: AbortSignal): Promise<GeocodeResult | null> {
  for (const source of [viaNominatim, viaPhoton]) {
    try {
      const result = await source(lat, lng, signal);
      if (result) return result;
    } catch (err) {
      if (signal.aborted) throw err;
    }
  }
  return null;
}
