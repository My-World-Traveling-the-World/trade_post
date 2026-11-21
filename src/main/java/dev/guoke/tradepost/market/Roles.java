package dev.guoke.tradepost.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * "用途大类"表 —— 决定每个产业收哪些大类的货，以及物品怎么归类。
 *
 * <p>全部外置在 <b>config/tradepost/market/roles.json</b>，改完 /tradepost reload 生效：
 * <pre>
 *   roles       大类名单
 *   industries  产业 → 它要收的大类（"*" = 全收，荒僻村用）
 *   tags        物品标签 → 大类（c:ingots → ingot）
 *   keywords    id 关键词 → 大类（标签认不出来时的兜底）
 * </pre>
 *
 * 这张表替代了"给每件物品手写一个买家"，所以整合包里模组物品也能自动落位。
 */
public final class Roles {

    public static final String FUEL = "fuel";
    public static final String ORE = "ore";
    public static final String INGOT = "ingot";
    public static final String GEM = "gem";
    public static final String WOOD = "wood";
    public static final String PLANT = "plant";
    public static final String FOOD = "food";
    public static final String FIBER = "fiber";
    public static final String DYE = "dye";
    public static final String GLASS = "glass";
    public static final String BUILT = "built";
    public static final String STUDY = "study";
    public static final String GEAR = "gear";

    public static final List<String> DEFAULT_ROLES = List.of(
            FUEL, ORE, INGOT, GEM, WOOD, PLANT, FOOD, FIBER, DYE, GLASS, BUILT, STUDY, GEAR);

    // ───────────── 外置字段（Gson 直接读写） ─────────────

    public String _readme = "用途大类表。industries = 各产业收哪些大类；tags 把物品标签映射到大类；"
            + "keywords 是标签认不出来时按 id 关键词兜底。星号 * 表示全收（荒僻村）。改完用 /tradepost reload 生效。";

    public List<String> roles = new ArrayList<>(DEFAULT_ROLES);

    public Map<String, List<String>> industries = defaultIndustries();

    public Map<String, String> tags = defaultTags();

    public Map<String, String> keywords = defaultKeywords();

    // ───────────── 单例与读写 ─────────────

    private static volatile Roles CURRENT = new Roles();

    public static Roles current() {
        return CURRENT;
    }

    public static void set(Roles r) {
        CURRENT = r == null ? new Roles() : r.normalized();
    }

