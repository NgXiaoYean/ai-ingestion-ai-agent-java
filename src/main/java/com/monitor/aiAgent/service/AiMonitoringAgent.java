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

        // 1. Calculate the math for ALL sources (The backend does its job)
        MonitoringAnalysisReport analysisReport = analysisService.analyze(metrics);

        // --- NEW DATA DIET CODE ---
        // 2. Filter out the perfectly healthy sources to save AI tokens
        List<SourceAnalysisResult> problematicSources = analysisReport.getSources().stream()
                .filter(source -> !"HEALTHY".equalsIgnoreCase(source.getHealthLevel()))
                .collect(Collectors.toList());

        // 3. Overwrite the list so the AI only reads about the broken ones
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