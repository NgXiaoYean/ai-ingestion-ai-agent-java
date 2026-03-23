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
    private int totalPostsToday;
    private int totalSavedByUsers;

    private String mostProblematicSource;

    private List<SourceAnalysisResult> sources;

    private Map<String, Integer> typeAllocation;
    private Map<String, Integer> categoryAllocation;
}