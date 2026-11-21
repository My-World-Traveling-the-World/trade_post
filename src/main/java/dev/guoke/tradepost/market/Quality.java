package dev.guoke.tradepost.market;

/**
 * 物品品质（目前只有整合包里的酒带这层信息：森罗物语酒馆的 brew_level 组件，1..6 档）。
 *
 * 纯逻辑，不碰任何 Minecraft 类型 —— 组件读写放在 mc/Bridge.java。
 * 价格口径：品质倍率乘在基础价上，收购和出售两边都乘，避免"低品质买高品质卖"套利。
 */
public final class Quality {

    public static final int MIN = 1;
    public static final int MAX = 6;

    /**
     * 1..6 档的价格倍率；0（没有品质组件的普通货）恒为 1.0。
     *
     * 3 档（普通）是基准 1.00，往两边拉大档差：难以下咽 0.45、典藏 4.00。
     * 买卖两边都乘同一个倍率，所以档差拉大不会产生套利；
     * 收购价另有一道"不高于同档买价 ×0.75"的封顶（Bridge.sellUnit）。
     */
    private static final double[] MULT = {1.0, 0.45, 0.70, 1.00, 1.60, 2.60, 4.00};

    /** 中文档名，和酒馆模组的 zh_cn 文案一致（聊天栏/提示里直接用） */
    private static final String[] NAMES = {"", "难以下咽", "劣质", "普通", "优质", "精酿", "典藏"};

    /** 出现概率：普通/优质居多，典藏少见 */
    private static final double[] CUM = {0.10, 0.30, 0.65, 0.85, 0.95, 1.00};

    private Quality() {
    }

    public static int clamp(int level) {
        return Math.max(MIN, Math.min(MAX, level));
    }

    /** 价格倍率；0 = 没有品质组件，恒为 1.0（别跟 1 档"难以下咽"混了） */
    public static double mult(int level) {
        if (level <= 0) return 1.0;
        return MULT[clamp(level)];
    }

    public static String name(int level) {
        if (level <= 0) return "";
        return NAMES[clamp(level)];
    }

    /** 这件货本村这一周卖给你的是几档：按（村, 物品, 周）定，面板与成交价同源，重开也不会变 */
    public static int forVillage(String vkey, String itemId, long day) {
        long week = day / 7;
        double r = Hashing.unit(vkey == null ? 0 : vkey.hashCode(),
                itemId == null ? 0 : itemId.hashCode(), week);
        for (int i = 0; i < CUM.length; i++) {
            if (r < CUM[i]) return i + 1;
        }
        return 3;
    }

    /** 文案后缀：level > 0 时返回「 · 精酿 ×1.70」，否则空串 */
    public static String suffix(int level) {
        if (level <= 0) return "";
        return " · " + name(level) + " ×" + String.format(java.util.Locale.ROOT, "%.2f", mult(level));
    }
}
