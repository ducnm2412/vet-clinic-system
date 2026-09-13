package com.vetclinic.profile.controller;

import com.vetclinic.profile.dto.CustomerSummaryResponse;
import com.vetclinic.profile.service.CustomerSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Chỉ ADMIN — khai trong SecurityConfig. Dùng: ?userIds=a,b,c (tối đa 100). */
@RestController
@RequiredArgsConstructor
public class CustomerSummaryController {

    private final CustomerSummaryService customerSummaryService;

    @GetMapping("/profile/customers/summary")
    public List<CustomerSummaryResponse> summary(@RequestParam List<UUID> userIds) {
        return customerSummaryService.summarize(userIds);
    }
}
