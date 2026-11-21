package dev.guoke.tradepost.mixin;

import dev.guoke.tradepost.market.Pay;
import dev.guoke.tradepost.mc.MarketMerchant;
import net.minecraft.class_1657;
import net.minecraft.class_1725;
import net.minecraft.class_1728;
import net.minecraft.class_3222;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_1915;
import net.minecraft.class_1916;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 点一行 → 按"价值"自动摆钱。
 *
 * <p>原版 {@code method_20215} 只搬和报价面额完全一致的物品：报价写 8 块、
 * 你手里 72 颗散绿宝石，它搬不动，天平空着。这里改成按价值搬：
 * 先搬块（9 一颗），不够再搬散绿宝石（1 一颗），塞满两个槽为止。
 * 收购页（成本是货不是钱）原样交回原版。
 */
@Mixin(class_1728.class)
public abstract class MerchantScreenHandlerMixin {

    @Shadow
    @Final
    private class_1915 field_7863;

    @Shadow
    @Final
    private class_1725 field_7861;

    /**
     * 原版 {@code method_20595()} 里有一句 {@code (Entity) this.field_7863} ——
     * 它假定商人一定是实体（村民），拿它的坐标去播"成交"的声音。
     * 我们的商人只是个 {@link MarketMerchant}，shift 快速移动物品时就会
     * {@code ClassCastException: MarketMerchant cannot be cast to class_1297}，
     * 服务端把整个点击包吞掉（表现为 shift 点格子没反应）。不是实体就直接跳过那段。
     */
    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.HANDLER_QUICK_MOVE, at = @At("HEAD"), cancellable = true)
    private void tradepost$skipEntitySound(CallbackInfo ci) {
        if (!(this.field_7863 instanceof net.minecraft.class_1297)) ci.cancel();
    }

    /**
     * shift 点背包/快捷栏里的东西：**先往天平两格塞**。
     *
     * <p>原版 {@code method_7601}（transferSlot）根本不往交易格塞东西 —— 它的分支只有
     * "结果格 → 背包""天平 → 背包""背包 → 快捷栏""快捷栏 → 背包"，所以玩家 shift 一堆绿宝石
     * 永远不会自己跳到天平上。这里补上这一段：只搬这一行报价真正要的东西
     * （买页只认绿宝石/绿宝石块，卖页只认这一行的货），搬完直接返回，剩下的交回原版。
     */
    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.HANDLER_QUICK_TRANSFER,
            at = @At("HEAD"), cancellable = true)
    private void tradepost$quickToTrade(class_1657 player, int index, CallbackInfoReturnable<class_1799> cir) {
        try {
            if (index == 2) {
                // 结果格：客户端不预测"钱进背包"。快速 shift 时客户端会先塞满一背包绿宝石，
                // 服务端同步后再"回收"，这就是"闪"。只让服务端转移。
                if (player != null && player.method_37908() != null && player.method_37908().method_8608()) {
                    cir.setReturnValue(class_1799.field_8037);
                }
                return;
            }
            if (index < 3) return;                       // 0/1 是天平，不在这里管
            class_1735 from = slot(index);
            if (from == null) return;
            class_1799 st = from.method_7677();
            if (st.method_7960()) return;
            boolean want = wanted(st);
            if (!want) {
                // 卖页：还没点过任何一行时，shift 也要能用 —— 按这摞货自己找到收它的那一行，
                // 替玩家"点"一下（走的是同一个点行流程：摆货 + 算货款），再把这一下吃掉。
                if (this.field_7863 instanceof MarketMerchant mm && mm.selling()) {
                    int row = mm.rowForStack(st);
                    if (row < 0) return;
                    ((class_1728) (Object) this).method_20215(row);
                    if (!st.method_7960()) from.method_7668();
                    dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 快速移动 槽{} {} 按货找行 {} 并摆上天平",
                            index, st.method_7909().method_7876(), row);
                    cir.setReturnValue(class_1799.field_8037);
                }
                return;
            }
            boolean moved = false;
            for (int i = 0; i < 2 && !st.method_7960(); i++) {
                class_1735 to = slot(i);
                if (to == null || !to.method_7680(st)) continue;
                class_1799 cur = to.method_7677();
                if (!cur.method_7960() && !class_1799.method_31577(cur, st)) continue;
                int room = to.method_7676(st) - (cur.method_7960() ? 0 : cur.method_7947());
                if (room <= 0) continue;
                int n = Math.min(room, st.method_7947());
                if (cur.method_7960()) {
                    class_1799 one = st.method_7972();
                    one.method_7939(n);
                    to.method_53512(one);
                } else {
                    cur.method_7933(n);
                    to.method_7668();
                }
                st.method_7934(n);
                moved = true;
            }
            if (!moved) {
                // 要的东西，但一格都塞不进去（天平满了/放不下这种货）——留一行日志好查
                dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 快速移动 槽{} {} 一件也没塞进天平（手上 {} 件，天平已有 {}）",
                        index, st.method_7909().method_7876(), st.method_7947(), onScale(st));
                return;                                  // 交回原版
            }
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 快速移动 槽{} {} 搬上天平，剩 {} 件",
                    index, st.method_7909().method_7876(), st.method_7947());
            if (st.method_7960()) from.method_53512(class_1799.field_8037);
            else from.method_7668();
            cir.setReturnValue(class_1799.field_8037);
        } catch (Throwable ignored) {
            // 出错就当没拦过，原版照旧
        }
    }

    /**
     * 这一摞是不是当前这行报价要的东西。
     *
     * <p>认两处：原版容器里"当前这条报价"（{@code method_7642}），以及玩家最后点的那一行
     * （{@link #PICK}）。只看前者时 shift 经常判定"不要" —— 那条报价要等货物摆上天平才会被
     * 原版认成 current，于是 shift 什么都没搬（玩家说的"shift 还是没适配"）。
     */
    private boolean wanted(class_1799 st) {
        class_1914 offer = this.field_7861.method_7642();
        class_1799 want = offer == null || offer.method_57556() == null
                ? null : offer.method_57556().comp_2427();
        if (want == null || want.method_7960()) want = PICK;      // 退回"玩家点的那一行"
        if (want == null || want.method_7960()) return false;
        class_1792 w = want.method_7909();
        class_1792 got = st.method_7909();
        if (got != w) {
            // 天平上放绿宝石块也算钱（原版就是这么收的），买页要认它
            return w == Pay.emeraldItem() && got == Pay.blockItem();
        }
        // 同一件物品还要看"是不是同一档"：附魔书比附魔，酒比品质，装备比耐久/附魔
        if ("minecraft:enchanted_book".equals(dev.guoke.tradepost.mc.Bridge.itemId(want))) {
            String a = dev.guoke.tradepost.mc.Bridge.bookCode(st);
            String b = dev.guoke.tradepost.mc.Bridge.bookCode(want);
            return a != null && a.equals(b);
        }
        int qa = dev.guoke.tradepost.mc.Bridge.brewOf(st);
        int qb = dev.guoke.tradepost.mc.Bridge.brewOf(want);
        return qa == qb;
    }

    /**
     * shift 取结果格时先存一份"原本有什么"。
     * 见 {@link dev.guoke.tradepost.mc.QuickTake}：不存的话回调里拿到的是空栈。
     */
    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.HANDLER_QUICK_TRANSFER, at = @At("HEAD"))
    private void tradepost$snapResult(class_1657 player, int index, CallbackInfoReturnable<class_1799> cir) {
        try {
            if (index != 2) {
                dev.guoke.tradepost.mc.QuickTake.begin(null);
                return;
            }
            class_1799 st = this.field_7861.method_5438(2);
            dev.guoke.tradepost.mc.QuickTake.begin(st.method_7960() ? null : st.method_7972());
        } catch (Throwable ignored) {
        }
    }

    // 注意：method_7611 / method_7616 是父类 class_1703 的，class_1728 自己没声明。
    // Mixin 的 @Shadow 只在目标类里找，找不到就是 "was not located in the target class" 启动崩，
    // 所以这里不 shadow，直接按父类型强转调用（运行时的类确实是 class_1703 的子类）。
    private class_1735 slot(int i) {
        return ((net.minecraft.class_1703) (Object) this).method_7611(i);
    }

    /**
     * class_1703#method_7616（insertItem）是 protected 的，从 mixin 里强转调用javac 不给过
     * （不在同一个包、也不是它的子类），所以走反射。一次点行只调一次，开销无所谓。
     */
    private boolean insert(class_1799 stack, int startIndex, int endIndex, boolean fromLast) {
        try {
            if (INSERT == null) {
                INSERT = net.minecraft.class_1703.class.getDeclaredMethod("method_7616",
                        class_1799.class, int.class, int.class, boolean.class);
                INSERT.setAccessible(true);
            }
            return (Boolean) INSERT.invoke(this, stack, startIndex, endIndex, fromLast);
        } catch (Throwable t) {
            return false;
        }
    }

    private static java.lang.reflect.Method INSERT;

    /** 玩家最后点的那一行要什么（{@code null} = 还没点过）。shift 搬货照它认货。 */
    private static class_1799 PICK;

    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.HANDLER_CLICK_OFFER, at = @At("HEAD"), cancellable = true)
    private void tradepost$stageByValue(int index, CallbackInfo ci) {
        class_1916 offers = this.field_7863.method_8264();
        if (offers == null || index < 0 || index >= offers.size()) return;
        class_1914 offer = offers.get(index);
        // 取证（INFO，每次点击一行）：点击到底进没进这里、这一行是什么货、是不是售罄
        dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 点击行 {} 页{} 售罄={} 货={} 成本={}",
                index,
                (this.field_7863 instanceof MarketMerchant mm0 && mm0.selling()) ? "卖" : "买",
                offer.method_8255(),
                offer.method_57556().comp_2427().method_7909().method_7876(),
                Pay.price(offer));
        if (offer.method_8255()) {           // 售罄（村里收满/没钱）：行留着看，点了不动作
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 点击行 {} 已售罄（村里收满/银库空了），不动作 天平0={} 天平1={}",
                    index, this.field_7861.method_5438(0).method_7947(), this.field_7861.method_5438(1).method_7947());
            ci.cancel();
            return;
        }
        int costValue = Pay.price(offer);            // 成本栈本身值多少（我们的卖页成本是货 → 0）
        int need = Pay.price(offer, this.field_7863 instanceof MarketMerchant);
        if (need <= 0 && costValue <= 0) return;     // 认不出的报价：交回原版

        // 卖页：这一行要的货，天平上已经摆着的就留着别动 —— 玩家常把整摞一次放上天平，
        // 要是照旧先全部退回背包再摆 1 件，他看到的只是"我的东西被弹回来了"，白摆一次。
        net.minecraft.class_9306 costComp = offer.method_57556();
        class_1799 rowWant = costComp == null ? null : costComp.comp_2427();
        boolean sellRow = costValue <= 0 && rowWant != null && !rowWant.method_7960();
        int rowNeed = sellRow ? Math.max(1, costComp.comp_2425()) : 0;
        int onAlready = sellRow ? onScaleFamily(rowWant) : 0;

        // 1) 天平上原来的东西退回背包（卖页且正是这一行的货则留下）
        for (int i = 0; i < 2; i++) {
            class_1799 st = this.field_7861.method_5438(i);
            if (st.method_7960()) continue;
            if (sellRow && dev.guoke.tradepost.mc.Bridge.sameFamily(st, rowWant)) continue;
            insert(st, 3, 39, true);                            // 就地挪回背包，塞不下的留在栈里
            this.field_7861.method_5447(i, st.method_7960() ? class_1799.field_8037 : st);
        }
        this.field_7861.method_7643(index);          // 记下选中哪一行
        if (this.field_7863 instanceof MarketMerchant mm1) mm1.noteClick(index);   // 成交后按这一行续摆
        PICK = offer.method_57556().comp_2427();     // 这一行到底要什么（shift 搬货照它认）

        // 2a) 卖页：成本是货。原版只搬"和报价完全一致"的那一摞，玩家手里 20 件、
        //     报价要 13 件就对不上，天平会一直空着 —— 这里按件数自己搬。
        if (costValue <= 0) {
            // 取证用 INFO（一进一出的两行，量很小）：卖页到底点了哪一行、货搬上去没有
            int movedAll = stageGoods(offer, Math.max(0, rowNeed - onAlready));
            class_1799 wantStack = offer.method_57556().comp_2427();
            dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 卖页点行 {} 价={} 搬上={} 天平={} 要={} 物品={}",
                    index, need, movedAll, onScale(wantStack),
                    Math.max(1, offer.method_57556().comp_2425()),
                    wantStack.method_7909().method_7876());
            syncPayout();
            ci.cancel();
            return;
        }

        // 2) 摆钱：整笔摆成一摞绿宝石，数量就是价钱（跟列表上同一个样子，不出现绿宝石块）
        Pay.stage(this.field_7863.method_8257(), this.field_7861, need);
        syncPayout();
        ci.cancel();
    }

    /** 原版自己走完（认不出的报价）时也把第二格里的钱对齐 */
    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.HANDLER_CLICK_OFFER, at = @At("RETURN"))
    private void tradepost$syncPayoutToo(int index, CallbackInfo ci) {
        syncPayout();
    }

    /** 第二格摆这一行货款里结果格放不下的那半截（卖页才有；买页的钱在玩家自己手上） */
    private void syncPayout() {
        try {
            if (((net.minecraft.class_1703) (Object) this).field_7761.size() <= MarketMerchant.PAYOUT_SLOT) return;
            class_1735 pad = slot(MarketMerchant.PAYOUT_SLOT);
            if (pad == null) return;
            class_1799 want = MarketMerchant.payoutFor(this.field_7861.method_7642());
            class_1799 cur = pad.method_7677();
            if (!cur.method_7960() && cur.method_7909() == want.method_7909()
                    && cur.method_7947() == want.method_7947()) {
                return;                                  // 已经一样了，别刷同步
            }
            pad.method_53512(want);
        } catch (Throwable ignored) {
            // 没有第二格就算了
        }
    }

    /** 把这一行要的货按件数搬上天平（卖页），连附魔/耐久一起带过去 */
    private int stageGoods(class_1914 offer, int deficit) {
        try {
            net.minecraft.class_9306 cost = offer.method_57556();
            if (cost == null) return 0;
            // 按整件货搬（不是只按物品 id）：附魔书必须认附魔档位，
            // 否则会把背包里第一本"随便什么书"搬上天平，结算时又按档位核对不上 → 永远卖不出去
            class_1799 want = cost.comp_2427();
            if (deficit <= 0) return 0;                      // 天平上已经够了：不再往背包里掏
            int n = deficit;
            int moved = stage(0, want, Math.min(64, n));
            if (moved < n) moved += stage(1, want, n - moved);
            return moved;
        } catch (Throwable t) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 卖页摆货失败", t);
            return 0;
        }
    }

    /** 关界面时第二格还留着没拿走的钱（成交了没点）→ 塞回背包，别让它凭空消失 */
    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.HANDLER_ON_CLOSED, at = @At("HEAD"))
    private void tradepost$rescuePayout(net.minecraft.class_1657 player, CallbackInfo ci) {
        try {
            if (((net.minecraft.class_1703) (Object) this).field_7761.size() <= MarketMerchant.PAYOUT_SLOT) return;
            class_1735 pad = slot(MarketMerchant.PAYOUT_SLOT);
            if (pad == null) return;
            class_1799 st = pad.method_7677();
            if (!st.method_7960()) {
                if (player instanceof class_3222 sp) {
                    dev.guoke.tradepost.mc.Bridge.giveOrDrop(sp, st.method_7972());
                }
                pad.method_53512(class_1799.field_8037);
            }
        } catch (Throwable ignored) {
            // 没有第二格就算了
        }
    }

    /** 背包里这件物品一共有多少件（含盔甲栏、副手） */
    private static int invCount(net.minecraft.class_1657 p, class_1792 item) {
        if (p == null || item == null) return -1;
        net.minecraft.class_1263 inv = p.method_31548();
        int n = 0;
        for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 st = inv.method_5438(i);
            if (!st.method_7960() && st.method_7909() == item) n += st.method_7947();
        }
        return n;
    }

    /** 天平两格上一共摆了几件"和 ref 同一种"的货 */
    private int onScale(class_1799 ref) {
        int n = 0;
        for (int i = 0; i < 2; i++) {
            class_1799 st = this.field_7861.method_5438(i);
            if (same(st, ref)) n += st.method_7947();
        }
        return n;
    }

    /** 天平两格上一共摆了几件"和 ref 同一族"的货（结算口径，不比耐久/回收价） */
    private int onScaleFamily(class_1799 ref) {
        int n = 0;
        for (int i = 0; i < 2; i++) {
            class_1799 st = this.field_7861.method_5438(i);
            if (!st.method_7960() && dev.guoke.tradepost.mc.Bridge.sameFamily(st, ref)) n += st.method_7947();
        }
        return n;
    }

    /** 两件货算不算同一种：物品相同；附魔书还要附魔档位完全相同 */
    private static boolean same(class_1799 a, class_1799 b) {
        return dev.guoke.tradepost.mc.Bridge.sameKind(a, b);
    }

    /**
     * 从玩家背包往天平某槽里搬 want 件"和 ref 同一种"的货，返回搬了多少。
     *
     * <p>同一种 ≠ 同一个物品 id：附魔书得按附魔档位认。以前只比 id，
     * 背包里第一本书（可能是节肢杀手）被搬上天平，结算时按档位一核对就"货不够"，
     * 于是附魔书怎么点都卖不掉；两本不同的书还会被并成一摞、其中一本的附魔直接丢。
     */
    private int stage(int target, class_1799 ref, int want) {
        // 实现放在 MarketMerchant.stageInto：成交后的自动续摆走的是同一份代码，口径不会跑偏
        return MarketMerchant.stageInto((class_1728) (Object) this, target, ref, want);
    }

}
