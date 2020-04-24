package org.java.mc4j.jmx;

public class TestAttributes implements TestAttributesMBean {
    private long longAttribute;
    private int intAttribute;
    private short shortAttribute;
    private byte byteAttribute;
    private double doubleAttribute;
    private float floatAttribute;
    private boolean booleanAttribute;

    private String stringAttribute; // must not be read

    @Override
    public long getLongAttribute() {
        return longAttribute;
    }

    @Override
    public int getIntAttribute() {
        return intAttribute;
    }

    @Override
    public short getShortAttribute() {
        return shortAttribute;
    }

    @Override
    public byte getByteAttribute() {
        return byteAttribute;
    }

    @Override
    public double getDoubleAttribute() {
        return doubleAttribute;
    }

    @Override
    public float getFloatAttribute() {
        return floatAttribute;
    }

    @Override
    public boolean isBooleanAttribute() {
        return booleanAttribute;
    }

    @Override
    public String getStringAttribute() {
        return stringAttribute;
    }

    public void increment() {
        longAttribute++;
        intAttribute++;
        shortAttribute++;
        byteAttribute++;
        doubleAttribute++;
        floatAttribute++;
        booleanAttribute = !booleanAttribute;
    }
}