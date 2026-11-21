package dev.guoke.tradepost.market;

import dev.guoke.tradepost.mc.Bridge;
import net.minecraft.class_1263;
import net.minecraft.class_1657;
import net.minecraft.class_1725;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_9306;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按"价值"结算，而不是按"面额"。
 *
 * <p>原版只认和报价完全一致的物品：报价写 8 块，你手里 72 颗散绿宝石就摆不出来。
 * 这里把天平上的东西按价值折算（绿宝石=1、绿宝石块=9），只要总价值够价，就能成交；
 * 多给的部分当场找零（8 块买 70 的东西 → 退你 2 颗绿宝石）。
 */
public final class Pay {
    private static final Logger LOG = LoggerFactory.getLogger("tradepost");

    private Pay() {
    }

    private static class_1792 EMERALD;
    private static class_1792 BLOCK;

    private static class_1792 emerald() {
        if (EMERALD == null) EMERALD = Bridge.item("minecraft:emerald");
        return EMERALD;
    }

    private static class_1792 block() {
        if (BLOCK == null) BLOCK = Bridge.item("minecraft:emerald_block");
        return BLOCK;
    }

    public static class_1792 emeraldItem() {
        return emerald();
    }

    public static class_1792 blockItem() {
        return block();
    }

    /**
     * 一摞 {@code count} 颗绿宝石，**并把这摞的最大堆叠数抬到 count**。
     *
     * <p>原版 {@code class_1263.method_5444()}（库存单格上限）默认 64，
     * {@code class_1725.method_5447} 写完格子会调 {@code stack.method_58408(min(64, 这摞的上限))}
     * 把数量夹回去 —— 236 颗的货款在界面上就只剩 64，玩家也只拿到 64。
     * 这里给这摞挂上 {@code max_stack_size} 组件（满级堆叠模组用的就是这一招），
     * 再把商人容器的单格上限放开（见 {@code MerchantInventoryMixin.method_5444}），
     * 数量就能原样进格子、原样画出来。
     */
    public static class_1799 emeralds(int count) {
        int n = Math.max(1, count);
        // 先用 1 建栈，再决定上限、最后设数量：构造器会按物品的默认上限（64）夹一次，
        // 顺序反了就永远只有 64 颗。
        class_1799 st = new class_1799(emerald(), 1);
        try {
            // 只在超过普通上限时才挂组件：小额仍是可以和普通绿宝石并堆的那一种
            if (n > 64) st.method_57379(net.minecraft.class_9334.field_50071, 9999);
        } catch (Throwable ignored) {
            // 挂不上组件就退回普通堆叠（最多显示 64，不影响结算）
        }
        st.method_7939(n);
        return st;
    }

    /** 一摞东西值多少绿宝石 */
    public static int value(class_1799 stack) {
        if (stack == null || stack.method_7960()) return 0;
        class_1792 it = stack.method_7909();
        if (it == block()) return 9 * stack.method_7947();
        if (it == emerald()) return stack.method_7947();
        return 0;
    }

    /**
     * 把一笔钱拆成"结果格"和"右边第二格"两摞。
     *
     * <p>天平/结果格的单格容量已经放开到 9999（{@code MerchantInventoryMixin.method_58350}），
     * 所以一笔钱就是一摞，不再凑绿宝石块：{@link #emeralds(int)} 给大摞挂 max_stack_size
     * 组件，数量原样进格子、原样画出来。超过 9999 才拆成第二格。
     *
     * @return {结果格, 第二格}，摆不下的用空栈
     */
    public static class_1799[] split(int amount) {
        int rest = Math.max(0, amount);
        if (rest <= 0) return new class_1799[]{class_1799.field_8037, class_1799.field_8037};
        if (rest <= 9999) return new class_1799[]{emeralds(rest), class_1799.field_8037};
        return new class_1799[]{emeralds(9999), emeralds(Math.min(9999, rest - 9999))};
    }

    /** 两格加起来能显示多少（不够的零头成交时补发） */
    public static int shown(int amount) {
        class_1799[] p = split(amount);
        return value(p[0]) + value(p[1]);
    }

    /** 一件报价要多少绿宝石 */
    public static int price(class_1914 offer) {
        if (offer == null) return 0;
        int p = unit(offer.method_57556());
        java.util.Optional<class_9306> second = offer.method_57557();
        if (second != null && second.isPresent()) p += unit(second.get());
        return p;
    }

    /**
     * 本行的真实价。
     *
     * <p>我们自己的集市把总价塞在报价的 xp 通道里（{@code method_19279}），
     * 成本栈只是"1 颗绿宝石"的占位 —— 这样列表上永远不会冒出绿宝石块，
     * 玩家看到的永远是一颗绿宝石 + 一串数字。收购页的成本是货不是钱，
     * {@link #price(class_1914)} 会返回 0，照样走原版那条路。
     */
    public static int price(class_1914 offer, boolean ourMarket) {
        int tagged = offer.method_19279();
        if (tagged < 0) return -tagged;   // 我们自己的集市：真价写成负 xp，客户端服务端都认得出
        return price(offer);
    }

    private static int unit(class_9306 cost) {
        if (cost == null) return 0;
        class_1792 it = cost.comp_2424().comp_349();
        int n = cost.comp_2425();
        if (it == block()) return 9 * n;
        if (it == emerald()) return n;
        return 0;
    }

    /** 天平两个槽里一共值多少 */
    public static int onScale(class_1725 inv) {
        return value(inv.method_5438(0)) + value(inv.method_5438(1));
    }

