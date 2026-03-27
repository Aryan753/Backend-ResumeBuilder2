package com.resumebuilder.service;

import com.resumebuilder.entity.User;
import com.resumebuilder.enums.AuthProvider;
import com.resumebuilder.enums.UserRole;
import com.resumebuilder.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminInitService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@resumebuilder.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123456}")
    private String adminPassword;

    public AdminInitService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .name("Admin")
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .authProvider(AuthProvider.LOCAL)
                    .role(UserRole.ADMIN)
                    .emailVerified(true)
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("✅ Admin account created: {}", adminEmail);
        } else {
            // Ensure existing admin email has ADMIN role
            userRepository.findByEmail(adminEmail).ifPresent(u -> {
                if (u.getRole() != UserRole.ADMIN) {
                    u.setRole(UserRole.ADMIN);
                    userRepository.save(u);
                    log.info("✅ Admin role assigned to: {}", adminEmail);
                }
            });
        }
    }
}
