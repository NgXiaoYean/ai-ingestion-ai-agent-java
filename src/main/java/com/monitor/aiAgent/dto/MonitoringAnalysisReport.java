package com.monitor.aiAgent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class MonitoringAnalysisReport {
    private int totalSources;
    private int totalSourcesWithIssues;
    private double averageSuccessRate;
    private long totalPostsToday;
    private String mostProblematicSource;
    private Map<String, Long> typeAllocation;
    private int totalSavedByUsers;

    // The list of cleaned source results
    private List<SourceAnalysisResult> sources;
}