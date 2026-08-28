package com.vetclinic.booking.domain;

import java.time.LocalTime;
import java.util.List;

public class ClinicSchedule {

    private ClinicSchedule() {
    }

    public static final List<LocalTime> SLOT_TIMES = List.of(
            LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30),
            LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30),
            LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(14, 0), LocalTime.of(14, 30),
            LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0), LocalTime.of(16, 30)
    );

    public static final int SLOT_DURATION_MINUTES = 30;

    // SlotGenerationScheduler dùng để biết sinh slot trước bao nhiêu ngày mỗi lần chạy.
    public static final int SLOT_GENERATION_HORIZON_DAYS = 14;
}
