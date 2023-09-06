/*
 * Copyright 2019 The Getty Project
 *
 * The Getty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.gettyio.core.buffer.bytebuf;

/**
 * 当用户试图访问{@link ReferenceCounted}时，将引发{@link IllegalStateException}
 * 引用计数被减少到0(因此被释放)。
 */
public class IllegalReferenceCountException extends IllegalStateException {

    public IllegalReferenceCountException(int refCnt) {
        this("refCnt: " + refCnt);
    }

    public IllegalReferenceCountException(int refCnt, int increment) {
        this("refCnt: " + refCnt + ", " + (increment > 0 ? "increment: " + increment : "decrement: " + -increment));
    }

    public IllegalReferenceCountException(String message) {
        super(message);
    }

}
