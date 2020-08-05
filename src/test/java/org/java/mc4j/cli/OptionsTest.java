package org.java.mc4j.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsTest {
    @Test
    void testNoOptions() {
        final Options options = new Options();

        String[] params = options.parse(new String[] {});
        assertEquals(0, params.length);

        final String[] args = new String[] {"param1", "param2"};
        params = options.parse(args);
        assertArrayEquals(args, params);
    }

    @Test
    void testUnknownOption() {
        final Options options = new Options();

        assertThrows(IllegalArgumentException.class, () -> {
            final String[] args = new String[] {"param1", "-x", "param2"};
            options.parse(args);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            final String[] args = new String[] {"param1", "--xyz", "param2"};
            options.parse(args);
        });
    }

    @Test
    void testRequiredShortOption() {
        final String[] args = new String[] {"param1", "-x", "param2"};

        final Options options = new Options();

        final Options.Flag x = options.withFlag('x')
                .require();

        options.parse(args);

        assertTrue(x.isSet());

        final Options.Flag y = options.withFlag('y')
                .require();

        assertThrows(IllegalArgumentException.class, () -> options.parse(args));

        assertFalse(y.isSet());
    }

    @Test
    void testRequiredLongOption() {
        final String[] args = new String[] {"param1", "--xx", "param2"};

        final Options options = new Options();

        final Options.Flag xx = options.withFlag("xx")
                .require();

        options.parse(args);

        assertTrue(xx.isSet());

        final Options.Flag yy = options.withFlag("yy")
                .require();

        assertThrows(IllegalArgumentException.class, () -> options.parse(args));

        assertFalse(yy.isSet());
    }

    @Test
    void testDefaultShortOption(){
        final String[] args = new String[] {"param1", "-x", "valueX"};

        final Options options = new Options();

        final Options.Argumented x = options.withArgumented('x', "VALUEX")
                .withDefaultArgumentValue("DEFAULTX")
                .require();

        assertNull(x.defaultArgumentValue());

        options.parse(args);

        assertTrue(x.isSet());
        assertEquals(args[2], x.stringValue());

        final Options.Argumented y = options.withArgumented('y', "VALUEY")
                .withDefaultArgumentValue("DEFAULTY");

        options.parse(args);

        assertFalse(y.isSet());
        assertEquals(y.defaultArgumentValue(), y.stringValue());
    }

    @Test
    void testDefaultLongOption(){
        final String[] args = new String[] {"param1", "--xx", "valueX"};

        final Options options = new Options();

        final Options.Argumented xx = options.withArgumented("xx", "VALUEXX")
                .withDefaultArgumentValue("DEFAULTXX")
                .require();

        assertNull(xx.defaultArgumentValue());

        options.parse(args);

        assertTrue(xx.isSet());
        assertEquals(args[2], xx.stringValue());

        final Options.Argumented yy = options.withArgumented("yy", "VALUEYY")
                .withDefaultArgumentValue("DEFAULTYY");

        options.parse(args);

        assertFalse(yy.isSet());
        assertEquals(yy.defaultArgumentValue(), yy.stringValue());
    }

    @Test
    void allOptions() {
        final String[] args = new String[] {
                "-q", "-x", "valX", "-yz", "valZ", "--xx", "valXX", "--yy", "param1", "param2", "--", "--zz"
        };

        final Options options = new Options();

        final Options.Flag q = options.withFlag('q');
        final Options.Argumented x = options.withArgumented('x', "VALUEX");
        final Options.Flag y = options.withFlag( 'y');
        final Options.Argumented z = options.withArgumented( 'z', "VALUEZ");
        final Options.Argumented xx = options.withArgumented("xx", "VALUEXX");
        final Options.Flag yy = options
                .withFlag("yy")
                .require();
        final Options.Argumented zz = options
                .withArgumented("z","VALUEZZ")
                .withDefaultArgumentValue("valZZ");

        final String[] params = options.parse(args);

        assertTrue(q.isSet());

        assertTrue(x.isSet());
        assertEquals(args[2], x.stringValue());

        assertTrue(y.isSet());

        assertTrue(z.isSet());
        assertEquals(args[4], z.stringValue());

        assertTrue(xx.isSet());
        assertEquals(args[6], xx.stringValue());

        assertTrue(yy.isSet());

        assertFalse(zz.isSet());
        assertEquals(zz.defaultArgumentValue(), zz.stringValue());

        assertArrayEquals(params, new String[] {args[8], args[9], args[11]});
    }
}