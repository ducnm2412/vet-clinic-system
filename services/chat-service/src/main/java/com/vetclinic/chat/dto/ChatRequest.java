package com.vetclinic.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @NotEmpty(message = "Cần ít nhất một tin nhắn") @Size(max = 60, message = "Quá nhiều tin nhắn")
        List<@Valid ChatMessage> messages
) {
}
