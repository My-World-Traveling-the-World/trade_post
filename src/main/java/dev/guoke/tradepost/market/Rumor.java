package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.List;

/**
 * 弱预报：只给"方向和大概多远"，不给坐标、不给产业，而且会漏。
 *
 * <p>设计意图：跑商要有运气成分。风声是"听来的"，不是 GPS——
 * 玩家知道西北方向有个村子，但不知道它卖什么、价格多少，得自己跑过去看。
 *
 * <ul>
 *   <li>只有 {@link #CHANCE}% 的村子会进预报（按村庄坐标哈希，确定性、不可刷）</li>
 *   <li>距离分三档，不给具体格数</li>
 *   <li>太近（眼前就有）和太远（>2km）都不算风声</li>
 * </ul>
 */
public final class Rumor {

    /** 有多少百分比的村子会被人提起 */
    public static final int CHANCE = 60;
    /** 超过这个距离就"听不清了" */
    public static final int MAX_DISTANCE = 2000;
    /** 肉眼可见的范围，不算风声 */
    public static final int MIN_DISTANCE = 96;
    private static final int NEAR = 400;
    private static final int FAR = 1000;

    private Rumor() {
    }

    /** 距离档 */
    public static String band(int dist) {
        if (dist <= NEAR) return "很近";
        if (dist <= FAR) return "半天路程";
        return "挺远";
    }

    /** 八方向中文（先说东西再说南北，符合中文习惯：西北、东南） */
    public static String direction(int dx, int dz) {
        boolean n = dz < 0, s = dz > 0, w = dx < 0, e = dx > 0;
        String ew = w ? "西" : (e ? "东" : "");
        String ns = n ? "北" : (s ? "南" : "");
        return ew + ns;
    }

    /**
     * @param cands 候选村庄坐标，每个元素是 {x, z}
     * @param max   最多说几条
     * @return 形如 "西北 · 半天路程"
     */
    public static List<String> rumors(long seed, int px, int pz, List<int[]> cands, int max) {
        Economy e = Economy.current();
        int chance = e.rumorChance;
        int maxDist = e.rumorMaxDistance;
        List<int[]> kept = new ArrayList<>();
        for (int[] c : cands) {
            if (c == null || c.length < 2) continue;
            int dx = c[0] - px;
            int dz = c[1] - pz;
            int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            if (dist < MIN_DISTANCE || dist > maxDist) continue;
            if (Hashing.below(100, seed, c[0], c[1], 11) >= chance) continue;   // 没被提起的村子
            kept.add(new int[]{dist, dx, dz});
        }
        kept.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<String> out = new ArrayList<>();
        for (int[] k : kept) {
            if (out.size() >= max) break;
            String dir = direction(k[1], k[2]);
            out.add((dir.isEmpty() ? "附近" : dir) + " · " + band(k[0]));
        }
        return out;
    }
}
