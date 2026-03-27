package com.resumebuilder.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.resumebuilder.dto.PaymentOrderDto;
import com.resumebuilder.dto.PaymentVerifyRequest;
import com.resumebuilder.entity.Payment;
import com.resumebuilder.entity.Resume;
import com.resumebuilder.entity.User;
import com.resumebuilder.enums.PaymentStatus;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.PaymentRepository;
import com.resumebuilder.repository.ResumeRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${app.resume.price.rupees:9}")
    private int resumePriceRupees;

    private final PaymentRepository paymentRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeService resumeService;


    private final AdminService adminService;

    public PaymentService(PaymentRepository paymentRepository,
                          ResumeRepository resumeRepository,
                          ResumeService resumeService,
                          AdminService adminService) {   // add this
        this.paymentRepository = paymentRepository;
        this.resumeRepository  = resumeRepository;
        this.resumeService     = resumeService;
        this.adminService      = adminService;           // add this
    }

//    public PaymentService(PaymentRepository paymentRepository,
//                          ResumeRepository resumeRepository,
//                          ResumeService resumeService, AdminService adminService) {
//        this.paymentRepository = paymentRepository;
//        this.resumeRepository  = resumeRepository;
//        this.resumeService     = resumeService;
//        this.adminService = adminService;
//    }

    @Transactional
    public PaymentOrderDto createOrder(User user, String resumeId) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            int amountPaise = resumePriceRupees * 100;

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + user.getId() + "_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1);

            Order order = client.orders.create(orderRequest);
            String orderId = order.get("id");

            Resume resume = (resumeId != null) ? resumeRepository.findById(resumeId).orElse(null) : null;

            Payment payment = Payment.builder()
                    .user(user).resume(resume)
                    .razorpayOrderId(orderId)
                    .amount(new BigDecimal(resumePriceRupees))
                    .currency("INR")
                    .status(PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(payment);

            return PaymentOrderDto.builder()
                    .orderId(orderId)
                    .amount((long) amountPaise)
                    .currency("INR")
                    .keyId(keyId)
                    .resumeId(resumeId)
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new AppException("Payment initialization failed. Please try again.");
        }
    }

    @Transactional
    public boolean verifyPayment(User user, PaymentVerifyRequest request) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id",   request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature",  request.getRazorpaySignature());
            Utils.verifyPaymentSignature(attributes, keySecret);

            Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                    .orElseThrow(() -> new AppException("Payment record not found."));

            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);


            adminService.updateRevenueStats(resumePriceRupees);

            if (request.getResumeId() != null) {
                resumeService.markAsPaid(request.getResumeId());
            }
            return true;

        } catch (RazorpayException e) {
            log.error("Payment verification failed: {}", e.getMessage());
            paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                    .ifPresent(p -> { p.setStatus(PaymentStatus.FAILED); paymentRepository.save(p); });
            throw new AppException("Payment verification failed. Contact support if amount was deducted.");
        }
    }
}
