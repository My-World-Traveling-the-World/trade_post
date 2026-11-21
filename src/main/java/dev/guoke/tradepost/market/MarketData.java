package dev.guoke.tradepost.market;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 市场数据（纯 Java，不依赖 Minecraft 与 Fabric）。
 *
 * 两个来源，外部优先：
 *   1. 配置文件目录（首次启动由模组自动从 jar 导出，之后玩家自己改）
 *      <config>/tradepost/market/{values.json,tag_values.json,industries/*.json,relics/*.json}
 *   2. jar 内置 /data/tradepost/market/（外部目录缺文件时回落）
 *
 * 外部目录里的 industries/ relics/ 是"目录里有什么就读什么"，不靠 index.json，
 * 所以丢一个文件进去就算加了一种产业，删掉就算取消，不需要改任何清单。
 */
public final class MarketData {

    public static final String ROOT = "/data/tradepost/market/";

    public final Map<String, Double> values;
    public final List<Industry> industries;
    public final List<Relic> relics;
    /** 类别表（categories.json）：产业只写 "@类别"，具体物品由这一层展开 */
    public final Categories categories;
    /** 稀有度表（rarity.json）：世界生成密度/战利品来源；缺失时只走价格轴 */
    public final Rarity rarity;
    /** 读书失败的文件（跳过不崩，用 /tradepost reload 看日志） */
    public final List<String> errors = new ArrayList<>();
    /** 是否用到了外部目录 */
    public final boolean external;

    private MarketData(Map<String, Double> values, List<Industry> industries, List<Relic> relics,
                       Categories categories, Rarity rarity, boolean external) {
        this.values = values;
        this.industries = industries;
        this.relics = relics;
        this.categories = categories == null ? Categories.empty() : categories;
        this.rarity = rarity == null ? Rarity.empty() : rarity;
        this.external = external;
    }

    public record Industry(String id, String display, List<String> supply, List<String> demand,
                           @SerializedName("biome_weights") Map<String, Integer> biomeWeights,
                           @SerializedName("prosperity_bias") int prosperityBias) {
    }

    public record Relic(String id, String item, int count, List<Cost> cost, int weight,
                        @SerializedName("value_multiplier") double multiplier,
                        /** 绿宝石直购价（颗）；给了它就不用 cost */ Integer price,
                        /** 附魔书用：{附魔id: 等级} */ java.util.Map<String, Integer> enchantments) {
    }

    /**
     * 秘宝成本条目。两种写法二选一：
     *   count = 写死件数（原版思路，换整合包要重调）
     *   value = 写"值多少钱"，运行时按这个包里该物品的实际基础价折算件数（换整合包不用改）
     */
    public record Cost(String item, String tag, int count, int value) {
    }

    private record Index(List<String> industries, List<String> relics) {
    }

    public double valueOf(String itemId) {
        Double v = values.get(itemId);
        return v == null ? 0.0 : v;
    }

    public Industry industry(String id) {
        for (Industry i : industries) {
            if (i.id().equals(id)) return i;
        }
        return null;
    }

    // —————————————————— 读取 ——————————————————

    public static MarketData load() {
        return load(null);
    }

    /** dir 为 null 或不存在时只用 jar 内置数据。 */
    public static MarketData load(Path dir) {
        Gson gson = new Gson();
        Path base = (dir != null && Files.isDirectory(dir)) ? dir : null;
        boolean external = base != null;

        Map<String, Double> values = new LinkedHashMap<>();
        List<Industry> industries = new ArrayList<>();
        List<Relic> relics = new ArrayList<>();

        try (Reader r = open(base, "values.json")) {
            if (r != null) {
                Map<String, Double> raw = gson.fromJson(r, new TypeToken<Map<String, Double>>() {}.getType());
                if (raw != null) values.putAll(raw);
            }
        } catch (Exception e) {
            // values.json 坏了也要能跑，只是所有物品都没价
            throw new IllegalStateException("读取 values.json 失败", e);
        }

        for (String name : names(base, "industries")) {
            try (Reader r = open(base, "industries/" + name + ".json")) {
                if (r == null) continue;
                Industry ind = gson.fromJson(r, Industry.class);
                if (ind != null && ind.id() != null) industries.add(ind);
            } catch (Exception e) {
                // 单个文件写坏了不该让整个市场失效
            }
        }
        for (String name : names(base, "relics")) {
            try (Reader r = open(base, "relics/" + name + ".json")) {
                if (r == null) continue;
                Relic rel = gson.fromJson(r, Relic.class);
                if (rel != null && rel.item() != null) relics.add(rel);
            } catch (Exception e) {
                // 同上
            }
        }
        return new MarketData(values, industries, relics, Categories.load(base), Rarity.load(base), external);
    }

    /** 外部目录里有 json 就按目录来（增删即生效）；否则回落到 index.json 清单（jar 内没有目录枚举能力）。 */
    private static List<String> names(Path base, String sub) {
        List<String> stems = new ArrayList<>();
        if (base != null && Files.isDirectory(base.resolve(sub))) {
            try (var s = Files.list(base.resolve(sub))) {
                s.filter(p -> p.getFileName().toString().endsWith(".json"))
                 .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                 .sorted()
                 .forEach(stems::add);
            } catch (IOException e) {
                // 目录读不了就当没有
            }
        }
        if (!stems.isEmpty()) return stems;

        Index idx = index(base);
        if (idx == null) return List.of();
        List<String> fromIndex = "industries".equals(sub) ? idx.industries() : idx.relics();
        return fromIndex == null ? List.of() : fromIndex;
    }

    private static Index index(Path base) {
        try (Reader r = open(base, "index.json")) {
            return r == null ? null : new Gson().fromJson(r, Index.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 先外部文件，再 jar 内置。 */
    private static Reader open(Path base, String rel) throws IOException {
        if (base != null) {
            Path f = base.resolve(rel);
            if (Files.isRegularFile(f)) return Files.newBufferedReader(f, StandardCharsets.UTF_8);
        }
        InputStream in = MarketData.class.getResourceAsStream(ROOT + rel);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    // —————————————————— 导出 ——————————————————

    /** 把 jar 内置数据写到 dir（已存在的文件不覆盖），返回写出的文件数。 */
    public static int exportDefaults(Path dir) {
        try {
            Files.createDirectories(dir.resolve("industries"));
            Files.createDirectories(dir.resolve("relics"));
            int n = 0;
            n += copy("values.json", dir);
            n += copy("tag_values.json", dir);
            n += copy("index.json", dir);
            n += copy("categories.json", dir);
            n += copy("rarity.json", dir);
            Index idx = index(null);
            if (idx != null) {
                if (idx.industries() != null) {
                    for (String s : idx.industries()) n += copy("industries/" + s + ".json", dir);
                }
                if (idx.relics() != null) {
                    for (String s : idx.relics()) n += copy("relics/" + s + ".json", dir);
                }
            }
            return n;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int copy(String rel, Path dir) throws IOException {
        Path target = dir.resolve(rel);
        if (Files.exists(target)) return 0;
        Files.createDirectories(target.getParent());
        try (InputStream in = MarketData.class.getResourceAsStream(ROOT + rel)) {
            if (in == null) return 0;
            Files.copy(in, target);
            return 1;
        }
    }

    public String summary() {
        return "物品 " + values.size() + " / 产业 " + industries.size() + " / 类别 " + categories.ids().size()
                + " / 秘宝 " + relics.size();
    }
}
