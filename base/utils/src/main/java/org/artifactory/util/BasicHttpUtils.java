package org.artifactory.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BasicHttpUtils {

    /**
     * @param status The (http based) response code
     * @return True if the code symbols a successful request cycle (i.e., in the 300-399 range)
     */
    public static boolean isRedirectionResponseCode(int status) {
        return HttpStatus.SC_MULTIPLE_CHOICES <= status && status <= 399;
    }

    /**
     * @param status The (http based) response code
     * @return True if the code symbols a successful request cycle (i.e., in the 200-299 range)
     */
    public static boolean isSuccessfulResponseCode(int status) {
        return HttpStatus.SC_OK <= status && status <= 299;
    }

    /**
     * @param status The (http based) response code
     * @return True if the code symbols a successful request cycle (i.e., in the 200-399 range)
     */
    public static boolean isInSuccessOrRedirectResponseCodeRange(int status) {
        return isSuccessfulResponseCode(status) || isRedirectionResponseCode(status);
    }

    public static String encodeQuery(String unescaped) {
        try {
            return URIUtil.encodeQuery(unescaped, "UTF-8");
        } catch (HttpException e) {
            // Nothing to do here, we will return the un-escaped value.
            log.warn("Could not encode path '{}' with UTF-8 charset, returning the un-escaped value.", unescaped);
        }
        return unescaped;
    }

    /**
     * Checks whether a given query string is encoded in UTF-8.
     *
     * @param query the query string to check
     * @return {@code true} if the query is encoded in UTF-8, {@code false} otherwise
     */
    public static boolean isEncoded(String query) {
        if (StringUtils.isBlank(query)) {
            return false;
        }
        try {
            String decoded = URIUtil.decode(query, "UTF-8");
            return !query.equals(decoded);
        } catch (HttpException e) {
            log.debug("Could not decode path '{}' with UTF-8 charset", query);
            return false;
        }
    }

}
