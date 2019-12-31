package com.gettyio.core.handler.codec.websocket;


import java.util.HashMap;
import java.util.Map;

public enum Opcode {

    CONTINUATION((byte) 0), TEXT((byte) 1), BINARY((byte) 2), CLOSE((byte) 8), PING((byte) 9), PONG((byte) 10);

    private static Map<Byte, Opcode> map = new HashMap<>();

    static {
        for (Opcode opcode : values()) {
            map.put(opcode.getCode(), opcode);
        }
    }

    public static Opcode valueOf(byte code) {
        return map.get(code);
    }

    private byte code;

    private Opcode(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

}
