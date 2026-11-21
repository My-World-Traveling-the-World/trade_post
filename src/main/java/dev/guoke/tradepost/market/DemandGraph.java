package dev.guoke.tradepost.market;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把"物品有哪些用途大类"和"产业要哪些大类"接起来，回答一个问题：
 * <b>这个村收不收这件货</b>（不是手写明的那几条，而是按大类泛收）。
 *
 * <p>标签展开靠 {@link Catalog}.expand，所以模组物品只要进了 #c:ingots 这类通用标签就自动有位置；
 * 标签认不出来的退化为 id 关键词（{@link Roles#byKeyword}）。整张映射懒加载一次，/tradepost reload 清掉重建。
 */
public final class DemandGraph {
    private static volatile DemandGraph INSTANCE;
    private static Catalog builtFor;

    /** 物品 id → 它属于哪些大类 */
    private final Map<String, Set<String>> itemRoles = new ConcurrentHashMap<>();
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    private DemandGraph() {
    }

    public static void clear() {
        INSTANCE = null;
        builtFor = null;
    }

    public static DemandGraph of(Catalog catalog) {
        DemandGraph g = INSTANCE;
        if (g != null && builtFor == catalog) return g;
        synchronized (DemandGraph.class) {
            if (INSTANCE == null || builtFor != catalog) {
                DemandGraph ng = new DemandGraph();
                ng.build(catalog);
                INSTANCE = ng;
                builtFor = catalog;
            }
            return INSTANCE;
        }
    }

    private void build(Catalog catalog) {
        if (catalog == null) return;
        for (Map.Entry<String, String> t : Roles.tagMap().entrySet()) {
            String tag = t.getKey();
            String role = t.getValue();
            if (tag == null || role == null) continue;
            try {
                for (String id : catalog.expand("#" + tag)) {
                    if (id == null) continue;
                    itemRoles.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(role);
                    known.add(id);
                }
            } catch (Exception ignored) {
                // 标签不存在或读不出来：跳过，后面还有关键词兜底
            }
        }
    }

    /** 这件货属于哪些大类（标签优先，认不出就按关键词猜） */
    public Set<String> rolesOf(String itemId) {
        if (itemId == null) return Set.of();
        Set<String> r = itemRoles.get(itemId);
        if (r != null && !r.isEmpty()) return r;
        Set<String> guess = Roles.byKeyword(itemId);
        if (!guess.isEmpty()) itemRoles.put(itemId, guess);
        return guess;
    }

    /** 这个产业会不会按"大类"收这件货 */
    public boolean wants(String industryId, String itemId) {
        List<String> want = Roles.buyers(industryId);
        if (want.isEmpty()) return false;
        for (String role : rolesOf(itemId)) {
            if (want.contains(role)) return true;
        }
        return false;
    }

    /** 统计：有多少产业的大类配置是空的（配错/新产业漏配时能看出来） */
    public int buyersConfigured() {
        int n = 0;
        for (String id : Roles.current().industries.keySet()) {
            if (!Roles.current().buyersOf(id).isEmpty()) n++;
        }
        return n;
    }

    public int knownItems() {
        return known.size();
    }
}
