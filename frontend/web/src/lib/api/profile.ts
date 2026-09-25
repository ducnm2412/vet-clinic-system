import { http } from "./client";
import type {
  Address,
  AddressRequest,
  CustomerProfile,
  CustomerProfileRequest,
  CustomerSummary,
  DoctorLicense,
  DoctorLicenseRequest,
  DoctorProfile,
  DoctorPublic,
  StaffProfile,
} from "@/types";

/** Hồ sơ của chính khách hàng. Backend giới hạn toàn bộ nhánh này cho role CUSTOMER. */
export const customerApi = {
  me: () => http.get<CustomerProfile>("/profile/customer/me"),
  updateMe: (body: CustomerProfileRequest) =>
    http.put<CustomerProfile>("/profile/customer/me", body),

  addresses: () => http.get<Address[]>("/profile/customer/me/addresses"),
  addAddress: (body: AddressRequest) =>
    http.post<Address>("/profile/customer/me/addresses", body),
  updateAddress: (id: string, body: AddressRequest) =>
    http.put<Address>(`/profile/customer/me/addresses/${id}`, body),
  deleteAddress: (id: string) => http.del<void>(`/profile/customer/me/addresses/${id}`),
};

/** Hồ sơ của chính bác sĩ. Backend giới hạn cho role DOCTOR. */
export const doctorApi = {
  me: () => http.get<DoctorProfile>("/profile/doctor/me"),
  updateMe: (body: Partial<DoctorProfile>) =>
    http.put<DoctorProfile>("/profile/doctor/me", body),

  licenses: () => http.get<DoctorLicense[]>("/profile/doctor/me/licenses"),
  addLicense: (body: DoctorLicenseRequest) =>
    http.post<DoctorLicense>("/profile/doctor/me/licenses", body),
  updateLicense: (id: string, body: DoctorLicenseRequest) =>
    http.put<DoctorLicense>(`/profile/doctor/me/licenses/${id}`, body),
  deleteLicense: (id: string) => http.del<void>(`/profile/doctor/me/licenses/${id}`),

  /** Công khai — trang đặt lịch hiển thị được kể cả khi chưa đăng nhập. */
  listPublic: () => http.get<DoctorPublic[]>("/profile/doctors", true),
};

/** Tra cứu dùng cho nhân viên và bác sĩ. */
export const staffApi = {
  me: () => http.get<StaffProfile>("/profile/staff/me"),
  updateMe: (body: Partial<StaffProfile>) =>
    http.put<StaffProfile>("/profile/staff/me", body),
  /** Chỉ ADMIN. Đây là danh sách nhân sự duy nhất backend cung cấp. */
  list: () => http.get<StaffProfile[]>("/profile/staff"),
};

export const customerLookupApi = {
  /**
   * Chỉ ADMIN. Điện thoại và địa chỉ của các khách đang hiện trên một trang bảng (tối đa 100).
   * Khách chưa từng mở trang hồ sơ thì không có trong kết quả. Thú cưng lấy riêng ở pet-service.
   */
  summary: (userIds: string[]) =>
    http.get<CustomerSummary[]>(`/profile/customers/summary?userIds=${userIds.map(encodeURIComponent).join(",")}`),

  /** STAFF/ADMIN tra cứu một khách cụ thể. */
  byId: (customerProfileId: string) =>
    http.get<CustomerProfile>(`/profile/customer/by-id/${customerProfileId}`),
};
