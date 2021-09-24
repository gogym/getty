
package com.gettyio.core.buffer;

/**
 * 当用户试图访问{@link ReferenceCounted}时，将引发{@link IllegalStateException}
 * 引用计数被减少到0(因此被释放)。
 */
public class IllegalReferenceCountException extends IllegalStateException {

    private static final long serialVersionUID = -2507492394288153468L;

    public IllegalReferenceCountException() {
    }

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
