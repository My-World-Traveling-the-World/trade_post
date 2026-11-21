package dev.guoke.tradepost.market;

/**
 * 村庄库存的纯逻辑：多满、每天恢复多少、缺货和满仓如何影响价格、还能成交几笔。
 * 不含任何 Minecraft 类型，方便脱离游戏跑单元测试。
 *
 * 所有系数都来自 {@link Economy}（config/tradepost/market/economy.json），这里只放公式。
 * 三条闸：库存上限、每天只回一点点、价格随库存浮动。
 */
public final class Stock {

    /** 新村子默认半仓 —— 曲线在 r=0.5 处必须正好是 1.0，否则新生意的价差会被吃掉 */
    public static final double NEUTRAL_RATIO = 0.50;

    /** 兼容旧调用的常量（真正的数值看 Economy） */
    public static final double DAILY_REGEN = 0.02;

    private Stock() {}

    /**
     * 单村某物品的库存上限（件）。按物品基础价反比定：便宜货堆得多，贵货少。
     * 例：铁锭(1.0) → 512 件 = 8 组；煤(0.18) → 2048 件 = 32 组；钻石(20) → 26 件。
     */
    public static int cap(double baseValue) {
        double v = Math.max(0.05, baseValue);
        // 村庄规模越大越能囤货：VillageMarket.stockScale() 由屋子栋数线性给出（0.5~1.6 倍）
        long c = Math.round(512.0 / v * VillageMarket.stockScale()
                * VillageLevel.stockMult(VillageMarket.levelHint()));   // 村等级越高囤得越多（Lv5 ×1.6）
        return (int) Math.max(16, Math.min(4096, c));
    }

    /** 驿站是物流枢纽：库存上限翻倍（铁 1024 件）。写读两侧必须用同一个上限。 */
    public static int capFor(double baseValue, boolean station) {
        int c = cap(baseValue);
        return station ? Math.min(4096, c * 2) : c;
    }

    /**
     * 食物类（走标签价/配方价的模组食材、菜、酒）的库存上限额外压在 foodCap 以内：
     * 一堆一模一样的菜堆 2000 份不合常理，也让"吃"这件事失去稀缺感。
     */
    public static int capFor(double baseValue, boolean station, boolean food) {
        int c = capFor(baseValue, station);
        return food ? Math.min(c, 64) : c;
    }

    /** 库存比例 0..1 */
    public static double ratio(double stock, double cap) {
        if (cap <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, stock / cap));
    }

    /**
     * 玩家来买 → 村里卖给你的价。以半仓为原点：
     * 满仓（没人要）→ 便宜，半仓 → 1.0，空仓（奇货可居）→ 贵。
     */
    public static double buyImpact(double ratio) {
        Economy e = Economy.current();
        double v = 1.0 - e.buySlope * (ratio - NEUTRAL_RATIO);
        return Math.max(e.buyGlutMin, Math.min(e.buyScarcityMax, v));
    }

    /**
     * 玩家来卖 → 村里收货价。同样以半仓为原点：
     * 空仓（缺货抢购）→ 贵，半仓 → 1.0，满仓（堆不下了）→ 便宜。
     */
    public static double sellImpact(double ratio) {
        Economy e = Economy.current();
        double v = 1.0 - e.sellSlope * (ratio - NEUTRAL_RATIO);
        return Math.max(e.sellGlutMin, Math.min(e.sellScarcityMax, v));
    }

    /** 高于半仓时回落的速度是补货速度的几倍：村里会把多出来的货慢慢消耗掉 */
    private static final double SURPLUS_DRAIN = 2.0;

    /**
     * 经过 days 天后的库存（懒计算，只在访问时补）。
     *
     * <p>旧版只会朝上限涨：玩家背包里有的东西被读一次就记成半仓，之后每天 +2%，
     * 几十天后整村全满仓 —— 满了就永久不收，卖页上整类货直接消失
     * （附魔金苹果浮漂卖不了就是这么来的）。现在改成向半仓回归：
     * 低于半仓照旧慢慢补，高于半仓按两倍速度回落。
     */
    public static double advance(double stock, double cap, long days) {
        if (days <= 0) return Math.max(0, Math.min(cap, stock));
        double regen = Economy.current().stockRegen;
        double target = cap * NEUTRAL_RATIO;
        double step = days * cap * regen;
        double next = stock < target
                ? Math.min(target, stock + step)
                : Math.max(target, stock - step * SURPLUS_DRAIN);
        return Math.max(0, Math.min(cap, next));
    }

    /** 这一项还能成交几次：库存够几笔就几笔，不超过基础上限。 */
    public static int maxUsesFor(double stock, int itemsPerTrade, int baseUses) {
        if (itemsPerTrade <= 0) return baseUses;
        int byStock = (int) Math.floor(stock / itemsPerTrade);
        return Math.max(0, Math.min(baseUses, byStock));
    }
}
