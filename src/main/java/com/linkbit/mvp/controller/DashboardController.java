package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.AdminOverviewResponse;
import com.linkbit.mvp.dto.LoanDetailResponse;
import com.linkbit.mvp.dto.LoanSummaryResponse;
import com.linkbit.mvp.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/loans/mine")
    public ResponseEntity<Page<LoanSummaryResponse>> getMyLoans(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(dashboardService.getMyLoans(
            authentication.getName(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    @GetMapping("/loans/{loanId}/details")
    public ResponseEntity<LoanDetailResponse> getLoanDetails(
            Authentication authentication,
            @PathVariable UUID loanId) {
        return ResponseEntity.ok(dashboardService.getLoanDetail(authentication.getName(), loanId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/overview")
    public ResponseEntity<AdminOverviewResponse> getAdminOverview() {
        return ResponseEntity.ok(dashboardService.getAdminOverview());
    }
}
