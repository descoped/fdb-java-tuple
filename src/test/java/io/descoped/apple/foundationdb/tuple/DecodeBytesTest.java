package io.descoped.apple.foundationdb.tuple;

import io.descoped.apple.foundationdb.Range;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertTrue;

public class DecodeBytesTest {

    private static final byte nil = 0x00;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final BigInteger LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int UUID_BYTES = 2 * Long.BYTES;
    private static final IterableComparator iterableComparator = new IterableComparator();

    private static final byte BYTES_CODE = 0x01;
    private static final byte STRING_CODE = 0x02;
    private static final byte NESTED_CODE = 0x05;
    private static final byte INT_ZERO_CODE = 0x14;
    private static final byte POS_INT_END = 0x1d;
    private static final byte NEG_INT_START = 0x0b;
    private static final byte FLOAT_CODE = 0x20;
    private static final byte DOUBLE_CODE = 0x21;
    private static final byte FALSE_CODE = 0x26;
    private static final byte TRUE_CODE = 0x27;
    private static final byte UUID_CODE = 0x30;
    private static final byte VERSIONSTAMP_CODE = 0x33;

    private static final byte[] NULL_ARR = new byte[]{nil};
    private static final byte[] NULL_ESCAPED_ARR = new byte[]{nil, (byte) 0xFF};

    @Test
    public void minimalByteCount() {
        long i = 1L;
        int x = (Long.SIZE + 7 - Long.numberOfLeadingZeros(i >= 0 ? i : -i)) / 8;
        System.out.printf("%s%n", x);
    }

    @Test
    public void intValue() {
        // length: 12
        // \x02a\x00\x02bb\x00\x15\x01\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 21, 1, 2, 99, 0] -> a bb c 
        // byte[] rep = {21, 1};

        // length: 13
        // \x02a\x00\x02bb\x00\x16\x01\x00\x02c\x00
        // [2, 97, 0, 2, 98, 98, 0, 22, 1, 0, 2, 99, 0] -> a bb  c 
        byte[] rep = {22, 1, 0};
        int code = rep[0];
        boolean isIntOrLong = code > NEG_INT_START && code < POS_INT_END;
        printf("int: %s, long: %s", isIntOrLong && rep.length <= 1 + 4, isIntOrLong && rep.length == 1 + 8);

        boolean positive = code >= INT_ZERO_CODE;
        int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
        int start = 1;
        int end = start + n;

        long res = 0L;
        for (int i = start; i < end; i++) {
            String bin1 = Integer.toBinaryString((int) res << 8);
            String bin2 = Integer.toBinaryString((int) (rep[i] & 0xff));
            res = (res << 8) | (rep[i] & 0xff);
        }
        System.out.printf("val: %s,%s => %s%n", n, end, res);
    }

    @Test
    public void integerValue() {
        // Integer.MAX_VALUE
        {
            // Integer.MAX_VALUE
            // length: 15
            // \x02a\x00\x02bb\x00\x18\x7f\xff\xff\xff\x02c\x00
            // [2, 97, 0, 2, 98, 98, 0, 24, 127, -1, -1, -1, 2, 99, 0] -> a bb ���c 
            byte[] rep = {24, 127, -1, -1, -1};
            int code = rep[0];
            boolean isIntOrLong = code > NEG_INT_START && code < POS_INT_END;
            printf("int: %s, long: %s", isIntOrLong && rep.length <= 1 + 4, isIntOrLong && rep.length == 1 + 8);

            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int start = 1;
            int end = start + n;
            int last = rep.length;

            if (last < end) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            assertTrue("Excepted Unsigned integer", positive && (n < Long.BYTES || rep[start] > 0));

            long res = 0L;
            for (int i = start; i < end; i++) {
                String bin1 = Integer.toBinaryString((int) res << 8);
                String bin2 = Integer.toBinaryString((int) (rep[i] & 0xff));
                res = (res << 8) | (rep[i] & 0xff);
            }
            printf("LongMax val: %s,%s => %s <= %s", n, end, res, Integer.MAX_VALUE);
        }

        // Integer.MIN_VALUE
        {
            // Integer.MIN_VALUE
            // length: 15
            // \x02a\x00\x02bb\x00\x10\x7f\xff\xff\xff\x02c\x00
            // [2, 97, 0, 2, 98, 98, 0, 16, 127, -1, -1, -1, 2, 99, 0] -> a bb ���c 
            byte[] rep = {16, 127, -1, -1, -1};
            int code = rep[0];
            boolean isIntOrLong = code > NEG_INT_START && code < POS_INT_END;
            printf("int: %s, long: %s", isIntOrLong && rep.length <= 1 + 4, isIntOrLong && rep.length == 1 + 8);

            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int start = 1;
            int end = start + n;
            int last = rep.length;

            if (last < end) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            assertTrue("Excepted Signed integer", !positive && (n < Long.BYTES || rep[start] < 0));

            long res = ~0L; // flip bits
            printf("%s", Long.toBinaryString(res));
            for (int i = start; i < end; i++) {
                String bin1 = Integer.toBinaryString((int) res << 8);
                String bin2 = Integer.toBinaryString((int) (rep[i] & 0xff));
                res = (res << 8) | (rep[i] & 0xff);
            }
            printf("LongMin val: %s,%s => %s <= %s", n, end, res + 1, Integer.MIN_VALUE); // res + 1 makes unsigned long to signed long?
        }
    }

