package com.resumebuilder.entity;

import com.resumebuilder.enums.AuthProvider;
import com.resumebuilder.enums.UserRole;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String name;
    private String phone;
    private String profilePicture;

    private AuthProvider authProvider = AuthProvider.LOCAL;
    private UserRole role = UserRole.USER;

    private String googleId;
    private boolean emailVerified = false;
    private boolean active = true;

    // Daily free resume quota
    private int dailyFreeResumesUsed = 0;
    private LocalDate lastResumeDate;

    // Ad-unlocked resumes tracking
    private int adUnlockedResumesToday = 0;
    private LocalDate lastAdUnlockDate;

    // Lifetime stats
    private int totalResumesCreated = 0;
    private int totalAdsWatched = 0;
    private double totalRevenuePaid = 0.0;

    // Activity tracking for admin
    private LocalDateTime lastSeenAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
