package com.monitor.aiAgent.repository;

import com.monitor.aiAgent.model.IngestionMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MetricsRepository extends MongoRepository<IngestionMetrics, String> {
    Optional<IngestionMetrics> findBySourceName(String sourceName);
}