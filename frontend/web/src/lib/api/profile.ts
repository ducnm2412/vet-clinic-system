import { http } from "./client";
import type {
  Address,
  AddressRequest,
  CustomerProfile,
  CustomerProfileRequest,
  DoctorLicense,
  DoctorLicenseRequest,
  DoctorProfile,
  DoctorPublic,
  Pet,
  PetRequest,
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

  pets: () => http.get<Pet[]>("/profile/customer/me/pets"),
  pet: (id: string) => http.get<Pet>(`/profile/customer/me/pets/${id}`),
  addPet: (body: PetRequest) => http.post<Pet>("/profile/customer/me/pets", body),
  updatePet: (id: string, body: PetRequest) =>
    http.put<Pet>(`/profile/customer/me/pets/${id}`, body),
  deletePet: (id: string) => http.del<void>(`/profile/customer/me/pets/${id}`),
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

export const petLookupApi = {
  /** DOCTOR/STAFF/ADMIN tra cứu thú cưng của mọi khách. */
  list: () => http.get<Pet[]>("/profile/pets"),
  byId: (id: string) => http.get<Pet>(`/profile/pets/${id}`),
};

export const customerLookupApi = {
  /** STAFF/ADMIN tra cứu một khách cụ thể. Không có endpoint liệt kê — xem VD-18. */
  byId: (customerProfileId: string) =>
    http.get<CustomerProfile>(`/profile/customer/by-id/${customerProfileId}`),
};
