package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.Address;
import com.vetclinic.profile.domain.CustomerProfile;
import com.vetclinic.profile.dto.AddressRequest;
import com.vetclinic.profile.dto.AddressResponse;
import com.vetclinic.profile.dto.CustomerProfileRequest;
import com.vetclinic.profile.dto.CustomerProfileResponse;
import com.vetclinic.profile.exception.ResourceNotFoundException;
import com.vetclinic.profile.repository.AddressRepository;
import com.vetclinic.profile.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final AddressRepository addressRepository;

    @Transactional
    public CustomerProfileResponse getMyProfile(UUID userId) {
        return toProfileResponse(getOrCreateProfile(userId));
    }

    // Gọi từ UserDeletedListener khi auth-service báo user đã bị xoá — dọn dữ liệu mồ côi.
    // Addresses tự xoá theo qua FK ON DELETE CASCADE trong DB. Thú cưng giờ nằm ở pet-service,
    // service đó tự nghe user.deleted và tự dọn.
    @Transactional
    public void deleteByUserId(UUID userId) {
        customerProfileRepository.findByUserId(userId).ifPresent(customerProfileRepository::delete);
    }

    // Dùng cho Staff/Admin tra cứu 1 khách hàng cụ thể — KHÔNG lazy-create như getMyProfile,
    // vì id do người gọi tự cung cấp; nếu không tồn tại phải báo lỗi rõ ràng, không tự tạo hộ.
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfileById(UUID customerProfileId) {
        CustomerProfile profile = customerProfileRepository.findById(customerProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found: " + customerProfileId));
        return toProfileResponse(profile);
    }

    @Transactional
    public CustomerProfileResponse updateMyProfile(UUID userId, CustomerProfileRequest request) {
        CustomerProfile profile = getOrCreateProfile(userId);
        profile.setPhone(request.phone());
        profile.setDateOfBirth(request.dateOfBirth());
        // flush ngay để @UpdateTimestamp được Hibernate điền vào object trước khi map ra response.
        customerProfileRepository.saveAndFlush(profile);
        return toProfileResponse(profile);
    }

    // KHÔNG readOnly: getOrCreateProfile có thể phải INSERT profile mới cho customer lần đầu gọi.
    @Transactional
    public List<AddressResponse> listAddresses(UUID userId) {
        CustomerProfile profile = getOrCreateProfile(userId);
        return addressRepository.findByCustomerProfileId(profile.getId()).stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest request) {
        CustomerProfile profile = getOrCreateProfile(userId);

        if (request.isDefault()) {
            clearOtherDefaultAddresses(profile.getId(), null);
        }

        Address address = Address.builder()
                .customerProfile(profile)
                .line1(request.line1())
                .line2(request.line2())
                .ward(request.ward())
                .city(request.city())
                .isDefault(request.isDefault())
                .build();

        addressRepository.saveAndFlush(address);
        return toAddressResponse(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        CustomerProfile profile = getOrCreateProfile(userId);
        Address address = addressRepository.findByIdAndCustomerProfileId(addressId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));

        if (request.isDefault()) {
            clearOtherDefaultAddresses(profile.getId(), addressId);
        }

        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setWard(request.ward());
        address.setCity(request.city());
        address.setDefault(request.isDefault());

        addressRepository.saveAndFlush(address);
        return toAddressResponse(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        CustomerProfile profile = getOrCreateProfile(userId);
        Address address = addressRepository.findByIdAndCustomerProfileId(addressId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
        addressRepository.delete(address);
    }

    /**
     * CN-19: hồ sơ cho khách vừa được mở tài khoản tại quầy, kèm số điện thoại nhân viên nhập.
     *
     * Không ghi đè nếu hồ sơ đã có số điện thoại: tài khoản trùng email là chuyện của auth-service
     * chặn, còn ở đây nếu vì lý do nào đó gọi lại thì dữ liệu khách tự khai vẫn được giữ.
     */
    @Transactional
    public void ensureProfileWithPhone(UUID userId, String phone) {
        CustomerProfile profile = getOrCreateProfile(userId);
        if (phone != null && !phone.isBlank() && (profile.getPhone() == null || profile.getPhone().isBlank())) {
            profile.setPhone(phone.trim());
            customerProfileRepository.saveAndFlush(profile);
        }
    }

    private CustomerProfile getOrCreateProfile(UUID userId) {
        return customerProfileRepository.findByUserId(userId)
                .orElseGet(() -> customerProfileRepository.saveAndFlush(
                        CustomerProfile.builder().userId(userId).build()));
    }

    // excludeAddressId null khi tạo mới (chưa có id); khi update thì loại chính địa chỉ đang sửa.
    private void clearOtherDefaultAddresses(UUID customerProfileId, UUID excludeAddressId) {
        List<Address> toUnset = addressRepository.findByCustomerProfileId(customerProfileId).stream()
                .filter(Address::isDefault)
                .filter(a -> excludeAddressId == null || !a.getId().equals(excludeAddressId))
                .toList();

        if (!toUnset.isEmpty()) {
            toUnset.forEach(a -> a.setDefault(false));
            addressRepository.saveAll(toUnset);
        }
    }

    private CustomerProfileResponse toProfileResponse(CustomerProfile profile) {
        return new CustomerProfileResponse(profile.getId(), profile.getUserId(), profile.getPhone(),
                profile.getDateOfBirth(), profile.getCreatedAt(), profile.getUpdatedAt());
    }

    private AddressResponse toAddressResponse(Address address) {
        return new AddressResponse(address.getId(), address.getLine1(), address.getLine2(), address.getWard(),
                address.getCity(), address.isDefault(), address.getCreatedAt(), address.getUpdatedAt());
    }

}
