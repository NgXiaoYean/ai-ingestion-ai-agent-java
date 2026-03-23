package com.monitor.aiAgent.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.monitor.aiAgent.service.AiMonitoringAgent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AiAgentScheduler {

    private final AiMonitoringAgent aiAgent;

    // Afternoon monitoring
    @Scheduled(cron = "0 0 15 * * *")
    public void afternoonCheck() throws Exception {
        aiAgent.runDailyAnalysis();
    }

    // End-of-day summary
    @Scheduled(cron = "0 50 23 * * *")
    public void endOfDayReport() throws Exception {
        aiAgent.runDailyAnalysis();
    }
}
