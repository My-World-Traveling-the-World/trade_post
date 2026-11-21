package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 村庄市场：给定 (世界种子, 村庄坐标, 天数, 生物群系) 确定性地算出该村的报价。
 *
 * 不存档的部分：产业、繁荣度、村癖、每日行情 —— 全部哈希推导，同一坐标永远同一结果。
 * 存档的部分：库存（玩家买卖改变了世界），通过 StockView 注入，纯逻辑层不碰存档。
 */
public final class VillageMarket {

    /** 村庄格边长（方块）。同格内视为同一个村庄。 */
    public static final int CELL = 64;

    /** 单村普通交易点最多几种货 */
    public static final int MAX_OFFERS = 7;

    /** 驿站最多几种货 */
    public static final int STATION_OFFERS = 14;

    /** 单笔上限：普通交易点 / 驿站（实际还会被库存压） */
    public static final int USES_VILLAGE = 16;
    public static final int USES_STATION = 64;

    private VillageMarket() {}

    public static int cellOf(int coord) {
        return Math.floorDiv(coord, CELL);
    }

    /**
     * 该格选中的产业。按生物群系权重加权（沙漠村不做林业），
     * 缺省权重 1；biome 为空时退化为等权哈希。
     */
    public static MarketData.Industry industry(MarketData data, long seed, int x, int z, BiomeInfo biome) {
        if (data.industries.isEmpty()) return null;
        int vx = cellOf(x), vz = cellOf(z);
        long h = Hashing.mix(Hashing.of(seed, vx, vz, 1));

        int total = 0;
        for (MarketData.Industry ind : data.industries) total += weightOf(ind, biome);
        if (total <= 0) {
            int idx = (int) Math.floorMod(h, data.industries.size());
            return data.industries.get(idx);
        }
        int pick = (int) Math.floorMod(h, total);
        int acc = 0;
        for (MarketData.Industry ind : data.industries) {
            acc += weightOf(ind, biome);
            if (pick < acc) return ind;
        }
        return data.industries.get(data.industries.size() - 1);
    }

    /**
     * 产业在某生物群系的权重：
     *   "#tag" 键 → 查群系标签（#c:is_mountain）
     *   普通键   → 查群系 path（desert），支持"包含"匹配（snowy_plains 命中 snowy）
     *   "default" → 兜底
     * 全部不命中就是 0（林业不该出现在沙漠）。
     */
    private static int weightOf(MarketData.Industry ind, BiomeInfo biome) {
        Map<String, Integer> w = ind.biomeWeights();
        if (w == null || w.isEmpty()) return 1;
        if (ind.prosperityBias() < 0) return 1;              // 荒僻：保底 1，永远只是候选之一
        if (biome == null) return Math.max(0, w.getOrDefault("default", 1));
        int best = -1;
        for (Map.Entry<String, Integer> e : w.entrySet()) {
            String k = e.getKey();
            int v = Math.max(0, e.getValue());
            if (k.startsWith("#")) {
                if (biome.hasTag(k.substring(1))) best = Math.max(best, v);
            } else if (k.equals("default")) {
                if (best < 0) best = v;
            } else if (k.equals(biome.path()) || (biome.path() != null && biome.path().contains(k))) {
                best = Math.max(best, v);
            }
        }
        return Math.max(0, best);
    }

    public static MarketData.Industry industry(MarketData data, long seed, int x, int z) {
        return industry(data, seed, x, z, null);
    }

    // —— 村庄的"先天条件"（占地、商路）——
    // 这一层是纯逻辑，拿不到世界，由 Bridge 每次进村前 setTraits 塞进来。
    // 用 ThreadLocal：服务端多线程定价时互不串味；没设置过就是 {0,0}，行为跟以前一样。
    // 槽位：0 = 商路条数，1 = 占地格²，2 = 屋子栋数，3 = 屋子样式数
    private static final ThreadLocal<int[]> TRAITS = ThreadLocal.withInitial(() -> new int[]{0, 0, 0, 0});

    /**
     * @param roadDeg 已通车的商路条数（阡陌交通路网）
     * @param area    村庄包围盒占地（格²，0 = 村外/未知）
     * @param houses  数出来的屋子栋数（0 = 拿不到结构片段）
     * @param kinds   屋子样式数（按占地/高度分档）
     */
    /**
     * 当前正在结算的村子等级 —— 库存上限要按它放大（Stock.cap 里读）。
     * 纯逻辑层拿不到世界也拿不到存档，只能由 Bridge / TradeStore 每次进村前塞进来；
     * 同一线程一次只结一个村，所以静态变量够用。
     */
    /** 买页额外加的行数：原来村子里只摆 5 行特产，玩家嫌太少（2026-11-13）。 */
    private static final int SUPPLY_EXTRA = 5;

    /**
     * 不死图腾：钉死的一口价 —— 村子卖给你 {@link #TOTEM_PRICE} 一个，回收价 = 64 × 0.75 = 48。
     * 不钉的话基础价要跟着产业/行情/库存浮，同一件神器两个村差一倍，玩家没法算账。
     */
    private static final String TOTEM = "minecraft:totem_of_undying";
    private static final double TOTEM_PRICE = 64.0;

    private static int LEVEL_HINT = 1;

    public static void setLevelHint(int lv) {
        LEVEL_HINT = Math.max(1, Math.min(VillageLevel.MAX, lv));
    }

