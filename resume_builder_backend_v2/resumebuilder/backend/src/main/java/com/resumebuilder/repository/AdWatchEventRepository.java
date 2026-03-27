package com.resumebuilder.repository;

import com.resumebuilder.entity.AdWatchEvent;
import com.resumebuilder.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AdWatchEventRepository extends MongoRepository<AdWatchEvent, String> {
    List<AdWatchEvent> findByUserAndWatchDate(User user, LocalDate date);
    long countByUserAndWatchDateAndResumeUnlocked(User user, LocalDate date, boolean unlocked);
    long countByWatchDate(LocalDate date);
    long countByWatchDateGreaterThanEqual(LocalDate startDate);
}
