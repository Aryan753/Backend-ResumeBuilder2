package com.resumebuilder.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "app_stats")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppStats {

    @Id
    private String id;

    @Indexed(unique = true)
    private LocalDate statDate;

    private int newUsers = 0;
    private int activeUsers = 0;
    private int resumesCreated = 0;
    private int resumesUploaded = 0;
    private int paidResumes = 0;
    private int adWatches = 0;
    private double revenueToday = 0.0;
}
