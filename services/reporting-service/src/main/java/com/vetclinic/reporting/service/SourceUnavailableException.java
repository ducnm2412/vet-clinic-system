package com.vetclinic.reporting.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Báo cáo chỉ có đúng một nguồn và nguồn đó không trả lời — 503, không phải 500. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message) {
        super(message);
    }
}
