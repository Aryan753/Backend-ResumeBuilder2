package com.resumebuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDto {
    private String id;       // String for MongoDB ObjectId
    private String name;
    private String email;
    private String phone;
    private String role;
    private String profilePicture;
    private int dailyFreeResumesUsed;
    private int freeResumesRemaining;
}
