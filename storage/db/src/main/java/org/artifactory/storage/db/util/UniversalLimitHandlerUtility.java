package org.artifactory.storage.db.util;

import com.amazonaws.util.StringUtils;
import lombok.experimental.UtilityClass;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.RequestThreadLocal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Eli Fishman
 */
@UtilityClass
public class UniversalLimitHandlerUtility {

    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Combined blocklist: OOM patterns merged with {@link ConstantValues#limitAllQueriesWithBlock}.
     * {@code null} means no OOM blacklist has been set — fall back to the live ConstantValues list.
     */
    private static final AtomicReference<Set<String>> effectiveBlocklist =
            new AtomicReference<>(null);

    public static int getConfiguredLimit() {
        if (RequestThreadLocal.isExcludedFromUniversalLimit()) {
            return 0;
        }
        String contextInfo = getContextInfo();
        if (!isContextInList(contextInfo, getEffectiveBlocklist())) {
            return 0;
        }
        // Effective-blocklist hit. Apply block > white > violations precedence:
        //   - operator-configured blocklist wins regardless of the whitelist.
        //   - else, if the context is whitelisted, exempt the request
        //     (whitelist > violations).
        int limit = ConstantValues.limitAllQueries.getInt();
        if (isContextInList(contextInfo,
                ConstantValues.limitAllQueriesWithBlock.getList())) {
            return Math.max(limit, 0);
        }
        if (isContextInWhitelist(contextInfo,
                ConstantValues.limitAllQueriesRemediationWhitelist.getList())) {
            return 0;
        }
        return Math.max(limit, 0);
    }

    public static boolean isToBlock() {
        if (RequestThreadLocal.isExcludedFromUniversalLimit()) {
            return false;
        }
        String contextInfo = getContextInfo();
        if (!isContextInList(contextInfo, getEffectiveBlocklist())) {
            return false;
        }
        if (isContextInList(contextInfo,
                ConstantValues.limitAllQueriesWithBlock.getList())) {
            return true;
        }
        return !isContextInWhitelist(contextInfo,
                ConstantValues.limitAllQueriesRemediationWhitelist.getList());
    }

    /**
     * Sets the OOM-recovery blacklist patterns. When non-empty, they are merged
     * with {@link ConstantValues#limitAllQueriesWithBlock} into a single effective blocklist.
     * When empty or null, the effective blocklist falls back to the ConstantValues list only.
     */
    public static void setOomBlacklist(Set<String> oomPatterns) {
        if (oomPatterns == null || oomPatterns.isEmpty()) {
            effectiveBlocklist.set(null);
            return;
        }
        Set<String> combined = new HashSet<>(
                ConstantValues.limitAllQueriesWithBlock.getList());
        combined.addAll(oomPatterns);
        effectiveBlocklist.set(Set.copyOf(combined));
    }

    /**
     * Merges additional OOM-recovery blacklist patterns into the effective blocklist without
     * discarding patterns already in effect. Invoked on peer HA nodes when another node activates
     * its OOM blacklist on startup, so the whole cluster blocks the same query contexts. Idempotent;
     * a {@code null}/empty argument leaves the effective blocklist unchanged.
     */
    public static void addToOomBlacklist(Set<String> oomPatterns) {
        if (oomPatterns == null || oomPatterns.isEmpty()) {
            return;
        }
        effectiveBlocklist.updateAndGet(existing -> {
            Set<String> combined = new HashSet<>(existing != null
                    ? existing
                    : ConstantValues.limitAllQueriesWithBlock.getList());
            combined.addAll(oomPatterns);
            return Set.copyOf(combined);
        });
    }

    public static Collection<String> getEffectiveBlocklist() {
        Set<String> merged = effectiveBlocklist.get();
        return merged != null
                ? merged
                : ConstantValues.limitAllQueriesWithBlock.getList();
    }

    /**
     * Check if the context info matches any item in the collection,
     * supporting wildcard patterns.
     * @param contextInfo The current context (e.g., "search/dates")
     * @param regexPatterns Patterns (e.g., ["search/*", "api/users"])
     * @return true if context matches any pattern in the collection
     */
    private static boolean isContextInList(
            String contextInfo, Collection<String> regexPatterns) {
        if (StringUtils.isNullOrEmpty(contextInfo)
                || regexPatterns == null || regexPatterns.isEmpty()) {
            return false;
        }
        return regexPatterns.stream()
                .anyMatch(pattern -> matchesRegexPattern(contextInfo, pattern));
    }

    /**
     * Check if the context info is whitelisted by literal-prefix match.
     * Distinct from {@link #isContextInList(String, Collection)} (regex) —
     * operators write bare path prefixes; sub-URLs of the prefix (after
     * path normalization) are exempted from OOM-remediation auto-blocking.
     * <p>
     * Both the context and each entry are passed through {@link #stripApiPrefix(String)}
     * before matching, so operators may write entries in any prefix form
     * ({@code "search/aql"}, {@code "api/search/aql"}, or
     * {@code "artifactory/api/search/aql"}) and get identical behaviour.
     *
     * @param contextInfo current context as returned by {@link #getContextInfo()}
     * @param whitelistPrefixes literal prefixes — may be written with or without a
     *                          leading {@code "api/"} or {@code "artifactory/api/"} segment
     * @return true if contextInfo equals or is a sub-URL of any whitelist prefix
     *         (after prefix stripping on both sides)
     */
    private static boolean isContextInWhitelist(
            String contextInfo, Collection<String> whitelistPrefixes) {
        if (StringUtils.isNullOrEmpty(contextInfo)
                || whitelistPrefixes == null || whitelistPrefixes.isEmpty()) {
            return false;
        }
        String normalizedContext = stripApiPrefix(contextInfo);
        return whitelistPrefixes.stream()
                .map(UniversalLimitHandlerUtility::stripApiPrefix)
                .filter(prefix -> !StringUtils.isNullOrEmpty(prefix))
                .anyMatch(prefix -> normalizedContext.equals(prefix)
                        || normalizedContext.startsWith(prefix + "/"));
    }

    /**
     * Check if context matches a regexPattern, supporting wildcard (*) syntax.
     * <p>
     * Both the context and the pattern are passed through {@link #stripApiPrefix(String)}
     * before matching, so operators may write patterns in any prefix form
     * ({@code "search/aql"}, {@code "api/search/aql"}, or
     * {@code "/artifactory/api/search/aql"}) and get identical behaviour regardless
     * of the deployment's servlet-mapping prefix.
     *
     * @param contextInfo The context to check — may carry any recognised URL prefix;
     *                    it is normalised to the bare form before comparison
     * @param regexPattern The pattern to match against — may be written with or without
     *                     a leading {@code "api/"} or {@code "artifactory/api/"} segment
     * @return true if context matches the regexPattern
     */
    private boolean matchesRegexPattern(String contextInfo, String regexPattern) {
        if (StringUtils.isNullOrEmpty(regexPattern)) {
            return false;
        }

        String normalizedContextInfo = stripApiPrefix(contextInfo);
        String normalizedRegexPattern = stripApiPrefix(regexPattern);
        if (StringUtils.isNullOrEmpty(normalizedRegexPattern)) {
            return false;
        }

        if (normalizedRegexPattern.equals(normalizedContextInfo)) {
            return true;
        }

        try {
            Pattern compiledPattern = PATTERN_CACHE.computeIfAbsent(normalizedRegexPattern,
                    newPattern -> Pattern.compile(newPattern, Pattern.CASE_INSENSITIVE));
            return compiledPattern.matcher(normalizedContextInfo).matches();

        } catch (PatternSyntaxException e) {
            return normalizedRegexPattern.contains(normalizedContextInfo);
        }
    }

    /**
     * Strips the well-known Artifactory REST URL prefixes from a path-like string so that
     * operator-written entries and route keys can be compared in a canonical, prefix-free form.
     *
     * <p>The following prefix forms are recognised (with or without a leading slash):
     * <ul>
     *   <li>{@code /artifactory/api/} — full public URL prefix used by some deployments</li>
     *   <li>{@code /api/} — prefix used by the default Jersey servlet mapping</li>
     * </ul>
     *
     * <p>Stripping is applied to <b>both</b> sides of every match (context info and
     * pattern/whitelist entry), so operators may write entries in any of the following
     * equivalent forms and get identical behaviour:
     * <pre>
     *   search/aql
     *   api/search/aql
     *   /api/search/aql
     *   artifactory/api/search/aql
     *   /artifactory/api/search/aql
     * </pre>
     */
    private static String stripApiPrefix(String entry) {
        if (StringUtils.isNullOrEmpty(entry)) {
            return entry;
        }
        String normalized = normalizePath(entry);
        // Strip "artifactory/api/" first — more specific than bare "api/"
        if (normalized.equals("artifactory/api")) {
            return "";
        }
        if (normalized.startsWith("artifactory/api/")) {
            return normalized.substring("artifactory/api/".length());
        }
        if (normalized.equals("api")) {
            return "";
        }
        if (normalized.startsWith("api/")) {
            return normalized.substring("api/".length());
        }
        return normalized;
    }

    /**
     * Normalize path by removing leading and trailing slashes.
     */
    private static String normalizePath(String path) {
        if (StringUtils.isNullOrEmpty(path)) {
            return path;
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return (start == 0 && end == path.length()) ? path : path.substring(start, end);
    }

    /**
     * Returns the route key for the current request or task, used to match against the
     * effective blocklist. The priority mirrors {@link org.artifactory.storage.db.monitor.DbQueryContext#getRouteKey()}
     * so that violation records can be persisted and matched symmetrically:
     *
     * <ol>
     *   <li><b>Jersey REST</b>: the URI template stored in {@link CurrentActivity#NAME}
     *       (e.g. {@code search/aql}), set by the URI-template context filter.</li>
     *   <li><b>Non-Jersey (RepoFilter)</b>: route family combined with the package type
     *       (e.g. {@code download npm}), when both {@link CurrentActivity#GROUP} and
     *       {@link CurrentActivity#PACKAGE} are available.</li>
     *   <li><b>Background jobs / activities without a package</b>: group alone
     *       (e.g. {@code download}).</li>
     *   <li><b>Fallback</b>: the raw request path from {@link RequestThreadLocal} for
     *       uncategorized requests and backward compatibility with operator-configured patterns.</li>
     * </ol>
     */
    private String getContextInfo() {
        // 1. Jersey REST: URI template (e.g. "search/aql") set by the URI-template context filter.
        String name = CurrentActivity.NAME.get();
        if (!StringUtils.isNullOrEmpty(name)) {
            return name;
        }

        // 2. Non-Jersey (RepoFilter): route family + package type (e.g. "download npm").
        String group = CurrentActivity.GROUP.get();
        String pkg = CurrentActivity.PACKAGE.get();
        if (!StringUtils.isNullOrEmpty(group) && !StringUtils.isNullOrEmpty(pkg)) {
            return group + " " + pkg;
        }

        // 3. Jobs / activities without a package: group alone.
        if (!StringUtils.isNullOrEmpty(group)) {
            return group;
        }

        // 4. Fallback: actual request path — covers uncategorized requests and legacy
        //    operator-configured patterns that reference request path prefixes.
        String requestPath = RequestThreadLocal.getRequestPath();
        if (!StringUtils.isNullOrEmpty(requestPath)) {
            return requestPath;
        }

        return "";
    }

    /**
     * Clear the pattern cache. Useful for testing or when patterns change.
     */
    public static void clearPatternCache() {
        PATTERN_CACHE.clear();
    }
}