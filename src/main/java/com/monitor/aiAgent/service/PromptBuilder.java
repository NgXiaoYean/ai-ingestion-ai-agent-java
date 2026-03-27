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
        2. SILENT DEGRADATION: If ingestionRatio is low (< 0.5) but primaryError is null, check 'sourceType'.
           - If it is 'Article' or 'Video', judge this as "No posts posted by creator today".
           - If it is 'News', judge this as "Potential upstream layout change or stealth block".
        3. MODERATION ANALYSIS:
          - If a source has a high 'highModRatio', highlight this as a severe content policy violation (highly sensitive content).
          - If it has a high 'mediumModRatio', treat it as standard content filtering (e.g., spam or minor infractions).
        --------------------------------------------------

        STRICT RULES:
        - LEADERBOARD: Rank the Top 3 worst feeds in the Source Analysis section based on Health Score, 'consecutiveFailCount' (Urgency), and drop percentage,.
        - KEY CONCERN: Highlight the source with the lowest Health Score. If scores are equal, highlight the one with the most consecutive failures.
        - NO RE-CALCULATION: Trust the backend for healthScore and successRate7Days.
        - NO INVENTING: If a field is null, display "-".

        JSON FIELD REFERENCE:
        - ingestionRatio: Use this to calculate and report "Drop %".
        - healthScore: 0-100 (90+ Healthy, 70-89 Warning, <70 Critical).
        - alertReasons: The human-readable explanations for score deductions.
        - primaryError: Raw technical log. Translate this into human terms. Show both technical log and human terms error. (e.g., "403" -> Access Blocked).

        --------------------------------------------------
        📖 DEFINITIONS & GUIDELINES (Use as a baseline, but apply your own expert judgment):

        Pattern Scope Guidelines:
        - ISOLATED: Exactly 1 source has a CRITICAL or WARNING issue.
        - GROUPED: 2–5 sources have issues AND share a trait (e.g., Same Language, Error, or Type).
        - BROAD: Many sources across different languages/types are failing (but not 100%).
        - SYSTEM-WIDE: Nearly all sources failing; indicates total network/database outage.

        Decision Guidelines:
        - RETRY: Temporary "glitch" (e.g., a single Timeout).
        - MONITOR: Health score is slightly low, but data is flowing. No immediate action.
        - WARNING: Clear drop in quality or volume; human review needed today.
        - CRITICAL: Source is broken or totally empty; needs immediate fix.
        - ESCALATE: System-wide failure, or a "Grouped/Broad" pattern is detected.
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
        **Analysis**: [Analyze why this pattern is happening. If Grouped, explain the similarity (e.g., "All ZH sources are failing").]

        --------------------------------------------------
        **📈 Source Analysis (Problematic Only)**

        [For each source in sources[], ordered by your Leaderboard Judgement:]
        **📛 [sourceName]**
        **Alert Level**: [alertLevel] | **Health Score**: **[healthScore]/100**
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
        - 🔴 [sourceName]: [Major signal]
        - ⚠️ [sourceName]: [Minor signal]

        --------------------------------------------------
        **🛠️ Top Actions**
        1. [Most urgent fix based on lowest Health Score or highest consecutive fails]
        2. [Secondary priority fix or Grouped pattern resolution]
        3. [Preventive task for sources showing volume drops or high issue ratios]

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