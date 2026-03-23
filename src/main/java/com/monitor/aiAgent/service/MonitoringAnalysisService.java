package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.MonitoringAnalysisReport;
import com.monitor.aiAgent.dto.SourceAnalysisResult;
import com.monitor.aiAgent.model.DailyStat;
import com.monitor.aiAgent.model.IngestionMetrics;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MonitoringAnalysisService {

    public MonitoringAnalysisReport analyze(List<IngestionMetrics> metricsList) {
        List<SourceAnalysisResult> results = metricsList.stream()
                .map(this::analyzeSingle)
                .sorted(Comparator.comparingInt(SourceAnalysisResult::getHealthScore))
                .collect(Collectors.toList());

        int totalSources = results.size();
        int totalSourcesWithIssues = (int) results.stream()
                .filter(SourceAnalysisResult::isAlertTriggered)
                .count();

        double averageSuccessRate = results.stream()
                .mapToInt(SourceAnalysisResult::getSuccessRate7Days)
                .average()
                .orElse(0);

        int totalPostsToday = results.stream()
                .mapToInt(SourceAnalysisResult::getPostsToday)
                .sum();

        int totalSavedByUsers = results.stream()
                .mapToInt(SourceAnalysisResult::getSavedByUsers)
                .sum();

        String mostProblematicSource = results.isEmpty() ? "-" : results.get(0).getSourceName();

        Map<String, Integer> typeAllocation = new LinkedHashMap<>();
        Map<String, Integer> categoryAllocation = new LinkedHashMap<>();

        for (SourceAnalysisResult result : results) {
            if (result.getPostTypeCount() != null) {
                for (Map.Entry<String, Integer> entry : result.getPostTypeCount().entrySet()) {
                    typeAllocation.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
        }

        return MonitoringAnalysisReport.builder()
                .totalSources(totalSources)
                .totalSourcesWithIssues(totalSourcesWithIssues)
                .averageSuccessRate(averageSuccessRate)
                .totalPostsToday(totalPostsToday)
                .totalSavedByUsers(totalSavedByUsers)
                .mostProblematicSource(mostProblematicSource)
                .sources(results)
                .typeAllocation(typeAllocation)
                .categoryAllocation(categoryAllocation)
                .build();
    }

    public SourceAnalysisResult analyzeSingle(IngestionMetrics m) {
        int totalRuns = 0;
        int failCount = 0;

        if (m.getDailyStats() != null) {
            for (DailyStat stat : m.getDailyStats()) {
                totalRuns += stat.getTotal();
                failCount += stat.getFailed();
            }
        }

        int successRate7Days = totalRuns <= 0 ? 100 : ((totalRuns - failCount) * 100) / totalRuns;
        int consecutiveFailCount = m.getConsecutiveFailCount();

        double ingestionRatio = m.getAvgPosts7Days() <= 0 ? 1.0
                : (double) m.getPostsToday() / m.getAvgPosts7Days();

        double dropPercent = m.getAvgPosts7Days() <= 0 ? 0
                : Math.max(0, ((double) (m.getAvgPosts7Days() - m.getPostsToday()) / m.getAvgPosts7Days()) * 100);

        double issueRatio = m.getPostsToday() <= 0 ? 0
                : (double) m.getIssuePosts() / m.getPostsToday();

        double moderationRatio = m.getPostsToday() <= 0 ? 0
                : (double) m.getModeratedPosts() / m.getPostsToday();

        int ingestionDeduction = calcIngestionDeduction(ingestionRatio, m.getPostsToday(), m.getAvgPosts7Days());
        int issueDeduction = calcIssueDeduction(issueRatio);
        int successRateDeduction = calcSuccessRateDeduction(successRate7Days);
        int failHistoryDeduction = calcFailHistoryDeduction(failCount, consecutiveFailCount);
        int moderationDeduction = calcModerationDeduction(moderationRatio);

        int healthScore = Math.max(0, 100
                - ingestionDeduction
                - issueDeduction
                - successRateDeduction
                - failHistoryDeduction
                - moderationDeduction);

        List<String> alertReasons = new ArrayList<>();
        String alertLevel = "NORMAL";
        boolean noPostsUnexpected = m.getPostsToday() == 0 && m.getAvgPosts7Days() > 0;

        if (successRate7Days < 50
                || noPostsUnexpected
                || consecutiveFailCount >= 3
                || ingestionRatio <= 0.30) {
            alertLevel = "CRITICAL";
        } else if (ingestionRatio < 0.70
                || issueRatio >= 0.30
                || moderationRatio >= 0.30
                || failCount >= 5
                || "failed".equalsIgnoreCase(m.getStatus())) {
            alertLevel = "WARNING";
        }

        if (m.getPostsToday() == 0 && m.getAvgPosts7Days() > 0) {
            alertReasons.add("No posts ingested today despite historical activity");
        }
        if (ingestionRatio < 0.70)
            alertReasons.add("Ingestion below 70% of 7-day average");
        if (ingestionRatio <= 0.50)
            alertReasons.add("Ingestion dropped by 50% or more");
        if (issueRatio >= 0.30)
            alertReasons.add("High issue-post ratio");
        if (moderationRatio >= 0.30)
            alertReasons.add("High moderated-post ratio");
        if (failCount >= 5)
            alertReasons.add("High recent fail count");
        if (consecutiveFailCount >= 3)
            alertReasons.add("3 or more consecutive failures");
        if ("failed".equalsIgnoreCase(m.getStatus()) && m.getErrorMessage() != null && !m.getErrorMessage().isBlank()) {
            alertReasons.add("Latest run failed: " + m.getErrorMessage());
        }

        String healthLevel = healthScore >= 90 ? "HEALTHY"
                : healthScore >= 70 ? "WARNING"
                        : "CRITICAL";

        return SourceAnalysisResult.builder()
                .sourceName(m.getSourceName())
                .sourceUrl(m.getSourceUrl())
                .healthScore(healthScore)
                .healthLevel(healthLevel)
                .alertLevel(alertLevel)
                .postsToday(m.getPostsToday())
                .avgPosts7Days(m.getAvgPosts7Days())
                .ingestionRatio(ingestionRatio)
                .dropPercent(dropPercent)
                .issuePosts(m.getIssuePosts())
                .issueRatio(issueRatio)
                .moderatedPosts(m.getModeratedPosts())
                .moderationRatio(moderationRatio)
                .successRate7Days(successRate7Days)
                .failCount(failCount)
                .totalRuns(totalRuns)
                .consecutiveFailCount(consecutiveFailCount)
                .currentStatus(m.getStatus())
                .primaryError(m.getErrorMessage())
                .lastRunTime(toLong(m.getLastRunTime()))
                .lastSuccessTime(toLong(m.getLastSucessTime()))
                .lastSuccessLabel(buildLastSuccessLabel(m))
                .alertTriggered(!alertReasons.isEmpty())
                .alertReasons(alertReasons)
                .postTypeCount(m.getPostTypeCount())
                .savedByUsers(m.getSavedByUsers())
                .ingestionDeduction(ingestionDeduction)
                .issueDeduction(issueDeduction)
                .successRateDeduction(successRateDeduction)
                .failHistoryDeduction(failHistoryDeduction)
                .moderationDeduction(moderationDeduction)
                .build();
    }

    private int calculateSuccessRate7Days(int totalRuns7Days, int failedRuns7Days) {
        if (totalRuns7Days <= 0)
            return 100;
        int successRuns = Math.max(0, totalRuns7Days - failedRuns7Days);
        return (successRuns * 100) / totalRuns7Days;
    }

    private int calcIngestionDeduction(double ingestionRatio, int postsToday, int avgPosts7Days) {
        if (postsToday == 0 && avgPosts7Days > 0)
            return 20;
        if (ingestionRatio <= 0.05)
            return 19;
        if (ingestionRatio <= 0.10)
            return 18;
        if (ingestionRatio <= 0.15)
            return 17;
        if (ingestionRatio <= 0.20)
            return 16;
        if (ingestionRatio <= 0.25)
            return 15;
        if (ingestionRatio <= 0.30)
            return 14;
        if (ingestionRatio <= 0.35)
            return 13;
        if (ingestionRatio <= 0.40)
            return 12;
        if (ingestionRatio <= 0.45)
            return 11;
        if (ingestionRatio <= 0.50)
            return 10;
        return 0;
    }

    private int calcIssueDeduction(double issueRatio) {
        if (issueRatio >= 0.05)
            return 15;
        if (issueRatio >= 0.03)
            return 10;
        if (issueRatio >= 0.01)
            return 5;
        return 0;
    }

    private int calcSuccessRateDeduction(int successRate7Days) {
        if (successRate7Days < 10)
            return 70;
        if (successRate7Days < 20)
            return 60;
        if (successRate7Days < 30)
            return 50;
        if (successRate7Days < 40)
            return 40;
        if (successRate7Days < 50)
            return 30;
        return 0;
    }

    private int calcFailHistoryDeduction(int failCount, int consecutiveFailCount) {
        if (consecutiveFailCount >= 3)
            return 30;
        if (failCount >= 20)
            return 30;
        if (failCount >= 15)
            return 25;
        if (failCount >= 10)
            return 20;
        if (failCount >= 5)
            return 15;
        return 0;
    }

    private int calcModerationDeduction(double moderationRatio) {
        if (moderationRatio >= 0.05)
            return 10;
        if (moderationRatio >= 0.03)
            return 7;
        if (moderationRatio >= 0.01)
            return 5;
        return 0;
    }

    private Long toLong(Number value) {
        return value == null ? null : value.longValue();
    }

    private String buildLastSuccessLabel(IngestionMetrics m) {
        Long ts = toLong(m.getLastSucessTime());
        if (ts == null)
            return "Unknown";

        Duration duration = Duration.between(Instant.ofEpochMilli(ts), Instant.now());
        long hours = duration.toHours();

        if (hours < 1)
            return "Within 1 hour";
        if (hours < 24)
            return hours + " hours ago";
        return duration.toDays() + " days ago";
    }
}