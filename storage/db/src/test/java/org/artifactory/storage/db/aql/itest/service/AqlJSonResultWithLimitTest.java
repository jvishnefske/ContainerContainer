package org.artifactory.storage.db.aql.itest.service;

import lombok.SneakyThrows;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.artifactory.storage.db.aql.itest.service.AqlJsonHelper.compareJsons;
import static org.artifactory.storage.db.aql.itest.service.AqlJsonHelper.load;
import static org.artifactory.storage.db.aql.itest.service.AqlJsonHelper.readCompact;

/**
 * @author William Arustamyan
 */
public class AqlJSonResultWithLimitTest extends AqlAbstractServiceTest {

    @BeforeMethod
    public void before() {
        RequestThreadLocal.bind(new RequestWrapper(new MockHttpServletRequest()));
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.aqlSearchQueryMaxLimit, "20000");
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.aqlSearchNotifyReachHardLimit, "false");
    }

    @Test
    @SneakyThrows
    public void testQueryWithDefaultLimit() {
        final String query = "items.find({\n" +
                "  \"repo\": \"repo1\"\n" +
                "})";
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.aqlSearchQueryMaxLimit, "20000");
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(query)) {
            String result = readCompact(aqlStreamer);
            String expectation = load("/aql/stream/itemsWithDefaultLimit.json");
            compareJsons(result, expectation);
        }
    }

    @Test
    @SneakyThrows
    public void testQueryWithLimit() {
        final String query = "items.find({\n" +
                "  \"repo\": \"repo1\"\n" +
                "}).limit(100)";
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(query)) {
            String result = readCompact(aqlStreamer);
            String expectation = load("/aql/stream/itemsWithRequestLimitAccepted.json");
            compareJsons(result, expectation);
        }
    }

    @Test
    @SneakyThrows
    public void testQueryWithLimitMoreThenDefault() {
        final String query = "items.find({\n" +
                "  \"repo\": \"repo1\"\n" +
                "}).limit(20001)";
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(query)) {
            String result = readCompact(aqlStreamer);
            String expectation = load("/aql/stream/itemsWithDefaultLimit.json");
            compareJsons(result, expectation);
        }
    }

    @Test
    @SneakyThrows
    public void testQueryWithNegativeLimit() {
        final String query = "items.find({\n" +
                "  \"repo\": \"repo1\"\n" +
                "}).limit(-1)";
        try (AqlRestResult aqlStreamer = aqlService.executeQueryLazy(query)) {
            String result = readCompact(aqlStreamer);
            String expectation = load("/aql/stream/itemsWithDefaultLimit.json");
            compareJsons(result, expectation);
        }
    }

    private static class MockHttpServletRequest implements HttpServletRequest {

        @Override
        public String getAuthType() {
            return "";
        }

        @Override
        public Cookie[] getCookies() {
            return new Cookie[0];
        }

        @Override
        public long getDateHeader(String s) {
            return 0;
        }

        @Override
        public String getHeader(String s) {
            return "";
        }

        @Override
        public Enumeration<String> getHeaders(String s) {
            return null;
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return null;
        }

        @Override
        public int getIntHeader(String s) {
            return 0;
        }

        @Override
        public String getMethod() {
            return "";
        }

        @Override
        public String getPathInfo() {
            return "search/aql";
        }

        @Override
        public String getPathTranslated() {
            return "";
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getQueryString() {
            return "";
        }

        @Override
        public String getRemoteUser() {
            return "";
        }

        @Override
        public boolean isUserInRole(String s) {
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return "";
        }

        @Override
        public String getRequestURI() {
            return "";
        }

        @Override
        public StringBuffer getRequestURL() {
            return null;
        }

        @Override
        public String getServletPath() {
            return "";
        }

        @Override
        public HttpSession getSession(boolean b) {
            return null;
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public ServletConnection getServletConnection() {
            return null;
        }

        @Override
        public String changeSessionId() {
            return "";
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse httpServletResponse)
                throws IOException, ServletException {
            return false;
        }

        @Override
        public void login(String s, String s1) throws ServletException {

        }

        @Override
        public void logout() throws ServletException {

        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
            return List.of();
        }

        @Override
        public Part getPart(String s) throws IOException, ServletException {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass)
                throws IOException, ServletException {
            return null;
        }

        @Override
        public Object getAttribute(String s) {
            return null;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return null;
        }

        @Override
        public String getCharacterEncoding() {
            return "";
        }

        @Override
        public void setCharacterEncoding(String s) throws UnsupportedEncodingException {

        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public long getContentLengthLong() {
            return 0;
        }

        @Override
        public String getContentType() {
            return "";
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return null;
        }

        @Override
        public String getParameter(String s) {
            return "";
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return null;
        }

        @Override
        public String[] getParameterValues(String s) {
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Map.of();
        }

        @Override
        public String getProtocol() {
            return "";
        }

        @Override
        public String getScheme() {
            return "";
        }

        @Override
        public String getServerName() {
            return "";
        }

        @Override
        public int getServerPort() {
            return 0;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return "";
        }

        @Override
        public String getRemoteHost() {
            return "";
        }

        @Override
        public void setAttribute(String s, Object o) {

        }

        @Override
        public void removeAttribute(String s) {

        }

        @Override
        public Locale getLocale() {
            return null;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String s) {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return "";
        }

        @Override
        public String getLocalAddr() {
            return "";
        }

        @Override
        public int getLocalPort() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            return null;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
                throws IllegalStateException {
            return null;
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType() {
            return null;
        }

        @Override
        public String getRequestId() {
            return null;
        }

        @Override
        public String getProtocolRequestId() {
            return null;
        }
    }
}
