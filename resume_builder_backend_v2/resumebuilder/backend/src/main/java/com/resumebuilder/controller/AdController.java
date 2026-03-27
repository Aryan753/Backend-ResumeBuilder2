package com.resumebuilder.controller;

import com.resumebuilder.dto.AdWatchRequest;
import com.resumebuilder.dto.AdWatchResponseDto;
import com.resumebuilder.dto.ApiResponse;
import com.resumebuilder.entity.User;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.service.AdService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ads")
public class AdController {

    private final AdService adService;
    private final UserRepository userRepository;

    public AdController(AdService adService, UserRepository userRepository) {
        this.adService = adService;
        this.userRepository = userRepository;
    }

    /**
     * Android app calls this AFTER a rewarded/interstitial ad completes.
     * The server records the watch and optionally unlocks a resume.
     */
    @PostMapping("/watched")
    public ResponseEntity<ApiResponse<AdWatchResponseDto>> recordAdWatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AdWatchRequest request) {
        User user = getUser(userDetails);
        AdWatchResponseDto result = adService.recordAdWatch(user, request.getAdType());
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
    }

    /**
     * Check how many ad-unlocked resume credits the user has
     */
    @GetMapping("/credits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdCredits(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        boolean hasCredit = adService.hasAdUnlockedCredit(user);
        return ResponseEntity.ok(ApiResponse.success("Credits checked", Map.of(
                "hasCredit", hasCredit,
                "adUnlockedResumes", user.getAdUnlockedResumesToday(),
                "totalAdsWatched", user.getTotalAdsWatched()
        )));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException("User not found."));
    }
}
