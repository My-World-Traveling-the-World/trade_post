package dev.guoke.tradepost.market;

import java.util.Set;

/**
 * 村庄所在生物群系的最小信息：path（"desert"）+ 它所属的标签（"c:is_mountain"…）。
 * 产业权重表按标签写（#c:is_mountain），这样整合包里的模组群系也能自动归类。
 */
public record BiomeInfo(String path, Set<String> tags) {

    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
}
