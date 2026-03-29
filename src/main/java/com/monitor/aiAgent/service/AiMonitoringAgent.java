package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.CompactMonitoringReport;
import com.monitor.aiAgent.dto.MonitoringAnalysisReport;
import com.monitor.aiAgent.model.IngestionMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiMonitoringAgent {

    private final MetricsFetchService fetchService;
    private final MonitoringAnalysisService analysisService;
    private final PromptBuilder promptBuilder;
    private final GroqClient groqClient;
    private final EmailService emailService;

    public void runDailyAnalysis() throws Exception {
        List<IngestionMetrics> metrics = fetchService.getTodayMetrics();

        MonitoringAnalysisReport fullReport = analysisService.analyze(metrics);

        CompactMonitoringReport compactReport = analysisService.getCompactReport(fullReport.getSources());

        String prompt = promptBuilder.buildMonitoringPrompt(compactReport);

        String aiReport = groqClient.ask(prompt);

        System.out.println("===== DAILY AI MONITORING REPORT =====");
        System.out.println(aiReport);

        emailService.sendAlert("Daily AI Monitoring Report", aiReport);
    }
}