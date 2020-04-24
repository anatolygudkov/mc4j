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
package org.java.mc4j.jmx;

import org.java.mc4j.MCountersWriter;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This class exposes JMX attributes as counters. Attributes of the following types are supported:
 * <ul>
 *     <li>{@code long}</li>
 *     <li>{@code int}</li>
 *     <li>{@code short}</li>
 *     <li>{@code byte}</li>
 *     <li>{@code double}</li>
 *     <li>{@code float}</li>
 *     <li>{@code boolean}</li>
 * </ul>
 * <p>
 * Floating-point variables {@code double} and {@code float} are converted to {@code long} with possible loss
 * of precision. For example: {@code 10.02} => {@code 10}; {@code 0.32405} => {@code 0}.
 * <p>
 * A {@code boolean} value of an attribute is converted to {@code 0} for {@code false} and {@code 1} for {@code true}.
 * <p>
 * A counter's label format looks like 'jmx://OBJECT_NAME?ATTRIBUTE_NAME'. For example:
 * {@code jmx://java.lang:type=Runtime?StartTime }; {@code jmx://java.lang:type=Runtime?BootClassPathSupported}.
 * <p>
 * The class should be configured with a list of MBeans' ObjectNames and names of attributes in the format:
 * "OBJECT_NAME_1[ATTRIBUTE_NAME_1, ATTRIBUTE_NAME_2,...ATTRIBUTE_NAME_X]
 * OBJECT_NAME_2[ATTRIBUTE_NAME_1, ATTRIBUTE_NAME_2,...ATTRIBUTE_NAME_Y]
 * OBJECT_NAME_N[ATTRIBUTE_NAME_1, ATTRIBUTE_NAME_2,...ATTRIBUTE_NAME_Z]"
 * <p>
 * Wildcard '<b>*</b>' can be used to specify all available attributes of supported types.
 * <p>
 * An example of usage:
 * <pre>
 * try (JmxPublisher jmxPublisher = ...) {
 *
 *     jmxPublisher.addMbeans("java.lang:type=Runtime[StartTime, Uptime] java.lang:type=OperatingSystem[*]");
 *
 *     jmxPublisher.refresh();
 *     ...
 * }
 * </pre>
 *
 * <b>IMPORTANT:</b> this class isn't thread safe.
 */
public class JmxPublisher implements AutoCloseable {
    private final Map<String, MBean> mBeans = new HashMap<>();

    private final MCountersWriter mCountersWriter;
    private final MBeanServer server;

    /**
     * Creates an instance of JmxPublisher with no MBeans specified.
     *
     * @param countersWriter The counter writer
     */
    public JmxPublisher(final MCountersWriter countersWriter) {
        this.mCountersWriter = countersWriter;

        server = ManagementFactory.getPlatformMBeanServer();
    }

    /**
     * Creates an instance of the publisher with
     * @param countersWriter           The counter writer
     * @param objectNamesWithAttributes List
     * @throws MalformedObjectNameException
     */
    public JmxPublisher(final MCountersWriter countersWriter, final String objectNamesWithAttributes)
            throws MalformedObjectNameException {
        this(countersWriter);

        addMBeans(objectNamesWithAttributes);
    }

    /**
     * @param objectNamesWithAttributes
     * @throws MalformedObjectNameException
     */
    public void addMBeans(final String objectNamesWithAttributes) throws MalformedObjectNameException {
        final Map<String, MBean> newMBeans = MBean.parseMBeans(objectNamesWithAttributes);
        newMBeans.forEach(mBeans::putIfAbsent);
    }

    public Collection<MBean> getMBeans() {
        return mBeans.values();
    }

    /**
     * Refreshes counters for currently available MBeans. If an MBean has disappeared, all the counters associated
     * with its attributes are closed/removed.
     */
    public void refresh() {
        for (final MBean mBean : mBeans.values()) {
            mBean.refresh(mCountersWriter, server);
        }
    }

    /**
     * Removes all counters created by  this instance of the JmxPublisher
     */
    @Override
    public void close() {
        if (mBeans.isEmpty()) {
            return;
        }

        for (final MBean mBean : mBeans.values()) {
            try {
                mBean.close();
            } catch (final Throwable ignore) {
            }
        }

        mBeans.clear();
    }
}