package dev.guoke.tradepost.api;

import dev.guoke.tradepost.market.BookShop;
import dev.guoke.tradepost.market.VillageLevel;
import net.minecraft.class_3218;
import net.minecraft.class_3222;

/**
 * 对外只读 API —— 给别的模组、整合包脚本、kubejs 之类查询用。
 *
 * <p>这里全是查询，不改任何状态、不发包、不动库存；调用它不需要开集市界面。
 * 计价规则和集市里用的是同一份代码，所以问到的价和玩家看到的价永远一致。
 *
 * <pre>{@code
 * double p = TradepostApi.basePrice("minecraft:diamond");   // 45.0
 * boolean b = TradepostApi.banned("minecraft:diamond_sword"); // true（改走装备回收）
 * int lv = TradepostApi.levelOf(xp);
 * }</pre>
 *
 * <p>注意：这里给的是"底价"，还没乘村庄财富、库存压力、品质、稀有度那些系数。
 * 想要"这个村此刻的真价"，读集市面板那份 JSON（root -> sell/buy 每行的 price），
 * 或者开一次集市看 {@code MarketMerchant.rows}。
 */
public final class TradepostApi {

    private TradepostApi() {
    }

    /** 物品底价（已含 rarity 溢出、tag_values、合成表链式反推）；≤0 表示这东西在我们这儿没价。 */
    public static double basePrice(String itemId) {
        return dev.guoke.tradepost.mc.Bridge.basePrice(itemId);
    }

    /** 是否被黑名单拦住（剑/甲/工具/唱片/桶这些走装备回收或干脆不收）。 */
    public static boolean banned(String itemId) {
        return dev.guoke.tradepost.mc.Bridge.isBanned(itemId);
    }

    /** 经验 → 村庄等级（1..5）。 */
    public static int levelOf(double xp) {
        return VillageLevel.of(xp);
    }

    /** 等级名字：露天集市 / 固定市集 / 商栈 / 商会 / 商都。 */
    public static String levelName(int level) {
        return VillageLevel.name(level);
    }

    /** 当前等级内的进度 0..1，可以直接喂给经验条。 */
    public static float levelProgress(double xp) {
        return VillageLevel.progress(xp);
    }

    /** 距离下一级还差多少经验；满级返回 -1。 */
    public static double xpToNext(double xp) {
        return VillageLevel.toNext(xp);
    }

    /** 附魔书收购价：book:minecraft:mending|1 这种码，或者 (附魔 id, 等级) 分开给。 */
    public static double bookValue(String code) {
        return BookShop.valueOf(code);
    }

    public static double bookValue(String enchantId, int level) {
        return BookShop.value(enchantId, level);
    }

    /** 玩家脚下这块地属于哪个村（拿不到就是 null，比如野外）。 */
    public static String villageOf(class_3222 player) {
        try {
            if (player == null || !(player.method_5770() instanceof class_3218 w)) return null;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            return dev.guoke.tradepost.mc.Bridge.villageKey(w, x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }

    // ———————————————— 下面两个会「开界面」，是写操作 ————————————————

    /**
     * 从任何地方替玩家打开集市（右键方块 / NPC 交互 / 别的模组的事件里都行）。
     *
     * <p>必须在<b>服务端</b>调（{@code ServerPlayerEntity}），客户端调过来没人理；
     * 玩家不在村里时返回 false，什么也不会发生。
     * 打开的是这个村此刻的集市，跟村民交易界面的那一份数据完全一致。
     *
     * <pre>{@code
     * // 例：自己的方块被右键时
     * if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
     *     TradepostApi.openBoard(sp);
     * }
     * }</pre>
     */
    public static boolean openBoard(class_3222 player) {
        if (villageOf(player) == null) return false;
        try {
            dev.guoke.tradepost.mc.Bridge.openBoard(player);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 这个村此刻的完整价目表（就是界面吃的那份 JSON，字段见 docs/EXTENDING.md「面板 JSON」一节）。
     * 玩家不在村里返回 null。想在自己的界面里做联动（比如行情牌、告示板）就读这个。
     */
    public static String boardJson(class_3222 player) {
        try {
            return dev.guoke.tradepost.mc.Bridge.boardJson(player);
        } catch (Throwable t) {
            return null;
        }
    }
}
