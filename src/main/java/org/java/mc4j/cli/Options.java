/**
 * MIT License
 * <p>
 * Copyright (c) 2020 anatolygudkov
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.java.mc4j.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Options {
    public abstract class Option<T extends Option> {
        protected final String longName;
        protected final char shortName;
        protected boolean required;
        protected String description;

        protected Option(final String longName, final char shortName) {
            if (longName != null) {
                if (!isLetterOrDigit(longName)) {
                    throw new IllegalArgumentException("Long name should consist of letters or digits only");
                }
            } else {
                if (shortName == 0) {
                    throw new IllegalArgumentException("Long name or short name should be specified");
                }
            }
            this.longName = longName;
            this.shortName = shortName;
        }

        public String longName() {
            return longName;
        }

        public char shortName() {
            return shortName;
        }

        public abstract String descriptiveName();

        public String description() {
            return description;
        }

        @SuppressWarnings("unchecked")
        public T withDescription(final String description) {
            this.description = description;
            return (T) this;
        }

        public boolean isRequired() {
            return required;
        }

        @SuppressWarnings("unchecked")
        public T require() {
            this.required = true;
            return (T) this;
        }

        public boolean isSet() {
            checkParsed();
            return arguments.containsKey(this);
        }
    }

    public class Flag extends Option<Flag> {
        Flag(final String name, final char shortName) {
            super(name, shortName);
        }

        public String descriptiveName() {
            final StringBuilder result = new StringBuilder();
            if (shortName != 0) {
                result.append("-").append(shortName);
            }
            if (longName != null) {
                if (result.length() > 0) {
                    result.append("  or  ");
                }
                result.append("--")
                        .append(longName);
            }
            return result.toString();
        }
    }

    public class Argumented extends Option<Argumented> {
        private final String argumentName;
        private String defaultArgumentValue;

        Argumented(final String name, final char shortName, final String argumentName) {
            super(name, shortName);
            this.argumentName = argumentName;
        }

        @Override
        public Argumented require() {
            super.require();
            defaultArgumentValue = null;
            return this;
        }

        public String defaultArgumentValue() {
            return defaultArgumentValue;
        }

        public Argumented withDefaultArgumentValue(final String defaultArgumentValue) {
            required = false;
            this.defaultArgumentValue = defaultArgumentValue;
            return this;
        }

        public String descriptiveName() {
            final StringBuilder result = new StringBuilder();
            if (shortName != 0) {
                result.append("-").append(shortName);
                result.append(" <").append(argumentName).append('>');
            }
            if (longName != null) {
                if (result.length() > 0) {
                    result.append("  or  ");
                }
                result.append("--")
                        .append(longName);
                result.append(" <").append(argumentName).append('>');
            }
            return result.toString();
        }

        public String stringValue() {
            checkParsed();
            final String result = arguments.get(this);
            return result != null ? result : defaultArgumentValue;
        }

        public int intValue() {
            return Integer.parseInt(stringValue());
        }

        public long longValue() {
            return Long.parseLong(stringValue());
        }

        public File fileValue() {
            return new File(stringValue());
        }

        public File existingFileValue() {
            final File result = fileValue();
            if (!result.exists()) {
                throw new IllegalArgumentException("Cannot find file: " + result.getAbsolutePath());
            }
            return result;
        }

        public boolean booleanValue() {
            String s = stringValue();
            if (s == null) {
                return false;
            }
            s = s.trim().toLowerCase();
            if (s.isEmpty()) {
                return false;
            }
            return "y".equals(s) ||
                    "yes".equals(s) ||
                    "true".equals(s) ||
                    "1".equals(s);
        }
    }

    private final Map<String, Option> longOptions = new HashMap<>();
    private final Map<Character, Option> shortOptions = new HashMap<>();
    private final List<Option> allOptions = new ArrayList<>();
    private final Map<Option, String> arguments = new IdentityHashMap<>();
    private boolean parsed;

    public Options() {
    }

    private void checkParsed() {
        if (parsed) {
            return;
        }
        throw new IllegalStateException("Options not parsed yet");
    }

    public Options.Option<?>[] options() {
        return allOptions.toArray(new Option[allOptions.size()]);
    }

    public boolean hasOptions() {
        return !allOptions.isEmpty();
    }

    public Flag withFlag(final String longName, final char shortName) {
        return registerOption(new Flag(longName, shortName));
    }

    public Argumented withArgumented(final String longName, final char shortName, final String argumentName) {
        return registerOption(new Argumented(longName, shortName, argumentName));
    }

    private static final int PARAM_EXPECTED_STATE = 0;
    private static final int ARGUMENT_EXPECTED_STATE = 1;

    public String[] parse(final String[] args) {
        parsed = true;
        arguments.clear();

        final List<String> parameters = new ArrayList<>();

        int currentIndex = 0;

        int state = PARAM_EXPECTED_STATE;
        Argumented currentOptionToArgument = null;

        _loop:
        while (currentIndex < args.length) {
            String s = args[currentIndex];
            if (s == null) {
                currentIndex++;
                continue;
            }
            s = s.trim();
            if (s.isEmpty()) {
                currentIndex++;
                continue;
            }

            final char firstChar = s.charAt(0);
            switch (firstChar) {
                case '-': {
                    switch (state) {
                        case PARAM_EXPECTED_STATE: {
                            if (s.length() == 1) {
                                throw new IllegalArgumentException("'-' isn't allowed option");
                            }
                            final char secondChar = s.charAt(1);
                            switch (secondChar) {
                                case '-':
                                    if (s.length() == 2) { // '--' - end of options
                                        currentIndex++;
                                        break _loop;
                                    }
                                    currentOptionToArgument = parseLong(s);
                                    if (currentOptionToArgument != null) {
                                        state = ARGUMENT_EXPECTED_STATE;
                                    }
                                    break;
                                default:
                                    currentOptionToArgument = parseShort(s);
                                    if (currentOptionToArgument != null) {
                                        state = ARGUMENT_EXPECTED_STATE;
                                    }
                                    break;
                            }
                            break;
                        }
                        case ARGUMENT_EXPECTED_STATE:
                            throw new IllegalArgumentException(""); // no required arg found for currentOption
                        default:
                            throw new IllegalStateException();
                    }
                    break;
                }
                default: {
                    switch (state) {
                        case PARAM_EXPECTED_STATE: {
                            parameters.add(s);
                            break;
                        }
                        case ARGUMENT_EXPECTED_STATE:
                            arguments.put(currentOptionToArgument, s);
                            state = PARAM_EXPECTED_STATE;
                            currentOptionToArgument = null;
                            break;
                        default:
                            throw new IllegalStateException();
                    }
                    break;
                }
            }
            currentIndex++;
        }

        if (state == ARGUMENT_EXPECTED_STATE) {
            throw new IllegalArgumentException("No required argument found for the option " +
                    currentOptionToArgument.longName());
        }

        // validate required options
        final String missedRequires = allOptions.stream()
                .filter(o -> o.required && !arguments.containsKey(o))
                .map(o -> "'" + o.descriptiveName() + "'")
                .collect(Collectors.joining(", "));

        if (!missedRequires.isEmpty()) {
            throw new IllegalArgumentException("Required option(s) missed: " + missedRequires);
        }

        // collect the rest of parameters
        for (int i = currentIndex; i < args.length; i++) {
            parameters.add(args[i]);
        }

        return parameters.toArray(new String[parameters.size()]);
    }

    private <T extends Option> T registerOption(final T option) {
        if (option.longName() != null) {
            if (longOptions.containsKey(option.longName())) {
                throw new IllegalArgumentException("Duplicated long option: " + option.longName());
            }
            longOptions.put(option.longName(), option);
        }
        if (option.shortName() != 0) {
            if (shortOptions.containsKey(option.shortName())) {
                throw new IllegalArgumentException("Duplicated short option: " + option.shortName());
            }
            shortOptions.put(option.shortName(), option);
        }
        allOptions.add(option);
        return option;
    }

    private Argumented parseShort(final String s) {
        final StringBuilder argument = new StringBuilder();
        Argumented argumented = null;

        for (int i = 1; i < s.length(); i++) { // we know that 's' isn't empty
            final char c = s.charAt(i);

            if (argumented != null) {
                argument.append(c);
                continue;
            }

            final Option nextOption = shortOptions.get(c);
            if (nextOption == null) {
                throw new IllegalArgumentException("Unknown option '" + c + "' in '" + s + "'");
            }

            if (nextOption instanceof Argumented) {
                argumented = (Argumented) nextOption;
                continue;
            }

            if (arguments.containsKey(nextOption)) {
                throw new IllegalArgumentException("Option '" + nextOption.descriptiveName() +
                        "' is duplicated in '" + s + "'");
            }
            arguments.put(nextOption, null);
        }

        if (argumented == null) {
            return null;
        }

        if (argument.length() > 0) {
            arguments.put(argumented, argument.toString());
            return null;
        }

        return null;
    }

    private Argumented parseLong(final String s) {
        final StringBuilder name = new StringBuilder();
        StringBuilder argument = null;

        for (int i = 2; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (argument != null) {
                argument.append(c);
                continue;
            }
            if (c == '=') {
                argument = new StringBuilder();
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                name.append(c);
                continue;
            }
            throw new IllegalArgumentException("Wrong character '" + c + "' in the long name: " + s);
        }

        final Option option = longOptions.get(name.toString());
        if (option == null) {
            throw new IllegalArgumentException("Unknown long option: " + name);
        }

        if (arguments.containsKey(option)) {
            throw new IllegalArgumentException("Option '" + option.descriptiveName() + "' duplicated in '" + s + "'");
        }
        arguments.put(option, null);

        if (!(option instanceof Argumented)) {
            if (argument != null) {
                throw new IllegalArgumentException("Option " + s + " is a flag and cannot have an argument");
            }
            return null;
        }

        if (argument != null) {
            if (argument.length() == 0) {
                throw new IllegalArgumentException("Argument is lost after '=' for the option: " + s);
            }
            arguments.put(option, argument.toString());
            return null;
        }

        return (Argumented) option;
    }

    private static boolean isLetterOrDigit(final String s) {
        if (s == null) {
            throw new IllegalArgumentException("Cannot be null");
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetterOrDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}