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
    private String language; // e.g., "MS", "EN", "ZH"
    private String sourceType; // e.g., "Article", "Video"

    private int healthScore;
    private String alertLevel; // NORMAL / WARNING / CRITICAL

    private int postsToday;
    private int avgPosts7Days;
    private double ingestionRatio;

    private int issuePosts;
    private double issueRatio;

    private int highModeratedPosts;
    private double highModRatio;

    private int mediumModeratedPosts;
    private double mediumModRatio;

    private int successRate7Days;
    private int failCount;
    private int totalRuns;
    private int consecutiveFailCount;

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
    private int consecutiveFailCountDeduction;
    private int moderationDeduction;
}