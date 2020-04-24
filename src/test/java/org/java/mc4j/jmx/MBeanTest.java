package org.java.mc4j.jmx;

import org.junit.jupiter.api.Test;

import javax.management.MalformedObjectNameException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MBeanTest {

    @Test
    void parseEmpty() throws MalformedObjectNameException {
        assertTrue(MBean.parseMBeans(null).isEmpty());
        assertTrue(MBean.parseMBeans("").isEmpty());
        assertTrue(MBean.parseMBeans(" ").isEmpty());
        assertTrue(MBean.parseMBeans(" \t\r\n ").isEmpty());
    }

    @Test
    void parseNonEmpty() throws MalformedObjectNameException {
        final String runtimeMBeanName = "java.lang:type=Runtime";
        final String runtimeMBeanStartTimeAttr = "StartTime";
        final String runtimeMBeanUptimeAttr = "Uptime";

        final String osMBeanName = "java.lang:type=OperatingSystem";

        final Map<String, MBean> mBeanMap = MBean.parseMBeans(" " + runtimeMBeanName +
                "[  " + runtimeMBeanStartTimeAttr + ", " + runtimeMBeanUptimeAttr + "] " +
                osMBeanName + "[*  ] ");

        assertEquals(2, mBeanMap.size());

        final MBean runtimeMBean = mBeanMap.get(runtimeMBeanName);
        assertNotNull(runtimeMBean);

        assertEquals(runtimeMBeanName, runtimeMBean.objectName().getCanonicalName());
        final Set<String> runtimeMBeanAttrs = runtimeMBean.attributeNames();
        assertEquals(2, runtimeMBeanAttrs.size());
        assertTrue(runtimeMBeanAttrs.contains(runtimeMBeanStartTimeAttr));
        assertTrue(runtimeMBeanAttrs.contains(runtimeMBeanUptimeAttr));

        final MBean osMBean = mBeanMap.get(osMBeanName);
        assertNotNull(osMBean);
        final Set<String> osMBeanAttrs = osMBean.attributeNames();
        assertEquals(1, osMBeanAttrs.size());
        assertTrue(osMBeanAttrs.contains("*"));
    }
}