    /** 背包里所有绿宝石/块折成绿宝石 */
    public static int purse(class_1657 player) {
        if (player == null) return 0;
        class_1263 inv = player.method_31548();
        int sum = 0;
        for (int i = 0; i < inv.method_5439(); i++) sum += value(inv.method_5438(i));
        return sum;
    }

    /** 天平上的钱够不够这单 */
    public static boolean affordable(class_1657 player, class_1725 inv, int price) {
        return price > 0 && onScale(inv) + purse(player) >= price;
    }

    /**
     * 成交：天平上的钱全收（多的当场找零），不够的部分从背包补（先块、后散钱）。
     */
    public static void settle(class_1657 player, class_1725 inv, int price) {
        int paid = drainScale(inv);
        if (paid < price) paid += withdraw(player, price - paid);
        int change = paid - price;
        if (change > 0) giveChange(player, change);
        LOG.info("[tradepost] 结算 价={} 付={} 找零={}", price, paid, Math.max(0, change));
    }

    /**
     * 点行时把天平摆成"一摞绿宝石，数量就是价钱"——跟列表上一个样子。
     *
     * <p>先前摆在天平上的钱先退回背包（价值不变），再从背包里扣出恰好这笔钱的等值物，
     * 合成一摞放进 0 号槽：绿宝石块在背包里被换成一摞绿宝石，价值一样、看起来清爽。
     * 钱不够就摆出全部家当，成交时不够价自然不成立。
     */
    public static void stage(class_1657 player, class_1725 inv, int price) {
        if (player == null || inv == null || price <= 0) return;
        int have = onScale(inv);
        int target = Math.max(0, Math.min(price, purse(player) + have));
        if (target == have) return;                      // 已经正好：别动。老代码"全退再全取"就是闪的根源
        drainScale(inv);
        int put;
        if (target < have) {
            put = target;
            giveChange(player, have - target);           // 只把多出来的退回背包
        } else {
            put = have + withdraw(player, target - have); // 只从背包补差额
        }
        if (put > 0) {
            // 天平上摆"块 + 散钱"两摞（{@link #split}）：两格共 1152，价钱多少都摆得下。
            class_1799[] sp = split(put);
            inv.method_5447(0, sp[0]);
            inv.method_5447(1, sp[1]);
        }
    }

    /** 把天平上所有钱收掉，返回收了多少 */
    private static int drainScale(class_1725 inv) {
        int paid = 0;
        for (int i = 0; i < 2; i++) {
            class_1799 st = inv.method_5438(i);
            int v = value(st);
            if (v <= 0) continue;
            inv.method_5447(i, class_1799.field_8037);
            paid += v;
        }
        return paid;
    }

    /** 从背包扣 need（先整块、再散钱、最后砸开一块补零头），返回真正扣到的价值 */
    public static int withdraw(class_1657 player, int need) {
        if (player == null || need <= 0) return 0;
        class_1263 inv = player.method_31548();
        int got = 0;
        for (int pass = 0; pass < 2 && need > 0; pass++) {
            class_1792 want = pass == 0 ? block() : emerald();
            for (int i = 0; i < inv.method_5439() && need > 0; i++) {
                class_1799 st = inv.method_5438(i);
                if (st.method_7960() || st.method_7909() != want) continue;
                int u = unitOf(st);
                if (u <= 0) continue;
                int take = Math.min(st.method_7947(), need / u);
                if (take <= 0) continue;
                class_1799 left = st.method_7972();
                left.method_7939(st.method_7947() - take);
                inv.method_5447(i, left.method_7947() <= 0 ? class_1799.field_8037 : left);
                got += take * u;
                need -= take * u;
            }
        }
        // 整块凑不出零头（相差不足 9）：砸开一块，把零头直接算进这笔钱，
        // 剩下 9-零头 当场退回背包。不砸的话，13 绿宝石的东西只摆得出 9，
        // 价钱不到、成品格不亮，看上去就是"点了没反应"。
        if (need > 0) {
            for (int i = 0; i < inv.method_5439() && need > 0; i++) {
                class_1799 st = inv.method_5438(i);
                if (st.method_7960() || st.method_7909() != block()) continue;
                int use = Math.min(need, 9);
                class_1799 left = st.method_7972();
                left.method_7939(st.method_7947() - 1);
                inv.method_5447(i, left.method_7947() <= 0 ? class_1799.field_8037 : left);
                got += use;
                need -= use;
                if (9 - use > 0) giveChange(player, 9 - use);
                break;
            }
        }
        return got;
    }

    /**
     * 找零：一律给散绿宝石，<b>绝不替玩家合成绿宝石块</b>。
     *
     * <p>以前按 9 的倍数给块，玩家会觉得"钱怎么自己变成块了"（合成本来是玩家的选择）。
     * 现在只给散钱；块只有在玩家自己花/自己拿的时候才动。
     */
    private static void giveChange(class_1657 player, int amount) {
        int singles = Math.max(0, amount);
        while (singles > 0) {
            int n = Math.min(64, singles);
            Bridge.giveOrDrop(player, new class_1799(emerald(), n));
            singles -= n;
        }
    }

    private static int unitOf(class_1799 st) {
        if (st == null || st.method_7960()) return 0;
        class_1792 it = st.method_7909();
        if (it == block()) return 9;
        if (it == emerald()) return 1;
        return 0;
    }
}
