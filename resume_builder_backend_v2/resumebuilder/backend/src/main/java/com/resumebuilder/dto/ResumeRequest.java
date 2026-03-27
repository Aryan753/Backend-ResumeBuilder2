package com.resumebuilder.dto;

import com.resumebuilder.enums.ExperienceLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeRequest {
    @NotBlank private String title;
    private String templateId;
    private String resumeData;
    private String jobDescription;
    private ExperienceLevel experienceLevel = ExperienceLevel.EXPERIENCED;
    private boolean isCustomTemplate = false;
    private String creationMethod = "FORM";
}
