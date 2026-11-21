package dev.guoke.tradepost.mc;

import dev.guoke.tradepost.Tradepost;
import dev.guoke.tradepost.market.MarketData;
import dev.guoke.tradepost.market.OfferSpec;
import dev.guoke.tradepost.market.Stock;
import dev.guoke.tradepost.market.StockView;
import dev.guoke.tradepost.market.VillageMarket;

import net.minecraft.class_124;
import net.minecraft.class_9331;
import net.minecraft.class_1856;
import net.minecraft.class_1860;
import net.minecraft.class_1863;
import net.minecraft.class_5455;
import net.minecraft.class_1263;
import net.minecraft.class_1268;
import net.minecraft.class_1275;
import net.minecraft.class_2558;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_1646;
import net.minecraft.class_1657;
import net.minecraft.class_1728;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1814;
import net.minecraft.class_1914;
import net.minecraft.class_1916;
import net.minecraft.class_1937;
import net.minecraft.class_2168;
import net.minecraft.class_2338;
import net.minecraft.class_5138;
import net.minecraft.class_3341;
import net.minecraft.class_3443;
import net.minecraft.class_2382;
import net.minecraft.class_2378;
import net.minecraft.class_243;
import net.minecraft.class_2586;
import net.minecraft.class_2561;
import net.minecraft.class_2818;
import net.minecraft.class_6862;
import net.minecraft.class_6880;
import net.minecraft.class_1923;
import net.minecraft.class_2960;
import net.minecraft.class_3195;
import net.minecraft.class_3218;
import net.minecraft.class_3449;
import net.minecraft.class_3222;
import net.minecraft.class_5321;
import net.minecraft.class_7923;
import net.minecraft.class_7924;
import net.minecraft.class_8934;
import net.minecraft.class_9306;
import net.minecraft.class_9334;

import java.util.List;
import java.util.function.Supplier;

/*
 * 唯一接触 Minecraft 的薄桥接层。
 * 类名用 intermediary（class_XXXX）是为了不依赖 tiny-remapper 做 remap：
 * 编译产物与 Fabric Loader 运行时看到的名称一致，mixin 也不需要 refmap。
 *
 * 对照表（yarn）：
 *   class_1792 Item        class_1799 ItemStack     class_1935 ItemConvertible
 *   class_1914 TradeOffer  class_1916 TradeOfferList class_9306 TradedItem
 *   class_1646 VillagerEntity  class_1937 World    class_3218 ServerWorld
 *   class_2378 Registry    class_7923 Registries    class_2960 Identifier
 *   class_2561 Text        class_2168 ServerCommandSource  class_243 Vec3d
 */
public final class Bridge {

    private Bridge() {}

    /** Registries.ITEM（DefaultedRegistry<Item>） */
    @SuppressWarnings("unchecked")
    private static final class_2378<class_1792> ITEMS = (class_2378<class_1792>) class_7923.field_41178;

    // —— 物品 ——

    public static class_1792 item(String id) {
        try {
            Object v = ITEMS.method_10223(class_2960.method_60654(id));
            return v instanceof class_1792 it ? it : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static class_1799 stack(String id, int count) {
        class_1792 it = item(id);
        if (it == null) return class_1799.field_8037;   // ItemStack.EMPTY
        return new class_1799(it, count);
    }

    // —— 驿站标记 ——

    /** 驿站结构里那个箱子的战利品表 id；玩家手搭时用 /data merge block 写入同样字段即可。 */
    private static final class_2960 STATION_LOOT = class_2960.method_60654("tradepost:post_station");

    /** 区块 → "这区块里有驿站标记" 的缓存；换维度/换存档/重载数据时清空。 */
    private static class_3218 cacheWorld = null;
    private static final java.util.Map<Long, Boolean> MARK_CACHE = new java.util.HashMap<>();

    public static void clearStationCache() {
        cacheWorld = null;
        MARK_CACHE.clear();
    }

    /**
     * 在 (x,z) 周围 ±2 区块内找驿站标记。
     * 只扫已加载区块（isChunkLoaded 拦截），不强制加载区块，避免阻塞线程。
     * 每个区块只扫一次方块实体——村里几十个村民反复刷交易表时不会反复扫。
     */
    public static boolean stationNear(class_3218 world, int x, int z) {
        if (world != cacheWorld) {                          // 换维度或换存档
            cacheWorld = world;
            MARK_CACHE.clear();
        }
        int cx0 = x >> 4, cz0 = z >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = cx0 + dx, cz = cz0 + dz;
                if (!world.method_8393(cx, cz)) continue;   // WorldView.isChunkLoaded(int,int)：未加载不缓存
                long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                Boolean hit = MARK_CACHE.get(key);
                if (hit == null) {
                    hit = chunkHasMarker(world, cx, cz);
                    MARK_CACHE.put(key, hit);
                }
                if (hit) return true;
            }
        }
        return false;
    }

    private static boolean chunkHasMarker(class_3218 world, int cx, int cz) {
        try {
            class_2818 chunk = world.method_8497(cx, cz);             // World.getChunk(int,int)
            if (chunk == null) return false;
            for (class_2586 be : chunk.method_12214().values()) {     // getBlockEntities
                if (isStationMarker(be)) return true;
            }
        } catch (Exception e) {
            Tradepost.LOGGER.debug("[tradepost] 扫描区块 {} {} 失败", cx, cz, e);
        }
        return false;
    }

    private static boolean isStationMarker(class_2586 be) {
        if (be instanceof class_8934 lootable) {                 // LootableInventory
            class_5321<?> key = lootable.method_54869();         // getLootTable()
            if (key != null) {
                class_2960 id = key.method_29177();              // RegistryKey.getValue()
                if (id != null && id.equals(STATION_LOOT)) return true;
            }
        }
        if (be instanceof class_1275 named) {                    // Nameable：铁砧改名的兜底
            class_2561 name = named.method_5797();
            if (name != null && name.getString().contains("驿站")) return true;
        }
        return false;
    }

    // —— 存档库存 ——

    /** 存档侧市场数据（<world>/data/tradepost_market.dat） */
    public static TradeStore store(class_3218 world) {
        return world.method_17983().method_17924(TradeStore.TYPE, "tradepost_market");
    }

    /** 生物群系的 path + 标签，用于产业加权（沙漠村不出林业） */
    public static dev.guoke.tradepost.market.BiomeInfo biomeInfo(class_3218 world, int x, int y, int z) {
        try {
            // getBiome 在区块没加载时也会去同步要区块（同一个 getChunkBlocking 坑），先挡一道
            if (!world.method_8393(x >> 4, z >> 4)) return null;
            class_6880<?> biome = world.method_23753(new class_2338(x, y, z));   // getBiome(BlockPos)
            if (biome == null) return null;
            String path = null;
            var key = biome.method_40230();                                      // getKey()
            if (!key.isEmpty()) path = key.get().method_29177().method_12832();
            java.util.Set<String> tags = new java.util.HashSet<>();
            biome.method_40228().forEach(t -> {                                  // getTagKeys()
                if (t instanceof net.minecraft.class_6862<?> tk) {
                    var id = tk.comp_327();     // TagKey#id()（record 组件）
                    if (id != null) tags.add(id.toString());
                }
            });
            return new dev.guoke.tradepost.market.BiomeInfo(path, tags);
        } catch (Exception e) {
            Tradepost.LOGGER.debug("[tradepost] 读取生物群系失败", e);
            return null;
        }
    }

    /** 物品 id（"minecraft:iron_ingot"） */
    public static String itemId(class_1799 stack) {
        if (stack == null || stack.method_7960()) return "minecraft:air";        // isEmpty
        class_2960 id = ITEMS.method_10221(stack.method_7909());                 // Registry.getId(Item)
        return id == null ? "minecraft:air" : id.toString();
    }

    /**
     * 区域市场键：以 areaRadius（默认 1024 格）切块，同一块里的村子共享库存压力。
     * 这是"村庄联动"的坐标基础 —— 隔壁村的收货价会受这片地方的总货量影响。
     */
    public static String areaKey(class_1937 world, int x, int z) {
        int r = Math.max(256, dev.guoke.tradepost.market.Economy.current().areaRadius);
        return dim(world) + "/" + Math.floorDiv(x, r) + ":" + Math.floorDiv(z, r);
    }

    /**
     * 维度前缀：主世界、下界、末地、天堂都有村庄，坐标可能完全重合，
     * 库存与片区压力必须按维度分开存，否则三个维度的同一个坐标会共用一本账。
     */
    public static String dim(class_1937 world) {
        try {
            if (world == null) return "?";
            return world.method_27983().method_29177().toString();   // getRegistryKey().getValue()
        } catch (Throwable t) {
            return "?";
        }
    }

    private static StockView view(TradeStore store, String vkey, long day, boolean station) {
        return new StockView() {
            @Override
            public double stockOf(String itemId) {
                double base = basePrice(itemId);
                return store.get(vkey, itemId, capOf(itemId, base, station), day);
            }

            @Override
            public int level() {
                try {
                    return Math.max(1, store.level(vkey));
                } catch (Throwable t) {
                    return 1;
                }
            }
        };
    }

