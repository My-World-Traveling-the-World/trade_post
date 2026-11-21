package dev.guoke.tradepost.market;

/**
 * 村庄等级：靠"在本村买卖"攒经验升级。
 *
 * <p>经验只增不减，按净绿宝石额 × 稀有度权重 × 方向系数记账；
 * 买货（村子出货）1.0，卖货（村子进货）0.6 —— 卖货主要喂世界饱和，不该同时把村子喂成商都。
 *
 * <p>等级只影响"能买到什么"和额度，价格幅度压得很小（±6%），
 * 免得高等级村变成唯一正确解。
 */
public final class VillageLevel {

    private VillageLevel() {}

    /**
     * 累计经验门槛，index = 等级 - 1。
     * 2026-11-13 重算：旧门槛（400/1500/5000/14000）配上"按绿宝石金额线性给经验"，
     * 卖一把 193 的剑就给 1158 点，等于一笔交易连跳两级，两个村子十几分钟就满了。
     * 现在单笔经验按金额开平方（见 {@link #xpFor}）并封顶，门槛同时抬高约 3 倍。
     */
    public static final double[] XP = {0, 600, 3000, 12000, 45000};

    /**
     * 2026-11-13 中间试过一版的"快曲线"（200/700/1800/4000），只活了几分钟就被撤了。
     * 留着它是因为万一有存档在那个版本里落过盘（v3），读档要按它反推等级再折回来。
     */
    public static final double[] XP_V3 = {0, 200, 700, 1800, 4000};
    public static final String[] NAMES = {"露天集市", "固定市集", "商栈", "商会", "商都"};
    public static final int MAX = 5;

    public static int of(double xp) {
        int lv = 1;
        for (int i = 0; i < XP.length; i++) {
            if (xp >= XP[i]) lv = i + 1;
        }
        return Math.min(MAX, lv);
    }

    public static String name(int lv) {
        return NAMES[Math.max(1, Math.min(MAX, lv)) - 1];
    }

    /** 升到下一级还差多少经验；满级返回 -1。 */
    public static double toNext(double xp) {
        int lv = of(xp);
        if (lv >= MAX) return -1;
        return Math.max(0, XP[lv] - xp);
    }

    /** 当前等级内的进度 0..1（原版商人界面那条绿经验条）。 */
    public static float progress(double xp) {
        int lv = of(xp);
        if (lv >= MAX) return 1f;
        double lo = XP[lv - 1];
        double hi = XP[lv];
        return (float) Math.max(0, Math.min(1, (xp - lo) / (hi - lo)));
    }

    // —— 等级带来的额度 ——

    /**
     * 买页行数（村子卖给你多少种货）。每级加得越来越多：+2 → +3 → +4 → +5 行
     * （4 / 6 / 9 / 13 / 18）—— 玩家要的是"等级越高，涨得越快"。
     */
    public static int sellSlots(int lv) {
        return switch (Math.max(1, Math.min(MAX, lv))) {
            case 1 -> 4;
            case 2 -> 6;
            case 3 -> 9;
            case 4 -> 13;
            default -> 18;
        };
    }

    /** 收货位（村子收你多少种货）。 */
    public static int buySlots(int lv) {
        return switch (Math.max(1, Math.min(MAX, lv))) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 3;
            case 4 -> 4;
            default -> 4;
        };
    }

    public static double stockMult(int lv) {
        return 1.0 + 0.15 * (Math.max(1, Math.min(MAX, lv)) - 1);
    }

    public static double restockMult(int lv) {
        return 1.0 + 0.25 * (Math.max(1, Math.min(MAX, lv)) - 1);
    }

    /** 这个等级能买到的最高稀有度档位（1..4）：Lv1-2 常见/稀缺，Lv3 珍稀，Lv4+ 传奇。 */
    public static int topTier(int lv) {
        int best = 1;
        for (int t = 1; t <= 4; t++) if (dev.guoke.tradepost.market.Rarity.gate(t) <= lv) best = t;
        return best;
    }

    /** 银库上限倍率：每高一级多 25% —— 高等级村收得动大生意。 */
    public static double walletMult(int lv) {
        return 1.0 + 0.25 * (Math.max(1, Math.min(MAX, lv)) - 1);
    }

    public static double buyMul(int lv) {
        return 1.0 - 0.015 * (Math.max(1, Math.min(MAX, lv)) - 1);
    }

    public static double sellMul(int lv) {
        return 1.0 + 0.0175 * (Math.max(1, Math.min(MAX, lv)) - 1);
    }

    // —— 记账 ——

    /** 单笔成交的经验上限：再大的生意也不至于一笔跳级。 */
    public static final double MAX_XP_TRADE = 40.0;

    /**
     * 换曲线时的存档折算：先按 {@code from} 反推"等级 + 级内进度"，再按 {@link #XP} 折成新经验。
     * 这样同一份存档不会因为改公式忽然掉级或跳级，屏幕上那条经验条也原地续着走。
     */
    public static double remapFrom(double oldXp, double[] from) {
        int lv = 1;
        for (int i = 0; i < from.length; i++) if (oldXp >= from[i]) lv = i + 1;
        lv = Math.min(MAX, lv);
        if (lv >= MAX) return XP[MAX - 1];
        double lo = from[lv - 1], hi = from[lv];
        double p = hi > lo ? Math.max(0, Math.min(1, (oldXp - lo) / (hi - lo))) : 0;
        return XP[lv - 1] + p * (XP[lv] - XP[lv - 1]);
    }

    /**
     * 一次成交给村子多少经验。selling = 玩家卖货给村子。
     *
     * <p>按绿宝石金额<b>开平方</b>：10 颗给 6.3 点，100 颗给 20 点，1000 颗给 63 点（再被
     * {@link #MAX_XP_TRADE} 压到 40）。线性给经验会让"一次大额成交"直接跳级，而且贵货
     * 的底价本来就高、价格里已经含了稀有度，再乘稀有度权重等于同一件事算两遍。
     *
     * @param base 物品底价，现在不参与计算（保留签名给外部 API）
     */
    public static double xpFor(int emeralds, double base, boolean selling) {
        if (emeralds <= 0) return 0;
        double xp = Math.min(MAX_XP_TRADE, 2.0 * Math.sqrt(emeralds));
        return xp * (selling ? 0.6 : 1.0);
    }

    public static String label(double xp) {
        int lv = of(xp);
        return name(lv) + " Lv." + lv;
    }
}
