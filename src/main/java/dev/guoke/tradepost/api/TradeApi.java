package dev.guoke.tradepost.api;

import net.minecraft.class_3222;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对外「问价 + 成交 + 成交通知」接口 —— 给别的模组、整合包、脚本、自定义商店用。
 *
 * <p>与 {@link TradepostApi} 的区别：那边只查底价，这边问的是<b>这个村此刻的真价</b>，
 * 还会真的把钱和货、村里库存、银库、经验、全世界货量一起记了 —— 走的是集市界面同一套代码，
 * 所以从这里成交和玩家自己点集市，账完全一样，不存在第二个价格来源。
 *
 * <pre>{@code
 * // 1) 问价：这个村此刻收一颗钻石多少、能收几手
 * TradeApi.Quote q = TradeApi.quote(player, "minecraft:diamond", TradeApi.Side.SELL, 3);
 * if (q.ok()) player.sendMessage(Text.literal("收 " + q.unit() + " 颗/件，共 " + q.total()));
 *
 * // 2) 直接成交（服务端）：成功返回 null，失败返回人话原因
 * String why = TradeApi.trade(player, "minecraft:diamond", TradeApi.Side.SELL, 3);
 *
 * // 3) 想让自己的任务/商店跟着记账
 * TradeApi.addListener(e -> LOGGER.info("{} 在 {} {} {} 件，{} 绿宝石",
 *         e.player(), e.village(), e.side(), e.count(), e.total()));
 * }</pre>
 *
 * <p><b>Side 是玩家视角</b>：{@code BUY} = 玩家花钱买货（村里卖给他），{@code SELL} = 玩家卖货（村里收）。
 * <p>必须在服务端调用（{@code ServerPlayerEntity}）；客户端问价会得到"不在村子里"。
 * <p>件数会按"一手"向下取整：集市里一份可能是 4 件、16 件，问 10 件而一手是 16 件时总额按 0 手算。
 */
public final class TradeApi {

    private TradeApi() {
    }

    /** 玩家视角：买 = 玩家掏钱拿货；卖 = 玩家交货拿钱。 */
    public enum Side { BUY, SELL }

    /**
     * 一行行情。
     *
     * @param lot    一手几件（集市里"份数"的粒度）
     * @param unit   每件几颗绿宝石（已含本村系数、品质、缺货压力）
     * @param packs  此刻最多能成交几手（银库 / 库存空间 / 货架剩余 / 钱包 夹出来的）
     * @param count  按你的请求取整之后实际能成交的件数
     * @param total  实际能成交的总额（绿宝石）
     * @param reason 不能成交的原因；ok 为 true 时是 null
     */
    public record Quote(String itemId, Side side, int lot, int unit, int packs,
                        int count, int total, boolean ok, String reason) {
    }

    /** 一笔成交。 */
    public record TradeEvent(UUID player, String village, String itemId, Side side, int count, int total) {
    }

    /** 成交监听器；抛异常只会被记一条日志，不会影响交易本身。 */
    public interface Listener {
        void onTrade(TradeEvent event);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static void addListener(Listener listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    /** 内部用：成交之后广播。任何入口（集市界面、{@link #trade}、命令）成交都会走到这里。 */
    public static void fireTrade(class_3222 player, String itemId, boolean sell, int count, int total) {
        if (LISTENERS.isEmpty() || player == null) return;
        TradeEvent ev = new TradeEvent(player.method_5667(), TradepostApi.villageOf(player), itemId,
                sell ? Side.SELL : Side.BUY, count, total);
        for (Listener l : LISTENERS) {
            try {
                l.onTrade(ev);
            } catch (Throwable t) {
                dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 成交通知出错（已忽略）", t);
            }
        }
    }

    /**
     * 问价：这个村此刻买/卖这件货什么行情、还能成交多少。不在村里返回 null。
     *
     * @param count 你想要多少件（只会告诉你"能成交多少"，不改任何状态）
     */
    public static Quote quote(class_3222 player, String itemId, Side side, int count) {
        try {
            var q = side == Side.SELL
                    ? dev.guoke.tradepost.mc.Bridge.quoteSell(player, itemId)
                    : dev.guoke.tradepost.mc.Bridge.quoteBuy(player, itemId);
            if (q == null) return null;
            int lot = Math.max(1, q.lot());
            int packs = Math.max(0, Math.min(count / lot, q.packs()));
            int n = packs * lot;
            int total = packs * q.price();
            int unit = Math.max(0, (int) Math.round((double) q.price() / lot));
            return new Quote(itemId, side, lot, unit, q.packs(), n, total,
                    q.why() == null && packs > 0, q.why() == null && packs <= 0 ? "件数不够一手（一手 " + lot + " 件）" : q.why());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 真的成交一笔（服务端）。成功返回 null，失败返回人话原因（"本村银库只剩 12…"这种）。
     *
     * <p>卖：从背包扣货、村里给钱、库存与全世界货量一起记；买：扣钱、给货（背包满了掉脚边）。
     * 带附魔/效果的货（gear:/book:/potion: 那些）不在这里卖买 —— 它们要连数据一起看，
     * 请用 {@link TradepostApi#openBoard} 让玩家自己在集市里点。
     *
     * @param count 件数，按一手向下取整
     */
    public static String trade(class_3222 player, String itemId, Side side, int count) {
        return dev.guoke.tradepost.mc.Bridge.trade(player, itemId, count, side == Side.SELL);
    }
}
