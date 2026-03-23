package com.monitor.aiAgent.service;

import com.monitor.aiAgent.config.NewsConfig;
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
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsConfig newsConfig;
    private final ArticleRepository newsRepository;
    private final MetricsRepository metricsRepository;
    private final FeedNormalizer normalizer;

    public List<News> fetchAll(String language) {
        List<News> allNews = new ArrayList<>();

        for (NewsConfig.Source source : newsConfig.getSources()) {
            for (NewsConfig.Feed feed : source.getFeeds()) {
                if (!feed.getLanguage().equalsIgnoreCase(language)) {
                    continue;
                }

                boolean feedSuccess = false;
                String feedError = null;

                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(feed.getUrl()))
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
                                if (news == null) continue;

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

                } catch (Exception e) {
                    feedError = e.getMessage();
                } finally {
                    recordFeedRun(source.getName(), feed.getUrl(), feedSuccess, feedError);
                }
            }
        }

        return allNews;
    }

    private void recordFeedRun(String sourceName, String feedUrl, boolean success, String errorMessage) {
        IngestionMetrics metrics = metricsRepository.findBySourceName(sourceName)
                .orElseGet(() -> {
                    IngestionMetrics m = new IngestionMetrics();
                    m.setSourceName(sourceName);
                    m.setDailyStats(new ArrayList<>());
                    m.setRecentFailures(new ArrayList<>());
                    return m;
                });

        DailyStat today = getOrCreateTodayStat(metrics);
        today.setTotal(today.getTotal() + 1);

        if (!success) {
            today.setFailed(today.getFailed() + 1);
            String category = classifyFailure(errorMessage);
            if (today.getFailureCategoryCounts() == null) {
                today.setFailureCategoryCounts(new HashMap<>());
            }
            today.getFailureCategoryCounts().merge(category, 1, Integer::sum);

            FailureSample sample = new FailureSample();
            sample.setTimestamp(System.currentTimeMillis());
            sample.setCategory(category);
            sample.setMessage(errorMessage);
            sample.setUrl(feedUrl);
            metrics.getRecentFailures().add(sample);

            if (metrics.getRecentFailures().size() > 20) {
                metrics.getRecentFailures().remove(0);
            }
        }

        today.setSuccess(today.getTotal() - today.getFailed());
        metricsRepository.save(metrics);
    }

    private DailyStat getOrCreateTodayStat(IngestionMetrics metrics) {
        String todayStr = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).toString();
        for (DailyStat stat : metrics.getDailyStats()) {
            if (todayStr.equals(stat.getDate())) {
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

    private String classifyFailure(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) return "UNKNOWN";
        String msg = errorMessage.toLowerCase();
        if (msg.contains("connection reset")) return "CONNECTION_RESET";
        if (msg.contains("timed out") || msg.contains("timeout")) return "TIMEOUT";
        if (msg.contains("403")) return "HTTP_403";
        if (msg.contains("404")) return "HTTP_404";
        if (msg.contains("500")) return "HTTP_500";
        if (msg.contains("no entries")) return "EMPTY_FEED";
        if (msg.contains("xml")) return "XML_PARSE_ERROR";
        return "UNKNOWN";
    }
}
