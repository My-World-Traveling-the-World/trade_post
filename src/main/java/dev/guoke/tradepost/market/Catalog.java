package dev.guoke.tradepost.market;

import java.util.Collections;
import java.util.List;

/**
 * 物品目录（纯逻辑接口）。
 * 作用有二：
 *   1) 把数据里的 "#c:ingots" 这类标签展开成具体物品 id —— 这样整合包里任何模组的金属锭
 *      都能自动进入贸易，不用给每个模组手写一行；
 *   2) 给 values.json 里没登记的（模组）物品一个兜底价，靠 tag_values.json 的标签基价推导。
 * 真正的实现在 MC 层（Bridge.TagCatalog），纯逻辑层只依赖这个接口，方便脱离游戏测试。
 */
public interface Catalog {

    /** 展开一个条目：普通 id 原样返回；"#ns:tag" 返回标签里的所有物品 id。 */
    List<String> expand(String entry);

    /** 兜底价（values.json 没登记时用）；未知返回 0。 */
    double value(String id);

    /** 所有可交易物品（没有标签归属的模组货也能被抽到）；离线实现返回空表。 */
    default List<String> all() {
        return Collections.emptyList();
    }

    /** 菜与酒的池子（配方扫描时收集）；每个村庄从里面抽一两道摆进杂货。 */
    default List<String> cuisines() {
        return Collections.emptyList();
    }

    /** 展开一个类别（配置里写 "@grain" 这种）；没有类别表的实现返回空表。 */
    default List<String> category(String id) {
        return Collections.emptyList();
    }

    /** 稀有度档位 1..4（见 Rarity）；离线实现恒为常见。 */
    default int rarity(String id) {
        return Rarity.COMMON;
    }

    /** 是不是正经的菜/酒（要有中文名、能吃）。离线实现走 Cuisine 关键词判定。 */
    default boolean dish(String id) {
        return Cuisine.isCuisine(id, null);
    }

    /**
     * 村里能不能把货摆出来卖（玩家向它买）。
     * 和 banned 不是一回事：没有中文译名的物品不走货架，但玩家照样能卖给它。
     */
    default boolean canSell(String id) {
        return true;
    }

    /**
     * 全球饱和系数（1.0 = 这种货还没被全世界卖烂；0.05 = 已经泛滥到不值钱）。
     * 只有 MC 层实现（靠存档里的累计卖出量），离线实现恒为 1。
     */
    default double worldIndex(String id) {
        return 1.0;
    }

    /** 是否被黑名单拉黑（太特殊的货不参与贸易）。默认不拉黑，方便离线测试。 */
    default boolean banned(String id) {
        return false;
    }

    /** 空目录：只认具体 id，不做任何兜底。离线测试用。 */
    Catalog NONE = new Catalog() {
        @Override public List<String> expand(String entry) {
            return entry == null || entry.startsWith("#") ? Collections.emptyList() : List.of(entry);
        }
        @Override public double value(String id) {
            return 0.0;
        }
    };
}
