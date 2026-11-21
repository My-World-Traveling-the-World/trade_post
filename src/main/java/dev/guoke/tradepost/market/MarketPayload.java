package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/** 服务端 → 客户端：集市价目表（内容为一行 JSON 字符串，省得手搓复合 codec）。 */
public record MarketPayload(String json) implements class_8710 {

    public static final class_8710.class_9154<MarketPayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:board"));

    public static final class_9139<class_9129, MarketPayload> CODEC = class_9139.method_56437(
            (class_9129 buf, MarketPayload value) -> buf.method_10814(value.json()),
            (class_9129 buf) -> new MarketPayload(buf.method_19772()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
