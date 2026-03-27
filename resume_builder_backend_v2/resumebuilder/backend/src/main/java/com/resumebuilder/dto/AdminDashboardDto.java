package com.resumebuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminDashboardDto {

    private long totalUsers;
    private long activeUsersToday;
    private long activeUsersThisWeek;
    private long newUsersToday;
    private long newUsersThisWeek;
    private long newUsersThisMonth;

    private long totalResumes;
    private long resumesToday;
    private long resumesThisWeek;
    private long fresherResumes;
    private long experiencedResumes;
    private long uploadedResumes;
    private long formCreatedResumes;

    private double totalRevenue;
    private double revenueToday;
    private double revenueThisWeek;
    private double revenueThisMonth;
    private long paidResumesTotal;

    private long totalAdsWatched;
    private long adsWatchedToday;
    private long adUnlockedResumes;

    private List<DailyStatDto> last7Days;
    private List<UserSummaryDto> topUsers;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyStatDto {
        private String date;
        private int newUsers;
        private int resumesCreated;
        private int adWatches;
        private double revenue;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserSummaryDto {
        private String id;       // String for MongoDB ObjectId
        private String name;
        private String email;
        private int totalResumes;
        private double totalPaid;
        private String joinedDate;
        private String lastSeen;
        private boolean active;
    }
}
