package com.resumebuilder.service;

import com.resumebuilder.dto.AdminDashboardDto;
import com.resumebuilder.entity.AppStats;
import com.resumebuilder.entity.User;
import com.resumebuilder.enums.UserRole;
import com.resumebuilder.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PaymentRepository paymentRepository;
    private final AdWatchEventRepository adWatchEventRepository;
    private final AppStatsRepository appStatsRepository;

    public AdminService(UserRepository userRepository,
                        ResumeRepository resumeRepository,
                        PaymentRepository paymentRepository,
                        AdWatchEventRepository adWatchEventRepository,
                        AppStatsRepository appStatsRepository) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.paymentRepository = paymentRepository;
        this.adWatchEventRepository = adWatchEventRepository;
        this.appStatsRepository = appStatsRepository;
    }

    public AdminDashboardDto getDashboard() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek  = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().minusDays(30).atStartOfDay();

        long totalUsers     = userRepository.count() - userRepository.countByRole(UserRole.ADMIN);
        long activeToday    = userRepository.countByLastSeenAtAfter(startOfToday);
        long activeThisWeek = userRepository.countByLastSeenAtAfter(startOfWeek);
        long newToday       = userRepository.countByCreatedAtAfter(startOfToday);
        long newThisWeek    = userRepository.countByCreatedAtAfter(startOfWeek);
        long newThisMonth   = userRepository.countByCreatedAtAfter(startOfMonth);

        long totalResumes = resumeRepository.count();

        // Revenue — aggregate from AppStats
        double totalRev = sumRevenue(LocalDate.of(2024, 1, 1));
        double weekRev  = sumRevenue(LocalDate.now().minusDays(7));
        double monthRev = sumRevenue(LocalDate.now().minusDays(30));
        double todayRev = appStatsRepository.findByStatDate(LocalDate.now())
                .map(AppStats::getRevenueToday).orElse(0.0);

        long totalAds = adWatchEventRepository.count();
        long todayAds = adWatchEventRepository.countByWatchDate(LocalDate.now());

        List<AdminDashboardDto.DailyStatDto> last7Days = buildLast7Days();
        List<AdminDashboardDto.UserSummaryDto> topUsers = buildTopUsers();

        return AdminDashboardDto.builder()
                .totalUsers(totalUsers)
                .activeUsersToday(activeToday)
                .activeUsersThisWeek(activeThisWeek)
                .newUsersToday(newToday)
                .newUsersThisWeek(newThisWeek)
                .newUsersThisMonth(newThisMonth)
                .totalResumes(totalResumes)
                .totalRevenue(totalRev)
                .revenueToday(todayRev)
                .revenueThisWeek(weekRev)
                .revenueThisMonth(monthRev)
                .totalAdsWatched(totalAds)
                .adsWatchedToday(todayAds)
                .last7Days(last7Days)
                .topUsers(topUsers)
                .build();
    }

    /** Sum revenueToday across all AppStats records on or after startDate */
    private double sumRevenue(LocalDate startDate) {
        return appStatsRepository
                .findByStatDateGreaterThanEqualOrderByStatDateAsc(startDate)
                .stream()
                .mapToDouble(AppStats::getRevenueToday)
                .sum();
    }

    private List<AdminDashboardDto.DailyStatDto> buildLast7Days() {
        List<AdminDashboardDto.DailyStatDto> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            AppStats stats = appStatsRepository.findByStatDate(date).orElse(null);
            result.add(AdminDashboardDto.DailyStatDto.builder()
                    .date(date.format(fmt))
                    .newUsers(stats != null ? stats.getNewUsers() : 0)
                    .resumesCreated(stats != null ? stats.getResumesCreated() : 0)
                    .adWatches(stats != null ? stats.getAdWatches() : 0)
                    .revenue(stats != null ? stats.getRevenueToday() : 0.0)
                    .build());
        }
        return result;
    }

    private List<AdminDashboardDto.UserSummaryDto> buildTopUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20))
                .getContent().stream()
                .filter(u -> u.getRole() == UserRole.USER)
                .map(u -> AdminDashboardDto.UserSummaryDto.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .totalResumes(u.getTotalResumesCreated())
                        .totalPaid(u.getTotalRevenuePaid())
                        .joinedDate(u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate().toString() : "")
                        .lastSeen(u.getLastSeenAt() != null ? u.getLastSeenAt().toLocalDate().toString() : "Never")
                        .active(u.getLastSeenAt() != null && u.getLastSeenAt().isAfter(LocalDateTime.now().minusDays(7)))
                        .build())
                .collect(Collectors.toList());
    }

    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Kolkata")
    public void snapshotDailyStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        AppStats stats = appStatsRepository.findByStatDate(today)
                .orElse(AppStats.builder().statDate(today).build());
        stats.setNewUsers((int) userRepository.countByCreatedAtAfter(startOfToday));
        stats.setActiveUsers((int) userRepository.countByLastSeenAtAfter(startOfToday));
        stats.setAdWatches((int) adWatchEventRepository.countByWatchDate(today));
        appStatsRepository.save(stats);
    }

    public void updateRevenueStats(double amount) {
        LocalDate today = LocalDate.now();
        AppStats stats = appStatsRepository.findByStatDate(today)
                .orElse(AppStats.builder().statDate(today).build());
        stats.setRevenueToday(stats.getRevenueToday() + amount);
        stats.setPaidResumes(stats.getPaidResumes() + 1);
        appStatsRepository.save(stats);
    }

    public void recordResumeCreated(boolean isUpload) {
        LocalDate today = LocalDate.now();
        AppStats stats = appStatsRepository.findByStatDate(today)
                .orElse(AppStats.builder().statDate(today).build());
        if (isUpload) stats.setResumesUploaded(stats.getResumesUploaded() + 1);
        else stats.setResumesCreated(stats.getResumesCreated() + 1);
        appStatsRepository.save(stats);
    }

    public void recordAdWatch() {
        LocalDate today = LocalDate.now();
        AppStats stats = appStatsRepository.findByStatDate(today)
                .orElse(AppStats.builder().statDate(today).build());
        stats.setAdWatches(stats.getAdWatches() + 1);
        appStatsRepository.save(stats);
    }

    public List<User> getAllUsers(int page, int size) {
        return userRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).getContent();
    }

    public void toggleUserActive(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.resumebuilder.exception.AppException("User not found"));
        user.setActive(!user.isActive());
        userRepository.save(user);
    }
}
