package com.vetclinic.order.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Bọc Page của Spring lại để response không lộ cấu trúc nội bộ của Spring Data. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
