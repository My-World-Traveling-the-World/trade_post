package dev.guoke.tradepost.market;

/**
 * 「最小份」：把浮点单价（绿宝石/件）压成一次能成交的整数份。
 *
 * 绿宝石是整数，所以每笔交易只能是「N 件 ↔ M 绿宝石」。挑 N 的时候要让
 * N × 单价 尽量接近整数 M，否则玩家会看到 0.24/件的木板卖 3 件拿 0 个绿宝石。
 *
 * 规则：在 1..64 件里找"最小的一档"，要求 件数 × 单价 与某个整数绿宝石的
 * 相对误差 ≤ 5%；都不达标就取误差最小的那一档。
 *   木板 0.24  → 4 件 = 1 绿宝石（+4%）
 *   陶瓦 0.132 → 15 件 = 2 绿宝石（+1%）
 *   煤   0.36  → 14 件 = 5 绿宝石（-0.8%）
 *   铁锭 2.44  → 9 件 = 22 绿宝石（+0.2%）
 *   钻石 24    → 1 件 = 24 绿宝石
 *
 * 这样每笔交易都是整数绿宝石，长期偏差被压到 5% 以内，不需要靠零头账兜。
 */
public final class Lot {
    private Lot() {}

    public static final int MAX_LOT = 64;
    public static final double TOL = 0.05;

    /** 一次最少成交多少件（一份） */
    public static int of(double unitPrice) {
        if (!(unitPrice > 0) || Double.isInfinite(unitPrice)) return 1;
        int best = MAX_LOT;
        double bestErr = Double.MAX_VALUE;
        for (int k = 1; k <= MAX_LOT; k++) {
            double v = unitPrice * k;
            double r = Math.rint(v);
            if (r < 1) continue;                 // 一份至少要换 1 个绿宝石
            double err = Math.abs(v - r) / v;
            if (err <= TOL) return k;            // 第一个达标的就是最小份
            if (err < bestErr) {
                bestErr = err;
                best = k;
            }
        }
        return best;
    }

    /** 一份（lot 件）值多少绿宝石 */
    public static int price(double unitPrice, int lot) {
        if (!(unitPrice > 0) || Double.isInfinite(unitPrice)) return 1;
        int p = (int) Math.rint(unitPrice * Math.max(1, lot));
        return Math.max(1, p);
    }

    /** 把件数往下取整到整数份 */
    public static int align(int count, int lot) {
        if (count <= 0) return 0;
        int l = Math.max(1, lot);
        return (count / l) * l;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
