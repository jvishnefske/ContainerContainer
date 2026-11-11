package org.artifactory.security.access;

import lombok.SneakyThrows;
import org.jfrog.common.JsonUtils;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessCookieParserTest {

    @SneakyThrows
    @Test
    void testCookieParsing() {
        Cookie baseCookie = buildCookie("tokencrowd");
        String cookie = JsonUtils.getInstance().valueToString(baseCookie);
        Class<?> cookieParserClass = Class.forName("org.artifactory.security.access.providers.AccessCookieParser");
        Method parse = cookieParserClass.getMethod("parse", String.class);
        Field modifiersField = cookieParserClass.getDeclaredField("mapper");
        modifiersField.setAccessible(true);
        parse.setAccessible(true);
        Cookie fromString = (Cookie) parse.invoke(null, cookie);

        assertEquals(fromString.getDomain(), baseCookie.getDomain());
        assertEquals(fromString.getName(), baseCookie.getName());
        assertEquals(fromString.getValue(), baseCookie.getValue());
        assertEquals(fromString.getSecure(), baseCookie.getSecure());
        assertEquals(fromString.getMaxAge(), baseCookie.getMaxAge());
    }

    @SneakyThrows
    @Test
    void testCookieParsingNotEqual() {
        Cookie baseCookie = buildCookie("tokencrowd");
        String cookie = JsonUtils.getInstance().valueToString(baseCookie);

        Cookie secondCookie = buildCookie("random");
        String secondCookieString = JsonUtils.getInstance().valueToString(secondCookie);

        assertNotEquals(secondCookieString, cookie);
        Class<?> cookieParserClass = Class.forName("org.artifactory.security.access.providers.AccessCookieParser");
        Method parse = cookieParserClass.getMethod("parse", String.class);
        Field modifiersField = cookieParserClass.getDeclaredField("mapper");
        modifiersField.setAccessible(true);
        parse.setAccessible(true);
        Cookie fromString = (Cookie) parse.invoke(null, cookie);
        Cookie fromSecondString = (Cookie) parse.invoke(null, secondCookieString);

        assertNotEquals(fromString.getValue(), fromSecondString.getValue());
        assertEquals(fromString.getDomain(), baseCookie.getDomain());
    }

    @SneakyThrows
    @Test
    void passEmptyStringToParser() {
        String cookie = "";
        Class<?> cookieParserClass = Class.forName("org.artifactory.security.access.providers.AccessCookieParser");
        Method parse = cookieParserClass.getMethod("parse", String.class);
        Field modifiersField = cookieParserClass.getDeclaredField("mapper");
        modifiersField.setAccessible(true);
        parse.setAccessible(true);
        Cookie fromString = (Cookie) parse.invoke(null, cookie);
        assertNull(fromString);
    }

    @SneakyThrows
    @Test
    void testParseCookieWithNullFields() {
        String cookie = "{\"name\":\"crowd.token_key\",\"value\":\"hQ8QQTafzGD0u5CQQTt0hQAAAAAAAgABbW9zaGV5\"}";
        Class<?> cookieParserClass = Class.forName("org.artifactory.security.access.providers.AccessCookieParser");
        Method parse = cookieParserClass.getMethod("parse", String.class);
        Field modifiersField = cookieParserClass.getDeclaredField("mapper");
        modifiersField.setAccessible(true);
        parse.setAccessible(true);
        Cookie fromString = (Cookie) parse.invoke(null, cookie);
        assertEquals("crowd.token_key", fromString.getName());
        assertEquals("hQ8QQTafzGD0u5CQQTt0hQAAAAAAAgABbW9zaGV5", fromString.getValue());
        assertEquals(0, fromString.getMaxAge());
        assertNull(fromString.getPath());
        assertTrue(fromString.getDomain().isEmpty());
        assertFalse(fromString.isHttpOnly());
        assertFalse(fromString.getSecure());
    }

    private Cookie buildCookie(String token) {
        String cookieName = "crowdToken";
        Cookie responseCookie = new Cookie(cookieName, token);
        responseCookie.setPath("/");

        responseCookie.setDomain("artifactory");
        responseCookie.setSecure(true);
        responseCookie.setHttpOnly(true);
        responseCookie.setMaxAge(32);
        return responseCookie;
    }
}