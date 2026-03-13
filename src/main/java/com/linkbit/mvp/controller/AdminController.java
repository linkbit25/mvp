package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.VerifyTopUpRequest;
import com.linkbit.mvp.service.CollateralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CollateralService collateralService;

    // NOTE: verify-topup route is handled by EscrowController
    // This controller is kept for any future admin collateral endpoints that don't conflict
}
