package com.resumebuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AtsScoreDto {
    private Double score;
    private String grade;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> missingKeywords;
    private String summary;
}
