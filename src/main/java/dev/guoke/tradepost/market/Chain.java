package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 制作链联动：某个村子缺小麦，它的面包、干草块、曲奇就都该贵。
 *
 * <p>做法是给每一件"能用配方做出来的东西"算一个**本地成本系数**：
 * 用这个村子的产业/供需/繁荣度/村癖/行情去给原材料计价，递归上溯到没有配方的基础货，
 * 再拿它和"全局中立价下的同一份成本"相比。比值就是这件成品在这个村该涨（或该跌）多少。
 *
 * <p>好处：
 * <ul>
 *   <li>手写价可以继续当锚（价格绝对值不变），只是乘上"本村材料贵多少"这个比例；</li>
 *   <li>加成项（工业品折价、菜酒溢价、地板价）在分子分母里同时出现，互相抵消，只留下原材料的差异；</li>
 *   <li>纯函数 + 按村按天记忆化，开一次面板只算面板上那十几件货的祖先链，开销可以忽略。</li>
 * </ul>
 */
public final class Chain {

    private Chain() {
    }

    private static List<RecipePricer.Rec> RECS = List.of();
    private static Map<String, List<RecipePricer.Rec>> BY_OUT = Map.of();

    /** 村键（seed:x:z:day）→ 物品 → 本村成本；村多了整体丢掉重来，防止无限长 */
    private static final Map<String, Map<String, Double>> LOCAL = new HashMap<>();
    private static final Map<String, Double> GLOBAL = new HashMap<>();

    /**
     * 最终系数缓存：村|物品 → factor。
     *
     * <p>farmersdelight:kelp_roll_slice 一条链实测 2.2 秒（日志「收购定价慢」），
     * 而它在每页被算两次（收购价一次、封顶用的要价一次），重建一次再算两次。
     * 同村同天的系数是常数（不含库存），缓存住即可。
     */
    private static final Map<String, Double> FACTOR = new HashMap<>();

    /** 递归深度上限：再深的链就当它是基础货，防止环形配方把栈打穿 */
    private static final int MAX_DEPTH = 6;

