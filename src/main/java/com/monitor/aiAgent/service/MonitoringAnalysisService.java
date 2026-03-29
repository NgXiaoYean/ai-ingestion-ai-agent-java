package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.CompactMonitoringReport;
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
                .build();
    }

    public SourceAnalysisResult analyzeSingle(IngestionMetrics m) {
        // 1. DATA CALCULATIONS (Keep your existing math)
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
        double ingestionRatio = m.getAvgPosts7Days() <= 0 ? 1.0 : (double) m.getPostsToday() / m.getAvgPosts7Days();
        double issueRatio = m.getPostsToday() <= 0 ? 0 : (double) m.getIssuePosts() / m.getPostsToday();
        double highModRatio = m.getPostsToday() <= 0 ? 0 : (double) m.getHighModeratedPosts() / m.getPostsToday();
        double mediumModRatio = m.getPostsToday() <= 0 ? 0 : (double) m.getMediumModeratedPosts() / m.getPostsToday();
        // 2. DEDUCTIONS (This builds the Daily Health Score)
        int ingestionDeduction = calcIngestionDeduction(ingestionRatio, m.getPostsToday(), m.getAvgPosts7Days());
        int issueDeduction = calcIssueDeduction(issueRatio);
        int successRateDeduction = calcSuccessRateDeduction(successRate7Days);
        int consecutiveFailCountDeduction = calcConstFailCount(consecutiveFailCount);
        int moderationDeduction = calcModerationDeduction(highModRatio, mediumModRatio);

        int healthScore = Math.max(0, 100 - ingestionDeduction - issueDeduction - successRateDeduction
                - consecutiveFailCountDeduction - moderationDeduction);

        // 3. SMART ALERT LOGIC (Immediate vs Daily)
        List<String> alertReasons = new ArrayList<>();
        String alertLevel = "NORMAL";

        boolean isLateCheck = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"))
                .isAfter(java.time.LocalTime.of(12, 0));

        // --- LEVEL 1: CRITICAL (Immediate Action Required) ---
        if (consecutiveFailCount >= 3) {
            alertLevel = "CRITICAL";
            alertReasons.add("CRITICAL: Connection Lost (3+ fails)");
        } else if (successRate7Days < 20) {
            alertLevel = "CRITICAL";
            alertReasons.add("CRITICAL: Unstable Source (Success < 20%)");
        } else if (m.getPostsToday() == 0 && m.getAvgPosts7Days() > 5 && isLateCheck) {
            alertLevel = "CRITICAL";
            alertReasons.add("CRITICAL: Content Blackout (Zero articles by 2PM)");
        }

        // --- LEVEL 2: WARNING (AI Agent's Daily Watchlist) ---
        else if (ingestionRatio < 0.50 || successRate7Days < 70) {
            alertLevel = "WARNING";
            // AI Agent will summarize these "Quality Drops" in the nightly report.
        }

        // --- LEVEL 3: MINOR (Daily Report Details) ---
        else if (issueRatio >= 0.30 || failCount >= 10) {
            alertLevel = "NORMAL"; // Keep dashboard green, but let AI see the reason
            if (issueRatio >= 0.30)
                alertReasons.add("INFO: High issue-post ratio (" + Math.round(issueRatio * 100) + "%)");
            if (failCount >= 10)
                alertReasons.add("INFO: Frequent minor network stutters");
        }

        // 4. ADD ADDITIONAL CONTEXT FOR THE AI AGENT
        // (This ensures the AI sees EVERYTHING even if alertLevel is NORMAL)
        if (m.getPostsToday() > 0 && ingestionRatio < 0.70)
            alertReasons.add("Context: Ingestion below 70% of average");

        // Latest Run Error Check
        boolean latestRunFailed = m.getLastFailureTime() != null && m.getLastRunTime() != null &&
                m.getLastFailureTime().longValue() == m.getLastRunTime().longValue();
        if (latestRunFailed && m.getErrorMessage() != null && !m.getErrorMessage().isBlank()) {
            alertReasons.add("Latest attempt failed with error: " + m.getErrorMessage());
        }

        return SourceAnalysisResult.builder()
                .sourceName(m.getSourceName())
                .sourceUrl(m.getSourceUrl())
                .language(m.getLanguage())
                .sourceType(m.getSourceType())
                .healthScore(healthScore)
                .alertLevel(alertLevel)
                .postsToday(m.getPostsToday())
                .avgPosts7Days(m.getAvgPosts7Days())
                .ingestionRatio(ingestionRatio)
                .issuePosts(m.getIssuePosts())
                .issueRatio(issueRatio)
                .highModeratedPosts(m.getHighModeratedPosts())
                .highModRatio(highModRatio)
                .mediumModeratedPosts(m.getMediumModeratedPosts())
                .mediumModRatio(mediumModRatio)
                .successRate7Days(successRate7Days)
                .failCount(failCount)
                .totalRuns(totalRuns)
                .consecutiveFailCount(consecutiveFailCount)
                .primaryError(m.getErrorMessage())
                .lastRunTime(toLong(m.getLastRunTime()))
                .lastSuccessTime(toLong(m.getLastSucessTime()))
                .lastSuccessLabel(buildLastSuccessLabel(m))
                .alertTriggered("CRITICAL".equals(alertLevel))
                .alertReasons(alertReasons)
                .postTypeCount(m.getPostTypeCount())
                .savedByUsers(m.getSavedByUsers())
                .ingestionDeduction(ingestionDeduction)
                .issueDeduction(issueDeduction)
                .successRateDeduction(successRateDeduction)
                .consecutiveFailCountDeduction(consecutiveFailCountDeduction)
                .moderationDeduction(moderationDeduction)
                .build();
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

    private int calcConstFailCount(int consecutiveFailCount) {
        if (consecutiveFailCount >= 3)
            return 30;
        return 0; // Ignore anything less than 15!
    }

    private int calcModerationDeduction(double highModRatio, double mediumModRatio) {
        int deduction = 0;

        // --- HIGH MODERATION (Highly sensitive content: Heavier Penalty) ---
        if (highModRatio >= 0.50) {
            deduction += 20; // 50%+ high moderation is severely penalized
        } else if (highModRatio >= 0.30) {
            deduction += 15;
        } else if (highModRatio >= 0.10) {
            deduction += 10;
        }

        // --- MEDIUM MODERATION (Standard filtering: Normal Penalty) ---
        if (mediumModRatio >= 0.50) {
            deduction += 10;
        } else if (mediumModRatio >= 0.30) {
            deduction += 7;
        } else if (mediumModRatio >= 0.10) {
            deduction += 5;
        }

        // Cap the deduction so moderation alone doesn't artificially break the score
        // below 0
        return Math.min(deduction, 50);
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

    private double calculateAvgSuccess(List<SourceAnalysisResult> results) {
        return results.stream()
                .mapToInt(SourceAnalysisResult::getSuccessRate7Days)
                .average()
                .orElse(0);
    }

    private long calculateTotalPosts(List<SourceAnalysisResult> results) {
        return results.stream()
                .mapToLong(SourceAnalysisResult::getPostsToday)
                .sum();
    }

    private Object[] mapToCompactArray(SourceAnalysisResult result) {
        return new Object[] {
                result.getSourceName(), // [0]
                result.getHealthScore(), // [1]
                result.getAlertLevel(), // [2]
                result.getPostsToday(), // [3]
                result.getAvgPosts7Days(), // [4]
                result.getIngestionRatio(), // [5]
                result.getSuccessRate7Days(), // [6]
                result.getConsecutiveFailCount(), // [7]
                result.getPrimaryError(), // [8]
                result.getHighModRatio(), // [9]
                result.getMediumModRatio(), // [10]
                result.getSourceType() // [11]
        };
    }

    public CompactMonitoringReport getCompactReport(List<SourceAnalysisResult> allResults) {
        List<Object[]> problemSources = allResults.stream()
                .filter(r -> !"NORMAL".equals(r.getAlertLevel()) || r.getHealthScore() < 100)
                .map(this::mapToCompactArray)
                .toList();

        int healthyCount = allResults.size() - problemSources.size();

        return CompactMonitoringReport.builder()
                .summary(Map.of(
                        "totalSources", allResults.size(),
                        "healthyCount", healthyCount,
                        "issues", problemSources.size(),
                        "avgSuccessRate", calculateAvgSuccess(allResults),
                        "totalPostsToday", calculateTotalPosts(allResults)))
                .problemSources(problemSources)
                .build();
    }
}