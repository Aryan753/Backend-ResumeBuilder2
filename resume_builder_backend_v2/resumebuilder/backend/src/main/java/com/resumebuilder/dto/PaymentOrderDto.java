package com.resumebuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentOrderDto {
    private String orderId;
    private Long amount; // in paise
    private String currency;
    private String keyId;
    private String resumeId;  // String for MongoDB ObjectId
}
