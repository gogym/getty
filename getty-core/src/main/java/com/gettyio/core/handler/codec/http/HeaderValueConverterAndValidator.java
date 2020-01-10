package com.gettyio.core.handler.codec.http;

public final class HeaderValueConverterAndValidator {
    public static void validateHeaderName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException("name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
                case '\t':
                case '\n':
                case 0x0b:
                case '\f':
                case '\r':
                case ' ':
                case ',':
                case ':':
                case ';':
                case '=':
                    throw new IllegalArgumentException("name contains one of the following prohibited characters: =,;: \\t\\r\\n\\v\\f: " + name);
            }
        }
    }

    public static void validateHeaderValue(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        // 0 - the previous character was neither CR nor LF
        // 1 - the previous character was CR
        // 2 - the previous character was LF
        int state = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // Check the absolutely prohibited characters.
            switch (c) {
                case 0x0b: // Vertical tab
                    throw new IllegalArgumentException("value contains a prohibited character '\\v': " + value);
                case '\f':
                    throw new IllegalArgumentException("value contains a prohibited character '\\f': " + value);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
                case 0:
                    switch (c) {
                        case '\r':
                            state = 1;
                            break;
                        case '\n':
                            state = 2;
                            break;
                    }
                    break;
                case 1:
                    switch (c) {
                        case '\n':
                            state = 2;
                            break;
                        default:
                            throw new IllegalArgumentException("Only '\\n' is allowed after '\\r': " + value);
                    }
                    break;
                case 2:
                    switch (c) {
                        case '\t':
                        case ' ':
                            state = 0;
                            break;
                        default:
                            throw new IllegalArgumentException("Only ' ' and '\\t' are allowed after '\\n': " + value);
                    }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException("value must not end with '\\r' or '\\n':" + value);
        }
    }

}