package com.linkbit.mvp.controller;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.dto.SetRiskStateRequest;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/loans")
@RequiredArgsConstructor
public class AdminRiskController {

    private final LoanRepository loanRepository;
    private final ChatService chatService;

    @PostMapping("/{loanId}/set-risk-state")
    public ResponseEntity<Void> setRiskState(
            @PathVariable UUID loanId,
            @Valid @RequestBody SetRiskStateRequest request) {

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        List<LoanStatus> allowedStates = List.of(LoanStatus.ACTIVE, LoanStatus.MARGIN_CALL, LoanStatus.LIQUIDATION_ELIGIBLE);
        if (!allowedStates.contains(request.getStatus())) {
            throw new RuntimeException("Invalid risk state specific override");
        }

        loan.setStatus(request.getStatus());
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Admin mechanically overridden risk state to: " + request.getStatus());
        return ResponseEntity.accepted().build();
    }
}
