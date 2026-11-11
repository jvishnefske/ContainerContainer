package org.artifactory.maven;

import com.jfrog.ph.acl.service.PackageArtifactService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.parser.StreamParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fetches Maven package version release dates by parsing HTML directory listings from remote repositories.
 *
 * @author Ron Malka
 */

@Slf4j
@AllArgsConstructor
public class MavenMetadataReleaseDatesFetcher {

    private final PackageArtifactService packageArtifactService;

    /**
     * Fetches release dates for Maven versions from remote repository.
     *
     * @param remoteUrl   URL to fetch version dates from
     * @param repoKey     repository key for authentication
     * @param versionsSet set of versions to look for
     * @return map of version to release date, empty if failed
     */
    public Map<String, Instant> fetchReleaseDates(String remoteUrl, String repoKey, Set<String> versionsSet) {
        CloseableHttpResponse datesResponse = null;
        try {
            HttpRequestBase requestForDates = new HttpGet(remoteUrl);
            datesResponse = packageArtifactService.executeRemoteHttpRequest(repoKey, requestForDates);
            if (datesResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                return extractVersionDates(remoteUrl, versionsSet, datesResponse.getEntity().getContent());
            }
        } catch (Exception e) {
            log.debug("Failed to fetch release dates for Maven curation for repoKey {}, remoteUrl {}", repoKey,
                    remoteUrl, e);
        } finally {
            if (datesResponse != null) {
                try {
                    datesResponse.close();
                } catch (Exception e) {
                    log.warn("Failed to close HTTP response for Maven package curation with message: {}",
                            e.getMessage());
                    log.debug("", e);
                }
            }
        }
        return Collections.emptyMap();
    }


    /**
     * Extracts version dates from HTML content stream.
     *
     * @param url         source URL for logging
     * @param versionsSet set of versions to match
     * @param content     HTML input stream to parse
     * @return map of version to release date, empty if parsing fails
     */
    private Map<String, Instant> extractVersionDates(String url, Set<String> versionsSet, InputStream content) {
        Map<String, Instant> versionMap = new HashMap<>();
        StreamParser streamer = null;

        try (Reader inputReader = new InputStreamReader(content, Charset.defaultCharset());
                BufferedReader reader = new BufferedReader(inputReader)) {
            streamer = new StreamParser(Parser.htmlParser()).parse(reader, url);
            Element link;

            while ((link = streamer.selectNext("a")) != null) {
                if (!processVersionLink(link, versionsSet, versionMap)) {
                    return Collections.emptyMap();
                }
                link.remove();
            }
        } catch (IOException e) {
            log.debug("Something went wrong when try parsing release dates from {}", url, e);
            return Collections.emptyMap();
        } finally {
            closeStreamer(streamer);
        }
        return versionMap;
    }

    /**
     * Processes a version link element to extract version and date information.
     *
     * @param link        element containing version link
     * @param versionsSet set of versions to match against
     * @param versionMap  map to store version-date pairs
     * @return true to continue processing, false to stop
     */
    private boolean processVersionLink(Element link, Set<String> versionsSet, Map<String, Instant> versionMap) {
        String href = link.attr("href");

        if (!isValidVersionLink(href, versionsSet)) {
            return true; // Continue processing
        }

        String versionKey = href.substring(0, href.length() - 1);
        log.debug("Found version link for: {}", versionKey);

        Node nextNode = link.nextSibling();
        if (nextNode == null) {
            log.debug("Could not find the date for version {}, stop parsing", versionKey);
            return false;
        }

        return extractDateFromTextNode(nextNode, versionKey, versionMap);
    }

    /**
     * Validates if href is a valid version link.
     *
     * @param href        link to validate
     * @param versionsSet set of valid versions
     * @return true if valid version link
     */
    private boolean isValidVersionLink(String href, Set<String> versionsSet) {
        if (href == null || !href.endsWith("/") || href.equals("../")) {
            return false;
        }

        String versionKey = href.substring(0, href.length() - 1);
        return versionsSet.contains(versionKey);
    }

    /**
     * Extracts date from text node for given version.
     *
     * @param nextNode   node containing date text
     * @param versionKey version identifier
     * @param versionMap map to store version-date pair
     * @return true if date extracted successfully
     */
    private boolean extractDateFromTextNode(Node nextNode, String versionKey, Map<String, Instant> versionMap) {
        if (!(nextNode instanceof TextNode textNode)) {
            log.debug("Parse Error: The Date Node for Version '{}' is not a TextNode", versionKey);
            return false;
        }

        String lineText = textNode.getWholeText();
        if (lineText.trim().isEmpty()) {
            log.debug("Parse Error: Version {} is missing date information.", versionKey);
            return false;
        }

        String[] dateParts = lineText.trim().split("\\s++");
        if (dateParts.length < 2) {
            log.debug("Date parts not found for version: {}, returning empty map", versionKey);
            return false;
        }

        String dateString = dateParts[0] + " " + dateParts[1];

        if (!parseVersionDate(versionMap, versionKey, dateString)) {
            log.debug("Failed to parse date {} for version: {}, returning empty map", dateString, versionKey);
            return false;
        }

        return true;
    }

    /**
     * Safely closes the stream parser.
     *
     * @param streamer parser to close
     */
    private void closeStreamer(StreamParser streamer) {
        if (streamer != null) {
            streamer.stop();
            streamer.close();
        }
    }


    /**
     * Parses date string and adds to version map.
     *
     * @param versionsDates map to store parsed date
     * @param versionTitle  version identifier
     * @param dateString    date in "yyyy-MM-dd HH:mm" format
     * @return true if parsing successful
     */
    private boolean parseVersionDate(Map<String, Instant> versionsDates, String versionTitle, String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            Date date = dateFormat.parse(dateString);
            versionsDates.put(versionTitle, date.toInstant());
        } catch (ParseException e) {
            log.debug("Failed to parse date: {} for version: {}", dateString, versionTitle, e);
            return false;
        }
        return true;
    }
}
