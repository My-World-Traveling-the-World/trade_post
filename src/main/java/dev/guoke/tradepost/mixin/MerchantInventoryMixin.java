package dev.guoke.tradepost.mixin;

import dev.guoke.tradepost.market.Pay;
import dev.guoke.tradepost.mc.MarketMerchant;
import net.minecraft.class_1725;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_1915;
import net.minecraft.class_1916;
import net.minecraft.class_2371;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让成品格按"价值"点亮。
 *
 * <p>原版 {@code method_7645()} 只在 {@code MerchantOffers.method_8267(槽0, 槽1, 索引)}
 * 找到"完全一致"的报价时才把成品放进 2 号槽 —— 摆了 72 颗散绿宝石去买"8 块"的货，
 * 它会认为没有任何报价匹配，成品格空着，你连点都点不了。
 * 这里在它跑完后补一刀：价值够就照样把成品亮出来。
 */
@Mixin(class_1725.class)
public abstract class MerchantInventoryMixin {

    @Shadow
    @Final
    private class_2371<class_1799> field_7845;

    @Shadow
    private class_1914 field_7843;

    @Shadow
    private int field_7842;

    @Shadow
    @Final
    private class_1915 field_7844;

    @Shadow
    public abstract class_1799 method_5438(int slot);

    @Shadow
    public abstract void method_5447(int slot, class_1799 stack);

    /**
     * 商人容器的单格上限（原版 {@code class_1263.method_5444()} 默认 64）。
     *
     * <p>{@code class_1725.method_5447} 写格子时会 {@code stack.method_58408(min(这上面, stack.getMaxCount()))}
     * 把数量夹回去 —— 不放开这里，236 颗的货款在结果格/天平均只显示 64。
     * 配合 {@code Pay.emeralds()} 给那摞挂的 max_stack_size 组件，才是一整套。
     */
    public int method_5444() {
        return 9999;
    }

    /**
     * 一格的容量（原版继承自 {@code class_1263} 的 default：
     * {@code min(容器上限, 这一摞自己的上限)}）。
     *
     * <p>{@code class_1725.method_5447} 写完格子会用这个方法把数量夹回去；只放开
     * {@link #method_5444()} 不够 —— 普通绿宝石堆自己的上限是 64，夹完还是 64。
     * 这里直接返回 9999，天平的 0/1 号格与结果格就能原样摆 1620 颗的整摞价钱。
     * 只作用于商人容器，玩家背包不受影响（背包还是一格 64）。
     */
    public int method_58350(class_1799 stack) {
        return 9999;
    }

    /**
     * 天平上这件货有多少"对得上报价"的。附件魔书必须真的是同一本（附魔 + 等级一致），
     * 否则拿一本节肢杀手去点"经验修补"那行，就能按高价把便宜书卖掉。
     */
    private static int count(class_1799 stack, class_1799 want) {
        if (stack == null || stack.method_7960() || want == null || want.method_7960()) return 0;
        if (stack.method_7909() != want.method_7909()) return 0;
        if ("minecraft:enchanted_book".equals(dev.guoke.tradepost.mc.Bridge.itemId(want))) {
            String a = dev.guoke.tradepost.mc.Bridge.bookCode(stack);
            String b = dev.guoke.tradepost.mc.Bridge.bookCode(want);
            if (a == null || !a.equals(b)) return 0;
        }
        return stack.method_7947();
    }

    /**
     * 附加功能：天平上放了货就自动认出行来（手动拖、shift 都算），不用先点列表。
     *
     * <p>只改"选中第几行"这个下标，后面原版的 tick 和我们自己的 showByValue 照旧跑；
     * 玩家手点的那一行如果确实收这堆货，{@code MarketMerchant#autoRowForScale} 会返回 -1，不动它。
     */
    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.MERCHANT_INV_TICK, at = @At("HEAD"))
    private void tradepost$autoPickRow(CallbackInfo ci) {
        try {
            if (!(this.field_7844 instanceof MarketMerchant mm)) return;
            int idx = mm.autoRowForScale((class_1725) (Object) this, this.field_7842);
            if (idx >= 0 && idx != this.field_7842) this.field_7842 = idx;
        } catch (Throwable ignored) {
            // 认不出来就当没这功能，手点行照旧
        }
    }

    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.MERCHANT_INV_TICK, at = @At("TAIL"))
    private void tradepost$showByValue(CallbackInfo ci) {
        class_1916 offers = this.field_7844.method_8264();
        if (offers == null || this.field_7842 < 0 || this.field_7842 >= offers.size()) return;
        class_1914 offer = offers.get(this.field_7842);
        if (offer == null) return;
        if (offer.method_19279() < 0 && Pay.price(offer) == 0) {
            // 卖页：天平上货摆够了就把钱摆进结果格。钱就是一摞绿宝石、数量 = 货款，
            // 跟列表上同一个样子（不再拆成"块 + 零头"两格）
            // 注意：这里不能因为"原版已经认可"就放手 —— 原版只把报价里的成本栈（一颗占位绿宝石）
            // 摆进结果格，玩家点走就只拿到 1 颗。钱一定要换成真货款。
            net.minecraft.class_9306 cost = offer.method_57556();
            if (cost == null) return;
            net.minecraft.class_1799 costStack = cost.comp_2427();
            int want = Math.max(1, cost.comp_2425());
            int have = count(method_5438(0), costStack) + count(method_5438(1), costStack);
            if (have < want) return;
            if (offer.method_8255()) return;                    // 售罄：别再亮成品格（界面上那行会画红叉）
            if (this.field_7844 instanceof MarketMerchant mm
                    && !mm.canPay(-offer.method_19279())) return; // 村子银库掏不出这笔钱
            this.field_7843 = offer;
            // 结果格摆的就是报价里的那摞钱（数量 = 货款），原版照数画
            class_1799 money = offer.method_18019().method_7972();
            if (money.method_7960()) return;
            // 结果格内容没变就别重摆：连续卖时 update 反复进来，每次都 method_5447(2, 新栈)
            // 会让客户端结果格/钱闪一下。内容相同直接跳过。
            class_1799 cur2 = method_5438(2);
            if (!cur2.method_7960() && cur2.method_7909() == money.method_7909()
                    && cur2.method_7947() == money.method_7947()) {
                return;
            }
            method_5447(2, money);
            this.field_7844.method_8258(method_5438(2));
            return;
        }
        if (this.field_7843 != null) return;                   // 原版已经认可这组钱，不动
        if (offer.method_8255()) return;                        // 已售罄
        int have = Pay.value(method_5438(0)) + Pay.value(method_5438(1));
        int need = Pay.price(offer, this.field_7844 instanceof MarketMerchant);
        if (need <= 0 || have < need) return;
        this.field_7843 = offer;
        method_5447(2, offer.method_18019().method_7972());
        this.field_7844.method_8258(method_5438(2));
    }
}
