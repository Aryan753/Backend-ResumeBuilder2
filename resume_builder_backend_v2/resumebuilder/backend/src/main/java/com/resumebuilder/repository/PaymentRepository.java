package com.resumebuilder.repository;

import com.resumebuilder.entity.Payment;
import com.resumebuilder.entity.User;
import com.resumebuilder.enums.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByRazorpayOrderId(String orderId);
    List<Payment> findByUserOrderByCreatedAtDesc(User user);
    List<Payment> findByUserAndStatus(User user, PaymentStatus status);
}