    /** 从 roles.json 读；文件不存在/读坏了就用内置默认值（不会因此失去功能） */
    public static Roles load(Path file) {
        Roles r = new Roles();
        if (file == null || !Files.isRegularFile(file)) return r.normalized();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Roles parsed = new Gson().fromJson(in, Roles.class);
            if (parsed != null) r = parsed;
        } catch (Exception ex) {
            // 写坏了就当没改过，退回默认（但要说一声，否则玩家以为改生效了）
            try {
                dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] roles.json 解析失败，已退回内置默认值（看看逗号和引号）", ex);
            } catch (Throwable ignored) {
                // 离线测试环境没有日志器，忽略
            }
        }
        return r.normalized();
    }

    /** 写出默认表（已存在不覆盖；空文件算没写过） */
    public static boolean exportDefault(Path file) {
        if (file == null) return false;
        try {
            if (Files.exists(file) && Files.size(file) > 0) return false;
        } catch (Exception ignored) {
            return false;
        }
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(new Roles()));
            }
            return true;
        } catch (Exception ex) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 写 roles.json 失败", ex);
            return false;
        }
    }

    /** 补齐漏写/写坏的字段，顺便把未知大类名过滤掉 */
    private Roles normalized() {
        if (roles == null || roles.isEmpty()) roles = new ArrayList<>(DEFAULT_ROLES);
        if (industries == null) industries = defaultIndustries();
        if (tags == null) tags = defaultTags();
        if (keywords == null) keywords = defaultKeywords();
        Set<String> known = new HashSet<>(roles);
        Map<String, List<String>> fixed = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : industries.entrySet()) {
            List<String> keep = new ArrayList<>();
            for (String r : e.getValue()) {
                if (r == null) continue;
                r = r.trim();
                if (r.equals("*") || known.contains(r)) keep.add(r);
            }
            fixed.put(e.getKey(), keep);
        }
        industries = fixed;
        return this;
    }

    // ───────────── 查询 ─────────────

    /** 这个产业要收哪些大类（"*" 展开成全部） */
    public List<String> buyersOf(String industryId) {
        if (industryId == null) return List.of();
        List<String> r = industries.get(industryId);
        if (r == null) return industryId.startsWith("auto_") ? forAuto(industryId) : List.of();
        if (r.contains("*")) return roles;
        return r;
    }

    /** 标签属于哪个大类（tag 名不带 #） */
    public String roleForTag(String tag) {
        return tag == null ? null : tags.get(tag);
    }

    /** 按 id 关键词猜大类（标签全都命中不了时的最后一道） */
    public Set<String> guessRoles(String itemId) {
        Set<String> out = new LinkedHashSet<>();
        if (itemId == null) return out;
        String p = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        for (Map.Entry<String, String> e : keywords.entrySet()) {
            if (e.getKey() != null && !e.getKey().isEmpty() && p.contains(e.getKey())) out.add(e.getValue());
        }
        return out;
    }

    /** 自动产业（auto_xxx）按 id 哈希固定领两个大类，让它们也变成某些货的买家 */
    private List<String> forAuto(String industryId) {
        int h = Math.abs(industryId.hashCode());
        if (roles.isEmpty()) return List.of();
        String a = roles.get(h % roles.size());
        String b = roles.get((h / 7 + 3) % roles.size());
        return a.equals(b) ? List.of(a) : List.of(a, b);
    }

    // ───────────── 静态门面（老调用点保持不变） ─────────────

    public static List<String> buyers(String industryId) {
        return CURRENT.buyersOf(industryId);
    }

    public static Set<String> byKeyword(String itemId) {
        return CURRENT.guessRoles(itemId);
    }

    public static Map<String, String> tagMap() {
        return CURRENT.tags;
    }

    // ───────────── 内置默认值 ─────────────

    private static Map<String, List<String>> defaultIndustries() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("farming", List.of(FUEL, FIBER, BUILT));
        m.put("orchard", List.of(BUILT, GLASS, FIBER));
        m.put("herding", List.of(PLANT, FUEL, BUILT));
        m.put("fishing", List.of(FIBER, DYE, FOOD));
        m.put("forestry", List.of(INGOT, FUEL, GEAR));
        m.put("quarrying", List.of(PLANT, WOOD, FIBER));
        m.put("smelting", List.of(ORE, FUEL));
        m.put("coal", List.of(INGOT, GEAR, WOOD));
        m.put("gems", List.of(INGOT, GLASS, GEAR));
        m.put("kiln", List.of(FUEL, GLASS, PLANT));
        m.put("textile", List.of(FIBER, DYE, PLANT));
        m.put("brewing", List.of(PLANT, FOOD, GLASS));
        m.put("commerce", List.of(INGOT, GEM, STUDY));
        m.put("scholar", List.of(DYE, FIBER, GEM));
        m.put("armory", List.of(INGOT, FIBER, FUEL));
        m.put("backwater", List.of("*"));
        return m;
    }

    private static Map<String, String> defaultTags() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("c:coals", FUEL);
        m.put("minecraft:coals", FUEL);
        m.put("c:fuels", FUEL);
        m.put("c:ores", ORE);
        m.put("c:raw_materials", ORE);
        m.put("c:ingots", INGOT);
        m.put("c:nuggets", INGOT);
        m.put("c:dusts", INGOT);
        m.put("c:plates", INGOT);
        m.put("c:gems", GEM);
        m.put("minecraft:logs", WOOD);
        m.put("minecraft:planks", WOOD);
        m.put("c:crops", PLANT);
        m.put("c:seeds", PLANT);
        m.put("c:mushrooms", PLANT);
        m.put("c:foods", FOOD);
        m.put("c:foods/raw_fish", FOOD);
        m.put("c:leathers", FIBER);
        m.put("c:strings", FIBER);
        m.put("minecraft:wool", FIBER);
        m.put("minecraft:feather_equivalents", FIBER);
        m.put("c:dyes", DYE);
        m.put("c:glass_blocks", GLASS);
        m.put("c:glass_panes", GLASS);
        m.put("c:stones", BUILT);
        m.put("minecraft:bookshelf_books", STUDY);
        m.put("c:tools", GEAR);
        m.put("c:rods", GEAR);
        return m;
    }

    private static Map<String, String> defaultKeywords() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("_ore", ORE);
        m.put("raw_", ORE);
        m.put("_scrap", ORE);
        m.put("_ingot", INGOT);
        m.put("_nugget", INGOT);
        m.put("_dust", INGOT);
        m.put("_plate", INGOT);
        m.put("_alloy", INGOT);
        m.put("diamond", GEM);
        m.put("emerald", GEM);
        m.put("lapis", GEM);
        m.put("amethyst", GEM);
        m.put("quartz", GEM);
        m.put("_gem", GEM);
        m.put("_crystal", GEM);
        m.put("_log", WOOD);
        m.put("_plank", WOOD);
        m.put("_wood", WOOD);
        m.put("_stem", WOOD);
        m.put("seed", PLANT);
        m.put("_crop", PLANT);
        m.put("mushroom", PLANT);
        m.put("sapling", PLANT);
        m.put("wheat", PLANT);
        m.put("carrot", PLANT);
        m.put("potato", PLANT);
        m.put("beetroot", PLANT);
        m.put("bread", FOOD);
        m.put("cooked_", FOOD);
        m.put("_stew", FOOD);
        m.put("_soup", FOOD);
        m.put("apple", FOOD);
        m.put("_meat", FOOD);
        m.put("beef", FOOD);
        m.put("porkchop", FOOD);
        m.put("chicken", FOOD);
        m.put("mutton", FOOD);
        m.put("rabbit", FOOD);
        m.put("cod", FOOD);
        m.put("salmon", FOOD);
        m.put("leather", FIBER);
        m.put("_wool", FIBER);
        m.put("_string", FIBER);
        m.put("feather", FIBER);
        m.put("_hide", FIBER);
        m.put("_dye", DYE);
        m.put("ink_sac", DYE);
        m.put("glass", GLASS);
        m.put("terracotta", BUILT);
        m.put("brick", BUILT);
        m.put("stone", BUILT);
        m.put("deepslate", BUILT);
        m.put("cobble", BUILT);
        m.put("concrete", BUILT);
        m.put("andesite", BUILT);
        m.put("tuff", BUILT);
        m.put("_book", STUDY);
        m.put("paper", STUDY);
        m.put("_map", STUDY);
        m.put("scroll", STUDY);
        m.put("sword", GEAR);
        m.put("shield", GEAR);
        m.put("arrow", GEAR);
        m.put("_axe", GEAR);
        m.put("_pickaxe", GEAR);
        m.put("_hoe", GEAR);
        m.put("_helmet", GEAR);
        m.put("_chestplate", GEAR);
        m.put("_leggings", GEAR);
        m.put("_boots", GEAR);
        m.put("_crossbow", GEAR);
        m.put("coal", FUEL);
        m.put("charcoal", FUEL);
        return m;
    }
}
