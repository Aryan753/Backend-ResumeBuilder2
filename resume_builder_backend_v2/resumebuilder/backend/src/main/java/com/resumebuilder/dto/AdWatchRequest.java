package com.resumebuilder.dto;

import lombok.Data;
@Data
public class AdWatchRequest {
    private String adType; // "REWARDED", "INTERSTITIAL", "BANNER"
    private String adUnitId; // AdMob Ad Unit ID from client
}
