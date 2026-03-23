package com.monitor.aiAgent.dto;

import lombok.Data;

@Data
public class AiSourceInsight {
    private String sourceName;
    private String likelyCause;
    private String recommendedAction;
    private String operationalPriority; // IMMEDIATE / TODAY / MONITOR
}