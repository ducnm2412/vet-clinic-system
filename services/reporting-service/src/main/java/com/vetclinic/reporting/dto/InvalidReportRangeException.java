package com.vetclinic.reporting.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Tham số báo cáo sai (khoảng ngày ngược, quá dài, interval lạ) — 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidReportRangeException extends RuntimeException {

    public InvalidReportRangeException(String message) {
        super(message);
    }
}
