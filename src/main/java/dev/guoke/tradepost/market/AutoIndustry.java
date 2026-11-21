package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 自动造产业（纯 Java，不依赖 Minecraft）。
 *
 * 思路：整合包里的模组物品几乎都打了通用标签（#c:ingots、#c:gears、#c:crops…），
 * 所以"一个标签 = 一类货"天然就是产业的原料。这里只维护一张
 * {@link #GROUPS} 表（标签 → 产业名 + 收什么 + 基准价），
 * 运行时由 MC 层把标签展开成真实物品，够数量就自动落一个产业 JSON 到
 * <config>/tradepost/market/industries/auto_*.json。
 *
 * 为什么"物品越多反而越好玩"：每个村从产业里挑货是按坐标哈希挑的，
 * 池子越大，两个同产业村子撞到同一批货的概率越小 —— 同产业也有差价。
 *
 * 已被手写产业引用过的标签会被跳过（冶炼村已经在卖锭，就不用再来一个"锭村"）。
 */
public final class AutoIndustry {

    /** 一个标签能被当成产业的最小物品数（少于这个数，跑商没得挑） */
    public static final int MIN_ITEMS = 6;

    /**
     * 标签 → 产业。
     *
     * @param key     产业 id 后缀（auto_&lt;key&gt;）
     * @param tag     物品标签（带 #）
     * @param display 显示名
     * @param demand  这个产业缺什么（玩家往这儿卖）
     * @param price   这个标签的基准价；写进 tag_values.json，用来给没被手写定价的物品兜底
     */
    public record Group(String key, String tag, String display, List<String> demand, double price) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group("nuggets", "#c:nuggets", "金属粒", List.of("#c:ingots"), 0.15),
            new Group("dusts", "#c:dusts", "金属粉", List.of("#c:ores"), 0.9),
            new Group("plates", "#c:plates", "金属板", List.of("#c:ingots"), 1.5),
            new Group("gears", "#c:gears", "齿轮", List.of("#c:ingots"), 2.0),
            new Group("rods", "#c:rods", "金属杆", List.of("#c:ingots"), 0.6),
            new Group("ores", "#c:ores", "矿石", List.of("#c:dusts"), 0.8),
            new Group("raw_materials", "#c:raw_materials", "原矿", List.of("#c:ingots"), 0.6),
            new Group("storage_blocks", "#c:storage_blocks", "材料块", List.of("#c:ingots"), 8.0),
            new Group("seeds", "#c:seeds", "种子", List.of("#c:crops"), 0.2),
            new Group("foods", "#c:foods", "食材", List.of("#c:crops"), 0.4),
            new Group("dyes", "#c:dyes", "染料", List.of("#c:crops"), 0.3),
            new Group("leathers", "#c:leathers", "皮革", List.of("#c:strings"), 0.6),
            new Group("planks", "#minecraft:planks", "木板", List.of("#minecraft:logs"), 0.25),
            new Group("wool", "#minecraft:wool", "羊毛", List.of("#c:dyes"), 0.4),
            new Group("mushrooms", "#c:mushrooms", "菌菇", List.of("#c:crops"), 0.3),
            new Group("bones", "#c:bones", "骨料", List.of("#c:foods"), 0.4),
            new Group("glass", "#c:glass_blocks", "玻璃", List.of("#c:stones"), 0.5),
            new Group("seafood", "#c:foods/raw_fish", "水产", List.of("#c:crops"), 0.5)
    );

    public record Draft(String key, String tag, String display, List<String> demand, int items) {
        public String id() {
            return "auto_" + key;
        }
    }

    /** drafts = 该建的产业；covered = 标签已被手写产业占用的；thin = 物品太少没资格成产业的 */
    public record Result(List<Draft> drafts, List<String> covered, List<String> thin) {
    }

    private AutoIndustry() {}

    /**
     * @param members  标签 → 该标签下"有价、可堆叠"的物品 id
     * @param usedTags 现有产业（手写 + 之前自动生成的）已经引用过的标签
     * @param price    物品基础价（用来过滤没价的物品）
     */
    public static Result generate(Map<String, List<String>> members, Set<String> usedTags,
                                  ToDoubleFunction<String> price) {
        List<Draft> drafts = new ArrayList<>();
        List<String> covered = new ArrayList<>();
        List<String> thin = new ArrayList<>();
        for (Group g : GROUPS) {
            if (usedTags.contains(g.tag())) {
                covered.add(g.tag());
                continue;
            }
            List<String> ids = members.getOrDefault(g.tag(), List.of());
            int n = 0;
            for (String id : ids) {
                if (price.applyAsDouble(id) > 0) n++;
            }
            if (n < MIN_ITEMS) {
                thin.add(g.tag() + " (" + n + ")");
                continue;
            }
            drafts.add(new Draft(g.key(), g.tag(), g.display(), g.demand(), n));
        }
        return new Result(drafts, covered, thin);
    }

    /** 落盘用的 JSON（手写拼接，保证玩家改起来是给人类看的格式） */
    public static String toJson(Draft d) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment\": \"自动生成（按物品标签聚类）。改这个文件即可覆盖；删掉即取消这个产业。\",\n");
        sb.append("  \"id\": \"").append(d.id()).append("\",\n");
        sb.append("  \"display\": \"").append(d.display()).append("\",\n");
        sb.append("  \"supply\": [\"").append(d.tag()).append("\"],\n");
        sb.append("  \"demand\": [");
        for (int i = 0; i < d.demand().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(d.demand().get(i)).append('"');
        }
        sb.append("],\n");
        sb.append("  \"biome_weights\": { \"default\": 1 },\n");
        sb.append("  \"prosperity_bias\": 0\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 标签 → 基准价：自动产业缺价时补进 tag_values.json 用 */
    public static Map<String, Double> groupPrices() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Group g : GROUPS) m.put(g.tag(), g.price());
        return m;
    }
}
