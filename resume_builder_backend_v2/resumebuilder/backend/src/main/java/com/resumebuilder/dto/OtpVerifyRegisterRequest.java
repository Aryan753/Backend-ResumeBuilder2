package com.resumebuilder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtpVerifyRegisterRequest {
    @NotBlank private String name;
    @NotBlank @Email private String email;
    @NotBlank @Size(min = 6) private String password;
    private String phone;
    @NotBlank private String otp;
}