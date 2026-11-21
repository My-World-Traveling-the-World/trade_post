package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/**
 * 客户端 → 服务端：把第二格里的货款收进背包。
 *
 * <p>为什么不靠原版点格子：第二格是我们自己加的槽（原生界面里没有它的位置），
 * 客户端没有这一格、或者同步没送到时，点下去什么都不会发生。所以这一下由我们自己接：
 * 界面在第二格的矩形上拦一次点击，发这个包，服务端把槽里的东西塞给玩家再清空。
 */
public record CollectPayload() implements class_8710 {

    public static final class_8710.class_9154<CollectPayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:collect"));

    public static final class_9139<class_9129, CollectPayload> CODEC = class_9139.method_56437(
            (class_9129 buf, CollectPayload value) -> { },
            (class_9129 buf) -> new CollectPayload());

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
