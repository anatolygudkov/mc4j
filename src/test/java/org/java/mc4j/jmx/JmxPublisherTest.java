package org.java.mc4j.jmx;

import org.java.mc4j.MCountersReader;
import org.java.mc4j.MCountersWriter;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.java.mc4j.MCountersUtils.getMCountersDirectoryName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JmxPublisherTest {

    @Test
    void testNoMBeans() {
        final File testCountersFile = new File(getMCountersDirectoryName(),
                "junit.jupiter-testNoMBeans-counters.dat");
        if (testCountersFile.exists()) {
            testCountersFile.delete();
        } else {
            testCountersFile.getParentFile().mkdirs();
        }

        try {

            try (MCountersWriter writer =
                         new MCountersWriter(testCountersFile, null, 100);
                 JmxPublisher publisher = new JmxPublisher(writer, null)) {

                publisher.refresh();

                assertTrue(publisher.getMBeans().isEmpty());
            }

        } catch (final Exception e) {
            fail(e);
        } finally {
            testCountersFile.delete();
        }
    }

    @Test
    void test2MBeans() {
        final File testCountersFile = new File(getMCountersDirectoryName(),
                "junit.jupiter-test2MBeans-counters.dat");
        if (testCountersFile.exists()) {
            testCountersFile.delete();
        } else {
            testCountersFile.getParentFile().mkdirs();
        }

        try {

            try (MCountersWriter writer =
                         new MCountersWriter(testCountersFile, null, 100);
                 MCountersReader reader =
                         new MCountersReader(testCountersFile);
                 JmxPublisher publisher = new JmxPublisher(writer, null)) {

                final String mBeanName1 = "org.java.mc4j.jmx.test:type=TestAttributes,name=TestAttributes1";
                final String mBeanName2 = "org.java.mc4j.jmx.test:type=TestAttributes,name=TestAttributes2";

                publisher.addMBeans(mBeanName1 + "[" +
                        "LongAttribute,BooleanAttribute] " + mBeanName2 + "[*]");

                final Collection<MBean> mBeans = publisher.getMBeans();

                assertEquals(2, mBeans.size());

                for (final MBean mBean : mBeans) {
                    assertNull(mBean.mBeanInfo());
                }

                publisher.refresh();

                for (final MBean mBean : mBeans) {
                    assertNull(mBean.mBeanInfo());
                }

                final AtomicInteger numberOfCounters = new AtomicInteger(0);

                reader.forEachCounter((id, label, value) -> numberOfCounters.incrementAndGet());

                assertEquals(0, numberOfCounters.get());

                final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

                final TestAttributes mBean1 = new TestAttributes();
                final TestAttributes mBean2 = new TestAttributes();

                final ObjectName objectName1 =
                        new ObjectName(mBeanName1);
                server.registerMBean(mBean1, objectName1);

                final ObjectName objectName2 =
                        new ObjectName(mBeanName2);
                server.registerMBean(mBean2, objectName2);

                publisher.refresh();

                MBean testMBean1 = null;
                MBean testMBean2 = null;

                for (final MBean mBean : mBeans) {
                    if (objectName1.equals(mBean.objectName())) {
                        testMBean1 = mBean;
                        continue;
                    }
                    if (objectName2.equals(mBean.objectName())) {
                        testMBean2 = mBean;
                        continue;
                    }
                }

                assertNotNull(testMBean1);
                assertNotNull(testMBean1.mBeanInfo());

                assertNotNull(testMBean2);
                assertNotNull(testMBean2.mBeanInfo());

                final AtomicLong sumValue = new AtomicLong();

                numberOfCounters.set(0);
                sumValue.set(0);
                reader.forEachCounter((id, label, value) -> {
                    numberOfCounters.incrementAndGet();
                    sumValue.addAndGet(value);
                });
                assertEquals(9, numberOfCounters.get());
                assertEquals(0, sumValue.get());

                mBean1.increment();
                mBean2.increment();

                publisher.refresh();

                numberOfCounters.set(0);
                sumValue.set(0);
                reader.forEachCounter((id, label, value) -> {
                    numberOfCounters.incrementAndGet();
                    sumValue.addAndGet(value);
                });
                assertEquals(9, numberOfCounters.get());
                assertEquals(9, sumValue.get());

                server.unregisterMBean(objectName1);
                server.unregisterMBean(objectName2);

                publisher.refresh();

                assertNull(testMBean1.mBeanInfo());
                assertNull(testMBean2.mBeanInfo());

                numberOfCounters.set(0);
                reader.forEachCounter((id, label, value) -> numberOfCounters.incrementAndGet());
                assertEquals(0, numberOfCounters.get());
            }
        } catch (final Exception e) {
            fail(e);
        } finally {
            testCountersFile.delete();
        }
    }
}