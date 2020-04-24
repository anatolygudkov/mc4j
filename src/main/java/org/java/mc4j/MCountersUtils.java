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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static java.lang.System.getProperty;
import static java.lang.invoke.MethodType.methodType;
import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class MCountersUtils {
    public static final int SIZE_OF_INT = 4;
    public static final int SIZE_OF_LONG = 8;

    public static final int SIZE_OF_CACHE_LINE = 64;

    /**
     * Property name for page size to align all files to.
     */
    public static final String FILE_PAGE_SIZE_PROP_NAME = "mcounters.file.page.size";

    /**
     * Default page size for alignment of all files.
     */
    public static final int FILE_PAGE_SIZE_DEFAULT = 4 * 1024;

    /**
     * Current page size for alignment of all files.
     */
    public static final int FILE_PAGE_SIZE;

    /**
     *
     */
    public static final String MCOUNTERS_DIR_PROP_NAME = "mcounters.dir";
    /**
     *
     */
    public static final String MCOUNTERS_DIR_PROP_DEFAULT;

    /**
     * Process identifier.
     */
    public static final long PID;

    /**
     * Value of PID if not recognized.
     */
    public static final long UNKNOWN_PID = 0;

    private static final String SUN_PID_PROP_NAME = "sun.java.launcher.pid";

    private static final boolean IS_LINUX;

    private static final MethodHandle INVOKE_CLEANER;
    private static final MethodHandle GET_CLEANER;
    private static final MethodHandle CLEAN;

    static {
        final String osName = System.getProperty("os.name").toLowerCase();
        IS_LINUX = osName.contains("linux");

        long pid = UNKNOWN_PID;

        final MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            try {
                // JDK 9+
                final Class<?> processHandlerClass = Class.forName("java.lang.ProcessHandle");
                final MethodHandle invokeCurrent = lookup.findStatic(processHandlerClass,
                        "current",
                        methodType(processHandlerClass));
                final MethodHandle invokePid = lookup.findVirtual(processHandlerClass,
                        "pid",
                        methodType(long.class));
                final Object processHandler = invokeCurrent.invokeExact(processHandlerClass);
                pid = (long) invokePid.invokeExact(processHandler);
            } catch (final Throwable t) {
                // JDK 8
                final String pidPropertyValue = System.getProperty(SUN_PID_PROP_NAME);
                if (null != pidPropertyValue) {
                    pid = Long.parseLong(pidPropertyValue);
                } else {
                    final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
                    pid = Long.parseLong(jvmName.split("@")[0]);
                }
            }
        } catch (final Throwable ignore) {
        }

        PID = pid;

        FILE_PAGE_SIZE = Integer.getInteger(FILE_PAGE_SIZE_PROP_NAME, FILE_PAGE_SIZE_DEFAULT);

        try {
            MethodHandle invokeCleaner = null;
            MethodHandle getCleaner = null;
            MethodHandle clean = null;

            try {
                invokeCleaner = lookup.findVirtual(
                        UnsafeAccess.getUnsafe().getClass(),
                        "invokeCleaner",
                        methodType(void.class, ByteBuffer.class));
            } catch (final NoSuchMethodException ex) {
                // JDK 8
                final Class<?> directBuffer = Class.forName("sun.nio.ch.DirectBuffer");
                final Class<?> cleaner = Class.forName("sun.misc.Cleaner");
                getCleaner = lookup.findVirtual(directBuffer, "cleaner", methodType(cleaner));
                clean = lookup.findVirtual(cleaner, "clean", methodType(void.class));
            }

            INVOKE_CLEANER = invokeCleaner;
            GET_CLEANER = getCleaner;
            CLEAN = clean;
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        String baseDirName = null;

        if (IS_LINUX) {
            final File devShmDir = new File("/dev/shm");
            if (devShmDir.exists()) {
                baseDirName = devShmDir.getAbsolutePath();
            }
        }
        if (null == baseDirName) {
            baseDirName = System.getProperty("java.io.tmpdir");
        }
        if (!baseDirName.endsWith(File.separator)) {
            baseDirName += File.separator;
        }

        MCOUNTERS_DIR_PROP_DEFAULT = baseDirName + "mcounters-" + System.getProperty("user.name", "default");
    }

    public static String getMCountersDirectoryName() {
        return getProperty(MCOUNTERS_DIR_PROP_NAME, MCOUNTERS_DIR_PROP_DEFAULT);
    }

    /**
     * Creates a new file and returns a {@link java.nio.MappedByteBuffer} for the file.
     * <p>
     * The file itself will be closed, but the mapping will persist.
     *
     * @param pathToFile of the file to create and map.
     * @param length     of the file to create and map.
     * @return {@link java.nio.MappedByteBuffer} for the file.
     */
    public static MappedByteBuffer mapNewFile(final File pathToFile, final long length) throws IOException {
        final MappedByteBuffer result;

        try (FileChannel channel = FileChannel.open(pathToFile.toPath(), CREATE_NEW, READ, WRITE)) {
            result = channel.map(READ_WRITE, 0, length);
            // now pre-touch all the pages
            int position = 0;
            while (position < length) {
                result.put(position, (byte) 0);
                position += FILE_PAGE_SIZE;
            }
        }

        return result;
    }

    public static MappedByteBuffer mapExistingFileReadOnly(final File pathToFile) throws IOException {
        try (RandomAccessFile file =
                     new RandomAccessFile(pathToFile, "r"); FileChannel channel = file.getChannel()) {
            return channel.map(READ_ONLY, 0, channel.size());
        }
    }

    public static void unmap(final ByteBuffer buffer) throws IOException {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }

        try {
            if (INVOKE_CLEANER != null) { // JDK 9+
                INVOKE_CLEANER.invokeExact(UnsafeAccess.getUnsafe(), buffer);
            } else { // JDK 8
                final Object cleaner = GET_CLEANER.invoke(buffer);
                if (null != cleaner) {
                    CLEAN.invoke(cleaner);
                }
            }
        } catch (final Throwable t) {
            throw new IOException(t);
        }
    }

    /**
     * Align an value to the next multiple up of alignment.
     * If the value equals an alignment multiple then it is returned unchanged.
     * <p>
     *
     * @param value     to be aligned up.
     * @param alignment to be used.
     * @return the value aligned to the next boundary.
     */
    public static int align(final int value, final int alignment) {
        return (value + (alignment - 1)) & -alignment;
    }
}