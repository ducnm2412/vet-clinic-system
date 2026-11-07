import type { CustomerGender } from "@/types";

/**
 * Ảnh đại diện mặc định của khách chưa tải ảnh lên, vẽ theo giới tính: nam tóc ngắn, nữ tóc dài,
 * còn lại (khác, chưa khai) là hình người trung tính. Tự điền kín khung tròn của chỗ đặt nó.
 */
export function DefaultAvatar({
  gender,
  className,
}: {
  gender: CustomerGender | null | undefined;
  className?: string;
}) {
  return (
    <svg
      viewBox="0 0 96 96"
      role="img"
      aria-label="Ảnh đại diện mặc định"
      className={className}
      preserveAspectRatio="xMidYMid slice"
    >
      {gender === "FEMALE" && (
        <path
          className="fill-pine"
          d="M29 44c0-14 8-24 19-24s19 10 19 24v24c0 5-4 8-9 8H38c-5 0-9-3-9-8z"
        />
      )}
      <path className="fill-teal" d="M14 96c0-20 15-32 34-32s34 12 34 32z" />
      <circle className="fill-white" cx="48" cy="41" r="16" />
      {gender === "MALE" && (
        <path
          className="fill-pine"
          d="M31 41c-1-13 7-22 17-22s18 9 17 22c-3-7-8-10-17-10s-14 3-17 10z"
        />
      )}
      {gender === "FEMALE" && (
        <path
          className="fill-pine"
          d="M31 43c-1-14 7-24 17-24s18 10 17 24c-6-4-9-9-11-13-5 6-12 11-23 13z"
        />
      )}
    </svg>
  );
}
