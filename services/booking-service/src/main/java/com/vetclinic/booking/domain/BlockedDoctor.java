package com.vetclinic.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** CN-08: bác sĩ có tài khoản đang bị khoá — không sinh slot mới, slot tương lai bị chặn. */
@Entity
@Table(name = "blocked_doctors")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BlockedDoctor {

    @Id
    @Column(name = "doctor_user_id")
    private UUID doctorUserId;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;
}
