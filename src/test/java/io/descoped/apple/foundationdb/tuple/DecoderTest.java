package io.descoped.apple.foundationdb.tuple;

import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.descoped.apple.foundationdb.tuple.DecodeBytesTest.printf;

public class DecoderTest {

    private static final byte nil = 0x00;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final BigInteger LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int UUID_BYTES = 2 * Long.BYTES;
    private static final IterableComparator iterableComparator = new IterableComparator();

    private static final byte BYTES_CODE = 0x01;
    private static final byte STRING_CODE = 0x02;
    private static final byte NESTED_CODE = 0x05;
    private static final byte INT_ZERO_CODE = 0x14; // 20
    private static final byte POS_INT_END = 0x1d;   // 29
    private static final byte NEG_INT_START = 0x0b; // 11
    private static final byte FLOAT_CODE = 0x20;    // 32
    private static final byte DOUBLE_CODE = 0x21;   // 33
    private static final byte FALSE_CODE = 0x26;    // 38
    private static final byte TRUE_CODE = 0x27;     // 39
    private static final byte UUID_CODE = 0x30;     // 64
    private static final byte VERSIONSTAMP_CODE = 0x33;

    private static final byte[] NULL_ARR = new byte[]{nil};
    private static final byte[] NULL_ESCAPED_ARR = new byte[]{nil, (byte) 0xFF};

    @Test
    public void decodeMaxInteger() {
        // Integer.MAX_VALUE
        // length: 15
        // \x02a\x00\x02bb\x00\x18\x7f\xff\xff\xff\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 24, 127, -1, -1, -1, 2, 99, 0] -> a bb ���c 
        byte[] rep = {2, 97, 0, 2, 98, 98, 0, 24, 127, -1, -1, -1, 2, 99, 0};
        //// INT_ZERO is a reference count for number of bytes to read ahead
        //byte[] intMax = {24, 127, -1, -1, -1};

        List<Object> elements = parseBytes(rep);
        elements.forEach(e -> printf("%s", e));
    }

    @Test
    public void decodeMinInteger() {
        // Integer.MIN_VALUE
        // length: 15
        // \x02a\x00\x02bb\x00\x10\x7f\xff\xff\xff\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 16, 127, -1, -1, -1, 2, 99, 0] -> a bb ���c 
        byte[] rep = {16, 127, -1, -1, -1};

        List<Object> elements = parseBytes(rep);
        elements.forEach(e -> printf("%s", e));
    }

    @Test
    public void decodeMaxLong() {
        // length: 19
        // \x02a\x00\x02bb\x00\x1c\x7f\xff\xff\xff\xff\xff\xff\xff\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 28, 127, -1, -1, -1, -1, -1, -1, -1, 2, 99, 0] -> a bb �������c 
        byte[] rep = {28, 127, -1, -1, -1, -1, -1, -1, -1};

        List<Object> elements = parseBytes(rep);
        elements.forEach(e -> printf("%s", e));
    }

    @Test
    public void decodeMinLong() {
        // length: 19
        // \x02a\x00\x02bb\x00\x0c\x7f\xff\xff\xff\xff\xff\xff\xff\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 12, 127, -1, -1, -1, -1, -1, -1, -1, 2, 99, 0] -> a bb �������c 
        byte[] rep = {12, 127, -1, -1, -1, -1, -1, -1, -1};

        List<Object> elements = parseBytes(rep);
        elements.forEach(e -> printf("%s", e));
    }

    @Test
    public void decodeBitInteger() {
        // length: 9
        // \x1c\x80\x00\x00\x00\x00\x00\x00\x00
        // [28, -128, 0, 0, 0, 0, 0, 0, 0] -> �        --> 9223372036854775808
        byte[] rep = {28, -128, 0, 0, 0, 0, 0, 0, 0};
        BigInteger bi = new BigInteger(new byte[]{0, -128, 0, 0, 0, 0, 0, 0, 0});

        List<Object> elements = parseBytes(rep);
        elements.forEach(e -> printf("bi: %s -- tu: %s -- tu-class: %s", bi, e, e.getClass()));
    }

    static List<Object> parseBytes(byte[] rep) {
        ByteArrayDecoder decoder = new ByteArrayDecoder(rep);
        List<Object> elements = new ArrayList<>();
        AtomicBoolean cancel = new AtomicBoolean();
        while (decoder.hasNext() && !cancel.get()) {
            Object token = switch (decoder.type()) {
                case BYTE_ARRAY -> decoder.getByteArray();
                case BOOLEAN -> decoder.getBoolean();
                case STRING -> decoder.getString();
                case INTEGER -> decoder.getInteger();
                case LONG -> decoder.getLong();
                case BIG_INTEGER -> decoder.getBigInteger();
                case FLOAT -> decoder.getFloat();
                case DOUBLE -> decoder.getDouble();
                case UUID -> decoder.getUUID();
                // TODO remove default
                default -> {
                    cancel.set(true);
                    yield null;
                } // throw new UnsupportedOperationException();
            };
            // TODO throw exception on null
            if (token != null) {
                elements.add(token);
            }
        }
        return elements;
    }

