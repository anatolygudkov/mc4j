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

import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MCountersEncoderDecoderTest {
    static final String LABEL = "label";
    static final String VALUE = "value";
    static final String PROPERTY = "property";

    @Test
    void emptyEncoder() {
        final int staticsLength = MCountersEncoder.staticsLength(new Properties());
        final int metadataLength = MCountersEncoder.metadataLength(0);
        final int valuesLength = MCountersEncoder.valuesLength(0);

        assertEquals(
                MCountersUtils.SIZE_OF_INT,
                staticsLength);
        assertEquals(0, metadataLength);
        assertEquals(0, valuesLength);

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MCountersLayout.HEADER_LENGTH +
                staticsLength +
                metadataLength +
                valuesLength);

        final MCountersEncoder encoder = new MCountersEncoder(
                byteBuffer,
                staticsLength,
                metadataLength,
                valuesLength
        );

        final Properties statics = new Properties();

        encoder.setStatics(statics);

        statics.put("p1", "v1");

        assertThrows(IllegalArgumentException.class, () -> encoder.setStatics(statics));
        assertThrows(IllegalArgumentException.class, () -> encoder.addCounter(0, "cnt1", 100));

        final long pid = 123;
        encoder.setPid(pid);

        final int version = 1;
        encoder.setVersion(version);

        final MCountersDecoder decoder = MCountersDecoder.prepare(byteBuffer);

        assertEquals(encoder.header().capacity(), decoder.header().capacity());
        assertEquals(encoder.statics().capacity(), decoder.statics().capacity());
        assertEquals(encoder.metadata().capacity(), decoder.metadata().capacity());
        assertEquals(encoder.values().capacity(), decoder.values().capacity());

        assertEquals(version, decoder.getVersion());
        assertEquals(pid, decoder.getPid());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullEncoder() {
        final int numberOfStatics = 1000;
        final int numberOfCounters = 1000;

        final Properties statics = new Properties();

        IntStream.range(0, numberOfStatics).forEach(i -> statics.put(PROPERTY + i, VALUE + i));

        final int staticsLength = MCountersEncoder.staticsLength(statics);
        final int metadataLength = MCountersEncoder.metadataLength(numberOfCounters);
        final int valuesLength = MCountersEncoder.valuesLength(numberOfCounters);

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MCountersLayout.HEADER_LENGTH +
                staticsLength +
                metadataLength +
                valuesLength);

        final MCountersEncoder encoder = new MCountersEncoder(
                byteBuffer,
                staticsLength,
                metadataLength,
                valuesLength
        );

        encoder.setStatics(statics);

        final long pid = 123;
        encoder.setPid(pid);

        final int version = 1;
        encoder.setVersion(version);

        IntStream.range(0, numberOfCounters).forEach(i ->
                encoder.addCounter(i, LABEL + i, i));

        final MCountersDecoder decoder = MCountersDecoder.prepare(byteBuffer);

        assertEquals(encoder.header().capacity(), decoder.header().capacity());
        assertEquals(encoder.statics().capacity(), decoder.statics().capacity());
        assertEquals(encoder.metadata().capacity(), decoder.metadata().capacity());
        assertEquals(encoder.values().capacity(), decoder.values().capacity());

        assertEquals(version, decoder.getVersion());
        assertEquals(pid, decoder.getPid());

        final AtomicInteger numOfStatics = new AtomicInteger();
        decoder.forEachStatic((lbl, val) -> {
            final int i = numOfStatics.get();
            final String staticLabel = PROPERTY + i;
            final String staticValue = VALUE + i;

            final String originalValue = (String) statics.remove(staticLabel); // unchecked
            assertEquals(originalValue, staticValue);

            final String foundValue = decoder.getStaticValue(staticLabel);
            assertEquals(originalValue, foundValue);

            numOfStatics.incrementAndGet();
        });
        assertTrue(statics.isEmpty());

        long nextId = numberOfCounters;

        final long problemId1 = nextId;
        assertThrows(IllegalArgumentException.class, () ->
                encoder.addCounter(problemId1, LABEL + problemId1, problemId1));

        final long aCtrId = numberOfCounters / 2;

        assertTrue(encoder.freeCounter(0));
        assertTrue(encoder.freeCounter(aCtrId));
        assertTrue(encoder.freeCounter(numberOfCounters - 1));

        assertFalse(encoder.freeCounter(0));
        assertFalse(encoder.freeCounter(aCtrId));
        assertFalse(encoder.freeCounter(numberOfCounters - 1));

        nextId++;
        encoder.addCounter(nextId, LABEL + nextId, nextId);

        nextId++;
        encoder.addCounter(nextId, LABEL + nextId, nextId);

        nextId++;
        encoder.addCounter(nextId, LABEL + nextId, nextId);

        nextId++;
        final long problemId2 = nextId;
        assertThrows(IllegalArgumentException.class, () ->
                encoder.addCounter(problemId2, LABEL + problemId2, problemId2));

        decoder.forEachCounter((id, lbl, val) -> {
            final String originalLabel = LABEL + id;
            final long originalValue = id;

            assertEquals(originalLabel, lbl);
            assertEquals(originalValue, val);

            try {
                final String foundLabel = decoder.getCounterLabel(id);
                assertEquals(originalLabel, foundLabel);
            } catch (final MCounterNotFoundException e) {
                fail(e);
            }

            try {
                final long foundValue = decoder.getCounterValue(id);
                assertEquals(originalValue, foundValue);
            } catch (final MCounterNotFoundException e) {
                fail(e);
            }
        });
    }

    @Test
    @Timeout(value = 10)
    void concurrentCountersModification() throws InterruptedException {
        final int numberOfCounters = 2;

        final int staticsLength = MCountersEncoder.staticsLength(null);
        final int metadataLength = MCountersEncoder.metadataLength(numberOfCounters);
        final int valuesLength = MCountersEncoder.valuesLength(numberOfCounters);

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MCountersLayout.HEADER_LENGTH +
                staticsLength +
                metadataLength +
                valuesLength);

        final MCountersEncoder encoder = new MCountersEncoder(
                byteBuffer,
                staticsLength,
                metadataLength,
                valuesLength
        );

        final MCountersDecoder decoder = MCountersDecoder.prepare(byteBuffer);

        final DirectMemoryBuffer encoderValuesBuffer = encoder.values();
        final DirectMemoryBuffer decoderValuesBuffer = decoder.values();

        final int counter0ValueOffset = encoder.addCounter(0, LABEL + "0", 0);
        final int counter1ValueOffset = encoder.addCounter(1, LABEL + "1", 1);

        final int lastValue = 2_000_000;

        final Thread thread0 = new Thread(() -> {
            long currentValue0 = decoderValuesBuffer.getLongVolatile(counter0ValueOffset);
            long currentValue1 = decoderValuesBuffer.getLongVolatile(counter1ValueOffset);

            while (currentValue0 < lastValue) {
                if (currentValue1 > currentValue0) {
                    encoderValuesBuffer.putLongVolatile(counter0ValueOffset, currentValue1);
                }
                currentValue0 = decoderValuesBuffer.getLongVolatile(counter0ValueOffset);
                currentValue1 = decoderValuesBuffer.getLongVolatile(counter1ValueOffset);
            }

            assertEquals(lastValue, currentValue0);
        });

        final Thread thread1 = new Thread(() -> {
            long currentValue0 = decoderValuesBuffer.getLongVolatile(counter0ValueOffset);
            long currentValue1 = decoderValuesBuffer.getLongVolatile(counter1ValueOffset);

            while (currentValue0 < lastValue) {
                if (currentValue1 == currentValue0) {
                    encoderValuesBuffer.putLongVolatile(counter1ValueOffset, currentValue1 + 1);
                }
                currentValue0 = decoderValuesBuffer.getLongVolatile(counter0ValueOffset);
                currentValue1 = decoderValuesBuffer.getLongVolatile(counter1ValueOffset);
            }

            assertEquals(lastValue, currentValue1);
        });

        thread0.start();
        thread1.start();

        thread0.join();
        thread1.join();
    }

    @Test
    @Timeout(value = 10)
    void concurrentCountersAddFree() throws InterruptedException {
        final int numberOfCounters = 5;

        final int staticsLength = MCountersEncoder.staticsLength(null);
        final int metadataLength = MCountersEncoder.metadataLength(numberOfCounters);
        final int valuesLength = MCountersEncoder.valuesLength(numberOfCounters);

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MCountersLayout.HEADER_LENGTH +
                staticsLength +
                metadataLength +
                valuesLength);

        final MCountersEncoder encoder = new MCountersEncoder(
                byteBuffer,
                staticsLength,
                metadataLength,
                valuesLength
        );

        final MCountersDecoder decoder = MCountersDecoder.prepare(byteBuffer);

        final int iterations = 1_000_000;

        final Thread thread0 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                addAndFreeCounter(encoder, decoder, 0, i);
            }
        });

        final Thread thread1 = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                addAndFreeCounter(encoder, decoder, 1, i);
            }
        });

        thread0.start();
        thread1.start();

        thread0.join();
        thread1.join();
    }

    private static void addAndFreeCounter(final MCountersEncoder encoder,
                                          final MCountersDecoder decoder,
                                          final int id,
                                          final int i) {
        encoder.addCounter(id, LABEL, i);

        try {
            final String label = decoder.getCounterLabel(id);
            assertEquals(LABEL, label);
        } catch (final MCounterNotFoundException e) {
            fail(e);
        }
        try {
            final long value = decoder.getCounterValue(id);
            assertEquals(i, value);
        } catch (final MCounterNotFoundException e) {
            fail(e);
        }

        assertTrue(encoder.freeCounter(id));

        assertThrows(MCounterNotFoundException.class, () ->
                decoder.getCounterLabel(id));
        assertThrows(MCounterNotFoundException.class, () ->
                decoder.getCounterValue(id));
    }
}