    public static int levelHint() {
        return LEVEL_HINT;
    }

    public static void setTraits(int roadDeg, int area, int houses, int kinds) {
        int[] t = TRAITS.get();
        t[0] = Math.max(0, roadDeg);
        t[1] = Math.max(0, area);
        t[2] = Math.max(0, houses);
        t[3] = Math.max(0, kinds);
    }

    /** 当前的商路条数 / 屋子栋数 / 样式数（给 Bridge 显示用） */
    public static int traitRoad() { return TRAITS.get()[0]; }

    public static int traitHouses() { return TRAITS.get()[2]; }

    public static int traitKinds() { return TRAITS.get()[3]; }

    /** 屋子栋数；拿不到结构片段时按占地估（3600 格² = 标准村 wealthHouses 栋） */
    public static double houses() {
        int[] t = TRAITS.get();
        Economy eco = Economy.current();
        int std = Math.max(1, eco.wealthHouses);
        if (t[2] > 0) return t[2];
        if (t[1] > 0) return t[1] * std / 3600.0;
        return std;
    }

    /**
     * 富庶度 w（约 0.3 ~ 2.6）：以屋子栋数线性增长，样式越多越热闹。
     * 这是整套"村子大小 → 价格/库存/银库"的唯一自变量 —— 不再有 T1/T2/T3 台阶。
     */
    public static double wealth() {
        int[] t = TRAITS.get();
        Economy eco = Economy.current();
        double base = houses() / Math.max(1, eco.wealthHouses);
        double variety = t[3] > 0 ? Math.min(1.20, 1.0 + eco.wealthVariety * (t[3] - 5)) : 1.0;
        return Math.max(0.30, Math.min(2.60, base * variety));
    }

    /** 收购价随富庶度线性上升：标准村 1.00，2 倍富庶 1.15，贫村 0.90 */
    public static double sellFactor() {
        return Math.max(0.80, Math.min(1.30, 0.85 + 0.15 * wealth()));
    }

    /** 卖给你的价随富庶度线性下降：大村货多且便宜 */
    public static double buyFactor() {
        return Math.max(0.85, Math.min(1.35, 1.15 - 0.15 * wealth()));
    }

    /** 库存上限随村庄规模变：小村货少，大村堆得多（0.6 ~ 1.6 倍，再夹在原上限里） */
    public static double stockScale() {
        return Math.max(0.50, Math.min(1.60, 0.60 + 0.40 * wealth()));
    }

    /**
     * 商路系数（买卖两侧都乘）：路通得越多，货运进来越顺，本地物价整体下移。
     * 于是玩家在枢纽进货便宜、在没路的偏远村出货贵 —— 商路本身就是一条进货渠道。
     */
    public static double roadFactor() {
        Economy eco = Economy.current();
        double per = Math.max(0.0, eco.roadPriceDrop);
        if (per <= 0) return 1.0;
        int d = Math.min(TRAITS.get()[0], Math.max(0, eco.roadMaxDegree));
        return Math.max(0.60, 1.0 - per * d);
    }

    /**
     * 繁荣度档位：1 = 小村, 2 = 普通, 3 = 大村。
     * 只用来显示（和兜底），价格/库存/银库都直接吃连续的 wealth()，不再有台阶。
     */
    public static int prosperity(long seed, int x, int z) {
        double w = wealth();
        return w >= 1.50 ? 3 : (w >= 0.67 ? 2 : 1);
    }

    /** 村癖：同一物品在同产业的两个村子里有固定的百分比差异（幅度看 Economy.quirkPct）。 */
    public static double quirk(long seed, int x, int z, String itemId) {
        int vx = cellOf(x), vz = cellOf(z);
        return 1.0 + Economy.current().quirkPct * Hashing.signed(seed, vx, vz, itemId.hashCode(), 3);
    }

    /** 每日行情：按天变化的随机波动（幅度看 Economy.dailyPct）。 */
    public static double daily(long seed, int x, int z, long day, String category) {
        int vx = cellOf(x), vz = cellOf(z);
        return 1.0 + Economy.current().dailyPct * Hashing.signed(seed, vx, vz, day, category.hashCode(), 4);
    }

    /**
     * 今天这个村子是不是赶集日。
     * 每 marketDayPeriod 天有一个村子被选中，那天它收购价 ×marketDaySell。
     * 目的：给玩家一个"今天该往哪跑"的理由，而不是永远走同一条环线。
     */
    public static boolean marketDay(long seed, int x, int z, long day) {
        Economy eco = Economy.current();
        long period = Math.max(1, eco.marketDayPeriod);
        return Hashing.below((int) period, seed, cellOf(x), cellOf(z), Math.floorDiv(day, period), 9) == 0;
    }

    /** 集市日系数：sell 侧是加成，buy 侧是轻微上浮 */
    private static double marketDayFactor(long seed, int x, int z, long day, boolean selling) {
        if (!marketDay(seed, x, z, day)) return 1.0;
        Economy eco = Economy.current();
        return selling ? eco.marketDaySell : eco.marketDayBuy;
    }

    /** 富庶度对买入（玩家掏绿宝石）的系数 —— 连续，没有档位台阶 */
    private static double buyProsperity(int t) {
        return buyFactor();
    }

