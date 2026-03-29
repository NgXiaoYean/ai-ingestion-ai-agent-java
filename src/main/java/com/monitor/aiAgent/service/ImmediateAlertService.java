package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.SourceAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImmediateAlertService {

    private final EmailService emailService;

    public void sendCriticalAlert(SourceAnalysisResult result) {
        if (!"CRITICAL".equalsIgnoreCase(result.getAlertLevel()))
            return;

        StringBuilder body = new StringBuilder();
        body.append("🔴 Critical ingestion issue detected\n\n");
        body.append("Source: ").append(result.getSourceName()).append("\n");
        body.append("Health: ").append(result.getHealthScore()).append("%\n");
        body.append("Success Rate (7d): ").append(result.getSuccessRate7Days()).append("%\n");
        body.append("Posts Today: ").append(result.getPostsToday()).append(" (Avg: ").append(result.getAvgPosts7Days())
                .append(")\n");
        body.append("Failures: ").append(result.getFailCount()).append("/").append(result.getTotalRuns()).append("\n");
        body.append("Primary Error: ").append(result.getPrimaryError()).append("\n");
        body.append("Reasons:\n");

        for (String reason : result.getAlertReasons()) {
            body.append("- ").append(reason).append("\n");
        }

        emailService.sendAlert("🚨 CRITICAL: " + result.getSourceName(), body.toString());
    }
}