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
package org.java.mc4j.tools.console;

import org.java.mc4j.MCountersReader;
import org.java.mc4j.cli.Application;
import org.java.mc4j.cli.Options;

import java.io.File;
import java.io.PrintStream;

/**
 * Prints all info from a counters' file:
 * <ul>
 *     <li>name of the counters' file</li>
 *     <li>version of the counters' file</li>
 *     <li>PID</li>
 *     <li>start time</li>
 *     <li>statics</li>
 *     <li>counters</li>
 * </ul>
 */
public class MCountersPrinter extends Application {
    private final Options.Argumented file;

    public MCountersPrinter(final String[] args) {
        super("mcprinter", args);

        withDescription("Prints all info from a counters' file.");
        withUsage("-f /var/mcounters.dat",
                "Parses and prints out the content of the 'mcounters.dat' file.");

        file = withArgumented("file", 'f', "FILE")
                .require()
                .withDescription("Path to a counters' file to be parsed.");
    }

    @Override
    protected void doWork() throws Throwable {
        final File countersFile = file.existingFileValue();

        final PrintStream output = System.out;

        output.println("file: " + countersFile.getAbsolutePath());

        try (MCountersReader mCountersReader = new MCountersReader(countersFile)) {

            output.println("version: " + mCountersReader.getVersion());
            output.println("pid: " + mCountersReader.getPid());
            output.println("started: " + mCountersReader.getStartTime());

            mCountersReader.forEachStatic((label, value) ->
                    output.printf("static: %s=%s%n", label, value));

            mCountersReader.forEachCounter((id, label, value) ->
                    output.printf("counter: %s[%d]=%d%n", label, id, value));
        }
    }

    public static void main(final String[] args) {
        new MCountersPrinter(args).start();
    }
}
