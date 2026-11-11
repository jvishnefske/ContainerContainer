package org.artifactory.api.context;

import org.artifactory.common.home.ArtifactoryHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArtifactoryContextUtils}
 *
 * @author oleksandrl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArtifactoryContextUtilsTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactoryHome home;

    @BeforeEach
    void setUp() {
        when(context.getArtifactoryHome()).thenReturn(home);
    }

    @AfterEach
    void tearDown() {
        // Clean up any bound context/home after each test
        try {
            ArtifactoryContextThreadBinder.unbind();
            ArtifactoryHome.unbind();
        } catch (Exception e) {
            // Ignore - may not be bound
        }
    }

    @Test
    void supplyWithContext_shouldBindContextAndExecuteSupplier_whenCalledFromUnboundThread() {
        // Arrange
        Supplier<String> supplier = () -> "test-result";
        // Act
        String result = ArtifactoryContextUtils.supplyWithContext(supplier, context, "Test operation failed");
        // Assert
        assertEquals("test-result", result);
    }

    @Test
    void supplyWithContext_shouldReturnSupplierResult_whenSupplierReturnsValue() {
        // Arrange
        Supplier<Long> supplier = () -> 12345L;
        // Act
        Long result = ArtifactoryContextUtils.supplyWithContext(supplier, context, "Failed to get count");
        // Assert
        assertEquals(12345L, result);
    }

    @Test
    void supplyWithContext_shouldUnbindContext_whenSupplierCompletesSuccessfully() {
        // Arrange
        Supplier<String> supplier = () -> "success";
        // Act
        ArtifactoryContextUtils.supplyWithContext(supplier, context, "Test failed");
        // Assert - verify context and home are unbound
        assertNull(ContextHelper.get(), "Context should be unbound after exception");
        assertThrows(IllegalStateException.class, ArtifactoryHome::get,
                "Home should be unbound after execution");
    }

    @Test
    void supplyWithContext_shouldRethrowException_whenSupplierThrowsRuntimeException() {
        // Arrange
        RuntimeException expectedException = new RuntimeException("Test exception");
        Supplier<String> supplier = () -> {
            throw expectedException;
        };
        // Act & Assert
        RuntimeException thrownException = assertThrows(RuntimeException.class,
                () -> ArtifactoryContextUtils.supplyWithContext(supplier, context, "Operation failed"));
        assertEquals(expectedException, thrownException);
    }

    @Test
    void supplyWithContext_shouldUnbindContext_whenSupplierThrowsException() {
        // Arrange
        Supplier<String> supplier = () -> {
            throw new RuntimeException("Test exception");
        };
        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> ArtifactoryContextUtils.supplyWithContext(supplier, context, "Operation failed"));
        // Verify context and home are unbound even after exception
        assertNull(ContextHelper.get(), "Context should be unbound after exception");
        assertThrows(IllegalStateException.class, ArtifactoryHome::get,
                "Home should be unbound after exception");
    }

    @Test
    void supplyWithContext_shouldWorkCorrectly_whenCalledFromAlreadyBoundThread() {
        // Arrange
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(home);
        Supplier<Integer> supplier = () -> 42;
        // Act
        Integer result = ArtifactoryContextUtils.supplyWithContext(supplier, context, "Test failed");
        // Assert
        assertEquals(42, result);
    }

    @Test
    void supplyWithContext_shouldHandleNullResult_whenSupplierReturnsNull() {
        // Arrange
        Supplier<String> supplier = () -> null;
        // Act
        String result = ArtifactoryContextUtils.supplyWithContext(supplier, context, "Test failed");
        // Assert
        assertNull(result);
    }

    @Test
    void supplyWithContext_shouldRethrowIllegalStateException_whenSupplierThrowsIt() {
        // Arrange
        IllegalStateException expectedException = new IllegalStateException("Illegal state");
        Supplier<String> supplier = () -> {
            throw expectedException;
        };
        // Act & Assert
        IllegalStateException thrownException = assertThrows(IllegalStateException.class,
                () -> ArtifactoryContextUtils.supplyWithContext(supplier, context, "State error"));
        assertEquals(expectedException, thrownException);
    }

    @Test
    void supplyWithContext_shouldAllowSupplierToAccessBoundContext_whenExecuting() {
        // Arrange
        Supplier<Boolean> supplier = () -> {
            // Verify context is bound during execution
            try {
                ContextHelper.get();
                ArtifactoryHome.get();
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        };
        // Act
        Boolean result = ArtifactoryContextUtils.supplyWithContext(supplier, context, "Context access failed");
        // Assert
        assertTrue(result, "Supplier should be able to access bound context during execution");
    }
}
