package com.monitor.aiAgent.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.aiAgent.dto.MonitoringAnalysisReport;

@Service
public class PromptBuilder {

  private final ObjectMapper mapper = new ObjectMapper();

  public String buildMonitoringPrompt(MonitoringAnalysisReport report) throws Exception {
    String json = mapper.writeValueAsString(report);

    return """
        You are an Expert AI Systems Engineer. Your job is to ANALYZE the following JSON data and provide a high-level operational report for administrators.

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
        🧠 EXPERT JUDGEMENT & CALCULATION RULES
        Before writing, perform these internal steps:
        1. DERIVE DROP PERCENT: Calculate the volume loss using (1.0 - ingestionRatio) * 100.
           - Example: 0.20 ratio = 80% drop. Report this as a percentage for readability.
        2. BUSINESS IMPACT: Use 'savedByUsers' to judge priority. A failure on a source with 500+ saves is more critical than one with 0 saves.
        3. SILENT DEGRADATION: If ingestionRatio is low (< 0.5) but primaryError is null, judge this as a "Potential Content Filtering" or "Upstream Quiet Period."
        4. DATA AUDIT: (totalSources - sources.size()) = Healthy sources. Acknowledge these in Notes.
        --------------------------------------------------

        STRICT RULES
        - LEADERBOARD: Rank the Top 3 worst feeds in the Source Analysis section based on Health Score, 'savedByUsers' (Impact), and 'consecutiveFailCount' (Urgency).
        - NO RE-CALCULATION: Trust the backend for healthScore and successRate7Days.
        - NO INVENTING: If a field is null, display "-".

        JSON FIELD REFERENCE:
        - ingestionRatio: Use this to calculate and report "Drop %".
        - healthScore: 0-100 (90+ Healthy, 70-89 Warning, <70 Critical).
        - alertReasons: The human-readable explanations for score deductions.
        - savedByUsers: The number of users following this feed (Impact metric).
        - primaryError: Raw technical log. Translate this into human terms (e.g., "403" -> Access Blocked).

        --------------------------------------------------
        OUTPUT FORMAT (STRICT)

        **📊 Daily Ingestion Report**

        **Overall Status**: [Your Judgement: HEALTHY / WARNING / CRITICAL]
        **Sources with Issues**: [totalSourcesWithIssues] / [totalSources]
        **Critical Sources**: [Count of sources with healthScore < 70 or alertLevel=CRITICAL]
        **Total Posts Today**: [totalPostsToday]
        **Avg Success Rate (7d)**: [averageSuccessRate]%

        **Key Concern**:
        - [1-2 sentence executive summary. Highlight the highest impact failure based on savedByUsers.]

        --------------------------------------------------
        **🔎 System Insight**

        **Pattern Scope**: [Isolated / Grouped / Broad / System-wide]
        **Category**: [e.g., Ingestion Drop / SSL Error / Access Block]
        **Interpretation**: [What does this mean for the platform today?]
        **Severity Signal**: [Low / Medium / High]

        --------------------------------------------------
        **📈 Source Analysis (Problematic Only)**

        [For each source in sources[], ordered by your Leaderboard Judgement:]
        **📛 [sourceName]**
        **Alert Level**: [alertLevel] | **Health Score**: **[healthScore]/100**
        **Impact**: [savedByUsers] users affected
        **Stats**: [postsToday] posts (Avg: [avgPosts7Days]) | **Calculated Drop**: [XX]%
        **Last Success**: [lastSuccessLabel]
        **Primary Error**: [primaryError]

        **What's Happening**:
        - [2 sentences of analysis. Connect the drop, error, and last success time.]

        **Root Cause Confidence**:
        - [XX]% [Likely Cause A]
        - [XX]% [Likely Cause B]
        (Sum to 100%. Use your technical judgment to translate the primaryError.)

        **Decision**: [RETRY / MONITOR / WARNING / CRITICAL / ESCALATE]
        **Recommended Action**: [One specific technical step]

        --------------------------------------------------
        **🚨 Alerts**
        - 🔴 [sourceName]: [Major signal + user impact]
        - ⚠️ [sourceName]: [Minor signal]

        --------------------------------------------------
        **🛠️ Top Actions**
        1. [Most urgent fix for high-impact sources]
        2. [Secondary priority]
        3. [Preventive task]

        --------------------------------------------------
        **📌 Notes**
        - [X] source(s) not listed: Healthy. No action needed.
        - [Final cross-source observation or "System is stable outside of these issues"]

        --------------------------------------------------
        Analyzed JSON:
        """
        + json;
  }
}