package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.List;

/**
 * 军需铺的成品装备：一件一档，材质/部位/附魔全由 (村庄, 周, 行号) 决定，同一天重开面板不变。
 *
 * 规则（用户 2026-11 定稿）：
 *   行数 = 村庄等级（L1 一行 … L5 五行），行间互不重复；
 *   材质随等级上移：L1 木石皮 / L2 石铁皮 / L3 铁金钻 / L4 钻 / L5 下界合金；
 *   附魔强度：L1 无 / L2 I / L3 I–II / L4 II / L5 II–III；
 *   价格 = 材料价 × 部位系数 × 附魔加价（时运/精准/经验修补再 ×1.5）× 等级买价系数；
 *   每天每行有额度（L1–2 一件、L3–4 两件、L5 三件）。
 *
 * 纯逻辑，不碰 Minecraft 类型；真正造物品在 mc/Bridge#gear。
 */
public final class GearShop {

    /** 一行货。code 是面板回传的购买码：gear:基础物品|附魔|等级 */
    public record Gear(String base, String ench, int enchLevel, int price, int quota) {
        public String code() {
            return "gear:" + base + "|" + ench + "|" + enchLevel;
        }
    }

    private static final String[] KINDS = {
            "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots"};
    private static final double[] KIND_COST = {2.2, 2.6, 2.6, 1.4, 1.4, 3.2, 5.0, 4.4, 2.6};
    private static final boolean[] KIND_ARMOR = {
            false, false, false, false, false, true, true, true, true};

    /** 每级能出现的材质（下标见 MAT_COST） */
    private static final int[][] MATS = {
            {0, 1, 2},          // L1 木/石/皮
            {1, 4, 2},          // L2 石/铁/皮
            {4, 5, 6},          // L3 铁/金/钻
            {6},                // L4 钻
            {7}                 // L5 下界合金
    };
    private static final String[] MAT_ID = {
            "wooden", "stone", "leather", "chainmail", "iron", "golden", "diamond", "netherite"};
    private static final double[] MAT_COST = {1, 2, 3, 4, 7, 10, 26, 64};

    /** 附魔池：按部位给，池子里的都能附（经验修补/时运/精准采集是"宝贝附魔"，加价另算） */
    /**
     * 附魔池：只放"看得见"的附魔，耐久这种凑数的留在最后兜底。
     * 一把货一个附魔，池子小反而每件都有辨识度。
     */
    private static final String[][] POOL = {
            // sword
            {"sharpness", "smite", "bane_of_arthropods", "looting", "fire_aspect", "knockback"},
            // pickaxe
            {"efficiency", "fortune", "silk_touch"},
            // axe
            {"sharpness", "efficiency"},
            // shovel
            {"efficiency"},
            // hoe
            {"efficiency", "unbreaking"},
            // helmet
            {"protection", "respiration", "aqua_affinity", "thorns"},
            // chestplate
            {"protection", "thorns"},
            // leggings
            {"protection", "swift_sneak", "thorns"},
            // boots
            {"protection", "feather_falling", "depth_strider", "thorns"}
    };

    /** 附魔自身最大等级（面板只显示，别给个 III 的精准采集） */
    private static final String[] MAX1 = {"silk_touch", "mending", "aqua_affinity"};
    private static final String[] TREASURE = {"looting", "fortune", "silk_touch", "mending"};

    private GearShop() {
    }

    /** 本村今天该摆哪几件（行数 = 等级，最多 5 行） */
    public static List<Gear> list(String vkey, int level, long day) {
        List<Gear> out = new ArrayList<>();
        if (level <= 0) return out;
        int lv = Math.min(level, 5);
        int rows = Math.min(5, lv);
        long vh = vkey == null ? 0 : vkey.hashCode();
        long week = day / 7;

        // 本等级所有合法「材质 × 部位」组合，按 (村, 周, 等级) 确定性洗牌后取前几行，
        // 这样同一格里不会出现两把同样的斧头，每周换一批也确实是换了一批。
        List<int[]> pairs = new ArrayList<>();
        for (int mi : MATS[lv - 1]) {
            for (int ki = 0; ki < KINDS.length; ki++) {
                if (!fits(mi, ki)) continue;
                pairs.add(new int[]{mi, ki});
            }
        }
        for (int i = pairs.size() - 1; i > 0; i--) {
            int j = Hashing.below(i + 1, vh, week, lv, i, 101);
            int[] t = pairs.get(i);
            pairs.set(i, pairs.get(j));
            pairs.set(j, t);
        }

        int quota = level <= 2 ? 1 : (level <= 4 ? 2 : 3);
        for (int r = 0; r < rows && r < pairs.size(); r++) {
            int mi = pairs.get(r)[0];
            int ki = pairs.get(r)[1];
            String base = MAT_ID[mi] + "_" + KINDS[ki];
            int el = enchPower(lv, Hashing.of(vh, week, lv, r, 103));
            String ench = "";
            if (el > 0) {
                String[] pool = POOL[ki];
                String pick = pool[Hashing.below(pool.length, vh, week, lv, r, 107)];
                ench = "minecraft:" + pick;
                el = Math.min(el, maxLevel(pick));
            }
            out.add(new Gear(base, ench, el, priceOf(mi, ki, ench, el, level), quota));
        }
        return out;
    }

    /** 木/石只出工具，皮/锁子甲只出防具，铁以上九种都行 */
    private static boolean fits(int mi, int ki) {
        boolean tool = ki < 5;
        if (mi <= 1) return tool;
        if (mi == 2 || mi == 3) return !tool;
        return true;
    }

    /** 面板回传的 code → 今天这件货；对不上说明是伪造/过期的，直接 null */
    public static Gear find(String vkey, int level, long day, String code) {
        for (Gear g : list(vkey, level, day)) {
            if (g.code().equals(code)) return g;
        }
        return null;
    }

    private static int enchPower(int level, long h) {
        if (level <= 1) return 0;
        if (level == 2) return 1;
        if (level == 3) return 1 + (int) (h & 1L);
        if (level == 4) return 2;
        return 2 + (int) (h & 1L);
    }

    private static int maxLevel(String ench) {
        for (String m : MAX1) {
            if (m.equals(ench)) return 1;
        }
        return 3;
    }

    private static boolean isTreasure(String ench) {
        for (String t : TREASURE) {
            if (ench.endsWith(t)) return true;
        }
        return false;
    }

    private static double markup(String ench, int el) {
        double m;
        if (el <= 0) m = 1.0;
        else if (el == 1) m = 1.15;
        else if (el == 2) m = 1.40;
        else m = 2.00;
        if (isTreasure(ench)) m *= 1.5;
        return m;
    }

    private static int priceOf(int mi, int ki, String ench, int el, int level) {
        double p = MAT_COST[mi] * KIND_COST[ki] * markup(ench, el) * VillageLevel.buyMul(level);
        return Math.max(1, (int) Math.round(p));
    }
}
