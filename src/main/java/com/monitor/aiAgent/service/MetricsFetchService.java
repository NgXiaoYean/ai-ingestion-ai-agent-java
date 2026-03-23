package com.monitor.aiAgent.service;

import com.monitor.aiAgent.model.IngestionMetrics;
import com.monitor.aiAgent.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricsFetchService {

    private final MetricsRepository metricsRepository;


    public List<IngestionMetrics> getAllMetrics() {
        return metricsRepository.findAll();
    }

    public List<IngestionMetrics> getTodayMetrics() {

        List<IngestionMetrics> metrics = metricsRepository.findAll();

        System.out.println("Metrics fetched: " + metrics.size());

        return metrics;
    }
}