package dev.guoke.tradepost.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 经济系数总表 —— 全部可在 config/tradepost/market/economy.json 里改，改完 /tradepost reload 生效。
 *
 * 设计目标（"跑商要动脑子，不是搬砖"）：
 *   - 单村原地倒买倒卖无利可图（靠 spreadFloor + 库存曲线）
 *   - 两个村子来回倒也没那么肥（靠 areaSpread 区域联动 + 慢恢复）
 *   - 想赚大钱只有一条路：记住哪产哪缺，跑一圈 3~5 站的环线
 */
public final class Economy {

    /** JSON 里可以忽略的说明字段 */
    public String _readme = "跑商数值总表；改完用 /tradepost reload 生效。数值会被夹到合理区间，写错不会炸存档。";

    /** 玩家买入基准（买 = 基础价 x buyBase） */
    public double buyBase = 0.85;
    /** 玩家卖出基准（卖 = 基础价 x sellBase） */
    public double sellBase = 1.18;
    /** 本村需求清单里的货：收购溢价 */
    public double demandBonus = 1.35;
    /** 本村没手写明、但"用途大类对得上"时的收购加成（介于需求和中性之间） */
    public double roleDemand = 1.18;
    /** 本村自己就在出的货：收购压价 */
    public double supplyPenalty = 0.90;
    /** 村癖幅度（同产业两村的固定差价） */
    public double quirkPct = 0.12;
    /** 每日行情幅度 */
    public double dailyPct = 0.10;
    /** 库存每日恢复上限比例（0.02 → 50 天回满） */
    public double stockRegen = 0.02;

    /** 买价曲线斜率：越大，满仓越便宜、空仓越贵 */
    public double buySlope = 0.85;
    public double buyGlutMin = 0.55;
    public double buyScarcityMax = 1.45;
    /** 收价曲线斜率 */
    public double sellSlope = 0.65;
    public double sellGlutMin = 0.68;
    public double sellScarcityMax = 1.33;

    /** 同村收价封顶：收购价 ≤ 本村卖给你的价 x 该值 */
    public double spreadFloor = 0.75;

    /** 区域市场半径（格）：同区域内的村庄共享库存压力 */
    public int areaRadius = 1024;
    /** 区域压力的权重：0 = 各村互不影响，0.45 = 周边村子被你砸过货就一起掉价 */
    public double areaSpread = 0.45;
    /** 把区域堆满需要几个库存上限的货量 */
    public double areaFullCaps = 2.0;
    /** 区域压力每天消退的比例（0.05 → 约 14 天减半） */
    public double areaDecay = 0.05;

    /** 集市日周期（天）：每 N 天有一个村子赶集 */
    public int marketDayPeriod = 6;
    /** 集市日该村收购价加成 */
    /**
     * 集市日收价加成。**必须与 marketDayBuy 对称（都 1.10）**：
     * 收你的 ×1.35、卖给你的 ×1.10 就是一个确定性套利窗口（非集市日买入、等集市日卖出 +26.9%），
     * 以前靠卖价封顶 0.75 压着，动态市场撤掉 0.75 之后必须对称化（DYNAMIC_MARKET_REVIEW P0-2）。
     */
    public double marketDaySell = 1.10;
    /** 集市日该村卖给你的价也略涨（人多，货贵） */
    public double marketDayBuy = 1.10;

    /** 风声：有多少百分比的村庄会被人提起（越小越难白嫖情报） */
    public int rumorChance = 60;
    /** 风声最远距离（格），再远就"听不清了"；太近（眼前就有）也不算 */
    public int rumorMaxDistance = 2000;
    /** 一次最多说几条风声 */
    public int rumorCount = 2;

