package com.monitor.aiAgent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProbeResult {
    private String sourceName;
    private String feedUrl;

    private boolean reachable;
    private Integer httpStatus;
    private String contentType;
    private String finalUrl;

    private boolean fetchSuccess;
    private boolean xmlParsed;
    private boolean parsable;
    private Integer entryCount;

    private String stage; // ACCESS / FETCH / PARSE / CONTENT
    private String errorMessage;
    private String likelyCategory; // ACCESS_BLOCKED / TIMEOUT / PARSER / EMPTY_FEED / REDIRECT / UNKNOWN
}