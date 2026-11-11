package org.artifactory.lifecycle.webapp.servlet;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import jakarta.servlet.ServletContext;
import java.io.File;

import static com.jfrog.sysconf.SysConfigConstants.KEY_JF_PRODUCT_HOME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
@Test
public class LogbackConfigListenerTest {

    @Test
    public void resolveHomeDir() {
        ServletContext servletContextMock = mock(ServletContext.class);
        File expectedHome = new File(System.getProperty("java.io.tmpdir"));
        when(servletContextMock.getInitParameter(KEY_JF_PRODUCT_HOME)).thenReturn(expectedHome.getAbsolutePath());

        LogbackConfigListener listener = new LogbackConfigListener();
        File resolvedHome = listener.resolveHomeDir(servletContextMock);
        Assert.assertEquals(expectedHome, resolvedHome);
    }

    @Test
    public void resolveHomeDirWithSystemProp() {
        File expectedHome = new File(System.getProperty("java.io.tmpdir"));
        setHomeDir(expectedHome.getAbsolutePath());
        ServletContext servletContextMock = mock(ServletContext.class);
        when(servletContextMock.getInitParameter(KEY_JF_PRODUCT_HOME)).thenReturn(null);

        LogbackConfigListener listener = new LogbackConfigListener();
        File resolvedHome = listener.resolveHomeDir(servletContextMock);
        Assert.assertEquals(expectedHome, resolvedHome);
    }

    private void setHomeDir(String productHome) {
        System.setProperty("jf." + KEY_JF_PRODUCT_HOME, productHome);
    }

    @AfterMethod
    void cleanSystemProps() {
        System.clearProperty("jf." + KEY_JF_PRODUCT_HOME);
    }

}