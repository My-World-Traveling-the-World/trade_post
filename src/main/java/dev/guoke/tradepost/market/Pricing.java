package dev.guoke.tradepost.market;

/**
 * 把 double 单价（绿宝石/件）压成原版交易界面能表示的整数组合。
 *
 * 原版 MerchantOffer 只有"整数绿宝石 + 整数物品"，但实际价值跨度极大
 * （1 组小麦 >= 1 绿宝石，1 个钻石 >= 20 绿宝石）。所以用"打包"表达精度：
 *   p < 1     -> 1 绿宝石 = C 件      （C 越大，精度越细）
 *   p >= 1    -> C 件 = 1..64 绿宝石
 * 这样 ±8% 的差价在低价货上也能看得出来。
 */
public final class Pricing {
    private Pricing() {}

    public static final int MAX_EMERALDS = 64;
    public static final int MAX_ITEMS = 64;

    /** 一侧的报价：给定物品数量与绿宝石数量 */
    public record Quote(int items, int emeralds) {
        @Override public String toString() { return items + " 件 = " + emeralds + " 绿宝石"; }
    }

    /**
     * 玩家买入：付出 emeralds，得到 items 件。
     *
     * 统一规则：让 items 尽量大（最多 64），绿宝石数 = round(items × 单价)。
     * 这样"实际单价"的相对分辨率约 1/64～1/8，±8% 的村癖和 ±6% 的每日行情
     * 在界面上真的差得出来（不会因为四舍五入被抹平）。
     *
     * 例：单价 0.1875 → 64 煤 = 12 绿宝石；单价 0.625 → 32 铁锭 = 20 绿宝石；
     *     单价 20 → 3 钻石 = 60 绿宝石。
     */
    public static Quote buyQuote(double unitPrice) {
        if (!(unitPrice > 0) || Double.isInfinite(unitPrice)) return new Quote(1, MAX_EMERALDS);
        if (unitPrice >= MAX_EMERALDS) return new Quote(1, MAX_EMERALDS);   // 单价爆表：1 件 = 64 绿宝石封顶
        int items = (int) Math.floor(MAX_EMERALDS / unitPrice);
        items = clamp(items, 1, MAX_ITEMS);
        int emeralds = clamp((int) Math.round(items * unitPrice), 1, MAX_EMERALDS);
        return new Quote(items, emeralds);
    }

    /** 玩家卖出：给出 items 件，拿到 emeralds。收购价默认比卖价低一档。 */
    public static Quote sellQuote(double unitPrice) {
        return buyQuote(unitPrice);
    }

    /**
     * 最小份报价：一次交易就是 lot 件 ↔ price 个绿宝石（见 {@link Lot}）。
     * 面板上的"份数"就是它的倍数：买 N 份 = N×items 件，付 N×emeralds。
     * 比旧的 buyQuote()（常常 64 件起）小得多，便宜货也能 1 个绿宝石起步。
     */
    public static Quote minimal(double unitPrice) {
        int lot = Lot.of(unitPrice);
        return new Quote(lot, Lot.price(unitPrice, lot));
    }

    /** 报价对应的实际单价，用于自检与命令输出 */
    public static double effective(Quote q) {
        return (double) q.emeralds() / (double) q.items();
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