    /** 富庶度对卖出（玩家收绿宝石）的系数 —— 连续，没有档位台阶 */
    private static double sellProsperity(int t) {
        return sellFactor();
    }

    // —— 秘宝 ——

    /** 有秘宝的村庄比例（%） */
    public static final int RELIC_CHANCE = 25;

    /** 秘宝换货周期（游戏日） */
    public static final int RELIC_ROTATION_DAYS = 7;

    /** 秘宝一周能被换走几次 */
    public static final int RELIC_USES = 4;

    /**
     * 本村当前周期的秘宝：只收物不收绿宝石，两个成本槽都用上。
     * 约 1/4 的村有，每 7 天换一次，赌的是"我记得那个村有个好东西"。
     * 成本带 tag 或物品不存在的秘宝直接跳过（不报错、不崩溃）。
     */
    public static OfferSpec relicOffer(MarketData data, long seed, int x, int z, long day, boolean station,
                                      Catalog catalog) {
        if (data.relics.isEmpty()) return null;
        int vx = cellOf(x), vz = cellOf(z);
        if (Hashing.below(100, seed, vx, vz, 5) >= RELIC_CHANCE) return null;
        long cycle = Math.floorDiv(day, RELIC_ROTATION_DAYS);

        int total = 0;
        List<MarketData.Relic> usable = new ArrayList<>();
        for (MarketData.Relic r : data.relics) {
            if (!relicUsable(data, r, catalog)) continue;
            usable.add(r);
            total += Math.max(1, r.weight());
        }
        if (usable.isEmpty() || total <= 0) return null;
        int pick = Hashing.below(total, seed, vx, vz, cycle, 6);
        MarketData.Relic chosen = usable.get(usable.size() - 1);
        int acc = 0;
        for (MarketData.Relic r : usable) {
            acc += Math.max(1, r.weight());
            if (pick < acc) { chosen = r; break; }
        }

        List<MarketData.Cost> costs = chosen.cost();
        String first = resolve(data, catalog, costs.get(0).item(), costs.get(0).tag(), seed, x, z);
        if (first == null) return null;
        int firstCount = countFor(data, catalog, costs.get(0), first);
        if (firstCount <= 0) return null;
        String second = null;
        int secondCount = 0;
        if (costs.size() > 1) {
            String s = resolve(data, catalog, costs.get(1).item(), costs.get(1).tag(), seed, x, z);
            if (s != null) {
                int c = countFor(data, catalog, costs.get(1), s);
                if (c > 0) { second = s; secondCount = c; }
            }
        }
        int give = Math.max(1, chosen.count());
        return new OfferSpec(first, firstCount, chosen.item(), give,
                station ? RELIC_USES * 2 : RELIC_USES, second, secondCount);
    }

    /**
     * 成本件数：写了 count 就用 count；只写 value 的按"在这个包里值多少钱"折算。
     * 这样秘宝表不用跟着整合包改——#c:ingots 挑到银还是钢，件数会自动变。
     */
    private static int countFor(MarketData data, Catalog catalog, MarketData.Cost c, String itemId) {
        if (c.count() > 0) return c.count();
        if (c.value() <= 0) return 0;
        double base = baseOf(data, catalog, itemId);
        if (base <= 0) return 0;
        long n = Math.round(c.value() / base);
        return (int) Math.max(1, Math.min(64, n));
    }

    /** 成本条目 → 具体物品：直接写 id 的用 id，写标签的让目录展开后按村哈希挑一个。 */
    private static String resolve(MarketData data, Catalog catalog, String item, String tag,
                                  long seed, int x, int z) {
        if (item != null && baseOf(data, catalog, item) > 0) return item;
        if (tag == null) return null;
        return pick(data, catalog, ensureTag(tag), seed, x, z);
    }

    private static String ensureTag(String t) {
        return t.startsWith("#") ? t : "#" + t;
    }

    private static boolean relicUsable(MarketData data, MarketData.Relic r, Catalog catalog) {
        if (r.item() == null || baseOf(data, catalog, r.item()) <= 0) return false;
        if (r.cost() == null || r.cost().isEmpty()) return false;
        for (MarketData.Cost c : r.cost()) {
            if (c.count() <= 0 && c.value() <= 0) return false;
            if (c.item() == null && c.tag() == null) return false;
            if (c.item() != null && baseOf(data, catalog, c.item()) <= 0 && c.tag() == null) return false;
        }
        return true;
    }

    /** 秘宝换货周期剩余天数（聊天栏提示用） */
    public static long relicDaysLeft(long day) {
        return RELIC_ROTATION_DAYS - Math.floorMod(day, (long) RELIC_ROTATION_DAYS);
    }

    public static List<OfferSpec> offers(MarketData data, long seed, int x, int z, long day) {
        return offers(data, seed, x, z, day, false);
    }

    public static List<OfferSpec> offers(MarketData data, long seed, int x, int z, long day, boolean station) {
        return offers(data, seed, x, z, day, station, null, null, null);
    }

    /**
     * 生成该村对外的报价单。
     *
     * @param station 村里有驿站（建筑里带 tradepost:post_station 战利品表的箱子）
     * @param stock   村庄库存视图；null 表示忽略库存（调试用）
     * @param biome   该村所在生物群系 id 的 path（如 "desert"），用于产业加权
     */
    public static List<OfferSpec> offers(MarketData data, long seed, int x, int z, long day,
                                         boolean station, StockView stock, BiomeInfo biome) {
        return offers(data, seed, x, z, day, station, stock, biome, null);
    }

