package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/** 客户端 → 服务端：以物易物换秘宝（payload 只带结果物品 id，成本由服务端现算，防改包）。 */
public record TradePayload(String itemId) implements class_8710 {

    public static final class_8710.class_9154<TradePayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:relic"));

    public static final class_9139<class_9129, TradePayload> CODEC = class_9139.method_56437(
            (class_9129 buf, TradePayload value) -> buf.method_10814(value.itemId()),
            (class_9129 buf) -> new TradePayload(buf.method_19772()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
