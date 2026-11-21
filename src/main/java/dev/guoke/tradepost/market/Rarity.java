package dev.guoke.tradepost.market;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 稀有度：独立于"分类"的第二根轴。
 *
 * 分类回答"这货归哪个产业"，稀有度回答"这东西多久能见一次、该不该限量"。
 * 两者正交：铁矿是采矿业的常见货，下界合金是采矿业的传奇货；酒是酒肆的常见货，
 * 附魔金苹果浮漂是渔猎的传奇货。
 *
 * 档位是推导出来的，不是手写表：
 *   价格轴  基础价 <2 / <8 / <40 / >=40 → R1..R4（价值本身就是稀缺度的市场表达）
 *   来源轴  rarity.json 里的世界生成密度（矿脉 count×size）与战利品来源
 *   取两者较高的一档 —— 便宜但只在结构箱里的东西也上不去（来源轴抬它），
 *   贵但满地都是的东西也不会被压下来（价格轴保它）。
 *
 * 效果：倍率进价、R3+ 不进世界饱和、每日成交上限、村庄等级门槛。
 */
public final class Rarity {

    public static final int MIN = 1;
    public static final int MAX = 4;

    public static final int COMMON = 1;
    public static final int UNCOMMON = 2;
    public static final int RARE = 3;
    public static final int LEGENDARY = 4;

    public static final String[] NAMES = {"", "常见", "稀缺", "珍稀", "传奇"};

    /** 价格倍率：稀有度不是"贵一点"，而是"贵一档" */
    public static final double[] MULT = {1.0, 1.0, 1.6, 2.4, 3.5};

    /** 能买它需要的村庄等级（0 = 不设门槛，档位 1 起算） */
    public static final int[] LEVEL = {0, 1, 1, 3, 4};

    /** 每村每天最多成交几笔（R1/R2 不额外限制，交给库存曲线） */
    public static final int[] USES = {0, 64, 16, 2, 1};

    /** rarity.json 的一条：t=档位，d=密度分（count×size），s=来源 */
    public record Entry(int t, int d, String s) {
    }

    private static final class File {
        int version;
        Map<String, Entry> items;
    }

    private static final String PATH = "/data/tradepost/market/rarity.json";

    private final Map<String, Entry> items;

    private Rarity(Map<String, Entry> items) {
        this.items = items == null ? Map.of() : items;
    }

    public static Rarity empty() {
        return new Rarity(Map.of());
    }

    /** dir 为 null 或没有 rarity.json 时用 jar 内置的；表坏了当空表（只走价格轴）。 */
    public static Rarity load(Path dir) {
        try (Reader r = open(dir)) {
            if (r == null) return empty();
            File f = new Gson().fromJson(r, File.class);
            if (f == null || f.items == null) return empty();
            return new Rarity(new LinkedHashMap<>(f.items));
        } catch (Exception e) {
            return empty();
        }
    }

    private static Reader open(Path dir) throws IOException {
        if (dir != null) {
            Path f = dir.resolve("rarity.json");
            if (Files.isRegularFile(f)) return Files.newBufferedReader(f, StandardCharsets.UTF_8);
        }
        InputStream in = Rarity.class.getResourceAsStream(PATH);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    public int size() {
        return items.size();
    }

    public Map<String, Entry> entries() {
        return items;
    }

    public Entry entry(String id) {
        return items.get(id);
    }

    public int sourceTier(String id) {
        Entry e = items.get(id);
        return e == null ? 0 : e.t();
    }

    public String source(String id) {
        Entry e = items.get(id);
        return e == null ? "" : e.s();
    }

    /** 只按价格轴定档：基础价（未乘稀有度）→ 档位 */
    public static int byValue(double rawBase) {
        if (!(rawBase > 0) || Double.isInfinite(rawBase)) return MIN;
        if (rawBase < 2) return COMMON;
        if (rawBase < 8) return UNCOMMON;
        if (rawBase < 40) return RARE;
        return LEGENDARY;
    }

    /** 价格轴与来源轴取高：贵的东西不会因为"满地都是"被压下去，结构箱独占的东西也不会被卖成白菜 */
    public static int of(String id, double rawBase, Rarity table) {
        int byVal = byValue(rawBase);
        int bySrc = table == null ? 0 : table.sourceTier(id);
        return Math.max(MIN, Math.min(MAX, Math.max(byVal, bySrc)));
    }

    public static double mult(int tier) {
        return MULT[Math.max(MIN, Math.min(MAX, tier))];
    }

    public static int gate(int tier) {
        return LEVEL[Math.max(MIN, Math.min(MAX, tier))];
    }

    public static int uses(int tier) {
        return USES[Math.max(MIN, Math.min(MAX, tier))];
    }

    public static String name(int tier) {
        return NAMES[Math.max(MIN, Math.min(MAX, tier))];
    }

    /** R3/R4 不进世界饱和：传奇货不该因为"全世界卖了 4000 个"变成 0.2 倍 */
    public static boolean saturationFree(int tier) {
        return tier >= RARE;
    }
}
