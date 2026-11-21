package dev.guoke.tradepost.market;

/** 村庄库存的只读视图，由存档侧实现；纯逻辑层通过它拿库存，不依赖 Minecraft。 */
public interface StockView {

    /** 该村该物品当前还剩多少件 */
    double stockOf(String itemId);

    /** 该村当前等级（1..5）；档位货的准入用。默认 5 = 不挡，方便离线测试。 */
    default int level() {
        return 5;
    }
}
