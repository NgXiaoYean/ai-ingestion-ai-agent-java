package com.monitor.aiAgent.service;

import com.monitor.aiAgent.dto.ProbeResult;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class FeedProbeService {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ProbeResult probe(String sourceName, String feedUrl) {
        ProbeResult.ProbeResultBuilder result = ProbeResult.builder()
                .sourceName(sourceName)
                .feedUrl(feedUrl)
                .reachable(false)
                .fetchSuccess(false)
                .xmlParsed(false)
                .parsable(false)
                .entryCount(0)
                .stage("ACCESS")
                .likelyCategory("UNKNOWN");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(feedUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int status = response.statusCode();
            HttpHeaders headers = response.headers();
            String contentType = headers.firstValue("Content-Type").orElse("unknown");
            String finalUrl = response.uri().toString();

            result.httpStatus(status)
                    .contentType(contentType)
                    .finalUrl(finalUrl);

            if (status < 200 || status >= 300) {
                return result
                        .reachable(false)
                        .stage("ACCESS")
                        .errorMessage("HTTP_" + status)
                        .likelyCategory(classifyHttpFailure(status))
                        .build();
            }

            result.reachable(true)
                    .fetchSuccess(true);

            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return result
                        .stage("FETCH")
                        .errorMessage("Empty response body")
                        .likelyCategory("EMPTY_FEED")
                        .build();
            }

            try (InputStream is = new ByteArrayInputStream(body)) {
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(is));

                int entryCount = feed.getEntries() == null ? 0 : feed.getEntries().size();

                result.xmlParsed(true)
                        .parsable(true)
                        .entryCount(entryCount);

                if (entryCount == 0) {
                    return result
                            .stage("CONTENT")
                            .errorMessage("Parsed successfully but no entries returned")
                            .likelyCategory("EMPTY_FEED")
                            .build();
                }

                return result
                        .stage("OK")
                        .likelyCategory("OK")
                        .build();
            } catch (Exception parseEx) {
                String category = classifyParseFailure(contentType, finalUrl, parseEx.getMessage());
                return result
                        .xmlParsed(false)
                        .parsable(false)
                        .stage("PARSE")
                        .errorMessage(parseEx.getMessage())
                        .likelyCategory(category)
                        .build();
            }

        } catch (java.net.http.HttpTimeoutException e) {
            return result
                    .stage("ACCESS")
                    .errorMessage("Timeout: " + e.getMessage())
                    .likelyCategory("TIMEOUT")
                    .build();
        } catch (Exception e) {
            return result
                    .stage("ACCESS")
                    .errorMessage(e.getMessage())
                    .likelyCategory("UNKNOWN")
                    .build();
        }
    }

    private String classifyHttpFailure(int status) {
        if (status == 401 || status == 403)
            return "ACCESS_BLOCKED";
        if (status == 404)
            return "NOT_FOUND";
        if (status >= 500)
            return "UPSTREAM_DOWN";
        return "HTTP_FAILURE";
    }

    private String classifyParseFailure(String contentType, String finalUrl, String error) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        String url = finalUrl == null ? "" : finalUrl.toLowerCase();
        String msg = error == null ? "" : error.toLowerCase();

        if (ct.contains("text/html"))
            return "HTML_INSTEAD_OF_RSS";
        if (url.endsWith("/") && !url.contains("feed") && !url.contains("rss"))
            return "REDIRECTED_TO_HOMEPAGE";
        if (msg.contains("xml"))
            return "INVALID_XML";
        return "PARSER_FAILURE";
    }
}