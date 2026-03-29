package com.monitor.aiAgent.service;

import com.monitor.aiAgent.config.NewsConfig;
import com.monitor.aiAgent.dto.ProbeResult;
import com.monitor.aiAgent.dto.SourceAnalysisResult;
import com.monitor.aiAgent.model.DailyStat;
import com.monitor.aiAgent.model.FailureSample;
import com.monitor.aiAgent.model.IngestionMetrics;
import com.monitor.aiAgent.model.News;
import com.monitor.aiAgent.repository.ArticleRepository;
import com.monitor.aiAgent.repository.MetricsRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsConfig newsConfig;
    private final ArticleRepository newsRepository;
    private final MetricsRepository metricsRepository;
    private final FeedNormalizer normalizer;
    private final MonitoringAnalysisService monitoringAnalysisService;
    private final EmailService emailService;
    private final FeedProbeService feedProbeService; // <-- ADDED THIS

    public List<News> fetchAll() {
        List<News> allNews = new ArrayList<>();

        for (NewsConfig.Source source : newsConfig.getSources()) {
            for (NewsConfig.Feed feed : source.getFeeds()) {

                boolean feedSuccess = false;
                String feedError = null;
                ProbeResult probeResult = null;

                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(feed.getUrl()))
                            .timeout(Duration.ofSeconds(15))
                            .header("User-Agent", "Mozilla/5.0")
                            .GET()
                            .build();

                    HttpResponse<InputStream> response = client.send(request,
                            HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() != 200) {
                        feedError = "HTTP_" + response.statusCode();
                    } else {
                        SyndFeedInput input = new SyndFeedInput();
                        SyndFeed rss = input.build(new XmlReader(response.body()));

                        int entryCount = rss.getEntries() == null ? 0 : rss.getEntries().size();
                        if (entryCount == 0) {
                            feedError = "No entries returned";
                        } else {
                            for (SyndEntry entry : rss.getEntries()) {
                                News news = normalizer.normalize(entry, source, feed);
                                if (news == null)
                                    continue;

                                try {
                                    newsRepository.save(news);
                                    allNews.add(news);
                                } catch (Exception e) {
                                    // Duplicate or error saving
                                }
                            }
                            feedSuccess = true;
                        }
                    }

                } catch (java.net.http.HttpTimeoutException e) {
                    feedError = "TIMEOUT";
                } catch (Exception e) {
                    feedError = e.getMessage();
                } finally {
                    // --- ADDED PROBE LOGIC FOR FAILURES ---
                    if (!feedSuccess) {
                        probeResult = feedProbeService.probe(source.getName(), feed.getUrl());
                        System.out.println("Probe result: " + probeResult);
                    }
                    // --------------------------------------

                    recordFeedRun(source.getName(), feed.getUrl(), feedSuccess, feedError);
                }
            }
        }

        return allNews;
    }

    // --- FULLY UPDATED TO MATCH ARTICLE SERVICE ---
    private void recordFeedRun(String sourceName, String feedUrl, boolean success, String errorMessage) {
        IngestionMetrics metrics = metricsRepository.findBySourceName(sourceName)
                .orElseGet(() -> {
                    IngestionMetrics m = new IngestionMetrics();
                    m.setSourceName(sourceName);
                    m.setDailyStats(new ArrayList<>());
                    m.setRecentFailures(new ArrayList<>());
                    return m;
                });

        if (metrics.getDailyStats() == null) {
            metrics.setDailyStats(new ArrayList<>());
        }
        if (metrics.getRecentFailures() == null) {
            metrics.setRecentFailures(new ArrayList<>());
        }

        DailyStat today = getOrCreateTodayStat(metrics);
        today.setTotal(today.getTotal() + 1);

        long now = System.currentTimeMillis();
        metrics.setLastRunTime(now);

        if (success) {
            metrics.setStatus("success");
            metrics.setLastSucessTime(now);
            metrics.setErrorMessage(null); // Clear out old errors
        } else {
            metrics.setStatus("failed");
            metrics.setLastFailureTime(now);
            metrics.setErrorMessage(errorMessage); // Save the exact error

            today.setFailed(today.getFailed() + 1);

            String category = classifyFailure(errorMessage);

            if (today.getFailureCategoryCounts() == null) {
                today.setFailureCategoryCounts(new HashMap<>());
            }

            today.getFailureCategoryCounts().merge(category, 1, Integer::sum);

            FailureSample sample = new FailureSample();
            sample.setTimestamp(now);
            sample.setCategory(category);
            sample.setMessage(errorMessage);
            sample.setUrl(feedUrl);
            metrics.getRecentFailures().add(sample);

            if (metrics.getRecentFailures().size() > 20) {
                metrics.getRecentFailures().remove(0);
            }
        }

        today.setSuccess(today.getTotal() - today.getFailed());

        trimToLast7Days(metrics.getDailyStats());

        metricsRepository.save(metrics);
    }

    private DailyStat getOrCreateTodayStat(IngestionMetrics metrics) {
        String todayStr = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).toString();
        for (DailyStat stat : metrics.getDailyStats()) {
            if (todayStr.equals(stat.getDate())) {
                if (stat.getFailureCategoryCounts() == null) {
                    stat.setFailureCategoryCounts(new HashMap<>());
                }
                return stat;
            }
        }
        DailyStat stat = new DailyStat();
        stat.setDate(todayStr);
        stat.setTotal(0);
        stat.setFailed(0);
        stat.setSuccess(0);
        stat.setFailureCategoryCounts(new HashMap<>());
        metrics.getDailyStats().add(stat);
        return stat;
    }

    // --- ADDED THIS TO KEEP THE DATABASE CLEAN ---
    private void trimToLast7Days(List<DailyStat> stats) {
        stats.sort(Comparator.comparing(DailyStat::getDate));
        while (stats.size() > 7) {
            stats.remove(0);
        }
    }

    private String classifyFailure(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank())
            return "UNKNOWN";
        String msg = errorMessage.toLowerCase();
        if (msg.contains("connection reset"))
            return "CONNECTION_RESET";
        if (msg.contains("timed out") || msg.contains("timeout"))
            return "TIMEOUT";
        if (msg.contains("403"))
            return "HTTP_403";
        if (msg.contains("404"))
            return "HTTP_404";
        if (msg.contains("500"))
            return "HTTP_500";
        if (msg.contains("no entries"))
            return "EMPTY_FEED";
        if (msg.contains("xml"))
            return "XML_PARSE_ERROR";
        return "UNKNOWN";
    }

    public void sendCombinedCriticalAlert() {
        List<IngestionMetrics> currentMetrics = metricsRepository.findAll();
        List<String> criticalBlocks = new ArrayList<>();

        for (IngestionMetrics metrics : currentMetrics) {
            SourceAnalysisResult result = monitoringAnalysisService.analyzeSingle(metrics);

            if ("CRITICAL".equalsIgnoreCase(result.getAlertLevel())) {
                StringBuilder block = new StringBuilder();
                block.append("Source: ").append(result.getSourceName()).append("\n");
                block.append("Health: ").append(result.getHealthScore()).append("%\n");
                block.append("Success Rate (7d): ").append(result.getSuccessRate7Days()).append("%\n");
                block.append("Posts Today: ").append(result.getPostsToday())
                        .append(" (Avg: ").append(result.getAvgPosts7Days()).append(")\n");
                block.append("Failures: ").append(result.getFailCount())
                        .append("/").append(result.getTotalRuns()).append("\n");
                block.append("Last Success: ")
                        .append(result.getLastSuccessLabel() == null ? "Unknown" : result.getLastSuccessLabel())
                        .append("\n");
                block.append("Primary Error: ")
                        .append(result.getPrimaryError() == null ? "-" : result.getPrimaryError()).append("\n");
                block.append("Reasons:\n");

                for (String reason : result.getAlertReasons()) {
                    block.append("- ").append(reason).append("\n");
                }

                criticalBlocks.add(block.toString());
            }
        }

        if (!criticalBlocks.isEmpty()) {
            StringBuilder body = new StringBuilder();
            body.append("🚨 Combined Critical Ingestion Alert\n\n");
            body.append("Critical sources detected: ").append(criticalBlocks.size()).append("\n\n");

            for (String block : criticalBlocks) {
                body.append(block).append("\n");
            }

            emailService.sendAlert(
                    "🚨 Critical Ingestion Alert - " + criticalBlocks.size() + " Source(s)",
                    body.toString());
        }
    }
}