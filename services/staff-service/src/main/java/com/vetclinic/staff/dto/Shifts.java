package com.vetclinic.staff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** CN-39: xếp ca trực. */
public final class Shifts {

    private Shifts() {
    }

    /**
     * Xếp một ca. {@code dates} cho phép xếp nhiều ngày một lượt — admin xếp cả tuần trong một
     * lần bấm thay vì bảy lần.
     */
    public record CreateRequest(
            @NotNull UUID userId,
            // Ngày gửi dạng "2026-09-28"; Jackson tự đọc ISO nên không cần @DateTimeFormat ở đây
            // (annotation đó dành cho tham số query, và không đặt được vào tham số kiểu generic).
            @NotNull @Size(min = 1, max = 31) List<@NotNull LocalDate> dates,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 500) String note
    ) {
    }

    public record Response(UUID id, UUID userId, LocalDate date, LocalTime startTime, LocalTime endTime, String note) {
    }

    /** Kết quả xếp nhiều ngày: ngày nào vừa xếp, ngày nào đã có ca giống hệt từ trước. */
    public record CreateResult(List<Response> created, List<LocalDate> skipped) {
    }
}
