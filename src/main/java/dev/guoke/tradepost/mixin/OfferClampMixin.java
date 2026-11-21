package dev.guoke.tradepost.mixin;

import net.minecraft.class_1799;
import net.minecraft.class_1914;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让"一摞绿宝石、数量就是价格"在列表上如实显示。
 *
 * <p>原版 {@code method_19272()}（getAdjustedFirstBuyItem）会把第一成本格的数量
 * {@code clamp(..., 1, 成本栈的最大堆叠数)} 夹到 64，所以往那格写 267 只会显示成 64。
 * 我们自己的报价用<b>负 xp</b> 打标记（原版永远不为负），命中就原样交出成本栈，
 * 界面于是由原版自己把真数字画出来 —— 字号、位置、阴影都跟堆叠数一模一样。
 *
 * <p>这个夹取只影响"显示"和原版自己的匹配；真正的结算走 Pay（按价值），不看这个值。
 */
@Mixin(class_1914.class)
public abstract class OfferClampMixin {

    @Inject(method = dev.guoke.tradepost.compat.VanillaNames.OFFER_FIRST_COST, at = @At("HEAD"), cancellable = true)
    private void tradepost$keepFullCount(CallbackInfoReturnable<class_1799> cir) {
        class_1914 self = (class_1914) (Object) this;
        if (self.method_19279() >= 0) return;             // 原版报价，照旧夹
        class_1799 cost = self.method_57556().comp_2427();
        if (cost == null || cost.method_7960()) return;
        cir.setReturnValue(cost.method_7972());            // 数量原样（= 价格）
    }
}
