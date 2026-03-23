package com.monitor.aiAgent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SourceAnalysisResult {
    private String sourceName;
    private String sourceUrl;

    private int healthScore;
    private String healthLevel; // HEALTHY / WARNING / CRITICAL
    private String alertLevel; // NORMAL / WARNING / CRITICAL

    private int postsToday;
    private int avgPosts7Days;
    private double ingestionRatio;
    private double dropPercent;

    private int issuePosts;
    private double issueRatio;

    private int moderatedPosts;
    private double moderationRatio;

    private int successRate7Days;
    private int failCount;
    private int totalRuns;
    private int consecutiveFailCount;

    private String currentStatus;
    private String primaryError;

    private Long lastRunTime;
    private Long lastSuccessTime;
    private String lastSuccessLabel;

    private boolean alertTriggered;
    private List<String> alertReasons;

    private Map<String, Integer> postTypeCount;
    private int savedByUsers;

    private int ingestionDeduction;
    private int issueDeduction;
    private int successRateDeduction;
    private int failHistoryDeduction;
    private int moderationDeduction;
}