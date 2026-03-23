package com.monitor.aiAgent.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiInsightReport {
    private String dailyOpsInsight;
    private String topPrioritySource;
    private List<AiSourceInsight> sourceInsights;
}