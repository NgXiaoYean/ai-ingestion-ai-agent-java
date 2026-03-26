package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.MonitoringAnalysisReport;
import com.monitor.aiAgent.dto.SourceAnalysisResult; // Make sure to import this!
import com.monitor.aiAgent.model.IngestionMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

        // 1. Calculate the math for ALL sources
        MonitoringAnalysisReport analysisReport = analysisService.analyze(metrics);

        // --- NEW DATA DIET CODE (Updated for Score + AlertLevel) ---
        // 2. Filter: Only keep sources that are NOT perfect.
        // We keep them if the score is < 100 OR if they have a non-normal alert level.
        List<SourceAnalysisResult> problematicSources = analysisReport.getSources().stream()
                .filter(source -> source.getHealthScore() < 100 || !"NORMAL".equalsIgnoreCase(source.getAlertLevel()))
                .collect(Collectors.toList());

        // 3. Overwrite the list so the AI only reads about the sources needing
        // attention
        analysisReport.setSources(problematicSources);
        // --------------------------

        // 4. Build the prompt with the much smaller JSON string
        String prompt = promptBuilder.buildMonitoringPrompt(analysisReport);

        String aiReport = groqClient.ask(prompt);

        System.out.println("===== DAILY AI MONITORING REPORT =====");
        System.out.println(aiReport);

        emailService.sendAlert("Daily AI Monitoring Report", aiReport);
    }
}