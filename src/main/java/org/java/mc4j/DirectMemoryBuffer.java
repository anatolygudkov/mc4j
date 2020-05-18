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

import sun.misc.Unsafe;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * This is a wrapper around {@code java.nio.DirectByteBuffer} to provide direct memory access.
 */
public class DirectMemoryBuffer {
    private static final Unsafe UNSAFE = UnsafeAccess.getUnsafe();
    private static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    static {
        try {
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET =
                    UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final long addressOffset;
    private final int capacity;

    /**
     *
     * @param buffer buffer to be wrapped
     * @param offset
     * @param length
     */
    public DirectMemoryBuffer(final ByteBuffer buffer, final int offset, final int length) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Only direct byte buffers supported");
        }

        final long address = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
        addressOffset = address + offset;
        capacity = length;
    }

    /**
     * Returns capacity of the buffer.
     * @return capacity of the buffer
     */
    public int capacity() {
        return capacity;
    }

    public int getInt(final int index) {
        return UNSAFE.getInt(null, addressOffset + index);
    }

    public int getIntVolatile(final int index) {
        return UNSAFE.getIntVolatile(null, addressOffset + index);
    }

    public void putInt(final int index, final int value) {
        UNSAFE.putInt(null, addressOffset + index, value);
    }

    public void putIntOrdered(final int index, final int value) {
        UNSAFE.putOrderedInt(null, addressOffset + index, value);
    }

    public void putIntVolatile(final int index, final int value) {
        UNSAFE.putIntVolatile(null, addressOffset + index, value);
    }

    public long getLong(final int index) {
        return UNSAFE.getLong(null, addressOffset + index);
    }

    public long getLongVolatile(final int index) {
        return UNSAFE.getLongVolatile(null, addressOffset + index);
    }

    public void putLong(final int index, final long value) {
        UNSAFE.putLong(null, addressOffset + index, value);
    }

    public void putLongOrdered(final int index, final long value) {
        UNSAFE.putOrderedLong(null, addressOffset + index, value);
    }

    public void putLongVolatile(final int index, final long value) {
        UNSAFE.putLongVolatile(null, addressOffset + index, value);
    }

    public long getAndAddLong(final int index, final long increment) {
        return UNSAFE.getAndAddLong(null, addressOffset + index, increment);
    }

    public long getAndSetLong(final int index, final long value) {
        return UNSAFE.getAndSetLong(null, addressOffset + index, value);
    }

    public boolean compareAndSwapLong(final int index, final long expectedValue, final long newValue) {
        return UNSAFE.compareAndSwapLong(null, addressOffset + index, expectedValue, newValue);
    }

    public void getBytes(final int index, final byte[] dst) {
        UNSAFE.copyMemory(null, addressOffset + index, dst, ARRAY_BASE_OFFSET, dst.length);
    }

    public void getBytes(final int index, final byte[] dst, final int offset, final int length) {
        UNSAFE.copyMemory(null,
                addressOffset + index,
                dst,
                ARRAY_BASE_OFFSET + offset,
                length);
    }

    public void putBytes(final int index, final byte[] src) {
        UNSAFE.copyMemory(src,
                ARRAY_BASE_OFFSET,
                null,
                addressOffset + index,
                src.length);
    }

    public void putBytes(final int index, final byte[] src, final int offset, final int length) {
        UNSAFE.copyMemory(src, ARRAY_BASE_OFFSET + offset, null, addressOffset + index, length);
    }
}
