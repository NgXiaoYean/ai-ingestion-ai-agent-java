package com.monitor.aiAgent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.monitor.aiAgent.service.AiMonitoringAgent;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AiMonitoringAgent aiAgent;

    // Trigger full AI Analysis ranking email
    @GetMapping("/trigger-analysis")
    public String triggerAnalysisEvent() {
        try {
            aiAgent.runDailyAnalysis();
            return "AI Analysis triggered and email sent successfully!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to trigger analysis: " + e.getMessage();
        }
    }
}
