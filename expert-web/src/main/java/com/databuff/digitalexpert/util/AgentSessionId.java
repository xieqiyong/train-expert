package com.databuff.digitalexpert.util;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentSessionId {

    /**
     * 位分配（可按需调）
     * - timestamp: 毫秒时间戳差值（41 bits 可用约 69 年）
     * - nodeId:     节点标识（10 bits => 0~1023）
     * - sequence:   同毫秒序列（12 bits => 0~4095）
     * <p>
     * 总计 63 bits 放入 long（最高位保持 0，避免负数）
     */
    private static final long EPOCH = 1704067200000L;
    private static final int NODE_BITS = 10;
    private static final int SEQ_BITS = 12;

    private static final int MAX_NODE = (1 << NODE_BITS) - 1;
    private static final int MAX_SEQ = (1 << SEQ_BITS) - 1;

    private static final long NODE_SHIFT = SEQ_BITS;
    private static final long TIME_SHIFT = NODE_BITS + SEQ_BITS;

    private static volatile long lastMs = -1L;
    private static final AtomicInteger seq = new AtomicInteger(0);

    private static final int NODE_ID = initNodeId();

    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private AgentSessionId() {
    }

    /**
     * 生成 sessionId（Base62 字符串）
     */
    public static String next() {
        long now = System.currentTimeMillis();
        long ms;

        int s;
        synchronized (AgentSessionId.class) {
            if (now < lastMs) {
                // 时钟回拨：简单做法是“钉住 lastMs”
                now = lastMs;
            }

            if (now == lastMs) {
                s = (seq.incrementAndGet()) & MAX_SEQ;
                if (s == 0) {
                    // 同毫秒序列溢出：等到下一毫秒
                    now = waitNextMillis(now);
                }
            } else {
                seq.set(0);
                s = 0;
            }
            lastMs = now;
            ms = now;
        }

        long timePart = (ms - EPOCH);
        if (timePart < 0) {
            // epoch 设置错误或系统时间异常
            timePart = ms; // 兜底：直接用绝对毫秒
        }

        long id = (timePart << TIME_SHIFT)
                | ((long) (NODE_ID & MAX_NODE) << NODE_SHIFT)
                | (long) (s & MAX_SEQ);

        // 可加一个短随机扰动（不影响唯一性，只增强不可猜测性）
        // 例如拼接 2 字符随机尾巴
        return toBase62(id) + randSuffix2();
    }

    /**
     * 可选：解析出大致生成时间（去掉随机后缀再解析）
     */
    public static long extractMillis(String sessionId) {
        if (sessionId == null || sessionId.length() < 3) throw new IllegalArgumentException("bad sessionId");
        // 去掉末尾 2 位随机后缀
        String core = sessionId.substring(0, sessionId.length() - 2);
        long id = fromBase62(core);
        long timePart = id >>> TIME_SHIFT;
        long ms = timePart + EPOCH;
        return ms;
    }

    // ----------------- internal helpers -----------------

    private static long waitNextMillis(long current) {
        long now;
        do {
            now = System.currentTimeMillis();
        } while (now <= current);
        seq.set(0);
        return now;
    }

    private static String toBase62(long value) {
        // value >= 0
        if (value == 0) return "0";
        char[] buf = new char[11]; // 62^11 > 2^63
        int pos = buf.length;
        long v = value;
        while (v > 0) {
            int r = (int) (v % 62);
            buf[--pos] = BASE62[r];
            v /= 62;
        }
        return new String(buf, pos, buf.length - pos);
    }

    private static long fromBase62(String s) {
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = base62Value(c);
            v = v * 62 + d;
        }
        return v;
    }

    private static int base62Value(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return 10 + (c - 'A');
        if (c >= 'a' && c <= 'z') return 36 + (c - 'a');
        throw new IllegalArgumentException("bad base62 char: " + c);
    }

    private static String randSuffix2() {
        int x = ThreadLocalRandom.current().nextInt(62 * 62);
        char a = BASE62[x / 62];
        char b = BASE62[x % 62];
        return new String(new char[]{a, b});
    }

    /**
     * 自动生成 nodeId 的兜底策略：hash(host + pid) & MAX_NODE
     */
    private static int initNodeId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName(); // "pid@hostname"
            String raw = host + "|" + pid;
            int h = murmur3_32(raw.getBytes(StandardCharsets.UTF_8));
            return (h & 0x7fffffff) & MAX_NODE;
        } catch (Exception e) {
            // 最终兜底：随机一个（重启可能变化，不适合多实例强一致）
            return ThreadLocalRandom.current().nextInt(MAX_NODE + 1);
        }
    }

    // 简单 Murmur3 32-bit（无三方依赖）
    private static int murmur3_32(byte[] data) {
        int length = data.length;
        int seed = 0x9747b28c;
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;

        int h1 = seed;
        int roundedEnd = (length & 0xfffffffc);  // round down to 4 byte block

        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | ((data[i + 3] & 0xff) << 24);

            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tail = length & 0x03;
        if (tail == 3) k1 ^= (data[roundedEnd + 2] & 0xff) << 16;
        if (tail >= 2) k1 ^= (data[roundedEnd + 1] & 0xff) << 8;
        if (tail >= 1) {
            k1 ^= (data[roundedEnd] & 0xff);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;
            h1 ^= k1;
        }

        h1 ^= length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);

        return h1;
    }

    public static String generate() {
        return "session_" + System.currentTimeMillis() + "_" + AgentSessionId.next();
    }
}
