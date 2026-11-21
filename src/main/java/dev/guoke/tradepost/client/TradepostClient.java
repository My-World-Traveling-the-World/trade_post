package dev.guoke.tradepost.client;

import dev.guoke.tradepost.market.MarketPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_310;
import net.minecraft.class_437;

public class TradepostClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 点钟的那一刻先弹"正在计算价格"：服务端算价要几秒，期间什么都不显示会被当成没反应
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            try {
                if (!world.method_8608()) return net.minecraft.class_1269.field_5811;      // 只在客户端做
                if (!player.method_5715()) return net.minecraft.class_1269.field_5811;     // 要潜行
                if (!dev.guoke.tradepost.mc.Bridge.isBell(world, hit.method_17777())) {
                    return net.minecraft.class_1269.field_5811;
                }
                class_310 mc = class_310.method_1551();
                if (!(mc.field_1755 instanceof LoadingScreen)) {
                    mc.execute(() -> mc.method_1507(new LoadingScreen()));
                }
            } catch (Throwable ignored) {
            }
            return net.minecraft.class_1269.field_5811;
        });
        ClientPlayNetworking.registerGlobalReceiver(MarketPayload.ID, (payload, ctx) -> {
            class_310 mc = ctx.client();
            mc.execute(() -> {
                class_437 cur = mc.field_1755;
                if (cur instanceof MarketScreen ms) {
                    ms.setPayload(payload);
                } else {
                    mc.method_1507(MarketScreen.create(payload));
                }
            });
        });
    }
}