    /**
     * @param catalog 物品目录：把 "#c:ingots" 这类标签展开成整合包里真实存在的物品，
     *                并给 values.json 没登记的模组物品兜底价。null 表示只认具体 id。
     */
    public static List<OfferSpec> offers(MarketData data, long seed, int x, int z, long day,
                                         boolean station, StockView stock, BiomeInfo biome, Catalog catalog) {
        List<OfferSpec> out = new ArrayList<>();
        MarketData.Industry ind = industry(data, seed, x, z, biome);
        if (ind == null) return out;
        int t = prosperity(seed, x, z);
        Economy eco = Economy.current();
        int roadDeg = Math.min(TRAITS.get()[0], Math.max(0, eco.roadMaxDegree));
        int cap = (station ? eco.stationOffers : eco.villageOffers)
                + roadDeg * Math.max(0, eco.roadExtraOffers);      // 通商的地方货更杂
        // 村庄等级：柜台长度、每行能成交几次、库存上限都按它伸。
        // 等级从 StockView 现读 —— 升级后下一次重算货架（成交/重开界面）就生效，不用等第二天。
        int lv = stock == null ? 1 : Math.max(1, Math.min(VillageLevel.MAX, stock.level()));
        // 高等级村每个供应条目多挑几种货：升级才有"买行变多"的实感（Lv1 4 种 → Lv5 8 种）。
        // 只加柜台不加货源的话，产业条目少的村（酒坊 2 条）升级了也挤不出新行。
        int perEntry = Math.max(1, eco.itemsPerEntry) + 2 * Math.max(0, lv - 1);
        setLevelHint(lv);
        int extraSupply = VillageLevel.sellSlots(lv) - VillageLevel.sellSlots(1);   // 买页：0..4 行
        int extraMisc = (lv - 1) / 2;                                              // 每两级 +1 格杂货（杂货只是配角，别抢特产）
        int totalCap = cap + SUPPLY_EXTRA + extraSupply + extraMisc;   // 买页多出来的行也要算进总预算，否则菜/杂货被挤掉
        // 货架预算分配：先把菜位与杂货位留出来，收货区固定 2 行（不随等级涨），其余全给本村特产。
        // 玩家卖货那页（卖页）本来就被货量限制住，行数多了只是空刷；买页才是主要去处，给足。
        int cuisineWant = Math.max(0, eco.cuisineSlots) + (station ? 1 : 0);
        int miscWant = Math.max(0, eco.miscSlots) + (station ? 2 : 0) + extraMisc;
        int tail = cuisineWant + miscWant;
        // 原来这里给"收货区"留 2 行（demandWant）；那 2 行生成的报价在面板上没有出口
        // （买页只认"村子给绿宝石"的货，卖页完全由玩家背包生成），纯粹白占预算。已删，还给买页。
        int supplyCap = Math.max(1, cap - tail) + SUPPLY_EXTRA + extraSupply;
        // 每行能成交几次：高等级村囤货多、成交额度也大（Lv5 ×1.6）
        int uses = (int) Math.round((station ? USES_STATION : USES_VILLAGE)
                * VillageLevel.stockMult(lv));
        java.util.Set<String> seen = new java.util.HashSet<>();   // 标签展开后可能与手写条目撞车
        long tp0 = System.nanoTime();                             // 分段计时：货架到底慢在哪一段
        int miscScan = 0;

        long tp1 = System.nanoTime();
        // —— 卖货：玩家掏绿宝石买本村特产 ——
        // 一个供应条目能出好几种货：标签越大出的越多，同产业的两个村子因此很少撞到同一批货
        int supplyCount = 0;
        supply:
        for (String entry : ind.supply()) {
            for (String item : picks(data, catalog, entry, seed, x, z, perEntry)) {
                if (item == null || currency(item)) continue;
                double raw = baseOf(data, catalog, item);
                double base = withIndex(catalog, item, raw);   // 全球饱和只改价，不改库存上限
                if (base <= 0 || !seen.add(item)) continue;
                if (!shelfOk(catalog, item)) continue;      // 没中文译名的货不上架（但玩家照样能卖给它）
                int tier = catalog == null ? Rarity.COMMON : catalog.rarity(item);
                if (stock != null && stock.level() < Rarity.gate(tier)) continue;   // 档位货要村等级够
                double chain = chainFactor(data, catalog, seed, x, z, day, ind, t, item);
                double buyBase = Economy.current().buyBase;
                double prosp = buyProsperity(t);
                double road = roadFactor();
                double qk = quirk(seed, x, z, item);
                double dl = daily(seed, x, z, day, ind.id());
                double mday = marketDayFactor(seed, x, z, day, false);
                double unit = base * chain * buyBase * prosp * road * qk * dl * mday;
                double stockNow = stock == null ? Double.MAX_VALUE : stock.stockOf(item);
                double sat = stock == null ? 1.0 : Stock.buyImpact(Stock.ratio(stockNow, Stock.cap(raw)));
                unit *= sat;
                // 不死图腾钉死 64（见 TOTEM_PRICE）：不跟产业/行情/库存漂
                if (TOTEM.equals(item)) unit = TOTEM_PRICE;
                // 大额货留一行账：价是怎么一步步涨上去的（想查浮漂/神器这类离谱价，搜 "高价货"）
                if (unit >= 100) {
                    dev.guoke.tradepost.Tradepost.LOGGER.info(
                            "[tradepost] 高价货 {} 原始价={} 现值={} 产业链x{} 买入基准x{} 富庶x{} 道路x{} 村癖x{} 行情x{} 集市x{} 库存x{} → 单价 {}",
                            item, fmt2(raw), fmt2(base), fmt3(chain), fmt3(buyBase), fmt3(prosp), fmt3(road),
                            fmt3(qk), fmt3(dl), fmt3(mday), fmt3(sat), fmt2(unit));
                }
                Pricing.Quote q = Pricing.minimal(unit);
                int u = (stock == null || item.startsWith("car:")) ? uses : Stock.maxUsesFor(stockNow, q.items(), uses);
                // 卖光的行不删：u=0 留红叉原位（跟原版村民一个样），补货后自动恢复
                if (tier >= Rarity.RARE) {
                    // 珍稀/传奇每天限量；村子比门槛每高一级就多给一笔
                    u = Math.min(u, Rarity.uses(tier) + Math.max(0, lv - Rarity.gate(tier)));
                }
                out.add(new OfferSpec("minecraft:emerald", q.emeralds(), item, q.items(), u));
                if (++supplyCount >= supplyCap) break supply;
            }
        }

        long tp2 = System.nanoTime();
        // —— 收货区已删（2026-11-13）：这段生成的报价在面板上没有出口（买页只认"村子给绿宝石"
        // 的货，卖页完全由玩家背包生成），白占 2 行预算，已还给买页。
        // 村子"想要什么"照旧走 ind.demand() 的溢价（见 sellPrice / demandBonus），跟这里的报价无关。

        long tp3 = System.nanoTime();
        // —— 菜与酒：每个村庄有几道本地菜（种类上千，几乎不会撞），价格按材料 + 等级溢价 ——
        if (catalog != null) {
            List<String> dishes = catalog.cuisines();
            int slots = cuisineWant;
            int n = dishes.size();
            if (slots > 0 && n > 0) {
                int start = Hashing.below(n, seed, cellOf(x), cellOf(z), 15);
                int step = 1 + Hashing.below(n > 1 ? n - 1 : 1, seed, cellOf(x), cellOf(z), 16);
                int added = 0;
                for (int i = 0; i < n && added < slots; i++) {
                    if (out.size() >= totalCap) break;
                    String item = dishes.get((int) (((long) start + (long) i * step) % n));
                    if (item == null || currency(item)) continue;
                    double raw = baseOf(data, catalog, item);
                    double base = withIndex(catalog, item, raw);   // 全球饱和只改价，不改库存上限
                    if (base <= 0 || !seen.add(item)) continue;
                    if (!shelfOk(catalog, item)) continue;
                    double unit = base * chainFactor(data, catalog, seed, x, z, day, ind, t, item)
                            * eco.buyBase * buyProsperity(t) * roadFactor() * quirk(seed, x, z, item)
                            * daily(seed, x, z, day, ind.id()) * marketDayFactor(seed, x, z, day, false);
                    double stockNow = stock == null ? Double.MAX_VALUE : stock.stockOf(item);
                    if (stock != null) unit *= Stock.buyImpact(Stock.ratio(stockNow, Stock.cap(raw)));
                    Pricing.Quote q = Pricing.minimal(unit);
                    int u = stock == null ? uses : Stock.maxUsesFor(stockNow, q.items(), uses);
                    out.add(new OfferSpec("minecraft:emerald", q.emeralds(), item, q.items(), u));
                    added++;
                }
            }
        }

        long tp4 = System.nanoTime();
        // —— 杂货：不属于任何产业标签的模组物品（家具、装饰块、杂项材料）也摆几个 ——
        //    池子 = 注册表里所有有价、没被黑名单拦、能堆叠的物品；按村坐标哈希抽，同村稳定。
        if (catalog != null) {
            List<String> pool = catalog.all();
            int slots = miscWant;
            int n = pool.size();
            if (slots > 0 && n > 0) {
                int start = Hashing.below(n, seed, cellOf(x), cellOf(z), 13);
                int step = 1 + Hashing.below(n > 1 ? n - 1 : 1, seed, cellOf(x), cellOf(z), 14);
                int added = 0;
                for (int i = 0; i < n && added < slots; i++) {
                    miscScan++;
                    if (out.size() >= totalCap) break;
                    String item = pool.get((int) (((long) start + (long) i * step) % n));
                    if (item == null || currency(item)) continue;
                    double raw = baseOf(data, catalog, item);
                    double base = withIndex(catalog, item, raw);   // 全球饱和只改价，不改库存上限
                    if (base <= 0 || !seen.add(item)) continue;
                    if (!shelfOk(catalog, item)) continue;
                    double unit = base * chainFactor(data, catalog, seed, x, z, day, ind, t, item)
                            * eco.buyBase * buyProsperity(t) * roadFactor() * quirk(seed, x, z, item)
                            * daily(seed, x, z, day, ind.id()) * marketDayFactor(seed, x, z, day, false);
                    double stockNow = stock == null ? Double.MAX_VALUE : stock.stockOf(item);
                    if (stock != null) unit *= Stock.buyImpact(Stock.ratio(stockNow, Stock.cap(raw)));
                    Pricing.Quote q = Pricing.minimal(unit);
                    int u = stock == null ? uses : Stock.maxUsesFor(stockNow, q.items(), uses);
                    out.add(new OfferSpec("minecraft:emerald", q.emeralds(), item, q.items(), u));
                    added++;
                }
            }
        }

        long tp5 = System.nanoTime();
        // —— 秘宝：只收物，不限库存（它本来就不是村里的货） ——
        if (stock != null) {
            OfferSpec relic = relicOffer(data, seed, x, z, day, station, catalog);
            if (relic != null) out.add(relic);
        }
        long tp6 = System.nanoTime();
        long all = (tp6 - tp0) / 1_000_000L;
        if (all >= 100L) {
            dev.guoke.tradepost.Tradepost.LOGGER.info(
                    "[tradepost] 货架 {} ms＝供给 {} ＋收货 {} ＋菜酒 {} ＋杂货 {}（扫 {} 件）＋秘宝 {}｜产出 {} 条",
                    all, (tp1 - tp0) / 1_000_000L, (tp2 - tp1) / 1_000_000L, (tp3 - tp2) / 1_000_000L,
                    (tp4 - tp3) / 1_000_000L, miscScan, (tp6 - tp5) / 1_000_000L, out.size());
        }
        return out;
    }

