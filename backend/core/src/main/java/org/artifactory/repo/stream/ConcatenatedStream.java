package org.artifactory.repo.stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This utility class provides a way to concatenate multiple streams into a single stream.
 * It takes a collection of stream suppliers and treats them as a single concatenated stream.
 * This is useful when you have multiple streams that you want to process as a single stream.
 * <p>
 * The class is not thread-safe, which means it should not be used from multiple threads concurrently.
 * It ensures that each stream is closed after it has been consumed.
 * <p>
 * The main reason for creating this class is to handle a unique case where we cannot afford to open all streams
 * prior to calling this function.
 * If we were to open all streams in advance and then flatten/concatenate them, we would hold multiple DB connections
 * until the iteration completes, which can take time.
 * This class provides a service of opening the stream "on demand" during the iteration and closing it at the end of
 * the iteration.
 *
 * @author Hussein Alshqerat
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@NotThreadSafe
public class ConcatenatedStream {

    /**
     * Returns a concatenated stream from a collection of stream suppliers.
     *
     * @param streamSuppliers a collection of suppliers of the streams to be concatenated
     * @param <T>             the type of elements in the streams
     * @return a concatenated stream
     */
    public static <T> Stream<T> of(Collection<Supplier<Stream<T>>> streamSuppliers) {
        ConcatenatedStreamIterator<T> iterator = new ConcatenatedStreamIterator<>(streamSuppliers.iterator());
        return StreamSupport.stream(((Iterable<T>) () -> iterator).spliterator(), false)
                .onClose(iterator::closeCurrentStream);
    }

    /**
     * Utility class that provides an iterator over a sequence of streams, treating them as a single concatenated
     * stream.
     * It ensures that each stream is closed after it has been consumed.
     *
     * @param <T> the type of elements in the streams
     */
    protected static class ConcatenatedStreamIterator<T> implements Iterator<T> {

        private final Iterator<Supplier<Stream<T>>> streamSuppliers;
        private Stream<T> currentStream;
        private Iterator<T> currentIterator;

        /**
         * Constructs a new ConcatenatedStreamIterator.
         *
         * @param streamSuppliers an iterator over suppliers of the streams to be concatenated
         */
        public ConcatenatedStreamIterator(Iterator<Supplier<Stream<T>>> streamSuppliers) {
            this.streamSuppliers = streamSuppliers;
        }

        /**
         * Returns true if the iteration has more elements.
         * It also ensures that the current stream is closed before moving to the next one.
         *
         * @return true if the iteration has more elements
         */
        @Override
        public boolean hasNext() {
            while ((currentIterator == null || !currentIterator.hasNext()) && streamSuppliers.hasNext()) {
                closeCurrentStream();
                currentStream = streamSuppliers.next().get();
                currentIterator = currentStream.iterator();
            }
            if (currentIterator == null || !currentIterator.hasNext()) {
                closeCurrentStream();
            }
            return currentIterator != null && currentIterator.hasNext();
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentIterator.next();
        }

        /**
         * Closes the current stream and sets the current stream and iterator to null.
         */
        void closeCurrentStream() {
            if (currentStream != null) {
                currentStream.close();
                currentStream = null;
                currentIterator = null;
            }
        }
    }
}