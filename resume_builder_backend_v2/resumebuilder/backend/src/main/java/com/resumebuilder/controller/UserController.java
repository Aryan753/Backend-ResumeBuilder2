package com.resumebuilder.controller;

import com.resumebuilder.dto.ApiResponse;
import com.resumebuilder.dto.UserDto;
import com.resumebuilder.entity.User;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.service.ResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final ResumeService resumeService;

    public UserController(UserRepository userRepository, ResumeService resumeService) {
        this.userRepository = userRepository;
        this.resumeService = resumeService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        int remaining = resumeService.getRemainingFreeResumes(user);
        UserDto dto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profilePicture(user.getProfilePicture())
                .dailyFreeResumesUsed(user.getDailyFreeResumesUsed())
                .freeResumesRemaining(remaining)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Profile fetched", dto));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        User user = getUser(userDetails);
        if (body.containsKey("name") && !body.get("name").isEmpty()) user.setName(body.get("name"));
        if (body.containsKey("phone")) user.setPhone(body.get("phone"));
        userRepository.save(user);

        UserDto dto = UserDto.builder()
                .id(user.getId()).name(user.getName())
                .email(user.getEmail()).phone(user.getPhone())
                .profilePicture(user.getProfilePicture())
                .freeResumesRemaining(resumeService.getRemainingFreeResumes(user))
                .build();
        return ResponseEntity.ok(ApiResponse.success("Profile updated!", dto));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException("User not found."));
    }
}
