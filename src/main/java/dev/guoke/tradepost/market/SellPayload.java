package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/**
 * 客户端 → 服务端：卖出某种货。
 * count <= 0 表示全卖，"*" 表示背包里所有种类的货都卖。
 * level 是该货的品质档（0 = 没有品质组件；-1 = 不分品质、各档都卖）。
 */
public record SellPayload(String itemId, int count, int level) implements class_8710 {

    public SellPayload(String itemId, int count) {
        this(itemId, count, -1);
    }

    public static final class_8710.class_9154<SellPayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:sell"));

    public static final class_9139<class_9129, SellPayload> CODEC = class_9139.method_56437(
            (class_9129 buf, SellPayload value) -> {
                buf.method_10814(value.itemId());
                buf.method_10804(value.count());
                buf.method_10804(value.level());
            },
            (class_9129 buf) -> new SellPayload(buf.method_19772(), buf.method_10816(), buf.method_10816()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