    static class ByteArrayDecoder {

        enum Type {
            BOOLEAN,
            BYTE_ARRAY,
            STRING,
            INTEGER,
            LONG,
            BIG_INTEGER,
            FLOAT,
            DOUBLE,
            UUID,
            UNKNOWN;
        }

        final byte[] rep;
        int pos = 0;

        ByteArrayDecoder(byte[] rep) {
            this.rep = rep;
        }

        byte current() {
            return rep[pos];
        }

        public boolean hasNext() {
            return pos < rep.length;
        }

        byte next() {
            return rep[pos++];
        }

        Type type() {
            if (current() == BYTES_CODE) {
                return Type.BYTE_ARRAY;
            } else if (isBoolean()) {
                return Type.BOOLEAN;
            } else if (current() == STRING_CODE) {
                return Type.STRING;
            } else if (isInteger()) {
                return Type.INTEGER;
            } else if (isLong()) {
                return Type.LONG;
            } else if (isBigInteger()) {
                return Type.BIG_INTEGER;
            } else if (isFloat()) {
                return Type.FLOAT;
            } else if (isDouble()) {
                return Type.DOUBLE;
            } else if (isUUID()) {
                return Type.UUID;
            }
//            throw new UnsupportedOperationException();
            return Type.UNKNOWN;
        }

        boolean isNotNullTerminated() {
            return current() != nil
                    && current() != BYTES_CODE
                    && current() != STRING_CODE;
        }

        int findNullTerminator() {
            for (int i = pos; i < rep.length; i++) {
                if (rep[i] == nil) {
                    return i;
                }
            }
            return -1;
        }

        boolean isBoolean() {
            return current() == TRUE_CODE || current() == FALSE_CODE;
        }

        boolean isInteger() {
            int code = current();
            boolean integerRange = code > NEG_INT_START && code < POS_INT_END;
            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int start = pos + 1;
            return integerRange && (positive && (n < Integer.BYTES || rep[start] > 0) || !positive && (n < Integer.BYTES || rep[start] < 0));
        }

        boolean isLong() {
            int code = current();
            boolean integerRange = code > NEG_INT_START && code < POS_INT_END;
            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int start = pos + 1;
            return integerRange && (positive && (n < Long.BYTES || rep[start] > 0) || !positive && (n < Long.BYTES || rep[start] < 0));
        }

        boolean isBigInteger() {
            int code = current();
            boolean integerRange = code > NEG_INT_START && code < POS_INT_END;
            boolean bigIntegerExplicit = code == NEG_INT_START || code == POS_INT_END;
            return (integerRange && !(isInteger() || isLong())) || bigIntegerExplicit;
        }

        boolean isFloat() {
            int code = current();
            return code == FLOAT_CODE;
        }

        boolean isDouble() {
            int code = current();
            return code == DOUBLE_CODE;
        }

        boolean isUUID() {
            int code = current();
            return code == UUID_CODE;
        }

        byte[] getByteArray() {
            if (!(current() == BYTES_CODE) || isNotNullTerminated()) {
                throw new IllegalArgumentException();
            }
            int end = findNullTerminator();
            if (end == -1) {
                throw new IllegalStateException();
            }
            next(); // skip signature byte
            byte[] bytes = new byte[end - pos];
            System.arraycopy(bytes, 0, bytes, 0, end - pos);
            pos = end + 1;
            return bytes;
        }

        boolean getBoolean() {
            if (current() == FALSE_CODE) {
                next();
                pos++;
                return false;
            } else if (current() == TRUE_CODE) {
                next();
                pos++;
                return true;
            }
            throw new IllegalStateException();
        }

        public Object getString() {
            if (!(current() == STRING_CODE) || isNotNullTerminated()) {
                throw new IllegalArgumentException();
            }
            int end = findNullTerminator();
            if (end == -1) {
                throw new IllegalStateException();
            }
            next(); // skip signature byte
            byte[] bytes = new byte[end - pos];
            System.arraycopy(rep, pos, bytes, 0, end - pos);
            pos = end + 1; // TODO make sure all get(Types) increment pos to end+1
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            CharBuffer cb = CharBuffer.allocate(bytes.length);
            CharsetDecoder decoder = UTF8.newDecoder();
            CoderResult cr = decoder.decode(bb, cb, true);
            if (cr.isError()) {
                try {
                    cr.throwException();
                } catch (CharacterCodingException e) {
                    throw new RuntimeException(e);
                }
            }
            String result = cb.flip().toString();
            return result;
        }

