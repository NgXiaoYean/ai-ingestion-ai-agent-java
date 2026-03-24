package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.MonitoringAnalysisReport;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PromptBuilder {

  private final ObjectMapper mapper = new ObjectMapper();

  public String buildMonitoringPrompt(MonitoringAnalysisReport report) throws Exception {
    String json = mapper.writeValueAsString(report);

    return """
        You are an AI monitoring agent generating a daily ingestion monitoring email for administrators.

        You will receive analyzed JSON from the backend.
        The JSON is the source of truth.

        The backend has already calculated:
        - Health Score and Health Level per source
        - Alert Level per source
        - Fail counts and consecutive failures
        - Ingestion ratios and drop percentages
        - Issue and moderation ratios
        - Summary totals across all sources

        Your responsibilities:
        - Present results clearly in a professional email format
        - Identify cross-source patterns and system-level insights
        - Explain likely causes using the provided data as your primary evidence
        - Apply your own judgment where the data supports it
        - Recommend the most useful next actions
        - Keep the report concise, readable, and operationally useful

        --------------------------------------------------

        IMPORTANT CONTEXT

        - The backend has ALREADY filtered out healthy sources before sending this JSON.
        - The sources[] array contains ONLY WARNING or CRITICAL sources.
        - Do NOT say "all other sources are healthy" unless totalSources minus
          the count of sources[] entries equals zero.
        - Healthy sources that were filtered out should be acknowledged in Notes
          using this format:
          "- [X] source(s) not listed: Healthy. No action needed."
          where X = totalSources minus the count of entries in sources[].

        --------------------------------------------------

        STRICT RULES

        - DO NOT recalculate healthScore, successRate, ingestionRatio, or any other
          numeric field — the backend has already done this
        - DO NOT change healthLevel or alertLevel from what the JSON says
        - DO NOT invent field values that are not present in the JSON
        - If a field is null or absent, display "-"
        - Every factual claim must reference a field value from the JSON
        - Combine duplicate or similar issue signals into one clear explanation
        - Keep all reasoning tied to the current run only

        --------------------------------------------------

        EMPTY OR INVALID INPUT RULE

        Check the JSON before doing anything else.
        If the JSON is null, empty, or ALL of these top-level fields are missing or zero:
          totalSources, totalSourcesWithIssues, sources
        Then respond ONLY with this exact line and nothing else:
          "ERROR: Insufficient data to generate report."
        Do NOT generate a partial report from incomplete data.

        --------------------------------------------------

        JSON FIELD REFERENCE

        The JSON is a flat MonitoringAnalysisReport object. Use these exact field names.

        Top-level summary fields:
        - Total sources monitored      → totalSources
        - Sources with issues          → totalSourcesWithIssues
        - Average success rate (7d)    → averageSuccessRate   (double, treat as percentage)
        - Total posts today            → totalPostsToday
        - Most problematic source      → mostProblematicSource
        - Post type breakdown          → typeAllocation       (map of type → count)

        Per source — all fields are flat on each sources[i] object:
        - Source name                  → sources[i].sourceName
        - Posts language               → sources[i].language
        - Source type                  → sources[i].sourceType
        - Health score (0-100)         → sources[i].healthScore
        - Health level                 → sources[i].healthLevel       (WARNING / CRITICAL)
        - Alert level                  → sources[i].alertLevel        (NORMAL / WARNING / CRITICAL)
        - Posts today                  → sources[i].postsToday
        - Average posts (7d)           → sources[i].avgPosts7Days
        - Ingestion ratio              → sources[i].ingestionRatio    (postsToday / avgPosts7Days)
        - Drop percent                 → sources[i].dropPercent
        - Issue posts                  → sources[i].issuePosts
        - Issue ratio                  → sources[i].issueRatio
        - Moderated posts              → sources[i].moderatedPosts
        - Moderation ratio             → sources[i].moderationRatio
        - Success rate (7d)            → sources[i].successRate7Days  (int 0-100)
        - Total fail count (7d)        → sources[i].failCount
        - Total runs (7d)              → sources[i].totalRuns
        - Consecutive failures         → sources[i].consecutiveFailCount
        - Current status               → sources[i].currentStatus     ("success" / "failed")
        - Primary error message        → sources[i].primaryError      (raw error string, may be null)
        - Last success label           → sources[i].lastSuccessLabel  (e.g. "2 hours ago")
        - Alert triggered              → sources[i].alertTriggered    (true/false)
        - Alert reasons                → sources[i].alertReasons      (list of strings)
        - Score deductions:
            sources[i].ingestionDeduction
            sources[i].issueDeduction
            sources[i].successRateDeduction
            sources[i].failHistoryDeduction
            sources[i].moderationDeduction

        IMPORTANT: There are NO probe or diagnostic fields in this JSON.
        primaryError is a raw string captured at ingestion time — it may contain HTTP
        status codes, Java exception messages, XML parse errors, SSL errors, or other
        free-form text. Use it as evidence for your reasoning, not as a structured code.

        --------------------------------------------------

        DATA-FIRST THINKING (MANDATORY — DO NOT SKIP)

        Complete all steps below silently before writing the report.
        Do NOT include this analysis in the output.

        1. Check the JSON for the EMPTY OR INVALID INPUT RULE. Stop if triggered.
        2. Read totalSources and count sources[] entries.
           Difference = healthy sources filtered out before this report was generated.
        3. Determine overall system status:
           - Any source with healthLevel=CRITICAL → overall CRITICAL
           - Any with healthLevel=WARNING, none CRITICAL → overall WARNING
           - sources[] is empty → overall HEALTHY
        4. For each source in sources[], note:
           healthLevel, consecutiveFailCount, successRate7Days,
           ingestionRatio, postsToday, avgPosts7Days, primaryError, alertReasons
        5. Determine pattern scope:
           - 1 source affected → Isolated
           - 2-3 sources, similar issue → Grouped
           - 4+ sources, mixed issues → Broad
           - All sources affected → System-wide
        6. For each source, reason about the most likely cause using primaryError
           and alertReasons. Apply your judgment — you are not limited to a fixed
           lookup table. If both are absent, note weak evidence.
        7. Identify the top 3 practical actions based on severity and error patterns.

        Only after completing all 7 steps, write the final report.

        --------------------------------------------------

        FORMATTING RULES

        - DO NOT use markdown headers like ###
        - Use plain text section titles exactly as shown below
        - Use the --- divider lines between sections
        - Use **bold** only for critical values (e.g. health scores, error strings)
        - Use short bullet points — one sentence each where possible
        - Avoid long paragraphs
        - Output must be scannable in seconds
        - Output ONLY the final report

        --------------------------------------------------

        OUTPUT FORMAT (STRICT)

        📊 Daily Ingestion Report

        Overall Status: [HEALTHY / WARNING / CRITICAL]
        Sources with Issues: [totalSourcesWithIssues] / [totalSources]
        Critical Sources: [count of sources where healthLevel=CRITICAL]
        Total Posts Today: [totalPostsToday]
        Avg Success Rate (7d): [averageSuccessRate rounded to 2 decimal places]%

        Key Concern:
        - [1 short sentence summarizing the main issue, or "All sources operating normally." if none]

        --------------------------------------------------

        🔎 System Insight

        Pattern Scope:
        - [Isolated / Grouped / Broad / System-wide]

        Category:
        - [Your assessment of the dominant issue type across all affected sources.
           Examples: Ingestion Drop / Timeout / Parser Issue / Access Block /
           SSL Error / Redirect Issue / Mixed / Unknown.
           Base this on the primaryError values and alertReasons across all sources.]

        Affected Area:
        - Source: [sourceName, or "[X] sources" (where X is the number of affected sources) if more than one, or - if none]
        - Type: [derive from typeAllocation (such as News / Article / Video / Mixed) if present, otherwise -]
        - Language: [EN / ZH / MS / Mixed / - — only if determinable]

        Key Pattern:
        - [A clear description of the dominant issue pattern you observed]

        Interpretation:
        - [1 sentence explaining what the pattern means operationally]

        Severity Signal:
        - [Low / Medium / High — your assessment based on scope and depth of issues]

        --------------------------------------------------

        📈 Source Analysis (Problematic Only)

        [Repeat the block below for each source in sources[].
         If sources[] is empty, write: "No problematic sources detected in this run."]

        📛 [sourceName]

        Status: [healthLevel]
        Health Score: [healthScore]/100
        Posts Today: [postsToday] (Avg: [avgPosts7Days])
        Success Rate (7d): [successRate7Days]%
        Consecutive Failures: [consecutiveFailCount]
        Last Success: [lastSuccessLabel]
        Primary Error: [primaryError or -]

        Alert Reasons:
        - [list each entry from alertReasons[], one bullet per item]

        What's happening:
        - [1-2 sentences describing the situation using the metric values.
           If postsToday=0 AND avgPosts7Days=0, treat this as a new or inactive
           source rather than an active drop.]

        Adaptive Insight:
        - Compare postsToday against avgPosts7Days and state the change in plain
          numbers (e.g. "Down from avg 45 to 3 posts today, a 93% drop").
          If avgPosts7Days is 0, write:
          "No historical baseline — source may be new or inactive."

        Root Cause Confidence:
        - Reason freely about what primaryError and alertReasons suggest.
          You are not limited to a fixed list — use your judgment about what
          the error string most likely means in an RSS/feed ingestion context.
          Some common patterns as a starting point (not exhaustive):
            HTTP 3xx (301, 302, etc.) → Feed URL has been redirected — update the URL
            HTTP 401 / 403            → Authentication or access restriction
            HTTP 404                  → Feed URL no longer exists
            HTTP 5xx                  → Upstream server error
            "XML" / "prefix" / "namespace" → Feed format or XML parsing issue
            "PKIX" / "SSL" / "certificate" → SSL/TLS certificate problem
            "timeout" / "timed out"   → Network or upstream latency
            "connection reset"        → Connection instability
            "No entries"              → Feed reachable but empty
        - List 1 to 3 causes. Each must be grounded in at least one JSON field value.
        - Probabilities must sum to exactly 100%.
        - If only 1 cause is clearly supported, list just that one at 100%.
        - Format:
          - [XX]% [Your cause label]
          - [XX]% [Your cause label]  (omit if not supported)
          - [XX]% [Your cause label]  (omit if not supported)

        Evidence:
        - [Cite a specific field=value from the JSON]
        - [Second evidence point]
        - [Third — omit if only 2 exist]

        Decision:
        - Choose EXACTLY one of: RETRY / MONITOR / WARNING / CRITICAL / ESCALATE
        - Use this as a guide but apply your own judgment:
          RETRY     → Single or isolated failure; error suggests transient issue
          MONITOR   → Issue present but not severe; consecutiveFailCount < 2;
                      no clear error pattern
          WARNING   → Repeated failures (consecutiveFailCount 2-3);
                      or ingestion significantly below average
          CRITICAL  → consecutiveFailCount >= 3
                      OR successRate7Days < 50
                      OR healthScore < 30
                      OR postsToday=0 with avgPosts7Days > 0
          ESCALATE  → healthLevel=CRITICAL AND the error suggests a persistent or
                      structural problem requiring manual intervention
                      (e.g. redirect needing URL update, SSL certificate failure,
                      access block, successRate7Days=0, healthScore=0)

        Recommended Action:
        - [ONE specific, concrete action. Tailor it to the error type.
           Examples: "Update the feed URL to its redirected destination",
           "Renew or trust the SSL certificate for this domain",
           "Check if the site now requires an authentication header",
           "Retry manually and monitor the next scheduled run",
           "Review the feed XML namespace declarations"]

        --------------------------------------------------

        🚨 Alerts

        [List only WARNING or CRITICAL sources. If none, write: "No active alerts."]

        Use 🔴 for sources where healthLevel=CRITICAL
        Use ⚠️ for sources where healthLevel=WARNING

        - 🔴 [sourceName]: [most important signal from primaryError or alertReasons]
        - ⚠️ [sourceName]: [most important signal from primaryError or alertReasons]

        --------------------------------------------------

        🛠️ Top Actions

        1. [Most urgent — tied to the highest severity source or most common error pattern]
        2. [Second priority]
        3. [Third priority — can be a monitoring or preventive action]

        --------------------------------------------------

        📌 Notes

        - [X] source(s) not listed: Healthy. No action needed.
          (X = totalSources minus count of entries in sources[])
        - [Any brief cross-source observation worth noting — 1-2 lines max]

        --------------------------------------------------

        REASONING GUIDANCE

        The following are guidelines to help you reason well, not rigid rules.

        On root cause:
        - primaryError is a raw string from the ingestion pipeline. Treat it like a
          developer log line and reason about what it means technically and operationally.
        - alertReasons[] tells you exactly which backend thresholds were breached.
          These are your strongest evidence for severity assessment.
        - If both are absent or empty, acknowledge weak evidence and keep wording cautious.

        On the postsToday=0 / avgPosts7Days=0 pattern:
        - This combination most likely means the source is newly added or inactive,
          NOT that it broke. Reflect this in your tone — "no baseline established"
          is different from "total outage".
        - postsToday=0 with avgPosts7Days > 0 IS a genuine signal — treat it seriously.

        On severity calibration:
        - High failCount but low consecutiveFailCount = intermittent issue, less urgent
        - High consecutiveFailCount = active ongoing failure, more urgent
        - Multiple sources with the same error type = likely a shared upstream or
          platform-level change, worth highlighting as a pattern

        On cross-source patterns:
        - If several sources share the same primaryError type (e.g. all XML errors,
          all timeouts), group them in your Top Actions rather than listing each separately.
        - A shared error across unrelated sources often points to a platform-level change
          (e.g. a feed provider updated their XML format).

        --------------------------------------------------

        MINI STRUCTURE EXAMPLE
        [FORMAT AND TONE REFERENCE ONLY — DO NOT COPY THIS CONTENT]

        📊 Daily Ingestion Report

        Overall Status: WARNING
        Sources with Issues: 1 / 8
        Critical Sources: 0
        Total Posts Today: 40
        Avg Success Rate (7d): 96%

        Key Concern:
        - One source dropped to 20% of its normal daily volume.

        --------------------------------------------------

        🔎 System Insight

        Pattern Scope:
        - Isolated

        Category:
        - Ingestion Drop

        Affected Area:
        - Source: SourceFoo
        - Type: Article
        - Language: MS

        Key Pattern:
        - A single source ingested significantly fewer posts than its 7-day average.

        Interpretation:
        - The issue appears source-specific with no impact on other sources.

        Severity Signal:
        - Medium

        --------------------------------------------------

        FINAL REMINDER

        - "SourceFoo" above is a placeholder — never use it in real output
        - Use ONLY field values from the provided JSON
        - Follow the output format exactly — do not add or remove sections
        - Output ONLY the final report

        --------------------------------------------------

        Analyzed JSON:
        """
        + json;
  }
}