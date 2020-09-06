/**
 * MIT License
 *
 * Copyright (c) 2020 anatolygudkov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.java.mc4j;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is thread safe. MCounters, created by an instance of the class, are also thread safe.
 * But see <b>IMPORTANT</b>s below.
 *
 * <p><b>IMPORTANT:</b>
 * <ul>
 *     <li>Don't do read/write from/to an MCounter after it was closed. This may lead to corruption of values
 *     of another counters</li>
 *     <li>Don't do read/write/close with an MCounter after the CounterWriter is closed,
 *     since this will lead to the crash of the process</li>
 * </ul>
 */
public final class MCountersWriter implements AutoCloseable {
    public static final int MAX_POSSIBLE_NUMBER_OF_COUNTERS = 10_000;

    private final AtomicLong idSequence = new AtomicLong(0);

    private final File countersFile;
    private final ByteBuffer buffer;
    private final MCountersEncoder encoder;
    private final DirectMemoryBuffer values;

    private volatile boolean closed;

    public MCountersWriter(final String countersFileName, final Properties statics, final int maxNumbersOfCounters)
            throws IOException {
        this(new File(MCountersUtils.getMCountersDirectoryName(), countersFileName), statics, maxNumbersOfCounters);
    }

    public MCountersWriter(final File countersFile, final Properties statics, final int maxNumbersOfCounters)
            throws IOException {
        if (maxNumbersOfCounters < 0 || maxNumbersOfCounters > MAX_POSSIBLE_NUMBER_OF_COUNTERS) {
            throw new IllegalArgumentException("Incorrect max numbers of counters: " + maxNumbersOfCounters);
        }

        this.countersFile = countersFile;

        this.countersFile.getParentFile().mkdirs();

        final int staticsLength = MCountersEncoder.staticsLength(statics);
        final int metadataLength = MCountersEncoder.metadataLength(maxNumbersOfCounters);
        final int valuesLength = MCountersEncoder.valuesLength(maxNumbersOfCounters);

        final int countersFileLength = MCountersUtils.align(
                MCountersLayout.HEADER_LENGTH +
                        staticsLength +
                        metadataLength +
                        valuesLength,
                MCountersUtils.FILE_PAGE_SIZE);

        buffer = MCountersUtils.mapNewFile(this.countersFile, countersFileLength);

        encoder = new MCountersEncoder(buffer,
                staticsLength,
                metadataLength,
                valuesLength
        );

        values = encoder.values;

        encoder.setPid(MCountersUtils.PID);
        encoder.setStartTime(System.currentTimeMillis());
        encoder.setStatics(statics);

        encoder.setVersion(MCountersLayout.COUNTERS_VERSION); // HB write
    }

    public File countersFile() {
        return countersFile;
    }

    public ByteBuffer countersBuffer() {
        return buffer;
    }

    public MCounter addCounter(final String label) {
        return addCounter(label, 0);
    }

    public MCounter addCounter(final String label, final long initialValue) {
        return new WritableCounter(label, initialValue);
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes the writer and unmap the counters file. MCounters, created by this writer, MUST NOT be
     * used after the close, since they will address unmapped memory and this will lead to the crash of the process.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        MCountersUtils.unmap(buffer);
    }

    private class WritableCounter implements MCounter {
        private final long id;
        private final String label;
        private final int valueOffset;

        private volatile boolean closed;

        WritableCounter(final String label, final long initialValue) {
            this.id = idSequence.incrementAndGet();
            this.label = label;

            valueOffset = encoder.addCounter(id, label, initialValue);
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public long get() {
            return values.getLongVolatile(valueOffset);
        }

        @Override
        public long getWeak() {
            return values.getLong(valueOffset);
        }

        @Override
        public void set(final long value) {
            values.putLongVolatile(valueOffset, value);
        }

        @Override
        public void setWeak(final long value) {
            values.putLong(valueOffset, value);
        }

        @Override
        public long increment() {
            return values.getAndAddLong(valueOffset, 1) + 1;
        }

        @Override
        public long getAndAdd(final long increment) {
            return values.getAndAddLong(valueOffset, increment);
        }

        @Override
        public long getAndSet(final long value) {
            return values.getAndSetLong(valueOffset, value);
        }

        @Override
        public boolean compareAndSet(final long expectedValue, final long updateValue) {
            return values.compareAndSwapLong(valueOffset, expectedValue, updateValue);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
            }
        }
    }
}