    @Test
    public void longValue() {
        // MATCH: TRUE
        // LONG.MAX_VALUE
        {
            // length: 19
            // \x02a\x00\x02bb\x00\x1c\x7f\xff\xff\xff\xff\xff\xff\xff\x02c\x00
            // [2, 97, 0, 2, 98, 98, 0, 28, 127, -1, -1, -1, -1, -1, -1, -1, 2, 99, 0] -> a bb �������c 
            byte[] rep = {28, 127, -1, -1, -1, -1, -1, -1, -1};
            int code = rep[0];
            boolean isIntOrLong = code > NEG_INT_START && code < POS_INT_END;
            printf("int: %s, long: %s", isIntOrLong && rep.length <= 1 + 4, isIntOrLong && rep.length == 1 + 8);

            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int start = 1;
            int end = start + n;
            int last = rep.length;

            if (last < end) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            assertTrue("Excepted Unsigned long", positive && (n < Long.BYTES || rep[start] > 0));

            long res = 0L;
            for (int i = start; i < end; i++) {
                String bin1 = Integer.toBinaryString((int) res << 8);
                String bin2 = Integer.toBinaryString((int) (rep[i] & 0xff));
                res = (res << 8) | (rep[i] & 0xff);
            }
            printf("LongMax val: %s,%s => %s <= %s", n, end, res, Long.MAX_VALUE);
        }
        // MATCH: FALSE
        // LONG.MIN_VALUE
        {
            // length: 19
            // \x02a\x00\x02bb\x00\x0c\x7f\xff\xff\xff\xff\xff\xff\xff\x02c\x00
            // [2, 97, 0, 2, 98, 98, 0, 12, 127, -1, -1, -1, -1, -1, -1, -1, 2, 99, 0] -> a bb �������c 
            byte[] rep = {12, 127, -1, -1, -1, -1, -1, -1, -1};
            int code = rep[0];
            boolean isIntOrLong = code > NEG_INT_START && code < POS_INT_END;
            printf("int: %s, long: %s", isIntOrLong && rep.length <= 1 + 4, isIntOrLong && rep.length == 1 + 8);

            boolean positive = code >= INT_ZERO_CODE;
            int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
            int start = 1;
            int end = start + n;
            int last = rep.length;

            if (last < end) {
                throw new IllegalArgumentException("Invalid tuple (possible truncation)");
            }

            assertTrue("Excepted Signed long", !positive && (n < Long.BYTES || rep[start] < 0));

            long res = ~0L; // flip bits
            printf("%s", Long.toBinaryString(res));
            for (int i = start; i < end; i++) {
                String bin1 = Integer.toBinaryString((int) res << 8);
                String bin2 = Integer.toBinaryString((int) (rep[i] & 0xff));
                res = (res << 8) | (rep[i] & 0xff);
            }
            printf("LongMin val: %s,%s => %s <= %s", n, end, res + 1, Long.MIN_VALUE); // res + 1 makes unsigned long to signed long?
        }
    }


    static void printf(String format, Object... args) {
        System.out.printf(format + "%n", args);
    }

    public static List<Integer> bytesToIntList(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return IntStream.generate(buffer::get).limit(buffer.remaining()).boxed().collect(Collectors.toList());
    }

    @Test
    public void pack() {
        Tuple t1 = Tuple.from("a", "bb", Integer.MAX_VALUE, 300, "c");

        byte[] p1 = t1.pack();
        System.out.printf("length: %s%n", p1.length);

        System.out.printf("%s%n", ByteArrayUtil.printable(p1));

        System.out.printf("%s -> %s%n", bytesToIntList(p1), new String(p1));

        Range r1 = t1.range(Tuple.from("d").pack());
        System.out.printf("=> %s%n", bytesToIntList(r1.begin));
        System.out.printf("=> %s%n", bytesToIntList(r1.end));
        System.out.printf("%s => %s%n", ByteArrayUtil.printable(r1.begin), ByteArrayUtil.printable(r1.end));

    }


    @Test
    public void hashCompare() {
        String pathString = "abcdefghij";

        String matchString = "abc";
        byte[] matchBytes = matchString.getBytes(StandardCharsets.UTF_8);
        int matchHashCode; // Arrays.hashCode(matchBytes);

        {
            ByteBuffer buffer = ByteBuffer.allocate(matchBytes.length + 1);
            buffer.put(matchBytes);
            buffer.put((byte) 0xff);
            buffer.flip();
            matchHashCode = Arrays.hashCode(buffer.array());
        }

        Map<byte[], Integer> tokens = new LinkedHashMap<>();
        StringBuilder builder = new StringBuilder();
        int prevHashCode = 0;
        for (int i = 0; i < pathString.length(); i++) {
            builder.append(pathString.charAt(i));
            byte[] pathBytes = builder.toString().getBytes(StandardCharsets.UTF_8);

            int hashCode; // = Arrays.hashCode(pathBytes);
            {
                ByteBuffer buffer = ByteBuffer.allocate(pathBytes.length + 1);
                buffer.put(pathBytes);
                buffer.put((byte) 0xff);
                buffer.flip();
                hashCode = Arrays.hashCode(buffer.array());
            }

            tokens.put(pathBytes, hashCode);
            int cmp = prevHashCode ^ (37 * matchHashCode);
            System.out.printf("%s @ %s -- %s%n", new String(pathBytes), hashCode, cmp);
            if (matchHashCode == hashCode) {
                System.err.println("  ----------: match");
            }
            prevHashCode = hashCode;
        }
    }
}
