package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 全球饱和在合成链上的传播：金锭被卖烂了，金块、金苹果、金装备也得跟着贱。
 *
 * 规则：成品的指数 = min(自己被打烂的程度, 材料按成本加权的平均指数)。
 * 迭代若干轮，让"锭 → 块 → 装备"这种多层合成也传到位。
 * 纯逻辑，离线可测（配方由 MC 层扫出来塞进来）。
 */
public final class IndexGraph {

    private IndexGraph() {}

    public static Map<String, Double> build(List<RecipePricer.Rec> recs,
                                            ToDoubleFunction<String> ownIndex,
                                            ToDoubleFunction<String> baseOf,
                                            int passes) {
        Map<String, Double> out = new HashMap<>();
        if (recs == null || recs.isEmpty()) return out;

        // 一个产物常常有很多条配方（原版配方 + 各模组同名配方 + 拆包配方）。
        // 这里**不挑最便宜的一条**：只要有任何一条配方用的材料崩了，这个产物就该跟着崩，
        // 否则玩家换个配方合成就能绕开全球饱和。所以取所有配方里加权指数最低的那个。
        Map<String, List<RecipePricer.Rec>> byOut = new HashMap<>();
        Map<String, Double> ownCache = new HashMap<>();
        ToDoubleFunction<String> own = id -> ownCache.computeIfAbsent(id, ownIndex::applyAsDouble);

        for (RecipePricer.Rec r : recs) {
            if (r == null || r.output() == null || r.count() <= 0) continue;
            if (unitCost(r, baseOf) <= 0) continue;      // 材料全无价的配方（运行时生成的怪配方）跳过
            byOut.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r);
        }

        int n = Math.max(1, passes);
        for (int pass = 0; pass < n; pass++) {
            for (Map.Entry<String, List<RecipePricer.Rec>> e : byOut.entrySet()) {
                double best = -1;
                for (RecipePricer.Rec r : e.getValue()) {
                    double w = weighted(r, out, own, baseOf);
                    if (w > 0 && (best < 0 || w < best)) best = w;
                }
                if (best < 0) continue;
                double v = Math.min(own.applyAsDouble(e.getKey()), best);
                Double prev = out.get(e.getKey());
                if (prev == null || v < prev) out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static double unitCost(RecipePricer.Rec r, ToDoubleFunction<String> baseOf) {
        double sum = 0;
        for (List<String> slot : r.slots()) {
            double b = cheapest(slot, baseOf);
            if (b <= 0) continue;
            sum += b;
        }
        return sum <= 0 ? 0 : sum / r.count();
    }

    private static double weighted(RecipePricer.Rec r, Map<String, Double> idx,
                                   ToDoubleFunction<String> own, ToDoubleFunction<String> baseOf) {
        double sumW = 0, sum = 0;
        for (List<String> slot : r.slots()) {
            double bestBase = Double.MAX_VALUE;
            String bestId = null;
            for (String id : slot) {
                double b = baseOf.applyAsDouble(id);
                if (b > 0 && b < bestBase) { bestBase = b; bestId = id; }
            }
            if (bestId == null) continue;
            double i = idx.containsKey(bestId) ? idx.get(bestId) : own.applyAsDouble(bestId);
            sumW += bestBase;
            sum += bestBase * i;
        }
        if (sumW <= 0) return -1;
        return sum / sumW;
    }

    private static double cheapest(List<String> slot, ToDoubleFunction<String> baseOf) {
        double best = 0;
        for (String id : slot) {
            double b = baseOf.applyAsDouble(id);
            if (b > 0 && (best <= 0 || b < best)) best = b;
        }
        return best;
    }
}