    /**
     * 条目 → 具体物品 id。
     * 直接写 id 的原样返回；"#标签" 让目录展开，再按 (村坐标, 条目名) 哈希挑一个 ——
     * 同一个村庄每次都是同一种货（稳定可预报），不同村庄挑到不同货（跑商有意义）。
     */
    private static String pick(MarketData data, Catalog catalog, String entry, long seed, int x, int z) {
        if (entry == null) return null;
        if (!entry.startsWith("#") && !entry.startsWith("@")) return entry;
        Catalog cat = catalog == null ? Catalog.NONE : catalog;
        List<String> ids = expandEntry(cat, entry);
        if (ids.isEmpty()) return null;
        int idx = Hashing.below(ids.size(), seed, cellOf(x), cellOf(z), entry.hashCode(), 7);
        String chosen = ids.get(idx);
        if (baseOf(data, cat, chosen) <= 0) {
            for (String id : ids) {                   // 挑到的那个没价就顺延，保证别空手
                if (baseOf(data, cat, id) > 0) return id;
            }
            return null;
        }
        return chosen;
    }

    /**
     * 同一个条目出多种货（标签越大出得越多）。
     * 起点和步长都由 (村坐标, 条目名) 哈希决定 —— 同村稳定、别村不同，
     * 所以"同产业的两个村子"在物品多起来以后基本不会撞货。
     */
    private static List<String> picks(MarketData data, Catalog catalog, String entry,
                                      long seed, int x, int z, int k) {
        if (entry == null) return List.of();
        if (!entry.startsWith("#") && !entry.startsWith("@")) return List.of(entry);
        Catalog cat = catalog == null ? Catalog.NONE : catalog;
        List<String> ids = expandEntry(cat, entry);
        if (ids.isEmpty() || k <= 0) return List.of();
        int size = ids.size();
        int vx = cellOf(x), vz = cellOf(z);
        int h = entry.hashCode();
        int start = Hashing.below(size, seed, vx, vz, h, 7);
        int step = size <= 1 ? 1 : 1 + Hashing.below(size - 1, seed, vx, vz, h, 8);
        List<String> out = new ArrayList<>(k);
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (int i = 0; i < size && out.size() < k; i++) {
            String id = ids.get((int) (((long) start + (long) i * step) % size));
            if (!taken.add(id)) continue;
            if (baseOf(data, cat, id) <= 0) continue;
            out.add(id);
        }
        if (out.isEmpty()) {                     // 全都没价：至少给一个，别让村里空手
            for (String id : ids) {
                if (baseOf(data, cat, id) > 0) { out.add(id); break; }
            }
        }
        return out;
    }

