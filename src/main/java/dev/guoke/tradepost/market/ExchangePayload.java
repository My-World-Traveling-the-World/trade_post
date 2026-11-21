package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/** 客户端 → 服务端：绿宝石 ⇄ 纯净绿宝石 汇兑。dir = "0" 买（90 绿宝石 → 1 纯净）、"1" 卖（1 纯净 → 70 绿宝石）。 */
public record ExchangePayload(String dir) implements class_8710 {

    public static final class_8710.class_9154<ExchangePayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:exchange"));

    public static final class_9139<class_9129, ExchangePayload> CODEC = class_9139.method_56437(
            (class_9129 buf, ExchangePayload value) -> buf.method_10814(value.dir()),
            (class_9129 buf) -> new ExchangePayload(buf.method_19772()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}