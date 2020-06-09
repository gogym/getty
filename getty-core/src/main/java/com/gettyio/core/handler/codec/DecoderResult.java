package com.gettyio.core.handler.codec;

/**
 * DecoderResult.java
 * @description:
 * @author:gogym
 * @date:2020/6/9
 * @copyright: Copyright by gettyio.com
 */
public class DecoderResult {

    protected static final Throwable SIGNAL_UNFINISHED = new Throwable("UNFINISHED");
    protected static final Throwable SIGNAL_SUCCESS = new Throwable("SUCCESS");

    public static final DecoderResult UNFINISHED = new DecoderResult(SIGNAL_UNFINISHED);
    public static final DecoderResult SUCCESS = new DecoderResult(SIGNAL_SUCCESS);

    public static DecoderResult failure(Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        return new DecoderResult(cause);
    }

    private final Throwable cause;

    protected DecoderResult(Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.cause = cause;
    }

    public boolean isFinished() {
        return cause != SIGNAL_UNFINISHED;
    }

    public boolean isSuccess() {
        return cause == SIGNAL_SUCCESS;
    }

    public boolean isFailure() {
        return cause != SIGNAL_SUCCESS && cause != SIGNAL_UNFINISHED;
    }

    public Throwable cause() {
        if (isFailure()) {
            return cause;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        if (isFinished()) {
            if (isSuccess()) {
                return "success";
            }

            String cause = cause().toString();
            return new StringBuilder(cause.length() + 17)
                    .append("failure(")
                    .append(cause)
                    .append(')')
                    .toString();
        } else {
            return "unfinished";
        }
    }
}
