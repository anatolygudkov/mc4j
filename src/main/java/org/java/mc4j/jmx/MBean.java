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

import org.java.mc4j.MCounter;
import org.java.mc4j.MCountersWriter;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class prepares and updates counters for all specified attributes of a JMX bean.
 */
public class MBean implements AutoCloseable {
    private static final int INITIAL_STATE = 0;
    private static final int OBJECT_NAME_STARTED_STATE = INITIAL_STATE + 1;
    private static final int OBJECT_NAME_ESCAPING_STARTED_STATE = OBJECT_NAME_STARTED_STATE + 1;
    private static final int ATTR_LIST_STARTED_STATE = OBJECT_NAME_ESCAPING_STARTED_STATE + 1;
    private static final int ATTR_NAME_STARTED_STATE = ATTR_LIST_STARTED_STATE + 1;
    private static final int ATTR_NAME_FINISHED_STATE = ATTR_NAME_STARTED_STATE + 1;

    /**
     * Converts an expression with a list of MBeans and their attributes to a number of instances of MBean. See
     * @param objectNamesWithAttributeNames
     * @return Map of pairs Object Name to MBean
     * @throws MalformedObjectNameException
     */
    public static Map<String, MBean> parseMBeans(final String objectNamesWithAttributeNames)
            throws MalformedObjectNameException {
        final Map<String, MBean> result = new HashMap<>();

        if (objectNamesWithAttributeNames == null) {
            return result;
        }

        final String unexpectedCharAtMessage = "Unexpected char at pos: ";

        int state = INITIAL_STATE;

        final StringBuilder objectName = new StringBuilder();
        final List<String> attributeNames = new ArrayList<>();

        final StringBuilder attributeName = new StringBuilder();

        for (int i = 0; i < objectNamesWithAttributeNames.length(); i++) {
            final char c = objectNamesWithAttributeNames.charAt(i);

            switch (state) {
                case INITIAL_STATE:
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    if (c == '\\') {
                        state = OBJECT_NAME_ESCAPING_STARTED_STATE;
                        objectName.setLength(0);
                        break;
                    }
                    if (c != '[') {
                        state = OBJECT_NAME_STARTED_STATE;
                        objectName.setLength(0);
                        objectName.append(c);
                        break;
                    }
                    throw new IllegalArgumentException(unexpectedCharAtMessage + i + ". No domain specified");

                case OBJECT_NAME_STARTED_STATE:
                    if (c == '[') {
                        state = ATTR_LIST_STARTED_STATE;
                        attributeNames.clear();
                        break;
                    }
                    if (c == '\\') {
                        state = OBJECT_NAME_ESCAPING_STARTED_STATE;
                        break;
                    }
                    objectName.append(c);
                    break;

                case OBJECT_NAME_ESCAPING_STARTED_STATE:
                    if (c == '[' ||
                            c == '\\') {
                        state = OBJECT_NAME_STARTED_STATE;
                        objectName.append(c);
                        break;
                    }
                    throw new IllegalArgumentException(unexpectedCharAtMessage + i);

                case ATTR_LIST_STARTED_STATE:
                case ATTR_NAME_FINISHED_STATE:
                    if (Character.isWhitespace(c) ||
                            c == ',') {
                        break;
                    }
                    if (Character.isLetterOrDigit(c) || c == '*') {
                        state = ATTR_NAME_STARTED_STATE;
                        attributeName.setLength(0);
                        attributeName.append(c);
                        break;
                    }
                    if (c == ']') {
                        state = INITIAL_STATE;
                        attributeNames.add(attributeName.toString());

                        final String objName = objectName.toString();
                        final MBean mBean = new MBean(objName, attributeNames);
                        result.put(objName, mBean);

                        break;
                    }
                    throw new IllegalArgumentException(unexpectedCharAtMessage + i);

                case ATTR_NAME_STARTED_STATE:
                    if (Character.isWhitespace(c) ||
                            c == ',') {
                        state = ATTR_NAME_FINISHED_STATE;
                        attributeNames.add(attributeName.toString());
                        break;
                    }
                    if (c == ']') {
                        state = INITIAL_STATE;
                        attributeNames.add(attributeName.toString());

                        final String objName = objectName.toString();
                        final MBean mBean = new MBean(objName, attributeNames);
                        result.put(objName, mBean);

                        break;
                    }
                    if (Character.isLetterOrDigit(c) || c == '*') {
                        attributeName.append(c);
                        break;
                    }
                    throw new IllegalArgumentException(unexpectedCharAtMessage + i);

                default:
                    throw new IllegalArgumentException(unexpectedCharAtMessage + i);
            }
        }

        switch (state) {
            case INITIAL_STATE:
                break;
            default:
                throw new IllegalArgumentException("The list must ends with ']'");
        }

        return result;
    }

    private final List<MBeanAttribute> mBeanAttributes = new ArrayList<>();
    private final List<MBeanAttribute> badMBeanAttributes = new ArrayList<>();