    // —— 货架容量：一个村子/驿站到底摆多少种货 ——
    /** 普通村庄货架条数（买入 + 卖出合计上限） */
    public int villageOffers = 14;
    /** 驿站货架条数 */
    public int stationOffers = 24;
    /** 卖给你几"类"货（每类再按 itemsPerEntry 出几件；标签越大出得越多） */
    public int supplySlots = 6;
    /** 每个供应条目出几件货 */
    public int itemsPerEntry = 4;
    /** 杂货位：每村额外摆几个"不属于任何产业"的模组物品（驿站 +2）；0 = 关掉 */
    public int miscSlots = 4;
    /** 菜与酒的货架位（每村几道本地菜） */
    public int cuisineSlots = 3;
    /** 做好的菜与酒：任何村子都愿意加价收（烹饪是劳动，材料还难找） */
    public double dishPremium = 1.35;
    /**
     * 一切"能吃的东西"（走标签价或兜底价的模组食材）的价格倍数。
     * 玩家反馈模组食物基础价太低：标签价 #c:foods 0.4 / #c:crops 0.3 是按建材思路定的，
     * 对着几百种模组食材太贱。乘 4 之后 1 个熟食约 1.6 绿宝石，仍远低于按配方算的菜。
     */
    public double foodMultiplier = 4.0;
    /**
     * 配方成本价的折扣：按材料成本反推出来的物品（菜、点心、合成器械），
     * 村里只按成本的这个比例认价。0.65 让"买材料→合成→原地卖回去"亏本（含 T3 村），
     * 但又远高于 0.05 的保底价。1.0 = 完全按成本认。
     */
    public double craftPenalty = 0.65;
    /**
     * 工序劳动（加法，不是乘区）：制作链每深一层，成本加这么多。
     * 12 步做出来的东西比 2 步的贵 10×stepLabor，且不被 craftPenalty/Cuisine 溢价放大。
     * 只作用于配方定价的物品（菜、工具、装备）；手写价不受影响。
     */
    public double stepLabor = 0.5;

    // —— 村庄银库：村里到底有多少绿宝石可以付给你 ——
    /**
     * 卖货不是凭空生钱，是从这个村子自己的银库里掏。掏空就收不动，逼你换村或者等它回血。
     * 三档按繁荣度：T1 小村穷、T3 富。
     */
    public double walletT1 = 400;
    public double walletT2 = 1200;
    public double walletT3 = 3000;
    /** 银库每天回血比例（0.03 → 约 33 天回满） */
    public double walletRegen = 0.03;
    /** 第一次到这个村时银库的初始比例 */
    public double walletStart = 0.6;
    /**
     * 每日收购预算系数：一个村一天最多付出去「银库余额 × 这个数」。
     *
     * <p>库存只按件数限量，件数 × 单价才是钱 —— 贵货一件 169 颗、便宜货一天才 29 颗，
     * 两者差 38 倍（DYNAMIC_MARKET_DESIGN §5.7）。这条闸把「每天每村能换走多少钱」压平，
     * 也顺手防「卖穿村子」：银子越少，第二天预算越小。0 = 关闭，1.0 = 等于银库（形同没闸）。
     */
    public double dailyBudgetRate = 0.3;

    // —— 全球饱和：某种货被全世界吃下多少，就值多少钱 ——
    /**
     * 以"件数"计：价格指数 = 1 / (1 + (累计件数 / worldSellK) ^ worldCurveP)。
     * 默认 K = 2000 件、p = 2：卖掉 2000 件 → 指数 0.5（腰斩）；
     * 卖掉 4000 件 → 0.2；卖掉 8000 件 → 0.06。
     * 不看单价 —— 贵的东西不会"卖几十件就崩"，泛滥的便宜货也逃不掉。
     * p > 1 让曲线前段很平（小打小闹几乎不跌价），越过 K 之后陡然下滑（真砸盘就雪崩）。
     * 这就是防"黄金树无限产金"的那道闸：金子越卖越不值钱，最后跌到木头价以下。
     */
    public double worldSellK = 2000;
    /** 曲线指数：1 = 双曲线；>1 = 前平后陡（默认 2.0，越大越"小量无视、大量雪崩"） */
    public double worldCurveP = 2.0;
    /** 全球饱和每天消退比例（0.005 → 约 138 天减半，别指望明天就涨回来） */
    public double worldDecay = 0.005;
    /** 价格指数下限（再怎么泛滥也不会归零，留点渣） */
    public double worldMinIndex = 0.02;
    /**
     * 只有基础价 ≥ 这个值的货才吃"全球饱和"。
     * 小麦/圆石/木板这种大宗便宜货被卖多少都不该全图崩 —— 它们只受片区压力影响（附近村子掉价）。
     * 默认 1.0 绿宝石：金锭(3.2)、钻石(24) 会崩，小麦(0.12)、煤(0.36) 不会。
     */
    public double worldMinBase = 1.0;

