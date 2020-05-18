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
import java.util.Arrays;

public final class MCountersDecoder extends MCountersLayout {

    public static MCountersDecoder prepare(final ByteBuffer countersByteBuffer) {
        final DirectMemoryBuffer header =
                new DirectMemoryBuffer(countersByteBuffer, 0, HEADER_LENGTH);

        final int staticsLength = header.getIntVolatile(HEADER_STATICS_LENGTH_OFFSET); // HB read
        final int metadataLength = header.getInt(HEADER_METADATA_LENGTH_OFFSET);
        final int valuesLength = header.getInt(HEADER_VALUES_LENGTH_OFFSET);

        return new MCountersDecoder(header,
                new DirectMemoryBuffer(countersByteBuffer, HEADER_LENGTH, staticsLength),
                new DirectMemoryBuffer(countersByteBuffer, HEADER_LENGTH + staticsLength, metadataLength),
                new DirectMemoryBuffer(countersByteBuffer,
                        HEADER_LENGTH + staticsLength + metadataLength,
                        valuesLength));
    }

    public MCountersDecoder(final DirectMemoryBuffer header,
                            final DirectMemoryBuffer statics,
                            final DirectMemoryBuffer countersMetadata,
                            final DirectMemoryBuffer countersValues) {
        super(header, statics, countersMetadata, countersValues);
    }

    public int getVersion() {
        return header.getIntVolatile(HEADER_COUNTERS_VERSION_OFFSET);
    }

    public long getPid() {
        return header.getLongVolatile(HEADER_PID_OFFSET);
    }

    public long getStartTime() {
        return header.getLongVolatile(HEADER_START_TIME_OFFSET);
    }

    public void forEachStatic(final StaticConsumer consumer) {
        int offset = STATICS_NUMBER_OF_STATICS_OFFSET;

        final int numOfStatics = statics.getIntVolatile(offset); // HB read

        offset = STATICS_RECORDS_OFFSET;

        for (int i = 0; i < numOfStatics; i++) {
            final int labelLength = statics.getInt(offset + STATICS_LABEL_LENGTH_OFFSET);
            final int valueLength = statics.getInt(offset + STATICS_VALUE_LENGTH_OFFSET);

            final int recordLength = staticsRecordLength(labelLength, valueLength);

            final byte[] labelBytes = new byte[labelLength];
            statics.getBytes(offset + STATICS_LABEL_OFFSET, labelBytes);

            final byte[] valueBytes = new byte[valueLength];
            statics.getBytes(offset + STATICS_LABEL_OFFSET + labelBytes.length, valueBytes);

            consumer.accept(new String(labelBytes, STRING_CHARSET), new String(valueBytes, STRING_CHARSET));

            offset += recordLength;
        }
    }

    public String getStaticValue(final String staticLabel) {
        int offset = STATICS_NUMBER_OF_STATICS_OFFSET;

        final int numOfStatics = statics.getIntVolatile(offset); // HB read

        offset = STATICS_RECORDS_OFFSET;

        final byte[] staticLabelBytes = staticLabel.getBytes(STRING_CHARSET);

        for (int i = 0; i < numOfStatics; i++) {
            final int labelLength = statics.getInt(offset + STATICS_LABEL_LENGTH_OFFSET);
            final int valueLength = statics.getInt(offset + STATICS_VALUE_LENGTH_OFFSET);

            final byte[] labelBytes = new byte[labelLength];
            statics.getBytes(offset + STATICS_LABEL_OFFSET, labelBytes);

            if (Arrays.equals(staticLabelBytes, labelBytes)) {
                final byte[] valueBytes = new byte[valueLength];
                statics.getBytes(offset + STATICS_LABEL_OFFSET + labelBytes.length, valueBytes);
                return new String(valueBytes, STRING_CHARSET);
            }

            final int recordLength = staticsRecordLength(labelLength, valueLength);

            offset += recordLength;
        }

        return null;
    }

    public void forEachCounter(final MCounterConsumer consumer) {
        int metadataOffset = 0;
        int valueOffset = 0;

        _stop:
        while (metadataOffset < metadata.capacity()) {
            final int idStatusOffset = metadataOffset + METADATA_COUNTER_ID_STATUS_OFFSET;

            final long idStatus = metadata.getLongVolatile(idStatusOffset); // HB read

            final int status = extractStatus(idStatus);

            switch (status) {
                case COUNTER_STATUS_NOT_USED:
                    break _stop;

                case COUNTER_STATUS_ALLOCATED:
                    final long id = extractId(idStatus);

                    final int labelLength = metadata.getInt(metadataOffset + METADATA_LABEL_LENGTH_OFFSET);

                    final byte[] labelBytes = new byte[labelLength];
                    metadata.getBytes(metadataOffset + METADATA_LABEL_OFFSET, labelBytes);

                    final long value = values.getLong(valueOffset);

                    if (metadata.getLongVolatile(idStatusOffset) == idStatus) { // the counter's status
                        // wasn't changed yet
                        consumer.accept(id, new String(labelBytes, STRING_CHARSET), value);
                    }
                    break;

                default:
                    break;
            }

            metadataOffset += METADATA_RECORD_LENGTH;
            valueOffset += VALUES_COUNTER_LENGTH;
        }
    }

    public long getCounterValue(final long counterId) throws MCounterNotFoundException {
        int metadataOffset = 0;
        int valueOffset = 0;

        while (metadataOffset < metadata.capacity()) {
            final int idStatusOffset = metadataOffset + METADATA_COUNTER_ID_STATUS_OFFSET;

            final long idStatus = metadata.getLongVolatile(idStatusOffset); // HB read

            final int status = extractStatus(idStatus);

            if (status == COUNTER_STATUS_NOT_USED) {
                break;
            }

            final long id = extractId(idStatus);

            if (counterId == id) {
                switch (status) {
                    case COUNTER_STATUS_ALLOCATED:
                        final long value = values.getLong(valueOffset);

                        if (metadata.getLongVolatile(idStatusOffset) == idStatus) { // the counter's status
                            // wasn't changed yet
                            return value;
                        }
                        continue;

                    default:
                        throw new MCounterNotFoundException(counterId);
                }
            }

            metadataOffset += METADATA_RECORD_LENGTH;
            valueOffset += VALUES_COUNTER_LENGTH;
        }

        throw new MCounterNotFoundException(counterId);
    }

    public String getCounterLabel(final long counterId) throws MCounterNotFoundException {
        int metadataOffset = 0;

        while (metadataOffset < metadata.capacity()) {
            final int idStatusOffset = metadataOffset + METADATA_COUNTER_ID_STATUS_OFFSET;

            final long idStatus = metadata.getLongVolatile(idStatusOffset); // HB read

            final int status = extractStatus(idStatus);

            if (status == COUNTER_STATUS_NOT_USED) {
                break;
            }

            final long id = extractId(idStatus);

            if (counterId == id) {
                switch (status) {
                    case COUNTER_STATUS_ALLOCATED:

                        final int labelLength = metadata.getInt(metadataOffset + METADATA_LABEL_LENGTH_OFFSET);

                        final byte[] labelBytes = new byte[labelLength];
                        metadata.getBytes(metadataOffset + METADATA_LABEL_OFFSET, labelBytes);

                        if (metadata.getLongVolatile(idStatusOffset) == idStatus) { // the counter's status
                            // wasn't changed yet
                            return new String(labelBytes, STRING_CHARSET);
                        }
                        continue;

                    default:
                        throw new MCounterNotFoundException(counterId);
                }
            }

            metadataOffset += METADATA_RECORD_LENGTH;
        }

        throw new MCounterNotFoundException(counterId);
    }
}