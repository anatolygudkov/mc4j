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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Usage {
    public static final String NL = System.lineSeparator();

    private static final int SCREEN_WIDTH = 80;
    private static final double USAGE_COLUMNS_WIDTH_FACTOR = 0.6;
    private static final double OPTIONS_COLUMNS_WIDTH_FACTOR = 0.5;

    private final String name;
    private final String command;
    private final Options options;

    private final List<DescriptedItem> usages = new ArrayList<>();
    private String version;
    private String description;

    public Usage(final String name, final String command, final Options options) {
        this.name = name;
        this.command = command;
        this.options = options;
    }

    public Usage withUsage(final String arguments, final String description) {
        usages.add(new DescriptedItem(command + " " + arguments, description));
        return this;
    }

    public Usage withVersion(final String version) {
        this.version = version;
        return this;
    }

    public Usage withDescription(final String description) {
        this.description = description;
        return this;
    }

    public void write(final Appendable to) throws IOException {
        to.append(name);
        if (!isNullOrBlank(version)) {
            to.append(" - ").append(version);
        }
        to.append(NL);
        to.append(NL);

        if (!isNullOrBlank(description)) {
            for (final String s : new WordWrapper(description, SCREEN_WIDTH).toArray()) {
                to.append(s).append(NL);
            }
            to.append(NL);
        }

        if (!usages.isEmpty()) {
            new DescriptiveTable("Usage:", usages)
                    .write(to, USAGE_COLUMNS_WIDTH_FACTOR);
        }

        if (options.hasOptions()) {
            final List<DescriptedItem> options = Arrays.stream(this.options.options())
                    .map(option -> new DescriptedItem(option.descriptiveName(), option.description()))
                    .collect(Collectors.toList());
            new DescriptiveTable("Options:", options)
                    .write(to, OPTIONS_COLUMNS_WIDTH_FACTOR);
        }
    }

    private class DescriptedItem {
        private final String item;
        private final String description;

        DescriptedItem(final String item, final String description) {
            this.item = item;
            this.description = description;
        }
    }

    private static class DescriptiveTable {
        private static final int COLUMN_SPACING = 2;
        private static final String COLUMN_SPACE = String.format("%" + COLUMN_SPACING + "s", "");
        private static final String TWO_WS = "  ";
        private static final String NEXT_LINE = " \\";

        private final String name;
        private final String[] items;
        private final String[] descriptions;

        DescriptiveTable(final String name,
                                final Collection<DescriptedItem> items) {
            this.name = name;
            this.items = new String[items.size()];
            this.descriptions = new String[items.size()];
            int i = 0;
            for (final DescriptedItem item : items) {
                this.items[i] = item.item;
                this.descriptions[i] = item.description;
                i++;
            }
        }

        void write(final Appendable to, final double targetColumnsFactor) throws IOException {
            to.append(name).append(NL);

            final String[][] itemsLines = new String[items.length][];
            final String[][] descsLines = new String[descriptions.length][];

            final int targetMaxItemWidth = (int) (SCREEN_WIDTH * targetColumnsFactor) -
                    COLUMN_SPACING -
                    TWO_WS.length() -    // in case of
                    NEXT_LINE.length();  // multiline

            int maxItemWidth = 0;
            boolean isMultilineItems = false;

            for (int i = 0; i < items.length; i++) {
                final List<String> itemLines = new ArrayList<>();
                final String[] wrappedLines = new WordWrapper(items[i], targetMaxItemWidth).toArray();
                final boolean isMultilineItem = wrappedLines.length > 1;
                for (int j = 0; j < wrappedLines.length; j++) {
                    String line = wrappedLines[j];
                    if (isMultilineItem) {
                        if (j > 0) {
                            line = TWO_WS + line;
                        }
                        if (j < wrappedLines.length - 1) {
                            line = line + NEXT_LINE;
                        }
                    }
                    itemLines.add(line);
                    if (line.length() > maxItemWidth) {
                        maxItemWidth = line.length();
                    }
                }
                itemsLines[i] = itemLines.toArray(new String[itemLines.size()]);
                isMultilineItems |= isMultilineItem;
            }

            final int descriptionWidth = SCREEN_WIDTH -
                    COLUMN_SPACING -
                    maxItemWidth -
                    COLUMN_SPACING;

            for (int i = 0; i < descriptions.length; i++) {
                descsLines[i] = new WordWrapper(descriptions[i], descriptionWidth).toArray();
            }

            for (int i = 0; i < itemsLines.length; i++) {
                if (isMultilineItems && i > 0) {
                    to.append(NL);
                }

                final String[] itemLines = itemsLines[i];
                final String[] descLines = descsLines[i];

                final int both = Math.min(itemLines.length, descLines.length);

                for (int j = 0; j < both; j++) {
                    to.append(COLUMN_SPACE);
                    final String line = itemLines[j];
                    to.append(line);
                    for (int k = 0; k < maxItemWidth - line.length(); k++) {
                        to.append(" ");
                    }
                    to.append(COLUMN_SPACE);
                    to.append(descLines[j]);
                    to.append(NL);
                }

                for (int j = both; j < itemLines.length; j++) {
                    to.append(COLUMN_SPACE);
                    to.append(itemLines[j]);
                    to.append(NL);
                }

                for (int j = both; j < descLines.length; j++) {
                    to.append(COLUMN_SPACE);
                    for (int k = 0; k < maxItemWidth; k++) {
                        to.append(" ");
                    }
                    to.append(COLUMN_SPACE);
                    to.append(descLines[j]);
                    to.append(NL);
                }
            }
        }
    }

    private static class WordWrapper implements CharSequence {
        private final CharSequence text;
        private final int width;
        private int startIndex;
        private int endIndex;

        WordWrapper(final CharSequence text, final int width) {
            if (width < 1) {
                throw new IllegalArgumentException("Width should be 1 or more");
            }
            this.text = text;
            this.width = width;
            this.startIndex = -1;
            this.endIndex = -1;
        }

        public String[] toArray() {
            final List<String> result = new ArrayList<>();
            while (next() != null) {
                result.add(this.toString());
            }
            return result.toArray(new String[result.size()]);
        }

        private static final int NOT_WS_STATE = 0;
        private static final int WS_STATE = 1;

        public CharSequence next() {
            if (endIndex + 1 == text.length()) {
                return null;
            }

            startIndex = endIndex + 1;

            char c;
            while (true) {
                c = text.charAt(startIndex);
                if (!Character.isWhitespace(c)) {
                    break;
                }
                startIndex++;
                if (startIndex == text.length()) {
                    return null;
                }
            }

            endIndex = startIndex;

            int state = NOT_WS_STATE;
            int currentIndex = startIndex;

            while (true) {
                currentIndex++;
                if (currentIndex == text.length()) {
                    switch (state) {
                        case NOT_WS_STATE:
                            endIndex = currentIndex - 1;
                            break;
                        default:
                            break;
                    }
                    return this;
                }
                c = text.charAt(currentIndex);
                switch (state) {
                    case NOT_WS_STATE:
                        if (Character.isWhitespace(c)) {
                            endIndex = currentIndex - 1;
                            state = WS_STATE;
                            break;
                        }
                        if (currentIndex - startIndex + 1 >= width) {
                            if (startIndex == endIndex) { // only one single word
                                break; // still continue
                            }
                            return this;
                        }
                        break;
                    case WS_STATE:
                        if (!Character.isWhitespace(c)) {
                            if (currentIndex - startIndex + 1 > width) {
                                return this;
                            }
                            state = NOT_WS_STATE;
                        }
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }

        @Override
        public int length() {
            if (startIndex == -1) {
                return text.length();
            }
            return endIndex - startIndex + 1;
        }

        @Override
        public char charAt(final int index) {
            if (startIndex == -1) {
                return text.charAt(index);
            }
            return text.charAt(startIndex + index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            if (startIndex == -1) {
                return text.subSequence(start, end);
            }
            return new StringBuilder().append(text, startIndex + start, startIndex + end);
        }

        public String toString() {
            if (startIndex == -1) {
                return text.toString();
            }
            return subSequence(0, endIndex - startIndex + 1).toString();
        }
    }

    private static boolean isNullOrBlank(final String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}