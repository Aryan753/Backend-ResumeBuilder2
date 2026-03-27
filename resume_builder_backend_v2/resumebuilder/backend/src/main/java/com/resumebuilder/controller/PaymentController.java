package com.resumebuilder.controller;

import com.resumebuilder.dto.ApiResponse;
import com.resumebuilder.dto.PaymentOrderDto;
import com.resumebuilder.dto.PaymentVerifyRequest;
import com.resumebuilder.entity.User;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    public PaymentController(PaymentService paymentService, UserRepository userRepository) {
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderDto>> createOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        User user = getUser(userDetails);
        String resumeId = body.get("resumeId");
        PaymentOrderDto order = paymentService.createOrder(user, resumeId);
        return ResponseEntity.ok(ApiResponse.success("Payment order created!", order));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PaymentVerifyRequest request) {
        User user = getUser(userDetails);
        boolean success = paymentService.verifyPayment(user, request);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Payment successful! Resume unlocked.", null));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Payment verification failed."));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException("User not found."));
    }
}
