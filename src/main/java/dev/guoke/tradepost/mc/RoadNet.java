package dev.guoke.tradepost.mc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.class_2338;
import net.minecraft.class_3218;
import net.minecraft.class_3449;

/**
 * 阡陌交通（RoadWeaver）路网桥。
 *
 * 跑商的价格该知道"这村通不通车"：路通得越多，货运进来越顺、本地越不缺货，
 * 物价整体下移（Economy.roadPriceDrop，买卖两侧都乘）、货架多摆一件。
 * 于是枢纽是进货点，没路的偏远村是出货点。
 *
 * 全部走反射：没装阡陌交通、或者它的接口变了，都不影响跑商本体，只是加成按 0 算。
 * 只在服务端跑。反射拿到的都是 public 方法（WorldDataProvider / StructureConnection 是 record）。
 */
public final class RoadNet {

    /** 路网缓存多久重读一次（修路是异步的，别指望立刻看到） */
    private static final long TTL_MS = 300_000L;

    private static class_3218 world;
    private static long builtAt = 0L;
    /** 村庄 key → 已通车的商路条数 */
    private static final Map<String, Integer> DEG = new HashMap<>();
    private static boolean missing = false;

    private RoadNet() {
    }

    /** /tradepost reload 时清掉，下次进村重新读 */
    public static void clear() {
        world = null;
        builtAt = 0L;
        DEG.clear();
    }

    /** 这个村子有几条已通车的商路（0 = 没装阡陌交通、或者这村还没通路） */
    public static int degree(class_3218 w, class_3449 vs) {
        if (w == null || vs == null) return 0;
        if (w != world || System.currentTimeMillis() - builtAt > TTL_MS) build(w);
        Integer n = DEG.get(Bridge.townKey(w, vs));
        return n == null ? 0 : n;
    }

    private static void build(class_3218 w) {
        world = w;
        builtAt = System.currentTimeMillis();
        DEG.clear();
        if (missing) return;
        try {
            Class<?> prov = load("net.shiroha233.roadweaver.persistence.WorldDataProvider");
            Object inst = prov.getMethod("getInstance").invoke(null);
            if (inst == null) return;
            Object raw = prov.getMethod("getStructureConnections", class_3218.class).invoke(inst, w);
            if (!(raw instanceof List<?> conns)) return;
            int roads = 0;
            int seen = 0;
            for (Object c : conns) {
                if (c == null) continue;
                if (++seen > 4096) break;   // 路网可能有几千条，别在开市那个 tick 里全跑
                Object st;
                try {
                    st = c.getClass().getMethod("status").invoke(c);
                } catch (Throwable skip) {
                    continue;
                }
                // 只有 COMPLETED 才算真通车；PLANNED/GENERATING/FAILED 都不该给加成
                if (st == null || !"COMPLETED".equals(String.valueOf(st))) continue;
                class_2338 a = (class_2338) c.getClass().getMethod("from").invoke(c);
                class_2338 b = (class_2338) c.getClass().getMethod("to").invoke(c);
                bump(w, a);
                bump(w, b);
                roads++;
            }
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 商路网：{} 条已通车", roads);
        } catch (ClassNotFoundException e) {
            missing = true;
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 没装阡陌交通（roadweaver），商路加成关闭");
        } catch (Throwable t) {
            missing = true;
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 读阡陌交通路网失败，商路加成关闭", t);
        }
    }

    /** 阡陌交通在别的类加载器里也不怕：自己的 → 线程上下文 → 系统，挨个试 */
    private static Class<?> load(String name) throws ClassNotFoundException {
        for (ClassLoader cl : new ClassLoader[]{RoadNet.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader(),
                net.fabricmc.loader.api.FabricLoader.getInstance().getClass().getClassLoader()}) {
            if (cl == null) continue;
            try {
                return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ignored) {
                // 下一家
            }
        }
        throw new ClassNotFoundException(name);
    }

    /** 商路端点落在哪座村，就给那座村记一条 */
    private static void bump(class_3218 w, class_2338 p) {
        if (p == null) return;
        // 商路端点常常在几万格外的村子：区块没加载时 villageStart 会让服务器同步生成区块，
        // 一次开市就能把主线程卡几十秒。只认已加载区块里的端点，没加载的等玩家走过去再记。
        if (!w.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) return;
        try {
            String k = Bridge.keyAtStart(w, p.method_10263(), p.method_10264(), p.method_10260());
            if (k != null) DEG.merge(k, 1, Integer::sum);
        } catch (Throwable ignored) {
            // 端点读不出来就算了
        }
    }
}
