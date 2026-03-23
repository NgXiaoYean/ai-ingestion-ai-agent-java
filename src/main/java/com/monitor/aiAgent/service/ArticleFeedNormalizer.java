package com.monitor.aiAgent.service;

import com.monitor.aiAgent.config.ArticleConfig;
import com.monitor.aiAgent.model.News;
import com.monitor.aiAgent.model.NewsType;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ArticleFeedNormalizer {

    private static final Charset WINDOWS_1252 = Charset.forName("Windows-1252");
    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile(
            "Ã.|Â.|â€|â€™|â€œ|â€|â€“|â€”|â€¦|â€¢|â„¢|â‚¬");

    public News normalize(
            SyndEntry entry,
            ArticleConfig.Source source,
            ArticleConfig.Feed feed) {
        String title = sanitizeTitle(entry.getTitle());
        String link = safe(entry.getLink());

        String image = extractImage(entry, source);
        if (image == null || image.isBlank()) {
            return null;
        }
        String content = extractDescription(entry);
        Long publishedAt = extractPublishedAt(entry);
        String author = extractAuthor(entry);

        return News.builder()
                .id(hash(source.getName(), link))
                .recordId(UUID.randomUUID().toString())
                .title(title)
                .imageUrl(image)
                .content(content)
                .author(author)
                .sourceName(source.getName())
                .sourceId(source.getId())
                .sourceImage(source.getImage())
                .sourceURL(source.getDomainUrl())
                .category(feed.getCategory())
                .link(link)
                .language(feed.getLanguage())
                .rssLink(feed.getUrl())
                .publishedAt(publishedAt)
                .rssType("rss")
                .status("PUBLISHED")
                .type(NewsType.ARTICLE)
                .createdAt(Instant.now().toEpochMilli())
                .build();
    }

    private String sanitizeTitle(String raw) {
        if (raw == null)
            return "";
        String decoded = StringEscapeUtils.unescapeHtml4(raw);
        String textOnly = Jsoup.parse(decoded).text();
        textOnly = textOnly.replaceAll("\\s+", " ").trim();
        String fixed = fixMojibake(textOnly);
        fixed = fixed.replace("\uFFFD", " ");
        fixed = fixed.replaceAll("[\\u0000-\\u001F\\u007F]", " ");
        fixed = fixed.replaceAll("[\\u0080-\\u009F]", " ");
        fixed = fixed.replaceAll("\\s+", " ").trim();
        return fixed;
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() == null)
            return "";
        String raw = entry.getDescription().getValue();
        String decoded = StringEscapeUtils.unescapeHtml4(raw);
        Document doc = Jsoup.parse(decoded);
        doc.select("img, figure").remove();
        doc.select("script, style").remove();
        doc.select("p:contains(The post)").remove();
        return doc.text().trim();
    }

    private String extractAuthor(SyndEntry entry) {
        if (entry == null)
            return "";
        String author = entry.getAuthor();
        if (author == null)
            return "";
        author = author.trim();
        return author.isEmpty() ? "" : author;
    }

    private String extractImage(SyndEntry entry, ArticleConfig.Source source) {
        MediaEntryModule mediaModule = (MediaEntryModule) entry.getModule(MediaEntryModule.URI);
        if (mediaModule != null) {
            if (mediaModule.getMetadata() != null
                    && mediaModule.getMetadata().getThumbnail() != null
                    && mediaModule.getMetadata().getThumbnail().length > 0) {
                Thumbnail thumb = mediaModule.getMetadata().getThumbnail()[0];
                if (thumb.getUrl() != null) {
                    return cleanImageUrl(thumb.getUrl().toString(), source);
                }
            }
            if (mediaModule.getMediaContents() != null) {
                for (MediaContent mc : mediaModule.getMediaContents()) {
                    if (mc.getReference() != null) {
                        return cleanImageUrl(mc.getReference().toString(), source);
                    }
                }
            }
        }

        if (entry.getEnclosures() != null) {
            for (SyndEnclosure enc : entry.getEnclosures()) {
                String url = enc.getUrl();
                String type = enc.getType();
                if (url == null || url.isBlank())
                    continue;
                if (type != null && type.startsWith("image")) {
                    return cleanImageUrl(url, source);
                }
                if (url.matches("(?i).+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?")) {
                    return cleanImageUrl(url, source);
                }
                if (isImageUrlConfirmed(url)) {
                    return cleanImageUrl(url, source);
                }
            }
        }

        if (entry.getLinks() != null) {
            for (SyndLink link : entry.getLinks()) {
                if ("enclosure".equalsIgnoreCase(link.getRel()) && link.getHref() != null) {
                    if (link.getType() != null && link.getType().startsWith("image")) {
                        return cleanImageUrl(link.getHref(), source);
                    }
                    if (link.getHref().matches("(?i).+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$")
                            && isImageUrlConfirmed(link.getHref())) {
                        return cleanImageUrl(link.getHref(), source);
                    }
                }
            }
        }

        Object foreignMarkup = entry.getForeignMarkup();
        if (foreignMarkup != null) {
            String customThumb = extractCustomThumb(foreignMarkup, source);
            if (customThumb != null)
                return cleanImageUrl(customThumb, source);
        }

        if (entry.getDescription() != null) {
            String img = extractImgFromHtml(entry.getDescription().getValue(), source);
            if (img != null)
                return cleanImageUrl(img, source);
        }

        if (entry.getContents() != null) {
            for (SyndContent c : entry.getContents()) {
                String img = extractImgFromHtml(c.getValue(), source);
                if (img != null)
                    return cleanImageUrl(img, source);
            }
        }
        return "";
    }

    private String extractCustomThumb(Object foreignMarkup, ArticleConfig.Source source) {
        if (foreignMarkup instanceof List) {
            @SuppressWarnings("unchecked")
            List<org.jdom2.Element> elements = (List<org.jdom2.Element>) foreignMarkup;
            for (org.jdom2.Element el : elements) {
                if ("thumb".equals(el.getName()) && el.hasAttributes()) {
                    String url = el.getAttribute("url").getValue();
                    if (!url.contains("default_image.png")) {
                        return cleanWordpressImage(url, source);
                    }
                }
            }
        }
        return null;
    }

    private String extractImgFromHtml(String html, ArticleConfig.Source source) {
        if (html == null || html.isBlank())
            return null;
        Document doc = Jsoup.parse(html);
        Element img = doc.selectFirst("img[src]");
        if (img == null)
            return null;
        return cleanWordpressImage(img.attr("src"), source);
    }

    private String cleanImageUrl(String url, ArticleConfig.Source source) {
        if (url == null)
            return null;
        int idx = url.indexOf("#");
        if (idx > 0)
            url = url.substring(0, idx);
        return cleanWordpressImage(url.trim(), source);
    }

    private String cleanWordpressImage(String url, ArticleConfig.Source source) {
        if (url == null)
            return null;
        url = url.replaceAll("-\\d+x\\d+(?=\\.(jpg|jpeg|png|gif|webp)(?:\\?|#|$))", "");
        if (url.contains("ichef.bbci.co.uk")) {
            url = url.replaceAll("ace/standard/\\d+", "news/1536");
            if (url.endsWith(".jpg")) {
                url = url.substring(0, url.length() - 4) + ".jpg.webp";
            } else if (!url.endsWith(".webp")) {
                url = url + ".webp";
            }
            return url;
        }
        if (source != null && "Everyday On Sales".equalsIgnoreCase(source.getName())) {
            if (!url.startsWith("http") && url.contains("wp-content")) {
                url = "https://www.everydayonsales.com/" + url;
            }
            return url;
        }
        return url;
    }

    private boolean isImageUrlConfirmed(String url) {
        if (url == null || url.isBlank())
            return false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            String ct = resp.headers().firstValue("Content-Type").orElse("");
            return ct.toLowerCase().startsWith("image/");
        } catch (Exception e) {
            return false;
        }
    }

    private Long extractPublishedAt(SyndEntry entry) {
        if (entry.getPublishedDate() != null)
            return entry.getPublishedDate().toInstant().toEpochMilli();
        if (entry.getUpdatedDate() != null)
            return entry.getUpdatedDate().toInstant().toEpochMilli();
        if (entry.getForeignMarkup() != null) {
            for (org.jdom2.Element element : entry.getForeignMarkup()) {
                String name = element.getName().toLowerCase();
                String text = element.getTextTrim();
                if (name.equals("date") || name.equals("dc:date") || name.equals("pubdate")) {
                    try {
                        return Instant.parse(text).toEpochMilli();
                    } catch (Exception e) {
                        try {
                            DateTimeFormatter rfc822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z",
                                    java.util.Locale.ENGLISH);
                            return LocalDateTime.parse(text, rfc822)
                                    .atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        return null;
    }

    private String fixMojibake(String value) {
        if (value == null || value.isBlank())
            return value;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (!looksMojibake(normalized))
            return normalized;
        String best = normalized;
        int bestScore = mojibakeScore(normalized);
        String decoded1252 = decodeMojibake(normalized, WINDOWS_1252);
        if (!decoded1252.equals(normalized)) {
            int score = mojibakeScore(decoded1252);
            if (score < bestScore) {
                best = decoded1252;
                bestScore = score;
            }
        }
        String decodedLatin1 = decodeMojibake(normalized, StandardCharsets.ISO_8859_1);
        if (!decodedLatin1.equals(normalized)) {
            int score = mojibakeScore(decodedLatin1);
            if (score < bestScore)
                best = decodedLatin1;
        }
        return best;
    }

    private boolean looksMojibake(String value) {
        if (value == null || value.isBlank())
            return false;
        if (value.indexOf('\uFFFD') >= 0)
            return true;
        if (MOJIBAKE_PATTERN.matcher(value).find())
            return true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x80 && c <= 0x9F)
                return true;
        }
        return false;
    }

    private int mojibakeScore(String value) {
        int score = 0;
        if (value == null || value.isBlank())
            return score;
        if (value.indexOf('\uFFFD') >= 0)
            score += 5;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x80 && c <= 0x9F)
                score += 3;
            else if (c == 'Ã' || c == 'Â' || c == 'â')
                score += 2;
        }
        Matcher matcher = MOJIBAKE_PATTERN.matcher(value);
        while (matcher.find())
            score += 2;
        return score;
    }

    private String decodeMojibake(String value, Charset charset) {
        try {
            CharsetEncoder encoder = charset.newEncoder();
            encoder.onMalformedInput(CodingErrorAction.REPORT);
            encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer bytes = encoder.encode(CharBuffer.wrap(value));
            return StandardCharsets.UTF_8.decode(bytes).toString();
        } catch (CharacterCodingException e) {
            return value;
        }
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private String hash(String source, String link) {
        return DigestUtils.sha256Hex(source + "|" + link);
    }}

    
    
        
    
        
    