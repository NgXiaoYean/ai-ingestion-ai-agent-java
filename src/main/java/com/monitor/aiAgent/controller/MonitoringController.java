package com.monitor.aiAgent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.monitor.aiAgent.model.IngestionMetrics;
import com.monitor.aiAgent.repository.MetricsRepository;

@RestController
@RequestMapping("/monitoring")
public class MonitoringController {
    private final MetricsRepository repository;

    public MonitoringController(MetricsRepository repository){
        this.repository = repository;
    }

    @GetMapping("/metrics")
    public List<IngestionMetrics> getMetrics() {
    
        List<IngestionMetrics> data = repository.findAll();
    
        System.out.println("Mongo returned: " + data.size());
    
        return data;
    }
}
