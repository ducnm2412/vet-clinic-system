package com.vetclinic.booking.exception;

import com.vetclinic.booking.dto.SuggestedSlotResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class SlotFullyBookedException extends RuntimeException {

    private final List<SuggestedSlotResponse> suggestions;

    public SlotFullyBookedException(List<SuggestedSlotResponse> suggestions) {
        super("Requested slot is fully booked");
        this.suggestions = suggestions;
    }
}
