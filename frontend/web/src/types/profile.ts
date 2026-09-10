export type PetGender = "MALE" | "FEMALE" | "UNKNOWN";

/** profile-service: PetResponse. Thú cưng hiện thuộc profile-service, không phải pet-service. */
export interface Pet {
  id: string;
  name: string;
  species: string;
  breed: string | null;
  gender: PetGender;
  dateOfBirth: string | null;
  weightKg: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface PetRequest {
  name: string;
  species: string;
  breed?: string;
  gender: PetGender;
  dateOfBirth?: string;
  weightKg?: number;
}

export interface CustomerProfile {
  id: string;
  userId: string;
  phone: string | null;
  dateOfBirth: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerProfileRequest {
  phone?: string;
  dateOfBirth?: string;
}

/**
 * profile-service: AddressResponse. Sổ địa chỉ chỉ lưu địa chỉ, không lưu người nhận —
 * người nhận nhập lại lúc đặt hàng và được đơn chụp lại tại thời điểm đó.
 */
export interface Address {
  id: string;
  line1: string;
  line2: string | null;
  ward: string | null;
  city: string | null;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AddressRequest {
  line1: string;
  line2?: string;
  ward?: string;
  city?: string;
  isDefault?: boolean;
}

export interface DoctorProfile {
  id: string;
  userId: string;
  specialty: string | null;
  phone: string | null;
  bio: string | null;
  yearsOfExperience: number | null;
  createdAt: string;
  updatedAt: string;
}

/** profile-service: DoctorPublicResponse — GET /profile/doctors, không cần đăng nhập. */
export interface DoctorPublic {
  id: string;
  userId: string;
  specialty: string | null;
  bio: string | null;
  yearsOfExperience: number | null;
}

export interface DoctorLicense {
  id: string;
  licenseNumber: string;
  issuedBy: string | null;
  issuedDate: string | null;
  expiryDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DoctorLicenseRequest {
  licenseNumber: string;
  issuedBy?: string;
  issuedDate?: string;
  expiryDate?: string;
}

export interface StaffProfile {
  id: string;
  userId: string;
  position: string | null;
  phone: string | null;
  hireDate: string | null;
  createdAt: string;
  updatedAt: string;
}
