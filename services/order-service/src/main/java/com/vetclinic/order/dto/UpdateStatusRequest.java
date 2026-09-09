package com.vetclinic.order.dto;

import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(
        @Size(max = 500) String note
) {
}
