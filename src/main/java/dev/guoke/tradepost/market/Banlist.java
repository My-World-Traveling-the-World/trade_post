package dev.guoke.tradepost.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 黑名单：太"特殊"的物品不参与贸易（既不收购，也不会被产业当成货卖）。
 *
 * <p>位置 {@code <config>/tradepost/market/blacklist.json}，改完 {@code /tradepost reload} 生效。
 * <ul>
 *   <li>{@code items}      具体物品 id，精确匹配</li>
 *   <li>{@code tags}       整个标签（写成 {@code #c:tools} 或 {@code c:tools} 都行）</li>
 *   <li>{@code keywords}   按 id 片段匹配（{@code dragon} 会命中 {@code xxx:dragon_tooth_knife}）</li>
 *   <li>{@code namespaces} 整个模组一刀切</li>
 * </ul>
 *
 * <p><b>手写价永远优先</b>：{@code values.json} 里定了价的物品不受黑名单影响 —— 手写价就是白名单。
 * 所以想拉黑某个原版物品，先把它从 values.json 删掉。
 */
public final class Banlist {

    public String _readme = "太特殊的物品不参与贸易。items=具体 id；tags=整个标签（#c:tools）；"
            + "keywords=按 id 片段匹配（dragon）；namespaces=整个模组。"
            + "allow=白名单，写在这里的具体 id 不受 keywords/namespaces 影响（用来救回被误伤的功能方块）。"
            + "values.json 里手写价的物品也不受黑名单影响。";

    public List<String> items = new ArrayList<>();
    public List<String> tags = new ArrayList<>();
    public List<String> keywords = new ArrayList<>();
    public List<String> namespaces = new ArrayList<>();
    /** 正则（大小写不敏感，对 id 的路径部分做 find）：比 keywords 精确，用来封"一整族后缀"，
     *  例如 {@code _slab$} 只封台阶，不会误伤 honey_glazed_ham 这种菜名。 */
    public List<String> patterns = new ArrayList<>();

    /** 编译好的正则缓存（transient → 不会被写进 json）。 */
    private transient List<java.util.regex.Pattern> compiled;

    /** 白名单：精确 id，优先于 keywords / namespaces / tags（items 仍然压倒一切）。 */
    public List<String> allow = new ArrayList<>();

    /** 稀有及以上、又没有任何标签价/手写价的物品，直接不参与贸易（防止"0.05 绿宝石卖神话物品"）。 */
    public Boolean banRareUnpriced = Boolean.TRUE;

    private static volatile Banlist CURRENT = defaults();

    /** 默认黑名单：装备/工具/容器/唱片按标签拦，Boss 掉落与任务道具按关键词拦。 */
    public static Banlist defaults() {
        Banlist b = new Banlist();
        b.tags.addAll(List.of(
                "#c:tools",                       // 各模组的工具（斧镐锹锄刀）
                "#c:armors",                      // 护甲
                "#minecraft:swords",
                "#minecraft:pickaxes",
                "#minecraft:axes",
                "#minecraft:shovels",
                "#minecraft:hoes",
                "#minecraft:trimmable_armor",
                "#minecraft:head_armor",
                "#minecraft:chest_armor",
                "#minecraft:leg_armor",
                "#minecraft:foot_armor",
                "#c:enchantable/durability",      // 有耐久的装备类，普遍是"一件一件"的特殊货
                "#c:buckets",
                "#c:music_discs",
                "#minecraft:bundles"
        ));
        b.keywords.addAll(List.of(
                "dragon", "wither", "nether_star", "totem", "artifact", "relic",
                "spawn_egg", "_shard", "_heart", "_core", "_rune", "_sigil", "_soul",
                "_staff", "_wand", "_scepter", "_charm", "_amulet", "_talisman",
                "_bucket", "_on_a_stick", "_elytra", "_shell_knife",
                // —— 装饰方块：家具/软装/挂饰，纯摆设，摆上货架没人买 ——
                "_carpet", "_rug", "rug_", "curtain", "tapestry", "chandelier",
                "vase", "poster", "_painting", "painting_", "picture_frame", "walldecor",
                "plushie", "_trophy", "_statue", "flower_pot", "planter",
                "_chair", "sofa", "_stool", "_desk", "_table", "cabinet", "wardrobe", "nightstand",
                "_candle", "candlestick", "_banner", "_pennant", "_sign",
                // —— 建筑装饰变体：台阶/楼梯/栅栏/门窗/墙/按钮/树叶/灯/雕纹/磨制，只是同一块石头的花样 ——
                "_slab", "stairs", "fence", "_door", "trapdoor", "_button", "pressure_plate",
                "_lamp", "_pane", "terracotta", "concrete", "glazed_terracotta",
                "stained_glass", "chiseled", "polished", "pillar", "mosaic", "shingles",
                "_tiles", "cut_copper", "_awning", "pergola", "trellis", "_bench", "_shelf", "_cabinet"
        ));
        // —— 建筑装饰变体（正则精确版）：整族后缀一刀切，不误伤同名菜/工具 ——
        b.patterns.addAll(List.of(
                "_slab$", "_stairs$", "_wall$", "_fence$", "_fence_gate$", "_door$", "_trapdoor$",
                "_button$", "_pressure_plate$", "_lamp$", "_pane$", "_carpet$", "_banner$",
                "_sign$", "_terracotta$", "_concrete(_powder)?$", "_glazed_terracotta$", "_stained_glass$",
                "_shingles?$", "_tiles?$", "^chiseled_", "_chiseled$", "^polished_", "_polished$",
                "_pillar$", "_mosaic$", "_awning$", "_pergola$", "_trellis$", "_bench$", "_shelf$",
                "_cabinet$", "_trim$", "_post$",
                // —— 不是货的"图标/占位物"：模组拿来当 logo 的漂浮方块、compat 模组的虚空占位物 ——
                // biomeswevegone:bwg_logo 就是一块悬浮的 logo；compatdelight:void_* 是"某模组没装"时的替身物品。
                "_logo$", "^void_"
        ));
        // 纯装饰/纯摆设的模组一刀切（家具店、玩偶、菜品展示盘之类）
        b.namespaces.addAll(List.of(
                "cocricot",          // 日式家具与街景装饰，4000+ 件
                "rojiuramod",        // 窗户/管道/围墙等街景装饰
                "beautify",          // 画框/百叶窗/小灯
                "accents",           // 帽子/武士刀摆件
                "kaleidoscope_doll", // 玩偶摆件
                "displaydelight",    // 菜品展示盘
                "realisticdining"    // 拟真餐饮：切菜板/炒锅/餐具，整套不进贸易
        ));
        // 白名单：名字里带 "_table" 但是干活用的台子
        b.allow.addAll(List.of(
                "minecraft:crafting_table", "minecraft:enchanting_table",
                "minecraft:smithing_table", "minecraft:fletching_table",
                "minecraft:cartography_table",
                "tide:angling_table", "automobility:auto_mechanic_table",
                "supplementaries:turn_table",
                // —— 点名救回：被 #c:tools / dragon 关键词误伤的功能货 ——
                "supplementaries:cannon", "supplementaries:cannonball",
                "supplementaries:cannon_boat", "supplementaries:cannon_boat_bamboo",
                "smallships:cannon", "immersive_aircraft:rotary_cannon",
                "grapplemod:grappling_hook", "toffys_hooks:grappling_hook",
                "wings_ac:fairy_dust", "wings_ac:fairy_dust_ore", "wings_ac:sky_is_not_the_limit",
            "block_placer", "minecraft:vault",
                "things:hardening_catalyst", "automobility:crowbar",
                "ends_delight:dragon_tooth_knife", "tide:blazing_fishing_rod"
        ));
        return b;
    }

    /** 稀有/史诗、又没有标签价的物品是否禁止交易。默认禁止。 */
    public static boolean banRareUnpriced() {
        Banlist b = CURRENT;
        return b == null || !Boolean.FALSE.equals(b.banRareUnpriced);
    }

    public static Banlist current() {
        return CURRENT;
    }

    public static void set(Banlist b) {
        CURRENT = (b == null) ? defaults() : b;
    }

    /** 编译 {@link #patterns}，结果缓存在实例上；配置换新实例时自然失效。 */
    public List<java.util.regex.Pattern> compiledPatterns() {
        List<java.util.regex.Pattern> out = compiled;
        if (out != null) return out;
        out = new ArrayList<>();
        if (patterns != null) {
            for (String p : patterns) {
                if (p == null || p.isBlank()) continue;
                try {
                    out.add(java.util.regex.Pattern.compile(p, java.util.regex.Pattern.CASE_INSENSITIVE));
                } catch (Throwable ignored) {
                    // 写错的正则跳过，不影响其它条目
                }
            }
        }
        compiled = out;
        return out;
    }

    /** 具体物品是否被拉黑（标签要另外看 {@link #tagBanned(String)}）。 */
    public static boolean isBanned(String id) {
        if (id == null || id.isEmpty() || id.startsWith("#")) return false;
        Banlist b = CURRENT;
        if (b == null) return false;
        if (b.items != null && b.items.contains(id)) return true;
        if (b.allow != null && b.allow.contains(id)) return false;

        String ns = id;
        String path = id;
        int c = id.indexOf(':');
        if (c > 0) {
            ns = id.substring(0, c);
            path = id.substring(c + 1);
        }
        if (b.namespaces != null) {
            for (String n : b.namespaces) {
                if (n != null && n.equalsIgnoreCase(ns)) return true;
            }
        }
        if (b.keywords != null && !b.keywords.isEmpty()) {
            String p = path.toLowerCase(Locale.ROOT);
            for (String k : b.keywords) {
                if (k == null || k.isEmpty()) continue;
                if (p.contains(k.toLowerCase(Locale.ROOT))) return true;
            }
        }
        for (java.util.regex.Pattern p : b.compiledPatterns()) {
            if (p.matcher(path).find()) return true;
        }
        return false;
    }

    /** 整个标签是否被拉黑。{@code "#c:tools"} 与 {@code "c:tools"} 等价。 */
    public static boolean tagBanned(String entry) {
        if (entry == null || entry.isEmpty()) return false;
        Banlist b = CURRENT;
        if (b == null || b.tags == null) return false;
        String key = entry.startsWith("#") ? entry.substring(1) : entry;
        for (String t : b.tags) {
            if (t == null || t.isEmpty()) continue;
            String tt = t.startsWith("#") ? t.substring(1) : t;
            if (tt.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    /** 从 config 目录读；文件不存在返回 false 并保持内置默认。 */
    public static boolean load(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) return false;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            if (sb.toString().isBlank()) return false;
            Banlist b = new Gson().fromJson(sb.toString(), Banlist.class);
            if (b == null) return false;
            if (b.items == null) b.items = new ArrayList<>();
            if (b.tags == null) b.tags = new ArrayList<>();
            if (b.keywords == null) b.keywords = new ArrayList<>();
            if (b.namespaces == null) b.namespaces = new ArrayList<>();
            if (b.allow == null) b.allow = new ArrayList<>();
            if (b.patterns == null) b.patterns = new ArrayList<>();
            if (b.tags.isEmpty() && b.keywords.isEmpty() && b.items.isEmpty()
                    && b.namespaces.isEmpty() && b.patterns.isEmpty()) {
                // 空文件/只写了 _readme：当成"没配置"，仍用内置默认，免得全包物品突然都能交易
                return false;
            }
            CURRENT = b;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 文件不存在或 0 字节时写出默认内容，返回是否写了。 */
    public static boolean exportDefault(Path file) {
        try {
            if (file == null) return false;
            if (Files.isRegularFile(file) && Files.size(file) > 0) return false;
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(defaults()));
                w.write('\n');
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
