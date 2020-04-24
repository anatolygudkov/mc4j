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

public final class MCountersReader implements AutoCloseable {
    private final ByteBuffer buffer;
    private final org.java.mc4j.MCountersDecoder decoder;

    public MCountersReader(final String countersFileName) throws IOException {
        this(new File(MCountersUtils.getMCountersDirectoryName(), countersFileName));
    }

    public MCountersReader(final File countersFile) throws IOException {
        this(MCountersUtils.mapExistingFileReadOnly(countersFile));
    }

    public MCountersReader(final ByteBuffer countersBuffer) throws IOException {
        buffer = countersBuffer;

        decoder = MCountersDecoder.prepare(countersBuffer);

        final int version = decoder.getVersion();
        if (version == 0) {
            throw new IOException("Counters haven't been initialized yet");
        }
        if (version != MCountersLayout.COUNTERS_VERSION) {
            throw new IOException("Unexpected version of the counters file: " + version);
        }
    }

    public int getVersion() {
        return decoder.getVersion();
    }

    public long getPid() {
        return decoder.getPid();
    }

    public void forEachStatic(final StaticConsumer consumer) {
        decoder.forEachStatic(consumer);
    }

    public String getStaticValue(final String staticLabel) {
        return decoder.getStaticValue(staticLabel);
    }

    public void forEachCounter(final MCounterConsumer consumer) {
        decoder.forEachCounter(consumer);
    }

    public long getCounterValue(final long counterId) throws MCounterNotFoundException {
        return decoder.getCounterValue(counterId);
    }

    public String getCounterLabel(final long counterId) throws MCounterNotFoundException {
        return decoder.getCounterLabel(counterId);
    }

    @Override
    public void close() throws IOException {
        MCountersUtils.unmap(buffer);
    }
}