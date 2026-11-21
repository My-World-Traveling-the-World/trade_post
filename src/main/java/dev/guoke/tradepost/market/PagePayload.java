package dev.guoke.tradepost.market;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

/**
 * 客户端 → 服务端：集市翻页（换买/卖页、换份数）。
 *
 * <p>为什么要发到服务端：原版村民交易里，"左边第几行"这件事两端必须共用同一份报价表
 * （{@code class_1916}）。服务端要按同一行去天平上摆钱摆货，客户端要按同一行画列表。
 * 所以翻页不是客户端自己换一页图纸，而是让服务端重建报价、再用 SetTradeOffers 包推回来。
 */
public record PagePayload(int tab, int qty) implements class_8710 {

    public static final class_8710.class_9154<PagePayload> ID =
            new class_8710.class_9154<>(class_2960.method_60654("tradepost:page"));

    public static final class_9139<class_9129, PagePayload> CODEC = class_9139.method_56437(
            (class_9129 buf, PagePayload value) -> {
                buf.method_10804(value.tab());
                buf.method_10804(value.qty());
            },
            (class_9129 buf) -> new PagePayload(buf.method_10816(), buf.method_10816()));

    @Override
    public class_8710.class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
