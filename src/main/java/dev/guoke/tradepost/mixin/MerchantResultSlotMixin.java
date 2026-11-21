package dev.guoke.tradepost.mixin;

import dev.guoke.tradepost.market.Pay;
import dev.guoke.tradepost.mc.MarketMerchant;
import net.minecraft.class_1657;
import net.minecraft.class_1725;
import net.minecraft.class_1727;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_1915;
import net.minecraft.class_3468;
import net.minecraft.class_9306;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成交时按价值扣钱，而不是按面额。
 *
 * <p>原版 {@code method_7667} 用 {@code offer.method_16953(槽0, 槽1)} 核对并扣减，
 * 只认和报价一模一样的物品。这里整个接管：天平上的钱全收、不够从背包补、
 * 多出来的当场找零，然后照原版流程记账 —— 加使用次数、响一声、加经验。
 */
@Mixin(class_1727.class)
public abstract class MerchantResultSlotMixin {

    @Shadow
    @Final
    private class_1725 field_7860;

    @Shadow
    @Final
    private class_1657 field_7857;

    @Shadow
    @Final
    private class_1915 field_7858;

    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.TRADE_OUTPUT_ON_TAKE, at = @At("HEAD"), cancellable = true)
    private void tradepost$settleByValue(class_1657 player, class_1799 stack, CallbackInfo ci) {
        class_1914 offer = this.field_7860.method_7642();
        if (offer == null) {
            ci.cancel();
            return;
        }
        // 成本栈本身值多少：我们自己的报价里，买页成本是"1 颗绿宝石"占位 → >0；
        // 卖页成本是货 → 0。卖页必须交回原版 —— 原版会核对天平上那几件货、扣掉、把 2 号格的钱给你，
        // 我们在这里插手就会变成"拿钱买自己的货"。
        if (Pay.price(offer) <= 0) {
            // 卖页 / 收购页：货换钱。**不能交回原版**：原版 method_16953（depleteBuyItems）比的是
            // "物品 + 组件完全一致"，而我们的卖页报价成本栈是按目录造的白板栈 —— 带附魔/耐久的
            // 真货永远对不上，它扣不了货。而点击流程在调本方法**之前**已经把 2 号格的钱塞进玩家
            // 光标了（字节码 1161~1193），于是"钱照拿、货不扣"，点一次白得一笔。
            // 所以这里自己按件数扣货（只认物品 id，附魔耐久都算数），扣得动才记账。
            int tagged = offer.method_19279();
            if (tagged >= 0) return;                     // 不是我们造的报价，交回原版
            ci.cancel();
            // 结算只在服务端做，客户端只取消原版、不碰任何状态。
            // 这里绝不能放 static 去重字段：单机里客户端和服务端共用 JVM，
            // 客户端预测先把 static 写成"已结算"，服务端真结算被挡 → 货没扣、钱白给（刷绿宝石）。
            // 正确的幂等来自"扣货后天平变空"：第二次回调时货物不够，走 deny 分支自然结束。
            if (player == null || player.method_37908() == null || player.method_37908().method_8608()) {
                return;
            }
            // 货款恒等于报价总价（负 xp 通道），不靠回调给的栈去算：
            // shift 快速移动时回调给的是空栈，拿不到数额，直接从这里取。
            int pay = Math.max(1, -tagged);
            class_9306 cost = offer.method_57556();
            if (cost == null) return;
            class_1792 item = cost.comp_2424().comp_349();
            class_1799 costStack = cost.comp_2427();
            int want = Math.max(1, cost.comp_2425());
            String deny = null;
            if (count(this.field_7860.method_5438(0), costStack) + count(this.field_7860.method_5438(1), costStack) < want) {
                deny = "天平上的货不够 " + want + " 件（天平0=" + desc(this.field_7860.method_5438(0))
                        + " 天平1=" + desc(this.field_7860.method_5438(1)) + " 要=" + desc(costStack) + "）";
            } else if (offer.method_8255()) {
                deny = "这条已经售罄";
            } else if (this.field_7858 instanceof MarketMerchant mm && !mm.canPay(-tagged)) {
                deny = "村子银库掏不出 " + (-tagged);
            }
            if (deny != null) {
                // 货被玩家从天平上拿走了，但点击流程已经把这一格的货款塞进他手里了 —— 收回来，
                // 否则就是"拿走钱不交货、货还在背包里，反复刷"。
                int stolen = pay;
                if (stolen > 0) {
                    net.minecraft.class_1703 h = player.field_7512;
                    if (h != null && !h.method_34255().method_7960()) {
                        h.method_34254(class_1799.field_8037);        // 清光标
                    } else {
                        Pay.withdraw(player, stolen);                  // 塞进背包的就从背包扣回来
                    }
                    h.method_7623();
                }
                dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 卖页成交被拒（{}），货款已收回 {}", deny, stolen);
                return;
            }
            int invBefore = dev.guoke.tradepost.Tradepost.DEBUG ? invCount(player, item) : 0;
            // 取证（按点击触发，不是按帧）：记下这一笔给了多少钱、扣了多少货
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 卖页结算 货={} 扣{}件 货款={} 天平0={} 天平1={}",
                    item.method_7876(), want, pay,
                    desc(this.field_7860.method_5438(0)), desc(this.field_7860.method_5438(1)));
            take(costStack, want);
            if (dev.guoke.tradepost.Tradepost.DEBUG) {
                // 排查用：成交后背包里还剩几件这件货（少于"成交前 - 成交件数"就说明货没扣干净）
                dev.guoke.tradepost.Tradepost.debug("[tradepost] 卖页成交后 背包剩={}（成交前 {}，卖出 {}）",
                        invCount(player, item), invBefore, want);
            }
            // 货款是点击流程直接塞进玩家手里的（1161 塞光标 → 1179 清格子 → 1193 才到这里），
            // 那一摞为了显示真价可能超过 64 颗。这里立刻把它拆成普通的 64 一摞，
            // 玩家背包里永远不会出现"怪摞"。
            flatten(player);
            this.field_7858.method_8262(offer);           // uses+1，顺带记账（卖页货款在这里结）
            player.method_7281(class_3468.field_15378);
            this.field_7858.method_19271(this.field_7858.method_19269() + Math.max(0, -tagged) / 10);
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 卖页成交 扣货 {} 件（{}）", want, item.method_7876());
            return;
        }
        // 我们的报价：真价在 xp 通道里（见 Pay.price 的重载）
        int price = Pay.price(offer, this.field_7858 instanceof MarketMerchant);
        if (price <= 0) {
            return;
        }
        ci.cancel();
        if (player == null || player.method_37908() == null || player.method_37908().method_8608()) {
            return;                          // 客户端只做预测，结账交给服务端
        }
        if (!Pay.affordable(player, this.field_7860, price)) return;
        Pay.settle(player, this.field_7860, price);
        // 货是点击逻辑自己发出去的（普通点击：1161 塞光标 → 1179 清格子 → 1193 调本方法；
        // shift 点击：method_7601 先塞背包再回调），两条路都不会漏，所以这里**绝不能再补发**——
        // 补一次就是"买一个给两个"。
        this.field_7858.method_8262(offer);                                    // 使用次数 +1（顺带记账）
        player.method_7281(class_3468.field_15378);                            // 成交音效
        this.field_7858.method_19271(this.field_7858.method_19269() + Math.max(0, -offer.method_19279()) / 10);
    }

    /** 超过 64 颗的绿宝石摞？那种只该出现在结果格里，落到玩家身上就拆开 */
    private static boolean bigStack(class_1799 st) {
        return st != null && !st.method_7960() && st.method_7909() == Pay.emeraldItem() && st.method_7947() > 64;
    }

    /** 把玩家身上（含光标）的大摞绿宝石全换成普通的 64 一摞 */
    private static void flatten(class_1657 player) {
        if (player == null) return;
        net.minecraft.class_1263 inv = player.method_31548();
        int fixed = 0;
        for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 st = inv.method_5438(i);
            if (!bigStack(st)) continue;
            inv.method_5447(i, class_1799.field_8037);
            fixed += st.method_7947();
        }
        net.minecraft.class_1703 h = player.field_7512;
        if (h != null) {
            class_1799 cur = h.method_34255();
            if (bigStack(cur)) {
                h.method_34254(class_1799.field_8037);
                fixed += cur.method_7947();
            }
            h.method_7623();
        }
        if (fixed <= 0) return;
        while (fixed > 0) {
            int n = Math.min(64, fixed);
            dev.guoke.tradepost.mc.Bridge.giveOrDrop(player, new class_1799(Pay.emeraldItem(), n));
            fixed -= n;
        }
        dev.guoke.tradepost.Tradepost.LOGGER.debug("[tradepost] 大摞货款已拆成普通堆叠");
    }

    /** 玩家背包里这件物品一共有多少件 */
    private static int invCount(class_1657 p, class_1792 item) {
        if (p == null || item == null) return -1;
        net.minecraft.class_1263 inv = p.method_31548();
        int n = 0;
        for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 st = inv.method_5438(i);
            if (!st.method_7960() && st.method_7909() == item) n += st.method_7947();
        }
        return n;
    }

    /**
     * 天平某槽里有多少件"对得上报价"的货。装备只认物品 id（耐久/附魔各不相同，
     * 但玩家卖的就是手上那件）；附魔书必须真的是同一本（附魔 + 等级一致），
     * 否则拿一本节肢杀手也能点"经验修补"那行的价。
     */
    private static int count(class_1799 stack, class_1799 want) {
        // 结算按"同一族"（不比耐久/回收价）：摆货用严格 sameKind 挑货，但成交发生在几秒后，
        // 成色指纹里的耐久和回收价会漂，严格比会把已经摆好的货判成"不够 1 件"（实测连拒 9 次）
        if (!dev.guoke.tradepost.mc.Bridge.sameFamily(stack, want)) return 0;
        return stack.method_7947();
    }

    /** 拒因日志用：一件货的短描述（id×数量 + 装备成色/附魔书档位） */
    private static String desc(class_1799 st) {
        return dev.guoke.tradepost.mc.Bridge.describe(st);
    }

    /** 从天平两个槽里按件数扣掉货（够不够由调用方先验过） */
    private void take(class_1799 want, int need) {
        int before = count(this.field_7860.method_5438(0), want) + count(this.field_7860.method_5438(1), want);
        int left = need;
        for (int i = 0; i < 2 && left > 0; i++) {
            class_1799 st = this.field_7860.method_5438(i);
            if (count(st, want) <= 0) continue;
            int n = Math.min(left, st.method_7947());
            class_1799 rest = st.method_7972();
            rest.method_7939(st.method_7947() - n);
            this.field_7860.method_5447(i, rest.method_7947() <= 0 ? class_1799.field_8037 : rest);
            left -= n;
        }
        int after = count(this.field_7860.method_5438(0), want) + count(this.field_7860.method_5438(1), want);
        String nm = want.method_7909().method_7876();
        if (after != before - need) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 扣货没按预期生效：{} 前={} 要扣={} 后={}",
                    nm, before, need, after);
        } else {
            dev.guoke.tradepost.Tradepost.LOGGER.debug("[tradepost] 扣货 {} 前={} 后={}", nm, before, after);
        }
    }
}
