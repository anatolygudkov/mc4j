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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class MCountersLayout {
    public static final int COUNTERS_VERSION = 1;
    public static final Charset STRING_CHARSET = StandardCharsets.UTF_8;

    protected static final int HEADER_COUNTERS_VERSION_OFFSET = 0;
    protected static final int HEADER_PID_OFFSET = HEADER_COUNTERS_VERSION_OFFSET + MCountersUtils.SIZE_OF_INT;
    protected static final int HEADER_STATICS_LENGTH_OFFSET = HEADER_PID_OFFSET + MCountersUtils.SIZE_OF_LONG;
    protected static final int HEADER_METADATA_LENGTH_OFFSET =
            HEADER_STATICS_LENGTH_OFFSET + MCountersUtils.SIZE_OF_INT;
    protected static final int HEADER_VALUES_LENGTH_OFFSET = HEADER_METADATA_LENGTH_OFFSET + MCountersUtils.SIZE_OF_INT;

    static final int HEADER_LENGTH =
            MCountersUtils.align(HEADER_VALUES_LENGTH_OFFSET + MCountersUtils.SIZE_OF_INT,
                    MCountersUtils.SIZE_OF_CACHE_LINE * 2);

    protected static final int STATICS_NUMBER_OF_STATICS_OFFSET = 0;
    protected static final int STATICS_RECORDS_OFFSET = STATICS_NUMBER_OF_STATICS_OFFSET + MCountersUtils.SIZE_OF_INT;

    protected static final int STATICS_LABEL_LENGTH_OFFSET = 0;
    protected static final int STATICS_VALUE_LENGTH_OFFSET = STATICS_LABEL_LENGTH_OFFSET + MCountersUtils.SIZE_OF_INT;
    protected static final int STATICS_LABEL_OFFSET = STATICS_VALUE_LENGTH_OFFSET + MCountersUtils.SIZE_OF_INT;

    protected static int staticsRecordLength(final int labelLength, final int valueLength) {
        return MCountersUtils.align(STATICS_LABEL_OFFSET + labelLength + valueLength,
                MCountersUtils.SIZE_OF_INT); // should be aligned to have next LABEL_LENGTH and VALUE_LENGTH
        // integers aligned
    }

    protected static final int METADATA_LABEL_MAX_LENGTH = MCountersUtils.SIZE_OF_CACHE_LINE * 6 -
            MCountersUtils.SIZE_OF_INT; // max length of the label's text without its length prefix
    protected static final int METADATA_COUNTER_ID_STATUS_OFFSET = 0;
    protected static final int METADATA_LABEL_LENGTH_OFFSET = MCountersUtils.SIZE_OF_CACHE_LINE * 2;
    protected static final int METADATA_LABEL_OFFSET = METADATA_LABEL_LENGTH_OFFSET + MCountersUtils.SIZE_OF_INT;
    protected static final int METADATA_RECORD_LENGTH = METADATA_LABEL_OFFSET + METADATA_LABEL_MAX_LENGTH;

    protected static final int VALUES_COUNTER_LENGTH = MCountersUtils.SIZE_OF_CACHE_LINE * 2;

    protected static final int COUNTER_STATUS_NOT_USED = 0;
    protected static final int COUNTER_STATUS_ALLOCATION_IN_PROGRESS = 1;
    protected static final int COUNTER_STATUS_ALLOCATED = 2;
    protected static final int COUNTER_STATUS_FREED = 3;

    protected static long makeIdStatus(final long id, final int status) {
        return id << 8 | status;
    }

    protected static int extractStatus(final long idStatus) {
        return (int) (idStatus & 0xff);
    }

    protected static long extractId(final long idStatus) {
        return idStatus >>> 8;
    }

    protected final DirectMemoryBuffer header;
    protected final DirectMemoryBuffer statics;
    protected final DirectMemoryBuffer metadata;
    protected final DirectMemoryBuffer values;

    protected MCountersLayout(final DirectMemoryBuffer header,
                              final DirectMemoryBuffer statics,
                              final DirectMemoryBuffer metadata,
                              final DirectMemoryBuffer values) {
        this.header = header;
        this.statics = statics;
        this.metadata = metadata;
        this.values = values;
    }

    public DirectMemoryBuffer header() {
        return header;
    }

    public DirectMemoryBuffer statics() {
        return statics;
    }

    public DirectMemoryBuffer metadata() {
        return metadata;
    }

    public DirectMemoryBuffer values() {
        return values;
    }
}
