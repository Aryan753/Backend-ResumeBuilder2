package com.resumebuilder.repository;

import com.resumebuilder.entity.Resume;
import com.resumebuilder.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {
    List<Resume> findByUserOrderByCreatedAtDesc(User user);
    Optional<Resume> findByIdAndUser(String id, User user);
    long countByUserAndCreatedAtAfter(User user, LocalDateTime startOfDay);
    void deleteByIdAndUser(String id, User user);
}