    public static synchronized void setRecipes(List<RecipePricer.Rec> recs) {
        RECS = recs == null ? List.of() : recs;
        Map<String, List<RecipePricer.Rec>> m = new HashMap<>();
        for (RecipePricer.Rec r : RECS) {
            if (r == null || r.output() == null) continue;
            m.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r);
        }
        BY_OUT = m;
        LOCAL.clear();
        GLOBAL.clear();
        FACTOR.clear();
    }

    public static synchronized void clear() {
        LOCAL.clear();
        GLOBAL.clear();
        FACTOR.clear();
    }

    public static int size() {
        return BY_OUT.size();
    }

    /**
     * @param villageKey 村与天的唯一键（同一个村同一天结果稳定）
     * @param baseOf     物品的全局基础价（手写价 / 标签价 / 全局配方价），没有价返回 0
     * @param levelFactor 某件货在**这个村**比中立价贵/便宜多少（不含库存，库存波动太频）
     * @param bonus      合成倍率（工业品折价、菜酒溢价），分子分母共用，只起抵消作用
     * @param floorOf    地板价（菜酒有），同上
     * @param item       要问的成品
     * @return 1.0 表示不调整；>1 表示本村材料贵，成品跟着涨
     */
    public static double factor(String villageKey,
                                ToDoubleFunction<String> baseOf,
                                ToDoubleFunction<String> levelFactor,
                                ToDoubleFunction<RecipePricer.Rec> bonus,
                                ToDoubleFunction<RecipePricer.Rec> floorOf,
                                String item) {
        if (item == null || BY_OUT.isEmpty() || !BY_OUT.containsKey(item)) return 1.0;
        String fk = villageKey + "|" + item;
        Double memoHit = FACTOR.get(fk);
        if (memoHit != null) return memoHit;
        double out = 1.0;
        try {
            NODES.get()[0] = 0;
            double g = global(item, baseOf, bonus, floorOf);
            if (g > 0) {
                Map<String, Double> memo = LOCAL.computeIfAbsent(villageKey, k -> new HashMap<>());
                if (LOCAL.size() > 4096) {
                    LOCAL.clear();
                    memo = LOCAL.computeIfAbsent(villageKey, k -> new HashMap<>());
                }
                double l = cost(item, memo, baseOf, levelFactor, bonus, floorOf, 0);
                if (l > 0) {
                    double r = l / g;
                    out = r < 0.6 ? 0.6 : Math.min(r, 2.0);
                }
            }
        } catch (Throwable t) {
            out = 1.0;
        }
        // 「算不出来」也必须缓存：不然每页重建都重跑一遍 2.2 秒
        // （farmersdelight:kelp_roll_slice 就是 l<=0 那条早退路径，以前压根没进缓存）
        if (FACTOR.size() > 20000) FACTOR.clear();
        FACTOR.put(fk, out);
        return out;
    }

    /**
     * 单次 factor() 的递归节点预算：超了就按"不联动"收（返回 0 → 上层 out 保持 1.0）。
     * 一条外文菜谱能展开成上万个节点，不设上限时单件货能吃掉两秒主线程。
     */
    private static final ThreadLocal<int[]> NODES = ThreadLocal.withInitial(() -> new int[]{0});
    private static final int NODE_BUDGET = 60000;

    /** 记一条系数（顺带限量，防止无限长） */
    private static void remember(String key, double value) {
        if (FACTOR.size() > 20000) FACTOR.clear();
        FACTOR.put(key, value);
    }

    /** 中立价下的成本（分母） */
    private static double global(String item, ToDoubleFunction<String> baseOf,
                                 ToDoubleFunction<RecipePricer.Rec> bonus,
                                 ToDoubleFunction<RecipePricer.Rec> floorOf) {
        Double c = GLOBAL.get(item);
        if (c != null) return c;
        double v = cost(item, GLOBAL, baseOf, id -> 1.0, bonus, floorOf, 0);
        return v;
    }

    private static double cost(String item, Map<String, Double> memo, ToDoubleFunction<String> baseOf,
                               ToDoubleFunction<String> levelFactor,
                               ToDoubleFunction<RecipePricer.Rec> bonus,
                               ToDoubleFunction<RecipePricer.Rec> floorOf,
                               int depth) {
        if (item == null) return 0;
        int[] nodes = NODES.get();
        if (++nodes[0] > NODE_BUDGET) return 0;      // 预算用完：这条链按"不联动"收
        Double hit = memo.get(item);
        if (hit != null) return hit;
        double base = baseOf.applyAsDouble(item);
        double lf = depth > MAX_DEPTH ? 1.0 : levelFactor.applyAsDouble(item);
        if (depth > MAX_DEPTH) return base;
        List<RecipePricer.Rec> rs = BY_OUT.get(item);
        if (rs == null || rs.isEmpty()) {
            double v = base > 0 ? base * lf : 0;
            memo.put(item, v);
            return v;
        }
        memo.put(item, 0.0);        // 占位，环形配方回指自己时直接拿 0
        double best = 0;
        for (RecipePricer.Rec r : rs) {
            if (r.slots() == null || r.slots().isEmpty()) continue;
            double sum = 0;
            boolean ok = true;
            for (List<String> slot : r.slots()) {
                double cheapest = Double.MAX_VALUE;
                if (slot != null) {
                    for (String id : slot) {
                        double c = cost(id, memo, baseOf, levelFactor, bonus, floorOf, depth + 1);
                        if (c > 0 && c < cheapest) cheapest = c;
                    }
                }
                if (cheapest == Double.MAX_VALUE) {
                    ok = false;
                    break;
                }
                sum += cheapest;
            }
            if (!ok || sum <= 0) continue;
            double cand = sum / Math.max(1, r.count());
            double b = bonus == null ? 1.0 : bonus.applyAsDouble(r);
            if (b > 0) cand *= b;
            double floor = floorOf == null ? 0 : floorOf.applyAsDouble(r);
            if (floor > cand) cand = floor;
            if (best <= 0 || cand < best) best = cand;
        }
        if (best <= 0) best = base > 0 ? base * lf : 0;
        memo.put(item, best);
        return best;
    }
}
