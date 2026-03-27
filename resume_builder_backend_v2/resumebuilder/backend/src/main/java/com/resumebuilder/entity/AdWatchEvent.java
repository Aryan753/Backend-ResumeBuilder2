package com.resumebuilder.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "ad_watch_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdWatchEvent {

    @Id
    private String id;

    @DBRef
    private User user;

    private String adType; // "BANNER", "INTERSTITIAL", "REWARDED"
    private LocalDate watchDate;
    private boolean resumeUnlocked = false;

    @CreatedDate
    private LocalDateTime createdAt;
}
