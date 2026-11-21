package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 配方定价：给"没有手写价、也没有类别标签"的模组物品（菜、点心、合成器械、装饰块）
 * 按合成成本反推一个价，而不是统统掉到 0.05 的保底价。
 *
 * 规则（等价交换 ProjectE 的简化版，但没有迭代不动点那套）：
 *   - 只在"输入的东西全部有价"时才算成本，任一格没价就跳过这个配方
 *   - 一格里可能是多个可选材料，取最便宜的那个
 *   - 多个配方都能做出同一件货时，取最便宜的那条（防止用贵配方抬价）
 *   - 已经手写定价 / 已有类别标签价的物品不覆盖（那是作者意图，优先）
 *   - 迭代若干轮，让"原木 → 木板 → 木棍 → 栅栏"这种链条一层层传下去
 */
public final class RecipePricer {

    private RecipePricer() {
    }

    /**
     * @param output 产物 item id
     * @param count  产物件数（≥1）
     * @param slots  每一格的可选材料 id；外层是格子，内层是"这一格能放哪些"
     */
    public record Rec(String output, int count, List<List<String>> slots) {
    }

    /** 迭代轮数：4 轮足够穿透"矿石→锭→块→装备"这种四层链 */
    public static final int PASSES = 4;
    /**
     * 制作链深度上限：循环配方（A↔B 能互相做）会让深度每轮 +1，这里封顶，
     * 防止"两件来回合成"被当成十几步的深链、工序劳动无限叠加。
     * 与 Chain.MAX_DEPTH（6）取同一量级。
     */
    private static final int MAX_CHAIN = 6;

    /** 最近一次 compute 算出的制作链深度表（给 /tradepost last 查"多深"用）。 */
    private static volatile java.util.Map<String, Integer> LAST_DEPTH = new java.util.HashMap<>();

    /** 某物品的制作链深度（最长原料链，封顶 MAX_CHAIN）；没算过返回 0。 */
    public static int depthOf(String id) {
        return LAST_DEPTH.getOrDefault(id, 0);
    }

    /**
     * @param fixed 已有权威价的物品 id（手写价 / 类别标签价），这些不会被配方价覆盖
     * @param recs  所有可用配方
     * @param fallbackOf 某件物品"当前最低认知价"，没价时返回保底价
     * @return 产物 id → 单件成本价（只含 fixed 之外的物品）
     */
    public static Map<String, Double> compute(Set<String> fixed, List<Rec> recs, ToDoubleFunction<String> fallbackOf) {
        return compute(fixed, recs, fallbackOf, r -> 1.0);
    }

    /**
     * @param bonus 成本之上的倍率（工业品用折扣 <1，菜和酒用溢价 >1）
     */
    public static Map<String, Double> compute(Set<String> fixed, List<Rec> recs, ToDoubleFunction<String> fallbackOf,
                                              ToDoubleFunction<Rec> bonus) {
        return compute(fixed, recs, fallbackOf, bonus, r -> 0.0);
    }

    /**
     * @param minPrice 地板价（菜/酒有，工业品为 0）：算出来比它还便宜就抬到地板
     */
    public static Map<String, Double> compute(Set<String> fixed, List<Rec> recs, ToDoubleFunction<String> fallbackOf,
                                              ToDoubleFunction<Rec> bonus, ToDoubleFunction<Rec> minPrice) {
        return compute(fixed, recs, fallbackOf, bonus, minPrice, 0.0);
    }

    /**
     * @param stepLabor 工序劳动（加法，不是乘区）：制作链每深一层，成本加这么多。
     *                  12 步的东西比 2 步的贵 10×stepLabor，而且不被折扣/溢价放大。
     *                  深度按"最长原料链"算（原料已经是成品的，深度按 0 计）。
     */
    public static Map<String, Double> compute(Set<String> fixed, List<Rec> recs, ToDoubleFunction<String> fallbackOf,
                                              ToDoubleFunction<Rec> bonus, ToDoubleFunction<Rec> minPrice,
                                              double stepLabor) {
        Map<String, Double> calc = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        if (recs == null || recs.isEmpty()) return calc;

        for (int pass = 0; pass < PASSES; pass++) {
            for (Rec r : recs) {
                if (r == null || r.output() == null || r.slots() == null || r.slots().isEmpty()) continue;
                if (fixed != null && fixed.contains(r.output())) continue;

                double sum = 0.0;
                int maxd = 0;
                boolean ok = true;
                for (List<String> slot : r.slots()) {
                    double best = Double.MAX_VALUE;
                    int bd = 0;
                    if (slot != null) {
                        for (String id : slot) {
                            double v = priceOf(id, fixed, calc, fallbackOf);
                            if (v > 0 && v < best) {
                                best = v;
                                bd = depth.getOrDefault(id, 0);
                            }
                        }
                    }
                    if (best == Double.MAX_VALUE || best <= 0) {
                        ok = false;
                        break;
                    }
                    sum += best;
                    if (bd > maxd) maxd = bd;
                }
                if (!ok || sum <= 0) continue;

                depth.merge(r.output(), Math.min(maxd + 1, MAX_CHAIN), Math::max);   // 最长制作链（封顶）
                int n = Math.max(1, r.count());
                double cand = sum / n;
                if (bonus != null) {
                    double b = bonus.applyAsDouble(r);
                    if (b > 0) cand *= b;
                }
                if (minPrice != null) {
                    double floor = minPrice.applyAsDouble(r);
                    if (floor > cand) cand = floor;
                }
                Double prev = calc.get(r.output());
                if (prev == null || cand < prev) calc.put(r.output(), cand);
            }
        }
        if (stepLabor > 0) {
            for (Map.Entry<String, Double> e : calc.entrySet()) {
                e.setValue(e.getValue() + depth.getOrDefault(e.getKey(), 0) * stepLabor);
            }
        }
        LAST_DEPTH = depth;
        return calc;
    }

    /** 材料价：权威价优先，其次上一轮算出来的配方价，最后保底价 */
    private static double priceOf(String id, Set<String> fixed, Map<String, Double> calc, ToDoubleFunction<String> fallbackOf) {
        if (id == null) return 0;
        if (fixed != null && fixed.contains(id)) {
            double v = fallbackOf.applyAsDouble(id);
            return v > 0 ? v : 0;
        }
        double base = fallbackOf.applyAsDouble(id);
        Double c = calc.get(id);
        if (c != null && c > 0 && (base <= 0 || c < base)) return c;
        return base;
    }

    /** 把一堆物品 id 的配方价合并进已有表（外部手工覆盖用得上） */
    public static Map<String, Double> merge(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> out = new HashMap<>(a == null ? Map.of() : a);
        if (b != null) b.forEach((k, v) -> out.merge(k, v, Math::min));
        return out;
    }

    /** 调试用：按价格从高到低列前 n 条 */
    public static List<String> top(Map<String, Double> prices, int n) {
        List<Map.Entry<String, Double>> es = new ArrayList<>(prices.entrySet());
        es.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, es.size()); i++) {
            out.add(es.get(i).getKey() + "=" + Math.round(es.get(i).getValue() * 100) / 100.0);
        }
        return out;
    }
}
