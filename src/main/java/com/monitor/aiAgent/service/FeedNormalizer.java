package com.monitor.aiAgent.service;

import com.monitor.aiAgent.config.NewsConfig;
import com.monitor.aiAgent.config.NewsDataConfig;
import com.monitor.aiAgent.dto.NewsDataItem;
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.text.Normalizer;
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
public class FeedNormalizer {
    private static final Charset WINDOWS_1252 = Charset.forName("Windows-1252");
    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile(
            "Ã.|Â.|â€|â€™|â€œ|â€|â€“|â€”|â€¦|â€¢|â„¢|â‚¬"
    );

    public News normalize(
            SyndEntry entry,
            NewsConfig.Source source,
            NewsConfig.Feed feed
    ) {
        String title = sanitizeTitle(entry.getTitle());
        String link = safe(entry.getLink());

        String image = extractImage(entry);
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
                .type(NewsType.NEWS)
                .createdAt(Instant.now().toEpochMilli())
                .build();
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() == null) {
            return "";
        }

        String raw = entry.getDescription().getValue();
        String decoded = StringEscapeUtils.unescapeHtml4(raw);
        Document doc = Jsoup.parse(decoded);

        doc.select("img, figure").remove();
        doc.select("script, style").remove();
        doc.select("p:contains(The post)").remove();

