package com.vetclinic.staff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ghi ngày nghỉ: admin nhập tay khi có người báo nghỉ. */
public final class Leaves {

    private Leaves() {
    }

    /** {@code dates}: nhiều ngày một lượt, như xếp ca. */
    public record CreateRequest(
            @NotNull UUID userId,
            @NotNull @Size(min = 1, max = 31) List<@NotNull LocalDate> dates,
            @Size(max = 500) String reason
    ) {
    }

    public record Response(UUID id, UUID userId, LocalDate date, String reason) {
    }

    /**
     * {@code created}: ngày nghỉ vừa ghi; {@code skipped}: ngày đã ghi nghỉ từ trước;
     * {@code removedShifts}: số ca đã xếp trong các ngày đó bị gỡ.
     */
    public record CreateResult(List<Response> created, List<LocalDate> skipped, int removedShifts) {
    }
}
