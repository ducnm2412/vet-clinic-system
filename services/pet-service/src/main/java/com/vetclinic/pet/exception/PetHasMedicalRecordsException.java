package com.vetclinic.pet.exception;

/**
 * Thú cưng đã có bệnh án thì không xoá được hồ sơ — trả 409.
 *
 * Bệnh án là hồ sơ lâm sàng của phòng khám, không phải dữ liệu riêng của khách muốn xoá là xoá.
 * Trước đây bệnh án nằm ở booking_db nên không có gì chặn: xoá thú cưng để lại bệnh án mồ côi
 * không ai tra ra được nữa.
 */
public class PetHasMedicalRecordsException extends RuntimeException {

    public PetHasMedicalRecordsException(String message) {
        super(message);
    }
}
