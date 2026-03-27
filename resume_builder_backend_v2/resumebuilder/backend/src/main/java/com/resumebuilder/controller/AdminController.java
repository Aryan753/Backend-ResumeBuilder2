package com.resumebuilder.controller;

import com.resumebuilder.dto.AdminDashboardDto;
import com.resumebuilder.dto.ApiResponse;
import com.resumebuilder.entity.User;
import com.resumebuilder.enums.UserRole;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    public AdminController(AdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardDto>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAdmin(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Dashboard loaded", adminService.getDashboard()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AdminDashboardDto.UserSummaryDto>>> getUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireAdmin(userDetails);
        List<AdminDashboardDto.UserSummaryDto> users = adminService.getAllUsers(page, size)
                .stream()
                .filter(u -> u.getRole() == UserRole.USER)
                .map(u -> AdminDashboardDto.UserSummaryDto.builder()
                        .id(u.getId()).name(u.getName()).email(u.getEmail())
                        .totalResumes(u.getTotalResumesCreated())
                        .totalPaid(u.getTotalRevenuePaid())
                        .joinedDate(u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate().toString() : "")
                        .lastSeen(u.getLastSeenAt() != null ? u.getLastSeenAt().toLocalDate().toString() : "Never")
                        .active(u.isActive())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Users fetched", users));
    }

    @PatchMapping("/users/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggleUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        requireAdmin(userDetails);
        adminService.toggleUserActive(id);
        return ResponseEntity.ok(ApiResponse.success("User status updated", null));
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQuickStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAdmin(userDetails);
        AdminDashboardDto db = adminService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success("Stats fetched", Map.of(
                "totalUsers", db.getTotalUsers(),
                "activeToday", db.getActiveUsersToday(),
                "newToday", db.getNewUsersToday(),
                "totalRevenue", db.getTotalRevenue(),
                "revenueToday", db.getRevenueToday(),
                "totalResumes", db.getTotalResumes(),
                "adsWatchedToday", db.getAdsWatchedToday()
        )));
    }

    private void requireAdmin(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException("User not found"));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AppException("Access denied. Admin only.");
        }
    }
}
