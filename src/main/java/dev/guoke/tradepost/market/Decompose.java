package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 把一件成品"打散成原始材料"再记账。
 *
 * 为什么要打散：全球饱和按"件数"计数。如果按成品本身计，卖 1 个金块（=9 锭）只算 1 件，
 * 或者今天卖金块、明天卖金苹果、后天卖金胡萝卜（每样各有一套独立计数器），
 * 就永远打不崩。打散到原料之后，所有黄金制品最后都落在同一批原料上，换皮无效。
 *
 * 规则：
 *   1. 顺着配方往下拆，遇到"比它自己便宜得多"的原料就停；
 *   2. 一个产物有多条配方时取**材料总价最低**的那条（玩家实际会用便宜的那条去合）；
 *   3. 拆的过程带路径保护，A→B→A 这种互逆配方不会死循环，回到自己身上就按原料算；
 *   4. 如果拆完发现原料全是"廉价到不吃全球饱和"的东西（金锭 → 金粒这种），就不拆，按它自己算，
 *      否则金锭永远打不崩；
 *   5. 深度上限兜底。
 *
 * 纯逻辑，可在离线环境跑。
 */
public final class Decompose {

    /** 打散后的一项：原材料 id + 每件成品需要多少 */
    public record Part(String item, double count) {
    }

    private final Map<String, List<Part>> memo = new HashMap<>();
    private final Map<String, List<RecipePricer.Rec>> byOut = new HashMap<>();
    private final ToDoubleFunction<String> baseOf;
    private final double minBase;
    private final int maxDepth;

    private Decompose(ToDoubleFunction<String> baseOf, double minBase, int maxDepth) {
        this.baseOf = baseOf;
        this.minBase = Math.max(0, minBase);
        this.maxDepth = Math.max(1, maxDepth);
    }

    public static Decompose build(List<RecipePricer.Rec> recs,
                                  ToDoubleFunction<String> baseOf,
                                  double minBase,
                                  int maxDepth) {
        Decompose d = new Decompose(baseOf, minBase, maxDepth);
        if (recs != null) {
            for (RecipePricer.Rec r : recs) {
                if (r == null || r.output() == null || r.count() <= 0) continue;
                d.byOut.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r);
            }
        }
        return d;
    }

    /** 某件货打散后的原料清单；返回空表表示"它自己就是原料" */
    public List<Part> of(String item) {
        if (item == null) return List.of();
        List<Part> p = resolve(item, maxDepth, new HashSet<>());
        if (p.size() == 1 && p.get(0).item().equals(item)) return List.of();
        return p;
    }

    private List<Part> resolve(String item, int depth, Set<String> path) {
        if (path.isEmpty()) {
            List<Part> hit = memo.get(item);
            if (hit != null) return hit;
        }
        List<Part> leaf = List.of(new Part(item, 1.0));
        List<RecipePricer.Rec> cands = byOut.get(item);
        if (depth <= 0 || cands == null || baseOf.applyAsDouble(item) <= 0) {
            if (path.isEmpty()) memo.put(item, leaf);
            return leaf;
        }

        // 选配方：不是"最便宜的那条"，而是"最接近这件东西本身价值的那条"。
        // 理由：整合包里有大量畸形配方（铜板换钻石、硬币换绿宝石、蛋糕切八块再拼回去），
        // 取最便宜会一路掉进垃圾材料里，等于没拆。所以优先取材料总价不超过自身价值 1.5 倍里最贵的，
        // 实在没有（说明所有配方都比它贵很多）再退回最便宜的那条。
        double own = baseOf.applyAsDouble(item);
        double ceiling = own * 1.5;
        RecipePricer.Rec best = null;
        double bestTotal = -1;
        RecipePricer.Rec fallback = null;
        double fallbackTotal = Double.MAX_VALUE;
        for (RecipePricer.Rec r : cands) {
            double total = 0;
            boolean ok = true;
            for (List<String> slot : r.slots()) {
                double cheapest = Double.MAX_VALUE;
                for (String id : slot) {
                    double b = baseOf.applyAsDouble(id);
                    if (b > 0 && b < cheapest) cheapest = b;
                }
                if (cheapest == Double.MAX_VALUE) { ok = false; break; }   // 有格子完全没价 → 这条配方算不出来
                total += cheapest;
            }
            if (!ok || total <= 0) continue;
            if (total <= ceiling && total > bestTotal) { bestTotal = total; best = r; }
            if (total < fallbackTotal) { fallbackTotal = total; fallback = r; }
        }
        if (best == null) best = fallback;
        if (best == null) {
            if (path.isEmpty()) memo.put(item, leaf);
            return leaf;
        }

        Map<String, Double> acc = new HashMap<>();
        double share = 1.0 / best.count();
        path.add(item);
        for (List<String> slot : best.slots()) {
            String m = null;
            double cheapest = Double.MAX_VALUE;
            for (String id : slot) {
                double b = baseOf.applyAsDouble(id);
                if (b > 0 && b < cheapest) { cheapest = b; m = id; }
            }
            if (m == null) continue;
            List<Part> sub = (!path.contains(m) && depth > 1)
                    ? resolve(m, depth - 1, path)
                    : List.of(new Part(m, 1.0));
            for (Part p : sub) acc.merge(p.item(), p.count() * share, Double::sum);
        }
        path.remove(item);

        // 拆出来全是廉价原料 → 等于没记，按自己算（否则金锭永远打不崩）
        boolean significant = false;
        for (String k : acc.keySet()) {
            if (baseOf.applyAsDouble(k) >= minBase) { significant = true; break; }
        }
        List<Part> out;
        if (acc.isEmpty() || !significant) {
            out = leaf;
        } else {
            out = new ArrayList<>(acc.size());
            for (Map.Entry<String, Double> e : acc.entrySet()) out.add(new Part(e.getKey(), e.getValue()));
            if (out.size() == 1 && out.get(0).item().equals(item)) out = leaf;   // A→B→A 抵消掉
        }
        if (path.isEmpty()) memo.put(item, out);
        return out;
    }
}
