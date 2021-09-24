package com.gettyio.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Cleaner;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;


/**
 * 允许使用{@link Cleaner}释放直接的{@link ByteBuffer}。这是封装在一个额外的类能够
 * 在Android上使用{@link PlatformDependent0}
 */
final class Cleaner0 {
    private final static Logger logger = LoggerFactory.getLogger(Cleaner0.class);

    private static final long CLEANER_FIELD_OFFSET;

    private Cleaner0() {
    }

    static {
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        Field cleanerField;
        long fieldOffset = -1;
        if (PlatformDependent0.hasUnsafe()) {
            try {
                cleanerField = direct.getClass().getDeclaredField("cleaner");
                cleanerField.setAccessible(true);
                Cleaner cleaner = (Cleaner) cleanerField.get(direct);
                cleaner.clean();
                fieldOffset = PlatformDependent0.objectFieldOffset(cleanerField);
            } catch (Throwable t) {
                // We don't have ByteBuffer.cleaner().
                fieldOffset = -1;
            }
        }
        logger.debug("java.nio.ByteBuffer.cleaner(): {}", fieldOffset != -1 ? "available" : "unavailable");
        CLEANER_FIELD_OFFSET = fieldOffset;
        // free buffer if possible
        freeDirectBuffer(direct);
    }


    /**
     * 释放堆外缓冲区
     *
     * @param buffer
     */
    static void freeDirectBuffer(ByteBuffer buffer) {
        if (CLEANER_FIELD_OFFSET == -1 || !buffer.isDirect()) {
            return;
        }
        try {
            Cleaner cleaner = (Cleaner) PlatformDependent0.getObject(buffer, CLEANER_FIELD_OFFSET);
            if (cleaner != null) {
                cleaner.clean();
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }


}
