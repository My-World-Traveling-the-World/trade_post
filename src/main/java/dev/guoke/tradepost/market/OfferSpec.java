package dev.guoke.tradepost.market;

/**
 * 一条交易报价（纯数据，不含任何 Minecraft 类型）。
 * 语义：玩家付出 giveItem x giveCount（+ 可选的 secondItem x secondCount），换回 getItem x getCount。
 * 第二成本槽是给秘宝用的：原版 TradeOffer 本来就支持两格成本，不用改界面。
 */
public record OfferSpec(String giveItem, int giveCount, String getItem, int getCount, int maxUses,
                        String secondItem, int secondCount) {

    public OfferSpec(String giveItem, int giveCount, String getItem, int getCount, int maxUses) {
        this(giveItem, giveCount, getItem, getCount, maxUses, null, 0);
    }

    public boolean hasSecond() {
        return secondItem != null && secondCount > 0;
    }

    public boolean givesEmeralds() {
        return giveItem.equals("minecraft:emerald");
    }

    @Override
    public String toString() {
        String s = giveCount + "x " + shortName(giveItem);
        if (hasSecond()) s += " + " + secondCount + "x " + shortName(secondItem);
        return s + "  ->  " + getCount + "x " + shortName(getItem);
    }

    public static String shortName(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }
}
