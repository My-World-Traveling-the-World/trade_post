package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/** 客户端 → 服务端：用纯净绿宝石给村庄捐款换经验。count 是颗数（1~12）。 */
public record DonatePayload(String count) implements class_8710 {

    public static final class_8710.class_9154<DonatePayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:donate"));

    public static final class_9139<class_9129, DonatePayload> CODEC = class_9139.method_56437(
            (class_9129 buf, DonatePayload value) -> buf.method_10814(value.count()),
            (class_9129 buf) -> new DonatePayload(buf.method_19772()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}