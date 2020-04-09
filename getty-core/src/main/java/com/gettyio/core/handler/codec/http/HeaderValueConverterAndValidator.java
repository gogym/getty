/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gettyio.core.handler.codec.http;

/**
 * HeaderValueConverterAndValidator.java
 *
 * @description:header头值转换和验证
 * @author:gogym
 * @date:2020/4/9
 * @copyright: Copyright by gettyio.com
 */
public final class HeaderValueConverterAndValidator {

    /**
     * 验证header头
     *
     * @param name
     */
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
                default:
                    break;
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
                // Vertical tab
                case 0x0b:
                    throw new IllegalArgumentException("value contains a prohibited character '\\v': " + value);
                case '\f':
                    throw new IllegalArgumentException("value contains a prohibited character '\\f': " + value);
                default:
                    break;
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
                        default:
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
                default:
                    break;
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException("value must not end with '\\r' or '\\n':" + value);
        }
    }

}