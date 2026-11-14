package com.vetclinic.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Quầy chốt số tiền: tiền dịch vụ + từng dòng thuốc (đơn giá, số lượng thực tế phát ra). */
public record SetItemsRequest(
        // Có thể rỗng: lượt khám không kê thuốc thì phiếu chỉ có tiền dịch vụ.
        @NotNull(message = "Thiếu danh sách dòng thuốc")
        @Valid
        List<Line> items,

        // Tiền dịch vụ quầy xác nhận (giá tham khảo sửa được). Null = giữ nguyên số đang có trên phiếu.
        @DecimalMin(value = "0", message = "Tiền dịch vụ không được âm")
        @DecimalMax(value = "9999999999", message = "Tiền dịch vụ quá lớn")
        BigDecimal serviceAmount,

        // Tên dịch vụ để ghi vào phiếu khi phiếu cũ chưa có; null = giữ nguyên.
        @Size(max = 120, message = "Tên dịch vụ tối đa 120 ký tự")
        String serviceName
) {
    public record Line(
            @NotNull(message = "Thiếu mã dòng thuốc")
            UUID itemId,

            @NotNull(message = "Nhập đơn giá")
            @DecimalMin(value = "0", message = "Đơn giá không được âm")
            @DecimalMax(value = "9999999999", message = "Đơn giá quá lớn")
            BigDecimal unitPrice,

            @NotNull(message = "Nhập số lượng")
            @Min(value = 1, message = "Số lượng tối thiểu là 1")
            @Max(value = 100000, message = "Số lượng quá lớn")
            Integer quantity
    ) {
    }
}
