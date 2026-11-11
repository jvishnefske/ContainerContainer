/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.request;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.jfrog.client.util.DenylistLoader;
import org.jfrog.client.util.DenylistValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Verifies that the given URL is not blocked {@see RTFACT-18385} for remote repository URL.
 * - Loopback URLs are blocked (prefixes can be whitelisted by ConstantValue), but bridge URLs are allowed.
 * - JFrog Bridge paths match {@code /bridge-&lt;instance&gt;/...} from the start of the URI path (e.g.
 *   {@code http://localhost:&lt;routerPort&gt;/bridge-xxx/artifactory/...}).
 * - site/link local address (10/8,172.16/12,192.168/16 prefixes and 169.254/16) are enabled by default.
 * - Known JFrog internal infrastructure hostnames are always blocked (RTDEV-90192).
 * <p>
 * Every decision here is made against the host derived by {@link #getDomainName(String)}, which resolves the URL
 * the same way the outbound Apache HTTP client does. That symmetry is the security property: if this class and the
 * client disagreed on which host a URL denotes, a URL could be cleared against one host and connected to another
 * (RTDEV-95692). For the same reason the two exemptions below - the admin whitelist and JFrog Bridge loopback URLs
 * - are matched on the derived host and normalized path rather than on the raw URL string.
 * <p>
 * That symmetry covers the host name, not the address behind it. The client resolves the name again when it opens
 * the connection, so a name answering a routable address here and a loopback one an instant later is still reached.
 * Closing that window means connecting to the address set this class already validated rather than to a second
 * lookup of the same name, which is left to a follow-up. What keeps the window down to a single request is that the
 * per-request floors ({@link #isOutboundTargetBlocked(String, String)},
 * {@link #isRedirectTargetBlocked(String, String)}) resolve live on every call; only the configure-time verdicts of
 * {@link #isRemoteRepoBlocked(String, String)} and {@link #isOutboundFetchBlocked(String, String)} are cached per
 * domain, and those are advisory - the floor runs again when the fetch actually happens.
 *
 * @author Nadav Yogev
 */
@Slf4j
@Component
public class UrlVerifier {

    private static final Pattern bridgeUrlPathPattern = Pattern.compile("/bridge-.*");
    private static final int MAX_PATH_DECODE_PASSES = 5;
    private static final String SCHEME_REQUIRED_REASON = "a repository url must state a scheme";

    private final List<String> jfrogInternalBlockedHosts;
    private final List<WhitelistEntry> whiteListedRemoteRepositories;
    private final boolean isDev;
    private final boolean useRemoteRepoStrictPolicy;
    private final Cache<String, Boolean> urlCache;
    private final Cache<String, Boolean> loggedUnresolvedDnsCache;
    private final Cache<String, Boolean> loggedUndeterminedUrlCache;
    private final DenylistValidator validator;
    private final CentralConfigService configService;

    private int routerInternalPort = 0;

    @Autowired
    public UrlVerifier(DenyListCacheHolderService denyListCacheHolderService, CentralConfigService configService) {
        whiteListedRemoteRepositories = parseWhitelist(ConstantValues.whitelistRemoteRepoUrls.getString());
        jfrogInternalBlockedHosts = ConstantValues.urlVerifierInternalJfrogBlockedHosts.getList();
        urlCache = CacheBuilder.newBuilder()
                .expireAfterWrite(ConstantValues.remoteRepoUrlVerifierCacheExpirySeconds.getInt(), TimeUnit.SECONDS)
                .maximumSize(ConstantValues.remoteRepoUrlVerifierCacheSize.getLong())
                .build();
        loggedUnresolvedDnsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(ConstantValues.remoteRepoUrlVerifierCacheExpirySeconds.getInt(),
                        TimeUnit.SECONDS)
                .maximumSize(ConstantValues.remoteRepoUrlVerifierCacheSize.getLong())
                .build();
        loggedUndeterminedUrlCache = CacheBuilder.newBuilder()
                .expireAfterWrite(ConstantValues.remoteRepoUrlVerifierCacheExpirySeconds.getInt(),
                        TimeUnit.SECONDS)
                .maximumSize(ConstantValues.remoteRepoUrlVerifierCacheSize.getLong())
                .build();
        isDev = ConstantValues.isDevOrTest(ArtifactoryHome.get());
        useRemoteRepoStrictPolicy = ConstantValues.remoteRepoBlockUrlStrictPolicy.getBoolean();
        this.validator =
                new DenylistValidator(
                        new DenylistLoader(ArtifactoryHome.get().getSysConfig()).getDenylist(),
                        denyListCacheHolderService.getDenyListCache()
                );
        this.configService = configService;
    }

    public boolean isRemoteRepoBlocked(String url, @Nonnull String repoKey) {
        return checkBlocked(url, repoKey, false, true,
                () -> StringUtils.isBlank(url) || !AolUtils.isAol() || isDev || isUrlWhitelisted(url)
                        || isLoopbackBridgeUrl(url));
    }

    /**
     * Fetch-time SSRF guard for package-manager outbound fetches (e.g. from
     * {@code alternativeRemoteDownloadUrl}). Unlike {@link #isRemoteRepoBlocked(String, String)}, this
     * method protects both SaaS and on-prem deployments.
     * JFrog Bridge loopback URLs and admin-whitelisted prefixes are still allowed.
     */
    public boolean isOutboundFetchBlocked(String url, @Nonnull String repoKey) {
        return checkBlocked(url, repoKey, false,
                () -> StringUtils.isBlank(url) || isRelativeReference(url) || isUrlWhitelisted(url)
                        || isLoopbackBridgeUrl(url));
    }

    /**
     * minimal security floor for redirect {@code Location} hops — blocks only loopback and
     * JFrog internal hosts, leaving private-range blocking to the tunable {@code JFrogDefaultRedirectStrategy}
     * denylist so legitimate internal-mirror redirects keep working.
     */
    public boolean isRedirectTargetBlocked(String url, @Nonnull String repoKey) {
        return isSsrfFloorBlocked(url, repoKey);
    }

    /**
     * Broad outbound choke-point floor (RTDEV-90192): applied to every outgoing remote request. Blocks only
     * loopback and JFrog-internal hosts so non-bridge loopback targets (e.g. the internal router port) cannot be
     * reached on any deployment, while genuine JFrog Bridge loopback paths and admin-whitelisted prefixes keep
     * working. Private-range mirrors remain allowed (left to admin policy), unlike
     * {@link #isOutboundFetchBlocked(String, String)}.
     */
    public boolean isOutboundTargetBlocked(String url, @Nonnull String repoKey) {
        return isSsrfFloorBlocked(url, repoKey);
    }

    private boolean isSsrfFloorBlocked(String url, @Nonnull String repoKey) {
        return checkBlocked(url, repoKey, true,
                () -> StringUtils.isBlank(url) || isRelativeReference(url) || isUrlWhitelisted(url)
                        || isLoopbackBridgeUrl(url));
    }

    private boolean checkBlocked(String url, @Nonnull String repoKey, boolean redirectFloorOnly,
            BooleanSupplier skipCondition) {
        return checkBlocked(url, repoKey, redirectFloorOnly, false, skipCondition);
    }

    /**
     * @param requireScheme true only for {@link #isRemoteRepoBlocked(String, String)}: a repository url is
     *                       the base every outbound request is appended to, so it has to state its own scheme. The
     *                       protocol-relative form is only meaningful where something else supplies one - a
     *                       redirect {@code Location}, which the client resolves against the request url - so every
     *                       other caller leaves this false and lets {@link #getUrlIdentity(String)} derive a host
     *                       from it as usual. Checked after {@code skipCondition} so this guard shares the same
     *                       SaaS-only, whitelist and Bridge-loopback exemptions as the rest of the method, instead
     *                       of running ahead of them and logging on deployments where the remainder is a no-op.
     */
    private boolean checkBlocked(String url, @Nonnull String repoKey, boolean redirectFloorOnly,
            boolean requireScheme, BooleanSupplier skipCondition) {
        try {
            if (skipCondition.getAsBoolean()) {
                return false;
            }
            if (requireScheme && statesAuthorityWithoutScheme(url)) {
                logUndeterminedUrlOnce(url, repoKey, SCHEME_REQUIRED_REASON);
                return true;
            }
            String domain = getDomainName(url);
            if (redirectFloorOnly) {
                return isRedirectFloorBlocked(domain, url, repoKey);
            }
            Boolean blocked = urlCache.getIfPresent(domain);
            if (blocked == null) {
                blocked = checkIfBlocked(domain, url, repoKey);
                urlCache.put(domain, blocked);
            }
            return blocked;
        } catch (UndeterminedHostException | URISyntaxException e) {
            // Fail closed on both, and deliberately without consulting remoteRepoBlockUrlOnUnknownHost or
            // useRemoteRepoStrictPolicy. A url naming no host is one the outbound client derives a host from and
            // connects to anyway; a url that will not parse is one we cannot place at all. Neither leaves a
            // configuration under which clearing it would be correct, so neither falls through to the policy default.
            logUndeterminedUrlOnce(url, repoKey, e.getMessage());
            return true;
        } catch (UnknownHostException e) {
            // The failure itself was already logged, at most once per cache window, by resolveAddresses() - every
            // path that throws this reaches us through it.
            log.trace("URL '{}' for repository '{}' resolves to an unknown host", url, repoKey);
            return ConstantValues.remoteRepoBlockUrlOnUnknownHost.getBoolean();
        } catch (Exception e) {
            log.warn("Unexpected error while resolving URL '{}' for repository '{}'", url, repoKey, e);
        }
        return useRemoteRepoStrictPolicy;
    }

    /**
     * True if {@code url} is covered by an admin-configured whitelist prefix, which exempts it from every check
     * below. The raw prefix must still match, so the exemption never covers more than it does today, and in
     * addition the URL's scheme, derived host and path must match the entry's - the port only when the entry pins
     * one, so an entry like {@code https://10.0.0.7} keeps covering every port on that host.
     * <p>
     * The identity check is what makes the exemption safe. A raw prefix match alone also exempts URLs whose real
     * host is unrelated to the entry, since the prefix can end mid-authority: with {@code https://acme.example}
     * whitelisted, both {@code https://acme.example@127.0.0.1:8046/} (the host is the part after {@code @}) and
     * {@code https://acme.example.attacker.test/} (a domain the attacker points wherever it likes) match it.
     */
    private boolean isUrlWhitelisted(String url) {
        if (whiteListedRemoteRepositories.isEmpty()) {
            return false;
        }
        UrlIdentity target;
        try {
            target = getUrlIdentity(url);
        } catch (Exception e) {
            log.debug("URL '{}' was not matched against the whitelist - no host could be determined from it", url, e);
            return false;
        }
        return whiteListedRemoteRepositories.stream()
                .anyMatch(entry -> url.startsWith(entry.rawPrefix()) && matchesIdentity(target, entry.identity()));
    }

    /**
     * A surviving {@code ..} segment denies the exemption, exactly as it does for a Bridge path (see
     * {@link #matchesRouterPortAndBridgePath(UrlIdentity)}). The path is load-bearing here - it is what confines an
     * entry such as {@code http://127.0.0.1:9999/trusted} to that one prefix - and comparing it without this
     * check let {@code http://127.0.0.1:9999/trusted/../admin} inherit the exemption and reach {@code /admin}.
     * {@link #getUrlIdentity(String)} normalizes what it can; this catches what normalization leaves behind,
     * notably encoded spellings such as {@code ..%2f} or {@code %252e%252e} - see
     * {@link #containsTraversalAtAnyEncodingDepth(String)}.
     */
    private static boolean matchesIdentity(UrlIdentity target, UrlIdentity whitelisted) {
        if (containsTraversalAtAnyEncodingDepth(target.path())) {
            return false;
        }
        return Strings.CS.equals(target.scheme(), whitelisted.scheme())
                && target.host().equals(whitelisted.host())
                && (whitelisted.port() < 0 || target.port() == whitelisted.port())
                && isPathCoveredBy(target.path(), whitelisted.path());
    }

    /**
     * True if {@code targetPath} is the whitelisted path itself or a descendant of it. The prefix has to end on a
     * segment boundary: an entry for {@code /trusted} confines the exemption to that path and what is below it, and
     * comparing it as a bare string prefix also carried it over to the sibling {@code /trusted-evil} - a separate
     * endpoint the admin never named, on a host that is trusted only for the configured path.
     * <p>
     * Trailing slashes on the entry are ignored, so {@code /trusted} and {@code /trusted/} cover the same paths. An
     * entry that names no path at all leaves an empty prefix, which covers the whole host, as it did before.
     */
    private static boolean isPathCoveredBy(String targetPath, String whitelistedPath) {
        String base = StringUtils.stripEnd(whitelistedPath, "/");
        return targetPath.equals(base) || targetPath.startsWith(base + "/");
    }

    /**
     * True if {@code path} carries a {@code ..} segment at any encoding depth. Both exemptions reject a traversal,
     * and the depth matters: {@link URI#getPath()} decodes one layer, which is what turns {@code ..%2f} into a
     * visible {@code ..}, but a second layer hides it again. {@code %252e%252e} arrives here as the literal text
     * {@code %2e%2e}, which a plain {@code ..} comparison does not see, while a hop that decodes once more resolves
     * it into a real traversal - so the exemption was granted for a path that does not stay inside the route it was
     * granted for. Each layer is therefore peeled off until the text stops changing.
     * <p>
     * A path still changing after {@value #MAX_PATH_DECODE_PASSES} passes is treated as a traversal rather than
     * trusted; every pass that changes anything also shortens the text, so a genuine path settles within one or two.
     * <p>
     * A substring match rather than a {@code ..} path segment, deliberately. It costs an exemption to the odd
     * legitimate path holding {@code ..} inside a token, such as {@code build..123}, which then takes the ordinary
     * checks instead. Matching whole segments would in exchange admit the spellings a downstream hop may still
     * resolve as a dot segment - {@code ..;} being the known one, which some servlet containers strip - and this is
     * the gate that hands out an exemption, so it errs towards refusing one.
     */
    private static boolean containsTraversalAtAnyEncodingDepth(@Nonnull String path) {
        String decoded = path;
        for (int pass = 0; pass < MAX_PATH_DECODE_PASSES; pass++) {
            if (decoded.contains("..")) {
                return true;
            }
            String next = decodePercentEscapesOnce(decoded);
            if (next.equals(decoded)) {
                return false;
            }
            decoded = next;
        }
        return true;
    }

    /**
     * Decodes one layer of {@code %XX} escapes, leaving anything that is not a complete escape as it stands.
     * {@link java.net.URLDecoder} is deliberately not used: it rejects a stray {@code %} outright, and a decoded
     * path may legitimately carry one - a file named {@code 100%} reaches us as {@code 100%25} - so failing closed
     * on it would deny the exemption to a genuine route. This exists only to look for dot segments, so decoding
     * escape by escape without reassembling multi-byte characters is enough.
     */
    private static String decodePercentEscapesOnce(@Nonnull String value) {
        int length = value.length();
        StringBuilder decoded = new StringBuilder(length);
        int index = 0;
        while (index < length) {
            char current = value.charAt(index);
            if (current == '%' && index + 2 < length
                    && isHexDigit(value.charAt(index + 1)) && isHexDigit(value.charAt(index + 2))) {
                decoded.append((char) Integer.parseInt(value.substring(index + 1, index + 3), 16));
                index += 3;
            } else {
                decoded.append(current);
                index++;
            }
        }
        return decoded.toString();
    }

    private static boolean isHexDigit(char character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private boolean checkIfBlocked(String domain, String url, String repoKey) throws UnknownHostException {
        // The denylist runs first because it compares the domain by name before performing any lookup of its own:
        // running it after isRepoBlocked meant a denylisted domain that fails to resolve was never compared against
        // the list at all, since isRepoBlocked throws first. The denylist then resolves the domain itself, and a
        // failure in that lookup is logged and dropped rather than propagated, so it cannot in turn preempt the
        // address check below - which resolves the domain independently and reports an unresolvable one itself.
        try {
            if (isDenylisted(domain)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Denylist check for domain '{}' of repository '{}' did not complete", domain, repoKey, e);
        }
        return isRepoBlocked(domain, url, repoKey);
    }

    /**
     * The denylist compares a host by name without connecting to it, so a {@code www.}-prefixed host is offered in
     * both forms. The derived host now keeps the prefix (see {@link #canonicalizeHost(String)}) whereas this list
     * only ever received the stripped form, and an entry naming {@code example.com} is meant to cover
     * {@code www.example.com} as well. Checking both keeps that reach without letting the prefix decide which host
     * gets resolved.
     */
    private boolean isDenylisted(@Nonnull String domain) {
        if (validator.isBlocked(domain)) {
            return true;
        }
        return domain.startsWith("www.") && validator.isBlocked(domain.substring(4));
    }

    private boolean isRedirectFloorBlocked(String domain, String url, String repoKey) throws UnknownHostException {
        InetAddress[] netAddresses = resolveAddresses(domain);
        if (isLoopbackOrWildcard(netAddresses)) {
            log.error("Can't follow redirect for {} to URL {} - target is a loopback or wildcard address, which is "
                    + "blocked.", repoKey, url);
            return true;
        }
        if (isJFrogInternalHost(domain)) {
            log.error("Can't follow redirect for {} to URL {} - target resolves to a JFrog internal host.",
                    repoKey, url);
            return true;
        }
        return false;
    }

    @VisibleForTesting
    boolean isRepoBlocked(@Nonnull String domain, @Nonnull String url, @Nonnull String repoKey)
            throws UnknownHostException {
        InetAddress[] netAddresses = resolveAddresses(domain);
        if (isLoopbackOrWildcard(netAddresses)) {
            log.error("Can't connect to {} URL {} - URL is a loopback or wildcard address, which is configured as "
                    + "blocked.", repoKey, url);
            return true;
        }
        boolean isSiteLocalBlocked = useRemoteRepoStrictPolicy && Arrays.stream(netAddresses)
                .anyMatch(address -> address.isSiteLocalAddress() || address.isLinkLocalAddress());
        if (isSiteLocalBlocked) {
            log.error("Can't connect to {} URL {} - URL is a site/link local address, which is configured as blocked",
                    repoKey, url);
            return true;
        }

        if (isJFrogInternalHost(domain)) {
            log.error("Can't connect to {} URL {} - URL resolves to a JFrog internal host, which is blocked.",
                    repoKey, url);
            return true;
        }
        return false;
    }

    /**
     * True if <em>any</em> address the domain resolved to is loopback or the wildcard address. Every resolved
     * address counts, because the outbound client is free to connect to any of them; the wildcard forms
     * ({@code 0.0.0.0}, {@code ::}, and short spellings such as {@code http://0/}) need naming separately because
     * they reach the local host without being reported as loopback.
     */
    private static boolean isLoopbackOrWildcard(InetAddress[] netAddresses) {
        return Arrays.stream(netAddresses)
                .anyMatch(address -> address.isLoopbackAddress() || address.isAnyLocalAddress());
    }

    private boolean isJFrogInternalHost(@Nonnull String domain) {
        return jfrogInternalBlockedHosts.stream()
                .anyMatch(blocked -> domain.equals(blocked) || domain.endsWith("." + blocked));
    }

    /**
     * The host that {@code url} denotes, derived exactly the way the outbound Apache HTTP client derives the host
     * it connects to, then canonicalized by {@link #canonicalizeHost(String)}.
     * <p>
     * {@link URI#getHost()} on its own is not sufficient, and using it was the RTDEV-95692 bypass: it returns
     * {@code null} for percent-encoded hosts ({@code http://%31%32%37%2E%30%2E%30%2E%31/}), short-form IPv4
     * ({@code http://127.1/}) and trailing-dot literals ({@code http://127.0.0.1./}), each of which
     * {@link URIUtils#extractHost(URI)} - and therefore the connection itself - resolves back to loopback.
     *
     * @throws UndeterminedHostException if no host can be derived at all, in which case callers must fail closed
     */
    @VisibleForTesting
    static String getDomainName(@Nonnull String url) throws URISyntaxException, UndeterminedHostException {
        return getUrlIdentity(url).host();
    }

    /**
     * True if {@code url} names no destination of its own, so it will be appended to a base url rather than connected
     * to. Package handlers pass one as the outbound "url" of a remote fetch - the bare npm package name
     * {@code express}, a cargo crate path, a docker digest such as {@code sha256:abc...} - and evaluating it blocks
     * ordinary package traffic, because no host can be derived from a fragment and that fails closed (RTDEV-96436).
     * <p>
     * Both clauses are needed, because each covers the other's blind spot. Requiring no authority alone would skip
     * {@code file:///etc/passwd} and {@code http:///x}: an empty authority reads as {@code null} on {@link URI},
     * while {@link HttpUtils#isAbsolute(String)} - the predicate {@code RemoteRepoBase.appendAndGetUrl} uses to
     * decide verbatim use - calls them absolute, so they are connected to rather than appended. Requiring only
     * {@code !isAbsolute} would skip every bracketed IPv6 url, since that predicate escapes each colon outside
     * {@code ://} and so cannot parse {@code http://[::1]:8046/} at all.
     * <p>
     * The scheme deliberately decides nothing. A first segment holding a colon parses as one - {@code sha256:abc...}
     * is a {@link URI} with scheme {@code sha256} - yet names no host and is appended like any other path, so
     * requiring the scheme to be absent rejected docker digests outright. Conversely an authority is a destination
     * without any scheme: a protocol-relative {@code //host/path} arriving as a redirect {@code Location} is
     * resolved against the request url by the HTTP client, and reaches {@code host}.
     * <p>
     * Three entry points skip these: {@link #isOutboundFetchBlocked(String, String)},
     * {@link #isOutboundTargetBlocked(String, String)} and {@link #isRedirectTargetBlocked(String, String)}. On the
     * redirect floor the reasoning holds for a different base - a {@code Location} stating no authority is resolved
     * by the client against the request url, whose host this class cleared before that request went out - so allowing
     * it is correct there, not merely harmless, and does not rest on {@code UrlVerifierRedirectStrategy} filtering
     * such Locations out beforehand, which it happens to do.
     * <p>
     * {@link #isRemoteRepoBlocked(String, String)} is the one that does not skip: it validates a
     * repository's own base url, where naming no host is invalid configuration and must keep failing closed at save
     * time rather than failing later inside the HTTP client.
     */
    private static boolean isRelativeReference(@Nullable String url) {
        if (StringUtils.isBlank(url)) {
            // Names no destination either, so the answer matches the contract rather than relying on the callers
            // having asked their own blank question first. The guard also has to stay for null: both the parse below
            // and HttpUtils.isAbsolute() would throw a NullPointerException on one, which neither of them catches.
            return true;
        }
        try {
            return new URI(url).getRawAuthority() == null && !HttpUtils.isAbsolute(url);
        } catch (URISyntaxException e) {
            // A string that will not parse is a fragment too, and for the same reason: the predicate deciding
            // verbatim use reports this very failure as "not absolute", so the assembler appends it. Nothing can
            // connect to it, and no host could be derived from it to authorize, so evaluating it only produced a
            // warning per fetch. Paths carrying a space or a lone '%' land here, both of which RFC 3986 forbids.
            log.trace("URL '{}' does not parse as a URI, so it is taken as a path appended to a base url", url, e);
            return true;
        }
    }

    /**
     * The scheme, derived host, port ({@code -1} when the URL does not state one) and path of {@code url}. The path
     * is normalized, so that a path-scoped whitelist entry is compared against where the URL actually points
     * rather than how it is spelled; {@link #matchesIdentity(UrlIdentity, UrlIdentity)} rejects what normalization
     * cannot resolve. Normalization touches the path only, leaving the derived host untouched.
     * <p>
     * A protocol-relative {@code //host/path} needs a scheme borrowed before its host can be derived, because
     * {@link URIUtils#extractHost(URI)} - which follows what the client does - answers only for an absolute uri. The
     * client resolves such a reference against the request url and reaches the authority it states, so the host is
     * derived from a scheme-prefixed copy rather than the reference being refused: failing closed on the missing
     * scheme rejected every mirror or CDN redirect sent as {@code Location: //cdn.example/...}, while making the
     * loopback cases look checked when they were only unparseable. The scheme itself stays unknown, since the real
     * one comes from the request, so a whitelist entry cannot match on it.
     */
    private static UrlIdentity getUrlIdentity(@Nonnull String url)
            throws URISyntaxException, UndeterminedHostException {
        URI uri = new URI(url).normalize();
        HttpHost host = URIUtils.extractHost(uri);
        if (host == null && uri.getScheme() == null && uri.getRawAuthority() != null) {
            host = URIUtils.extractHost(new URI("http:" + url).normalize());
        }
        if (host == null) {
            throw new UndeterminedHostException(url);
        }
        String domain = canonicalizeHost(host.getHostName());
        if (domain.isEmpty()) {
            throw new UndeterminedHostException(url);
        }
        String path = uri.getPath();
        return new UrlIdentity(StringUtils.lowerCase(uri.getScheme(), Locale.ROOT), domain, host.getPort(),
                path == null ? "" : path);
    }

    /**
     * True if {@code url} states an authority but no scheme, the protocol-relative form.
     * {@link #getUrlIdentity(String)} derives a host from one, which every caller wants except
     * {@link #isRemoteRepoBlocked(String, String)}: a repository url has to carry its own scheme, so there
     * this shape is invalid configuration rather than a reference something else will complete.
     */
    private static boolean statesAuthorityWithoutScheme(@Nullable String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.getScheme() == null && uri.getRawAuthority() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Normalizes a derived host so that equal destinations compare equal: a trailing dot is dropped (it marks the
     * name as fully qualified and denotes the same target, while the resolver accepts the literal on some
     * platforms and rejects it on others), and the host is lower-cased so that neither the JFrog-internal host
     * list nor the denylist - both exact string matches - can be evaded by casing alone.
     * <p>
     * A leading {@code www.} is deliberately kept, unlike before RTDEV-95692. {@code www.example.com} and
     * {@code example.com} are separate names that may resolve to different addresses, so dropping the prefix
     * pointed the address check at a different host than the outbound client connects to: a repository at
     * {@code http://www.attacker.test/} was cleared by resolving {@code attacker.test} to a routable address while
     * the client resolved {@code www.attacker.test} and connected to wherever that pointed, loopback included.
     * That is the same check/connect desync this class exists to prevent. The stripped form is still consulted
     * where the host is compared as a name rather than resolved - see {@link #isDenylisted(String)}.
     */
    private static String canonicalizeHost(@Nonnull String hostName) {
        return StringUtils.stripEnd(hostName, ".").toLowerCase(Locale.ROOT);
    }

    private static List<WhitelistEntry> parseWhitelist(@Nullable String whitelistedPrefixes) {
        if (whitelistedPrefixes == null) {
            return List.of();
        }
        List<WhitelistEntry> entries = Lists.newArrayList();
        for (String prefix : StringUtils.split(whitelistedPrefixes, ",")) {
            String rawPrefix = prefix.trim();
            if (rawPrefix.isEmpty()) {
                continue;
            }
            try {
                entries.add(new WhitelistEntry(rawPrefix, getUrlIdentity(rawPrefix)));
            } catch (Exception e) {
                log.warn("Ignoring remote repository URL whitelist entry '{}' - no host could be determined from "
                        + "it, so it cannot be matched safely. Reason: {}", rawPrefix, e.getMessage());
            }
        }
        return List.copyOf(entries);
    }

    @VisibleForTesting
    boolean isLoopbackBridgeUrl(@Nonnull String url) {
        try {
            UrlIdentity identity = getUrlIdentity(url);
            InetAddress[] netAddresses = resolveAddresses(identity.host());
            // allMatch rather than anyMatch: this grants an exemption, so a domain that also answers a routable
            // address must not earn it.
            if (Arrays.stream(netAddresses).allMatch(InetAddress::isLoopbackAddress)
                    && matchesRouterPortAndBridgePath(identity)) {
                log.trace("URL '{}' is not blocked. Considered as loopback Bridge URL", url);
                return true;
            }
        } catch (UnknownHostException e) {
            // Already logged once per cache window by resolveAddresses() - avoid re-logging on every request.
            log.trace("URL '{}' domain could not be resolved; not treated as a loopback Bridge URL", url);
        } catch (UndeterminedHostException e) {
            // A url naming no host cannot be a Bridge url, which is an ordinary negative answer here rather than a
            // failure of the check. It reaches us for every relative alternative-download path a package handler
            // passes (e.g. the bare npm package name "express"), so logging it as a fault put a warning and a
            // stack trace in the service log on every such fetch. What the missing host means for the request is
            // decided by checkBlocked(), which fails closed on it.
            log.trace("URL '{}' names no host; not treated as a loopback Bridge URL", url);
        } catch (Exception e) {
            log.warn("Failed to perform bridge URL check for url '{}'", url, e);
        }
        return false;
    }

    /**
     * Resolves {@code domain} to every {@link InetAddress} it maps to, via a live lookup every time - the outcome
     * is never cached, since it feeds SSRF authorization decisions and the outbound HTTP client resolves the same
     * domain again, independently, when it opens the connection. Caching it (positive or negative) would let
     * an attacker pass this check once, then rebind the domain to a private/loopback address and keep passing
     * for the rest of the cache window - notably including the negative case, since
     * {@code remoteRepoBlockUrlOnUnknownHost} defaults to fail-open. Only the resulting log line for an
     * unresolvable domain is deduplicated (once per cache window), to fix the log-volume regression from
     * RTDEV-93356's per-request floor checks without weakening the check itself.
     * <p>
     * Every address is returned rather than just the first: a domain answering both a routable and a loopback
     * address would otherwise clear this check while the client stayed free to connect to either.
     */
    private InetAddress[] resolveAddresses(String domain) throws UnknownHostException {
        try {
            return InetAddress.getAllByName(domain);
        } catch (UnknownHostException e) {
            logUnresolvedDomainOnce(domain, e);
            throw e;
        }
    }

    /**
     * Deduplicated like the unresolved-domain warning next to it, and for the same reason: the floors resolve live on
     * every request, so an upstream that keeps redirecting to a url naming no host - or a url that will not parse -
     * would otherwise put a warning in the service log once per request (RTDEV-93356). The scheme-less repository
     * url guard (RTDEV-95692) shares this same cache and message shape rather than logging on its own, for the same
     * reason: {@code isRemoteRepoBlocked} is checked on every remote download, not only at config-save time.
     * <p>
     * Keyed on the repository and the url up to any query or fragment, in a cache of its own. Keying on the whole url
     * would let a changing query string defeat the throttling one request at a time, and sharing the DNS cache would
     * have these entries evicting domains from it, which is what keeps that warning throttled. The full url is still
     * in the debug line for the requests that are suppressed here.
     *
     * @param reason why the url could not be evaluated, appended verbatim to both the throttled and the debug line -
     *               callers pass their own message rather than an exception so each keeps its own wording.
     */
    private void logUndeterminedUrlOnce(String url, String repoKey, String reason) {
        String key = repoKey + '|' + StringUtils.substringBefore(StringUtils.substringBefore(url, "#"), "?");
        if (loggedUndeterminedUrlCache.asMap().putIfAbsent(key, Boolean.TRUE) == null) {
            log.warn("Can't connect to {} URL {} - {}. Suppressing repeat log lines for {}s.", repoKey, url,
                    reason, ConstantValues.remoteRepoUrlVerifierCacheExpirySeconds.getInt());
            return;
        }
        log.debug("Can't connect to {} URL {} - {}.", repoKey, url, reason);
    }

    private void logUnresolvedDomainOnce(@Nullable String domain, UnknownHostException e) {
        if (domain == null) {
            return;
        }
        // putIfAbsent is atomic on the cache's underlying map, so under concurrent failures for the same
        // domain only the thread that actually wins the insert logs the warning.
        if (loggedUnresolvedDnsCache.asMap().putIfAbsent(domain, Boolean.TRUE) == null) {
            log.warn("Unable to resolve domain '{}'. Reason: {}. Suppressing repeat log lines for {}s.",
                    domain, e.getMessage(), ConstantValues.remoteRepoUrlVerifierCacheExpirySeconds.getInt());
        }
    }

    /**
     * True if the full URI path is a JFrog Bridge route: it matches {@link #bridgeUrlPathPattern}
     * ({@link Pattern#matches()}), i.e. starts with {@code /bridge-} (e.g.
     * {@code /bridge-xxx/artifactory/repo/}). Not {@code /api/v1/bridge-...} and not
     * {@code /artifactory/bridge-...} (repo key).
     */
    public static boolean isBridgePath(@Nullable String path) {
        if (path == null) {
            return false;
        }
        return bridgeUrlPathPattern.matcher(path).matches();
    }

    /**
     * True if {@code url} parses as a URI whose path {@linkplain #isBridgePath(String) is a Bridge route}.
     */
    public static boolean isBridgeUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        try {
            String path = URI.create(url).getPath();
            return isBridgePath(path);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True if {@code loopbackUrl} targets the internal router port on a Bridge route. The URI is normalized and any
     * surviving {@code ..} segment rejected before the path is matched, so a traversal such as
     * {@code /bridge-x/../router/api} cannot borrow the Bridge exemption to reach a different route on that port.
     * The rejection holds however deeply the segment is encoded - see
     * {@link #containsTraversalAtAnyEncodingDepth(String)} - because the path pattern deliberately admits the whole
     * route below {@code /bridge-&lt;instance&gt;/}, which leaves climbing out of it the only way to reach another
     * route on that port.
     */
    private boolean matchesRouterPortAndBridgePath(@Nonnull UrlIdentity identity) {
        if (routerInternalPort == 0) {
            routerInternalPort = configService.getSystemConfiguration().getSysConfig().helper().getRouterPort();
            log.debug("Using router internal port '{}' for Bridge URL matching", routerInternalPort);
        }
        // The port and path come from the identity already derived for this URL rather than from a second parse, so
        // the exemption is decided on the same host and port the rest of the class authorized. Reading
        // URI.getPort() here instead returned -1 for the encoded host spellings, which denied the exemption for a
        // URL denoting exactly the destination the plain spelling is exempted for.
        String path = identity.path();
        if (containsTraversalAtAnyEncodingDepth(path)) {
            return false;
        }
        return routerInternalPort == identity.port() && bridgeUrlPathPattern.matcher(path).matches();
    }

    /**
     * An admin-configured whitelist prefix, kept both as the raw prefix it was configured as and as its parsed
     * {@link UrlIdentity}. See {@link #isUrlWhitelisted(String)} for why both are needed.
     */
    private record WhitelistEntry(String rawPrefix, UrlIdentity identity) {
    }

    /**
     * What a URL actually denotes, as opposed to how it is spelled. {@code port} is {@code -1} when the URL does
     * not state one, and {@code path} is {@code ""} rather than null when it has none.
     */
    private record UrlIdentity(String scheme, String host, int port, String path) {
    }

    /**
     * Thrown when no host can be derived from a URL at all. Deliberately distinct from {@link UnknownHostException},
     * which means a host <em>was</em> derived but DNS could not resolve it: only the former must fail closed. A host
     * we cannot determine is one whose destination we cannot evaluate even though the client will still connect to
     * it, whereas a host that does not resolve for us does not resolve for the outbound client either.
     */
    @VisibleForTesting
    static class UndeterminedHostException extends Exception {

        UndeterminedHostException(String url) {
            super("could not determine the host that '" + url + "' would connect to");
        }
    }
}
