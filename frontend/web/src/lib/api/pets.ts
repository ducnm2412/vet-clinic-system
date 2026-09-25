import { http } from "./client";
import type { MedicalRecord, MedicalRecordRequest, Pet, PetRequest } from "@/types";

/**
 * pet-service (VD-10). Trước đây hồ sơ thú cưng nằm trong profile-service, nay là service riêng
 * nên đường dẫn là /pets/** chứ không còn /profile/....
 */

/** Thú cưng của chính khách. Chủ nuôi lấy từ token, không gửi lên được. */
export const myPetApi = {
  list: () => http.get<Pet[]>("/pets/me"),
  get: (id: string) => http.get<Pet>(`/pets/me/${id}`),
  add: (body: PetRequest) => http.post<Pet>("/pets/me", body),
  update: (id: string, body: PetRequest) => http.put<Pet>(`/pets/me/${id}`, body),
  remove: (id: string) => http.del<void>(`/pets/me/${id}`),
  /** CN-24: lịch sử khám của bé này, mới nhất trước. */
  history: (id: string) => http.get<MedicalRecord[]>(`/pets/me/${id}/medical-records`),
};

/** DOCTOR/STAFF/ADMIN tra cứu thú cưng của mọi khách. */
export const petLookupApi = {
  list: () => http.get<Pet[]>("/pets"),
  byId: (id: string) => http.get<Pet>(`/pets/${id}`),
  /** Thú cưng của các khách đang hiện trên một trang bảng — một lượt thay vì mỗi khách một lần. */
  byOwners: (ownerUserIds: string[]) =>
    http.get<Pet[]>(`/pets/by-owners?ownerUserIds=${ownerUserIds.map(encodeURIComponent).join(",")}`),
  /** CN-24: toàn bộ lượt khám trước đây của một con vật. */
  history: (petId: string) => http.get<MedicalRecord[]>(`/pets/${petId}/medical-records`),

  /**
   * CN-19: nhân viên lập hồ sơ thú cưng hộ khách tại quầy. Nhánh duy nhất gửi chủ nuôi lên —
   * khách tự thêm thì chủ nuôi lấy từ token.
   */
  createFor: (ownerUserId: string, pet: PetRequest) =>
    http.post<Pet>("/pets", { ownerUserId, pet }),
};

/**
 * Bệnh án và đơn thuốc. Từ 25/09/2026 thuộc pet-service, không còn nằm dưới /booking/... nữa
 * (VD-10 chặng 2) — bệnh án là hồ sơ của con vật, không phải của một lượt đặt lịch.
 */
export const medicalRecordApi = {
  /** Vừa tạo vừa sửa — backend dùng PUT cho cả hai. */
  save: (appointmentId: string, body: MedicalRecordRequest) =>
    http.put<MedicalRecord>(`/medical-records/by-appointment/${appointmentId}`, body),

  byAppointment: (appointmentId: string) =>
    http.get<MedicalRecord>(`/medical-records/by-appointment/${appointmentId}`),

  /** Đánh dấu khách đã nhận thuốc — chỉ làm được sau khi phiếu thu đã thanh toán. */
  markReceived: (appointmentId: string) =>
    http.put<MedicalRecord>(`/medical-records/by-appointment/${appointmentId}/receive`),

  /** Hàng đợi đã thanh toán, dùng chung mọi bác sĩ — chỉ STAFF/ADMIN gọi được. */
  pending: () => http.get<MedicalRecord[]>("/medical-records/pending"),

  /** "Bệnh án còn treo" của riêng bác sĩ đang đăng nhập (PENDING hoặc PAID) — chỉ DOCTOR. */
  mine: () => http.get<MedicalRecord[]>("/medical-records/mine/outstanding"),
};
