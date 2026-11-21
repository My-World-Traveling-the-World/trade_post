package dev.guoke.tradepost.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * 珍馐与酒水：料理不该按"材料成本 × 折扣"算，那是工业品的逻辑。
 * 菜和酒的价值来自「材料难找 + 工序复杂 + 会坏会占背包」，所以给一个溢价倍率，
 * 并按关键词分等级（普通菜 → 精致菜 → 酒 → 陈酿）。
 *
 * 只有"靠配方价定出来的物品"会走这条规则 —— 手写价和标签价的货不受影响，
 * 所以小麦还是 0.3、铁锭还是 1.0，只有做出来的菜会贵起来。
 */
public final class Cuisine {

    private Cuisine() {
    }

    /** 一个等级：命中任意关键词就按这个倍率（多档命中取最高） */
    public static final class Tier {
        public String name = "";
        public double mult = 1.6;
        /** 保底价：材料再便宜（一碗汤 = 水 + 两棵菜），做出来的菜也不该只值 0.2 */
        public double min = 1.0;
        public List<String> keywords = new ArrayList<>();

        public String label() {
            return name == null || name.isEmpty() ? ("×" + mult) : name;
        }
    }

    public static final class Config {
        public String _readme = "菜与酒的溢价：只作用于按配方成本定价的物品；改完 /tradepost reload 生效";
        /** 这些命名空间下做出来的东西默认算料理（找不到关键词时的兜底） */
        public List<String> namespaces = new ArrayList<>();
        /** 等级表，从普通到顶级 */
        public List<Tier> tiers = new ArrayList<>();
        /** 每多一格材料加成（3 格以上才计） */
        public double slotBonus = 0.20;
        /** 材料里有"贵货"（单价 ≥ rarePrice）时的额外加成 */
        public double ingredientBonus = 0.35;
        public double rarePrice = 6.0;
        /** 倍率上限，防止某条链条算出天价 */
        public double max = 3.2;
        /**
         * 物件词：物品名里带这些词的，就算"能吃"也不算菜。
         * 只作用于非料理命名空间（料理模组自己的菜不受影响）——
         * 否则 tide:enchanted_golden_apple_bobber 这种"附魔金苹果浮漂"会命中宴席档关键词 golden，
         * 一个钓鱼浮漂被按宴席菜算成 3.5 倍价（1324 绿宝石）。
         */
        public List<String> excludeWords = new ArrayList<>();
    }

    private static volatile Config current = defaults();

    public static Config current() {
        Config c = current;
        return c == null ? defaults() : c;
    }

    public static void set(Config c) {
        current = c == null ? defaults() : c;
    }

    public static Config defaults() {
        Config c = new Config();
        c.namespaces = new ArrayList<>(Arrays.asList(
                "kaleidoscope_cookery", "kaleidoscope_tavern", "farmersdelight", "ends_delight",
                "compatdelight", "ubesdelight", "brewinandchewin", "vinery", "brewery",
                "candlelight", "delightful", "miners_delight", "culturaldelights", "oceansdelight",
                "nethersdelight", "corn_delight", "cavedelight", "rusticdelight", "expandeddelight"));

        c.tiers = new ArrayList<>();
        c.tiers.add(tier("酒水", 2.2, 4.0, "wine", "beer", "ale", "cider", "mead", "sake", "soju",
                "whisky", "whiskey", "vodka", "rum", "brandy", "cocktail", "liquor", "champagne",
                "cognac", "tequila", "gin_", "moonshine", "schnapps", "liqueur"));
        c.tiers.add(tier("陈酿", 3.0, 12.0, "aged", "vintage", "reserve", "grand", "premium",
                "masterwork", "legendary", "century"));
        c.tiers.add(tier("宴席", 2.6, 8.0, "feast", "banquet", "platter", "tower", "deluxe",
                "royal", "golden", "dragon", "stuffed", "whole_"));
        c.tiers.add(tier("精致料理", 2.0, 3.5, "soup", "stew", "broth", "curry", "noodle", "ramen",
                "dumpling", "roast", "steak", "pie", "cake", "pudding", "salad", "skewer",
                "sandwich", "burger", "sushi", "tempura", "hotpot", "casserole", "gratin"));
        c.tiers.add(tier("家常菜", 1.6, 1.8, "cooked", "baked", "fried", "grilled", "smoked",
                "roasted", "steamed", "braised", "stirfried", "stir_fried", "seasoned", "pickled",
                "juice", "tea", "coffee", "smoothie", "jam", "honey_bread", "toast", "porridge"));
        // 物件词：钓具、工具这些"看起来像吃的"（金苹果浮漂）不许进菜价
        c.excludeWords = new ArrayList<>(Arrays.asList(
                "bobber", "bait", "fishhook", "fish_hook", "lure", "reel", "tackle", "fishing_rod",
                "seed", "sapling", "spawn_egg", "bucket", "banner", "sign", "boat", "minecart",
                "boots", "leggings", "chestplate", "helmet", "sword", "pickaxe", "shovel", "hoe",
                "shield", "totem", "arrow", "bolt", "bullet", "ingot", "nugget", "block", "ore"));
        return c;
    }

