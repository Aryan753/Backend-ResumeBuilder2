package com.resumebuilder.entity;

import com.resumebuilder.enums.ExperienceLevel;
import com.resumebuilder.enums.ResumeStatus;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "resumes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Resume {

    @Id
    private String id;

    @DBRef
    private User user;

    private String title;
    private String fileName;
    private String templateId;

    private ExperienceLevel experienceLevel = ExperienceLevel.EXPERIENCED;

    private String userUploadedTemplatePath;
    private boolean isCustomTemplate = false;

    private String resumeData; // JSON
    private String pdfFilePath;
    private Double atsScore;
    private String atsFeedback;
    private String jobDescription;

    private ResumeStatus status = ResumeStatus.DRAFT;
    private boolean isPaid = false;

    private String creationMethod; // "FORM", "UPLOAD", "TEMPLATE_EDIT"

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
