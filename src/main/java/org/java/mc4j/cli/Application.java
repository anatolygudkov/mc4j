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
package org.java.mc4j.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

public abstract class Application {
    private static final boolean IS_LINUX;
    static {
        final String osName = System.getProperty("os.name").toLowerCase();
        IS_LINUX = osName.contains("linux");
    }

    private final Options options = new Options();
    private final Usage usage;

    private final Options.Flag help;
    private final Options.Flag question;

    private final String[] args;

    protected Application(final String name, final String[] args) {
        this(name,
                name + (IS_LINUX ? ".sh" : ".cmd"),
                args);
    }

    protected Application(final String name, final String command, final String[] args) {
        help = options.withFlag("help", 'h').withDescription("This help.");
        question = options.withFlag(null, '?').withDescription(help.description());

        usage = new Usage(name, command, options);

        this.args = args;
    }

    public final Options.Flag withFlag(final String longName,
                                       final char shortName) {
        return options.withFlag(longName, shortName);
    }

    public final Options.Argumented withArgumented(final String longName,
                                                   final char shortName,
                                                   final String argumentName) {
        return options.withArgumented(longName, shortName, argumentName);
    }

    public Usage withUsage(final String arguments,
                           final String description) {
        return usage.withUsage(arguments, description);
    }

    public Usage withVersion(final String version) {
        return usage.withVersion(version);
    }

    public Usage withDescription(final String description) {
        return usage.withDescription(description);
    }

    protected final void start() {
        try {
            options.parse(args);
        } catch (final Throwable t) {
            System.err.println(t.getLocalizedMessage() + Usage.NL);
            doHelp(System.err);
            System.exit(1);
        }

        if (help.isSet() || question.isSet()) {
            doHelp(System.out);
            return;
        }

        try {
            doWork();
        } catch (final Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void doHelp(final PrintStream out) {
        try {
            usage.write(out);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected abstract void doWork() throws Throwable;
}