    /**
     * 条目 → 候选物品表。
     * "@cuisine" 是特殊条目：指"这个整合包里的所有菜和酒"（菜没有统一的物品标签，只能靠配方扫描收集）。
     */
    private static List<String> expandEntry(Catalog cat, String entry) {
        if (entry == null) return List.of();
        if ("@cuisine".equals(entry)) return cat.cuisines();
        if (entry.startsWith("@")) return cat.category(entry.substring(1));   // "@grain" → categories.json
        return cat.expand(entry);
    }

    /** 物品基础价：优先 values.json 的手工定价，其次目录给的标签兜底价。 */
    private static double baseOf(MarketData data, Catalog catalog, String item) {
        if (item == null) return 0;
        double v = data == null ? 0 : data.valueOf(item);
        if (v > 0) return v;                        // 手写价 = 白名单，黑名单管不到
        if (catalog == null) return 0;
        if (catalog.banned(item)) return 0;         // 黑名单：当成没价，各处在"没价就跳过"时自然滤掉
        return catalog.value(item);
    }

    /**
     * 叠上"全球饱和"系数：这种货被全世界卖烂之后，买卖两边一起降价。
     * 离线目录（Catalog.NONE）恒返回 1.0，所以纯逻辑测试不受影响。
     */
    private static double withIndex(Catalog catalog, String item, double base) {
        if (base <= 0) return base;
        // 珍稀/传奇不进世界饱和：全世界卖得多也不该把传奇货压成白菜
        if (catalog != null && Rarity.saturationFree(catalog.rarity(item))) return base;
        double idx = catalog == null ? 1.0 : catalog.worldIndex(item);
        return base * Math.max(0.01, Math.min(1.0, idx));
    }

