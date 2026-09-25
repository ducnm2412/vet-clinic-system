import { http } from "./client";
import type { Pet, PetRequest } from "@/types";

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
};

/** DOCTOR/STAFF/ADMIN tra cứu thú cưng của mọi khách. */
export const petLookupApi = {
  list: () => http.get<Pet[]>("/pets"),
  byId: (id: string) => http.get<Pet>(`/pets/${id}`),
  /** Thú cưng của các khách đang hiện trên một trang bảng — một lượt thay vì mỗi khách một lần. */
  byOwners: (ownerUserIds: string[]) =>
    http.get<Pet[]>(`/pets/by-owners?ownerUserIds=${ownerUserIds.map(encodeURIComponent).join(",")}`),
};