        return doc.text().trim();
    }

    private String extractAuthor(SyndEntry entry) {
        String author = entry.getAuthor();
        return author == null ? "" : author.trim();
    }

    private String extractImage(SyndEntry entry) {
        MediaEntryModule mediaModule =
                (MediaEntryModule) entry.getModule(MediaEntryModule.URI);

        if (mediaModule != null) {
            if (mediaModule.getMetadata() != null &&
                    mediaModule.getMetadata().getThumbnail() != null &&
                    mediaModule.getMetadata().getThumbnail().length > 0) {

                Thumbnail thumb = mediaModule.getMetadata().getThumbnail()[0];
                if (thumb.getUrl() != null) {
                    return cleanImageUrl(thumb.getUrl().toString());
                }
            }

            if (mediaModule.getMediaContents() != null) {
                for (MediaContent mc : mediaModule.getMediaContents()) {
                    if (mc.getReference() != null) {
                        return cleanImageUrl(mc.getReference().toString());
                    }
                }
            }
        }

        if (entry.getEnclosures() != null) {
            for (SyndEnclosure enc : entry.getEnclosures()) {
                if (enc.getType() != null && enc.getType().startsWith("image")) {
                    return cleanImageUrl(enc.getUrl());
                }
            }
        }

        List<org.jdom2.Element> foreignMarkup = entry.getForeignMarkup();
        if (foreignMarkup != null && !foreignMarkup.isEmpty()) {
            String customThumb = extractCustomThumb(foreignMarkup);
            if (customThumb != null) {
                return cleanImageUrl(customThumb);
            }
        }

        if (entry.getDescription() != null) {
            String img = extractImgFromHtml(entry.getDescription().getValue());
            if (img != null) return cleanImageUrl(img);
        }

        if (entry.getContents() != null) {
            for (SyndContent c : entry.getContents()) {
                String img = extractImgFromHtml(c.getValue());
                if (img != null) return cleanImageUrl(img);
            }
        }

        return "";
    }

    private String extractCustomThumb(List<org.jdom2.Element> elements) {
        for (org.jdom2.Element el : elements) {
            if ("thumb".equalsIgnoreCase(el.getName()) && el.hasAttributes()) {
                String url = el.getAttributeValue("url");
                if (url != null && !url.contains("default_image.png")) {
                    return url;
                }
            }

            if ("thumbnail".equalsIgnoreCase(el.getName())) {
                org.jdom2.Element urlElement = el.getChild("url");
                if (urlElement != null) {
                    String url = urlElement.getTextTrim();
                    if (url != null && !url.isBlank()) {
                        return url;
                    }
                }
            }
        }
        return null;
    }

    private String extractImgFromHtml(String html) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);
        Element img = doc.selectFirst("img[src]");
        return img != null ? img.attr("src") : null;
    }

    private String cleanImageUrl(String url) {
        if (url == null) return null;

        int idx = url.indexOf("#");
        if (idx > 0) {
            url = url.substring(0, idx);
        }

        if (url.startsWith("https://berita.rtm.gov.my/images/")) {
            url = url.replace(
                    "https://berita.rtm.gov.my/images/",
                    "https://berita.rtm.gov.my/wp-content/uploads/"
            );
        }
        url = url.replace("%20", "-");

        if (url.contains("ichef.bbci.co.uk")) {
            url = url.replaceAll("ace/standard/\\d+", "news/1536");
            if (url.endsWith(".jpg")) {
                url = url.substring(0, url.length() - 4) + ".jpg.webp";
            } else if (!url.endsWith(".webp")) {
                url = url + ".webp";
            }
        }

        return url.trim();
    }

    private long extractPublishedAt(SyndEntry entry) {
        if (entry.getPublishedDate() != null) {
            return entry.getPublishedDate().toInstant().toEpochMilli();
        }
        if (entry.getUpdatedDate() != null) {
            return entry.getUpdatedDate().toInstant().toEpochMilli();
        }
        if (entry.getForeignMarkup() != null) {
            for (org.jdom2.Element element : entry.getForeignMarkup()) {
                if ("date".equalsIgnoreCase(element.getName())) {
                    try {
                        return Instant.parse(element.getTextTrim()).toEpochMilli();
                    } catch (Exception ignored) {}
                }
            }
        }
        return Instant.now().toEpochMilli();
    }

    private String sanitizeTitle(String raw) {
        if (raw == null) return "";
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

    private String fixMojibake(String value) {
        if (value == null || value.isBlank()) return value;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (!looksMojibake(normalized)) return normalized;

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
            if (score < bestScore) {
                best = decodedLatin1;
            }
        }
        return best;
    }

    private boolean looksMojibake(String value) {
        if (value == null || value.isBlank()) return false;
        if (value.indexOf('\uFFFD') >= 0) return true;
        if (MOJIBAKE_PATTERN.matcher(value).find()) return true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x80 && c <= 0x9F) return true;
        }
        return false;
    }

    private int mojibakeScore(String value) {
        int score = 0;
        if (value == null || value.isBlank()) return score;
        if (value.indexOf('\uFFFD') >= 0) score += 5;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x80 && c <= 0x9F) score += 3;
            else if (c == 'Ã' || c == 'Â' || c == 'â') score += 2;
        }
        Matcher matcher = MOJIBAKE_PATTERN.matcher(value);
        while (matcher.find()) score += 2;
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
    }

    public News normalize(NewsDataItem item, NewsDataConfig.Source source){
        try{
            var author = "";
            var category = "";

            if (item.getCreator() != null ) author = item.getCreator().get(0);
            if (item.getCategory() != null ) category = item.getCategory().get(0);

            return News.builder()
                    .id(UUID.randomUUID().toString())
                    .recordId(UUID.randomUUID().toString())
                    .title(sanitizeTitle(item.getTitle()))
                    .imageUrl(item.getImage_url())
                    .content(item.getDescription())
                    .author(author)
                    .category(category)
                    .sourceName(filterSourceName(item.getSource_name()))
                    .sourceId(source.getId())
                    .sourceImage(item.getSource_icon())
                    .sourceURL(item.getSource_url())
                    .rssLink("domain=" + source.getId().toLowerCase())
                    .rssType("newsdata")
                    .link(item.getLink())
                    .language(tolanguageEnum(item.getLanguage()))
                    .publishedAt(toEpochMillis(item.getPubDate()))
                    .status("PUBLISHED")
                    .createdAt(Instant.now().toEpochMilli())
                    .build();

        } catch (Exception e) {
            log.info("error normalizing news data article, {}", e.getMessage());
            return null;
        }
    }

    private String filterSourceName (String name) {
        return switch (name.toLowerCase()) {
            case "utama" -> "Utusan";
            case "my metro" -> "Harian Metro";
            case "berita malaysia" -> "Berita Harian";
            default -> name;
        };
    }

    private String tolanguageEnum (String ln) throws Exception {
        return switch (ln.toLowerCase()) {
            case "english" -> "EN";
            case "malay" -> "MS";
            case "chinese" -> "ZH";
            default -> throw new Exception("enum not registered: " + ln);
        };
    }

    private static final DateTimeFormatter API_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private long toEpochMillis(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return Instant.now().toEpochMilli();
        }
        try {
            return LocalDateTime.parse(pubDate, API_FORMAT)
                    .atZone(ZoneId.of("Asia/Kuala_Lumpur"))
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            return Instant.now().toEpochMilli();
        }
    }
}