    /** 不作为货物出现的条目：绿宝石本身是货币，参与报价会变成"7 绿宝石换 1 绿宝石" */
    /** 能不能摆上货架（卖东西给玩家）；null 目录 = 只认手写 id，不设限 */
    private static boolean shelfOk(Catalog catalog, String item) {
        return catalog == null || catalog.canSell(item);
    }

    private static boolean currency(String itemId) {
        return itemId.equals("minecraft:emerald") || itemId.equals("minecraft:emerald_block");
    }

    // —— 全物品收购（潜行右键村民）——

    /** 该村需求清单里的货：卖给它有溢价（数值看 Economy.demandBonus） */
    public static final double DEMAND_BONUS = 1.30;
    /** 该村自己就在大量出产的货：它不缺，压价（数值看 Economy.supplyPenalty） */
    public static final double SUPPLY_PENALTY = 0.88;

    /**
     * 本村此刻**要价**（玩家拿绿宝石买它时的单价）：不查货架、只看库存，任何物品都能算。
     *
     * <p>存在的理由：收购价必须永远低于本村要价 × 0.75，否则能原地倒买倒卖刷钱。
     * 早先这条闸门靠买页货架上那口价（Bridge.Ctx.buyUnits），可买页只列还有库存的货 ——
     * 把某件货买空后它就整条消失，闸门失效，卖价直接浮到缺货溢价（约 1.33 倍），
     * 玩家把库存买光再卖回来就翻倍。这个函数把同一套公式抽出来，不依赖货架在不在。
     *
     * @param stockNow 当前库存件数；@param cap 库存上限（小于等于 0 表示不按库存调节）
     */
    public static double askUnit(MarketData data, Catalog catalog, long seed, int x, int z, long day,
                                 MarketData.Industry ind, int prosperity, String itemId,
                                 double stockNow, double cap) {
        if (itemId == null || currency(itemId)) return 0;
        double raw = baseOf(data, catalog, itemId);
        double base = withIndex(catalog, itemId, raw);
        if (base <= 0) return 0;
        double buyBase = Economy.current().buyBase;
        double unit = base
                * chainFactor(data, catalog, seed, x, z, day, ind, prosperity, itemId)
                * buyBase
                * buyProsperity(prosperity)
                * roadFactor()
                * quirk(seed, x, z, itemId)
                * daily(seed, x, z, day, ind == null ? "-" : ind.id())
                * marketDayFactor(seed, x, z, day, false);
        if (cap > 0) unit *= Stock.buyImpact(Stock.ratio(stockNow, cap));
        // 不死图腾钉死 64：这一口价同时决定"买它多少钱"和"卖回给它值多少"（后者 = 64 × 0.75）
        if (TOTEM.equals(itemId)) return TOTEM_PRICE;
        return unit;
    }
    /**
     * 收购单价：任何有价的物品，任何村子都收，价格靠这几层拉开。
     *
     *   基础价 × 1.32（收购基准）
     *          × 需求系数（本村需求 1.55 / 本村出产 0.80 / 中性 1.00）
     *          × 繁荣度 × 村癖(±8%) × 每日行情(±6%) × 库存（空仓 1.4 / 满仓 0.6）
     *
     * 所以同一件货在"缺它"的村能卖到"产它"的村的 2~3 倍 —— 这就是跑商的利润来源。
     */
    public static double universalSellUnit(MarketData data, Catalog catalog, long seed, int x, int z, long day,
                                          MarketData.Industry ind, int prosperity, String itemId, double stockRatio) {
        return universalSellUnit(data, catalog, seed, x, z, day, ind, prosperity, itemId, stockRatio, 0);
    }

    /**
     * @param areaPressure 区域市场压力（0..1）：同一片地方的村子被你砸过货，大家一起掉价。
     *                     这就是"村庄之间的联动"——你在 A 村倾销，隔壁 B 村的收购价也会跟着跌。
     */
    public static double universalSellUnit(MarketData data, Catalog catalog, long seed, int x, int z, long day,
                                          MarketData.Industry ind, int prosperity, String itemId,
                                          double stockRatio, double areaPressure) {
        if (itemId == null || currency(itemId)) return 0;
        double base = withIndex(catalog, itemId, baseOf(data, catalog, itemId));
        if (base <= 0) return 0;
        Economy eco = Economy.current();
        double factor = 1.00;
        // 顺序很重要：本村自己出的货永远按"产"算。
        // 配置里同一类别同时写进 supply/demand 时，不能既按缺货加价收、又摆在货架上卖。
        if (ind != null && listedIn(catalog, ind.supply(), itemId)) {
            factor = eco.supplyPenalty;            // 自己就在出的货，不缺
        } else if (ind != null && listedIn(catalog, ind.demand(), itemId)) {
            factor = eco.demandBonus;              // 手写明的高价收（认需求才赚得多）
        } else if (ind != null && DemandGraph.of(catalog).wants(ind.id(), itemId)) {
            factor = eco.roleDemand;               // 用途大类对得上：一般价收（环路更密）
        }
        boolean dish = catalog != null ? catalog.dish(itemId) : Cuisine.isCuisine(itemId, null);
        if (dish) factor *= eco.dishPremium;                              // 菜与酒：哪儿都加价收
        String scope = ind == null ? "-" : ind.id();
        double r = Math.max(0.0, Math.min(1.0, stockRatio + Math.max(0.0, areaPressure)));
        return base * chainFactor(data, catalog, seed, x, z, day, ind, prosperity, itemId)
             * eco.sellBase * factor
             * sellProsperity(prosperity)
             * roadFactor()
             * quirk(seed, x, z, itemId)
             * daily(seed, x, z, day, scope)
             * marketDayFactor(seed, x, z, day, true)
             * Stock.sellImpact(r);
    }

