package com.monitor.aiAgent.model;

import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "ai_ingestion_metrics")
public class IngestionMetrics {

    @Id
    private ObjectId id;
    private String sourceName;
    private String sourceUrl;

    private String language;
    private String sourceType;

    private Number lastRunTime;
    private Number lastSucessTime;
    private Number lastFailureTime;

    private String status;
    private String errorMessage;

    private List<DailyStat> dailyStats;
    private List<FailureSample> recentFailures;

    private int postsToday;
    private int avgPosts7Days;

    private int totalRuns7Days;
    private int failedRuns7Days;
    private int consecutiveFailCount;
    private int successRate7Days;

    private int issuePosts;
    private int moderatedPosts;
    private int savedByUsers;
    private Map<String, Integer> postTypeCount;

    private int score;
}