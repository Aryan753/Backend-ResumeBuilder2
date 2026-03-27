package com.resumebuilder.repository;

import com.resumebuilder.entity.User;
import com.resumebuilder.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByGoogleId(String googleId);

    long countByCreatedAtAfter(LocalDateTime dateTime);
    long countByLastSeenAtAfter(LocalDateTime dateTime);
    long countByRole(UserRole role);
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);
}
