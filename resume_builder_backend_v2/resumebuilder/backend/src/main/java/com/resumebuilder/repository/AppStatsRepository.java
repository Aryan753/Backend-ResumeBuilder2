package com.resumebuilder.repository;

import com.resumebuilder.entity.AppStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppStatsRepository extends MongoRepository<AppStats, String> {
    Optional<AppStats> findByStatDate(LocalDate date);
    List<AppStats> findByStatDateGreaterThanEqualOrderByStatDateAsc(LocalDate startDate);
}
