package com.resumebuilder.service;

import com.resumebuilder.dto.AdWatchResponseDto;
import com.resumebuilder.entity.AdWatchEvent;
import com.resumebuilder.entity.User;
import com.resumebuilder.repository.AdWatchEventRepository;
import com.resumebuilder.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AdService {

    private final AdWatchEventRepository adWatchEventRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;

    @Value("${app.resume.ads.required:1}")
    private int adsRequiredPerResume;

    public AdService(AdWatchEventRepository adWatchEventRepository,
                     UserRepository userRepository,
                     AdminService adminService) {
        this.adWatchEventRepository = adWatchEventRepository;
        this.userRepository = userRepository;
        this.adminService = adminService;
    }

    @Transactional
    public AdWatchResponseDto recordAdWatch(User user, String adType) {
        resetAdCountIfNewDay(user);

        boolean isRewarded = "REWARDED".equalsIgnoreCase(adType)
                || "INTERSTITIAL".equalsIgnoreCase(adType);

        AdWatchEvent event = AdWatchEvent.builder()
                .user(user)
                .adType(adType != null ? adType.toUpperCase() : "REWARDED")
                .watchDate(LocalDate.now())
                .resumeUnlocked(isRewarded)
                .build();
        adWatchEventRepository.save(event);

        user.setTotalAdsWatched(user.getTotalAdsWatched() + 1);
        if (isRewarded) {
            user.setAdUnlockedResumesToday(user.getAdUnlockedResumesToday() + 1);
            user.setLastAdUnlockDate(LocalDate.now());
        }
        userRepository.save(user);

        adminService.recordAdWatch();

        int remaining = user.getAdUnlockedResumesToday();
        return AdWatchResponseDto.builder()
                .resumeUnlocked(isRewarded)
                .adUnlockedResumesRemaining(remaining)
                .message(isRewarded
                        ? "Ad watched! You've unlocked 1 free resume."
                        : "Thanks for watching! Use Rewarded ads to unlock free resumes.")
                .build();
    }

    public boolean hasAdUnlockedCredit(User user) {
        resetAdCountIfNewDay(user);
        return user.getAdUnlockedResumesToday() > 0;
    }

    @Transactional
    public void consumeAdCredit(User user) {
        resetAdCountIfNewDay(user);
        int current = user.getAdUnlockedResumesToday();
        if (current > 0) {
            user.setAdUnlockedResumesToday(current - 1);
            userRepository.save(user);
        }
    }

    /**
     * Resets the daily ad-unlock counter when a new calendar day has started.
     * Previously this method had an empty body, so the counter never reset.
     */
    private void resetAdCountIfNewDay(User user) {
        LocalDate today = LocalDate.now();
        if (user.getLastAdUnlockDate() == null || !user.getLastAdUnlockDate().equals(today)) {
            user.setAdUnlockedResumesToday(0);
            user.setLastAdUnlockDate(today);
            userRepository.save(user);
        }
    }
}
