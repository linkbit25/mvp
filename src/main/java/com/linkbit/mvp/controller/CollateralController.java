package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.TopUpCollateralRequest;
import com.linkbit.mvp.service.CollateralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class CollateralController {

    private final CollateralService collateralService;

    // NOTE: topup-collateral route is handled by EscrowController
    // This controller is kept for any future collateral endpoints that don't conflict
}
