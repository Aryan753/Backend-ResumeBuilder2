package com.resumebuilder.controller;

import com.resumebuilder.dto.*;
import com.resumebuilder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // FIX #1: /register removed — registration must go through OTP verification.
    // Keeping a plain /register endpoint allowed bypassing OTP entirely.
    // Use /send-otp → /register-otp flow instead.

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful!", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Google login successful!", response));
    }

    // FIX #3: Token moved from @RequestParam (query string) to Authorization header.
    // Query params are logged by servers, proxies, and browser history — tokens must never appear there.
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestHeader("Authorization") String bearerToken) {
        // Strip "Bearer " prefix if present
        String token = bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7)
                : bearerToken;
        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed!", response));
    }

    // FIX #6: @Valid added so @NotBlank / @Email constraints on OtpRequest are enforced
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpRequest request) {
        authService.sendRegistrationOtp(request.getEmail());
        // FIX #6: Neutral message — doesn't reveal whether email is already registered
        return ResponseEntity.ok(ApiResponse.success(
                "If this email is not registered, an OTP has been sent.", null));
    }

    @PostMapping("/register-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> registerWithOtp(
            @Valid @RequestBody OtpVerifyRegisterRequest request) {
        AuthResponse response = authService.registerWithOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Registration successful!", response));
    }
}