package org.artifactory.security.access.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.Optional;

/**
 * @author Mor Merhav
 * Used to parse the cookie JSON coming from Access login
 */

@UtilityClass
class AccessCookieParser {

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public Cookie handleMissingInstantiator(DeserializationContext ctxt, Class<?> instClass,
                    ValueInstantiator valueInsta, JsonParser p, String msg) throws IOException {
                InternalCookie internalCookie = p.readValueAs(InternalCookie.class);
                Cookie parsedCookie = new Cookie(internalCookie.name(), internalCookie.value());
                parsedCookie.setHttpOnly(internalCookie.httpOnly);
                parsedCookie.setPath(internalCookie.path);
                parsedCookie.setSecure(internalCookie.secure);
                parsedCookie.setMaxAge(internalCookie.maxAge);
                parsedCookie.setDomain(Optional.ofNullable(internalCookie.domain).orElse(""));
                parsedCookie.setComment(internalCookie.comment);
                return parsedCookie;
            }
        });
    }

    /**
     * Parses the cookie JSON into {@link Cookie}
     *
     * @param cookieJson the cookie JSON representation
     * @return the cookie object if JSON is non-null, null otherwise.
     */
    @SneakyThrows
    public Cookie parse(String cookieJson) {
        if (cookieJson == null || cookieJson.isBlank()) {
            return null;
        }
        return mapper.readValue(cookieJson, Cookie.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InternalCookie(String name, String value, int version, String comment
            , String domain, int maxAge, String path, boolean secure, boolean httpOnly) {
    }
}
