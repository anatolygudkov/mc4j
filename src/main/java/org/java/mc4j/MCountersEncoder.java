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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class MCountersEncoder extends MCountersLayout {

    public static int staticsLength(final Properties statics) {
        int result = STATICS_RECORDS_OFFSET; // some space for number of statics

        if (statics == null || statics.isEmpty()) {
            return result;
        }

        final List<String> labels = new ArrayList<>(statics.stringPropertyNames());
        Collections.sort(labels);

        for (final String label : labels) {
            final String value = statics.getProperty(label);

            final byte[] labelBytes = label.getBytes(STRING_CHARSET);
            final byte[] valueBytes = value.getBytes(STRING_CHARSET);

            result += staticsRecordLength(labelBytes.length, valueBytes.length);
        }

        result = MCountersUtils.align(result, MCountersUtils.SIZE_OF_CACHE_LINE * 2);

        return result;
    }

    public static int metadataLength(final int numberOfCounters) {
        return numberOfCounters * METADATA_RECORD_LENGTH;
    }

    public static int valuesLength(final int numberOfCounters) {
        return numberOfCounters * VALUES_COUNTER_LENGTH;
    }

    public MCountersEncoder(final ByteBuffer countersByteBuffer,
                            final int staticsLength,
                            final int metadataLength,
                            final int valuesLength) {
        this(new DirectMemoryBuffer(countersByteBuffer, 0, HEADER_LENGTH),
                new DirectMemoryBuffer(countersByteBuffer, HEADER_LENGTH, staticsLength),
                new DirectMemoryBuffer(countersByteBuffer, HEADER_LENGTH + staticsLength, metadataLength),
                new DirectMemoryBuffer(countersByteBuffer,
                        HEADER_LENGTH + staticsLength + metadataLength,
                        valuesLength));
    }

    public MCountersEncoder(final DirectMemoryBuffer header,
                            final DirectMemoryBuffer statics,
                            final DirectMemoryBuffer metadata,
                            final DirectMemoryBuffer values) {
        super(header, statics, metadata, values);

        header.putInt(HEADER_STATICS_LENGTH_OFFSET, statics.capacity());
        header.putInt(HEADER_METADATA_LENGTH_OFFSET, metadata.capacity());
        header.putInt(HEADER_VALUES_LENGTH_OFFSET, values.capacity());
        // will be finished by HB write/volatile write of VERSION at the end
        // of header preparation
    }

    public void setVersion(final int version) {
        header.putIntVolatile(HEADER_COUNTERS_VERSION_OFFSET, version);
    }

    public void setPid(final long pid) {
        header.putLongVolatile(HEADER_PID_OFFSET, pid);
    }

    public void setStartTime(final long startTime) {
        header.putLongVolatile(HEADER_START_TIME_OFFSET, startTime);
    }

    public void setStatics(final Properties statics) {
        int offset = 0;

        if (statics == null || statics.isEmpty()) {
            this.statics.putIntOrdered(offset, 0);
            return;
        }

        if (offset + STATICS_RECORDS_OFFSET > this.statics.capacity()) {
            throw new IllegalArgumentException("Statics buffer is too small " + this.statics.capacity());
        }

        final List<String> labels = new ArrayList<>(statics.stringPropertyNames());
        Collections.sort(labels);

        this.statics.putIntOrdered(offset, labels.size());

        offset = STATICS_RECORDS_OFFSET;

        for (final String label : labels) {
            final String value = statics.getProperty(label);

            final byte[] labelBytes = label.getBytes(STRING_CHARSET);
            final byte[] valueBytes = value.getBytes(STRING_CHARSET);

            final int recordLength = staticsRecordLength(labelBytes.length, valueBytes.length);

            if (offset + recordLength > this.statics.capacity()) {
                throw new IllegalArgumentException("Properties don't feet to the statics's buffer size " +
                        this.statics.capacity());
            }

            this.statics.putBytes(offset + STATICS_LABEL_OFFSET, labelBytes);
            this.statics.putBytes(offset + STATICS_LABEL_OFFSET + labelBytes.length, valueBytes);

            this.statics.putInt(offset + STATICS_LABEL_LENGTH_OFFSET, labelBytes.length);
            this.statics.putIntOrdered(offset + STATICS_VALUE_LENGTH_OFFSET, valueBytes.length); // HB write

            offset += recordLength;
        }
    }

    public int addCounter(final long id, final String label, final long initialValue) {
        int metadataOffset = 0;
        int valueOffset = 0;

        while (metadataOffset < metadata.capacity()) {
            final int idStatusOffset = metadataOffset + METADATA_COUNTER_ID_STATUS_OFFSET;

            final long idStatus = metadata.getLongVolatile(idStatusOffset); // HB read

            final int status = extractStatus(idStatus);

            switch (status) {
                case COUNTER_STATUS_NOT_USED:
                case COUNTER_STATUS_FREED:
                    final long inProgressIdStatus = makeIdStatus(id, COUNTER_STATUS_ALLOCATION_IN_PROGRESS);

                    if (metadata.compareAndSwapLong(idStatusOffset, idStatus, inProgressIdStatus)) {

                        final byte[] labelBytes = label.getBytes(STRING_CHARSET);
                        final int labelLength = Math.min(labelBytes.length, METADATA_LABEL_MAX_LENGTH);

                        metadata.putInt(metadataOffset + METADATA_LABEL_LENGTH_OFFSET, labelLength);
                        metadata.putBytes(metadataOffset + METADATA_LABEL_OFFSET,
                                labelBytes, 0, labelLength);

                        values.putLong(valueOffset, initialValue);

                        final long allocatedIdStatus = makeIdStatus(id, COUNTER_STATUS_ALLOCATED);

                        metadata.putLongOrdered(idStatusOffset, allocatedIdStatus); // HB write

                        return valueOffset;
                    }

                    continue;
                default:
                    break;
            }

            metadataOffset += METADATA_RECORD_LENGTH;
            valueOffset += VALUES_COUNTER_LENGTH;
        }

        throw new IllegalArgumentException("There is no free space to add new counter");
    }

    public boolean freeCounter(final long id) {
        int metadataOffset = 0;

        while (metadataOffset < metadata.capacity()) {
            final int idStatusOffset = metadataOffset + METADATA_COUNTER_ID_STATUS_OFFSET;

            final long idStatus = metadata.getLongVolatile(idStatusOffset); // HB read

            final long currentId = extractId(idStatus);

            if (currentId == id) {
                final int status = extractStatus(idStatus);

                switch (status) {
                    case COUNTER_STATUS_ALLOCATED:
                        final long newIdStatus = makeIdStatus(id, COUNTER_STATUS_FREED);
                        metadata.compareAndSwapLong(idStatusOffset, idStatus, newIdStatus); // HB write; we don't care
                        // about result of CAS, since the counter may be freed by another thread already
                        // (a race condition) and this is good for us anyway
                        return true;
                    default:
                        return false;
                }
            }

            metadataOffset += METADATA_RECORD_LENGTH;
        }
        return false;
    }
}