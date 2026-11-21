package dev.guoke.tradepost.market;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 哪些物品算"酒水"（会带 kaleidoscope_tavern:brew_level 品质组件）。
 *
 * <p>为什么不能靠"这一个栈上有没有 brew_level"来判断：酿造时品质是一瓶一瓶加上去的，
 * 物品的**默认组件**里没有它 —— 用空白栈去探永远是 false（DRINK_FLOOR 就是这么失效的）。
 * 所以这里两路一起上：
 * <ul>
 *   <li>静态名单：酒馆模组当前版本的全部酒/果汁/鸡尾酒 id（下面是 1.2.0.1 的全表）；</li>
 *   <li>运行期登记：任何时候真见到一个带 brew_level 的栈，就把它的 id 记进来
 *       （模组以后加新酒，玩家第一次拿到就自动认）。</li>
 * </ul>
 * 名单只影响"有没有品质档"和"底价地板"，不影响别的定价逻辑。
 */
public final class Drinks {

    private Drinks() {}

    /** 酒馆模组 1.2.0.1 里的全部酒水（lang 文件里 block.kaleidoscope_tavern.* 挑出来的） */
    private static final Set<String> KNOWN = Set.of(
            // 葡萄酒 / 烈酒
            "wine", "plum_wine", "sakura_wine", "honey_wine", "ice_wine", "sweet_berry_wine",
            "champagne", "brandy", "carignan", "sherry", "rum", "vodka", "vinegar",
            // 白葡萄酒
            "riesling_dry_white", "sauvignon_blanc_dry_white", "polaris_sweet_white",
            // 鸡尾酒
            "red_queen", "mother_snow", "luminous_bride", "madame_shexiang", "miners_star",
            "sunset_glow", "nether_special", "sculk_special", "glowflower_brew",
            "bloody_mary", "godfather", "grasshopper", "mojito", "molotov",
            "mystery_cocktail", "screwdriver", "signature_cocktail", "depth_charge",
            // 果汁
            "grape_juice", "green_grape_juice", "gold_grape_juice", "ice_grape_juice",
            "glow_berries_juice", "sweet_berries_juice", "watermelon_juice"
    );

    /** 运行期登记的"真的见过 brew_level"的物品 id */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    /** 见到一个带品质的栈：登记它的物品 id */
    public static void seen(String itemId) {
        if (itemId != null && !itemId.isEmpty()) SEEN.add(itemId);
    }

    /** 这个 id 算不算酒水 */
    public static boolean is(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (SEEN.contains(itemId)) return true;
        int c = itemId.indexOf(':');
        String ns = c < 0 ? "minecraft" : itemId.substring(0, c);
        String path = c < 0 ? itemId : itemId.substring(c + 1);
        if (!"kaleidoscope_tavern".equals(ns)) return false;
        if (KNOWN.contains(path)) return true;
        // 模组以后新加的酒：按命名习惯兜一下
        return path.endsWith("_wine") || path.endsWith("_juice") || path.endsWith("_brew")
                || path.endsWith("_cocktail") || path.endsWith("_special");
    }
}
