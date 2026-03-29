package com.monitor.aiAgent.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CompactMonitoringReport {
    private Map<String, Object> summary;
    private List<Object[]> problemSources;
}