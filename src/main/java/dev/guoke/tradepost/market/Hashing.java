package dev.guoke.tradepost.market;

/**
 * 确定性哈希：同一 (世界种子, 村庄格, 物品) 永远得到同一个值。
 * 不存档、不联网、跨会话稳定。使用 splitmix64，避免 java.util.Random 的种子相关性。
 */
public final class Hashing {
    private Hashing() {}

    public static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long of(long... parts) {
        long h = 0xC2B2AE3D27D4EB4FL;
        for (long p : parts) {
            h = mix(h ^ mix(p));
        }
        return h;
    }

    /** [0,1) 的确定性小数 */
    public static double unit(long... parts) {
        return (of(parts) >>> 11) * 0x1.0p-53;
    }

    /** [-1,1) */
    public static double signed(long... parts) {
        return unit(parts) * 2.0 - 1.0;
    }

    /** [0, bound) 的确定性整数 */
    public static int below(int bound, long... parts) {
        if (bound <= 0) return 0;
        return (int) (of(parts) >>> 33) % bound;
    }
}
