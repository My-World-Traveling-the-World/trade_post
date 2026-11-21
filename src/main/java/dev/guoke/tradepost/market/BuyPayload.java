package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/** 客户端 → 服务端：买本村在售的货（units = 买几份，一份 = 面板上那一行的件数）。 */
public record BuyPayload(String itemId, int units) implements class_8710 {

    public static final class_8710.class_9154<BuyPayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:buy"));

    public static final class_9139<class_9129, BuyPayload> CODEC = class_9139.method_56437(
            (class_9129 buf, BuyPayload value) -> {
                buf.method_10814(value.itemId());
                buf.method_10804(value.units());
            },
            (class_9129 buf) -> new BuyPayload(buf.method_19772(), buf.method_10816()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
