package org.artifactory.ph;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageConfigChangeTypeTest {

    @Mock
    CentralConfigDescriptor oldDescriptor;

    @Mock
    CentralConfigDescriptor newDescriptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        Mockito.verifyNoMoreInteractions(oldDescriptor, newDescriptor);
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("testBaseUrlChangeProvider")
    void testBaseUrlChange(String newDescUrl, String oldDescUrl) {
        when(newDescriptor.getUrlBase()).thenReturn(newDescUrl);
        when(oldDescriptor.getUrlBase()).thenReturn(oldDescUrl);
        Assertions.assertTrue(PackageConfigChangeType.BASE_URL.isChanged(newDescriptor, oldDescriptor));
        verify(newDescriptor, atLeastOnce()).getUrlBase();
        verify(oldDescriptor, atLeastOnce()).getUrlBase();
    }

    @ParameterizedTest
    @MethodSource("testBaseUrlNotChangeProvider")
    void testBaseUrlNotChanged(String newDescUrl, String oldDescUrl) {
        when(newDescriptor.getUrlBase()).thenReturn(newDescUrl);
        when(oldDescriptor.getUrlBase()).thenReturn(oldDescUrl);
        Assertions.assertFalse(PackageConfigChangeType.BASE_URL.isChanged(newDescriptor, oldDescriptor));
        verify(newDescriptor, atLeastOnce()).getUrlBase();
        verify(oldDescriptor, atLeastOnce()).getUrlBase();
    }

    @Test
    void testBaseUrlChangeWithNullOldDescriptor() {
        when(newDescriptor.getUrlBase()).thenReturn("http://localhost:8082");
        Assertions.assertFalse(PackageConfigChangeType.BASE_URL.isChanged(newDescriptor, null));
    }

    private static Object[][] testBaseUrlChangeProvider() {
        //newDescUrl,oldDescUrl
        return new Object[][] {{"http://localhost:8082", ""},
                {"", "http://localhost:8082"},
                {"http://localhost:8082", null},
                {null, "http://localhost:8082"}
        };
    }

    private static Object[][] testBaseUrlNotChangeProvider() {
        //newDescUrl,oldDescUrl
        return new Object[][] {{"http://localhost:8082", "http://localhost:8082"},
                {null, null},
                {"", null}
        };
    }
}