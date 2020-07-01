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

/**
 * An interface of a modifiable counter.
 */
public interface MCounter extends AutoCloseable {

    /**
     * Returns ID of the counter. The ID is unique for each added counter.
     *
     * @return ID of the counter
     */
    long id();

    /**
     * Returns label of the counter.
     *
     * @return label of the counter
     */
    String label();

    /**
     * Returns current value of the counter with volatile semantics.
     *
     * @return current value
     */
    long get();

    /**
     * Returns current value of the counter with weak ordering semantics. This is the same as
     * standard (non volatile) read of a field.
     *
     * @return current value of the counter
     */
    long getWeak();

    /**
     * Sets new value of the counter with volatile semantics.
     *
     * @param value new value to be set
     */
    void set(long value);

    /**
     * Sets new value of the counter with weak ordering semantics. This is the same as
     * standard (non volatile) write of a field.
     *
     * @param value new value to be set
     */
    void setWeak(long value);

    /**
     * Increments the counter atomically and returns new value.
     *
     * @return new value of the counter
     */
    long increment();

    /**
     * Adds an increment to the counter with volatile semantics.
     *
     * @param increment
     * @return previous value of the counter
     */
    long getAndAdd(long increment);

    /**
     * Returns the current value of the counter and atomically sets it to a new value.
     *
     * @param value
     * @return the previous value of the counter
     */
    long getAndSet(long value);

    /**
     * Compares the current value to the expected one and if {@code true}  then sets to the update value atomically.
     *
     * @param expectedValue
     * @param updateValue
     * @return {@code true}  if
     */
    boolean compareAndSet(long expectedValue, long updateValue);

    /**
     * Returns {@code true} if the connector has been closed.
     *
     * @return {@code true} if the counter has been closed
     */
    boolean isClosed();

    /**
     * Close with no checked exception.
     */
    void close();

}