        public Integer getInteger() {
            // decode a int
            int code = current();
            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            next(); // skip signature byte
            int end = pos + n;

            if (end > rep.length) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            if (positive && (n <= Integer.BYTES || rep[pos] > 0)) {
                int res = 0;
                for (int i = pos; i < end; i++, pos++) {
                    res = (res << 8) | (rep[i] & 0xff);
                }
                return res;

            } else if (!positive && (n <= Integer.BYTES || rep[pos] < 0)) {
                int res = ~0;
                for (int i = pos; i < end; i++, pos++) {
                    res = (res << 8) | (rep[i] & 0xff);
                }
                return res + 1;
            }
            throw new IllegalStateException();
        }

        public Long getLong() {
            // decode a long
            int code = current();
            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            next(); // skip signature byte
            int end = pos + n;

            if (end > rep.length) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            if (positive && (n <= Long.BYTES || rep[pos] > 0)) {
                long res = 0L;
                for (int i = pos; i < end; i++, pos++) {
                    res = (res << 8) | (rep[i] & 0xff);
                }
                return res;

            } else if (!positive && (n <= Long.BYTES || rep[pos] < 0)) {
                long res = ~0;
                for (int i = pos; i < end; i++, pos++) {
                    res = (res << 8) | (rep[i] & 0xff);
                }
                return res + 1L;
            }
            throw new IllegalStateException();
        }

        public BigInteger getBigInteger() {
            int code = current();
            next();
            int start = pos;

            // positive BigInteger
            if (code == POS_INT_END) {
                int n = rep[start] & 0xff;
                byte[] intBytes = new byte[n + 1];
                System.arraycopy(rep, start + 1, intBytes, 1, n);
                pos = start + intBytes.length + 1;
                BigInteger res = new BigInteger(intBytes);
                return res;
            }

            // negative BigInteger
            if (code == NEG_INT_START) {
                int n = (rep[start] ^ 0xff) & 0xff;
                byte[] intBytes = new byte[n + 1];
                System.arraycopy(rep, start + 1, intBytes, 1, n);
                pos = start + intBytes.length + 1;
                BigInteger origValue = new BigInteger(intBytes);
                BigInteger offset = BigInteger.ONE.shiftLeft(n * 8).subtract(BigInteger.ONE);
                return origValue.subtract(offset);
            }

            // fallback
            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int end = pos + n;

            if (end > rep.length) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            byte[] longBytes = new byte[9];
            System.arraycopy(rep, start, longBytes, longBytes.length - n, n);
            pos = start + longBytes.length + 1;
            if (!positive)
                for (int i = longBytes.length - n; i < longBytes.length; i++)
                    longBytes[i] = (byte) (longBytes[i] ^ 0xff);

            BigInteger val = new BigInteger(longBytes);
            if (!positive) val = val.negate();

            return val;

            /*
            // Convert to long if in range -- otherwise, leave as BigInteger.
            if (val.compareTo(LONG_MIN_VALUE) >= 0 && val.compareTo(LONG_MAX_VALUE) <= 0) {
                state.add(val.longValue(), end);
            } else {
                // This can occur if the thing can be represented with 8 bytes but requires using
                // the most-significant bit as a normal bit instead of the sign bit.
                state.add(val, end);
            }
            */
        }

        private static float decodeFloatBits(int i) {
            int origBits = (i >= 0) ? (~i) : (i ^ Integer.MIN_VALUE);
            return Float.intBitsToFloat(origBits);
        }

        public Float getFloat() {
            next();
            int start = pos;
            int rawFloatBits = ByteBuffer.wrap(rep, start, Float.BYTES).getInt();
            pos = start + Float.BYTES + 1;
            float res = decodeFloatBits(rawFloatBits);
            return res;
        }

        private static double decodeDoubleBits(long l) {
            long origBits = (l >= 0) ? (~l) : (l ^ Long.MIN_VALUE);
            return Double.longBitsToDouble(origBits);
        }

        public Double getDouble() {
            next();
            int start = pos;
            long rawDoubleBits = ByteBuffer.wrap(rep, start, Double.BYTES).getLong();
            double res = decodeDoubleBits(rawDoubleBits);
            pos = start + Double.BYTES + 1;
            return res;
        }

        public UUID getUUID() {
            next();
            int start = pos;
            ByteBuffer bb = ByteBuffer.wrap(rep, start, UUID_BYTES).order(ByteOrder.BIG_ENDIAN);
            long msb = bb.getLong();
            long lsb = bb.getLong();
            pos += UUID_BYTES + 1;
            return new UUID(msb, lsb);
        }
    }
}
