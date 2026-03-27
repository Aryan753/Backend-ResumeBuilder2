package com.resumebuilder.dto;

import com.resumebuilder.enums.ResumeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ResumeDto {
    private String id;       // String for MongoDB ObjectId
    private String title;
    private String fileName;
    private String templateId;
    private String resumeData;
    private Double atsScore;
    private String atsFeedback;
    private ResumeStatus status;
    private boolean isPaid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
