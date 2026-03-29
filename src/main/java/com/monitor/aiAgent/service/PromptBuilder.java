package com.monitor.aiAgent.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.aiAgent.dto.CompactMonitoringReport;

@Service
public class PromptBuilder {

  private final ObjectMapper mapper = new ObjectMapper();

  public String buildMonitoringPrompt(CompactMonitoringReport report) throws Exception {
    String reportJson = mapper.writeValueAsString(report);

    return """
        Role: Expert AI Systems Engineer. Analyze the COMPACT JSON to output an operational report.

        📊 SCHEMA (Positional Arrays in 'problemSources'):
        [0] sourceName, [1] healthScore, [2] alertLevel, [3] postsToday, [4] avgPosts7Days,
        [5] ingestionRatio, [6] successRate7Days, [7] consecutiveFailCount, [8] primaryError,
        [9] highModRatio, [10] mediumModRatio, [11] sourceType

        🧠 EXPERT AUTONOMY & GUIDELINES:
        You are a senior engineer. The rules below are your baseline, but you MUST use your own technical intuition. If you spot a critical anomaly, a cascading failure, or a better troubleshooting step not explicitly listed here, use your judgment to report it.

        1. DROP %: Calculate as (1.0 - [5]) * 100.
        2. SILENT DEGRADATION: If [5] < 0.5 & [8] is null:
           - [11] in ('ARTICLE', 'ARTICLEVIDEO') -> "Normal: Creator inactive today."
           - [11] in ('NEWS', 'NEWSVIDEO') -> "CRITICAL: Stealth Block / Layout Change."
        3. MODERATION: [9] >= 0.20 -> Severe violation. [10] >= 0.30 -> Standard filtering.
        4. THRESHOLDS: Detail a source ONLY IF: [1] < 80, OR [7] >= 3, OR Drop >= 50%, OR [9] >= 0.20 (Or if your expert judgment deems it critical).
        5. PRIORITIZE: Technical errors > Content fluctuations.
        6. NO INVENTING: If field is null, display "-". Trust backend math.

        📖 SCOPE & ACTION DEFINITIONS (Apply your judgment):
        - Scope: ISOLATED (1 source), GROUPED (2-5 shared trait), BROAD (many), SYSTEM-WIDE (almost all).
        - Action: RETRY (glitch), MONITOR (flowing but low score), WARNING (drop), CRITICAL (broken), ESCALATE (system-wide).

        ==================================================
        OUTPUT FORMAT (STRICT MARKDOWN)

        **📊 Daily Ingestion Report**

        **Overall Status**: [Your Judgement: HEALTHY / WARNING / CRITICAL]
        **Sources with Issues**: [summary.issues] / [summary.totalSources]
        **Critical Sources**: [Count where [1] < 70 or [2] == 'CRITICAL']
        **Total Posts Today**: [summary.totalPostsToday]
        **Avg Success Rate (7d)**: [summary.avgSuccessRate]%

        **Key Concern**:
        - [1-2 sentences highlighting your primary technical concern based on the data.]

        ---
        **🔎 System Insight**
        **Pattern Scope**: [Isolated / Grouped / Broad / System-wide]
        **Category**: [e.g., Ingestion Drop / SSL Error / Access Block]
        **Analysis**: [Explain why this is happening. Point out any hidden patterns you detect.]

        ---
        **📈 Source Analysis (Problematic Only)**
        [Rank Top 3 worst feeds ordered by your technical assessment of their severity]

        **📛 [0]**
        **Alert Level**: [2] | **Health Score**: **[1]/100**
        **Stats**: [3] posts (Avg: [4]) | **Drop**: [XX]%
        **Primary Error**: [8] (Translate raw log to human terms)

        **What's Happening**: [1-2 sentences explaining the root issue.]

        **Root Cause Confidence**:
        - [XX]% [Cause A]
        - [XX]% [Cause B]

        **Decision**: [RETRY / MONITOR / WARNING / CRITICAL / ESCALATE]
        **Recommended Action**: [Your expert recommendation for a technical fix.]

        ---
        **🚨 Alerts**
        - 🔴 [[0]]: [Major signal]
        - ⚠️ [[0]]: [Minor signal]

        ---
        **🛠️ Top Actions**
        1. [Most urgent fix]
        2. [Secondary priority fix]
        3. [Preventive task]

        ---
        **📌 Notes**
        - [summary.healthyCount] source(s) not listed: Healthy. No action needed.
        - [Your final cross-source observation or architectural thought.]

        Analyzed JSON:
        """
        + reportJson;
  }
}