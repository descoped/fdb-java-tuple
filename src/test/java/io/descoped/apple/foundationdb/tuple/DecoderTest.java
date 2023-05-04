package io.descoped.apple.foundationdb.tuple;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class DecoderTest {

    static final Map<Class<? extends Decoder<?>>, Decoder<?>> factory = new HashMap<>();

    static {
        factory.put(StringDecoder.class, new StringDecoder());
    }

    @Test
    public void decode() {
        // Integer.MAX_VALUE
        // length: 15
        // \x02a\x00\x02bb\x00\x18\x7f\xff\xff\xff\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 24, 127, -1, -1, -1, 2, 99, 0] -> a bb ���c 
        byte[] rep = {2, 97, 0, 2, 98, 98, 0, 24, 127, -1, -1, -1, 2, 99, 0};
        // INT_ZERO is a reference count for number of bytes to read ahead
        byte[] intMax = {24, 127, -1, -1, -1};

        int pos = 0;
        if (rep[pos] == Type.BYTE.start) {

            for (int j = pos; j < rep.length; j++) {
                if (rep[j] == Type.BYTE.end) {
                    break;
                }
            }
            Type.BYTE.decoder.decode(rep);
        }

        ByteBuffer buffer = ByteBuffer.wrap(rep);
        buffer.mark();
        Type type = Type.codeFor(buffer.get());
        buffer.reset();

        for (int i = 0; i < rep.length; i++) {
            // TODO eval start byte for fixed control start signature
            //  get type and invoke decoder
        }

        Object s = factory.get(StringDecoder.class).decode(new byte[0]);
    }

    enum Type {
        BYTE(0x01, 0x00, new ByteArrayDecoder()),
        STRING(0x02, 0x00, new StringDecoder());

        final byte start;
        final int end;
        final Decoder<?> decoder;

        Type(int start, int end, Decoder<?> decoder) {
            this.start = (byte) start;
            this.end = end;
            this.decoder = decoder;
        }

        static Type codeFor(byte b) {
            for (Type type : values()) {
                if (b == type.start) {
                    return type;
                }
            }
            return null;
        }
    }

    interface Decoder<T> {
        T decode(byte[] bytes);
    }

    static class ByteArrayDecoder implements Decoder<byte[]> {

        @Override
        public byte[] decode(byte[] bytes) {
            return new byte[0];
        }
    }

    static class StringDecoder implements Decoder<String> {

        @Override
        public String decode(byte[] bytes) {
            return null;
        }
    }
}
