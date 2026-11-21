package dev.guoke.tradepost.market;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 类别层：把"这种货归哪一类"从产业里拆出来，单独一张表。
 *
 * 一个类别 = 标签优先 + 名称兜底 + 显式排除，三选一命中即算：
 *   tags    "#c:foods/grain" 这类公共标签（模组一般都会打，装新模组自动进类）
 *   names   物品 id 的正则兜底（没打标签的老模组靠它）
 *   exclude 显式踢掉的东西（装饰件、货币、任务物品……）
 *
 * 产业配置里写 "@grain" 就代表这一整类。类别为空（整合包里一件都没命中）时该条目直接跳过，
 * 不生成空货位，也不报错。
 */
public final class Categories {

    /**
     * @param price 这一类的"默认价"：只在这件货没有手写价、没有标签价、也没有配方价时才用。
     *              模组的树苗 / 花 / 树叶 / 石材这类货往往归类清楚却没人给价，过去一律掉到 0.05（等于白送）。
     *              0 = 不给默认价（继续走兜底）。
     */
    public record Cat(String display, List<String> tags, List<String> names, List<String> exclude, double price) {
    }

    private static final String PATH = "/data/tradepost/market/categories.json";

    /** 兜底类别：所有没被任何类别认领的货都归这里，保证"不会凭空消失" */
    public static final String MISC = "misc";

    private final Map<String, Cat> map;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();
    /** 物品 id → 它所属类别的默认价（第一个认领它的类别说了算），首次用到时建一次 */
    private volatile Map<String, Double> priceIndex;

    private Categories(Map<String, Cat> map) {
        this.map = map == null ? Map.of() : map;
    }

    public static Categories empty() {
        return new Categories(Map.of());
    }

    /** dir 为 null 或没有 categories.json 时用 jar 内置的。 */
    public static Categories load(Path dir) {
        try (Reader r = open(dir)) {
            if (r == null) return empty();
            Map<String, Cat> raw = new Gson().fromJson(r, new com.google.gson.reflect.TypeToken<LinkedHashMap<String, Cat>>() {
            }.getType());
            return new Categories(raw);
        } catch (Exception e) {
            return empty();     // 类别表写坏了不许让市场整体失效
        }
    }

    private static Reader open(Path dir) throws IOException {
        if (dir != null) {
            Path f = dir.resolve("categories.json");
            if (Files.isRegularFile(f)) return Files.newBufferedReader(f, StandardCharsets.UTF_8);
        }
        InputStream in = Categories.class.getResourceAsStream(PATH);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set<String> ids() {
        return map.keySet();
    }

    public Cat get(String id) {
        return map.get(id);
    }

    /** 某个类别的默认价；没写或类别不存在返回 0。 */
    public double price(String id) {
        Cat c = id == null ? null : map.get(id);
        return c == null ? 0 : c.price();
    }

    /**
     * 这件货所属类别的默认价（0 = 没有）。
     * 只用来给"别的路都没价"的货兜个底，不覆盖手写价 / 标签价 / 配方价。
     */
    public double priceOf(Catalog cat, String itemId) {
        if (cat == null || itemId == null) return 0;
        Map<String, Double> idx = priceIndex;
        if (idx == null) {
            Map<String, Double> built = new LinkedHashMap<>();
            for (Map.Entry<String, Cat> e : map.entrySet()) {
                if (MISC.equals(e.getKey())) continue;      // 兜底类自己不报价
                double p = e.getValue().price();
                if (p <= 0) continue;
                for (String it : expand(cat, e.getKey())) built.putIfAbsent(it, p);
            }
            priceIndex = idx = built;
        }
        Double v = idx.get(itemId);
        return v == null ? 0 : v;
    }

    /** 类别 → 具体物品 id。结果按类别缓存一次（配置重载会新建实例，缓存自然失效）。 */
    public List<String> expand(Catalog cat, String id) {
        if (cat == null || id == null) return List.of();
        boolean misc = MISC.equals(id);
        Cat c = map.get(id);
        if (c == null && !misc) return List.of();
        List<String> hit = cache.get(id);
        if (hit != null) return hit;
        List<String> built = misc ? buildMisc(cat) : build(cat, c);
        cache.put(id, built);
        return built;
    }

    /**
     * 兜底类：全物品 − 所有类别的并集 − 装饰件 − 黑名单。
     * 不写规则也能保证每件货都有个去处（哪怕归得不准），不会因为漏一条正则就整件消失。
     */
    private List<String> buildMisc(Catalog cat) {
        LinkedHashSet<String> rest = new LinkedHashSet<>(cat.all());
        for (String k : map.keySet()) {
            if (MISC.equals(k)) continue;
            Cat c = map.get(k);
            if (c == null) continue;
            rest.removeAll(expand(cat, k));
        }
        List<String> decor = cat.expand("#tradepost:decor");
        if (!decor.isEmpty()) rest.removeAll(decor);
        List<String> out = new ArrayList<>(rest.size());
        for (String it : rest) {
            if (cat.banned(it)) continue;
            out.add(it);
        }
        return out;
    }

    private static List<String> build(Catalog cat, Cat c) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (c.tags() != null) {
            for (String tag : c.tags()) {
                if (tag == null || tag.isBlank()) continue;
                out.addAll(cat.expand(tag));
            }
        }
        List<Pattern> names = patterns(c.names());
        if (!names.isEmpty()) {
            for (String item : cat.all()) {
                if (anyMatch(names, item)) out.add(item);
            }
        }
        if (out.isEmpty()) return List.of();

        Set<String> drop = new LinkedHashSet<>();
        if (c.exclude() != null) {
            List<Pattern> exNames = new ArrayList<>();
            for (String e : c.exclude()) {
                if (e == null || e.isBlank()) continue;
                if (e.startsWith("#")) drop.addAll(cat.expand(e));
                else exNames.add(compile(e));
            }
            if (!exNames.isEmpty()) {
                for (String item : out) {
                    if (anyMatch(exNames, item)) drop.add(item);
                }
            }
        }
        List<String> list = new ArrayList<>(out.size());
        for (String item : out) {
            if (drop.contains(item)) continue;
            if (cat.banned(item)) continue;
            list.add(item);
        }
        return list;
    }

    private static List<Pattern> patterns(Collection<String> raw) {
        List<Pattern> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            out.add(compile(s));
        }
        return out;
    }

    /** 正则写坏就退化成"包含"，别让一条错配置废掉整个类别。 */
    private static Pattern compile(String s) {
        try {
            return Pattern.compile(s, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE);
        }
    }

    private static boolean anyMatch(List<Pattern> ps, String item) {
        if (item == null) return false;
        for (Pattern p : ps) {
            if (p.matcher(item).find()) return true;
        }
        return false;
    }
}