    // —— 制作链联动 ——

    /**
     * 某件货在**这个村**比中立价贵/便宜多少：产业供需 + 用途大类 + 菜酒溢价 + 村癖 + 当日行情 + 繁荣度。
     * 不含库存（库存一天里能变好几次，拿它算链价会一直抖）。
     */
    private static double levelFactor(MarketData data, Catalog catalog, long seed, int x, int z, long day,
                                      MarketData.Industry ind, int t, String item) {
        if (item == null) return 1.0;
        Economy eco = Economy.current();
        double f = 1.0;
        if (ind != null && listedIn(catalog, ind.supply(), item)) {
            f = eco.supplyPenalty;
        } else if (ind != null && listedIn(catalog, ind.demand(), item)) {
            f = eco.demandBonus;
        } else if (ind != null && DemandGraph.of(catalog).wants(ind.id(), item)) {
            f = eco.roleDemand;
        }
        boolean dish = catalog != null ? catalog.dish(item) : Cuisine.isCuisine(item, null);
        if (dish) f *= eco.dishPremium;
        // 村癖 / 当日行情 / 繁荣度不加进来：它们本来就会乘在成品自己身上，
        // 再算进材料价等于乘两遍，白白放大波动
        return f;
    }

    /**
     * 制作链系数：本村材料比平均贵多少，用这些材料做出来的成品就贵多少。
     * 例：小麦在某村是缺货（×1.35），那村的干草块 / 面包 / 曲奇会同步往上抬。
     *
     * @return 1.0 = 不调整；只在"确实有配方"的物品上生效，基础货原样返回
     */
    public static double chainFactor(MarketData data, Catalog catalog, long seed, int x, int z, long day,
                                     MarketData.Industry ind, int t, String item) {
        if (item == null || Chain.size() == 0) return 1.0;
        try {
            Economy eco = Economy.current();
            String vk = seed + ":" + x + ":" + z + ":" + day;
            return Chain.factor(vk,
                    id -> baseOf(data, catalog, id),
                    id -> levelFactor(data, catalog, seed, x, z, day, ind, t, id),
                    rec -> {
                        boolean dish = catalog != null ? catalog.dish(rec.output()) : Cuisine.isCuisine(rec.output(), null);
                        double b = dish ? Cuisine.bonus(rec.output(), rec.slots(), id -> baseOf(data, catalog, id)) : 0;
                        return b > 0 ? b : eco.craftPenalty;
                    },
                    rec -> catalog != null && catalog.dish(rec.output())
                            ? Cuisine.minPrice(rec.output(), rec.slots()) : 0,
                    item);
        } catch (Throwable thr) {
            return 1.0;
        }
    }

    /** 条目清单里有没有这件货（"#标签" 会展开） */
    public static boolean listedIn(Catalog catalog, List<String> entries, String itemId) {
        if (entries == null || itemId == null) return false;
        Catalog cat = catalog == null ? Catalog.NONE : catalog;
        for (String e : entries) {
            if (e == null) continue;
            if (e.startsWith("#") || e.startsWith("@")) {
                if (expandEntry(cat, e).contains(itemId)) return true;
            } else if (e.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    public static String describe(MarketData data, long seed, int x, int z, long day) {
        return describe(data, seed, x, z, day, false);
    }

    public static String describe(MarketData data, long seed, int x, int z, long day, boolean station) {
        return describe(data, seed, x, z, day, station, null);
    }

    /** 带群系版本：必须和 offers() 用同一群系，否则表头产业会和实际报价对不上。 */
    public static String describe(MarketData data, long seed, int x, int z, long day, boolean station, BiomeInfo biome) {
        MarketData.Industry ind = industry(data, seed, x, z, biome);
        int t = prosperity(seed, x, z);
        return (station ? "[驿站]" : "[村庄]") + " 格 " + cellOf(x) + "," + cellOf(z)
             + " 产业=" + (ind == null ? "无" : ind.display() + "(" + ind.id() + ")")
             + " 富庶=" + String.format("%.2f", wealth()) + "（" + Math.round(houses()) + " 户/" + traitKinds() + " 式）"
             + " 第 " + day + " 天"
             + (marketDay(seed, x, z, day) ? " ★集市日" : "");
    }

    /** 日志用：两位 / 三位小数（Locale.ROOT，别让中文系统吐逗号） */
    private static String fmt2(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String fmt3(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