    private static Tier tier(String name, double mult, double min, String... kws) {
        Tier t = new Tier();
        t.name = name;
        t.mult = mult;
        t.min = min;
        t.keywords = new ArrayList<>(Arrays.asList(kws));
        return t;
    }

    /** 物品是不是料理模组出的（不看关键词）—— 家具方块也常在这些命名空间里，所以还要配合"能不能吃" */
    public static boolean isCuisineNamespace(String itemId) {
        if (itemId == null) return false;
        int i = itemId.indexOf(':');
        String ns = i < 0 ? itemId : itemId.substring(0, i);
        for (String n : current().namespaces) {
            if (n != null && n.equalsIgnoreCase(ns)) return true;
        }
        return false;
    }

    /** 这件东西算不算"菜/酒"：先看命名空间（料理模组），再看物品名里的关键词 */
    public static boolean isCuisine(String itemId, List<List<String>> slots) {
        if (itemId == null) return false;
        Config c = current();
        String ns = itemId;
        String path = itemId;
        int i = itemId.indexOf(':');
        if (i >= 0) {
            ns = itemId.substring(0, i);
            path = itemId.substring(i + 1);
        }
        for (String n : c.namespaces) {
            if (n != null && n.equalsIgnoreCase(ns)) return true;
        }
        String p = path.toLowerCase(Locale.ROOT);
        if (excluded(c, p)) return false;
        for (Tier t : c.tiers) {
            for (String k : t.keywords) {
                if (k != null && !k.isEmpty() && p.contains(k.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    /** 名字里带物件词（浮漂、鱼钩、靴子…）的一律不算菜 —— 只对非料理命名空间生效 */
    private static boolean excluded(Config c, String path) {
        if (c == null || c.excludeWords == null) return false;
        for (String w : c.excludeWords) {
            if (w != null && !w.isEmpty() && path.contains(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** 是不是酒水类（等级名带"酒"的档位命中关键词）—— 只有酒有品质/年份这种额外信息 */
    public static boolean isDrink(String itemId) {
        if (itemId == null) return false;
        String path = itemId;
        int i = itemId.indexOf(':');
        if (i >= 0) path = itemId.substring(i + 1);
        String p = path.toLowerCase(Locale.ROOT);
        for (Tier t : current().tiers) {
            if (t.name == null || !t.name.contains("酒")) continue;
            for (String k : t.keywords) {
                if (k != null && !k.isEmpty() && p.contains(k.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    /**
     * 溢价倍率：等级倍率 + 工序加成 + 贵材料加成，封顶 max。
     * 不是料理的返回 0，调用方改用工业品的折扣。
     */
    public static double bonus(String itemId, List<List<String>> slots, ToDoubleFunction<String> priceOf) {
        if (!isCuisine(itemId, slots)) return 0;
        Config c = current();
        String path = itemId;
        int i = itemId.indexOf(':');
        if (i >= 0) path = itemId.substring(i + 1);
        String p = path.toLowerCase(Locale.ROOT);

        double best = 1.6;
        for (Tier t : c.tiers) {
            for (String k : t.keywords) {
                if (k != null && !k.isEmpty() && p.contains(k.toLowerCase(Locale.ROOT))) {
                    best = Math.max(best, t.mult);
                    break;
                }
            }
        }

        int n = slots == null ? 0 : slots.size();
        if (n > 2) best += c.slotBonus * (n - 2);

        double dearest = 0;
        if (slots != null && priceOf != null) {
            for (List<String> slot : slots) {
                if (slot == null) continue;
                for (String id : slot) {
                    double v = priceOf.applyAsDouble(id);
                    if (v > dearest) dearest = v;
                }
            }
        }
        if (dearest >= c.rarePrice) best += c.ingredientBonus;

        return Math.min(c.max, Math.max(1.0, best));
    }

    /**
     * 保底价：一碗汤的材料可能只值 0.15，但"做菜"本身是劳动 + 稀有食材的奖励，
     * 所以每种等级都有自己的地板价（家常菜 1.0 / 精致 2.5 / 酒 3.0 / 宴席 6.0 / 陈酿 8.0）。
     */
    public static double minPrice(String itemId, List<List<String>> slots) {
        if (!isCuisine(itemId, slots)) return 0;
        Config c = current();
        String path = itemId;
        int i = itemId == null ? -1 : itemId.indexOf(':');
        if (i >= 0) path = itemId.substring(i + 1);
        String p = path.toLowerCase(Locale.ROOT);
        double best = 2.0;
        for (Tier t : c.tiers) {
            for (String k : t.keywords) {
                if (k != null && !k.isEmpty() && p.contains(k.toLowerCase(Locale.ROOT))) {
                    if (t.min > best) best = t.min;
                    break;
                }
            }
        }
        return best;
    }

    /** 菜单上显示的等级名（"酒水"/"精致料理"…），不是料理返回空串 */
    public static String grade(String itemId, List<List<String>> slots) {
        if (!isCuisine(itemId, slots)) return "";
        Config c = current();
        String path = itemId;
        int i = itemId == null ? -1 : itemId.indexOf(':');
        if (i >= 0) path = itemId.substring(i + 1);
        String p = path.toLowerCase(Locale.ROOT);
        String best = "家常菜";
        double bm = 0;
        for (Tier t : c.tiers) {
            for (String k : t.keywords) {
                if (k != null && !k.isEmpty() && p.contains(k.toLowerCase(Locale.ROOT))) {
                    if (t.mult > bm) {
                        bm = t.mult;
                        best = t.label();
                    }
                    break;
                }
            }
        }
        return best;
    }

    // —— 配置文件 ——

    public static Config load(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                current = defaults();
                return current;
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                Config parsed = new Gson().fromJson(o, Config.class);
                if (parsed == null) {
                    current = defaults();
                    return current;
                }
                Config d = defaults();
                if (parsed.namespaces == null || parsed.namespaces.isEmpty()) parsed.namespaces = d.namespaces;
                if (parsed.tiers == null || parsed.tiers.isEmpty()) parsed.tiers = d.tiers;
                // 老配置里没有这个键 → Gson 建对象时字段初始化成了空表，不是 null；空表也当"没配"处理
                if (parsed.excludeWords == null || parsed.excludeWords.isEmpty()) parsed.excludeWords = d.excludeWords;
                parsed.slotBonus = clamp(parsed.slotBonus, 0, 1);
                parsed.ingredientBonus = clamp(parsed.ingredientBonus, 0, 2);
                parsed.rarePrice = clamp(parsed.rarePrice, 0.5, 200);
                parsed.max = clamp(parsed.max, 1.0, 8.0);
                current = parsed;
                return parsed;
            }
        } catch (Exception e) {
            current = defaults();
            return current;
        }
    }

    public static void exportDefault(Path file) throws IOException {
        if (file == null) return;
        if (Files.isRegularFile(file) && Files.size(file) > 0) return;
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(g.toJson(defaults()));
        }
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
