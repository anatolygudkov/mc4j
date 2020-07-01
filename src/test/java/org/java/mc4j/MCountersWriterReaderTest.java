/**
 * MIT License
 * <p>
 * Copyright (c) 2020 anatolygudkov
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.java.mc4j;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.java.mc4j.MCountersEncoderDecoderTest.PROPERTY;
import static org.java.mc4j.MCountersEncoderDecoderTest.VALUE;
import static org.java.mc4j.MCountersEncoderDecoderTest.LABEL;
import static org.java.mc4j.MCountersUtils.getMCountersDirectoryName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MCountersWriterReaderTest {

    @Test
    @SuppressWarnings("unchecked")
    void fullFile() {
        final int numberOfStatics = 1000;
        final int numberOfCounters = 1000;

        final Properties statics = new Properties();

        IntStream.range(0, numberOfStatics).forEach(i -> statics.put(PROPERTY + i, VALUE + i));

        final File testCountersFile = new File(getMCountersDirectoryName(),
                "junit.jupiter-fullFile-counters.dat");
        if (testCountersFile.exists()) {
            testCountersFile.delete();
        } else {
            testCountersFile.getParentFile().mkdirs();
        }

        final List<MCounter> counters = new ArrayList<>();

        try {
            try (MCountersWriter writer =
                         new MCountersWriter(testCountersFile, statics, numberOfCounters);
                 MCountersReader reader
                         = new MCountersReader(testCountersFile)) {

                IntStream.range(0, numberOfCounters).forEach(i -> {
                    final MCounter counter = writer.addCounter(LABEL + i, 0);
                    counter.set(i);
                    counters.add(counter);
                });

                final AtomicInteger numOfStatics = new AtomicInteger();
                reader.forEachStatic((lbl, val) -> {
                    final int i = numOfStatics.get();
                    final String staticLabel = PROPERTY + i;
                    final String staticValue = VALUE + i;

                    final String originalValue = (String) statics.remove(staticLabel); // unchecked
                    assertEquals(originalValue, staticValue);

                    final String foundValue = reader.getStaticValue(staticLabel);
                    assertEquals(originalValue, foundValue);

                    numOfStatics.incrementAndGet();
                });
                assertTrue(statics.isEmpty());

                reader.forEachCounter((id, lbl, val) -> {
                    final String originalLabel = LABEL + val;

                    assertEquals(originalLabel, lbl);

                    try {
                        final String foundLabel = reader.getCounterLabel(id);
                        assertEquals(originalLabel, foundLabel);
                    } catch (final MCounterNotFoundException e) {
                        fail(e);
                    }

                    try {
                        final long foundValue = reader.getCounterValue(id);
                        assertEquals(val, foundValue);
                    } catch (final MCounterNotFoundException e) {
                        fail(e);
                    }
                });

                for (final MCounter counter : counters) {
                    counter.close();
                    assertTrue(counter.isClosed());
                }

                reader.forEachCounter((id, lbl, val) -> {
                    fail("All counters must be closed");
                });

            } catch (final IOException e) {
                fail(e);
            }
        } finally {
            testCountersFile.delete();
        }
    }
}