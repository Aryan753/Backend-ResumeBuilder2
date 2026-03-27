package com.resumebuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdWatchResponseDto {
    private boolean resumeUnlocked;
    private int adUnlockedResumesRemaining;
    private String message;
}