    /**
     * 成交之后记账：玩家买走 → 村里库存减；玩家卖来 → 村里库存加。
     * 靠 offer 的两个槽判断方向（绿宝石在哪边，就说明玩家在哪边掏钱）。
     */
    public static void afterTrade(class_1646 villager, class_1914 offer) {
        try {
            MarketData data = Tradepost.data();
            if (data == null || offer == null) return;
            if (!(villager.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(villager.method_23317());
            int z = (int) Math.floor(villager.method_23321());
            long day = world.method_8532() / 24000L;
            String vkey = vkey(world, x, (int) Math.floor(villager.method_23318()), z);
            TradeStore store = store(world);
            store.settle(vkey, day);

            boolean station = stationNear(world, x, z);
            String area = areaKey(world, x, z);
            book(store, vkey, area, day, station, offer.method_8246(), -1);   // 玩家付的（第一格）
            book(store, vkey, area, day, station, offer.method_19272(), -1);  // 玩家付的（第二格，秘宝用）
            book(store, vkey, area, day, station, offer.method_8250(), +1);   // 玩家拿到的
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 记账失败", e);
        }
    }

    /**
     * 记一笔库存。sign = -1 玩家拿走（村里少货），+1 玩家送来（村里多货）。
     * 绿宝石是村里的货币，不进库存表；其余物品（含秘宝的实物成本）照记。
     */
    private static void book(TradeStore store, String vkey, String areaKey, long day, boolean station,
                             class_1799 stack, int sign) {
        if (stack == null || stack.method_7960()) return;                   // isEmpty
        String id = itemId(stack);
        if (bucket(id)) return;
        double cap = capOf(id, basePrice(id), station);
        store.add(vkey, areaKey, id, sign * (double) stack.method_7947(), cap, day);
    }

    /** 货币类物品：不当作库存货物 */
    private static boolean bucket(String id) {
        return id.equals("minecraft:emerald") || id.equals("minecraft:air");
    }

    // —— 物品目录：标签展开 + 模组物品兜底价 ——

    /** Registries.ITEM 的注册表键，用来建 TagKey（类型是 class_5321<class_2378<Item>>） */
    private static final class_5321<class_2378<class_1792>> ITEM_KEY = class_7924.field_41197;

    private static final class TagCatalog implements dev.guoke.tradepost.market.Catalog {
        private final java.util.Map<String, List<String>> tagCache = new java.util.concurrent.ConcurrentHashMap<>();
        /**
         * 展开+过滤掉黑名单之后的标签结果。
         *
         * <p>没有它的话，每次报价都要对上万件货逐个跑一遍黑名单（79 个关键词 + 36 个正则），
         * 一次开页 2 秒多就全耗在这（日志里"开页 2367 ms＝收购 2365"）。结果只跟标签和黑名单有关，
         * 两者变了（/tradepost reload）才清。
         */
        private final java.util.Map<String, List<String>> expandCache = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile java.util.Map<String, Double> tagValues = null;
        private volatile java.util.Map<String, Double> derived = null;
        private volatile Double fallback = null;

        private volatile java.util.Set<String> bannedSet = null;
        private volatile List<String> allCache = null;
        /** 配方反推出来的成本价（"龙肉炖"这种没标签的模组货靠它商品化） */
        private volatile java.util.Map<String, Double> recipePrices = null;
        /** 菜与酒的池子（每村杂货位从这儿抽，种类上千，撞车概率极低） */
        private volatile List<String> cuisinePool = null;
        /** 全球饱和：由 Bridge.ctx 每次挂上"当前世界 + 当前天数"的净值查询 */
        private volatile java.util.function.ToDoubleFunction<String> worldIndexFn = null;

        void setWorldIndexFn(java.util.function.ToDoubleFunction<String> fn) {
            this.worldIndexFn = fn;
        }

        /** 1.0 = 这种货还没被全世界卖烂；0.05 = 已经泛滥到不值钱 */
        @Override
        public double worldIndex(String id) {
            java.util.function.ToDoubleFunction<String> f = worldIndexFn;
            return f == null ? 1.0 : f.applyAsDouble(id);
        }

        void setCuisinePool(List<String> pool) {
            this.cuisinePool = pool;
        }

        /** 类别表随市场数据一起重载；空表时 "@xxx" 条目自然展开成空。 */
        private volatile dev.guoke.tradepost.market.Categories categories =
                dev.guoke.tradepost.market.Categories.empty();

        void setCategories(dev.guoke.tradepost.market.Categories c) {
            this.categories = c == null ? dev.guoke.tradepost.market.Categories.empty() : c;
            this.foodItems = null;      // 类别表重载，食物缓存跟着失效
        }

        /** 食物类：靠物品自带的 food 组件认，不看名字、不看标签——模组菜谱一件不落 */
        private volatile List<String> foodItems;

        private List<String> foodItems() {
            List<String> hit = foodItems;
            if (hit != null) return hit;
            List<String> list = new java.util.ArrayList<>();
            try {
                class_9331<?> type = class_7923.field_49658.method_10223(
                        class_2960.method_60655("minecraft", "food"));
                if (type != null) {
                    class_2378<class_1792> reg = class_7923.field_41178;
                    java.util.Iterator<class_1792> it = reg.method_10220().iterator();
                    while (it.hasNext()) {
                        class_1792 item = it.next();
                        try {
                            if (compRead(new class_1799(item), type) != null) {
                                list.add(reg.method_10221(item).toString());
                            }
                        } catch (Throwable ignored) {
                            // 单件货读组件失败就跳过，不影响整张表
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 没有食物组件类型（理论上不可能）就退化成空
            }
            foodItems = list;
            return list;
        }

        @Override
        public List<String> category(String id) {
            if ("food".equals(id)) return foodItems();
            return categories.expand(this, id);
        }

        @Override
        public boolean dish(String id) {
            return isDish(id);
        }

        /** 没中文译名的货不上架（但 value() 照旧给价，玩家能卖） */
        @Override
        public boolean canSell(String id) {
            return hasZhName(id);
        }

        @Override
        public List<String> cuisines() {
            List<String> p = cuisinePool;
            return p == null ? java.util.Collections.emptyList() : p;
        }

        void clear() {
            tagCache.clear();
            expandCache.clear();
            derived = null;
            fallback = null;
            bannedSet = null;
            allCache = null;
            recipePrices = null;
            cuisinePool = null;
            worldIndexFn = null;
            tierCache.clear();
        }

        void setRecipePrices(java.util.Map<String, Double> m) {
            this.recipePrices = m;
            this.allCache = null;
        }

        java.util.Map<String, Double> recipePrices() {
            return recipePrices;
        }

        /** 已有的权威价集合：手写价 + 类别标签价。配方价不许覆盖它们。 */
        java.util.Set<String> fixedIds() {
            java.util.Set<String> s = new java.util.HashSet<>();
            java.util.Map<String, Double> d = derived();
            for (class_1792 it : ITEMS) {
                class_2960 idn = ITEMS.method_10221(it);
                if (idn == null) continue;
                String id = idn.toString();
                if (Tradepost.valueOf(id) > 0 || d.containsKey(id)) s.add(id);
            }
            return s;
        }

        /** 配方定价用的"当前最低认知价"：手写 → 标签 → 保底（都不乘黑名单、不算配方价本身） */
        double seedValue(String id) {
            double e = Tradepost.valueOf(id);
            if (e > 0) return e;
            Double v = derived().get(id);
            if (v != null && v > 0) return v * dev.guoke.tradepost.market.Rarity.mult(rarity(id)) * foodFactor(id);
            return fallbackBase() * dev.guoke.tradepost.market.Rarity.mult(rarity(id)) * foodFactor(id);
        }

        /**
         * 全部可交易物品：注册表里所有"有价、没被黑名单拦、能堆叠、不是绿宝石"的物品。
         * 用来给"不属于任何贸易标签"的模组货（家具、装饰块、杂项材料）留一个货架位。
         */
        @Override
        public List<String> all() {
            List<String> c = allCache;
            if (c != null) return c;
            synchronized (this) {
                if (allCache != null) return allCache;
                List<String> out = new java.util.ArrayList<>();
                try {
                    for (class_1792 it : ITEMS) {
                        class_2960 idn = ITEMS.method_10221(it);
                        if (idn == null) continue;
                        String id = idn.toString();
                        if ("minecraft:air".equals(id) || "minecraft:emerald".equals(id)
                                || "minecraft:emerald_block".equals(id)) continue;
                        if (it.method_7882() <= 1) continue;              // 一刀一件的家伙不进杂货
                        if (bannedItem(id)) continue;
                        if (value(id) <= 0) continue;
                        out.add(id);
                    }
                } catch (Throwable t) {
                    Tradepost.LOGGER.warn("[tradepost] 生成杂货池失败", t);
                }
                allCache = out;
                return out;
            }
        }

        /** 黑名单里"整标签"拉黑的物品集合（把 #c:tools 这类展开成具体 id），懒加载一次。 */
        private java.util.Set<String> bannedSet() {
            java.util.Set<String> s = bannedSet;
            if (s != null) return s;
            synchronized (this) {
                if (bannedSet != null) return bannedSet;
                java.util.Set<String> built = new java.util.HashSet<>();
                for (String tag : dev.guoke.tradepost.market.Banlist.current().tags) {
                    if (tag == null || tag.isEmpty()) continue;
                    String e = tag.startsWith("#") ? tag : "#" + tag;
                    for (String id : readTag(e)) built.add(id);
                }
                bannedSet = built;
                return built;
            }
        }

        private boolean bannedItem(String id) {
            return dev.guoke.tradepost.market.Banlist.isBanned(id) || bannedSet().contains(id);
        }

        @Override
        public List<String> expand(String entry) {
            if (entry == null) return List.of();
            if (!entry.startsWith("#")) return List.of(entry);
            return expandCache.computeIfAbsent(entry, this::expandRaw);
        }

        /** 真正的展开：读标签 → 去掉黑名单。只由 expand() 调一次，结果进 expandCache。 */
        private List<String> expandRaw(String entry) {
            if (dev.guoke.tradepost.market.Banlist.tagBanned(entry)) return List.of();
            List<String> base = tagCache.computeIfAbsent(entry, TagCatalog::readTag);
            if (base.isEmpty()) return List.of();
            List<String> out = new java.util.ArrayList<>(base.size());
            for (String id : base) {
                if (!bannedItem(id)) out.add(id);
            }
            return java.util.Collections.unmodifiableList(out);
        }

        /** 黑名单里的物品一律算"没价"：不进买页、不进秘宝材料、也不出现在收购清单。 */
        @Override
        public boolean banned(String id) {
            return bannedItem(id);
        }

        /** "#ns:path" → 该标签下所有物品 id（已注册的才算）。标签不存在就返回空表。 */
        private static List<String> readTag(String entry) {
            List<String> out = new java.util.ArrayList<>();
            try {
                class_2960 id = class_2960.method_60654(entry.substring(1));
                class_6862<class_1792> key = class_6862.method_40092(ITEM_KEY, id);
                var list = ITEMS.method_40266(key);                       // Registry.getEntryList(TagKey)
                if (list.isEmpty()) return out;
                list.get().method_40239().forEach(e -> {                   // stream()
                    var rk = e.method_40230();                             // getKey()
                    if (rk.isPresent()) out.add(rk.get().method_29177().toString());
                });
            } catch (Exception e) {
                Tradepost.LOGGER.debug("[tradepost] 展开标签 {} 失败", entry, e);
            }
            return out;
        }

        @Override
        public double value(String id) {
            double raw = rawValue(id);
            if (raw <= 0) return 0;
            if (Tradepost.valueOf(id) > 0) return raw;   // 手写价是作者意图，不再叠稀有度
            // 配方成本价同理：材料价里已经含过一次稀有度（seedValue 会乘），成品再乘一遍就是
            // 凭空 3.5 倍 —— 附魔金苹果浮漂就是这么卖到 64 个绿宝石块的（成本 78 → ×3.5 = 273）。
            // 稀有度对配方货只保留"限量/门槛"两件事，不再进价。
            if (isRecipePriced(id) && !isDish(id)) return raw;   // 菜/酒先留着老口径（那边另有 Cuisine 溢价要调）
            return raw * dev.guoke.tradepost.market.Rarity.mult(rarity(id));
        }

        /** 这个价是不是"按配方成本反推"来的（标签价与手写价优先，所以要先排掉它们） */
        private boolean isRecipePriced(String id) {
            java.util.Map<String, Double> rp = recipePrices;
            if (rp == null || id == null) return false;
            if (Tradepost.valueOf(id) > 0 || bannedItem(id)) return false;
            Double p = rp.get(id);
            if (p == null || p <= 0) return false;
            Double tag = derived().get(id);
            return tag == null || tag <= 0;             // 有标签价的走标签那条路
        }

        /** 配方反推价的地板（见 rawValue 里"配方成本价"那段）：防止除出 0.0006 这种等于没价的数 */
        private static final double RECIPE_FLOOR = 0.1;

        /** 未乘稀有度的原始价：档位要由价格轴推出来，所以先算价、再定档、最后乘倍率 */
        /**
         * 酒类（带 {@code kaleidoscope_tavern:brew_level} 组件的那一整批，含鸡尾酒）的底价。
         *
         * <p>它们的配方成本就是"葡萄汁 + 空瓶"那一档（零点几颗绿宝石），等级倍率乘上去
         * 1~6 档也就 1~3 颗 —— 玩家看到的"酒不值钱、等级没区别"就是这么来的。
         * 给个 8 的底，倍率再把档次拉开（0.45×～4×）。
         */
        private static final double DRINK_FLOOR = 8.0;

        private double rawValue(String id) {
            double v = rawValueInner(id);
            if (v > 0 && v < DRINK_FLOOR && isDrinkItem(id)) return DRINK_FLOOR;
            return v;
        }

        private double rawValueInner(String id) {
            // 预制车（car:xxx）不是真物品：共用"机动车"的手写底价
            if (id != null && id.startsWith("car:")) {
                return Tradepost.valueOf("automobility:automobile");
            }
            double explicit = Tradepost.valueOf(id);
            if (explicit > 0) return explicit;          // 手写价永远优先，不乘稀有度（也等于白名单）
            if (bannedItem(id)) return 0;               // 黑名单：太特殊的货不参与贸易
            java.util.Map<String, Double> d = derived();
            Double v = d.get(id);
            if (v != null && v > 0) return v * foodFactor(id);   // 标签基价 × 食物倍数（稀有度在外层乘）
            // 配方成本价：菜、点心、合成器械、装饰块这些"没有贸易标签"的模组货，
            // 按材料成本反推（龙肉炖 = 龙肉 + 碗），不再一律 0.05。
            java.util.Map<String, Double> rp = recipePrices;
            if (rp != null) {
                Double p = rp.get(id);
                // 地板价（RECIPE_FLOOR）：绳子、榻榻米这种"材料便宜 / 一炉出 4 个"的货，成本除下来
                // 只有 0.0006 颗绿宝石 —— 取整就是 0，卖不出去也买不到，等于没有价。给个 0.1 的底，
                // 一摞 64 个约 6 颗，不至于是白送。
                if (p != null && p > 0) return Math.max(p, RECIPE_FLOOR);   // 工业品已乘折扣、菜与酒已乘溢价，这里直接就是最终基础价
            }
            // 稀有的特殊货（史诗级神器、Boss 掉落）如果没有标签价，就别拿保底价收 ——
            // 否则会出现"0.05 绿宝石收神话物品"。开关在 blacklist.json 的 banRareUnpriced。
            if (dev.guoke.tradepost.market.Banlist.banRareUnpriced()) {
                String rarity = rarityOf(id);
                if ("rare".equals(rarity) || "epic".equals(rarity)) return 0;
            }
            // 类别默认价（categories.json 的 "price"）：模组的树苗 / 花 / 树叶 / 石材这类货
            // 归类清楚、却既没标签价也没配方价，过去一律掉到 0.05 —— 摆上货架等于白送。
            // 排在最后只为兜底：手写价 / 标签价 / 配方价都优先，稀有货照样走上面的"不收"。
            double catPrice = categoryPrice(id);
            if (catPrice > 0) return catPrice * foodFactor(id);
            // 兜底：能吃的至少按食物价（#c:foods 0.4）算，别跟泥土一个价；
            // 其余普通物品给极低保底价（0.05，改 tag_values.json 的 "default" 可调）。
            double foodBase = tagValueTable().getOrDefault("#c:foods", 0.4);
            double fallback = isEdible(id) ? foodBase : fallbackBase();
            return fallback * foodFactor(id);
        }

        /** 给 /tradepost why 用：这件货的价是从哪条路来的 */
        public String sourceOf(String id) {
            if (Tradepost.valueOf(id) > 0) return "手写表 values.json";
            if (bannedItem(id)) return "黑名单";
            Double v = derived().get(id);
            if (v != null && v > 0) {
                String r = rarityOf(id);
                return "标签基价 " + fmt2(v) + " × 稀有度 " + r + "(" + fmt2(rarityFactor(id)) + ")";
            }
            java.util.Map<String, Double> rp = recipePrices;
            if (rp != null) {
                Double p = rp.get(id);
                if (p != null && p > 0) return "配方成本推算（材料价已含稀有度，成品不再叠）";
            }
            String r = rarityOf(id);
            if (dev.guoke.tradepost.market.Banlist.banRareUnpriced() && ("rare".equals(r) || "epic".equals(r))) {
                return "稀有且无标签价 → 不收（banRareUnpriced）";
            }
            double catPrice = categoryPrice(id);
            if (catPrice > 0) return "类别默认价 " + fmt2(catPrice) + "（categories.json）";
            // 能吃的兜底按食物价（#c:foods），不算"兜底"——所以这里标成"标签基价"
            if (isEdible(id)) {
                double foodBase = tagValueTable().getOrDefault("#c:foods", 0.4);
                return "标签基价 " + fmt2(foodBase) + "（食物兜底按 #c:foods）× 稀有度 " + r + "(" + fmt2(rarityFactor(id)) + ")";
            }
            return "默认兜底 " + fmt2(fallbackBase()) + " × 稀有度 " + r + "(" + fmt2(rarityFactor(id)) + ")";
        }

        private static String fmt2(double v) {
            return v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        /** 能吃的东西按 Economy.foodMultiplier 抬价（模组食材的标签基价定得太贱） */
        private static double foodFactor(String id) {
            try {
                // 原版食物不乘倍率：标签价 #c:foods 0.4 就是原版价，模组食材才 ×4。
                if (id != null && id.startsWith("minecraft:")) return 1.0;
                if (!isEdible(id)) return 1.0;
                double m = dev.guoke.tradepost.market.Economy.current().foodMultiplier;
                return m >= 1.0 ? m : 1.0;
            } catch (Throwable t) {
                return 1.0;
            }
        }

        /** 这件货所属类别的默认价（categories.json 的 "price"）；没有就 0。 */
        private double categoryPrice(String id) {
            try {
                dev.guoke.tradepost.market.MarketData d = Tradepost.data();
                return d == null ? 0 : d.categories.priceOf(this, id);
            } catch (Throwable t) {
                return 0;
            }
        }

        private double fallbackBase() {
            Double fb = fallback;
            if (fb == null) {
                fb = tagValueTable().get("default");
                if (fb == null || fb <= 0) fb = 0.05;
                fallback = fb;
            }
            return fb;
        }

        /** Rarity 组件 → 乘子。作者没标的（默认 COMMON）就是 1.0，不至于误伤。 */
        private static double rarityFactor(String id) {
            String r = rarityOf(id);
            if (r == null) return 1.0;
            return switch (r) {
                case "uncommon" -> 2.0;
                case "rare" -> 5.0;
                case "epic" -> 15.0;
                default -> 1.0;
            };
        }

        /** 稀有度表 + 每件货的档位缓存（价格轴与来源轴取高，见 Rarity） */
        private volatile dev.guoke.tradepost.market.Rarity rarityTable = dev.guoke.tradepost.market.Rarity.empty();
        private final java.util.Map<String, Integer> tierCache = new java.util.concurrent.ConcurrentHashMap<>();

        void setRarity(dev.guoke.tradepost.market.Rarity r) {
            this.rarityTable = r == null ? dev.guoke.tradepost.market.Rarity.empty() : r;
            tierCache.clear();
        }

        @Override
        public int rarity(String id) {
            Integer c = tierCache.get(id);
            if (c != null) return c;
            int t;
            try {
                t = dev.guoke.tradepost.market.Rarity.of(id, rawValue(id), rarityTable);
            } catch (Throwable e) {
                t = dev.guoke.tradepost.market.Rarity.COMMON;
            }
            tierCache.put(id, t);
            return t;
        }

        /** 读物品的 minecraft:rarity 组件（1.20.5+），拿不到返回 null。 */
        static String rarityOf(String id) {
            try {
                class_1792 it = ITEMS.method_10223(class_2960.method_60654(id));
                if (it == null) return null;
                Object r = it.method_57347().method_57829(class_9334.field_50073);  // Item.getComponents().get(DataComponentTypes.RARITY)
                if (r == class_1814.field_8907) return "uncommon";
                if (r == class_1814.field_8903) return "rare";
                if (r == class_1814.field_8904) return "epic";
                return "common";
            } catch (Exception e) {
                return null;
            }
        }

        /** 用 tag_values.json 的标签基价，给 values.json 没登记的模组物品推个价。 */
        private java.util.Map<String, Double> derived() {
            java.util.Map<String, Double> d = derived;
            if (d != null) return d;
            synchronized (this) {
                if (derived != null) return derived;
                java.util.Map<String, Double> built = new java.util.HashMap<>();
                for (var e : tagValueTable().entrySet()) {
                    if (!e.getKey().startsWith("#")) continue;      // "default" 是保底价，不是标签
                    if (dev.guoke.tradepost.market.Banlist.tagBanned(e.getKey())) continue;
                    for (String itemId : expand(e.getKey())) {
                        if (bannedItem(itemId)) continue;
                        built.putIfAbsent(itemId, e.getValue());
                    }
                }
                derived = built;
                return built;
            }
        }

        private java.util.Map<String, Double> tagValueTable() {
            java.util.Map<String, Double> t = tagValues;
            if (t != null) return t;
            synchronized (this) {
                if (tagValues != null) return tagValues;
                java.util.Map<String, Double> read = readTagValues();
                tagValues = read;
                return read;
            }
        }

        /** 标签基价：外部 config 优先（玩家/自动生成改过就生效），否则 jar 内置。 */
        private static java.util.Map<String, Double> readTagValues() {
            java.util.Map<String, Double> read = new java.util.LinkedHashMap<>();
            java.lang.reflect.Type ty = new com.google.gson.reflect.TypeToken<java.util.Map<String, Double>>() {}.getType();
            java.io.Reader r = null;
            try {
                java.nio.file.Path ext = Tradepost.dataDir().resolve("tag_values.json");
                if (java.nio.file.Files.isRegularFile(ext)) {
                    r = java.nio.file.Files.newBufferedReader(ext, java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    java.io.InputStream in = TagCatalog.class.getResourceAsStream("/data/tradepost/market/tag_values.json");
                    if (in != null) r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
                }
                if (r != null) {
                    java.util.Map<String, Double> raw = new com.google.gson.Gson().fromJson(r, ty);
                    if (raw != null) read.putAll(raw);
                }
            } catch (Exception e) {
                Tradepost.LOGGER.warn("[tradepost] 读取 tag_values.json 失败，模组物品将没有兜底价", e);
            } finally {
                if (r != null) try { r.close(); } catch (Exception ignored) { }
            }
            return read;
        }
    }

    private static final TagCatalog CATALOG = new TagCatalog();

    public static dev.guoke.tradepost.market.Catalog catalog() {
        return CATALOG;
    }

    /** 市场数据重载后把类别表挂上（产业里的 "@类别" 靠它展开）。 */
    public static void applyCategories(dev.guoke.tradepost.market.Categories c) {
        CATALOG.setCategories(c);
    }

    /** 市场数据重载后把稀有度表挂上（价格倍率 / 饱和豁免 / 限量都靠它）。 */
    public static void applyRarity(dev.guoke.tradepost.market.Rarity r) {
        CATALOG.setRarity(r);
    }

    /** /reload 之后物品标签会变，清掉缓存重新展开 */
    public static void clearCatalogCache() {
        CATALOG.clear();
        dev.guoke.tradepost.market.Chain.clear();
        INDEX_TABLE = null;
        INDEX_STAMP = Long.MIN_VALUE;
        recipeLoaded = false;
        recipeWorld = null;
    }

    // —— 配方定价：把"没有标签价"的模组货（菜、点心、器械、装饰）按材料成本定价 ——

    private static volatile boolean recipeLoaded = false;
    private static class_3218 recipeWorld;
    /** recipeWorld 的种子：指数表缓存按存档分开存，别让两个存档互相串 */
    private static long indexSeed;

    /** 扫出来的全部配方：配方定价和"合成链饱和传播"共用同一份 */
    private static volatile java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> RECIPES = null;
    /** 全球饱和在合成链上传播后的指数表（按 存档版本 + 天数 缓存） */
    private static volatile java.util.Map<String, Double> INDEX_TABLE = null;
    private static volatile long INDEX_STAMP = Long.MIN_VALUE;
    /** 成品 → 原始材料（记账用：卖金块记 9 个金锭，换皮卖金苹果也记到同一批原料上） */
    private static volatile dev.guoke.tradepost.market.Decompose DECOMP = null;

    /**
     * 某种货此刻的全球价格指数：自己被打烂的程度，和材料被打烂的程度，取更狠的那个。
     * 这样金锭崩了，金块 / 金苹果 / 金装备一起跟着崩 —— 不然玩家合成一下就能绕开。
     */
    /** 指数表磁盘缓存：<config>/tradepost/cache/index-<day>.txt（一天一份，格式 item<TAB>value） */
    private static java.nio.file.Path indexCachePath(long day) {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                    .resolve("tradepost").resolve("cache").resolve("index-" + indexSeed + "-" + day + ".txt");
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.util.Map<String, Double> loadIndex(long day) {
        try {
            java.nio.file.Path p = indexCachePath(day);
            if (p == null || !java.nio.file.Files.isRegularFile(p)) return null;
            java.util.List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).startsWith("v1")) return null;
            java.util.Map<String, Double> m = new java.util.HashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                String s = lines.get(i);
                int tab = s.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    m.put(s.substring(0, tab), Double.parseDouble(s.substring(tab + 1)));
                } catch (Throwable ignored) {
                }
            }
            return m.isEmpty() ? null : m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void saveIndex(long day, java.util.Map<String, Double> m) {
        try {
            if (m == null || m.isEmpty()) return;
            java.nio.file.Path p = indexCachePath(day);
            if (p == null) return;
            java.nio.file.Files.createDirectories(p.getParent());
            StringBuilder sb = new StringBuilder(1 << 18);
            sb.append("v1\t").append(day).append('\n');
            for (java.util.Map.Entry<String, Double> e : m.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            java.nio.file.Files.writeString(p, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            if (m.size() > 200) {
                try {
                    java.nio.file.Files.deleteIfExists(indexCachePath(day - 1));
                    java.nio.file.Files.deleteIfExists(indexCachePath(day - 2));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 指数表缓存写不出去", t);
        }
    }

    public static double worldIndexFor(TradeStore store, long day, String item) {
        // 便宜的大宗货不吃全球饱和：小麦、圆石、木板被卖多少都只影响附近片区（areaPressure）
        double base = basePrice(item);
        if (base > 0 && base < dev.guoke.tradepost.market.Economy.current().worldMinBase) return 1.0;
        // 一天只重建一次全表。以前是"每 8 笔成交重建一次"，而单次实测 2.2 秒
        // （日志「收购定价慢」会随机落在"当页第一件货"上，就是这个原因）—— 连续卖东西卡就是它。
        // 全球饱和本来就是按天走的慢变量，当天沿用昨天的表没有可感知偏差。
        long stamp = day;
        java.util.Map<String, Double> t = INDEX_TABLE;
        if (t == null || INDEX_STAMP != stamp) {
            // 先看磁盘缓存：这张表只跟"天"有关（同一天里本来就不重建），
            // 所以当天第二次开游戏不该再算一遍 12,801 条配方
            java.util.Map<String, Double> cached = loadIndex(stamp);
            if (cached != null) {
                t = cached;
                INDEX_TABLE = t;
                INDEX_STAMP = stamp;
                Tradepost.LOGGER.info("[tradepost] 全球指数表命中磁盘缓存（第 {} 天，{} 件）", stamp, cached.size());
            } else {
                java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs = RECIPES;
                long t0 = System.nanoTime();
                if (recs == null || recs.isEmpty()) {
                    t = java.util.Collections.emptyMap();
                } else {
                    t = dev.guoke.tradepost.market.IndexGraph.build(
                            recs, id -> store.worldIndex(id, day), Bridge::basePrice, 4);
                }
                long tms = (System.nanoTime() - t0) / 1_000_000L;
                if (tms >= 50) {
                    Tradepost.LOGGER.info("[tradepost] 全球指数表重建 {} ms（{} 条配方，一天一次）",
                            tms, recs == null ? 0 : recs.size());
                }
                INDEX_TABLE = t;
                INDEX_STAMP = stamp;
                saveIndex(stamp, t);
            }
        }
        Double v = t.get(item);
        return v != null ? v : store.worldIndex(item, day);
    }

    /**
     * 扫一遍配方表，给没手写价 / 没标签价的物品算出成本价。
     * 第一次开面板或第一次交易时跑一次（约几十毫秒），/tradepost reload 后会重算。
     */
    public static void ensureRecipePrices(class_3218 world) {
        if (world == null) return;
        if (WARM_SCAN != null) {                                 // 预热还没跑完：一次做完，免得算两遍
            Scanner sc = WARM_SCAN;
            while (sc.step(500) > 0) { }
            finishWarmUp();
        }
        if (recipeLoaded && world == recipeWorld) return;
        synchronized (Bridge.class) {
            if (recipeLoaded && world == recipeWorld) return;
            try {
                class_1863 rm = world.method_8433();                     // getRecipeManager()
                class_5455 lookup = world.method_30349();                // getRegistryManager()（也是 WrapperLookup）
                java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs = new java.util.ArrayList<>();
                for (var entry : rm.method_8126()) {                    // values()，所有配方
                    try {
                        var r = entry.comp_1933();
                        addRecipe(recs, r, lookup);
                    } catch (Throwable ignored) {
                        // 某个模组的自定义配方不让读就跳过它，不影响别的
                    }
                }
                runPricing(world, recs);
            } catch (Throwable t) {
                recipeLoaded = true;                                     // 失败也别每次重试
                Tradepost.LOGGER.warn("[tradepost] 配方定价失败（本次不启用）", t);
            }
        }
    }

    /**
     * 一条配方 → 一条材料成本记录。
     *
     * <p>原版锻造台配方（下界合金剑/甲/工具）的 getIngredients() 返回空表，
     * 所以那些装备一直算不出材料价、装备回收通道也就不收它们（日志里"材料价0.00"）。
     * 这里兜一层：直接读它身上那几个 class_1856 字段（模板 / 底料 / 添加物）。
     */
    private static void addRecipe(java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs,
                                  class_1860<?> r, class_5455 lookup) {
        try {
            class_1799 out = r.method_8110(lookup);                       // getResult(lookup)
            if (out == null || out.method_7960()) return;
            class_2960 oid = ITEMS.method_10221(out.method_7909());
            if (oid == null) return;
            String outId = oid.toString();
            if ("minecraft:air".equals(outId) || "minecraft:emerald".equals(outId)
                    || "minecraft:emerald_block".equals(outId)) return;
            java.util.List<class_1856> ings = new java.util.ArrayList<>(r.method_8117());
            if (ings.isEmpty()) {
                for (java.lang.reflect.Field f : r.getClass().getDeclaredFields()) {
                    if (!class_1856.class.isAssignableFrom(f.getType())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(r);
                        if (v instanceof class_1856 ing) ings.add(ing);
                    } catch (Throwable ignored) {
                        // 读不了就跳过这个字段
                    }
                }
            }
            if (ings.isEmpty()) {
                if (dev.guoke.tradepost.market.Cuisine.isCuisineNamespace(outId)) {
                    Tradepost.LOGGER.info("[tradepost] 配方跳过·getIngredients空 {}", outId);
                }
                return;
            }
            java.util.List<java.util.List<String>> slots = new java.util.ArrayList<>();
            for (class_1856 ing : ings) {
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (class_1799 st : ing.method_8105()) {                  // getMatchingStacks()
                    if (st == null || st.method_7960()) continue;
                    class_2960 iid = ITEMS.method_10221(st.method_7909());
                    if (iid != null) ids.add(iid.toString());
                }
                if (ids.isEmpty()) continue;   // 空槽位（成形配方里空的位置）：跳过这一格，别放弃整条配方
                slots.add(ids);
            }
            if (slots.isEmpty()) return;     // 所有材料槽都空才算废配方
            recs.add(new dev.guoke.tradepost.market.RecipePricer.Rec(outId, out.method_7947(), slots));
            // 装备要回收，但材料价常常算不全（见 gearBase 的注释）：另存一份"这件装备用了哪些材料"
            if (CATALOG.banned(outId)) GEAR_RECIPES.putIfAbsent(outId, slots);
        } catch (Throwable ignored) {
            // 某个模组的自定义配方不让读就跳过它
        }
    }


    // ── 配方定价的"分片预热"（教训：绝不能在别的线程跑） ─────────────────────
    // 2026-11-13 01:00 那次服务端卡十分钟：后台线程在建 TagKey 时握着 Guava Interner
    // 的段锁，而登录期的服务端/渲染线程也要建 TagKey ⇒ 三方互相等死。
    // 现在整件事都排到服务端 tick 上，每 tick 只做几毫秒。
    private interface Scanner { int step(int max); }

    private static Scanner WARM_SCAN;
    private static java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> WARM_RECS;
    private static class_3218 WARM_WORLD;
    private static class_3218 WARM_PENDING;
    private static int WARM_DELAY = -1;

    /**
     * 排队等预热：等玩家真的进了世界、再过 {@code delayTicks} 个 tick 才开始。
     * <p>登录那几十秒客户端也在建 TagKey（原版/别的模组加载生物群系标签），跟我们在同一把
     * Guava Interner 段锁上挤，会互相堵死（2026-11-13 凌晨两次"一进世界就卡住"就是这么来的）。
     */
    public static void requestWarmUp(class_3218 world, int delayTicks) {
        if (world == null || WARM_SCAN != null || WARM_PENDING != null) return;
        WARM_PENDING = world;
        WARM_DELAY = Math.max(0, delayTicks);
    }

    /** 服务器起来时登记预热任务；之后每 tick 由 {@link #tickWarmUp()} 推一小片。 */
    public static void beginWarmUp(class_3218 world) {
        if (world == null || WARM_SCAN != null) return;
        if (recipeLoaded && world == recipeWorld) return;
        final class_1863 rm = world.method_8433();
        final class_5455 lookup = world.method_30349();
        final var iter = rm.method_8126().iterator();
        final java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs = new java.util.ArrayList<>();
        WARM_WORLD = world;
        WARM_RECS = recs;
        WARM_SCAN = max -> {
            int n = 0;
            while (iter.hasNext() && n < max) {
                var entry = iter.next();
                n++;
                try {
                    var r = entry.comp_1933();
                    addRecipe(recs, r, lookup);
                } catch (Throwable ignored) {
                    // 某个模组的自定义配方不让读就跳过它
                }
            }
            return n;
        };
    }

    /** 服务端每 tick 调一次：做 2 毫秒或 400 条，扫完立刻算价。 */
    public static void tickWarmUp() {
        if (WARM_PENDING != null) {                      // 还没到点：等玩家进世界之后再说
            if (WARM_DELAY > 0) { WARM_DELAY--; return; }
            class_3218 w = WARM_PENDING;
            WARM_PENDING = null;
            WARM_DELAY = -1;
            beginWarmUp(w);
        }
        Scanner sc = WARM_SCAN;
        if (sc == null) return;
        long t0 = System.nanoTime();
        int done = 0;
        while (done < 400 && System.nanoTime() - t0 < 2_000_000L) {
            int n = sc.step(100);
            if (n <= 0) { finishWarmUp(); return; }
            done += n;
        }
    }

    private static void finishWarmUp() {
        WARM_SCAN = null;
        class_3218 world = WARM_WORLD;
        java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs = WARM_RECS;
        WARM_WORLD = null;
        WARM_RECS = null;
        if (world == null || recs == null) return;
        try {
            long t0 = System.nanoTime();
            runPricing(world, recs);
            Tradepost.LOGGER.info("[tradepost] 配方定价预热完成：{} ms（分片跑，主线程没卡住）",
                    (System.nanoTime() - t0) / 1_000_000L);
        } catch (Throwable t) {
            recipeLoaded = true;
            Tradepost.LOGGER.warn("[tradepost] 配方定价预热失败（开界面时会再试一次）", t);
        }
    }

    /** 扫完配方之后的重活：建拆解表、算成本价、填菜酒池。 */
    private static void runPricing(class_3218 world, java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs) {
            RECIPES = recs;
            DECOMP = dev.guoke.tradepost.market.Decompose.build(
                    recs, Bridge::basePrice,
                    dev.guoke.tradepost.market.Economy.current().worldMinBase, 6);
            INDEX_TABLE = null;
            INDEX_STAMP = Long.MIN_VALUE;
            dev.guoke.tradepost.market.Chain.setRecipes(recs);   // 制作链联动要用同一份配方
            java.util.Map<String, Double> prices = dev.guoke.tradepost.market.RecipePricer.compute(
                    CATALOG.fixedIds(), recs, CATALOG::seedValue,
                    rec -> {
                        // 食物不做工业折价：做饭应该增值（熟 > 生），打折会让熟肉比生肉还便宜。
                        // craftPenalty 只给工具/装备/方块这类工业品，防止"买材料合成再卖"刷钱。
                        return isEdible(rec.output())
                                ? 1.0
                                : dev.guoke.tradepost.market.Economy.current().craftPenalty;
                    },
                    rec -> isDish(rec.output())
                            ? dev.guoke.tradepost.market.Cuisine.minPrice(rec.output(), rec.slots()) : 0,
                    dev.guoke.tradepost.market.Economy.current().stepLabor);
            CATALOG.setRecipePrices(prices);

            // 菜与酒的池子：每个村庄的杂货位从这里抽，种类上千，几乎不会撞
            java.util.TreeSet<String> dishes = new java.util.TreeSet<>();
            for (var rec : recs) {
                if (!prices.containsKey(rec.output())) continue;
                if (isDish(rec.output())) dishes.add(rec.output());
            }
            CATALOG.setCuisinePool(new java.util.ArrayList<>(dishes));

            recipeWorld = world;
            try {
                indexSeed = world.method_8412();
            } catch (Throwable ignored) {
                indexSeed = 0L;
            }
            recipeLoaded = true;
            Tradepost.LOGGER.info("[tradepost] 配方定价：扫到 {} 个配方，{} 件物品拿到成本价（其中菜与酒 {} 种）",
                    recs.size(), prices.size(), dishes.size());
            dumpHotBasePrices();
    }

    /** 价格来源缩成一个词，审计表和命令行都用它（"为什么这件货是这个价"）。 */
    static String srcOf(String id) {
        try {
            String s = CATALOG.sourceOf(id);
            if (s.startsWith("手写")) return "手写";
            if (s.startsWith("黑名单")) return "黑名单";
            if (s.startsWith("标签")) return "标签";
            if (s.startsWith("配方")) return "配方";
            if (s.startsWith("类别默认价")) return "类别";
            if (s.startsWith("默认兜底")) return "兜底";
            if (s.contains("不收")) return "不收";
            return s.length() > 16 ? s.substring(0, 16) : s;
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 把整合包里所有物品 + 当前生效的价导成草稿，方便挑真正想当货的条目去手工定价。 */
    /** /tradepost cat [类别]：不带参数列出所有类别和命中件数，带参数看具体命中哪些物品。 */
    public static int cmdCategories(class_2168 src, String id) {
        MarketData d = Tradepost.data();
        if (d == null) {
            src.method_9226(() -> class_2561.method_43470("市场数据还没加载"), false);
            return 0;
        }
        if (id != null && id.equalsIgnoreCase("audit")) return categoryAudit(src, d);
        StringBuilder sb = new StringBuilder();
        if (id == null || id.isBlank()) {
            java.util.List<String> keys = new java.util.ArrayList<>(d.categories.ids());
            java.util.Collections.sort(keys);
            sb.append("类别 ").append(keys.size()).append(" 个：");
            for (String k : keys) {
                var c = d.categories.get(k);
                int n = catalog().category(k).size();
                sb.append("\n ").append(k).append("  ").append(c == null ? "" : c.display())
                  .append(" → ").append(n).append(" 件");
            }
        } else {
            java.util.List<String> items = catalog().category(id);
            var c = d.categories.get(id);
            sb.append("类别 ").append(id).append(c == null ? "（未定义）" : "  " + c.display())
              .append(" → ").append(items.size()).append(" 件");
            for (int i = 0; i < Math.min(items.size(), 20); i++) sb.append("\n  ").append(items.get(i));
        }
        String out = sb.toString();
        src.method_9226(() -> class_2561.method_43470(out), false);
        return 1;
    }

    /** 导出"每件货被哪个类别认领"的清单，兜底类用 misc 标出来，方便批量补规则。 */
    /** /tradepost audit csv：把每件货的分类与价格来源导成 CSV（含"兜底"那一档）。 */
    public static int auditCsv(class_2168 src) {
        return categoryAudit(src, Tradepost.data());
    }

    private static int categoryAudit(class_2168 src, MarketData d) {
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("tradepost").resolve("output");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("category_audit.csv");
            if (src.method_9225() != null) ensureRecipePrices(src.method_9225());   // 价格来源列不能是空的

            java.util.Map<String, String> claim = new java.util.HashMap<>();
            for (String k : d.categories.ids()) {
                if (dev.guoke.tradepost.market.Categories.MISC.equals(k)) continue;
                for (String it : catalog().category(k)) claim.putIfAbsent(it, k);
            }
            java.util.List<String> miscList = catalog().category(dev.guoke.tradepost.market.Categories.MISC);
            java.util.Set<String> misc = new java.util.HashSet<>(miscList);

            StringBuilder out = new StringBuilder("id,name,base,category,src\n");
            java.util.TreeSet<String> ids = new java.util.TreeSet<>(claim.keySet());
            ids.addAll(misc);
            int rows = 0;
            for (String it : ids) {
                String cat = claim.get(it);
                out.append(csv(it)).append(',')
                   .append(csv(nameKey(it))).append(',')
                   .append(basePrice(it)).append(',')
                   .append(cat == null ? dev.guoke.tradepost.market.Categories.MISC : cat).append(',')
                   .append(csv(srcOf(it))).append('\n');
                rows++;
            }
            java.nio.file.Files.writeString(file, out.toString(), java.nio.charset.StandardCharsets.UTF_8);
            String msg = "类别审计：" + rows + " 件，其中兜底 " + misc.size() + " 件\n→ " + file;
            src.method_9226(() -> class_2561.method_43470(msg), false);
            return 1;
        } catch (Throwable t) {
            src.method_9226(() -> class_2561.method_43470("审计导出失败：" + t), false);
            return 0;
        }
    }

    public static int dumpCatalog(class_2168 src) {
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("tradepost");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("items.generated.json");

            java.util.TreeMap<String, Double> priced = new java.util.TreeMap<>();
            java.util.TreeMap<String, Double> derived = new java.util.TreeMap<>();
            java.util.TreeMap<String, String> rarities = new java.util.TreeMap<>();
            ITEMS.method_10220().forEach(it -> {                     // Registry.stream()
                class_2960 id = ITEMS.method_10221(it);              // getId(Item)
                if (id == null) return;
                String key = id.toString();
                double explicit = Tradepost.valueOf(key);
                if (explicit > 0) priced.put(key, explicit);
                else {
                    double v = CATALOG.value(key);
                    if (v > 0) {
                        priced.put(key, v);
                        derived.put(key, v);
                        String r = TagCatalog.rarityOf(key);
                        if (r != null && !r.equals("common")) rarities.put(key, r);
                    }
                }
            });

            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"_comment\": \"手工定价的进 values.json；这份只是草稿，derived 是标签兜底推出来的\",\n");
            sb.append("  \"_derived\": ").append(derived.size())
              .append(", \"_total\": ").append(priced.size()).append(",\n  \"values\": {\n");
            int n = 0;
            for (var e : priced.entrySet()) {
                sb.append("    \"").append(e.getKey()).append("\": ").append(e.getValue());
                sb.append(++n < priced.size() ? ",\n" : "\n");
            }
            sb.append("  },\n  \"_rarity\": {\n");
            int m = 0;
            for (var e : rarities.entrySet()) {
                sb.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"");
                sb.append(++m < rarities.size() ? ",\n" : "\n");
            }
            sb.append("  }\n}\n");
            java.nio.file.Files.writeString(file, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            send(src, "已导出 " + priced.size() + " 个物品（其中标签兜底 " + derived.size()
                    + " 个，其中 " + rarities.size() + " 个按稀有度加过价）到 " + file);
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] dump 失败", e);
            send(src, "导出失败，看日志");
            return 0;
        }
    }

    /**
     * 全部物品一览：注册表里每个物品一行，含真实价格和价格来源，写成 CSV（Excel 直接开）。
     * 这是「这份名单到底覆盖了多少」的权威答案 —— 离线导出的表只能覆盖标签价和手写价。
     */
    public static int dumpAll(class_2168 src) {
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("tradepost");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("items-all.csv");
            if (src != null && src.method_9225() != null) ensureRecipePrices(src.method_9225());

            java.util.TreeMap<String, String> rows = new java.util.TreeMap<>();
            int[] count = new int[6];   // 0 手写 1 标签 2 配方 3 兜底 4 黑名单 5 合计
            ITEMS.method_10220().forEach(it -> {                     // Registry.stream()
                class_2960 id = ITEMS.method_10221(it);              // getId(Item)
                if (id == null) return;
                String key = id.toString();
                String source;
                boolean banned = CATALOG.banned(key);
                double base = Tradepost.valueOf(key);
                if (base > 0) { source = "手写"; count[0]++; }
                else if (banned) { source = "黑名单"; count[4]++; }
                else {
                    java.util.Map<String, Double> d = CATALOG.derived();
                    if (d != null && d.containsKey(key)) { source = "标签"; count[1]++; }
                    else {
                        java.util.Map<String, Double> rp = CATALOG.recipePrices();
                        if (rp != null && rp.containsKey(key)) { source = "配方"; count[2]++; }
                        else { source = "兜底"; count[3]++; }
                    }
                }
                base = basePrice(key);
                boolean edible = false, dish = false, sellable = false;
                try { edible = isEdible(key); } catch (Throwable ignored) { }
                try { dish = CATALOG.dish(key); } catch (Throwable ignored) { }
                try { sellable = CATALOG.canSell(key); } catch (Throwable ignored) { }
                double cap = base > 0 ? capOf(key, base, false) : 0;
                String name = hasZhName(key) ? nameKey(key) : "";
                count[5]++;
                rows.put(key, csv(key) + "," + csv(name) + "," + fmt(base) + "," + source + ","
                        + (banned ? "是" : "") + "," + (edible ? "是" : "") + "," + (dish ? "是" : "")
                        + "," + (sellable ? "是" : "") + "," + fmt(cap) + "\n");
            });

            StringBuilder sb = new StringBuilder();
            sb.append('\uFEFF');
            sb.append("物品id,中文名,基础价,价格来源,黑名单,可食用,菜/酒,有中文名,村庄库存上限\n");
            for (String v : rows.values()) sb.append(v);
            java.nio.file.Files.writeString(file, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            send(src, "已导出全部 " + count[5] + " 个物品到 " + file);
            send(src, "价格来源：手写 " + count[0] + " ｜ 标签 " + count[1] + " ｜ 配方 " + count[2]
                    + " ｜ 兜底 0.05 " + count[3] + " ｜ 黑名单 " + count[4]);
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] dumpall 失败", e);
            send(src, "导出失败，看日志");
            return 0;
        }
    }

    /** CSV 字段转义：有逗号或引号就加引号 */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String fmt(double v) {
        if (v == 0) return "0";
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    // —— 自动产业：按物品标签聚类成产业 ——

    /**
     * 扫整合包里的标签，够数量的就落一个产业 JSON 到 <config>/tradepost/market/industries/。
     * force=false 时如果已经有 auto_*.json 就跳过（不覆盖玩家改过的）。
     * 返回 null 表示"已存在，跳过"。
     */
    public static dev.guoke.tradepost.market.AutoIndustry.Result generateAuto(boolean force) {
        java.nio.file.Path dir = Tradepost.dataDir().resolve("industries");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 建不了产业目录 {}", dir, e);
            return null;
        }
        if (!force && hasAutoFiles(dir)) return null;

        ensureTagPrices();          // 新标签先补个基准价，否则展开出来全是没价物品
        clearCatalogCache();

        java.util.Set<String> used = new java.util.HashSet<>();
        MarketData d = Tradepost.data();
        if (d != null) {
            for (MarketData.Industry ind : d.industries) {
                if (ind.supply() != null) for (String s : ind.supply()) if (s.startsWith("#")) used.add(s);
                if (ind.demand() != null) for (String s : ind.demand()) if (s.startsWith("#")) used.add(s);
            }
        }

        java.util.Map<String, List<String>> members = new java.util.LinkedHashMap<>();
        for (var g : dev.guoke.tradepost.market.AutoIndustry.GROUPS) members.put(g.tag(), tradeableItems(g.tag()));

        var res = dev.guoke.tradepost.market.AutoIndustry.generate(members, used, CATALOG::value);
        int written = 0;
        for (var draft : res.drafts()) {
            try {
                java.nio.file.Files.writeString(dir.resolve(draft.id() + ".json"),
                        dev.guoke.tradepost.market.AutoIndustry.toJson(draft),
                        java.nio.charset.StandardCharsets.UTF_8);
                written++;
            } catch (Exception e) {
                Tradepost.LOGGER.warn("[tradepost] 写不了产业 {}", draft.id(), e);
            }
        }
        Tradepost.LOGGER.info("[tradepost] 自动产业：写入 {} 个（标签已被手写产业占用 {} 个，物品太少跳过 {} 个）",
                written, res.covered().size(), res.thin().size());
        return res;
    }

    private static boolean hasAutoFiles(java.nio.file.Path dir) {
        try (var s = java.nio.file.Files.list(dir)) {
            return s.anyMatch(p -> p.getFileName().toString().startsWith("auto_"));
        } catch (Exception e) {
            return false;
        }
    }

    /** 标签 → 能当货的物品：有价、可堆叠（工具盔甲一剑一件，不适合当批量货）。 */
    private static List<String> tradeableItems(String tag) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String id : CATALOG.expand(tag)) {
            if (id.equals("minecraft:air") || id.equals("minecraft:emerald") || id.equals("minecraft:emerald_block")) continue;
            try {
                class_1792 it = ITEMS.method_10223(class_2960.method_60654(id));
                if (it == null || it.method_7882() <= 1) continue;     // Item.getMaxCount()
            } catch (Exception e) {
                continue;
            }
            if (CATALOG.value(id) <= 0) continue;
            out.add(id);
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** 把自动产业的标签价补进 config 的 tag_values.json（已存在的键不动，玩家手调优先）。 */
    private static void ensureTagPrices() {
        java.nio.file.Path ext = Tradepost.dataDir().resolve("tag_values.json");
        java.util.Map<String, Double> cur = TagCatalog.readTagValues();
        int added = 0;
        for (var e : dev.guoke.tradepost.market.AutoIndustry.groupPrices().entrySet()) {
            if (!cur.containsKey(e.getKey())) {
                cur.put(e.getKey(), e.getValue());
                added++;
            }
        }
        if (added == 0) return;
        StringBuilder sb = new StringBuilder("{\n");
        int n = 0;
        for (var e : cur.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": ").append(e.getValue());
            sb.append(++n < cur.size() ? ",\n" : "\n");
        }
        sb.append("}\n");
        try {
            java.nio.file.Files.createDirectories(ext.getParent());
            java.nio.file.Files.writeString(ext, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            Tradepost.LOGGER.info("[tradepost] 补充了 {} 个标签基准价到 {}", added, ext);
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 写 tag_values.json 失败", e);
        }
    }

    /** /tradepost propose：强制重新聚类一次，并把结果说清楚 */
    public static int propose(class_2168 src) {
        var res = generateAuto(true);
        if (res == null) {
            send(src, "自动产业生成失败，看日志");
            return 0;
        }
        MarketData d = Tradepost.reloadData();
        StringBuilder sb = new StringBuilder("自动产业：新建/覆盖 " + res.drafts().size() + " 个");
        int shown = 0;
        for (var draft : res.drafts()) {
            if (shown++ >= 6) { sb.append(" …"); break; }
            sb.append(" | ").append(draft.display()).append(" ").append(draft.items()).append(" 种");
        }
        sb.append("；标签已被占用 ").append(res.covered().size()).append(" 个");
        if (!res.thin().isEmpty()) sb.append("；物品太少跳过 ").append(String.join(", ", res.thin()));
        sb.append("（现有产业 ").append(d == null ? "?" : d.industries.size()).append(" 个，/tradepost reload 后生效）");
        send(src, sb.toString());
        return 1;
    }

    // —— 报价 ——

    public static class_1914 offer(OfferSpec s) {
        class_1792 giveItem = item(s.giveItem());
        class_1792 getItem = item(s.getItem());
        if (giveItem == null || getItem == null) return null;
        class_1799 result = new class_1799(getItem, s.getCount());
        class_9306 first = new class_9306(giveItem, s.giveCount());   // TradedItem(Item, count)
        java.util.Optional<class_9306> second = java.util.Optional.empty();
        if (s.hasSecond()) {
            class_1792 secondItem = item(s.secondItem());
            if (secondItem != null) second = java.util.Optional.of(new class_9306(secondItem, s.secondCount()));
        }
        return new class_1914(first, second, result, s.maxUses(), 1, 0.0F);
    }

    /** 用本村行情重写村民的交易表；世界/数据没准备好时原样返回。 */
    public static void overrideOffers(class_1646 villager, class_1916 target) {
        MarketData data = Tradepost.data();
        if (data == null) return;
        if (!(villager.method_5770() instanceof class_3218 world)) return;
        try {
            int x = (int) Math.floor(villager.method_23317());
            int z = (int) Math.floor(villager.method_23321());
            long seed = world.method_8412();
            long day = world.method_8532() / 24000L;
            boolean station = stationNear(world, x, z);
            int y = (int) Math.floor(villager.method_23318());
            String vkey = vkey(world, x, y, z);
            TradeStore store = store(world);
            store.settle(vkey, day);
            // 群系同样取锚点（走 ctx），不然玩家挪到群系边界这边，村民货单也会跟着抖
            var biome = ctx(world, x, y, z).biome;
            List<OfferSpec> specs = VillageMarket.offers(data, seed, x, z, day, station,
                    view(store, vkey, day, station), biome, CATALOG);
            if (specs.isEmpty()) return;
            target.clear();
            for (OfferSpec s : specs) {
                class_1914 o = offer(s);
                if (o != null) target.add(o);
            }
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 生成报价失败，保留原版交易", e);
        }
    }

    // —— 集市：看得见的价格 + 一键卖出 ——

    /** /tradepost audit：到底有多少物品能交易、被黑名单拦了多少（判断"种类变少"的元凶） */
    /** /tradepost rarity：档位分布 + 传奇清单 + 导出 CSV（只读，不改任何东西） */
    /** 自检：组件货造出来的物品是否真的带组件（附魔书 / 药水） */
    public static int cmdGoods(class_2168 src) {
        class_3222 p;
        try {
            p = src.method_9207();
        } catch (Exception e) {
            return 0;
        }
        try {
            net.minecraft.class_7225.class_7874 lookup = p.method_5770().method_30349();
            class_1799 book = dev.guoke.tradepost.market.Goods.of("minecraft:enchanted_book", 1,
                    "minecraft:mending", 1, "", 0, 0, "", lookup);
            class_1799 pot = dev.guoke.tradepost.market.Goods.of("minecraft:potion", 1,
                    "", 0, "minecraft:strong_healing", 0, 0, "", lookup);
            boolean be = comp(book, "minecraft:stored_enchantments") != null;
            boolean pe = comp(pot, "minecraft:potion_contents") != null;
            say(p, "组件自检：附魔书 " + (be ? "ok" : "空（失败）")
                    + " · 药水 " + (pe ? "ok" : "空（失败）"));
        } catch (Throwable t) {
            say(p, "组件自检失败：" + t);
        }
        return 1;
    }

    /**
     * 只读取一个组件。
     *
     * <p>别用 {@code class_1799.method_57381} —— 它的字节码是 {@code ComponentMap.method_57939}，
     * 那个方法会把组件"取走"（先写时复制，然后 put(Optional.empty) / remove，再把旧值返回）。
     * 也就是说每读一次，物品上就少一个组件；附魔书被剥成白板就是这么来的。
     * 真正只读的路是 {@code getComponents().method_57829(type)}（ComponentMap 里纯 map.get + 回落 base）。
     */

    public static Object compRead(class_1799 st, class_9331<?> type) {
        if (st == null || type == null) return null;
        try {
            net.minecraft.class_9323 map = st.method_57353();
            return map == null ? null : map.method_57829(type);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object comp(class_1799 st, String id) {
        try {
            class_9331<?> t = class_7923.field_49658.method_10223(class_2960.method_60654(id));
            return t == null ? null : compRead(st, t);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int cmdRarity(class_2168 src) {
        int[] hist = new int[5];
        java.util.List<String> legendary = new java.util.ArrayList<>();
        StringBuilder out = new StringBuilder("id,tier,rarity,base,gate,uses,source\n");
        int total = 0;
        try {
            for (class_1792 it : ITEMS) {
                class_2960 idn = ITEMS.method_10221(it);
                if (idn == null) continue;
                String id = idn.toString();
                double v = CATALOG.value(id);
                if (v <= 0) continue;
                total++;
                int t = CATALOG.rarity(id);
                hist[Math.max(1, Math.min(4, t))]++;
                if (t >= dev.guoke.tradepost.market.Rarity.LEGENDARY && legendary.size() < 25) legendary.add(id);
                out.append(id).append(',').append(t).append(',')
                        .append(dev.guoke.tradepost.market.Rarity.name(t)).append(',')
                        .append(Math.round(v * 100) / 100.0).append(',')
                        .append(dev.guoke.tradepost.market.Rarity.gate(t)).append(',')
                        .append(dev.guoke.tradepost.market.Rarity.uses(t)).append(',')
                        .append(dev.guoke.tradepost.market.Rarity.name(t)).append('\n');
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 稀有度统计失败", t);
        }
        String dirMsg;
        try {
            java.nio.file.Path outDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("tradepost").resolve("output");
            java.nio.file.Files.createDirectories(outDir);
            java.nio.file.Path f = outDir.resolve("rarity_report.csv");
            java.nio.file.Files.writeString(f, out.toString(), java.nio.charset.StandardCharsets.UTF_8);
            dirMsg = "\n已导出 " + f;
        } catch (Throwable t) {
            dirMsg = "\n导出失败：" + t.getMessage();
        }
        send(src, "稀有度（可交易 " + total + " 件）：常见 " + hist[1] + "｜稀缺 " + hist[2]
                + "｜珍稀 " + hist[3] + "｜传奇 " + hist[4] + dirMsg
                + (legendary.isEmpty() ? "" : "\n传奇：" + String.join("，", legendary)));
        return total;
    }

    public static int audit(class_2168 src) {
        int total = 0, hand = 0, banned = 0, tradeable = 0, tagOnly = 0, noZh = 0;
        java.util.Map<String, int[]> byNs = new java.util.HashMap<>();   // 命名空间 -> {总数, 被拦}
        java.util.Map<String, Integer> srcs = new java.util.HashMap<>();  // 价格来源 -> 件数
        java.util.List<String> noZhSample = new java.util.ArrayList<>();
        try {
            // 配方价平时是"第一次开集市"才算的（要读全服配方表，1~2 秒），审计想看到真数就先算一遍
            if (src.method_9225() != null) ensureRecipePrices(src.method_9225());
            for (class_1792 it : ITEMS) {
                class_2960 idn = ITEMS.method_10221(it);
                if (idn == null) continue;
                String id = idn.toString();
                total++;
                srcs.merge(srcOf(id), 1, Integer::sum);
                int colon = id.indexOf(':');
                String ns = colon > 0 ? id.substring(0, colon) : id;
                int[] nsStat = byNs.computeIfAbsent(ns, k -> new int[2]);
                nsStat[0]++;
                double h = Tradepost.valueOf(id);
                if (h > 0) hand++;
                boolean ban = CATALOG.banned(id);
                if (ban) {
                    banned++;
                    nsStat[1]++;
                }
                double v = CATALOG.value(id);
                if (v > 0) {
                    tradeable++;
                    if (h <= 0) tagOnly++;
                    if (!CATALOG.canSell(id)) {
                        noZh++;
                        if (noZhSample.size() < 4) noZhSample.add(id);
                    }
                }
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 审计失败", t);
        }
        dev.guoke.tradepost.market.Economy eco = dev.guoke.tradepost.market.Economy.current();
        java.util.Map<String, Double> rp = CATALOG.recipePrices();
        int recipePriced = rp == null ? 0 : rp.size();
        // 谁把黑名单撑到这么大：按命名空间排个座次，改配置时知道该动哪一条
        java.util.List<java.util.Map.Entry<String, int[]>> hot = new java.util.ArrayList<>(byNs.entrySet());
        hot.removeIf(e -> e.getValue()[1] <= 0);
        hot.sort((a, b) -> Integer.compare(b.getValue()[1], a.getValue()[1]));
        StringBuilder ns = new StringBuilder();
        for (int i = 0; i < hot.size() && i < 6; i++) {
            java.util.Map.Entry<String, int[]> e = hot.get(i);
            if (i > 0) ns.append(" / ");
            ns.append(e.getKey()).append(' ').append(e.getValue()[1]).append('/').append(e.getValue()[0]);
        }
        String msg = "物品审计：注册 " + total + " 件｜手写价 " + hand + "｜标签/兜底价 " + tagOnly
                + "｜配方价 " + recipePriced + "（" + (rp == null ? "未计算" : "已计算") + "）"
                + "｜黑名单拦 " + banned + "｜可交易 " + tradeable
                + "｜无中文名不上架 " + noZh
                + "\n价格来源：手写 " + srcs.getOrDefault("手写", 0) + "｜标签 " + srcs.getOrDefault("标签", 0)
                + "｜配方 " + srcs.getOrDefault("配方", 0) + "｜类别 " + srcs.getOrDefault("类别", 0)
                + "｜兜底 " + srcs.getOrDefault("兜底", 0) + "｜不收 " + srcs.getOrDefault("不收", 0)
                + "\n黑名单大户（拦/共）：" + (ns.length() == 0 ? "无" : ns)
                + (noZhSample.isEmpty() ? "" : "\n没中文名的举例：" + noZhSample)
                + "\n货架：村庄 " + eco.villageOffers + " 条（供给 " + eco.supplySlots + " 类 × " + eco.itemsPerEntry
                + " 件 + 杂货 " + eco.miscSlots + " 位）｜驿站 " + eco.stationOffers + " 条"
                + "｜杂货池 " + CATALOG.all().size() + " 件"
                + "\n分类/兜底清单：/tradepost cat audit（导出 config/tradepost/output/category_audit.csv）";
        src.method_9226(() -> class_2561.method_43470(msg), false);
        return 1;
    }

    /** 基础价（手写价优先，其次标签兜底价） */
    /** 对外 API 用：这个 id 是否被黑名单拦住（拦住的普通物品不进买页、不进收购清单） */
    public static boolean isBanned(String id) {
        try {
            return id != null && CATALOG.banned(id);
        } catch (Throwable t) {
            return false;
        }
    }

    public static double basePrice(String id) {
        double v = Tradepost.valueOf(id);
        if (v > 0) return v;
        return CATALOG.value(id);
    }

    // —— 村庄身份：认世界生成留下的结构，玩家放钟/放床伪造不了 ——

    /** 自己的村庄标签：= 原版 #minecraft:village + 没打标签的村子（天堂天使村、末地哨站、霍比特村…） */
    private static final class_6862<class_3195> VILLAGE_TAG =
            class_6862.method_40092(class_7924.field_41246, class_2960.method_60654("tradepost:village"));

    /** 区块 -> 村庄 id 的缓存（"s<cx>_<cz>"，无村庄记空串） */
    private static final java.util.Map<Long, String> VILLAGE_CACHE = new java.util.HashMap<>();
    private static class_3218 villageCacheWorld;

    public static void clearVillageCache() {
        VILLAGE_CACHE.clear();
        VS_CACHE.clear();
        TOWN.clear();
        REGIONS.clear();
        BIOME_CACHE.clear();
        townWorld = null;
        vsCacheWorld = null;
        villageCacheWorld = null;
        RoadNet.clear();
    }

    /**
     * 脚下是不是真村庄：查世界生成留下的结构（#minecraft:village）。
     * 玩家自己放的钟、堆的床和工作台都不算，野外永远为 null。
     *
     * @return 村庄 id（"s<chunkX>_<chunkZ>"，取结构起点区块），不在村里返回 null
     */
    public static String villageKey(class_3218 world, int x, int y, int z) {
        try {
            return townKey(world, villageStart(world, x, y, z));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 村庄 id：结构起点区块 + 包围盒中心（同一区块叠了两座村也不会撞 key） */
    static String keyOf(class_3449 start) {
        class_1923 cp = start.method_34000();
        class_2382 c = start.method_14969().method_14659();
        return "s" + cp.field_9181 + "_" + cp.field_9180 + "_" + c.method_10263() + "_" + c.method_10260();
    }

    /** 某一点（商路端点之类）落在哪座村，返回村庄 key；不在村里返回 null */
    static String keyAtStart(class_3218 world, int x, int y, int z) {
        return townKey(world, villageStart(world, x, y, z));
    }

    /**
     * 一处"地方"的 key：几座结构叠在一处时取领头那座的 key ——
     * 价格/库存/银库/锚点全用它，站在这座村哪个角落（哪座结构里）都是同一个市场。
     */
    static String townKey(class_3218 world, class_3449 vs) {
        if (vs == null) return null;
        Town t = townOf(world, vs);
        return t != null ? t.key : keyOf(vs);
    }

    // —— 一处"地方"（Town）：包围盒挨在一起的几座村庄结构，其实是同一个市场 ——
    //
    // 玩家手放、或结构生成撞车（原版村 + CTOV 村叠在一处）时，世界里有 2~3 个结构起点，
    // 每个都会算出一份几乎相同的屋子清单 —— 于是"一个村被当成两个村"，户数还一样。
    // 这里把它们并成一个地方：同一套价格/库存/银库/产业，户数与占地按片段的包围盒去重后合并。

    static final class Town {
        final String key;                        // 领头那座村的 key（成员里字典序最小的，谁问都一样）
        final class_3449 lead;                   // 领头那座村，定价锚点用它
        final java.util.List<class_3449> members;
        final int[] stat;                        // {户数, 样式数}（多座结构按片段包围盒去重后合并）
        final long area;                         // 占地（取最大那座，重叠部分不重复算）
        final boolean merged;                    // 不止一座结构

        Town(String key, class_3449 lead, java.util.List<class_3449> members, int[] stat, long area) {
            this.key = key;
            this.lead = lead;
            this.members = members;
            this.stat = stat;
            this.area = area;
            this.merged = members.size() > 1;
        }
    }

    private static final java.util.Map<String, Town> TOWN = new java.util.HashMap<>();
    // （REGIONS 见下面 samePlace 之后）
    private static class_3218 townWorld;

    /** 两个结构起点区块相差不超过 n 个区块 */
    private static boolean chunkNear(class_1923 a, class_1923 b, int n) {
        return Math.abs(a.field_9181 - b.field_9181) <= n && Math.abs(a.field_9180 - b.field_9180) <= n;
    }

    // —— 房屋统计：数结构片段里的"屋子"（占地 ≥20 格²、高 ≥4 的才算，道路/广场/树都过不了这关）——

    private static final java.util.Map<String, int[]> VSTAT = new java.util.HashMap<>();

    /**
     * @return {屋子栋数, 样式数}。样式按"占地分档 × 高度分档"算 —— 原版 jigsaw 拿不到池元素 id，
     *         尺寸是最稳的近似（小房/大房/教堂/农场…尺寸天然不同）。
     */
    static int[] villageStat(class_3449 vs) {
        if (vs == null) return new int[]{0, 0};
        String k = keyOf(vs);
        int[] hit = VSTAT.get(k);
        if (hit != null) return hit;
        int houses = 0;
        java.util.Set<Integer> kinds = new java.util.HashSet<>();
        try {
            java.util.List<class_3443> pieces = vs.method_14963();
            if (pieces != null) {
                for (class_3443 p : pieces) {
                    if (p == null) continue;
                    class_3341 b = p.method_14935();
                    if (b == null) continue;
                    long fp = (long) b.method_35414() * (long) b.method_14663();
                    int h = b.method_14660();
                    if (fp < 20 || h < 4) continue;
                    houses++;
                    kinds.add(Integer.valueOf((int) Math.min(99, fp / 12) * 100 + Math.min(9, h / 3)));
                }
            }
        } catch (Throwable ignored) {
            // 结构片段读不出来就当没有（下面按占地估）
        }
        int[] out = new int[]{houses, kinds.size()};
        if (VSTAT.size() > 4096) VSTAT.clear();
        VSTAT.put(k, out);
        return out;
    }

    /** 包围盒占地（格²，只看水平面） */
    static long areaOf(class_3341 b) {
        return (long) b.method_35414() * (long) b.method_14663();
    }

    /** 两个包围盒在水平面上的重叠面积（格²），不重叠返回 0 */
    static long overlapOf(class_3341 a, class_3341 b) {
        long ox = Math.min(a.method_35418(), b.method_35418()) - Math.max(a.method_35415(), b.method_35415()) + 1L;
        long oz = Math.min(a.method_35420(), b.method_35420()) - Math.max(a.method_35417(), b.method_35417()) + 1L;
        return ox <= 0 || oz <= 0 ? 0 : ox * oz;
    }

    /**
     * 这个村属于哪个"镇"。三座村挤在一处时（玩家手放、或结构生成撞车），
     * 它们其实是同一个市场：共用一片区域压力，标题也会标"镇"。
     * 判定：包围盒重叠面积 ≥ 较小那座的 30%。群名取群里字典序最小的 key（对称，谁问都一样）。
     *
     * @return null = 独门独户
     */
    static Town townOf(class_3218 world, class_3449 vs) {
        if (vs == null) return null;
        if (townWorld != world) {
            TOWN.clear();
            townWorld = world;
        }
        String k = keyOf(vs);
        Town hit = TOWN.get(k);
        if (hit != null) return hit;
        try {
            class_1923 scp = vs.method_34000();
            int sx = scp.field_9181 * 16 + 8, sz = scp.field_9180 * 16 + 8;
            for (Region rg : REGIONS) {
                if (rg.holds(sx, sz)) {
                    TOWN.put(k, rg.town);
                    return rg.town;
                }
            }
        } catch (Throwable ignored) {
            // 没有起点就拿不到，正常算子
        }
        // 先收一圈候选（±6 区块），再从新成员补一圈 —— 免得"站在这座村的另一头就并不到隔壁那座"
        java.util.LinkedHashMap<String, class_3449> cand = new java.util.LinkedHashMap<>();
        collectNear(world, vs, 6, cand);
        java.util.List<class_3449> first = new java.util.ArrayList<>(cand.values());
        for (class_3449 o : first) collectNear(world, o, 2, cand);
        java.util.List<class_3449> all = new java.util.ArrayList<>(cand.values());
        int n = all.size();
        int selfIdx = -1;
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            if (selfIdx < 0 && keyOf(all.get(i)).equals(k)) selfIdx = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (samePlace(all.get(j), all.get(i))) union(parent, i, j);
            }
        }
        if (selfIdx < 0) selfIdx = 0;
        int root = find(parent, selfIdx);
        java.util.List<class_3449> members = new java.util.ArrayList<>(3);
        for (int i = 0; i < n; i++) {
            if (find(parent, i) == root) members.add(all.get(i));
        }
        if (members.isEmpty()) members.add(vs);
        members.sort((a, b) -> keyOf(a).compareTo(keyOf(b)));
        class_3449 lead = members.get(0);
        java.util.Set<String> boxes = new java.util.HashSet<>();
        java.util.Set<Integer> kinds = new java.util.HashSet<>();
        int houses = 0;
        long area = 0;
        for (class_3449 m : members) {
            try {
                area = Math.max(area, areaOf(m.method_14969()));
            } catch (Throwable ignored) {
                // 没有包围盒就不算占地
            }
            try {
                java.util.List<class_3443> pieces = m.method_14963();
                if (pieces == null) continue;
                for (class_3443 p : pieces) {
                    if (p == null) continue;
                    class_3341 b = p.method_14935();
                    if (b == null) continue;
                    String sig = b.method_35415() + "_" + b.method_35416() + "_" + b.method_35417() + "_"
                            + b.method_35418() + "_" + b.method_35419() + "_" + b.method_35420();
                    if (!boxes.add(sig)) continue;      // 两座结构在同一处放了一样的屋子：只算一次
                    long fp = (long) b.method_35414() * (long) b.method_14663();
                    int h = b.method_14660();
                    if (fp < 20 || h < 4) continue;
                    houses++;
                    kinds.add(Integer.valueOf((int) Math.min(99, fp / 12) * 100 + Math.min(9, h / 3)));
                }
            } catch (Throwable ignored) {
                // 结构片段读不出来就跳过这座
            }
        }
        Town t = new Town(keyOf(lead), lead, members, new int[]{houses, kinds.size()}, area);
        if (TOWN.size() > 4096) {
            TOWN.clear();
            REGIONS.clear();
        }
        StringBuilder names = new StringBuilder();
        for (class_3449 m : members) {
            TOWN.put(keyOf(m), t);
            names.append(keyOf(m)).append(' ');
            try {
                class_3341 b = m.method_14969();
                REGIONS.add(new Region(b.method_35415() - 12, b.method_35417() - 12,
                        b.method_35418() + 12, b.method_35420() + 12, t));
            } catch (Throwable ignored) {
                // 没有包围盒就不登记地盘
            }
        }
        if (REGIONS.size() > 8192) REGIONS.clear();
        Tradepost.LOGGER.info("[tradepost] 市场 {}：{} 座结构（{}）· {} 户 {} 式 · 占地 {}",
                t.key, members.size(), names.toString().trim(), houses, kinds.size(), area);
        return t;
    }

    /** 把这座结构周围 radius 区块内的村庄结构收进 map（按 key 去重） */
    private static void collectNear(class_3218 world, class_3449 vs, int radius,
                                    java.util.Map<String, class_3449> out) {
        if (vs == null) return;
        out.putIfAbsent(keyOf(vs), vs);
        try {
            class_1923 cp = vs.method_34000();
            for (class_3449 o : villagesNear(world, cp.field_9181, cp.field_9180, radius)) {
                if (o != null) out.putIfAbsent(keyOf(o), o);
            }
        } catch (Throwable ignored) {
            // 扫不动就算了
        }
    }

    /** 两座结构是不是同一处：包围盒相交（或只差 pad 格），或起点区块相邻（≤2 区块） */
    private static boolean samePlace(class_3449 a, class_3449 b) {
        try {
            class_3341 x = a.method_14969(), y = b.method_14969();
            if (overlapOf(x, y) > 0) return true;
            int pad = 8;
            boolean ox = Math.min(x.method_35418(), y.method_35418()) + pad
                    >= Math.max(x.method_35415(), y.method_35415()) - pad;
            boolean oz = Math.min(x.method_35420(), y.method_35420()) + pad
                    >= Math.max(x.method_35417(), y.method_35417()) - pad;
            if (ox && oz) return true;
        } catch (Throwable ignored) {
            // 没有包围盒，退回区块判断
        }
        try {
            return chunkNear(a.method_34000(), b.method_34000(), 2);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 已经登记过的市场地盘：结构起点落进来就沿用那处市场（防止"先算哪座"决定锚点） */
    static final class Region {
        final int minX, minZ, maxX, maxZ;
        final Town town;

        Region(int minX, int minZ, int maxX, int maxZ, Town town) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.town = town;
        }

        boolean holds(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static final java.util.List<Region> REGIONS = new java.util.ArrayList<>();

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }

    /** 这个村属于哪个"镇"（几座结构并成一处的群名）；独门独户返回 null */
    static String clusterOf(class_3218 world, class_3449 vs) {
        Town t = townOf(world, vs);
        return t == null || !t.merged ? null : t.key;
    }

    private static java.util.Set<String> VILLAGE_IDS;
    private static class_2378<class_3195> STRUCT_REG;

    /** 村庄结构表里有多少种结构（0 = 数据包没读到，那时所有地方都会显示"不是村庄"） */
    public static int villageStructureCount(class_3218 world) {
        if (VILLAGE_IDS == null || STRUCT_REG == null) buildVillageIds(world);
        java.util.Set<String> ids = VILLAGE_IDS;
        return ids == null ? 0 : ids.size();
    }

    private static boolean isVillageStructure(class_3195 st) {
        try {
            return VILLAGE_IDS != null && STRUCT_REG != null
                    && VILLAGE_IDS.contains(STRUCT_REG.method_10221(st).toString());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void buildVillageIds(class_3218 world) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        try {
            STRUCT_REG = world.method_30349().method_30530(class_7924.field_41246);
            var list = STRUCT_REG.method_40266(VILLAGE_TAG);
            list.ifPresent(l -> l.method_40239().forEach(e ->
                    e.method_40230().ifPresent(k -> ids.add(k.method_29177().toString()))));
            if (ids.isEmpty()) {
                // 自己的标签没读进来（数据包被别的包顶掉、版本不对…）→ 退回原版 #minecraft:village，
                // 至少原版村庄还能用，不至于全图"不是村庄"
                Tradepost.LOGGER.warn("[tradepost] #tradepost:village 是空的，退回 #minecraft:village");
                STRUCT_REG.method_40266(class_6862.method_40092(class_7924.field_41246,
                                class_2960.method_60654("minecraft:village")))
                        .ifPresent(l -> l.method_40239().forEach(e ->
                                e.method_40230().ifPresent(k -> ids.add(k.method_29177().toString()))));
            }
            Tradepost.LOGGER.info("[tradepost] 村庄结构表：{} 项", ids.size());
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 读不到 #tradepost:village 结构表", t);
        }
        VILLAGE_IDS = ids;
    }

    /**
     * 这一点属于哪个村庄。
     *
     * 以前直接 StructureAccessor.getStructureAt(pos, tag)，几座村挨在一起时它只会返回
     * 其中一座（顺序不保证），玩家挪几步价格表就换村。现在把"当前位置所在区块 ±3 区块"
     * 里所有村庄起点都捞出来，按「先看包围盒是否包住你，再看离村心多远」挑最近的一座，
     * 结果只跟你站的位置有关，反复打开也一样。
     */
    /** 区块 -> 村庄起点 的缓存；null 值用 Optional.empty 表示"这里没有村" */
    private static final java.util.Map<Long, java.util.Optional<class_3449>> VS_CACHE = new java.util.HashMap<>();
    private static class_3218 vsCacheWorld;

    private static long vsKey(int x, int y, int z) {
        return ((long) (x >> 4) << 40) ^ ((long) (z >> 4) << 8) ^ ((y >> 4) & 0xFFL);
    }

    private static class_3449 villageStart(class_3218 world, int x, int y, int z) {
        if (vsCacheWorld != world) {
            VS_CACHE.clear();
            vsCacheWorld = world;
        }
        long ck = vsKey(x, y, z);
        java.util.Optional<class_3449> hit = VS_CACHE.get(ck);
        if (hit != null) return hit.orElse(null);
        class_3449 found = villageStartUncached(world, x, y, z);
        if (VS_CACHE.size() > 8192) VS_CACHE.clear();
        VS_CACHE.put(ck, java.util.Optional.ofNullable(found));
        return found;
    }

    /** 以 (cx,cz) 区块为中心、半径 radius 区块内所有村庄起点（去重，含已是无效起点的剔除） */
    private static java.util.List<class_3449> villagesNear(class_3218 world, int cx, int cz, int radius) {
        java.util.List<class_3449> out = new java.util.ArrayList<>(4);
        if (VILLAGE_IDS == null || STRUCT_REG == null) buildVillageIds(world);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // 只读**已加载**区块自带的结构起点表：World.getChunk(int,int) 未加载就返回 null，绝不生成区块。
                // （method_41035 / method_41413 都会走 getChunkBlocking，站在服务端主线程上等于自杀：
                //  线上日志里那次 121 秒的 tick 就是它们同步要区块造成的死等）
                class_2818 chunk = world.method_8497(cx + dx, cz + dz);
                if (chunk == null) continue;
                java.util.Map<class_3195, class_3449> starts;
                try {
                    starts = chunk.method_12016();   // ProtoChunk#getStructureStarts()：只读已有表
                } catch (Throwable t) {
                    continue;
                }
                if (starts == null || starts.isEmpty()) continue;
                for (java.util.Map.Entry<class_3195, class_3449> e : starts.entrySet()) {
                    if (e.getKey() == null || !isVillageStructure(e.getKey())) continue;
                    class_3449 st = e.getValue();
                    if (st == null || !st.method_16657()) continue;
                    boolean dup = false;
                    for (class_3449 o : out) {
                        if (o == st || keyOf(o).equals(keyOf(st))) { dup = true; break; }
                    }
                    if (!dup) out.add(st);
                }
            }
        }
        return out;
    }

    private static class_3449 villageStartUncached(class_3218 world, int x, int y, int z) {
        java.util.List<class_3449> cands = new java.util.ArrayList<>(4);
        // 脚下这块区块已加载时，先看它自带的结构起点表里有没有包住你的村
        // —— 等价于原来的 getStructureAt，但拿的是已存在的表，不会去同步生成区块
        class_2818 self = world.method_8497(x >> 4, z >> 4);
        if (self != null) {
            try {
                for (java.util.Map.Entry<class_3195, class_3449> e : self.method_12016().entrySet()) {
                    if (e.getKey() == null || !isVillageStructure(e.getKey())) continue;
                    class_3449 st = e.getValue();
                    if (st == null || !st.method_16657()) continue;   // method_16657 = isValid()：有结构片段才算真村庄
                    if (st.method_14969().method_47593(x, y, z)) { cands.add(st); break; }
                }
            } catch (Throwable ignored) {
                // 读不出来就当脚下没村
            }
        }
        // 起点区块在别处的大村（半径 5 已经够 CTOV/暮色这类叠出来的村）
        for (class_3449 st : villagesNear(world, x >> 4, z >> 4, 5)) {
            boolean dup = false;
            for (class_3449 o : cands) {
                if (o == st || keyOf(o).equals(keyOf(st))) { dup = true; break; }
            }
            if (!dup) cands.add(st);
        }
        class_3449 best = null;
        double bestScore = Double.MAX_VALUE;
        for (class_3449 st : cands) {
            class_3341 box = st.method_14969();
            class_2382 center = box.method_14659();
            double ddx = center.method_10263() - x, ddy = center.method_10264() - y, ddz = center.method_10260() - z;
            double score = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            if (!box.method_47593(x, y, z)) score += 100000.0;   // 包住你的村子优先
            if (score < bestScore) {
                bestScore = score;
                best = st;
            }
        }
        return best;
    }

    /**
     * 村庄/库存的 key：村里 = 结构 id（整村共享一套库存和价格）；
     * 村外退回 64 格网格（散落村民/流浪商人还能交易，只是没有"村"的概念）。
     */
    public static String vkey(class_3218 world, int x, int y, int z) {
        return vkeyOf(world, villageStart(world, x, y, z), x, z);
    }

    /** 已经拿到村庄结构时直接算 key，省一次 ±3 区块扫描 */
    public static String vkeyOf(class_3218 world, class_3449 vs, int x, int z) {
        String d = dim(world) + "/";
        if (vs != null) return d + townKey(world, vs);
        return d + TradeStore.key(VillageMarket.cellOf(x), VillageMarket.cellOf(z));
    }

    /**
     * 定价锚点：村里的所有哈希（产业/繁荣度/集市日/片区）都用结构起点区块×16，
     * 于是整座村无论你站在哪个角落，看到的都是同一套价；村外仍按 64 格网格。
     */
    private static int[] anchor(class_3218 world, class_3449 vs, int x, int z) {
        if (vs == null) return new int[]{x, z};
        Town town = townOf(world, vs);
        class_1923 cp = (town != null ? town.lead : vs).method_34000();
        return new int[]{cp.field_9181 * 16 + 8, cp.field_9180 * 16 + 8};
    }

    /** 眼前的钟算不算"村里的钟" */
    public static boolean inVillage(class_3218 world, int x, int y, int z) {
        return villageKey(world, x, y, z) != null;
    }

    /** 一村的结算上下文：产业 / 繁荣度 / 是否驿站 / 库存表 */
    public static final class Ctx {
        public final class_3218 world;
        public final long seed;
        public final long day;
        public final boolean station;
        public final String vkey;
        public final TradeStore store;
        public final dev.guoke.tradepost.market.MarketData.Industry ind;
        public final int prosperity;
        public final int x, y, z;
        /** 定价锚点（村里 = 结构起点区块中心），所有哈希都用它，不用玩家真实坐标 */
        public final int hx, hz;
        public final dev.guoke.tradepost.market.BiomeInfo biome;
        /** 已通车的商路条数（阡陌交通；0 = 没装/没通路） */
        public final int roadDeg;
        /** 村庄包围盒占地（格²，0 = 村外），用来算繁荣度 */
        public final int area;
        /** 所属"镇"（几座村包围盒重叠时的群名），null = 独门独户 */
        public final String cluster;
        /** 数出来的屋子栋数 / 样式数（拿不到结构片段时按占地估算，houses=0） */
        public final int houses, kinds;
        /** 富庶度（连续）：村子越大越高，价格/库存/银库都按它算 */
        public final double wealth;

        Ctx(class_3218 world, long seed, long day, boolean station, String vkey, TradeStore store,
            dev.guoke.tradepost.market.MarketData.Industry ind, int prosperity,
            int x, int y, int z, int hx, int hz, dev.guoke.tradepost.market.BiomeInfo biome,
            int roadDeg, int area, String cluster, int houses, int kinds, double wealth) {
            this.world = world;
            this.seed = seed;
            this.day = day;
            this.station = station;
            this.vkey = vkey;
            this.store = store;
            this.ind = ind;
            this.prosperity = prosperity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hx = hx;
            this.hz = hz;
            this.biome = biome;
            this.roadDeg = roadDeg;
            this.area = area;
            this.cluster = cluster;
            this.houses = houses;
            this.kinds = kinds;
            this.wealth = wealth;
        }

        private java.util.Map<String, Double> buyUnits = null;
        /** 这一版价目表的货架（同一份 Ctx 上只生成一次：买页、秘宝页、收购封顶价都用它） */
        private java.util.List<OfferSpec> shelf = null;

        /**
         * 本村此刻"卖给你"的单价表（物品 → 绿宝石/件）。
         * 收购价会拿它封顶，保证同一村同一货：收进价 < 卖出价，杜绝原地倒买倒卖。
         */
        public java.util.Map<String, Double> buyUnits() {
            if (buyUnits == null) {
                java.util.Map<String, Double> m = new java.util.HashMap<>();
                for (OfferSpec s : offersFor(this)) {
                    if (!"minecraft:emerald".equals(s.giveItem()) || s.getCount() <= 0) continue;
                    double unit = s.giveCount() / (double) s.getCount();
                    m.merge(s.getItem(), unit, Math::min);
                }
                buyUnits = m;
            }
            return buyUnits;
        }

        /** 本村所在区域市场：同镇（包围盒重叠的几座村）共用一份，其余按 1024 格切块 */
        public String areaKey() {
            if (cluster != null) return Bridge.dim(world) + "/town:" + cluster;
            return Bridge.areaKey(world, hx, hz);
        }

        /**
         * 这个村银库上限：卖货是从这里掏钱。
         * 按富庶度线性给 —— 标准村（12 栋）= walletT2，屋子越多越厚，最少也有标准村的 1/3。
         */
        public double walletCap() {
            dev.guoke.tradepost.market.Economy eco = dev.guoke.tradepost.market.Economy.current();
            double w = Math.max(0.33, Math.min(2.5, wealth));
            int lv = store == null || vkey == null ? 1 : Math.max(1, store.level(vkey));
            // 等级越高银库越厚（每级 +25%）：高等级村才收得动你拉来的一车货
            return eco.walletT2 * w * dev.guoke.tradepost.market.VillageLevel.walletMult(lv);
        }

        /** 今天是不是本村的集市日 */
        public boolean marketDay() {
            return VillageMarket.marketDay(seed, hx, hz, day);
        }

        public String title() {
            return (cluster != null ? "镇·" : "") + (station ? "驿站" : "村庄") + "·"
                    + (ind == null ? "?" : ind.display()) + "·" + houseText();
        }

        /** "12 户·5 式"（拿不到结构片段时写"约 12 户"） */
        public String houseText() {
            int n = houses > 0 ? houses : (int) Math.round(dev.guoke.tradepost.market.VillageMarket.houses());
            return n + " 户" + (kinds > 0 ? "·" + kinds + " 式" : "");
        }

        /** 一句话说清这村的"先天条件"：占地、商路、是否成镇 */
        public String shape() {
            StringBuilder sb = new StringBuilder();
            if (area > 0) sb.append("占地 ").append(area).append(" 格²");
            else sb.append("村外");
            sb.append(" · ").append(houseText()).append(" · 富庶 ").append(String.format("%.2f", wealth));
            if (roadDeg > 0) sb.append(" · 商路 ").append(roadDeg).append(" 条");
            if (cluster != null) sb.append(" · 与邻村成镇");
            return sb.toString();
        }
    }

    /** 市场 -> 群系（产业哈希的一项输入）。算过一次就锁住，避免同一座村因站位不同换产业 */
    private static final java.util.Map<String, dev.guoke.tradepost.market.BiomeInfo> BIOME_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static Ctx ctx(class_3218 world, int x, int y, int z) {
        ensureRecipePrices(world);                    // 第一次到这里时把配方价算好（便宜的一次性开销）
        dev.guoke.tradepost.market.MarketData data = Tradepost.data();
        long seed = world.method_8412();
        long day = world.method_8532() / 24000L;
        boolean station = stationNear(world, x, z);
        class_3449 vs = villageStart(world, x, y, z);
        String vkey = vkeyOf(world, vs, x, z);
        int[] h = anchor(world, vs, x, z);
        TradeStore store = store(world);
        store.settle(vkey, day);
        CATALOG.setWorldIndexFn(item -> worldIndexFor(store, day, item));   // 全球饱和：卖烂了的货全世界一起掉价（含合成链）
        // 群系必须在**锚点**取样：产业是按 (种子, 锚点, 群系) 哈希出来的，
        // 拿玩家脚下的群系会让"站在村口两群系交界处、挪一格"直接换产业。
        // 再按 vkey 缓存一层，第一眼算定就锁死，之后无论站哪儿都是同一个产业。
        dev.guoke.tradepost.market.BiomeInfo biome = BIOME_CACHE.get(vkey);
        if (biome == null) {
            biome = biomeInfo(world, h[0], y, h[1]);
            if (biome != null) BIOME_CACHE.put(vkey, biome);
        }
        // 先天条件：占地多大（大村更富）、有几条已通车的商路（路通收得好）、跟邻村挤不挤成一个镇
        Town town = townOf(world, vs);
        int area = town == null ? 0 : (int) Math.min(Integer.MAX_VALUE, town.area);
        int roadDeg = RoadNet.degree(world, vs);
        String cluster = town != null && town.merged ? town.key : null;
        int[] stat = town == null ? new int[]{0, 0} : town.stat;
        int houses = stat[0], kinds = stat[1];
        // 纯逻辑层（VillageMarket）拿不到世界，定价前把这几项塞给它
        VillageMarket.setTraits(roadDeg, area, houses, kinds);
        double wealth = VillageMarket.wealth();
        dev.guoke.tradepost.market.MarketData.Industry ind =
                data == null ? null : VillageMarket.industry(data, seed, h[0], h[1], biome);
        int prosperity = VillageMarket.prosperity(seed, h[0], h[1]);
        return new Ctx(world, seed, day, station, vkey, store, ind, prosperity,
                x, y, z, h[0], h[1], biome, roadDeg, area, cluster, houses, kinds, wealth);
    }

    /** 同村"收进 / 卖出"最低价差折扣：收购价 ≤ 本村卖出价 × 0.75 */
    private static final double LOCAL_SPREAD = 0.75;

    /**
     * 收购单价缓存：key = 村|天|存档版本|物品|品质档。
     *
     * <p>一页要算十几件货，其中 farmersdelight:kelp_roll_slice 这种菜要走完整条配方链，
     * 单项实测 2.2 秒（日志「收购定价慢」），而它每次翻页 / 重开 / 自动认货重建都会被重算。
     * 同一个 key 的价一定是同一个价（库存变化会动 store.version()，所以版本进了 key），
     * 直接缓存，第二次开始是 0 成本。
     */
    private static final java.util.Map<String, double[]> SELL_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, double[]> e) {
                    return size() > 4096;
                }
            });

    /**
     * 某件货的库存上限。食物（没有手写价的那些模组食材、菜、酒）额外压在 64 件以内，
     * 其余照 512 ÷ 基础价 反算；驿站翻倍（但食物仍是 64 封顶）。
     */
    public static double capOf(String itemId, double base, boolean station) {
        boolean food = false;
        try {
            // 菜与酒都算"消耗品"：村里的仓底就是几十份，不会囤几千瓶
            food = Tradepost.valueOf(itemId) <= 0 && (isEdible(itemId) || isDrinkItem(itemId));
        } catch (Throwable ignored) {
            // 拿不到物品就不特殊处理
        }
        double c = dev.guoke.tradepost.market.Stock.capFor(base, station, food);
        // 酒按"瓶"算仓底：价越高库存上限越小，典藏酒会被折算成二十几瓶就"收满了"（实测 27/27 卖不动）。
        // 酒馆里堆一两百瓶才合理，这里给个下限。
        if (isDrinkItem(itemId)) c = Math.max(c, 128);
        return c;
    }

    /** 本村此刻收购某件货的单价（绿宝石/件）；0 表示不收 */
    public static double sellUnit(Ctx c, int x, int z, String itemId) {
        return sellUnit(c, x, z, itemId, 0);
    }

    /** 同上，但按品质档加价（level 0 = 没有品质组件） */
    public static double sellUnit(Ctx c, int x, int z, String itemId, int level) {
        // 不死图腾：回收价钉死 48 = 买价 64 × 0.75（买那边在 VillageMarket.TOTEM_PRICE 也钉死）。
        // 两边都钉死，行情/库存推不歪，也绝不会出现"低价买进高价卖回"的套利。
        if ("minecraft:totem_of_undying".equals(itemId)) return 48.0;
        String ck = c.vkey + "|" + c.day + "|" + c.store.stockVer() + "|" + itemId + "|" + level;
        double[] cached = SELL_CACHE.get(ck);
        if (cached != null) return cached[0];
        VillageMarket.setTraits(c.roadDeg, c.area, c.houses, c.kinds);
        dev.guoke.tradepost.market.MarketData data = Tradepost.data();
        double base = basePrice(itemId);
        if (data == null || base <= 0) return 0;
        double cap = capOf(itemId, base, c.station);
        double have = c.store.get(c.vkey, itemId, cap, c.day);
        double ratio = dev.guoke.tradepost.market.Stock.ratio(have, cap);
        double pressure = c.store.pressure(c.areaKey(), itemId, cap, c.day)
                * dev.guoke.tradepost.market.Economy.current().areaSpread;
        double unit = VillageMarket.universalSellUnit(data, CATALOG, c.seed, c.hx, c.hz, c.day, c.ind,
                c.prosperity, itemId, ratio, pressure);
        // 同一村同一物品：收购价必须明显低于它卖给你的价（0.75 折），否则能原地倒买倒卖刷钱。
        // 比的是"同一品质档"的价：buyUnits 里那口价已经含了本村那瓶酒的品质倍率，
        // 先除回无品质的底价，再拿同一个倍率比，否则典藏酒的收购价会被按"无语质"封顶，
        // 结果是 1~6 档卖一个价（玩家看到的"卖价不按等级加钱"就是这么来的）。
        // 闸门算的是"本村此刻的问价"：不靠货架（买页只列有货的条目，买空就不在表里了 ——
        // 那正是"把库存买光再卖回来翻倍"这个刷钱漏洞的入口），直接按库存算一遍要价。
        double ask = VillageMarket.askUnit(data, CATALOG, c.seed, c.hx, c.hz, c.day, c.ind,
                c.prosperity, itemId, have, cap);
        int bq = hasQuality(itemId) ? offerQuality(c, itemId) : 0;
        Double buy = c.buyUnits().get(itemId);
        if (buy != null && buy > 0) {
            double buyPlain = bq > 0 ? buy / dev.guoke.tradepost.market.Quality.mult(bq) : buy;
            if (buyPlain > 0 && (ask <= 0 || buyPlain < ask)) ask = buyPlain;   // 架上那口价更准就拿它
        }
        double before = unit;
        if (ask > 0) unit = Math.min(unit, ask * LOCAL_SPREAD);
        if (before > unit * 1.05) {
            Tradepost.debug("[tradepost] 收购价被压 {} 原算{} 压到{} 本村要价{} 架上价{}",
                    itemId, String.format(java.util.Locale.ROOT, "%.2f", before),
                    String.format(java.util.Locale.ROOT, "%.2f", unit),
                    String.format(java.util.Locale.ROOT, "%.2f", ask), buy == null ? "无" : buy);
        }
        else if (unit > 0) unit *= LOCAL_SPREAD;   // 连要价都算不出来（理论上不会）时至少别裸卖
        // 品质溢价最后乘：面板上的价、成交的价都用这一个口径
        if (level > 0) unit *= dev.guoke.tradepost.market.Quality.mult(level);
        SELL_CACHE.put(ck, new double[]{unit});
        return unit;
    }

    // ───────────── 装备回收（黑名单装备的专属通道） ─────────────

    /** 材料价打 55 折 */
    public static final double GEAR_BUYBACK = 0.55;
    /** 每级附魔 +8%（封顶 +192%） */
    public static final double GEAR_ENCH = 0.08;
    /** 快用坏的也还值 35% */
    public static final double GEAR_DUR_FLOOR = 0.35;
    /**
     * /tradepost gear：逐件解释背包里的装备为什么没进回收行。
     * 只有"黑名单 + 算得出材料价"的东西才走回收通道，这里把两个条件都打出来。
     */
    public static int cmdGear(class_2168 src) {
        if (!(src.method_44023() instanceof class_3222 player)) {
            send(src, "只能玩家用");
            return 0;
        }
        class_1263 inv = player.method_31548();
        int shown = 0;
        for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 st = inv.method_5438(i);
            if (st.method_7960()) continue;
            String id = itemId(st);
            if (id == null) continue;
            boolean ban = CATALOG.banned(id);
            double base = gearBase(id);
            if (!ban && base <= 0) continue;                 // 跟回收无关的普通货，不刷屏
            int price = gearPrice(st, id);
            shown++;
            say(player, "槽" + i + " " + shortId(id) + "：黑名单=" + ban
                    + " 材料价=" + fmt(base) + " 回收价=" + price
                    + (price > 0 ? "" : "   ← 不进回收行"));
        }
        try {
            if (player.method_5770() instanceof class_3218 world) {
                int x = (int) Math.floor(player.method_23317());
                int z = (int) Math.floor(player.method_23321());
                Ctx c = ctx(world, x, (int) Math.floor(player.method_23318()), z);
                int left = GEAR_PER_DAY - c.store.gearSold(c.vkey, c.day, GEAR_KEY);
                say(player, "今日回收额度剩 " + left + " / " + GEAR_PER_DAY);
            }
        } catch (Throwable ignored) {
            // 不在村庄里就不报额度
        }
        if (shown == 0) say(player, "背包里没有能进回收通道的装备/工具/盔甲");
        return shown;
    }
    /** 每村每天收几件 */
    public static final int GEAR_PER_DAY = 2;
    /** 当日额度记在这个 code 上（TradeStore 的 gearSold/gearTake 按天清零） */
    public static final String GEAR_KEY = "tradepost:gear";

    /**
     * 装备的材料价（拆解配方里每样原料的价加起来）；没有配方价就不是"装备"。
     *
     * <p>配方价常常算不全：下界合金剑 = 钻石剑 + 下界合金锭 + 升级模板，
     * 而钻石剑自己也在黑名单里、rawValue 直接给 0，那一格就被算成 0，
     * 于是剑/护腿/头盔全都只剩"锭"那份钱（117.07），界面上三行装备都写 64
     * （玩家原话："怎么装备全是64绿宝石"）。
     * 这里取两者较大的一个：配方价，或者"按缓存的材料清单重算、缺价的格子跳过"。
     */
    private static double gearBase(String id) {
        try {
            java.util.Map<String, Double> rp = CATALOG.recipePrices();
            double v = rp == null ? 0 : rp.getOrDefault(id, 0.0);
            double mat = gearMaterial(id);
            return Math.max(v, mat);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 缓存：装备 id → 它的配方材料表（addRecipe 只给黑名单产物登记，量很小） */
    private static final java.util.Map<String, java.util.List<java.util.List<String>>> GEAR_RECIPES
            = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 按材料清单重算一遍成本。
     *
     * <p>每格取**最便宜**的已知价：标签（#c:ingots 之类）展开后是一组候选，
     * 取最贵会让一件铁胸甲被某条配方链放大到几百（实测 8 格 × 45.3 = 362）。
     * 没价的格子按 0 算，不整条作废。
     */
    private static double gearMaterial(String id) {
        java.util.List<java.util.List<String>> slots = GEAR_RECIPES.get(id);
        if (slots == null || slots.isEmpty()) return 0;
        double sum = 0;
        for (java.util.List<String> slot : slots) {
            double best = 0;
            if (slot != null) {
                for (String ing : slot) {
                    if (ing == null || ing.isEmpty()) continue;
                    double v = matValue(ing);
                    if (v <= 0) continue;
                    if (best <= 0 || v < best) best = v;
                }
            }
            sum += best;
        }
        return sum;
    }

    /**
     * 单件材料价，优先级：**手写价 → 类别标签价 → 配方反推**。
     *
     * <p>以前是"两者取大"，于是铁锭手写价 2.0 会被某条配方链推到 45.3；
     * 装备回收价 = 材料价 × 0.55，直接被放大二十倍。
     */
    private static double matValue(String id) {
        try {
            double hand = basePrice(id);
            if (hand > 0) return hand;
        } catch (Throwable ignored) {
            // 查不到就继续往下
        }
        try {
            java.util.Map<String, Double> rp = CATALOG.recipePrices();
            if (rp != null) return rp.getOrDefault(id, 0.0);
        } catch (Throwable ignored) {
            // 预热还没跑完就按 0
        }
        return 0;
    }

    /** 能不能进回收通道：被黑名单挡掉、但拆得出材料的东西（剑/甲/工具/弓） */
    public static boolean gearItem(String id) {
        if (id == null || id.isEmpty()) return false;
        if (!CATALOG.banned(id)) return false;
        return gearBase(id) > 0;
    }

    /**
     * 附魔总等级（各条附魔等级相加）。
     *
     * <p>这里**必须**跟附魔书走同一条读法（{@link #enchMap}）：早先自己另写了一套反射，
     * 结果剑上的 {@code minecraft:enchantments} 一条都读不出来（日志里 gearSig 永远是 e0），
     * 于是锋利 IV 的白板和附魔剑一个价。enchMap 已经在附魔书上验证过，复用它。
     */
    public static int gearEnch(class_1799 st) {
        int n = 0;
        try {
            for (int v : enchMap(st).values()) n += v;
        } catch (Throwable ignored) {
        }
        return n;
    }

    /** 堆上的附魔：id → 等级（附魔书读 stored_enchantments） */
    public static java.util.Map<String, Integer> enchMap(class_1799 st) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        try {
            Object comp = null;
            for (class_9331<?> key : new class_9331<?>[]{class_9334.field_49643, class_9334.field_49633}) {
                comp = compRead(st, key);
                if (comp != null && !Boolean.TRUE.equals(comp.getClass().getMethod("method_57543").invoke(comp))) break;
                comp = null;
            }
            if (comp == null) return out;
            // method_57539() 的返回类型带 fastutil（编译期不在 classpath 上），走反射拿
            Object set = comp.getClass().getMethod("method_57539").invoke(comp);
            if (!(set instanceof Iterable<?> it)) return out;
            for (Object o : it) {
                if (!(o instanceof java.util.Map.Entry<?, ?> en)) continue;
                String id = enchId(en.getKey());
                if (id == null || !(en.getValue() instanceof Number num)) continue;
                out.merge(id, num.intValue(), Math::max);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Holder<Enchantment> → "minecraft:sharpness"；认不出返回 null */
    private static String enchId(Object holder) {
        try {
            Object opt = holder.getClass().getMethod("method_40230").invoke(holder);
            if (opt instanceof java.util.Optional<?> o && o.isPresent()) {
                Object key = o.get();
                return String.valueOf(key.getClass().getMethod("method_29177").invoke(key));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 附魔书的档位码，两种写法：
     * <ul><li>单附魔 {@code book:附魔|等级}（跟学术铺同一种写法）</li>
     * <li>多附魔 {@code book:附魔@等级;附魔@等级}（按附魔 id 排序，价按各附魔之和）</li></ul>
     * 白板书返回 null（走普通收购那条路）。模组附魔不再被拒 ——
     * BookShop.valueOr 会给它一个基准价，不然玩家拿到表外附魔只能当白板卖。
     */
    public static String bookCode(class_1799 st) {
        if (st == null || st.method_7960()) return null;
        if (!"minecraft:enchanted_book".equals(itemId(st))) return null;
        java.util.Map<String, Integer> map = enchMap(st);
        if (map.isEmpty()) return null;
        double worth = 0;
        for (java.util.Map.Entry<String, Integer> e : map.entrySet()) {
            worth += dev.guoke.tradepost.market.BookShop.valueOr(e.getKey(), e.getValue());
        }
        if (worth <= 0) return null;
        if (map.size() == 1) {
            java.util.Map.Entry<String, Integer> e = map.entrySet().iterator().next();
            return "book:" + e.getKey() + "|" + e.getValue();
        }
        java.util.List<String> ids = new java.util.ArrayList<>(map.keySet());
        java.util.Collections.sort(ids);
        StringBuilder sb = new StringBuilder("book:");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(ids.get(i)).append('@').append(map.get(ids.get(i)));
        }
        return sb.toString();
    }

    /** 耐久还剩几成（最低按 35% 算） */
    public static double gearDur(class_1799 st) {
        try {
            int max = st.method_7919();
            if (max <= 0) return 1.0;
            double left = 1.0 - (double) st.method_7936() / (double) max;
            return Math.max(GEAR_DUR_FLOOR, Math.min(1.0, left));
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /** 单件回收价：材料价 × 55% × 耐久 × (1 + 8%×附魔等级) */
    public static int gearPrice(class_1799 st, String id) {
        double base = gearBase(id);
        if (base <= 0) return 0;
        double v = base * GEAR_BUYBACK * gearDur(st) * (1.0 + GEAR_ENCH * Math.min(24, gearEnch(st)));
        return (int) Math.max(1, Math.min(9999, Math.round(v)));
    }

    /** 同一件装备的"成色指纹"：附魔/耐久/价格不同的分开报价，不会混成一堆 */
    public static String gearSig(class_1799 st, String id) {
        return id + "/e" + gearEnch(st) + "/d" + Math.round(gearDur(st) * 100) + "/p" + gearPrice(st, id);
    }

    /** 全量附魔码 "id@等级;id@等级"（按 id 排序）；空表返回 "" */
    public static String gearEnchCode(java.util.Map<String, Integer> em) {
        if (em == null || em.isEmpty()) return "";
        java.util.List<String> ids = new java.util.ArrayList<>(em.keySet());
        java.util.Collections.sort(ids);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(ids.get(i)).append('@').append(em.get(ids.get(i)));
        }
        return sb.toString();
    }


    /**
     * 两件货算不算"同一种"（摆货 / 扣货 / 结算核对都走这里）。
     *
     * <p>只比物品 id 会出事：卖一把锋利 IV 的下界合金剑，天平上摆的可能是背包里那把白板剑，
     * 结算照样按 193 付钱 —— 玩家看到的是"卖了剑，剑还在，白板那把没了"。
     * 所以：附魔书按附魔档位、装备按成色指纹（附魔/耐久/价格）、酒按品质档。
     */
    public static boolean sameKind(class_1799 a, class_1799 b) {
        if (a == null || b == null || a.method_7960() || b.method_7960()) return false;
        if (a.method_7909() != b.method_7909()) return false;
        String id = itemId(b);
        if (id == null) return true;
        if ("minecraft:enchanted_book".equals(id)) {
            String x = bookCode(a);
            return x != null && x.equals(bookCode(b));
        }
        if (CATALOG.banned(id)) return gearSig(a, id).equals(gearSig(b, id));
        if (isDrinkItem(id)) return brewOf(a) == brewOf(b);
        return true;
    }

    /**
     * 结算用的"同一族"：比 {@link #sameKind} 宽松，只认不会变的那几项。
     *
     * <p>为什么需要两档：摆货时要用严格的 {@code sameKind}（连耐久和回收价都比），才挑得中
     * 玩家点的那一把；但成交判定发生在几秒之后，而 {@code gearSig} 里的 {@code d}（耐久）
     * 和 {@code p}（回收价）会随行情/村子库存漂移 —— 实测卖锋利 IV 下界合金剑：摆上去的时候
     * 天平=1 要=1，几秒后结算却"天平上的货不够 1 件"，连拒 9 次，剑卖不掉。
     *
     * <p>所以结算只认：物品 id、附魔书的附魔档位、装备的附魔等级总和、酒的品质档。
     */
    public static boolean sameFamily(class_1799 a, class_1799 b) {
        if (a == null || b == null || a.method_7960() || b.method_7960()) return false;
        if (a.method_7909() != b.method_7909()) return false;
        String id = itemId(b);
        if (id == null) return true;
        if ("minecraft:enchanted_book".equals(id)) {
            String x = bookCode(a);
            return x != null && x.equals(bookCode(b));
        }
        if (CATALOG.banned(id)) return gearEnch(a) == gearEnch(b);
        if (isDrinkItem(id)) return brewOf(a) == brewOf(b);
        return true;
    }
    /** 日志用的一行货描述：短名×数量 + 书档位/装备成色/酒品质 */
    public static String describe(class_1799 st) {
        if (st == null || st.method_7960()) return "空";
        String id = itemId(st);
        StringBuilder sb = new StringBuilder(shortId(id)).append('×').append(st.method_7947());
        try {
            if ("minecraft:enchanted_book".equals(id)) {
                sb.append(" 书=").append(bookCode(st));
            } else if (id != null && CATALOG.banned(id)) {
                sb.append(" 成色=").append(gearSig(st, id));
            } else if (id != null && isDrinkItem(id)) {
                sb.append(" 档=").append(brewOf(st));
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    /** 短名：minecraft:stone_bricks -> stone_bricks */
    public static String shortId(String id) {
        if (id == null) return "?";
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    /** 物品的翻译键 —— 客户端按玩家当前语言渲染，中英文都对 */
    public static String nameKey(String itemId) {
        class_1792 it = item(itemId);
        return it == null ? null : it.method_7876();
    }

    // ───────────── 品质（酒的 brew_level 组件） ─────────────

    private static Object BREW_TYPE;
    private static boolean BREW_TRIED;

    /** 酒的品质组件类型；没有酒馆模组时返回 null，所有品质逻辑退化成普通货 */
    @SuppressWarnings("unchecked")
    private static class_9331<Integer> brewType() {
        if (!BREW_TRIED) {
            BREW_TRIED = true;
            try {
                class_2960 rl = class_2960.method_60655("kaleidoscope_tavern", "brew_level");
                class_9331<?> t = class_7923.field_49658.method_10223(rl);
                if (t != null) BREW_TYPE = t;
            } catch (Throwable t) {
                Tradepost.LOGGER.info("[tradepost] 取不到酒的品质组件，按普通货算");
            }
        }
        return (class_9331<Integer>) BREW_TYPE;
    }

    /** 这种物品**本身**会不会带品质：拿一个空白栈看默认组件里有没有 brew_level（结果缓存） */
    private static final java.util.Map<String, Boolean> DRINK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isDrinkItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        if (dev.guoke.tradepost.market.Drinks.is(itemId)) return true;   // 名单里 / 见过带 brew_level 的
        Boolean hit = DRINK_CACHE.get(itemId);
        if (hit != null) return hit;
        boolean yes = false;
        try {
            class_9331<Integer> t = brewType();
            if (t != null) {
                class_1792 it = item(itemId);
                if (it != null) {
                    class_1799 probe = new class_1799(it, 1);
                    yes = compRead(probe, t) != null;
                }
            }
        } catch (Throwable ignored) {
        }
        DRINK_CACHE.put(itemId, yes);
        return yes;
    }

    /** 这一堆货的品质档；0 = 没有品质组件 */
    public static int brewOf(class_1799 st) {
        class_9331<Integer> t = brewType();
        if (t == null || st == null || st.method_7960()) return 0;
        try {
            Integer v = (Integer) compRead(st, t);
            if (v == null) return 0;
            // 真见到 brew_level 了：把这种物品登记成"酒水"，以后靠 id 就能认出来
            // （默认组件里没有它，光拿空白栈探是探不到的 —— 酒价地板就是栽在这上面）
            dev.guoke.tradepost.market.Drinks.seen(itemId(st));
            return dev.guoke.tradepost.market.Quality.clamp(v);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 这种货会不会带品质：目前只有酒馆的酒有这层信息 */
    public static boolean hasQuality(String itemId) {
        // 认"物品本身带 brew_level 组件"，不再靠名字里有没有 wine/beer 这类关键词 ——
        // 鸡尾酒（mojito、godfather…）名字里没有关键词，老写法把它们当普通货，等级白搭。
        if (isDrinkItem(itemId)) return true;
        return itemId != null && brewType() != null
                && itemId.startsWith("kaleidoscope_tavern:")
                && dev.guoke.tradepost.market.Cuisine.isDrink(itemId);
    }

    /** 本村这一周卖给你的这种货是几档（0 = 没有品质） */
    public static int offerQuality(Ctx c, String itemId) {
        if (!hasQuality(itemId)) return 0;
        int q = dev.guoke.tradepost.market.Quality.forVillage(c.vkey, itemId, c.day);
        // 酒必须有等级：低级村至少"劣质"，L4 起至少"普通"，否则买回家是一瓶没年份的酒
        int lv = c.store == null ? 1 : Math.max(1, c.store.level(c.vkey));
        return Math.max(q, lv >= 4 ? 3 : 2);
    }

    /** 一份的绿宝石价：带品质的按档位乘倍率（面板价与成交价都走这里，保证一致） */
    public static int offerPrice(int price, int level) {
        int p = level <= 0 ? price
                : Math.max(1, (int) Math.round(price * dev.guoke.tradepost.market.Quality.mult(level)));
        // 天平一格能摆 9999 颗（MerchantInventoryMixin.method_58350），不再凑 9 的倍数
        return Math.max(1, Math.min(9999, p));
    }

    /** 造一堆带品质的货 */
    public static class_1799 withQuality(String itemId, int count, int level) {
        class_1792 it = item(itemId);
        if (it == null) return class_1799.field_8037;
        class_1799 st = new class_1799(it, Math.max(1, count));
        if (level > 0) {
            class_9331<Integer> t = brewType();
            if (t != null) {
                try {
                    st.method_57379(t, dev.guoke.tradepost.market.Quality.clamp(level));
                } catch (Throwable ignored) {
                }
            }
        }
        return st;
    }

    // —— 中文译名检查：没有 zh_cn 词条的模组物品不上架（但玩家照样能卖给它）——

    private static final java.util.Map<String, java.util.Set<String>> ZH_KEYS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 模组自带的中文词条键集合；模组没提供 zh_cn.json 时返回空集 */
    private static java.util.Set<String> zhKeys(String ns) {
        java.util.Set<String> c = ZH_KEYS.get(ns);
        if (c != null) return c;
        java.util.Set<String> built = new java.util.HashSet<>();
        try {
            net.fabricmc.loader.api.ModContainer mc =
                    net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(ns).orElse(null);
            if (mc != null) {
                java.nio.file.Path file = null;
                for (java.nio.file.Path root : mc.getRootPaths()) {
                    java.nio.file.Path t = root.resolve("assets/" + ns + "/lang/zh_cn.json");
                    if (java.nio.file.Files.isRegularFile(t)) { file = t; break; }
                }
                if (file != null) {
                    try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(
                            file, java.nio.charset.StandardCharsets.UTF_8)) {
                        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                        built.addAll(o.keySet());
                    }
                }
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.debug("[tradepost] 读 zh_cn 词条失败：{}", ns, t);
        }
        ZH_KEYS.put(ns, built);
        return built;
    }

    /**
     * 这件货在游戏里有没有中文名。
     * 判断依据是模组自带的 assets/&lt;ns&gt;/lang/zh_cn.json 有没有这个键 ——
     * 没中文名的模组货只显示 "xxx:yyy" 或英文原名，摆在货架上很丑，所以不卖；
     * 但收购不受影响（玩家打到的东西总能卖掉）。
     */
    public static boolean hasZhName(String itemId) {
        if (itemId == null) return true;
        int i = itemId.indexOf(':');
        String ns = i < 0 ? itemId : itemId.substring(0, i);
        String path = i < 0 ? itemId : itemId.substring(i + 1);
        if ("minecraft".equals(ns)) return true;             // 原版中文词条在资源包里，不在这里查
        try {
            net.fabricmc.loader.api.ModContainer mc =
                    net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(ns).orElse(null);
            if (mc == null) return true;                     // 不在模组里（动态注册/数据包物品）：不拦
        } catch (Throwable t) {
            return true;
        }
        java.util.Set<String> keys = zhKeys(ns);
        if (keys.isEmpty()) return false;                    // 这模组压根没有中文文件
        String key = nameKey(itemId);
        if (key != null && keys.contains(key)) return true;
        return keys.contains("item." + ns + "." + path) || keys.contains("block." + ns + "." + path);
    }

    /** 聊天栏用的名字（服务端发可翻译文本，客户端翻译） */
    private static class_2561 nameOf(String itemId) {
        String k = nameKey(itemId);
        return k == null ? class_2561.method_43470(shortId(itemId)) : class_2561.method_43471(k);
    }

    // —— 菜与酒：既要"看着像菜"，又要真的能吃（料理模组里也有沙发、画和凳子）——

    /** 物品能不能吃（有 FOOD 组件） */
    public static boolean isEdible(String itemId) {
        try {
            class_1792 it = item(itemId);
            return it != null && it.method_57347().method_57829(class_9334.field_50075) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是不是正经的菜/酒：料理模组命名空间，或名字像菜且能吃 */
    public static boolean isDish(String itemId) {
        if (itemId == null) return false;
        if (dev.guoke.tradepost.market.Cuisine.isCuisineNamespace(itemId)) return isEdible(itemId);
        return isEdible(itemId) && dev.guoke.tradepost.market.Cuisine.isCuisine(itemId, null);
    }

    /** 主背包（36 格）+ 副手还能再塞下多少个这种物品；盔甲格不算 */
    public static int spaceFor(class_1657 player, class_1799 proto) {
        if (proto.method_7960()) return 0;
        class_1263 inv = player.method_31548();
        int max = Math.max(1, Math.min(proto.method_7914(), 64));
        int space = 0;
        int size = inv.method_5439();
        for (int i = 0; i < size; i++) {
            if (i >= 36 && i <= 39) continue;
            class_1799 st = inv.method_5438(i);
            if (st.method_7960()) {
                space += max;
                continue;
            }
            if (class_1799.method_31577(st, proto)) {
                space += Math.max(0, Math.min(max, st.method_7914()) - st.method_7947());
            }
        }
        return space;
    }

    /**
     * 塞进背包，塞不下的掉在脚边（跟大多数模组一致）。
     * 不依赖 giveItemStack 的返回值语义：先叠堆、再占空格、最后手动掉落剩余。
     * 返回掉在地上的件数。
     */
    public static int giveOrDrop(class_1657 player, class_1799 stack) {
        if (stack.method_7960()) return 0;
        // 单格 9999 只用于天平/结果格的显示；一旦进背包，存盘时 count 必须 ≤99，
        // 否则退出存档会抛 "Value must be within range [1;99]"。所以进背包一律拆成 ≤64 的小摞。
        int total = stack.method_7947();
        if (total > 64) {
            int spilled = 0;
            int left = total;
            while (left > 0) {
                int n = Math.min(left, 64);
                class_1799 part = stack.method_7972();
                part.method_7939(n);
                try {
                    part.method_57379(net.minecraft.class_9334.field_50071, 64);
                } catch (Throwable ignored) {
                    // 组件挂不上也无所谓，数量已经是小摞了
                }
                player.method_7270(part);
                if (!part.method_7960()) {
                    spilled += part.method_7947();
                    player.method_7328(part, false);
                }
                left -= n;
            }
            return spilled;
        }
        // 原版 giveItemStack = PlayerInventory.insertStack(-1, stack)：
        // 塞得进多少塞多少，塞不下的部分会留在传入的这个 stack 里（它自己不会掉落）
        player.method_7270(stack);
        int dropped = stack.method_7960() ? 0 : stack.method_7947();
        if (dropped > 0) player.method_7328(stack, false);
        return dropped;
    }

    private static int countOf(class_1263 inv, String itemId) {
        return countOf(inv, itemId, -1);
    }

    /** 背包里某种货有多少件；level < 0 = 不分品质 */
    private static int countOf(class_1263 inv, String itemId, int level) {
        int total = 0;
        for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 st = inv.method_5438(i);
            if (st.method_7960()) continue;
            if (matches(st, itemId, level)) total += st.method_7947();
        }
        return total;
    }

    /** 这一堆是不是"某种货 + 指定品质档"；level < 0 表示不分品质 */
    private static boolean matches(class_1799 st, String itemId, int level) {
        if (st.method_7960() || !itemId.equals(itemId(st))) return false;
        return level < 0 || brewOf(st) == level;
    }

    /** 背包里这种货最低的品质档（0 = 混着普通货；没有就返回 -1） */
    private static int lowestLevel(class_1263 inv, String itemId) {
        int best = -1;
        for (int i = 0; i < inv.method_5439(); i++) {
            class_1799 st = inv.method_5438(i);
            if (st.method_7960() || !itemId.equals(itemId(st))) continue;
            int q = brewOf(st);
            if (best < 0 || q < best) best = q;
        }
        return best;
    }

    /**
     * 从背包里卖某种货：want <= 0 表示全卖。返回实际卖出件数。
     * 所有路径（潜行右键村民、点价目表、/tradepost sell）最终都走这里，价格口径唯一。
     */
    public static int sellFromInv(class_3222 player, Ctx c, int x, int z, String itemId, int want) {
        return sellFromInv(player, c, x, z, itemId, want, -1);
    }

    /**
     * 同上，但只卖指定品质档（level 0 = 没品质组件）；level < 0 表示"不分品质"，
     * 此时只卖背包里最低的那一档，免得玩家一句 /tradepost sell 把典藏按普通价卖掉。
     */
    public static int sellFromInv(class_3222 player, Ctx c, int x, int z, String itemId, int want, int level) {
        Tradepost.LOGGER.info("[tradepost] 面板卖出 id={} 件数={} 档={}", itemId, want, level);
        class_1263 inv = player.method_31548();
        int lv = level < 0 ? Math.max(0, lowestLevel(inv, itemId)) : level;
        int total = countOf(inv, itemId, lv) + countSlots(player, itemId, lv);
        if (total <= 0) {
            say(player, join("背包里没有 ", itemId, ""));
            return 0;
        }
        double unit = sellUnit(c, x, z, itemId, lv);
        if (unit <= 0) {
            say(player, join("本村不收 ", itemId, ""));
            return 0;
        }
        // 整份成交：一份 = lot 件，卖 price 个绿宝石（见 market/Lot.java），零头件数留在背包里
        int lot = dev.guoke.tradepost.market.Lot.of(unit);
        int price = dev.guoke.tradepost.market.Lot.price(unit, lot);
        double base = basePrice(itemId);
        double cap = capOf(itemId, base, c.station);
        double have = c.store.get(c.vkey, itemId, cap, c.day);
        int room = (int) Math.max(0, Math.floor(cap - have));
        if (room <= 0) {
            say(player, join("本村 ", itemId, " 已收满（上限 " + (int) cap + "），过几天再来"));
            return 0;
        }
        int wantN = want <= 0 ? total : Math.min(want, total);
        int lots = wantN / lot;
        if (lots <= 0) {
            say(player, join("本村收 ", itemId, " 每 " + lot + " 件 " + price + " 绿宝石，你只有 " + total + " 件"));
            return 0;
        }
        lots = Math.min(lots, room / lot);
        // 村庄银库：村子只能付得起自己有的绿宝石
        String uuid = player.method_5667().toString();
        double purse = c.store.wallet(c.vkey, c.walletCap(), c.day);
        int afford = (int) Math.floor((purse + c.store.credit(uuid)) / price);
        lots = Math.min(lots, afford);
        if (lots <= 0) {
            say(player, join("本村银库空了（只剩 " + (int) purse + " 绿宝石），过几天或去别村卖 ", itemId, ""));
            return 0;
        }
        int sold = lots * lot;
        int pay = lots * price;
        int left = takeSlots(player, itemId, lv, sold);
        for (int i = 0; i < inv.method_5439() && left > 0; i++) {
            class_1799 st = inv.method_5438(i);
            if (!matches(st, itemId, lv)) continue;
            int take = Math.min(left, st.method_7947());
            left -= take;
            if (take >= st.method_7947()) inv.method_5447(i, class_1799.field_8037);
            else st.method_7939(st.method_7947() - take);
        }
        int spilled = 0;
        class_1799 money = new class_1799(item("minecraft:emerald"), pay);
        spilled = giveOrDrop(player, money);    // 塞不下的绿宝石掉在脚边，不吞钱
        c.store.add(c.vkey, c.areaKey(), itemId, sold, cap, c.day);
        c.store.walletAdd(c.vkey, -pay, c.walletCap(), c.day);
        awardXp(c, base, pay, true, player);
        worldSpread(c.store, c.day, itemId, sold);         // 全世界这种货又多了 → 以后越卖越贱（换算成原料记账）
        int pct = (int) Math.round(100.0 * Math.min(cap, have + sold) / cap);
        int restItems = total - sold;
        say(player, join("卖出 " + sold + "x ", itemId,
                dev.guoke.tradepost.market.Quality.suffix(lv)
                        + " → " + pay + " 绿宝石（" + lots + " 份 × " + price + "，每份 " + lot + " 件 · "
                        + c.title() + "，库存 " + pct + "%）"
                        + (restItems > 0 ? " · 剩 " + restItems + " 件不满一份" : "")
                        + (spilled > 0 ? " · 背包满了，" + spilled + " 个掉在脚边" : "")));
        return sold;
    }

    /** 成交后给村子记账；升级时在聊天栏提示（顺带告诉玩家新等级解锁了什么）。 */
    private static void awardXp(Ctx c, double base, int pay, boolean selling, class_3222 player) {
        try {
            if (c == null || c.store == null || pay <= 0) return;
            int before = c.store.level(c.vkey);
            int after = c.store.addXp(c.vkey, dev.guoke.tradepost.market.VillageLevel.xpFor(pay, base, selling));
            if (after > before) {
                StringBuilder sb = new StringBuilder();
                sb.append("〔集市〕").append(c.title()).append(" 升级 → ")
                        .append(dev.guoke.tradepost.market.VillageLevel.name(after))
                        .append(" Lv.").append(after).append("：买页 +")
                        .append(dev.guoke.tradepost.market.VillageLevel.sellSlots(after)
                                - dev.guoke.tradepost.market.VillageLevel.sellSlots(before))
                        .append(" 行 · 库存上限 +")
                        .append(Math.round((dev.guoke.tradepost.market.VillageLevel.stockMult(after) - 1) * 100))
                        .append("% · 银库上限 +")
                        .append(Math.round((dev.guoke.tradepost.market.VillageLevel.walletMult(after) - 1) * 100))
                        .append("%");
                int t0 = dev.guoke.tradepost.market.VillageLevel.topTier(before);
                int t1 = dev.guoke.tradepost.market.VillageLevel.topTier(after);
                if (t1 > t0) {
                    sb.append(" · 新解锁").append(dev.guoke.tradepost.market.Rarity.NAMES[t1]).append("货");
                }
                double next = dev.guoke.tradepost.market.VillageLevel.toNext(c.store.xp(c.vkey));
                if (next >= 0) sb.append(" · 下一级还差 ").append(Math.round(next)).append(" 经验");
                say(player, sb.toString());
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 村庄经验记账失败", t);
        }
    }

    /**
     * 全球饱和记账：把卖出/买进的成品打散成原始材料再累加。
     *
     * 直接按成品记会被"换皮"绕开 —— 金块、金苹果、金胡萝卜各有一套计数器，轮着卖永远打不崩。
     * 打散之后它们全落在金锭/金粒这些原料上，共享同一份计数，再靠 IndexGraph 把跌价传回成品。
     */
    public static void worldSpread(TradeStore store, long day, String itemId, double count) {
        if (store == null || itemId == null || count <= 0) return;
        double base = basePrice(itemId);
        if (base <= 0) return;
        dev.guoke.tradepost.market.Decompose d = DECOMP;
        if (d == null || base < dev.guoke.tradepost.market.Economy.current().worldMinBase) {
            store.worldAdd(itemId, count, base, day);
            return;
        }
        java.util.List<dev.guoke.tradepost.market.Decompose.Part> parts = d.of(itemId);
        if (parts.isEmpty()) {
            store.worldAdd(itemId, count, base, day);
            return;
        }
        for (dev.guoke.tradepost.market.Decompose.Part p : parts) {
            store.worldAdd(p.item(), count * p.count(), basePrice(p.item()), day);
        }
    }

    /** 潜行右键村民：把他背包里这一整叠（同种货全背包）卖给本村 */
    public static boolean sellHeld(class_1657 player, class_1268 hand) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return false;
            class_1799 held = player.method_5998(hand);
            if (held.method_7960()) return false;
            String id = itemId(held);
            if (id == null) return false;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            sellFromInv((class_3222) player, ctx(world, x, y, z), x, z, id, 0);
            return true;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 卖出失败", e);
            return false;
        }
    }

    private static class_5250 line(String text, class_124 color) {
        class_5250 t = class_2561.method_43470(text);
        return t.method_27692(color);
    }

    private static class_5250 clickable(class_5250 t, String command) {
        return t.method_10862(class_2583.field_24360.method_10958(
                new class_2558(class_2558.class_2559.field_11750, command)));
    }

    /**
     * 价目表：把背包里所有"本村肯收"的货按当前价列出来，点一下直接卖。
     * 潜行右键钟，或 /tradepost board 触发。
     */
    public static int priceBoard(class_3222 player) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return 0;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                say(player, "这里不是村庄 —— 钟只在村庄范围内有用（价格和库存按村庄算）");
                return 0;
            }
            Ctx c = ctx(world, x, y, z);
            class_1263 inv = player.method_31548();
            java.util.LinkedHashMap<String, Integer> have = new java.util.LinkedHashMap<>();
            for (int i = 0; i < inv.method_5439(); i++) {
                class_1799 st = inv.method_5438(i);
                if (st.method_7960()) continue;
                String id = itemId(st);
                if (id == null) continue;
                have.merge(id, st.method_7947(), Integer::sum);
            }
            player.method_43502(line("〔集市〕" + c.title() + " · 第 " + c.day + " 天 · 点击行即可卖出",
                    class_124.field_1065), false);
            double purse0 = c.store.wallet(c.vkey, c.walletCap(), c.day);
            double credit0 = c.store.credit(player.method_5667().toString());
            player.method_43502(line(" 本村银库 " + (int) purse0 + " / " + (int) c.walletCap() + " 绿宝石"
                    + (purse0 < c.walletCap() * 0.2 ? "（快见底了，去别村卖或等它回血）" : "")
                    + (credit0 > 0.004 ? " · 你的零头 " + fmt(credit0) + " 绿宝石" : ""),
                    class_124.field_1080), false);
            int shown = 0;
            long sum = 0;
            double sumExact = 0;
            for (java.util.Map.Entry<String, Integer> e : have.entrySet()) {
                String id = e.getKey();
                int n = e.getValue();
                double unit = sellUnit(c, x, z, id);
                if (unit <= 0) continue;
                int lot = dev.guoke.tradepost.market.Lot.of(unit);
                int price = dev.guoke.tradepost.market.Lot.price(unit, lot);
                int lots = n / lot;
                int pay = lots * price;
                String tag = "";
                // 先看"产"再看"缺"：同一类别被同时写进两边时（配置重复），
                // 以"本村自己在出"为准，不然会出现"又卖给你、又说缺货加价收"。
                if (c.ind != null && VillageMarket.listedIn(CATALOG, c.ind.supply(), id)) tag = "[产]";
                else if (c.ind != null && VillageMarket.listedIn(CATALOG, c.ind.demand(), id)) tag = "[缺]";
                else if (c.ind != null && dev.guoke.tradepost.market.DemandGraph.of(CATALOG).wants(c.ind.id(), id)) tag = "[要]";
                String tail = lots > 0 ? " → " + pay + " 绿宝石"
                        : " → 不足一份（" + lot + " 件起收）";
                if (c.ind != null && VillageMarket.listedIn(CATALOG, c.ind.demand(), id)) {
                    player.method_43502(clickable(line(String.format(" %s %dx %-20s 每 %d 件 = %d 绿宝石%s",
                            tag, n, shortId(id), lot, price, tail), class_124.field_1077),
                            "/tradepost sell " + id), false);
                } else {
                    player.method_43502(clickable(line(String.format(" %s %dx %-20s 每 %d 件 = %d 绿宝石%s",
                            tag, n, shortId(id), lot, price, tail), tag.equals("[产]") ? class_124.field_1063 : class_124.field_1060),
                            "/tradepost sell " + id), false);
                }
                sum += pay;
                shown++;
                if (shown >= 24) break;
            }
            if (shown == 0) {
                say(player, "背包里没有本村肯收的货");
            } else {
                player.method_43502(clickable(line(" 合计可卖 " + sum + " 绿宝石（整份成交）· 全部卖出",
                        class_124.field_1065), "/tradepost sellall"), false);
            }
            List<String> rs = rumors(world, x, z);
            if (!rs.isEmpty()) say(player, "风声：" + String.join(" ｜ ", rs));
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 价目表失败", e);
            return 0;
        }
    }

    /**
     * /tradepost why &lt;物品&gt;：把这件货的定价链路全摊开——基础价从哪来、打散成什么原料、
     * 全球饱和到几成、本村收购/售价多少。调数值或怀疑价格怪的时候用这个。
     */
    /**
     * /tradepost last —— 查最近一笔成交物品的价格是怎么一步步算出来的（含算法）。
     */
    public static int explainLast(class_2168 src) {
        try {
            if (!(src.method_44023() instanceof class_3222 player)) {
                send(src, "只能玩家用");
                return 0;
            }
            if (LAST_TRADE_ID.isEmpty()) {
                send(src, "§7还没有成交记录，先买或卖一笔。");
                return 0;
            }
            String id = LAST_TRADE_ID;
            String kind = LAST_TRADE_TAB == 0 ? "买" : "卖";
            StringBuilder sb = new StringBuilder();
            sb.append("§6最近一笔：§r").append(kind).append(" ").append(id)
              .append(" ×").append(LAST_TRADE_PER).append("件 = ").append(LAST_TRADE_UNIT).append(" 颗/份").append('\n');
            sb.append("§7──────────────────────\n");
            for (String line : tracePrice(id)) {
                sb.append(line).append('\n');
            }
            // 买入价链（含品质/各乘区），需要村庄上下文
            if (LAST_TRADE_TAB == 0 && player.method_5770() instanceof class_3218 world) {
                int px = (int) Math.floor(player.method_23317());
                int py = (int) Math.floor(player.method_23318());
                int pz = (int) Math.floor(player.method_23321());
                Ctx c = ctx(world, px, py, pz);
                if (c != null) {
                    sb.append("§7──────────────────────\n");
                    for (String line : traceBuy(c, id)) {
                        sb.append(line).append('\n');
                    }
                }
            }
            sb.append("§7──────────────────────\n");
            sb.append("§7卖价另乘 0.75 本地价差。");
            send(src, sb.toString());
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 查最近一笔失败", e);
            return 0;
        }
    }

    /** 把某件货的基础价推导过程写成几行文字（给 /tradepost last 用，递归展开到原料）。 */
    private static java.util.List<String> tracePrice(String id) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            traceRecursive(id, 0, out);
        } catch (Throwable t) {
            out.add("推导失败：" + t);
        }
        return out;
    }

    /** 递归推导一件货：配方物品展开每个原料的价格（封顶 6 层防循环）。 */
    private static void traceRecursive(String id, int depth, java.util.List<String> out) {
        if (depth > 6) return;
        String indent = "  ".repeat(depth);
        double base = basePrice(id);
        String src = srcOf(id);
        dev.guoke.tradepost.market.Economy eco = dev.guoke.tradepost.market.Economy.current();
        double ff = (!id.startsWith("minecraft:") && isEdible(id)) ? Math.max(1.0, eco.foodMultiplier) : 1.0;
        double rm = dev.guoke.tradepost.market.Rarity.mult(CATALOG.rarity(id));
        if ("配方".equals(src)) {
            dev.guoke.tradepost.market.RecipePricer.Rec rec = findRecipe(id);
            if (rec == null) {
                out.add(indent + shortId(id) + " = " + fmt(base) + "  §7(配方，条目没找到)");
                return;
            }
            out.add(indent + "§6" + shortId(id) + "§r = " + fmt(base) + "  §7(配方)");
            StringBuilder rp = new StringBuilder(indent + "  配方：");
            boolean first = true;
            for (java.util.List<String> slot : rec.slots()) {
                if (!first) rp.append(" + ");
                rp.append(slot != null && !slot.isEmpty() ? shortId(slot.get(0)) : "?");
                first = false;
            }
            rp.append(" → ").append(shortId(id)).append("×").append(Math.max(1, rec.count()));
            out.add(rp.toString());
            double sum = 0;
            java.util.List<String> ingIds = new java.util.ArrayList<>();
            for (java.util.List<String> slot : rec.slots()) {
                double best = 0; String bestId = null;
                if (slot != null) for (String ing : slot) {
                    double v = basePrice(ing);
                    if (v > best) { best = v; bestId = ing; }
                }
                if (bestId == null) continue;
                sum += best;
                ingIds.add(bestId);
                out.add(indent + "  · " + shortId(bestId) + " = " + fmt(best) + "  §7(" + srcOf(bestId) + ")");
            }
            int n = Math.max(1, rec.count());
            double material = sum / n;
            double bonus = isEdible(id) ? 1.0 : eco.craftPenalty;
            double floor = isDish(id) ? dev.guoke.tradepost.market.Cuisine.minPrice(id, rec.slots()) : 0;
            int d = dev.guoke.tradepost.market.RecipePricer.depthOf(id);
            double labor = d * eco.stepLabor;
            out.add(indent + "  材料成本 = " + fmt(material) + "  §7(原料合计 ÷ 产出 " + n + ")");
            out.add(indent + "  × 加成 " + fmt(bonus) + (isEdible(id) ? "  §7(食物不打折)" : "  §7(工业折价)"));
            if (floor > 0) out.add(indent + "  地板 " + fmt(floor) + "  §7(菜/酒等级)");
            out.add(indent + "  + 工序劳动 " + fmt(labor) + "  §7(深度 " + d + " × stepLabor " + fmt(eco.stepLabor) + ")");
            double computed = Math.max(floor, material * bonus) + labor;
            double actual = computed;
            // 酒类底价 8.0（TagCatalog.DRINK_FLOOR）：配方价低于它就抬上去，酒品质倍率再拉开
            if (isDrinkItem(id) && computed > 0 && computed < 8.0) {
                out.add(indent + "  酒类底价：" + fmt(computed) + " < 8.0 → 抬到 8.0");
                actual = 8.0;
            }
            out.add(indent + "  = " + fmt(actual) + "  §7(基础价 " + fmt(base) + ")");
            for (String ing : ingIds) {
                if ("配方".equals(srcOf(ing))) traceRecursive(ing, depth + 1, out);
            }
        } else if ("手写".equals(src)) {
            out.add(indent + shortId(id) + " = " + fmt(base) + "  §7(手写 values.json)");
        } else if ("标签".equals(src)) {
            out.add(indent + shortId(id) + " = " + fmt(base) + "  §7(标签 " + fmt(base / (ff * rm))
                    + " × 食物倍率 " + fmt(ff) + " × 稀有度 " + fmt(rm) + ")");
        } else if ("类别".equals(src)) {
            out.add(indent + shortId(id) + " = " + fmt(base) + "  §7(类别 " + fmt(base / (ff * rm))
                    + " × 食物倍率 " + fmt(ff) + " × 稀有度 " + fmt(rm) + ")");
        } else {
            out.add(indent + shortId(id) + " = " + fmt(base) + "  §7(兜底 " + fmt(base / (ff * rm))
                    + " × 食物倍率 " + fmt(ff) + " × 稀有度 " + fmt(rm) + ")");
        }
    }

    /** 买入价推导链（基础价 → 各乘区 → 品质 → 每份价），中文标签 */
    private static java.util.List<String> traceBuy(Ctx c, String id) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            if (c == null) return out;
            double base = basePrice(id);
            dev.guoke.tradepost.market.Economy eco = dev.guoke.tradepost.market.Economy.current();
            double total = base;
            out.add("买入价链：");
            out.add("  基础价 " + fmt(total));
            double chain = c.ind == null ? 1.0 : dev.guoke.tradepost.market.VillageMarket.chainFactor(
                    Tradepost.data(), CATALOG, c.seed, c.hx, c.hz, c.day, c.ind, c.prosperity, id);
            double[] steps = {
                    chain,
                    eco.buyBase,
                    dev.guoke.tradepost.market.VillageMarket.buyFactor(),
                    dev.guoke.tradepost.market.VillageMarket.roadFactor(),
                    dev.guoke.tradepost.market.VillageMarket.quirk(c.seed, c.hx, c.hz, id),
                    c.ind == null ? 1.0 : dev.guoke.tradepost.market.VillageMarket.daily(c.seed, c.hx, c.hz, c.day, c.ind.id()),
                    dev.guoke.tradepost.market.VillageMarket.marketDay(c.seed, c.hx, c.hz, c.day) ? eco.marketDayBuy : 1.0,
                    c.store == null ? 1.0 : dev.guoke.tradepost.market.Stock.buyImpact(
                            dev.guoke.tradepost.market.Stock.ratio(
                                    c.store.get(c.vkey, id, dev.guoke.tradepost.market.Stock.cap(base), c.day),
                                    dev.guoke.tradepost.market.Stock.cap(base)))
            };
            String[] names = {"产业链", "买入基准", "富庶", "道路", "村癖", "行情", "集市日", "库存"};
            for (int i = 0; i < steps.length; i++) {
                double nv = total * steps[i];
                out.add("  × " + names[i] + " " + fmt(steps[i]) + " → " + fmt(nv));
                total = nv;
            }
            int q = offerQuality(c, id);
            if (q > 0) {
                double qm = dev.guoke.tradepost.market.Quality.mult(q);
                out.add("  × 品质 " + dev.guoke.tradepost.market.Quality.name(q) + "（档" + q + "） " + fmt(qm)
                        + " → " + fmt(total * qm));
                total *= qm;
            }
            out.add("  = " + fmt(total) + "  §7(每份价)");
        } catch (Throwable t) {
            out.add("  买入价推导失败：" + t);
        }
        return out;
    }

    private static dev.guoke.tradepost.market.RecipePricer.Rec findRecipe(String id) {
        if (RECIPES == null) return null;
        for (dev.guoke.tradepost.market.RecipePricer.Rec r : RECIPES) {
            if (id.equals(r.output())) return r;
        }
        return null;
    }

    /** /tradepost setlevel <1..5>：把当前村庄直接设到指定等级（测试用，可降级） */
    public static int cmdSetlevel(class_2168 src, int n) {
        try {
            if (!(src.method_44023() instanceof class_3222 player)) {
                send(src, "只能玩家用");
                return 0;
            }
            if (!(player.method_5770() instanceof class_3218 world)) return 0;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                send(src, "这里不是村庄");
                return 0;
            }
            int target = Math.max(1, Math.min(dev.guoke.tradepost.market.VillageLevel.MAX, n));
            double need = dev.guoke.tradepost.market.VillageLevel.XP[target - 1];
            Ctx c = ctx(world, x, y, z);
            String vkey = c.vkey;   // 同 uplevel：用面板那把 key，别写错地方
            c.store.setXp(vkey, need);
            send(src, "村庄等级设为 Lv." + target + "（" + dev.guoke.tradepost.market.VillageLevel.name(target) + "）");
            return 1;
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 设等级失败", t);
            return 0;
        }
    }

    /** /tradepost uplevel：把当前村庄直接升一级（测试用） */
    public static int cmdUplevel(class_2168 src) {
        try {
            if (!(src.method_44023() instanceof class_3222 player)) {
                send(src, "只能玩家用");
                return 0;
            }
            if (!(player.method_5770() instanceof class_3218 world)) return 0;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                send(src, "这里不是村庄 —— 升级只在村庄范围内有效");
                return 0;
            }
            Ctx c = ctx(world, x, y, z);
            // 必须用面板/货架同一把 key（c.vkey 带维度前缀）；villageKey 不带前缀，
            // 经验会写到另一把钥匙上，面板读不到，等级永远不变（实测就是这个 bug）
            String vkey = c.vkey;
            int lv = c.store.level(vkey);
            if (lv >= dev.guoke.tradepost.market.VillageLevel.MAX) {
                send(src, "已经是最高等级 Lv." + lv + "（" + dev.guoke.tradepost.market.VillageLevel.name(lv) + "）");
                return 0;
            }
            double need = dev.guoke.tradepost.market.VillageLevel.XP[lv];   // 下一级门槛
            double cur = c.store.xp(vkey);
            c.store.addXp(vkey, Math.max(0, need - cur));
            int after = c.store.level(vkey);
            send(src, "村庄升级：Lv." + lv + "（" + dev.guoke.tradepost.market.VillageLevel.name(lv)
                    + "） → Lv." + after + "（" + dev.guoke.tradepost.market.VillageLevel.name(after) + "）");
            return 1;
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 升级失败", t);
            return 0;
        }
    }

    public static int explain(class_2168 src, String rawId) {
        try {
            if (!(src.method_44023() instanceof class_3222 player)) {
                send(src, "只能玩家用");
                return 0;
            }
            if (!(player.method_5770() instanceof class_3218 world)) return 0;
            String itemId = rawId.indexOf(':') < 0 ? "minecraft:" + rawId : rawId;
            class_1792 it = ITEMS.method_10223(class_2960.method_60654(itemId));
            if (it == null) {
                send(src, "没这个物品：" + itemId);
                return 0;
            }
            ensureRecipePrices(world);
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            Ctx c = ctx(world, x, y, z);
            long day = c.day;

            StringBuilder sb = new StringBuilder();
            for (String line : tracePrice(itemId)) {
                sb.append(line).append('\n');
            }
            sb.append("§7──────────────────────\n");

            dev.guoke.tradepost.market.Decompose d = DECOMP;
            if (d == null) {
                sb.append("打散：配方还没扫完（交易一次或重载后自动扫）\n");
            } else {
                java.util.List<dev.guoke.tradepost.market.Decompose.Part> parts = d.of(itemId);
                if (parts.isEmpty()) {
                    sb.append("打散：自己就是原料\n");
                } else {
                    StringBuilder ds = new StringBuilder("打散：");
                    boolean first = true;
                    for (dev.guoke.tradepost.market.Decompose.Part p : parts) {
                        if (!first) ds.append(" + ");
                        ds.append(shortId(p.item())).append("×").append(fmt(p.count()));
                        first = false;
                    }
                    ds.append("  §7(卖出时按这些原料记全球饱和)");
                    sb.append(ds).append('\n');
                }
            }

            double base = basePrice(itemId);
            TradeStore store = c.store;
            double idx = worldIndexFor(store, day, itemId);
            sb.append("全球饱和：指数 ").append(fmt(idx)).append("§7（1.00 = 没被打烂）\n");

            double cap = capOf(itemId, base, c.station);
            double have = store.get(c.vkey, itemId, cap, day);
            sb.append("本村库存：").append(fmt(have)).append(" / ").append(fmt(cap))
              .append("（").append(Math.round(100 * dev.guoke.tradepost.market.Stock.ratio(have, cap))).append("%）\n");

            double sellU = sellUnit(c, x, z, itemId);
            double area = store.pressure(c.areaKey(), itemId, cap, day) * dev.guoke.tradepost.market.Economy.current().areaSpread;
            sb.append("收购价：").append(fmt(sellU)).append("/个  §7（片区压力 ").append(fmt(area)).append("）\n");
            double buyU = 0;
            for (dev.guoke.tradepost.market.OfferSpec s : offersFor(c)) {
                if ("minecraft:emerald".equals(s.giveItem()) && itemId.equals(s.getItem()) && s.getCount() > 0) {
                    double u = s.giveCount() / (double) s.getCount();
                    if (buyU <= 0 || u < buyU) buyU = u;
                }
            }
            if (buyU > 0) sb.append("出售价：").append(fmt(buyU)).append("/个  §7（你从村里买）\n");
            else sb.append("出售价：本村不卖这件货\n");
            sb.append(c.title());
            send(src, sb.toString());
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] why 失败", e);
            return 0;
        }
    }

    /** 基础价从哪来：手写表 / 配方推导 / 标签兜底 / 默认兜底 */
    private static String priceSource(String itemId) {
        double v = Tradepost.valueOf(itemId);
        if (v > 0) return "手写表 values.json";
        if (CATALOG instanceof TagCatalog tc) {
            String s = tc.sourceOf(itemId);
            if (s != null) return s;
        }
        return "未知";
    }

    /** /tradepost board：开价目表（等同于潜行右键钟） */
    public static int cmdBoard(class_2168 src) {
        if (!(src.method_44023() instanceof class_3222 player)) {
            send(src, "只能玩家用");
            return 0;
        }
        return priceBoard(player);
    }

    /** /tradepost sell <itemId> [件数] */
    public static int cmdSell(class_2168 src, String itemId, int count) {
        try {
            if (!(src.method_44023() instanceof class_3222 player)) {
                send(src, "只能玩家用");
                return 0;
            }
            if (!(player.method_5770() instanceof class_3218 world)) return 0;
            if (itemId.indexOf(':') < 0) itemId = "minecraft:" + itemId;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            int sold = sellFromInv(player, ctx(world, x, y, z), x, z, itemId, count);
            return sold > 0 ? 1 : 0;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] sell 失败", e);
            return 0;
        }
    }

    /** /tradepost sellall：背包里能卖的都卖 */
    public static int cmdSellAll(class_2168 src) {
        try {
            if (!(src.method_44023() instanceof class_3222 player)) {
                send(src, "只能玩家用");
                return 0;
            }
            if (!(player.method_5770() instanceof class_3218 world)) return 0;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            Ctx c = ctx(world, x, y, z);
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            class_1263 inv = player.method_31548();
            for (int i = 0; i < inv.method_5439(); i++) {
                class_1799 st = inv.method_5438(i);
                if (st.method_7960()) continue;
                String id = itemId(st);
                if (id != null) ids.add(id);
            }
            int kinds = 0;
            double exactSum = 0;
            double creditNow = c.store.credit(player.method_5667().toString());
            for (String id : ids) {
                double unit = sellUnit(c, x, z, id);
                int n = countOf(inv, id);
                if (unit > 0 && n > 0) exactSum += unit * n;
                if (sellFromInv(player, c, x, z, id, 0) > 0) kinds++;
            }
            long total = (long) Math.floor(exactSum + creditNow + 1e-9);
            double left = c.store.credit(player.method_5667().toString());
            say(player, "一共卖了 " + kinds + " 种货，合计 " + total + " 绿宝石"
                    + (left > 0.004 ? "（零头 " + fmt(left) + " 记着）" : ""));
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] sellall 失败", e);
            return 0;
        }
    }

    /**
     * 把一件货按"桶"的规则记进 have（附魔书按档位、带品质的按档次分开）。
     * 背包里的货和天平上已摆的货必须走同一套规则：两边算法只要有一点不同，
     * 玩家把货挪到天平上，这一行就会从卖页里消失。
     */
    private static void countInto(java.util.Map<String, Integer> have, int[] booze, class_1799 st) {
        if (st == null || st.method_7960()) return;
        String id = itemId(st);
        if (id == null) return;
        int q = brewOf(st);
        if (hasQuality(id)) {
            booze[0]++;
            if (q > 0) booze[1]++;
        }
        // 附魔书按"附魔+等级"分桶：白板和顶级经验修补不能是同一个价。
        // 白板/多附魔的书没有对应的成本栈可比，不收（不再按 20 的底价当普通货买）
        if ("minecraft:enchanted_book".equals(id)) {
            String book = bookCode(st);
            if (book == null) return;
            have.merge(book, st.method_7947(), Integer::sum);
            return;
        }
        // 带品质的按档次分开报价：同一个物品，典藏和劣质不能混成一堆
        have.merge(q > 0 ? id + "#" + q : id, st.method_7947(), Integer::sum);
    }

    // ───────────── GUI（A 档：行情 + 点行卖出） ─────────────

    /** 构造 GUI 用的价目表 JSON；不在村庄返回 null（已提示玩家） */
    public static String boardJson(class_3222 player) {
        long[] marks = new long[6];                   // 分段计时：0=底价/市场 1=收购 2=回收 3=货架 4=结束
        marks[0] = System.nanoTime();
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return null;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                say(player, "这里不是村庄 —— 钟只在村庄范围内有用（价格和库存按村庄算）");
                return null;
            }
            Ctx c = ctx(world, x, y, z);
            // 取证用：每次开面板记下"判给了哪处市场 / 哪个锚点 / 什么产业"，
            // 走一格换产业时对比 latest.log 里两次记录就知道哪一步在飘
            Tradepost.debug("[tradepost] 价目表 @ {},{} → 市场 {} · 锚点 {},{} · {} · {} 户 {} 式 · 富庶 {}",
                    x, z, c.vkey, c.hx, c.hz,
                    c.ind == null ? "无产业" : c.ind.display(), c.houses, c.kinds,
                    String.format(java.util.Locale.ROOT, "%.2f", c.wealth));
            marks[1] = System.nanoTime();
            class_1263 inv = player.method_31548();
            java.util.LinkedHashMap<String, Integer> have = new java.util.LinkedHashMap<>();
            // 取证：背包里的酒到底读没读到品质（读不到就全按裸瓶算，界面上看不出档次）
            int[] booze = new int[2];                    // [0]=酒摞数 [1]=读到品质的摞数
            for (int i = 0; i < inv.method_5439(); i++) {
                countInto(have, booze, inv.method_5438(i));
            }
            // 天平上已经摆上去的货也算"你有的"。不然一成交它们就不在背包里了 →
            // 这一行从列表消失 → 天平上剩下的一整摞再也找不到行，
            // 玩家眼里就是"卖完一份，再点怎么都没反应"（整摞卖不掉的真因）。
            if (player.field_7512 instanceof class_1728 h0) {
                var mi0 = dev.guoke.tradepost.mc.MarketMerchant.merchantInv(h0);
                if (mi0 != null) {
                    countInto(have, booze, mi0.method_5438(0));
                    countInto(have, booze, mi0.method_5438(1));
                }
            }
            int boozeStacks = booze[0];
            int boozeWithQ = booze[1];
            if (boozeStacks > 0) {
                Tradepost.LOGGER.info("[tradepost] 背包酒 {} 摞，读到品质 {} 摞（读不到就按裸瓶价收）",
                        boozeStacks, boozeWithQ);
            }
            // 村庄等级：卖页特收行数、买页每份件数、升级提示都要它 —— 提到最前面算一次
            double vxp = c.store.xp(c.vkey);
            int vlv = dev.guoke.tradepost.market.VillageLevel.of(vxp);
            // 涨跌箭头：这次开面板的单价（分）先攒着，最后一并记进存档，下次开面板拿它比
            java.util.Map<String, Integer> nowCents = new java.util.LinkedHashMap<>();
            java.util.List<com.google.gson.JsonObject> rows = new java.util.ArrayList<>();
            long sum = 0;
            double maxPressure = 0;
            for (java.util.Map.Entry<String, Integer> e : have.entrySet()) {
                String bucket = e.getKey();
                int n = e.getValue();
                int q = 0;
                String id = bucket;
                int hash = bucket.indexOf('#');
                if (hash > 0) {
                    id = bucket.substring(0, hash);
                    try {
                        q = Integer.parseInt(bucket.substring(hash + 1));
                    } catch (NumberFormatException ignored) {
                        q = 0;
                    }
                }
                // 附魔书按书的档位定价（学术铺 I 级价 × 等级倍率），再按本地差价 75% 收。
                // 早先这里走的是"附魔书这个物品"的底价 26，于是 7 买来的书能 26 卖回去，白送钱。
                boolean isBook = id.startsWith("book:");
                String stockId = isBook ? "minecraft:enchanted_book" : id;
                long ts0 = System.nanoTime();
                double unit = isBook
                        ? Math.max(1, Math.round(dev.guoke.tradepost.market.BookShop.valueOf(id) * LOCAL_SPREAD))
                        : sellUnit(c, x, z, id, q);
                long td = (System.nanoTime() - ts0) / 1_000_000L;
                if (td >= 20L) {
                    // 收购这一页 2 秒多，到底是哪一件算得慢（每件都记太吵，只记 ≥20ms 的）
                    Tradepost.LOGGER.info("[tradepost] 收购定价慢 {} ms：{}", td, id);
                }
                if (unit <= 0) continue;
                if (q > 0) {
                    // 取证：这一档到底乘没乘上倍率（同一件货 1~6 档都写同一个数就是没乘）
                    Tradepost.LOGGER.info("[tradepost] 卖行品质 {} 档{} 共{}件 单价{} 倍率{} 底价{} 店里卖{}",
                            id, q, n, (int) Math.round(unit),
                            String.format(java.util.Locale.ROOT, "%.2f",
                                    dev.guoke.tradepost.market.Quality.mult(q)),
                            String.format(java.util.Locale.ROOT, "%.2f", basePrice(id)),
                            String.format(java.util.Locale.ROOT, "%.2f",
                                    c.buyUnits().getOrDefault(id, 0.0)));
                }
                int lot = isBook ? 1 : dev.guoke.tradepost.market.Lot.of(unit);
                // 手里不够"一次的量"就不卖：老代码 n<lot 时把 lot 缩成 n，
                // 1 个泥土（lot=31）就能按 Lot.price 兜底换 1 颗绿宝石——刷钱口子。
                // 改成 lots=0：行保留、标"缺N个"，凑够一份才给钱。
                int price = dev.guoke.tradepost.market.Lot.price(unit, lot);
                int lots = n / lot;
                int pay = lots * price;
                String tag = "";
                // 先看"产"再看"缺"：同一类别被同时写进两边时（配置重复），
                // 以"本村自己在出"为准，不然会出现"又卖给你、又说缺货加价收"。
                if (c.ind != null && VillageMarket.listedIn(CATALOG, c.ind.supply(), id)) tag = "[产]";
                else if (c.ind != null && VillageMarket.listedIn(CATALOG, c.ind.demand(), id)) tag = "[缺]";
                else if (c.ind != null && dev.guoke.tradepost.market.DemandGraph.of(CATALOG).wants(c.ind.id(), id)) tag = "[要]";
                com.google.gson.JsonObject row = new com.google.gson.JsonObject();
                row.addProperty("id", id);
                if (isBook) {
                    // 行里的"货"必须是一本真书（带 stored_enchantments）：
                    // 玩家塞进天平的真书要跟这个成本栈对得上，界面才会亮
                    row.addProperty("base", "minecraft:enchanted_book");
                    row.addProperty("name", shortId("minecraft:enchanted_book"));
                    String bk = nameKey("minecraft:enchanted_book");
                    if (bk != null) row.addProperty("key", bk);
                    // 码里可能是一本多附魔（book:a@1;b@2）：图标只画第一个附魔，
                    // 价仍按 valueOf 的全部之和（见上面 unit 的算法）。
                    row.addProperty("bookcode", id.substring(5));   // 造真书时用整条码（多附魔不丢）
                    String body = id.substring(5);
                    int semi = body.indexOf(';');
                    if (semi >= 0) body = body.substring(0, semi);
                    int bar = body.lastIndexOf('|');
                    int at = body.indexOf('@');
                    if (bar > 0) {
                        row.addProperty("ench", body.substring(0, bar));
                        row.addProperty("elvl", Integer.parseInt(body.substring(bar + 1)));
                    } else if (at > 0) {
                        row.addProperty("ench", body.substring(0, at));
                        row.addProperty("elvl", Integer.parseInt(body.substring(at + 1)));
                    }
                } else {
                    row.addProperty("name", shortId(id));
                    String nk = nameKey(id);
                    if (nk != null) row.addProperty("key", nk);
                }
                row.addProperty("count", n);
                row.addProperty("unit", String.format("%.3f", unit));
                row.addProperty("lot", lot);
                row.addProperty("price", price);
                row.addProperty("lots", lots);
                row.addProperty("pay", pay);
                row.addProperty("need", Math.max(0, lot - n));   // 还差几个才够一份（0=够）
                row.addProperty("tag", tag);
                markTrend(c, nowCents, row, id + (q > 0 ? "#" + q : ""), unit);
                if (q > 0) {
                    row.addProperty("q", q);
                    row.addProperty("qname", dev.guoke.tradepost.market.Quality.name(q));
                }
                rows.add(row);
                sum += pay;
                double p = c.store.pressure(c.areaKey(), stockId,
                        capOf(stockId, basePrice(stockId), c.station), c.day);
                if (p > maxPressure) maxPressure = p;
            }
            marks[2] = System.nanoTime();
            // ── 装备/工具/盔甲：黑名单把它们挡在普通收购之外，走单独的回收通道 ──
            // 材料价 × 55% × 耐久 × 附魔，每村每天 2 件（额度按天记在 TradeStore 里）
            java.util.LinkedHashMap<String, com.google.gson.JsonObject> gearRows = new java.util.LinkedHashMap<>();
            for (int i = 0; i < inv.method_5439(); i++) {
                class_1799 gs = inv.method_5438(i);
                if (gs.method_7960()) continue;
                String gid = itemId(gs);
                if (gid == null || !CATALOG.banned(gid)) continue;   // 不在黑名单 = 走普通收购
                int worth = gearPrice(gs, gid);
                Tradepost.LOGGER.info("[tradepost] 回收候选 槽{} {} 材料价{} 附魔{} 耐久{} 回收价{}",
                        i, gid, String.format(java.util.Locale.ROOT, "%.2f", gearBase(gid)),
                        enchMap(gs), String.format(java.util.Locale.ROOT, "%.2f", gearDur(gs)), worth);
                if (worth <= 0) continue;
                String sig = gearSig(gs, gid);
                com.google.gson.JsonObject row = gearRows.get(sig);
                if (row == null) {
                    row = new com.google.gson.JsonObject();
                    row.addProperty("id", "gear:" + sig);
                    row.addProperty("name", shortId(gid));
                    String nk = nameKey(gid);
                    if (nk != null) row.addProperty("key", nk);
                    row.addProperty("gear", 1);
                    row.addProperty("sig", sig);
                    // 造物要用：Goods.row 拿 "base" 当物品 id、"ench"/"elvl" 当附魔。
                    // 少了这三样，列表那行会被拼成空栈，MarketMerchant.add 直接判成"不是货"整行丢掉
                    // —— 这就是"武器没人收"的真身（代码里一行都没出现过）。
                    row.addProperty("base", gid);
                    java.util.Map<String, Integer> em = enchMap(gs);
                    String topEnch = null;
                    int topLv = 0;
                    for (java.util.Map.Entry<String, Integer> ee : em.entrySet()) {
                        if (ee.getValue() != null && ee.getValue() > topLv) {
                            topLv = ee.getValue();
                            topEnch = ee.getKey();
                        }
                    }
                    if (topEnch != null) {
                        row.addProperty("ench", topEnch);
                        row.addProperty("elvl", topLv);
                    }
                    row.addProperty("esum", gearEnch(gs));   // 附魔等级总和：结算 sameFamily 拿它比
                    row.addProperty("enchs", gearEnchCode(em));  // 全量附魔：图标/悬停显示全部词条
                    row.addProperty("lot", 1);
                    row.addProperty("price", worth);
                    row.addProperty("unit", String.format(java.util.Locale.ROOT, "%.3f", (double) worth));
                    row.addProperty("count", 0);
                    row.addProperty("tag", "[收]");
                    gearRows.put(sig, row);
                }
                row.addProperty("count", row.get("count").getAsInt() + gs.method_7947());
            }
            int gearLeft = GEAR_PER_DAY - c.store.gearSold(c.vkey, c.day, GEAR_KEY);
            if (!gearRows.isEmpty() || gearLeft <= 0) {
                Tradepost.LOGGER.info("[tradepost] 回收行 {} 条，今日额度剩 {}", gearRows.size(), gearLeft);
            }
            // 按回收价从高到低挑：每天只有 2 件额度，先给值钱的（否则背包前一格的白板垃圾
            // 会把额度吃掉，玩家眼里就是"剑卖不出去"）。1 颗绿宝石的东西不占额度。
            java.util.List<com.google.gson.JsonObject> gearList = new java.util.ArrayList<>(gearRows.values());
            gearList.sort((a, b) -> Integer.compare(b.get("price").getAsInt(), a.get("price").getAsInt()));
            for (com.google.gson.JsonObject row : gearList) {
                if (gearLeft <= 0) break;
                if (row.get("price").getAsInt() < 10) continue;   // 太便宜的不值一个日额度
                int n = Math.min(gearLeft, row.get("count").getAsInt());
                if (n <= 0) continue;
                gearLeft -= n;              // 额度是真的会被花掉的：不改这里，三行都能卖（每天 2 件的限制等于没有）
                row.addProperty("count", n);
                row.addProperty("lots", n);
                row.addProperty("pay", row.get("price").getAsInt() * n);
                markTrend(c, nowCents, row, "gear:" + row.get("sig").getAsString(),
                        Double.parseDouble(row.get("unit").getAsString()));
                rows.add(row);
                sum += row.get("pay").getAsInt();
            }
            // ── 特收：本村"缺"的货（列在产业需求表里）钉在卖页最前面几行，行数随村等级涨 ──
            // Lv1 2 行 → Lv5 6 行。只钉前几行：多出来的需求货照背包顺序排在后面，不抢界面。
            int specialMax = Math.max(2, Math.min(6, 1 + vlv));
            java.util.List<com.google.gson.JsonObject> front = new java.util.ArrayList<>();
            java.util.List<com.google.gson.JsonObject> rest = new java.util.ArrayList<>();
            int marked = 0;
            for (com.google.gson.JsonObject o : rows) {
                boolean want = o.has("tag") && "[缺]".equals(o.get("tag").getAsString());
                if (want && marked < specialMax) {
                    o.addProperty("special", 1);
                    front.add(o);
                    marked++;
                } else {
                    rest.add(o);
                }
            }
            if (!front.isEmpty()) {
                rows.clear();
                rows.addAll(front);
                rows.addAll(rest);
            }
            Tradepost.debug("[tradepost] 特收 {} 行（Lv{} 上限 {}）", marked, vlv, specialMax);
            int kinds = rows.size();
            marks[3] = System.nanoTime();
            // 行序**不按价钱排**：成交一次价钱就变，一重排玩家下一眼就点错行（"卖完一件就卖不了下一件"）。
            // 现在按背包装进来的顺序，同一摊货每次都在同一个位置。
            while (rows.size() > 40) rows.remove(rows.size() - 1);
            int nGear = 0, nBook = 0, nOther = 0;
            for (com.google.gson.JsonObject o : rows) {
                String bid = o.has("id") ? o.get("id").getAsString() : "";
                if (bid.startsWith("gear:")) nGear++;
                else if (bid.startsWith("book:")) nBook++;
                else nOther++;
            }
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < rows.size() && i < 16; i++) {
                if (i > 0) ids.append(" | ");
                ids.append(rows.get(i).has("id") ? rows.get(i).get("id").getAsString() : "?");
            }
            if (rows.size() > 16) ids.append(" …共").append(rows.size());
            Tradepost.LOGGER.info("[tradepost] 卖页 {} 行：装备 {} / 附魔书 {} / 普通 {}：{}",
                    rows.size(), nGear, nBook, nOther, ids);
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (com.google.gson.JsonObject o : rows) arr.add(o);
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("title", "〔集市〕" + c.title());
            root.addProperty("ind", c.ind == null ? "无产业" : c.ind.display());
            root.addProperty("lv", vlv);
            root.addProperty("lvname", dev.guoke.tradepost.market.VillageLevel.name(vlv));
            root.addProperty("xp", vxp);
            root.addProperty("xpnext", dev.guoke.tradepost.market.VillageLevel.toNext(vxp));
            // 界面下方那条信息栏要用的两个数：经验条比例、银库余额
            root.addProperty("xpr", dev.guoke.tradepost.market.VillageLevel.progress(vxp));
            root.addProperty("wallet", (int) c.store.wallet(c.vkey, c.walletCap(), c.day));
            // 今日收购预算还剩多少（闸关掉时是无穷大，这里压成一个「不显示」的哨兵值）
            double budgetRaw = c.store.budgetLeft(c.vkey, c.walletCap(), c.day);
            int budgetLeft = budgetRaw >= 99999 ? -1 : (int) Math.floor(budgetRaw);
            root.addProperty("budget", budgetLeft);
            root.addProperty("sub", "第 " + c.day + " 天"
                    + (c.marketDay() ? " ★集市日" : "")
                    + " · 银库 " + (int) c.store.wallet(c.vkey, c.walletCap(), c.day) + " 绿宝石"
                    + (budgetLeft >= 0 ? " · 今日还可收 " + budgetLeft + " 颗" : ""));
            root.addProperty("total", sum);
            root.add("sell", arr);
            // 本村在售的货（花绿宝石买）
            // 货架只生成一次：下面“买页/秘宝”两段都要这一份，原来各算一遍（一页白算两遍）
            final java.util.List<OfferSpec> specs = offersFor(c);
            com.google.gson.JsonArray buys = new com.google.gson.JsonArray();
            for (OfferSpec s : specs) {
                if (s.giveItem() == null || !s.giveItem().equals("minecraft:emerald")) continue;
                double base = basePrice(s.getItem());
                if (base <= 0) continue;
                double capB = capOf(s.getItem(), base, c.station);
                int stock = (int) Math.floor(c.store.get(c.vkey, s.getItem(), capB, c.day));
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", s.getItem());
                o.addProperty("name", shortId(s.getItem()));
                String bk = nameKey(s.getItem());
                if (bk != null) o.addProperty("key", bk);
                int lv = offerQuality(c, s.getItem());
                // 每份件数按村等级给足：Lv1 一次 8 件 → Lv5 一次 32 件（每级 +6）。
                // 绿宝石一格最多 64 颗，贵货自动折半到付得起为止（32 颗钻石 = 640 颗宝石，塞不下）。
                int lvB = Math.max(1, Math.min(5, vlv));
                int bundle = Math.min(64, Math.max(s.getCount(), 8 + 6 * (lvB - 1)));
                // 一堆塞不下的货（鸡蛋 16、雪球 16、末影珍珠 16、药水 1）按它自己的堆叠上限收
                try {
                    net.minecraft.class_1792 it0 = item(s.getItem());
                    if (it0 != null) {
                        net.minecraft.class_1799 probe0 = new net.minecraft.class_1799(it0, 1);
                        if (!probe0.method_7960()) bundle = Math.max(1, Math.min(bundle, probe0.method_7914()));
                    }
                } catch (Throwable ignored) {
                }
                int basePay = Math.max(1, s.giveCount());
                int rawPay = (int) Math.round(basePay * (double) bundle / Math.max(1, s.getCount()));
                int payNow = offerPrice(rawPay, lv);
                while (payNow > 64 && bundle > 1) {
                    bundle = Math.max(1, bundle / 2);
                    rawPay = (int) Math.round(basePay * (double) bundle / Math.max(1, s.getCount()));
                    payNow = offerPrice(rawPay, lv);
                }
                o.addProperty("count", bundle);     // 每份件数
                o.addProperty("pay", payNow);       // 每份绿宝石（含品质溢价）
                // 买页也挂涨跌箭头：比的是"一件多少钱"（每份总价 ÷ 件数），不然件数随等级变会把箭头带歪
                markTrend(c, nowCents, o, "buy:" + s.getItem(),
                        payNow / (double) Math.max(1, bundle));
                if (lv > 0) {
                    o.addProperty("q", lv);
                    o.addProperty("qname", dev.guoke.tradepost.market.Quality.name(lv));
                }
                // 库存不够一整份时按剩下的件数卖（半摞也能买），而不是整行判售罄
                if (stock > 0 && stock < bundle) {
                    bundle = stock;
                    rawPay = (int) Math.round(basePay * (double) bundle / Math.max(1, s.getCount()));
                    payNow = offerPrice(rawPay, lv);
                    o.addProperty("count", bundle);
                    o.addProperty("pay", payNow);
                }
                o.addProperty("stock", stock);
                o.addProperty("sold", stock <= 0);
                // 取证：薄行（库存储备不到 3 份）打一行，用来回答"为什么买一次就没货"
                if (stock < bundle * 3) {
                    Tradepost.LOGGER.info("[tradepost] 买行薄 {} 库存{}件 每份{}件 每份价{} 上限{}",
                            s.getItem(), stock, bundle, payNow, (int) capB);
                }
                buys.add(o);
            }
            // 军需铺的成品装备：一件一档，附魔按周轮换，每天额度卖完就空着
            if (sellsGear(c)) {
                for (dev.guoke.tradepost.market.GearShop.Gear g
                        : dev.guoke.tradepost.market.GearShop.list(c.vkey, Math.max(1, vlv), c.day)) {
                    int left = Math.max(0, g.quota() - c.store.gearSold(c.vkey, c.day, g.code()));
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", g.code());
                    o.addProperty("base", g.base());
                    o.addProperty("name", shortId(g.base()));
                    String gk = nameKey(g.base());
                    if (gk != null) o.addProperty("key", gk);
                    o.addProperty("count", 1);
                    o.addProperty("pay", g.price());
                    o.addProperty("stock", left);
                    o.addProperty("sold", left <= 0);
                    if (!g.ench().isEmpty() && g.enchLevel() > 0) {
                        o.addProperty("ench", g.ench());
                        o.addProperty("elvl", g.enchLevel());
                    }
                    buys.add(o);
                }
            }
            // 学术铺的附魔书：真书，stored_enchantments 不是空的
            if (sells(c, "@enchanted_book")) {
                for (dev.guoke.tradepost.market.BookShop.Book b
                        : dev.guoke.tradepost.market.BookShop.list(c.vkey, Math.max(1, vlv), c.day)) {
                    int left = Math.max(0, b.quota() - c.store.gearSold(c.vkey, c.day, b.code()));
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", b.code());
                    o.addProperty("base", "minecraft:enchanted_book");
                    o.addProperty("name", shortId("minecraft:enchanted_book"));
                    String bk2 = nameKey("minecraft:enchanted_book");
                    if (bk2 != null) o.addProperty("key", bk2);
                    o.addProperty("ench", b.ench());
                    o.addProperty("elvl", b.level());
                    o.addProperty("count", 1);
                    o.addProperty("pay", b.price());
                    o.addProperty("stock", left);
                    o.addProperty("sold", left <= 0);
                    buys.add(o);
                }
            }
            // 酿造铺的药水：真药水，potion_contents 带效果
            if (sells(c, "@potion")) {
                for (dev.guoke.tradepost.market.PotionShop.Potion p
                        : dev.guoke.tradepost.market.PotionShop.list(c.vkey, Math.max(1, vlv), c.day)) {
                    int left = Math.max(0, p.quota() - c.store.gearSold(c.vkey, c.day, p.code()));
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("id", p.code());
                    o.addProperty("base", p.item());
                    o.addProperty("name", shortId(p.item()));
                    String pk = nameKey(p.item());
                    if (pk != null) o.addProperty("key", pk);
                    o.addProperty("potion", p.effect());
                    o.addProperty("form", p.form());
                    o.addProperty("count", p.count());
                    o.addProperty("pay", p.price());
                    o.addProperty("stock", left);
                    o.addProperty("sold", left <= 0);
                    buys.add(o);
                }
            }
            root.add("buy", buys);
            // 这次的价钱记进存档：下次开面板拿它比出红绿箭头（同一天不覆盖，见 TradeStore.rememberPrices）
            c.store.rememberPrices(c.vkey, c.day, nowCents);
            marks[4] = System.nanoTime();
            // 秘宝（以物易物）：成本两样、不受库存限制，每 7 天换一批
            com.google.gson.JsonArray relics = new com.google.gson.JsonArray();
            for (OfferSpec s : specs) {
                if (!s.hasSecond()) continue;
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", s.getItem());
                o.addProperty("name", shortId(s.getItem()));
                String rk = nameKey(s.getItem());
                if (rk != null) o.addProperty("key", rk);
                o.addProperty("count", s.getCount());
                o.addProperty("cost", s.giveCount() + "x " + shortId(s.giveItem())
                        + " + " + s.secondCount() + "x " + shortId(s.secondItem()));
                com.google.gson.JsonArray costs = new com.google.gson.JsonArray();
                addCost(costs, s.giveItem(), s.giveCount());
                addCost(costs, s.secondItem(), s.secondCount());
                o.add("costs", costs);
                o.addProperty("ok", countOf(inv, s.giveItem()) >= s.giveCount()
                        && countOf(inv, s.secondItem()) >= s.secondCount());
                relics.add(o);
            }
            if (relics.size() == 0) {
                try {
                    java.util.List<dev.guoke.tradepost.market.MarketData.Relic> priced = new java.util.ArrayList<>();
                    for (dev.guoke.tradepost.market.MarketData.Relic rl : Tradepost.data().relics) {
                        if (rl.price() != null && rl.price() > 0) priced.add(rl);
                    }
                    if (!priced.isEmpty()) {
                        long week = Math.floorDiv(c.day, 7L);
                        int idx = (int) Math.floorMod(java.util.Objects.hash(c.seed, c.x, c.z, week), (long) priced.size());
                        dev.guoke.tradepost.market.MarketData.Relic rl = priced.get(idx);
                        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                        o.addProperty("id", rl.item());
                        o.addProperty("name", shortId(rl.item()));
                        String rk2 = nameKey(rl.item());
                        if (rk2 != null) o.addProperty("key", rk2);
                        o.addProperty("count", rl.count());
                        o.addProperty("cost", rl.price() + " 绿宝石");
                        o.addProperty("price", rl.price());
                        int cash = countOf(inv, "minecraft:emerald") + 9 * countOf(inv, "minecraft:emerald_block");
                        o.addProperty("ok", cash >= rl.price());
                        relics.add(o);
                    }
                } catch (Throwable ignored) {
                }
            }
            root.add("relics", relics);
            try {
                root.addProperty("fair", dev.guoke.tradepost.market.VillageMarket.marketDay(c.seed, c.x, c.z, c.day) ? 1 : 0);
            } catch (Throwable ignored) {
            }
            // 风声：只给方向 + 距离档（弱预报，会漏；不给坐标、不给产业）
            com.google.gson.JsonArray rumors = new com.google.gson.JsonArray();
            for (String s : rumors(world, x, z)) rumors.add(s);
            root.add("rumors", rumors);
            marks[5] = System.nanoTime();
            long totalMs = (marks[5] - marks[0]) / 1_000_000L;
            if (totalMs > 200L) {
                Tradepost.LOGGER.info("[tradepost] 开页 {} ms＝市场 {} ＋收购 {} ＋回收 {} ＋货架 {} ＋风气 {}",
                        totalMs, ms(marks[0], marks[1]), ms(marks[1], marks[2]),
                        ms(marks[2], marks[3]), ms(marks[3], marks[4]), ms(marks[4], marks[5]));
            }
            return root.toString();
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 构造价目表失败", e);
            return null;
        }
    }

    /**
     * 记下这一行此刻的单价（分）并给出涨跌标记，供面板画红/绿箭头。
     * 比的是"上次开面板那天"的价（TradeStore.seenCents）——同一天里不重记，
     * 所以成交、补货引起的价钱变动不会让箭头乱翻。
     */
    private static void markTrend(Ctx c, java.util.Map<String, Integer> now,
                                  com.google.gson.JsonObject row, String key, double unit) {
        if (c == null || unit <= 0) return;
        int cents = (int) Math.round(unit * 100);
        now.put(key, cents);
        int prev = c.store.seenCents(c.vkey, key);
        if (prev >= 0 && prev != cents) row.addProperty("tr", cents > prev ? 1 : -1);
    }

    /** 两段 nanoTime 之间是多少毫秒（计时日志用） */
    private static long ms(long from, long to) {
        return to <= 0 || from <= 0 ? 0 : (to - from) / 1_000_000L;
    }

    /** 潜行右键钟：客户端装了本模组就开 GUI，否则退回聊天栏价目表 */
    public static void openBoard(class_3222 player) {
        try {
            long t0 = System.nanoTime();
            String json = boardJson(player);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (ms > 200L) Tradepost.LOGGER.warn("[tradepost] 开集市耗时 {} ms（超过 200ms 说明有人在主线程干重活）", ms);
            if (json == null) return;
            com.google.gson.JsonObject root = null;
            try {
                root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            } catch (Throwable ignored) {
                // 解析不出来就走纯客户端界面
            }
            // 集市已经开着：只按新价目表重建报价，不重开容器（重开会闪一下并重置界面状态）
            if (root != null && MarketMerchant.refresh(player, root, json)) return;
            // 先把集市开成服务端真容器（原版商人容器）：报价走原版同步，背包/交易槽全原生；
            // 客户端收到 json 后把原版界面换成本模组的子类（共用同一个 handler）。
            if (root == null || !MarketMerchant.open(player, root, json)) {
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                        .canSend(player, dev.guoke.tradepost.market.MarketPayload.ID)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            player, new dev.guoke.tradepost.market.MarketPayload(json));
                } else {
                    priceBoard(player);
                }
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 开集市失败", t);
            priceBoard(player);
        }
    }

    /** 本村当前报价（含买入/卖出，与 GUI 完全同源） */
    private static java.util.List<OfferSpec> offersFor(Ctx c) {
        if (c.shelf != null) return c.shelf;          // 一次开页只生成一遍货架（这是开页最贵的一步）
        VillageMarket.setTraits(c.roadDeg, c.area, c.houses, c.kinds);
        dev.guoke.tradepost.market.MarketData data = Tradepost.data();
        if (data == null) return java.util.List.of();
        java.util.List<OfferSpec> res = VillageMarket.offers(data, c.seed, c.hx, c.hz, c.day, c.station,
                view(c.store, c.vkey, c.day, c.station), c.biome, CATALOG);
        c.shelf = res;
        return res;
    }

    /** 从背包扣货，返回实际扣掉的件数 */
    private static int takeItems(class_1263 inv, String itemId, int want) {
        int left = want;
        for (int i = 0; i < inv.method_5439() && left > 0; i++) {
            class_1799 st = inv.method_5438(i);
            if (st.method_7960()) continue;
            if (!itemId.equals(itemId(st))) continue;
            int n = Math.min(st.method_7947(), left);
            st.method_7939(st.method_7947() - n);
            if (st.method_7947() <= 0) inv.method_5447(i, class_1799.field_8037);
            left -= n;
        }
        return want - left;
    }

    /** 天平上的两个交易槽（原版商人容器的 0/1 号槽），背包之外的"手上" */
    private static boolean merchantOpen(class_3222 player) {
        return player.field_7512 instanceof class_1728;
    }

    /** 天平上有多少件这种货；level < 0 = 不分品质 */
    private static int countSlots(class_3222 player, String itemId, int level) {
        if (!merchantOpen(player)) return 0;
        int total = 0;
        for (int i = 0; i < 2; i++) {
            class_1735 s = player.field_7512.method_7611(i);
            if (s == null) continue;
            class_1799 st = s.method_7677();
            if (!st.method_7960() && matches(st, itemId, level)) total += st.method_7947();
        }
        return total;
    }

    private static int countSlots(class_3222 player, String itemId) {
        return countSlots(player, itemId, -1);
    }

    /** 先把天平上的货扣掉，返回还差多少件 */
    private static int takeSlots(class_3222 player, String itemId, int level, int want) {
        if (!merchantOpen(player)) return want;
        int left = want;
        for (int i = 0; i < 2 && left > 0; i++) {
            class_1735 s = player.field_7512.method_7611(i);
            if (s == null) continue;
            class_1799 st = s.method_7677();
            if (st.method_7960() || !matches(st, itemId, level)) continue;
            int n = Math.min(st.method_7947(), left);
            st.method_7939(st.method_7947() - n);
            s.field_7871.method_5447(s.field_7874, st.method_7947() <= 0 ? class_1799.field_8037 : st);
            left -= n;
        }
        return left;
    }

    /** 天平 + 背包一起扣货，返回实际扣掉的件数 */
    private static int takeAny(class_3222 player, String itemId, int want) {
        int rest = takeSlots(player, itemId, -1, want);
        return (want - rest) + takeItems(player.method_31548(), itemId, rest);
    }

    /** GUI 点行：买本村在售的货（units = 买几份；一份 = 面板那一行的件数。钱/库存不够就买能买的最大量） */
    public static void buyFromGui(class_3222 player, String itemId, int units) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                say(player, "这里不是村庄 —— 集市只在村庄范围内有用");
                return;
            }
            Ctx c = ctx(world, x, y, z);
            if (itemId != null && itemId.startsWith("gear:")) {
                buyGear(player, c, world, itemId, units);
                return;
            }
            if (itemId != null && itemId.startsWith("book:")) {
                buyBook(player, c, world, itemId, units);
                return;
            }
            if (itemId != null && itemId.startsWith("potion:")) {
                buyPotion(player, c, world, itemId, units);
                return;
            }
            OfferSpec spec = null;
            for (OfferSpec s : offersFor(c)) {
                if ("minecraft:emerald".equals(s.giveItem()) && s.getItem().equals(itemId)) {
                    spec = s;
                    break;
                }
            }
            Tradepost.LOGGER.info("[tradepost] 面板买入 id={} 份数={} 找到了报价={}", itemId, units, spec != null);
            if (spec == null) {
                say(player, "本村不卖这个");
                openBoard(player);
                return;
            }
            double base = basePrice(itemId);
            double cap = capOf(itemId, base, c.station);
            int stock = (int) Math.floor(c.store.get(c.vkey, itemId, cap, c.day));
            int per = Math.max(1, spec.getCount());       // 每份件数（= 面板那一行的件数）
            int q = offerQuality(c, itemId);
            int price = Math.max(1, offerPrice(spec.giveCount(), q));    // 每份绿宝石（含品质溢价）
            int want = Math.max(1, Math.min(units, 64));  // units 是"份数"，一次最多 64 份，防止手滑
            int lots = Math.min(want, stock / per);
            if (lots <= 0) {
                say(player, "本村这货卖完了，等它慢慢补货");
                openBoard(player);
                return;
            }
            class_1263 inv = player.method_31548();
            int haveEmerald = countOf(inv, "minecraft:emerald") + countSlots(player, "minecraft:emerald");
            String uuid = player.method_5667().toString();
            double credit = c.store.credit(uuid);
            int afford = (int) Math.floor((haveEmerald + credit) / price);
            lots = Math.min(lots, afford);
            if (lots <= 0) {
                say(player, "绿宝石不够：一份 " + per + " 件要 " + price + " 个，你有 " + haveEmerald + " 个");
                openBoard(player);
                return;
            }
            int count = lots * per;
            int pay = lots * price;
            Tradepost.LOGGER.info("[tradepost] 面板买入成交 id={} 每份={}件 单价={} 份数={} 共付={} 天平里={} 背包里={}",
                    itemId, per, price, lots, pay, countSlots(player, "minecraft:emerald"),
                    countOf(player.method_31548(), "minecraft:emerald"));
            takeAny(player, "minecraft:emerald", pay);
            int dropped = giveOrDrop(player, q > 0 ? withQuality(itemId, count, q) : stack(itemId, count));  // 背包满就掉脚边
            c.store.add(c.vkey, c.areaKey(), itemId, -count, cap, c.day);
            c.store.walletAdd(c.vkey, pay, c.walletCap(), c.day);    // 你付的钱进了这个村的银库
            awardXp(c, base, pay, false, player);
            say(player, join("买到 " + count + "x ", itemId,
                    dev.guoke.tradepost.market.Quality.suffix(q) + " ← " + pay + " 绿宝石（" + lots + " 份 × "
                    + price + "，每份 " + per + " 件）"
                    + (dropped > 0 ? " · 背包满了，" + dropped + " 个掉在脚边" : "")));
            openBoard(player);
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] GUI 买入失败", e);
        }
    }

    /**
     * 集市面板一笔成交：原版 {@code class_1727#method_7667} 已经把天平上的东西扣掉、把货交给玩家，
     * 这里只负责记账（库存、银库、经验，卖货时还有全世界的货量）。
     *
     * <p>单价/份数都用面板建报价时的那一份（MarketMerchant 传进来的 unit/units），
     * 所以账目跟我们摆在天平上的钱完全一致，不会出现"显示一个价、实收另一个价"。
     *
     * @param tab 0=买 1=卖
     * @param row 面板那一行的 json
     * @param per 一份的件数（买=每份件数，卖=一份件数）
     * @param unit 实际单价（绿宝石）
     * @param units 这一笔成交了几份
     */
    /** 最近一笔成交（给 /tradepost last 查价用） */
    private static String LAST_TRADE_ID = "";
    private static int LAST_TRADE_TAB = -1;
    private static int LAST_TRADE_PER = 0;
    private static int LAST_TRADE_UNIT = 0;

    public static void marketTradeDone(class_3222 player, int tab, com.google.gson.JsonObject row,
                                       int per, int unit, int units) {
        try {
            if (row == null) return;
            int n = Math.max(1, units);
            int p = Math.max(1, per);
            int price = Math.max(1, unit);
            String code = jsonStr(row, "id", "");
            if (code.isEmpty()) return;
            LAST_TRADE_ID = code;
            LAST_TRADE_TAB = tab;
            LAST_TRADE_PER = p;
            LAST_TRADE_UNIT = price;
            if (!(player.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                Tradepost.LOGGER.warn("[tradepost] 集市成交时玩家已经不在村里（不记账）：{}", code);
                return;
            }
            Ctx c = ctx(world, x, y, z);
            if (tab == 0) {
                String base = jsonStr(row, "base", code);
                if (base == null || base.isEmpty()) base = code;
                int count = p * n;
                int pay = price * n;
                if (code.startsWith("gear:") || code.startsWith("book:") || code.startsWith("potion:")) {
                    c.store.gearTake(c.vkey, c.day, code, n);        // 装备/书/药按份扣当日额度
                } else {
                    double cap = capOf(base, basePrice(base), c.station);
                    c.store.add(c.vkey, c.areaKey(), base, -count, cap, c.day);
                }
                c.store.walletAdd(c.vkey, pay, c.walletCap(), c.day); // 你付的钱进了这个村的银库
                double xpBase = code.startsWith("book:") ? 12.0
                        : code.startsWith("potion:") ? 8.0 : basePrice(base);
                awardXp(c, xpBase, pay, false, player);
                Tradepost.LOGGER.info("[tradepost] 集市成交 买 {} 份={} 每份={}件 单价={} 共付={}",
                        code, n, p, price, pay);
                say(player, "〔集市〕买到 " + count + "x " + shortId(base) + " ← " + pay + " 绿宝石（"
                        + n + " 份 × " + price + "）");
                try { dev.guoke.tradepost.api.TradeApi.fireTrade(player, base, false, count, pay); }
                catch (Throwable ignored) { }
            } else {
                int sold = p * n;
                int pay = price * n;
                boolean gear = code.startsWith("gear:");
                // 附魔书的行号是 book:附魔|等级，库存记在"附魔书"这个物品上
                String stockId = code.startsWith("book:") ? "minecraft:enchanted_book" : code;
                double base = gear ? 40.0 : basePrice(stockId);
                if (gear) {
                    c.store.gearTake(c.vkey, c.day, GEAR_KEY, sold);       // 装备按件扣当日额度
                } else {
                    double cap = capOf(stockId, base, c.station);
                    c.store.add(c.vkey, c.areaKey(), stockId, sold, cap, c.day);
                    worldSpread(c.store, c.day, stockId, sold);
                }
                // 银库掏不出的钱不许凭空印：成交前 MerchantResultSlotMixin 已经用 canPay() 拦过，
                // 能走到这里还吃紧就说明另有漏口（比如别的成交路径绕过了那道检查），必须响一声。
                double purse = c.store.wallet(c.vkey, c.walletCap(), c.day);
                if (purse + 1e-6 < pay) {
                    Tradepost.LOGGER.warn("[tradepost] 卖页银库不够：本村只剩 {}，这一笔要付 {} —— 差额是超发的钱",
                            (int) purse, pay);
                }
                c.store.walletAdd(c.vkey, -pay, c.walletCap(), c.day);
                awardXp(c, base, pay, true, player);
                // 货款就是结果格里那一摞（数量 = 货款），点位格时原版已经把整摞塞进背包，
                // 这里绝不能再补发任何东西 —— 早先按"最多 64 颗"另算，多给了整整一倍。
                MarketMerchant.collect(player);
                Tradepost.LOGGER.info("[tradepost] 卖页货款 {}（一摞 {} 颗，已由结果格给出）", pay, Math.min(9999, pay));
                Tradepost.LOGGER.info("[tradepost] 集市成交 卖 {} 份={} 每份={}件 单价={} 共得={}",
                        code, n, p, price, pay);
                say(player, "〔集市〕卖出 " + sold + "x " + shortId(code) + " → " + pay + " 绿宝石（"
                        + n + " 份 × " + price + "）");
                try { dev.guoke.tradepost.api.TradeApi.fireTrade(player, stockId, true, sold, pay); }
                catch (Throwable ignored) { }
            }
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 集市成交记账失败", t);
        }
    }

    private static String jsonStr(com.google.gson.JsonObject o, String key, String fallback) {
        try {
            return o.get(key).getAsString();
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ─────────────────── 对外行情 / 成交（TradeApi、命令、别的模组都走这里） ───────────────────
    // 规矩：算式只许在这里写一遍。外部拿到的价必须跟集市界面上那一行一模一样 ——
    // 两处各算各的，迟早差一颗绿宝石，那就是刷钱的口子。

    /**
     * 一行行情。
     *
     * @param lot   一手几件（集市里"份数"的粒度：卖侧按价格档位算，买侧按每份件数）
     * @param price 一手几颗绿宝石
     * @param packs 此刻最多能成交几手（卖侧=银库与库存空间夹出来的，买侧=货架剩的）
     * @param why   不能成交的原因；null 表示能成交
     */
    public record Quote(String itemId, boolean sell, int lot, int price, int packs,
                        int wallet, int purse, String why) {}

    /** 玩家卖货给本村（白板价）：这件货此刻收不收、一手几件几颗、能收几手。 */
    public static Quote quoteSell(class_3222 player, String itemId) {
        try {
            if (player == null || itemId == null || itemId.isEmpty()) return null;
            if (!(player.method_5770() instanceof class_3218 world)) return null;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) return null;
            Ctx c = ctx(world, x, y, z);
            int wallet = (int) Math.floor(c.store.wallet(c.vkey, c.walletCap(), c.day));
            int purse = (int) Math.floor(dev.guoke.tradepost.market.Pay.purse(player));
            double unit = sellUnit(c, x, z, itemId, 0);          // 白板价；带品质的用 quoteSell(stack)
            if (unit <= 0) return new Quote(itemId, true, 1, 0, 0, wallet, purse, "本村不收这件货");
            int lot = dev.guoke.tradepost.market.Lot.of(unit);
            int price = dev.guoke.tradepost.market.Lot.price(unit, lot);
            double cap = capOf(itemId, basePrice(itemId), c.station);
            double have = c.store.get(c.vkey, itemId, cap, c.day);
            int roomPacks = (int) Math.floor(Math.max(0, cap - have) / lot);
            int walletPacks = price <= 0 ? 0 : wallet / price;
            int packs = Math.max(0, Math.min(roomPacks, walletPacks));
            String why = packs > 0 ? null
                    : walletPacks <= 0 ? "本村银库只剩 " + wallet + "，一手要 " + price
                    : "本村这件货的库存满了";
            return new Quote(itemId, true, lot, price, packs, wallet, purse, why);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 报价失败（卖）{}", itemId, t);
            return null;
        }
    }

    /** 同上，但按整摞货算：装备回收要看耐久和附魔，只有栈能给得出来。 */
    public static Quote quoteSell(class_3222 player, class_1799 stack) {
        try {
            if (stack == null || stack.method_7960()) return null;
            String id = itemId(stack);
            if (id == null || id.isEmpty()) return null;
            if (!gearItem(id)) return quoteSell(player, id);
            if (!(player.method_5770() instanceof class_3218 world)) return null;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) return null;
            Ctx c = ctx(world, x, y, z);
            int wallet = (int) Math.floor(c.store.wallet(c.vkey, c.walletCap(), c.day));
            int purse = (int) Math.floor(dev.guoke.tradepost.market.Pay.purse(player));
            int worth = gearPrice(stack, id);
            int left = GEAR_PER_DAY - c.store.gearSold(c.vkey, c.day, GEAR_KEY);
            int packs = worth <= 0 ? 0 : Math.max(0, Math.min(Math.min(left, stack.method_7947()), wallet / worth));
            String why = worth <= 0 ? "这件货回收价是 0"
                    : left <= 0 ? "今天的回收额度用完了（每村每天 " + GEAR_PER_DAY + " 件）"
                    : wallet < worth ? "本村银库只剩 " + wallet + "，这件要 " + worth
                    : null;
            return new Quote("gear:" + gearSig(stack, id), true, 1, worth, packs, wallet, purse, why);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 报价失败（装备）", t);
            return null;
        }
    }

    /** 玩家从本村买货（白板货架）：这件货此刻卖不卖、每份几件几颗、还有几份。 */
    public static Quote quoteBuy(class_3222 player, String itemId) {
        try {
            if (player == null || itemId == null || itemId.isEmpty()) return null;
            if (!(player.method_5770() instanceof class_3218 world)) return null;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) return null;
            Ctx c = ctx(world, x, y, z);
            int wallet = (int) Math.floor(c.store.wallet(c.vkey, c.walletCap(), c.day));
            int purse = (int) Math.floor(dev.guoke.tradepost.market.Pay.purse(player));
            if (itemId.startsWith("gear:") || itemId.startsWith("book:") || itemId.startsWith("potion:")) {
                return new Quote(itemId, false, 1, 0, 0, wallet, purse,
                        "带附魔/效果的货要连数据一起看，请用 TradepostApi.openBoard 或 boardJson");
            }
            for (OfferSpec s : offersFor(c)) {
                if (s.getItem() == null || !s.getItem().equals(itemId)) continue;
                if (s.giveItem() == null || !s.giveItem().equals("minecraft:emerald")) continue;
                int per = Math.max(1, s.getCount());
                int price = offerPrice(s.giveCount(), offerQuality(c, s.getItem()));
                double cap = capOf(s.getItem(), basePrice(s.getItem()), c.station);
                int stock = (int) Math.floor(c.store.get(c.vkey, s.getItem(), cap, c.day));
                int packs = Math.min(stock / per, price <= 0 ? 0 : purse / price);
                String why = price <= 0 ? "本村这件货没有开价"
                        : stock < per ? "今天卖完了（货架还有 " + Math.max(0, stock) + " 件）"
                        : purse < price ? "你只有 " + purse + " 颗绿宝石，一份要 " + price
                        : null;
                return new Quote(itemId, false, per, price, Math.max(0, packs), wallet, purse, why);
            }
            return new Quote(itemId, false, 1, 0, 0, wallet, purse, "本村不卖这件货");
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 报价失败（买）{}", itemId, t);
            return null;
        }
    }

    /**
     * 直接成交一笔（不经过集市界面）。成功返回 null，失败返回人话原因。
     * 价格、记账、经验、全世界货量全部跟集市界面同一套 —— 这是给别处（命令、别的模组、脚本）用的入口。
     *
     * @param sell true = 玩家卖货给村里（村里掏银库），false = 玩家买
     * @param count 件数，会按一手的件数向下取整
     */
    public static String trade(class_3222 player, String itemId, int count, boolean sell) {
        try {
            if (count <= 0) return "件数要大于 0";
            Quote q = sell ? quoteSell(player, itemId) : quoteBuy(player, itemId);
            if (q == null) return "不在村子里";
            if (q.why() != null) return q.why();
            int packs = Math.min(count / Math.max(1, q.lot()), q.packs());
            if (packs <= 0) return "一次至少要 " + q.lot() + " 件";
            int n = packs * q.lot();
            int total = packs * q.price();
            if (!(player.method_5770() instanceof class_3218 world)) return "不在村子里";
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            Ctx c = ctx(world, x, y, z);
            double cap = capOf(itemId, basePrice(itemId), c.station);
            if (sell) {
                int have = countOf(player.method_31548(), itemId);
                if (have < n) return "背包里只有 " + have + " 件";
                if (total > q.wallet()) return "本村银库只剩 " + q.wallet() + "，这一笔要 " + total;
                removeItems(player, itemId, n);
                for (int left = total; left > 0; ) {
                    int k = Math.min(64, left);
                    giveOrDrop(player, new class_1799(dev.guoke.tradepost.market.Pay.emeraldItem(), k));
                    left -= k;
                }
                c.store.add(c.vkey, c.areaKey(), itemId, n, cap, c.day);
                worldSpread(c.store, c.day, itemId, n);
                c.store.walletAdd(c.vkey, -total, c.walletCap(), c.day);
                awardXp(c, basePrice(itemId), total, true, player);
            } else {
                if (total > q.purse()) return "你只有 " + q.purse() + " 颗绿宝石，这一笔要 " + total;
                int got = dev.guoke.tradepost.market.Pay.withdraw(player, total);
                if (got < total) {                                   // 钱包查过了还凑不齐，退回去别吞钱
                    if (got > 0) giveOrDrop(player, new class_1799(dev.guoke.tradepost.market.Pay.emeraldItem(), got));
                    return "钱没凑够";
                }
                giveOrDrop(player, stack(itemId, n));
                c.store.add(c.vkey, c.areaKey(), itemId, -n, cap, c.day);
                c.store.walletAdd(c.vkey, total, c.walletCap(), c.day);
                awardXp(c, basePrice(itemId), total, false, player);
            }
            Tradepost.LOGGER.info("[tradepost] 直接成交 {} {} ×{} 手（{} 绿宝石/手，共 {}）",
                    sell ? "卖" : "买", itemId, packs, q.price(), total);
            dev.guoke.tradepost.api.TradeApi.fireTrade(player, itemId, sell, n, total);
            return null;
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 直接成交失败 {}", itemId, t);
            return "内部错误：" + t;
        }
    }

    /** 从背包扣掉若干件（不含光标）。 */
    private static void removeItems(class_1657 player, String itemId, int count) {
        var inv = player.method_31548();
        int left = count;
        for (int i = 0; i < inv.method_5439() && left > 0; i++) {
            class_1799 st = inv.method_5438(i);
            if (st.method_7960() || !itemId.equals(itemId(st))) continue;
            int take = Math.min(left, st.method_7947());
            st.method_7934(take);                                    // decrement
            left -= take;
        }
    }

    /**
     * 启动时把「基础价 ≥100」的货打一条日志：哪件货定价离谱，看这一行就知道是手写价、标签价、
     * 配方价还是兜底价，以及稀有度有没有再乘一遍。
     */
    private static void dumpHotBasePrices() {
        try {
            java.util.List<String> hot = new java.util.ArrayList<>();
            for (net.minecraft.class_1792 it : net.minecraft.class_7923.field_41178) {
                class_2960 rl = net.minecraft.class_7923.field_41178.method_10221(it);
                if (rl == null) continue;
                String id = rl.toString();
                double b = basePrice(id);
                if (b < 100) continue;
                String src = Tradepost.valueOf(id) > 0 ? "手写"
                        : CATALOG.derived().containsKey(id) ? "标签"
                        : CATALOG.recipePrices().containsKey(id) ? "配方" : "兜底";
                hot.add(String.format(java.util.Locale.ROOT, "%s=%.1f[%s]", id, b, src));
            }
            Tradepost.LOGGER.info("[tradepost] 基础价 ≥100 的货 {} 件：{}", hot.size(), hot);
            explainPrice("tide:enchanted_golden_apple_bobber");
            explainPrice("tide:golden_apple_bobber");
            explainPrice("things:hardening_catalyst");
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 打高价清单失败", t);
        }
    }

    /** 临时排查：把某件货命中的配方逐格摊开，看成本怎么堆上去的 */
    private static void explainPrice(String target) {
        try {
            java.util.List<dev.guoke.tradepost.market.RecipePricer.Rec> recs = RECIPES;
            if (recs == null) return;
            StringBuilder sb = new StringBuilder();
            for (var rec : recs) {
                if (!target.equals(rec.output())) continue;
                sb.append(" | 配方 件数=").append(rec.count()).append(" 格数=").append(rec.slots().size());
                double sum = 0;
                int gi = 0;
                for (var slot : rec.slots()) {
                    java.util.List<String> sorted = new java.util.ArrayList<>(slot);
                    sorted.sort(java.util.Comparator.comparingDouble(CATALOG::seedValue));
                    sb.append(" [格").append(gi++).append(" ").append(slot.size()).append("选: ");
                    for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                        sb.append(sorted.get(i)).append("=")
                          .append(String.format(java.util.Locale.ROOT, "%.3f", CATALOG.seedValue(sorted.get(i)))).append(" ");
                    }
                    sb.append("]");
                    if (!sorted.isEmpty()) sum += CATALOG.seedValue(sorted.get(0));
                }
                sb.append(" 合计=").append(String.format(java.util.Locale.ROOT, "%.2f", sum));
            }
            Tradepost.LOGGER.info("[tradepost] 溯源 {} 菜?={} seed={} value={} 配方价={}{}",
                    target, isDish(target),
                    String.format(java.util.Locale.ROOT, "%.2f", CATALOG.seedValue(target)),
                    String.format(java.util.Locale.ROOT, "%.2f", CATALOG.value(target)),
                    CATALOG.recipePrices().get(target), sb);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 溯源失败 " + target, t);
        }
    }

    /** 军械铺才摆成品装备：供应里有工具/武器/护甲/盾/弹药任意一项 */
    private static boolean sellsGear(Ctx c) {
        if (c.ind == null) return false;
        for (String s : c.ind.supply()) {
            if (s == null) continue;
            if (s.equals("@tool") || s.equals("@weapon") || s.equals("@armor")
                    || s.equals("@shield") || s.equals("@ammo")) return true;
        }
        return false;
    }

    /** 买装备：按购买码认出今天那件货，扣绿宝石，发一把同样附魔的 */
    private static void buyGear(class_3222 player, Ctx c, class_3218 world, String code, int units) {
        int lv = Math.max(1, c.store.level(c.vkey));
        dev.guoke.tradepost.market.GearShop.Gear g =
                dev.guoke.tradepost.market.GearShop.find(c.vkey, lv, c.day, code);
        if (g == null) {
            say(player, "这件货今天没摆在架子上（每周换一批，等级也不一样）");
            openBoard(player);
            return;
        }
        int left = Math.max(0, g.quota() - c.store.gearSold(c.vkey, c.day, code));
        int n = Math.min(Math.max(1, Math.min(units, 64)), left);
        if (n <= 0) {
            say(player, "今天这件已经卖完了（额度 " + g.quota() + " 件），明天再来");
            openBoard(player);
            return;
        }
        class_1263 inv = player.method_31548();
        int price = g.price();
        int haveEmerald = countOf(inv, "minecraft:emerald") + countSlots(player, "minecraft:emerald");
        double credit = c.store.credit(player.method_5667().toString());
        int afford = (int) Math.floor((haveEmerald + credit) / price);
        n = Math.min(n, afford);
        if (n <= 0) {
            say(player, "绿宝石不够：一件 " + price + " 个，你有 " + haveEmerald + " 个");
            openBoard(player);
            return;
        }
        int pay = n * price;
        takeAny(player, "minecraft:emerald", pay);
        class_1799 st = dev.guoke.tradepost.market.GearStack.of(
                g.base(), g.ench(), g.enchLevel(), n, world.method_30349());
        int dropped = giveOrDrop(player, st);
        c.store.gearTake(c.vkey, c.day, code, n);
        c.store.walletAdd(c.vkey, pay, c.walletCap(), c.day);
        awardXp(c, basePrice(g.base()), pay, false, player);
        String en = g.ench().isEmpty() || g.enchLevel() <= 0
                ? "" : "（" + shortId(g.ench()) + " " + g.enchLevel() + " 级）";
        say(player, join("买到 " + n + "x ", g.base(), en
                + " ← " + pay + " 绿宝石（" + n + " × " + price + "）"
                + (dropped > 0 ? " · 背包满了，" + dropped + " 把掉在脚边" : "")));
        openBoard(player);
    }

    /** 这个产业的供应表里有没有这一类货 */
    private static boolean sells(Ctx c, String cat) {
        if (c.ind == null || cat == null) return false;
        for (String s : c.ind.supply()) {
            if (cat.equals(s)) return true;
        }
        return false;
    }

    /** 买附魔书：按码认出今天这本，扣绿宝石，发一本带 stored_enchantments 的真书 */
    private static void buyBook(class_3222 player, Ctx c, class_3218 world, String code, int units) {
        int lv = Math.max(1, c.store.level(c.vkey));
        dev.guoke.tradepost.market.BookShop.Book b =
                dev.guoke.tradepost.market.BookShop.find(c.vkey, lv, c.day, code);
        if (b == null) {
            say(player, "这本书今天没摆在架子上（每周换一批，等级也不一样）");
            openBoard(player);
            return;
        }
        int left = Math.max(0, b.quota() - c.store.gearSold(c.vkey, c.day, code));
        int n = Math.min(Math.max(1, Math.min(units, 64)), left);
        if (n <= 0) {
            say(player, "今天这本已经卖完了，明天再来");
            openBoard(player);
            return;
        }
        class_1263 inv = player.method_31548();
        int have = countOf(inv, "minecraft:emerald") + countSlots(player, "minecraft:emerald");
        double credit = c.store.credit(player.method_5667().toString());
        n = Math.min(n, (int) Math.floor((have + credit) / b.price()));
        if (n <= 0) {
            say(player, "绿宝石不够：一本 " + b.price() + " 个，你有 " + have + " 个");
            openBoard(player);
            return;
        }
        int pay = n * b.price();
        takeAny(player, "minecraft:emerald", pay);
        class_1799 st = dev.guoke.tradepost.market.Goods.of("minecraft:enchanted_book", n,
                b.ench(), b.level(), "", 0, 0, "", world.method_30349());
        int dropped = giveOrDrop(player, st);
        c.store.gearTake(c.vkey, c.day, code, n);
        c.store.walletAdd(c.vkey, pay, c.walletCap(), c.day);
        awardXp(c, 12.0, pay, false, player);
        say(player, join("买到 " + n + "x ", "minecraft:enchanted_book",
                "（" + shortId(b.ench()) + " " + b.level() + " 级） ← " + pay + " 绿宝石"
                + (dropped > 0 ? " · 背包满了，" + dropped + " 本掉在脚边" : "")));
        openBoard(player);
    }

    /** 买药水：按码认出今天这瓶，扣绿宝石，发一瓶带 potion_contents 的真药水 */
    private static void buyPotion(class_3222 player, Ctx c, class_3218 world, String code, int units) {
        int lv = Math.max(1, c.store.level(c.vkey));
        dev.guoke.tradepost.market.PotionShop.Potion p =
                dev.guoke.tradepost.market.PotionShop.find(c.vkey, lv, c.day, code);
        if (p == null) {
            say(player, "这瓶药今天没摆在架子上（每周换一批，等级也不一样）");
            openBoard(player);
            return;
        }
        int left = Math.max(0, p.quota() - c.store.gearSold(c.vkey, c.day, code));
        int n = Math.min(Math.max(1, Math.min(units, 64)), left);
        if (n <= 0) {
            say(player, "今天这种药已经卖完了，明天再来");
            openBoard(player);
            return;
        }
        class_1263 inv = player.method_31548();
        int have = countOf(inv, "minecraft:emerald") + countSlots(player, "minecraft:emerald");
        double credit = c.store.credit(player.method_5667().toString());
        n = Math.min(n, (int) Math.floor((have + credit) / p.price()));
        if (n <= 0) {
            say(player, "绿宝石不够：一份 " + p.price() + " 个，你有 " + have + " 个");
            openBoard(player);
            return;
        }
        int pay = n * p.price();
        takeAny(player, "minecraft:emerald", pay);
        class_1799 st = dev.guoke.tradepost.market.Goods.of(p.item(), n * p.count(),
                "", 0, p.effect(), 0, 0, "", world.method_30349());
        int dropped = giveOrDrop(player, st);
        c.store.gearTake(c.vkey, c.day, code, n);
        c.store.walletAdd(c.vkey, pay, c.walletCap(), c.day);
        awardXp(c, 8.0, pay, false, player);
        say(player, join("买到 " + (n * p.count()) + "x ", p.item(),
                " ← " + pay + " 绿宝石（" + n + " 份 × " + p.price() + "）"
                + (dropped > 0 ? " · 背包满了，" + dropped + " 个掉在脚边" : "")));
        openBoard(player);
    }

    private static void addCost(com.google.gson.JsonArray costs, String itemId, int count) {
        if (itemId == null || count <= 0) return;
        com.google.gson.JsonObject cp = new com.google.gson.JsonObject();
        cp.addProperty("id", itemId);
        String ck = nameKey(itemId);
        if (ck != null) cp.addProperty("key", ck);
        cp.addProperty("count", count);
        costs.add(cp);
    }

    /** GUI 点行（秘宝页）：以物易物换秘宝 */
    public static void tradeRelic(class_3222 player, String resultItem) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            if (villageKey(world, x, y, z) == null) {
                say(player, "这里不是村庄 —— 集市只在村庄范围内有用");
                return;
            }
            Ctx c = ctx(world, x, y, z);
            OfferSpec spec = null;
            for (OfferSpec s : offersFor(c)) {
                if (s.hasSecond() && s.getItem().equals(resultItem)) {
                    spec = s;
                    break;
                }
            }
            if (spec == null) {
                say(player, "本村现在没有这件秘宝（每 7 天换一批）");
                openBoard(player);
                return;
            }
            class_1263 inv = player.method_31548();
            if (countOf(inv, spec.giveItem()) + countSlots(player, spec.giveItem()) < spec.giveCount()
                    || countOf(inv, spec.secondItem()) + countSlots(player, spec.secondItem()) < spec.secondCount()) {
                say(player, class_2561.method_43470("材料不够：要 " + spec.giveCount() + "x ")
                        .method_10852(nameOf(spec.giveItem()))
                        .method_10852(class_2561.method_43470(" + " + spec.secondCount() + "x "))
                        .method_10852(nameOf(spec.secondItem())));
                openBoard(player);
                return;
            }
            takeAny(player, spec.giveItem(), spec.giveCount());
            takeAny(player, spec.secondItem(), spec.secondCount());
            int dropped = giveOrDrop(player, stack(spec.getItem(), spec.getCount()));
            say(player, join("换到 " + spec.getCount() + "x ", spec.getItem(),
                    dropped > 0 ? " · 背包满了，" + dropped + " 个掉在脚边" : ""));
            openBoard(player);
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] 秘宝交易失败", e);
        }
    }

    /** GUI 点行：卖某种货（count <= 0 = 该种全卖；"*" = 背包里所有种类都卖），然后刷新界面 */
    /** 每村每天能捐多少颗纯净绿宝石 */
    private static final int DONATE_CAP = 12;
    /** 一颗纯净绿宝石换多少村庄经验 */
    private static final double DONATE_XP = 250.0;


    /** 捐赠：只收纯净绿宝石，1 颗 = 250 村庄经验，每村每天上限 12 颗 */
    /** 汇兑：1 纯净 = 90 买 / 70 卖（每村都有，不占库存、不占额度） */
    public static void exchange(class_3222 player, String dir) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            Ctx c = ctx(world, x, y, z);
            final String PURE = "pureemeraldtools:pure_emerald";
            class_1263 inv = player.method_31548();
            if ("1".equals(dir)) {
                int have = countOf(inv, PURE) + countSlots(player, PURE);
                if (have < 1) {
                    say(player, "没有纯净绿宝石可卖");
                    return;
                }
                takeAny(player, PURE, 1);
                giveOrDrop(player, stack("minecraft:emerald", 70));
                say(player, "卖出 1 纯净 → 70 绿宝石");
                openBoard(player);
                return;
            }
            int singles = countOf(inv, "minecraft:emerald") + countSlots(player, "minecraft:emerald");
            int blocks = countOf(inv, "minecraft:emerald_block") + countSlots(player, "minecraft:emerald_block");
            int need = 90;
            int takeSingles = Math.min((singles / 9) * 9, 90);
            need -= takeSingles;
            int takeBlocks = need / 9;
            if (takeSingles + takeBlocks * 9 < 90 || takeBlocks > blocks) {
                say(player, "绿宝石不够：买 1 纯净要 90 颗（可用绿宝石块，1 块=9 颗）");
                return;
            }
            if (takeSingles > 0) takeAny(player, "minecraft:emerald", takeSingles);
            if (takeBlocks > 0) takeAny(player, "minecraft:emerald_block", takeBlocks);
            giveOrDrop(player, stack(PURE, 1));
            say(player, "买入 1 纯净 → 90 绿宝石");
            openBoard(player);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 汇兑失败", t);
        }
    }

    public static void donate(class_3222 player, int n) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            Ctx c = ctx(world, x, y, z);
            final String PURE = "pureemeraldtools:pure_emerald";
            int have = countOf(player.method_31548(), PURE) + countSlots(player, PURE);
            int used = c.store.donated(c.vkey, c.day);
            int left = Math.max(0, DONATE_CAP - used);
            n = Math.max(0, Math.min(Math.min(n, have), left));
            if (have <= 0) {
                say(player, "没有纯净绿宝石：这东西只能挖矿得到");
                return;
            }
            if (left <= 0) {
                say(player, "这个村今天已经捐满了（每天 " + DONATE_CAP + " 颗）");
                return;
            }
            if (n <= 0) return;
            takeAny(player, PURE, n);
            c.store.addDonated(c.vkey, c.day, n);
            c.store.addXp(c.vkey, n * DONATE_XP);
            say(player, "捐出 " + n + " 颗纯净绿宝石，+" + Math.round(n * DONATE_XP) + " 村庄经验");
            openBoard(player);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 捐赠失败", t);
        }
    }

    public static void sellFromGui(class_3222 player, String itemId, int count) {
        sellFromGui(player, itemId, count, -1);
    }

    /** 同上；level 指定只卖哪个品质档（-1 = 不分品质，全部卖） */
    public static void sellFromGui(class_3222 player, String itemId, int count, int level) {
        try {
            if (!(player.method_5770() instanceof class_3218 world)) return;
            int x = (int) Math.floor(player.method_23317());
            int y = (int) Math.floor(player.method_23318());
            int z = (int) Math.floor(player.method_23321());
            Ctx c = ctx(world, x, y, z);
            if ("*".equals(itemId)) {
                // 全部卖出：按"物品 + 品质档"分桶，别把典藏当普通货卖了
                java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
                class_1263 inv = player.method_31548();
                for (int i = 0; i < inv.method_5439(); i++) {
                    class_1799 st = inv.method_5438(i);
                    if (st.method_7960()) continue;
                    String id = itemId(st);
                    if (id == null) continue;
                    int q = brewOf(st);
                    keys.add(q > 0 ? id + "#" + q : id);
                }
                for (String k : keys) {
                    int hash = k.indexOf('#');
                    if (hash <= 0) {
                        sellFromInv(player, c, x, z, k, 0, 0);
                    } else {
                        try {
                            sellFromInv(player, c, x, z, k.substring(0, hash), 0,
                                    Integer.parseInt(k.substring(hash + 1)));
                        } catch (NumberFormatException ignored) {
                            sellFromInv(player, c, x, z, k.substring(0, hash), 0, -1);
                        }
                    }
                }
            } else {
                sellFromInv(player, c, x, z, itemId, Math.max(0, count), level);
            }
            openBoard(player);
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] GUI 卖出失败", e);
        }
    }

    /** 钟 = 村中心：潜行右键钟开价目表 */
    public static boolean isBell(class_1937 world, class_2338 pos) {
        try {
            class_2960 id = class_7923.field_41175.method_10221(world.method_8320(pos).method_26204());
            return id != null && id.toString().equals("minecraft:bell");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void say(class_3222 player, String text) {
        say(player, class_2561.method_43470(text));
    }

    private static void say(class_3222 player, class_2561 text) {
        // 成交回执原来打在动作栏（屏幕上那行"字幕"）上；界面已经把结果显示得很清楚了，
        // 这里只留日志，避免每次买卖都糊玩家一脸。
        try {
            String s = text.getString();
            if (s != null && s.startsWith("〔集市〕")) {
                Tradepost.debug("[tradepost] %s", s);
                return;
            }
        } catch (Throwable ignored) {
        }
        player.method_43502(text, true);        // sendMessageToClient(text, overlay=actionbar)
    }

    /** 拼一段可翻译文本：前缀 + 物品名 + 后缀 */
    private static class_5250 join(String prefix, String itemId, String suffix) {
        return class_2561.method_43470(prefix).method_10852(nameOf(itemId))
                .method_10852(class_2561.method_43470(suffix));
    }

    // —— 命令 ——

    public static int reportHere(class_2168 src) {
        try {
            class_3218 world = (class_3218) src.method_9225();
            class_243 pos = (class_243) src.method_9222();
            int x = (int) Math.floor(pos.field_1352);
            int z = (int) Math.floor(pos.field_1350);
            long seed = world.method_8412();
            long day = world.method_8532() / 24000L;

            int y0 = (int) Math.floor(pos.field_1351);
            Ctx c = ctx(world, x, y0, z);
            StringBuilder sb = new StringBuilder();
            sb.append(VillageMarket.describe(Tradepost.data(), c.seed, c.hx, c.hz, c.day, c.station, c.biome))
              .append(" 群系=").append(c.biome == null ? "?" : c.biome.path()).append("\n");
            sb.append("  村庄锚点 ").append(c.hx).append(",").append(c.hz)
              .append(villageKey(world, x, y0, z) == null ? " · 村外（按 64 格网格算）" : " · 村内")
              .append(" · 村表 ").append(villageStructureCount(world)).append(" 项")
              .append(" · ").append(c.shape()).append("\n");
            List<OfferSpec> specs = offersFor(c);
            for (OfferSpec s : specs) {
                sb.append("  ").append(s).append("  (上限 ").append(s.maxUses()).append(")\n");
            }
            if (specs.isEmpty()) sb.append("  本村没有匹配物品（物品 id 在整合包里不存在？）\n");
            send(src, sb.toString());
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] /tradepost here 失败", e);
            send(src, "出错了，看日志");
            return 0;
        }
    }

    /**
     * 行情预报：把周边 5×5 个村庄格的产业/繁荣度/便宜货列出来（不含当前格）。
     * 不强制加载区块：远处没加载的格子只能给"粗估"（缺生物群系，产业可能不同）。
     */
    /** 只靠种子推演周边村庄起点，不加载也不生成区块。radiusChunks 是半径（区块）。 */
    public static int reportScan(class_2168 src, int radiusChunks) {
        try {
            class_3218 world = (class_3218) src.method_9225();
            class_243 pos = (class_243) src.method_9222();
            int px = (int) Math.floor(pos.field_1352);
            int pz = (int) Math.floor(pos.field_1350);
            long seed = world.method_8412();
            VillageRadar.Result r = VillageRadar.scan(world, px >> 4, pz >> 4, radiusChunks);
            java.util.List<VillageRadar.Hit> hits = new java.util.ArrayList<>(r.hits);
            hits.sort((a, b) -> Integer.compare(dist2(a, px, pz), dist2(b, px, pz)));

            StringBuilder sb = new StringBuilder();
            sb.append("种子推演：半径 ").append(radiusChunks).append(" 区块（约 ").append(radiusChunks * 16)
              .append(" 格）内命中 ").append(hits.size()).append(" 个村庄起点（村庄结构集 ")
              .append(r.villageSets).append(" 个，可推算 ").append(r.spreadSets)
              .append(" 个，跳过 ").append(r.skippedSets).append(" 个）\n");
            int shown = 0;
            int filtered = 0;
            for (VillageRadar.Hit h : hits) {
                if (shown >= 24) {
                    sb.append("  …还有 ").append(hits.size() - shown).append(" 个未显示\n");
                    break;
                }
                boolean loaded = world.method_8393(h.chunkX, h.chunkZ);
                if (loaded && !h.structures.isEmpty()) {
                    var biomeEntry = world.method_23753(new class_2338(h.blockX, 64, h.blockZ));
                    boolean ok = false;
                    if (biomeEntry != null) {
                        for (net.minecraft.class_3195 st : h.structures) {
                            if (st.method_41607().method_40241(biomeEntry)) {
                                ok = true;
                                break;
                            }
                        }
                    } else {
                        ok = true;
                    }
                    if (!ok) {
                        filtered++;
                        continue;
                    }
                }
                var biome = loaded ? biomeInfo(world, h.blockX, 64, h.blockZ) : null;
                var ind = VillageMarket.industry(Tradepost.data(), seed, h.blockX, h.blockZ, biome);
                int t = VillageMarket.prosperity(seed, h.blockX, h.blockZ);
                int dx = h.blockX - px, dz = h.blockZ - pz;
                sb.append("  · (").append(h.blockX).append(", ").append(h.blockZ).append(") ")
                  .append((int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz))).append(" 格 ")
                  .append(dir(dx, dz)).append(' ');
                if (ind != null) {
                    sb.append(ind.display()).append(" T").append(t);
                }
                if (!loaded) {
                    sb.append(" (未加载，群系未取)");
                }
                sb.append("  [").append(h.setId).append(" 间距 ").append(h.spacing).append(" 区块]\n");
                shown++;
            }
            if (filtered > 0) {
                sb.append("  （另有 ").append(filtered).append(" 个候选因群系不合法被筛掉）\n");
            }
            if (hits.isEmpty()) {
                sb.append("  （没命中：可能这一片确实没村，或该结构的放置不是随机散布型）\n");
            }
            say(src.method_44023(), sb.toString());
            return hits.size();
        } catch (Throwable e) {
            Tradepost.LOGGER.warn("[tradepost] scan 失败", e);
            return 0;
        }
    }

    /** 诊断：这一格到底被算成哪个市场（结构命中 / 并成了哪几座 / 锚点 / 产业） */
    public static int reportWhere(class_2168 src) {
        try {
            class_3218 world = (class_3218) src.method_9225();
            class_243 pos = (class_243) src.method_9222();
            int x = (int) Math.floor(pos.field_1352);
            int y = (int) Math.floor(pos.field_1351);
            int z = (int) Math.floor(pos.field_1350);
            long seed = world.method_8412();
            dev.guoke.tradepost.market.MarketData data = Tradepost.data();
            dev.guoke.tradepost.market.BiomeInfo biome = biomeInfo(world, x, y, z);
            StringBuilder sb = new StringBuilder();
            sb.append("位置 ").append(x).append(", ").append(z)
              .append(" · 区块 ").append(x >> 4).append(", ").append(z >> 4)
              .append(" · 网格格 ").append(VillageMarket.cellOf(x)).append(", ").append(VillageMarket.cellOf(z)).append('\n');
            class_3449 vs = villageStart(world, x, y, z);
            if (vs == null) {
                sb.append("  命中结构：无 → 只好按 64 格网格算价\n");
            } else {
                class_1923 cp = vs.method_34000();
                sb.append("  命中结构：").append(keyOf(vs)).append(" · 起点区块 ").append(cp.field_9181).append(", ").append(cp.field_9180).append('\n');
            }
            Town town = townOf(world, vs);
            if (town != null) {
                class_1923 lp = town.lead.method_34000();
                sb.append("  并成一处：").append(town.key)
                  .append(town.merged ? "（" + town.members.size() + " 座结构）" : "（独座）")
                  .append(" · ").append(town.stat[0]).append(" 户·").append(town.stat[1]).append(" 式")
                  .append(" · 占地 ").append(town.area).append(" 格²")
                  .append(" · 锚点 ").append(lp.field_9181 * 16 + 8).append(",").append(lp.field_9180 * 16 + 8).append('\n');
                for (class_3449 m : town.members) {
                    class_3341 b = m.method_14969();
                    sb.append("    · ").append(keyOf(m)).append("  盒 ")
                      .append(b.method_35415()).append(",").append(b.method_35417()).append(" ~ ")
                      .append(b.method_35418()).append(",").append(b.method_35420())
                      .append(b.method_47593(x, y, z) ? "  ← 包住你" : "").append('\n');
                }
            } else {
                sb.append("  并成一处：无（这座结构算不出地方）\n");
            }
            java.util.LinkedHashMap<String, class_3449> cand = new java.util.LinkedHashMap<>();
            collectNear(world, vs, 6, cand);
            if (cand.isEmpty()) {
                try {
                    for (class_3449 o : villagesNear(world, x >> 4, z >> 4, 6)) {
                        if (o != null) cand.putIfAbsent(keyOf(o), o);
                    }
                } catch (Throwable ignored) {
                    // 检不出来就算了
                }
            }
            sb.append("  ±6 区块内检出结构 ").append(cand.size()).append(" 座\n");
            for (class_3449 o : cand.values()) {
                if (town != null && town.members.contains(o)) continue;
                class_3341 b = o.method_14969();
                sb.append("    · ").append(keyOf(o)).append("  盒 ")
                  .append(b.method_35415()).append(",").append(b.method_35417()).append(" ~ ")
                  .append(b.method_35418()).append(",").append(b.method_35420())
                  .append(b.method_47593(x, y, z) ? "  ← 包住你" : "").append('\n');
            }
            // 产业对照：按锚点算 vs 按你的网格算
            int ax = x, az = z;
            if (town != null) {
                class_1923 lp = town.lead.method_34000();
                ax = lp.field_9181 * 16 + 8;
                az = lp.field_9180 * 16 + 8;
            }
            String anchorInd = "?";
            String gridInd = "?";
            try {
                if (data != null) {
                    var ia = VillageMarket.industry(data, seed, ax, az, biome);
                    if (ia != null) anchorInd = ia.display();
                    var ig = VillageMarket.industry(data, seed, x, z, biome);
                    if (ig != null) gridInd = ig.display();
                }
            } catch (Throwable ignored) {
                // 拿不到就算了
            }
            sb.append("  产业：按锚点 ").append(anchorInd).append(" · 按你脚下网格 ").append(gridInd).append('\n');
            // 产业哈希的第三项输入是群系：把"锚点群系"和"脚下群系"并排打出来，
            // 两边不一样就说明你正站在群系交界处（这正是走一格换产业的老病根）
            try {
                dev.guoke.tradepost.market.BiomeInfo ba = biomeInfo(world, ax, y, az);
                String bn = biome == null ? "?" : biome.path();
                String an = ba == null ? "?" : ba.path();
                sb.append("  群系：锚点 ").append(an).append(" · 脚下 ").append(bn)
                  .append(an.equals(bn) ? "" : "   ← 不一样！").append('\n');
                if (data != null && ba != null) {
                    var ib = VillageMarket.industry(data, seed, ax, az, ba);
                    sb.append("  产业（用锚点群系）：").append(ib == null ? "?" : ib.display()).append('\n');
                }
            } catch (Throwable ignored) {
                // 拿不到群系名就算了
            }
            sb.append("  vkey：").append(vkeyOf(world, vs, x, z)).append('\n');
            send(src, sb.toString());
            return 1;
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] /tradepost where 失败", e);
            send(src, "出错了，看日志");
            return 0;
        }
    }

    /** 诊断：这一片有哪些村庄结构、被并成了几处市场（钟/价格按"处"算） */
    public static int reportVillages(class_2168 src, int radiusChunks) {
        try {
            class_3218 world = (class_3218) src.method_9225();
            class_243 pos = (class_243) src.method_9222();
            int px = (int) Math.floor(pos.field_1352);
            int pz = (int) Math.floor(pos.field_1350);
            java.util.List<class_3449> found = villagesNear(world, px >> 4, pz >> 4, radiusChunks);
            java.util.Map<String, Town> towns = new java.util.LinkedHashMap<>();
            int orphan = 0;
            for (class_3449 vs : found) {
                Town t = townOf(world, vs);
                if (t == null) {
                    orphan++;
                    continue;
                }
                towns.put(t.key, t);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("已加载区块里的村庄结构 ").append(found.size()).append(" 座 → ")
              .append(towns.size()).append(" 处市场（半径 ").append(radiusChunks).append(" 区块）\n");
            int shown = 0;
            for (Town t : towns.values()) {
                if (shown >= 20) {
                    sb.append("  …还有 ").append(towns.size() - shown).append(" 处未显示\n");
                    break;
                }
                shown++;
                class_1923 cp = t.lead.method_34000();
                int bx = cp.field_9181 * 16 + 8, bz = cp.field_9180 * 16 + 8;
                int dx = bx - px, dz = bz - pz;
                int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                int deg = RoadNet.degree(world, t.lead);
                int area = (int) Math.min(Integer.MAX_VALUE, t.area);
                VillageMarket.setTraits(deg, area, t.stat[0], t.stat[1]);
                sb.append("  · ").append(bx).append(", ").append(bz).append("  ").append(dist).append(" 格 ")
                  .append(dir(dx, dz))
                  .append(" · 结构 ").append(t.members.size()).append(" 座")
                  .append(t.merged ? "（并成一处）" : "")
                  .append(" · ").append(t.stat[0]).append(" 户·").append(t.stat[1]).append(" 式")
                  .append(" · 占地 ").append(area).append(" 格²")
                  .append(" · 富庶 ").append(String.format(java.util.Locale.ROOT, "%.2f", VillageMarket.wealth()));
                if (deg > 0) sb.append(" · 商路 ").append(deg).append(" 条");
                sb.append('\n');
            }
            if (orphan > 0) sb.append("  （").append(orphan).append(" 座结构算不出地方，已忽略）\n");
            if (towns.isEmpty()) sb.append("  （这一片已加载区块里没有村庄结构）\n");
            say(src.method_44023(), sb.toString());
            return towns.size();
        } catch (Throwable e) {
            Tradepost.LOGGER.warn("[tradepost] villages 失败", e);
            return 0;
        }
    }

    private static int dist2(VillageRadar.Hit h, int px, int pz) {
        int dx = h.blockX - px;
        int dz = h.blockZ - pz;
        return dx * dx + dz * dz;
    }

    /** 风声：真实村庄位置 + 会漏报，只给方向和距离档，不给坐标、不给产业 */
    public static List<String> rumors(class_3218 world, int px, int pz) {
        try {
            VillageRadar.Result r = VillageRadar.scan(world, px >> 4, pz >> 4, 128);
            List<int[]> cands = new java.util.ArrayList<>();
            java.util.Set<String> dedup = new java.util.HashSet<>();
            for (VillageRadar.Hit h : r.hits) {
                if (!dedup.add(h.blockX + "," + h.blockZ)) continue;
                cands.add(new int[]{h.blockX, h.blockZ});
            }
            int want = dev.guoke.tradepost.market.Economy.current().rumorCount;
            return dev.guoke.tradepost.market.Rumor.rumors(world.method_8412(), px, pz, cands, want);
        } catch (Throwable t) {
            Tradepost.LOGGER.debug("[tradepost] 风声计算失败", t);
            return java.util.Collections.emptyList();
        }
    }

    public static int reportForecast(class_2168 src) {
        try {
            class_3218 world = (class_3218) src.method_9225();
            class_243 pos = (class_243) src.method_9222();
            int x = (int) Math.floor(pos.field_1352);
            int z = (int) Math.floor(pos.field_1350);
            long day = world.method_8532() / 24000L;
            List<String> rs = rumors(world, x, z);
            StringBuilder sb = new StringBuilder();
            sb.append("风声（第 ").append(day).append(" 天）");
            if (rs.isEmpty()) {
                sb.append("：最近没人提起别的村子。");
            } else {
                sb.append("：\n");
                for (String s : rs) sb.append("  · ").append(s).append("\n");
                sb.append("  只是听来的，准不准得自己跑一趟；没被提起的村子照样存在。");
            }
            send(src, sb.toString());
            return rs.size();
        } catch (Exception e) {
            Tradepost.LOGGER.warn("[tradepost] /tradepost forecast 失败", e);
            send(src, "出错了，看日志");
            return 0;
        }
    }

    private static String dir(int dx, int dz) {
        String ns = dz < 0 ? "北" : (dz > 0 ? "南" : "");
        String ew = dx < 0 ? "西" : (dx > 0 ? "东" : "");
        return ns + ew + (ns.isEmpty() && ew.isEmpty() ? "此" : "");
    }

    public static int reportInfo(class_2168 src) {
        MarketData d = Tradepost.data();
        String msg = d == null ? "市场数据未加载" : "市场数据已加载：" + d.summary();
        send(src, msg);
        if (d != null) {
            dev.guoke.tradepost.market.DemandGraph g = dev.guoke.tradepost.market.DemandGraph.of(CATALOG);
            send(src, "用途大类：" + g.buyersConfigured() + " 个产业已配大类，已识别 "
                    + g.knownItems() + " 件带标签物品（认不出的按 id 关键词兜底）");
            dev.guoke.tradepost.market.Economy e = dev.guoke.tradepost.market.Economy.current();
            send(src, String.format("经济系数：买 %.2f / 卖 %.2f / 缺货 %.2f / 大类 %.2f / 自产 %.2f / 区域 %.2f / 集市 %d 天",
                    e.buyBase, e.sellBase, e.demandBonus, e.roleDemand, e.supplyPenalty, e.areaSpread, e.marketDayPeriod));
        }
        return 1;
    }

    /**
     * `/tradepost mix` —— 混币自检。
     *
     * <p>绿宝石和绿宝石块按价值混着付，涉及三处容易算错的地方：一处是"块 = 9 颗"的折算，
     * 一处是"结果格只放得下一摞"时的面额拆分，一处是把两摞成本加起来的报价总价。
     * 这三处都是纯算式，跑一张 ✔/✘ 表比等玩家在柜台前发现少给钱快得多。
     */
    public static int cmdMix(class_2168 src) {
        java.util.List<String> lines = dev.guoke.tradepost.market.MixCheck.run();
        int bad = 0;
        for (String s : lines) {
            if (s.startsWith("\u2718")) bad++;
            send(src, s);
            Tradepost.LOGGER.info("[tradepost] 混币自检 {}", s);
        }
        String tail = bad == 0 ? "混币自检通过，全部 " + lines.size() + " 项" : "混币自检有 " + bad + " 项不过";
        send(src, tail);
        Tradepost.LOGGER.info("[tradepost] {}（失败 {} 项）", tail, bad);
        return bad == 0 ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    private static void send(class_2168 src, String text) {
        Supplier<class_2561> sup = () -> class_2561.method_43470(text);
        src.method_9226(sup, false);
    }
}
