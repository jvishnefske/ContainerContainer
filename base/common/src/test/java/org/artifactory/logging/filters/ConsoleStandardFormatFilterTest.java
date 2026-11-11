package org.artifactory.logging.filters;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static ch.qos.logback.core.spi.FilterReply.ACCEPT;
import static ch.qos.logback.core.spi.FilterReply.DENY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author Dor Solomon
 */
class ConsoleStandardFormatFilterTest {
    @Mock
    ArtifactoryHome home;
    @Mock
    ConsoleJsonFormatFilter consoleJsonFormatFilter;
    ConsoleStandardFormatFilter consoleStandardFormatFilter;
    AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(home);
        consoleStandardFormatFilter = new ConsoleStandardFormatFilter();
        ReflectionTestUtils.setField(consoleStandardFormatFilter, "jsonFormatFilter", consoleJsonFormatFilter);
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testDecide() {
        when(consoleJsonFormatFilter.decide(null)).thenReturn(ACCEPT);
        assertEquals(DENY, consoleStandardFormatFilter.decide(null));
        when(consoleJsonFormatFilter.decide(null)).thenReturn(DENY);
        assertEquals(ACCEPT, consoleStandardFormatFilter.decide(null));
    }
}