    private final ObjectName objectName;
    private final Set<String> attributeNames;

    private MBeanInfo mBeanInfo;

    public MBean(final String objectName, final Collection<String> attributeNames)
            throws MalformedObjectNameException {
        this.objectName = new ObjectName(objectName);
        this.attributeNames = new HashSet<>(attributeNames);
    }

    public ObjectName objectName() {
        return objectName;
    }

    public Set<String> attributeNames() {
        return attributeNames;
    }

    public MBeanInfo mBeanInfo() {
        return mBeanInfo;
    }

    /**
     * Refresh counters' values in the CounterWriter provided.
     * @param countersWriter The counter writer to create/refresh counters
     * @param server The MBeanServer to query attributes and their values
     */
    public void refresh(final MCountersWriter countersWriter, final MBeanServer server) {
        if (mBeanInfo == null) {
            try {
                mBeanInfo = server.getMBeanInfo(objectName);

                if (!mBeanAttributes.isEmpty()) {
                    throw new IllegalStateException();
                }

                final boolean any = attributeNames.contains("*");

                Arrays
                        .stream(mBeanInfo.getAttributes())
                        .filter(ai ->
                                any || attributeNames.contains(ai.getName())
                        )
                        .filter(ai -> {
                            if (!ai.isReadable()) {
                                return false;
                            }
                            final String type = ai.getType();
                            return "long".equals(type) ||
                                    "int".equals(type) ||
                                    "short".equals(type) ||
                                    "byte".equals(type) ||
                                    "double".equals(type) ||
                                    "float".equals(type) ||
                                    "boolean".equals(type);
                        })
                        .forEach(ai -> mBeanAttributes.add(new MBeanAttribute(ai)));
            } catch (final Throwable ignore) { // cannot reach specified MBean with its attributes right now
                return; // just return
            }
        }

        if (mBeanAttributes.isEmpty()) {
            try {
                server.getMBeanInfo(objectName); // check object's availability
            } catch (final Throwable t) {
                close();
            } finally {
                return;
            }
        }

        for (final MBeanAttribute mBeanAttribute : mBeanAttributes) {
            try {
                mBeanAttribute.refresh(countersWriter, server);
            } catch (final InstanceNotFoundException e) { // we have lost the MBean
                close();
                return;
            } catch (final Throwable t) { // any other problem like AttributeNotFoundException,
                // MBeanException, UnsupportedOperationException (for unsupported type) etc.
                badMBeanAttributes.add(mBeanAttribute);
            }
        }

        if (!badMBeanAttributes.isEmpty()) {
            for (final MBeanAttribute badMBeanAttribute : badMBeanAttributes) {
                try {
                    badMBeanAttribute.close();
                } catch (final Throwable ignore) {
                }
            }
            mBeanAttributes.removeAll(badMBeanAttributes);
            badMBeanAttributes.clear();
        }
    }

    /**
     * Closes all counters prepared for attributes of this MBean.
     */
    @Override
    public void close() {
        if (mBeanInfo == null) {
            return;
        }

        try {
            for (final MBeanAttribute mBeanAttribute : mBeanAttributes) {
                try {
                    mBeanAttribute.close();
                } catch (final Throwable ignore) {
                }
            }
        } finally {
            badMBeanAttributes.clear();
            mBeanAttributes.clear();
            mBeanInfo = null;
        }
    }

    /**
     * Returns text representation of an internal state of this MBean.
     * @return internal state of the class
     */
    @Override
    public String toString() {
        return "MBean{" +
                "objectName=" + objectName +
                ", mBeanInfo=" + mBeanInfo +
                ", attributeNames=" + attributeNames +
                ", mBeanAttributes=" + mBeanAttributes +
                '}';
    }

    private class MBeanAttribute implements AutoCloseable {
        private final MBeanAttributeInfo attributeInfo;
        private MCounter counter;

        MBeanAttribute(final MBeanAttributeInfo attributeInfo) {
            this.attributeInfo = attributeInfo;
        }

        void refresh(final MCountersWriter countersWriter, final MBeanServer server)
                throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {

            final String attributeName = attributeInfo.getName();

            final Object value = server.getAttribute(objectName, attributeName);

            final long counterValue;
            if (value instanceof Number) {
                counterValue = ((Number) value).longValue();
            } else if (value instanceof Boolean) {
                counterValue = (Boolean) value ? 1 : 0;
            } else {
                throw new UnsupportedOperationException("Unsupported JMX value type of the value " +
                        value + " of " + objectName + '[' + attributeName + ']');
            }

            if (counter == null) {
                counter = countersWriter.addCounter("jmx://" +
                        objectName.getCanonicalName() + "?" +
                        attributeName);
            }

            counter.set(counterValue);
        }

        @Override
        public void close() throws Exception {
            if (counter == null) {
                return;
            }

            counter.close();

            counter = null;
        }
    }
}