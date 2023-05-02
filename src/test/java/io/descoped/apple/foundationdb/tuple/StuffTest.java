package io.descoped.apple.foundationdb.tuple;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class StuffTest {

    @Test
    public void minimalByteCount() {
        long i = 1L;
        int x = (Long.SIZE + 7 - Long.numberOfLeadingZeros(i >= 0 ? i : -i)) / 8;
        System.out.printf("%s%n", x);
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
