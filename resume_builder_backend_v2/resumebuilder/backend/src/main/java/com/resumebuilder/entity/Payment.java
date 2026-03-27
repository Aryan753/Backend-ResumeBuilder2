package com.resumebuilder.entity;

import com.resumebuilder.enums.PaymentStatus;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    private String id;

    @DBRef
    private User user;

    @DBRef
    private Resume resume;

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    private BigDecimal amount; // in rupees
    private String currency = "INR";

    private PaymentStatus status = PaymentStatus.PENDING;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
}