    // —— 先天条件：商路（阡陌交通）与村庄占地 ——
    /**
     * 商路加成：村里每有一条"已通车"的商路，收购价 +该比例。
     * 路通的地方货进得来、出得去，收货敢给高价；没通路的偏远村照旧。
     * 只加收购侧（玩家卖货），不加卖出侧 —— 不然买货也跟着贵，等于没区别。
     * 0 = 关掉（不读路网）。
     */
    public double roadPriceDrop = 0.04;
    /** 商路加成的封顶条数：路再多也只按这条数算（防止枢纽村价格失控） */
    public int roadMaxDegree = 3;
    /** 每有一条商路，货架多摆几件货（0 = 不加） */
    public int roadExtraOffers = 1;
    /**
     * "标准村"有多少栋屋子（富庶度 1.0 的位置）。
     * 调这个就是整体校准：整合包里村子普遍偏大，就把它调大，价格回到中性。
     */
    public int wealthHouses = 12;
    /** 每多一种房屋样式（占地/高度分档），富庶度 +该比例（最多 +20%） */
    public double wealthVariety = 0.03;

    private static Economy current = new Economy();

    public static Economy current() {
        return current;
    }

    public static void set(Economy e) {
        if (e != null) current = e;
    }

    /** 读配置；文件不存在或写坏就用默认值，并且保证数值都在合理区间 */
    public static Economy load(Path file) {
        Economy e = new Economy();
        if (file != null && Files.isRegularFile(file)) {
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                com.google.gson.JsonObject raw = com.google.gson.JsonParser.parseString(readAll(r)).getAsJsonObject();
                Economy parsed = new Gson().fromJson(raw, Economy.class);
                if (parsed != null) {
                    // 老版本 config 里没有的字段（例如后加的 rumor*）不能被当成 0，缺就用默认值
                    if (!raw.has("rumorChance")) parsed.rumorChance = e.rumorChance;
                    if (!raw.has("rumorMaxDistance")) parsed.rumorMaxDistance = e.rumorMaxDistance;
                    if (!raw.has("rumorCount")) parsed.rumorCount = e.rumorCount;
                    if (!raw.has("villageOffers")) parsed.villageOffers = e.villageOffers;
                    if (!raw.has("stationOffers")) parsed.stationOffers = e.stationOffers;
                    if (!raw.has("supplySlots")) parsed.supplySlots = e.supplySlots;
                    if (!raw.has("itemsPerEntry")) parsed.itemsPerEntry = e.itemsPerEntry;
                    if (!raw.has("miscSlots")) parsed.miscSlots = e.miscSlots;
                    if (!raw.has("cuisineSlots")) parsed.cuisineSlots = e.cuisineSlots;
                    if (!raw.has("dishPremium")) parsed.dishPremium = e.dishPremium;
                    if (!raw.has("craftPenalty")) parsed.craftPenalty = e.craftPenalty;
        if (!raw.has("stepLabor")) parsed.stepLabor = e.stepLabor;
                    if (!raw.has("foodMultiplier")) parsed.foodMultiplier = e.foodMultiplier;
                    if (!raw.has("walletT1")) parsed.walletT1 = e.walletT1;
                    if (!raw.has("walletT2")) parsed.walletT2 = e.walletT2;
                    if (!raw.has("walletT3")) parsed.walletT3 = e.walletT3;
                    if (!raw.has("walletRegen")) parsed.walletRegen = e.walletRegen;
                    if (!raw.has("walletStart")) parsed.walletStart = e.walletStart;
        if (!raw.has("dailyBudgetRate")) parsed.dailyBudgetRate = e.dailyBudgetRate;
                    if (!raw.has("worldSellK")) parsed.worldSellK = e.worldSellK;
                    if (!raw.has("worldCurveP")) parsed.worldCurveP = e.worldCurveP;
                    if (!raw.has("worldDecay")) parsed.worldDecay = e.worldDecay;
                    if (!raw.has("worldMinIndex")) parsed.worldMinIndex = e.worldMinIndex;
                    if (!raw.has("worldMinBase")) parsed.worldMinBase = e.worldMinBase;
                    if (!raw.has("roadPriceDrop")) parsed.roadPriceDrop = e.roadPriceDrop;
                    if (!raw.has("roadMaxDegree")) parsed.roadMaxDegree = e.roadMaxDegree;
                    if (!raw.has("roadExtraOffers")) parsed.roadExtraOffers = e.roadExtraOffers;
                    if (!raw.has("wealthHouses")) parsed.wealthHouses = e.wealthHouses;
                    if (!raw.has("wealthVariety")) parsed.wealthVariety = e.wealthVariety;
                    e = parsed;
                }
            } catch (Exception ex) {
                dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] economy.json 读不了，用默认值", ex);
            }
        }
        e.clampAll();
        return e;
    }

    private static String readAll(BufferedReader r) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    /** 把默认值写出去（已存在则不覆盖，免得冲掉玩家的调参） */
    public static boolean exportDefault(Path file) {
        if (file == null) return false;
        try {
            // 空文件也算没写过（上一次写盘被打断的情况，别让它永久占位）
            if (Files.exists(file) && Files.size(file) > 0) return false;
        } catch (Exception ignored) {
            return false;
        }
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(new Economy()));
            }
            return true;
        } catch (Exception ex) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 写 economy.json 失败", ex);
            return false;
        }
    }

    /** 有人手改坏（写成 0 或负数）也不会把经济搞死 */
    public void clampAll() {
        buyBase = clamp(buyBase, 0.30, 1.20);
        sellBase = clamp(sellBase, 0.60, 2.00);
        demandBonus = clamp(demandBonus, 1.00, 2.00);
        roleDemand = clamp(roleDemand, 1.00, 2.00);
        supplyPenalty = clamp(supplyPenalty, 0.40, 1.00);
        quirkPct = clamp(quirkPct, 0.0, 0.35);
        dailyPct = clamp(dailyPct, 0.0, 0.35);
        stockRegen = clamp(stockRegen, 0.002, 0.20);
        buySlope = clamp(buySlope, 0.0, 2.0);
        buyGlutMin = clamp(buyGlutMin, 0.20, 1.00);
        buyScarcityMax = clamp(buyScarcityMax, 1.00, 3.00);
        sellSlope = clamp(sellSlope, 0.0, 2.0);
        sellGlutMin = clamp(sellGlutMin, 0.20, 1.00);
        sellScarcityMax = clamp(sellScarcityMax, 1.00, 3.00);
        spreadFloor = clamp(spreadFloor, 0.30, 0.99);
        areaRadius = (int) clamp(areaRadius, 256, 8192);
        areaSpread = clamp(areaSpread, 0.0, 1.0);
        areaFullCaps = clamp(areaFullCaps, 0.5, 20.0);
        areaDecay = clamp(areaDecay, 0.0, 0.50);
        marketDayPeriod = (int) clamp(marketDayPeriod, 1, 60);
        marketDaySell = clamp(marketDaySell, 1.0, 3.0);
        marketDayBuy = clamp(marketDayBuy, 0.5, 2.0);
        rumorChance = (int) Math.round(clamp(rumorChance, 0, 100));
        rumorMaxDistance = (int) Math.round(clamp(rumorMaxDistance, 200, 8000));
        rumorCount = (int) Math.round(clamp(rumorCount, 0, 4));
        villageOffers = (int) Math.round(clamp(villageOffers, 3, 32));
        stationOffers = (int) Math.round(clamp(stationOffers, 4, 48));
        supplySlots = (int) Math.round(clamp(supplySlots, 1, 12));
        itemsPerEntry = (int) Math.round(clamp(itemsPerEntry, 1, 8));
        miscSlots = (int) Math.round(clamp(miscSlots, 0, 12));
        cuisineSlots = (int) Math.round(clamp(cuisineSlots, 0, 8));
        dishPremium = clamp(dishPremium, 0.5, 3.0);
        craftPenalty = clamp(craftPenalty, 0.3, 1.5);
        stepLabor = clamp(stepLabor, 0.0, 20.0);
        foodMultiplier = clamp(foodMultiplier, 1.0, 20.0);
        walletT1 = clamp(walletT1, 0, 200000);
        walletT2 = clamp(walletT2, 0, 200000);
        walletT3 = clamp(walletT3, 0, 200000);
        walletRegen = clamp(walletRegen, 0.0, 0.30);
        walletStart = clamp(walletStart, 0.0, 1.0);
        dailyBudgetRate = clamp(dailyBudgetRate, 0.0, 1.0);
        worldSellK = clamp(worldSellK, 100, 500000);
        worldCurveP = clamp(worldCurveP, 0.5, 4.0);
        worldDecay = clamp(worldDecay, 0.0, 0.20);
        worldMinIndex = clamp(worldMinIndex, 0.005, 1.0);
        worldMinBase = clamp(worldMinBase, 0.0, 500.0);
        roadPriceDrop = clamp(roadPriceDrop, 0.0, 0.30);
        roadMaxDegree = (int) Math.round(clamp(roadMaxDegree, 0, 8));
        roadExtraOffers = (int) Math.round(clamp(roadExtraOffers, 0, 4));
        wealthHouses = (int) Math.round(clamp(wealthHouses, 3, 80));
        wealthVariety = clamp(wealthVariety, 0.0, 0.10);
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
