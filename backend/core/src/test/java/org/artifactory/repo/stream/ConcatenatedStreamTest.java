package org.artifactory.repo.stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class ConcatenatedStreamTest {

    @Mock
    private Supplier<Stream<String>> streamSupplier1;
    @Mock
    private Supplier<Stream<String>> streamSupplier2;
    private Stream<String> stream1;
    private Stream<String> stream2;
    private List<Supplier<Stream<String>>> streamSuppliers;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        stream1 = Stream.of("a", "b", "c");
        stream2 = Stream.of("d", "e", "f");
        streamSuppliers = Arrays.asList(streamSupplier1, streamSupplier2);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void of_createsConcatenatedStream() {
        when(streamSupplier1.get()).thenReturn(stream1);
        when(streamSupplier2.get()).thenReturn(stream2);

        Stream<String> concatenatedStream = ConcatenatedStream.of(streamSuppliers);

        assertNotNull(concatenatedStream);
        assertEquals(6, concatenatedStream.count());
    }

    @Test
    void hasNext_returnsTrueWhenThereAreMoreElements() {
        when(streamSupplier1.get()).thenReturn(stream1);
        when(streamSupplier2.get()).thenReturn(stream2);

        ConcatenatedStream.ConcatenatedStreamIterator<String> iterator =
                new ConcatenatedStream.ConcatenatedStreamIterator<>(streamSuppliers.iterator());

        assertTrue(iterator.hasNext());
    }

    @Test
    void hasNext_returnsFalseWhenThereAreNoMoreElements() {
        when(streamSupplier1.get()).thenReturn(Stream.empty());
        when(streamSupplier2.get()).thenReturn(Stream.empty());

        ConcatenatedStream.ConcatenatedStreamIterator<String> iterator =
                new ConcatenatedStream.ConcatenatedStreamIterator<>(streamSuppliers.iterator());

        assertFalse(iterator.hasNext());
    }

    @Test
    void next_returnsNextElement() {
        when(streamSupplier1.get()).thenReturn(stream1);
        when(streamSupplier2.get()).thenReturn(stream2);

        ConcatenatedStream.ConcatenatedStreamIterator<String> iterator =
                new ConcatenatedStream.ConcatenatedStreamIterator<>(streamSuppliers.iterator());

        assertEquals("a", iterator.next());
    }

    @Test
    void next_throwsExceptionWhenThereAreNoMoreElements() {
        when(streamSupplier1.get()).thenReturn(Stream.empty());
        when(streamSupplier2.get()).thenReturn(Stream.empty());

        ConcatenatedStream.ConcatenatedStreamIterator<String> iterator =
                new ConcatenatedStream.ConcatenatedStreamIterator<>(streamSuppliers.iterator());

        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void closeCurrentStream_closesCurrentStream() {
        Stream<String> stream1Spy = spy(stream1);
        when(streamSupplier1.get()).thenReturn(stream1Spy);

        ConcatenatedStream.ConcatenatedStreamIterator<String> iterator =
                new ConcatenatedStream.ConcatenatedStreamIterator<>(streamSuppliers.iterator());
        iterator.hasNext();  // to open the first stream
        iterator.closeCurrentStream();
        verify(stream1Spy).close();
    }
}