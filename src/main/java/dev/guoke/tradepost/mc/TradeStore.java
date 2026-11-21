package dev.guoke.tradepost.mc;

import dev.guoke.tradepost.market.Stock;

import net.minecraft.class_18;
import net.minecraft.class_2487;
import net.minecraft.class_4284;
import net.minecraft.class_7225;

import java.util.HashMap;
import java.util.Map;

/**
 * 存档侧的市场数据：每个村庄格每件物品还剩多少库存、最后结算到第几天。
 *
 * 只用原版 PersistentState（跟掠夺、地图数据同一套机制），落在存档
 * <world>/data/tradepost_market.dat，跟存档走，不跨存档。
 *
 * 产业/繁荣度/村癖仍然是哈希算出来的（不存档），存档的只有"玩家把货砸进去之后世界变了多少"。
 */
public final class TradeStore extends class_18 {

    @SuppressWarnings("unchecked")
    public static final class_18.class_8645<TradeStore> TYPE = new class_18.class_8645<>(
            TradeStore::new,
            TradeStore::fromNbt,
            class_4284.field_45081);   // 借用 SAVED_DATA_RAIDS 的 datafixer 类型；我们总写当前版本，不会被 fix

    private final Map<String, Map<String, Double>> stock = new HashMap<>();
    private final Map<String, Long> day = new HashMap<>();

    /**
     * 区域市场的累计净流入：areaKey → 物品 → 件数（正数 = 玩家往这片地方灌了多少货）。
     * 这是"村庄联动"的存档侧：同一片区域内的村子共用这份压力，
     * 你在 A 村倾销，B/C 村的收购价跟着跌，逼着你换片区跑。
     */
    private final Map<String, Map<String, Double>> area = new HashMap<>();
    /** 每个区域最后一次记账的日期（用来按天消退压力） */
    private final Map<String, Long> areaDay = new HashMap<>();

    /**
     * 村庄银库：vkey → 这个村还有多少绿宝石可以付给玩家。
     * 卖货从这里掏钱，掏空就收不动（第二天回一点血）；玩家买东西会把钱塞回这里。
     */
    private final Map<String, Double> wallet = new HashMap<>();
    /** 每个村最后一次结息的日子 */
    private final Map<String, Long> walletDay = new HashMap<>();

    /**
     * 单笔成交「价值 ≥ 这个数」才计入每日收购预算。
     *
     * <p>预算的本意是压住「一件顶一天」的贵货，不是压住一把一把卖的便宜货：
     * 20 颗以下的成交（小麦、羊毛、杂货）完全不进预算账，免得 20 单便宜货把贵货的额度吃光。
     */
    private static final int BUDGET_TRACK_MIN = 20;

    /** 今天这个村已经付给玩家多少（每日收购预算，见 DYNAMIC_MARKET_DESIGN §5.7） */
    private final Map<String, Double> budgetSpent = new HashMap<>();
    private final Map<String, Long> budgetDay = new HashMap<>();

    /**
     * 零头账：玩家 UUID → 不足 1 个绿宝石的余额（永远待在 [0,1) 之间）。
     *
     * 绿宝石是整数，价格却是浮点数。没有这本账就会两头漏：
     * 卖 3 件单价 0.13 的货（理论 0.39）四舍五入成 0，等于白送；
     * 买 1 件 0.6 的货硬收 1，等于多收 67%。
     * 现在零头存在账上，下次成交自动补/抵，长期一分不差。
     */
    private final Map<String, Double> pending = new HashMap<>();

    /**
     * 全球饱和：物品 → 全世界累计吃下的"绿宝石价值"（件数 x 基础价），会按天慢慢消退。
     * 这是防"无限资源刷钱"的总闸：金子被几万人卖过之后，全地图的金价一起垮。
     */
    private final Map<String, Double> world = new HashMap<>();
    private final Map<String, Long> worldDay = new HashMap<>();
    /** 村庄经验：vkey → 累计经验（只增不减）。等级由 VillageLevel.of(xp) 现算，不单独存。 */
    private final Map<String, Double> xp = new HashMap<>();

    /** 装备货当天已卖件数：key = 村|购买码，gearDay 记是哪一天，跨天自动归零（不用历史曲线） */
    private final Map<String, Double> gearSold = new HashMap<>();
    /** 捐赠：key = vkey|day，value = 已捐颗数（每村每天上限用） */
    private final Map<String, Double> donated = new HashMap<>();
    private final Map<String, Long> gearDay = new HashMap<>();

    /**
     * 上次开面板那天的单价（×100 的整数，省得存浮点）：vkey → (货码 → 分)。
     * 只用于面板上的涨跌箭头 —— 同一天不重记，这样箭头一整天钉住，不会每次成交都翻。
     */
    private final Map<String, Map<String, Integer>> seenPrice = new HashMap<>();
    private final Map<String, Long> seenDay = new HashMap<>();

    /** 每次记账 +1：用来给"合成链饱和传播"的缓存做失效判断 */
    private long version = 0;

    public long version() {
        return version;
    }

    public TradeStore() {}

    public static String key(int vx, int vz) {
        return vx + "," + vz;
    }

    /** 读库存（顺带按天数补货） */
    public double get(String vkey, String item, double cap, long today) {
        Map<String, Double> m = stock.computeIfAbsent(vkey, k -> new HashMap<>());
        long last = day.getOrDefault(vkey, today);
        double cur = m.getOrDefault(item, cap * 0.5);    // 没记录的物品从半仓开始：村里有点货，也还有收货空间
        double now = Stock.advance(cur, cap, today - last);
        if (now != cur) m.put(item, now);
        return now;
    }

    /** 结算到第 today 天，只做一次补货推进 */
    public void settle(String vkey, long today) {
        // 库存上限按村等级放大：先把这个村的等级塞给纯逻辑层，后面所有 capOf 才是同一个数
        dev.guoke.tradepost.market.VillageMarket.setLevelHint(level(vkey));
        long last = day.getOrDefault(vkey, today);
        if (today <= last) return;
        day.put(vkey, today);
        Map<String, Double> m = stock.get(vkey);
        if (m == null) return;
        for (Map.Entry<String, Double> e : m.entrySet()) {
            e.setValue(Stock.advance(e.getValue(), capOf(e.getKey()), today - last));
        }
        method_80();
    }

    /** 库存上限缓存（不存盘，按物品基础价现算） */
    private static double capOf(String item) {
        double base = dev.guoke.tradepost.Tradepost.valueOf(item);
        return base <= 0 ? 64 : Stock.cap(base);
    }

    public void add(String vkey, String item, double delta, double cap, long today) {
        add(vkey, null, item, delta, cap, today);
    }

    /**
     * 记一笔库存；同时把这次的净流入累进区域市场（areaKey 可以为 null，那就是纯单村）。
     * delta > 0 = 玩家把货卖给了村子（这片地方货变多），< 0 = 玩家把货买走（货变少）。
     */
    /**
     * 库存版本：只被 {@link #add} 推进（读库存不动它）。
     * 收购价缓存靠它失效 —— 用 {@link #version()} 不行，那个被记账/零头也推，缓存永远命中不了。
     */
    private long stockVer;

    public long stockVer() {
        return stockVer;
    }

    public void add(String vkey, String areaKey, String item, double delta, double cap, long today) {
        double cur = get(vkey, item, cap, today);
        stockVer++;
        double next = Math.max(0, Math.min(cap, cur + delta));
        stock.computeIfAbsent(vkey, k -> new HashMap<>()).put(item, next);
        day.putIfAbsent(vkey, today);
        if (areaKey != null && delta != 0) {
            Map<String, Double> m = area.computeIfAbsent(areaKey, k -> new HashMap<>());
            long last = areaDay.getOrDefault(areaKey, today);
            double decay = decayFactor(today - last);
            // 先把这一片的旧压力按天数消退，再叠加本次的流入
            for (Map.Entry<String, Double> en : m.entrySet()) en.setValue(en.getValue() * decay);
            double v = m.getOrDefault(item, 0.0) + delta;
            // 只保留"还有货"的正数部分；玩家持续买走会把它拉回 0，价格自然恢复
            m.put(item, Math.max(0.0, v));
            areaDay.put(areaKey, today);
        }
        method_80();
    }

    /**
     * 区域压力 0..1：这一带堆了多少该物品。
     * 堆满 areaFullCaps 个库存上限就算完全饱和（价格按"整片地方都没人要"算）。
     */
    public double pressure(String areaKey, String item, double cap, long today) {
        if (areaKey == null) return 0;
        Map<String, Double> m = area.get(areaKey);
        if (m == null) return 0;
        double net = m.getOrDefault(item, 0.0);
        if (net <= 0) return 0;
        net *= decayFactor(today - areaDay.getOrDefault(areaKey, today));
        double full = Math.max(16.0, cap * Math.max(0.5, dev.guoke.tradepost.market.Economy.current().areaFullCaps));
        return Math.min(1.0, net / full);
    }

    /** 区域压力按天消退：每天乘 (1-areaDecay) */
    private static double decayFactor(long days) {
        if (days <= 0) return 1.0;
        double d = dev.guoke.tradepost.market.Economy.current().areaDecay;
        if (d <= 0) return 1.0;
        return Math.pow(Math.max(0.0, 1.0 - d), Math.min(days, 3650));
    }

    // —— 零头账 ——

    /** 这名玩家账上还有多少绿宝石零头（0 ≤ x < 1） */
    public double credit(String uuid) {
        return pending.getOrDefault(uuid, 0.0);
    }

    /**
     * 卖货结账：exact 是这笔交易的理论价值。返回实际付给玩家的整数绿宝石，
     * 剩下的零头记在账上，下次继续攒。
     */
    public int receive(String uuid, double exact) {
        double c = pending.getOrDefault(uuid, 0.0) + Math.max(0.0, exact);
        int pay = (int) Math.floor(c + 1e-9);
        double left = c - pay;
        if (left < 1e-6) pending.remove(uuid);
        else pending.put(uuid, left);
        version++;
        method_80();
        return pay;
    }

    /**
     * 买货结账：exact 是理论花费，返回玩家实际要掏的整数绿宝石。
     * 账上零头先抵，不够的部分向上取整（多付的那点又记回账上，所以不亏）。
     */
    public int charge(String uuid, double exact) {
        double c = pending.getOrDefault(uuid, 0.0);
        double want = Math.max(0.0, exact);
        double need = want - c;
        int pay = need <= 1e-9 ? 0 : (int) Math.ceil(need - 1e-9);
        double left = c + pay - want;
        if (left < 0) left = 0;
        if (left < 1e-6) pending.remove(uuid);
        else pending.put(uuid, left);
        version++;
        method_80();
        return pay;
    }

    // —— 村庄银库 ——

    /** 这个村现在有多少绿宝石（按天回血，上限 walletCap） */
    public double wallet(String vkey, double cap, long today) {
        double cur = wallet.getOrDefault(vkey, cap * dev.guoke.tradepost.market.Economy.current().walletStart);
        long last = walletDay.getOrDefault(vkey, today);
        long d = today - last;
        if (d > 0 && cap > 0) {
            cur = Math.min(cap, cur + d * cap * dev.guoke.tradepost.market.Economy.current().walletRegen);
            wallet.put(vkey, cur);
            walletDay.put(vkey, today);
        }
        return Math.max(0.0, cur);
    }

    /**
     * 今天这个村还能收多少颗货（每日收购预算 = dailyBudgetRate × 当前银库余额，跨天清零）。
     *
     * <p>用「当前余额」而不是「银库上限」：村子越穷预算越小，天然不会把它卖穿。
     */
    public double budgetLeft(String vkey, double cap, long today) {
        double rate = dev.guoke.tradepost.market.Economy.current().dailyBudgetRate;
        if (rate <= 0) return Double.MAX_VALUE;
        double purse = wallet(vkey, cap, today);
        double budget = Math.max(0.0, rate * purse);
        double spent = budgetDay.getOrDefault(vkey, today) == today
                ? budgetSpent.getOrDefault(vkey, 0.0) : 0.0;
        return Math.max(0.0, budget - spent);
    }

    /** 银库收支：delta < 0 = 付给玩家，> 0 = 玩家买东西塞回来了 */
    public void walletAdd(String vkey, double delta, double cap, long today) {
        double cur = wallet(vkey, cap, today);
        wallet.put(vkey, Math.max(0.0, Math.min(cap, cur + delta)));
        walletDay.putIfAbsent(vkey, today);
        if (delta <= -BUDGET_TRACK_MIN) {
            long d = budgetDay.getOrDefault(vkey, today);
            if (d != today) {
                budgetSpent.put(vkey, 0.0);      // 跨天了，重新开始记这一天花了多少
                budgetDay.put(vkey, today);
            }
            budgetSpent.merge(vkey, -delta, Double::sum);
        }
        method_80();
    }

    // —— 全球饱和 ——

    /** 记一笔：某种货被玩家在这个世界卖掉多少**件**（base 参数留着兼容旧调用点，不参与记账） */
    public void worldAdd(String item, double count, double base, long today) {
        if (item == null || count == 0) return;
        double cur = worldNet(item, today);
        // 只数件数、不看单价：贵的东西不会"卖几十件就崩"，便宜货卖一万件也照样堆到崩
        world.put(item, Math.max(0.0, cur + Math.max(0.0, count)));
        worldDay.putIfAbsent(item, today);
        version++;
        method_80();
    }

    /**
     * 全球价格指数：1.0 = 没被砸过；越小说明这种货已经泛滥。
     *
     * 曲线是 1 / (1 + (累计件数 / K)^p)：
     *   p = 1 是双曲线（一卖就掉，小量也疼）；
     *   p > 1 时前半段很平、越过 K 之后陡然下滑 —— 小打小闹几乎不影响行情，真砸盘才崩。
     * p 由 economy.json 的 worldCurveP 控制（默认 2.0）。
     */
    public double worldIndex(String item, long today) {
        double net = worldNet(item, today);
        var eco = dev.guoke.tradepost.market.Economy.current();
        double k = Math.max(1.0, eco.worldSellK);
        double p = Math.max(0.5, Math.min(4.0, eco.worldCurveP));
        double r = net / k;
        double idx = 1.0 / (1.0 + Math.pow(r, p));
        double lo = eco.worldMinIndex;
        return Math.max(lo, Math.min(1.0, idx));
    }

    /** 当前累计件数（顺带按天消退） */
    private double worldNet(String item, long today) {
        Double v = world.get(item);
        if (v == null) return 0;
        long last = worldDay.getOrDefault(item, today);
        long d = today - last;
        double cur = v;
        if (d > 0) {
            double dec = dev.guoke.tradepost.market.Economy.current().worldDecay;
            if (dec > 0) cur = v * Math.pow(Math.max(0.0, 1.0 - dec), Math.min(d, 3650));
            world.put(item, cur);
            worldDay.put(item, today);
        }
        return Math.max(0.0, cur);
    }

    /** 这个村累计多少经验 */
    public double xp(String vkey) {
        return xp.getOrDefault(vkey, 0.0);
    }

    /** 加经验（顺带返回加完之后的等级，方便调用方提示"升级了"） */
    public int addXp(String vkey, double amount) {
        if (vkey == null || amount <= 0) return dev.guoke.tradepost.market.VillageLevel.of(xp(vkey));
        xp.put(vkey, xp(vkey) + amount);
        method_80();
        return dev.guoke.tradepost.market.VillageLevel.of(xp(vkey));
    }

    /** 直接把村庄经验设到某个值（测试用：/tradepost setlevel 指定等级，可降级） */
    public int setXp(String vkey, double amount) {
        if (vkey == null) return 1;
        xp.put(vkey, Math.max(0, amount));
        method_80();
        return dev.guoke.tradepost.market.VillageLevel.of(xp(vkey));
    }

    public int level(String vkey) {
        return dev.guoke.tradepost.market.VillageLevel.of(xp(vkey));
    }

    /** 上次开面板时这件货的单价（分）；没有记录返回 -1 */
    public int seenCents(String vkey, String code) {
        Map<String, Integer> m = seenPrice.get(vkey);
        if (m == null) return -1;
        Integer v = m.get(code);
        return v == null ? -1 : v;
    }

    /**
     * 记下"这次开面板时看到的价"，箭头下次拿它比。
     * 同一天不覆盖：不然成交一次重算一次货架，箭头就跟着抖（玩家看到的"箭头乱跳"）。
     */
    public void rememberPrices(String vkey, long today, Map<String, Integer> cents) {
        if (vkey == null || cents == null || cents.isEmpty()) return;
        if (seenDay.getOrDefault(vkey, -1L) >= today) return;
        seenPrice.put(vkey, new HashMap<>(cents));
        seenDay.put(vkey, today);
    }

    /** 这件装备今天已经卖了几件（跨天自动从 0 开始） */
    /** 这个村今天已经捐了多少颗纯净绿宝石 */
    public int donated(String vkey, long today) {
        return (int) Math.floor(donated.getOrDefault(vkey + "|" + today, 0.0));
    }

    /** 记一笔捐赠，返回累计颗数 */
    public int addDonated(String vkey, long today, int n) {
        String k = vkey + "|" + today;
        int now = donated(vkey, today);
        donated.put(k, (double) (now + n));
        if (donated.size() > 4096) {
            donated.keySet().removeIf(key -> !key.endsWith("|" + today));
        }
        method_80();
        return now + n;
    }

    public int gearSold(String vkey, long today, String code) {
        String k = vkey + "|" + code;
        Long d = gearDay.get(k);
        if (d == null || d != today) return 0;
        return (int) Math.floor(gearSold.getOrDefault(k, 0.0));
    }

    /** 记一笔卖出；顺便在表太大时把旧日期的条目清掉 */
    public void gearTake(String vkey, long today, String code, int n) {
        if (n <= 0) return;
        String k = vkey + "|" + code;
        int now = gearSold(vkey, today, code);
        gearSold.put(k, (double) (now + n));
        gearDay.put(k, today);
        if (gearSold.size() > 4096) {
            gearSold.keySet().removeIf(key -> {
                Long d = gearDay.get(key);
                return d == null || d != today;
            });
            gearDay.keySet().removeIf(key -> !gearSold.containsKey(key));
        }
        method_80();
    }

    // —— 存盘 ——

    @Override
    public class_2487 method_75(class_2487 nbt, class_7225.class_7874 lookup) {
        class_2487 villages = new class_2487();
        for (Map.Entry<String, Map<String, Double>> e : stock.entrySet()) {
            class_2487 v = new class_2487();
            class_2487 items = new class_2487();
            for (Map.Entry<String, Double> it : e.getValue().entrySet()) {
                items.method_10549(it.getKey(), it.getValue());
            }
            v.method_10566("items", items);
            v.method_10544("day", day.getOrDefault(e.getKey(), 0L));
            villages.method_10566(e.getKey(), v);
        }
        nbt.method_10566("villages", villages);
        class_2487 areas = new class_2487();
        for (Map.Entry<String, Map<String, Double>> e : area.entrySet()) {
            class_2487 items = new class_2487();
            for (Map.Entry<String, Double> it : e.getValue().entrySet()) {
                items.method_10549(it.getKey(), it.getValue());
            }
            areas.method_10566(e.getKey(), items);
        }
        nbt.method_10566("areas", areas);
        class_2487 areaDays = new class_2487();
        for (Map.Entry<String, Long> e : areaDay.entrySet()) {
            areaDays.method_10544(e.getKey(), e.getValue());
        }
        nbt.method_10566("area_days", areaDays);
        class_2487 wallets = new class_2487();
        for (Map.Entry<String, Double> e : wallet.entrySet()) wallets.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("wallets", wallets);
        class_2487 walletDays = new class_2487();
        for (Map.Entry<String, Long> e : walletDay.entrySet()) walletDays.method_10544(e.getKey(), e.getValue());
        nbt.method_10566("wallet_days", walletDays);
        class_2487 budgetSpentNbt = new class_2487();
        for (Map.Entry<String, Double> e : budgetSpent.entrySet()) budgetSpentNbt.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("budget_spent", budgetSpentNbt);
        class_2487 budgetDaysNbt = new class_2487();
        for (Map.Entry<String, Long> e : budgetDay.entrySet()) budgetDaysNbt.method_10544(e.getKey(), e.getValue());
        nbt.method_10566("budget_days", budgetDaysNbt);
        class_2487 worlds = new class_2487();
        for (Map.Entry<String, Double> e : world.entrySet()) worlds.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("world_qty", worlds);
        class_2487 worldDays = new class_2487();
        for (Map.Entry<String, Long> e : worldDay.entrySet()) worldDays.method_10544(e.getKey(), e.getValue());
        nbt.method_10566("world_qty_days", worldDays);
        class_2487 pend = new class_2487();
        for (Map.Entry<String, Double> e : pending.entrySet()) pend.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("pending", pend);
        class_2487 xps = new class_2487();
        for (Map.Entry<String, Double> e : xp.entrySet()) xps.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("village_xp", xps);
        class_2487 gs = new class_2487();
        for (Map.Entry<String, Double> e : gearSold.entrySet()) gs.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("gear_sold", gs);
        class_2487 dn = new class_2487();
        for (Map.Entry<String, Double> e : donated.entrySet()) dn.method_10549(e.getKey(), e.getValue());
        nbt.method_10566("donated", dn);
        class_2487 gd = new class_2487();
        for (Map.Entry<String, Long> e : gearDay.entrySet()) gd.method_10544(e.getKey(), e.getValue());
        nbt.method_10566("gear_days", gd);
        class_2487 seen = new class_2487();
        for (Map.Entry<String, Map<String, Integer>> e : seenPrice.entrySet()) {
            class_2487 v = new class_2487();
            for (Map.Entry<String, Integer> it : e.getValue().entrySet()) {
                v.method_10544(it.getKey(), (long) it.getValue());
            }
            seen.method_10566(e.getKey(), v);
        }
        nbt.method_10566("seen_price", seen);
        class_2487 seenDays = new class_2487();
        for (Map.Entry<String, Long> e : seenDay.entrySet()) seenDays.method_10544(e.getKey(), e.getValue());
        nbt.method_10566("seen_days", seenDays);
        // 存档格式版本：v2 起"仓满"会自己回落到半仓（见 Stock.advance），
        // 老存档读进来时先做一次回落（见 fromNbt），不然那些被顶满的货永远卖不掉
        nbt.method_10544("v", 4L);   // v3：经验试过一版快曲线（读档要折回来）；v4：现在的慢曲线
        return nbt;
    }

    private static TradeStore fromNbt(class_2487 nbt, class_7225.class_7874 lookup) {
        TradeStore s = new TradeStore();
        class_2487 villages = nbt.method_10562("villages");
        for (String vkey : villages.method_10541()) {
            class_2487 v = villages.method_10562(vkey);
            class_2487 items = v.method_10562("items");
            Map<String, Double> m = new HashMap<>();
            for (String item : items.method_10541()) {
                m.put(item, items.method_10574(item));
            }
            s.stock.put(vkey, m);
            s.day.put(vkey, v.method_10537("day"));
        }
        class_2487 areas = nbt.method_10562("areas");
        for (String areaKey : areas.method_10541()) {
            class_2487 items = areas.method_10562(areaKey);
            Map<String, Double> m = new HashMap<>();
            for (String item : items.method_10541()) {
                m.put(item, items.method_10574(item));
            }
            s.area.put(areaKey, m);
        }
        class_2487 areaDays = nbt.method_10562("area_days");
        for (String areaKey : areaDays.method_10541()) {
            s.areaDay.put(areaKey, areaDays.method_10537(areaKey));
        }
        class_2487 wallets = nbt.method_10562("wallets");
        for (String vkey : wallets.method_10541()) s.wallet.put(vkey, wallets.method_10574(vkey));
        class_2487 walletDays = nbt.method_10562("wallet_days");
        for (String vkey : walletDays.method_10541()) s.walletDay.put(vkey, walletDays.method_10537(vkey));
        class_2487 budgetSpentNbt = nbt.method_10562("budget_spent");
        for (String vkey : budgetSpentNbt.method_10541()) s.budgetSpent.put(vkey, budgetSpentNbt.method_10574(vkey));
        class_2487 budgetDaysNbt = nbt.method_10562("budget_days");
        for (String vkey : budgetDaysNbt.method_10541()) s.budgetDay.put(vkey, budgetDaysNbt.method_10537(vkey));
        class_2487 worlds = nbt.method_10562("world_qty");
        for (String item : worlds.method_10541()) s.world.put(item, worlds.method_10574(item));
        class_2487 worldDays = nbt.method_10562("world_qty_days");
        for (String item : worldDays.method_10541()) s.worldDay.put(item, worldDays.method_10537(item));
        class_2487 pend = nbt.method_10562("pending");
        for (String uuid : pend.method_10541()) s.pending.put(uuid, pend.method_10574(uuid));
        class_2487 xps = nbt.method_10562("village_xp");
        for (String vkey : xps.method_10541()) s.xp.put(vkey, xps.method_10574(vkey));
        class_2487 gs = nbt.method_10562("gear_sold");
        for (String k : gs.method_10541()) s.gearSold.put(k, gs.method_10574(k));
        class_2487 dn = nbt.method_10562("donated");
        for (String k : dn.method_10541()) s.donated.put(k, dn.method_10574(k));
        class_2487 gd = nbt.method_10562("gear_days");
        for (String k : gd.method_10541()) s.gearDay.put(k, gd.method_10537(k));
        class_2487 seen = nbt.method_10562("seen_price");
        for (String vkey : seen.method_10541()) {
            class_2487 v = seen.method_10562(vkey);
            Map<String, Integer> m = new HashMap<>();
            for (String code : v.method_10541()) m.put(code, (int) v.method_10537(code));
            s.seenPrice.put(vkey, m);
        }
        class_2487 seenDays = nbt.method_10562("seen_days");
        for (String k : seenDays.method_10541()) s.seenDay.put(k, seenDays.method_10537(k));
        // 老存档（没有 v 键 → 0）：很多物品是被"看一眼就种下半仓 + 每天补货"顶到满仓的，
        // 村子其实并不缺这些货，却把收货位占死了。一次性回落到六成，之后交给 Stock.advance 自己维持。
        long ver = 0L;
        try {
            ver = nbt.method_10537("v");   // 老存档没这个键：不同版本这里可能给 0 也可能抛，兜住
        } catch (Throwable ignored) {
        }
        if (ver == 3L) {
            // 极少数存档可能在"快曲线"那一版落过盘：按等级+级内进度折回现在的慢曲线
            for (Map.Entry<String, Double> xpEntry : s.xp.entrySet()) {
                xpEntry.setValue(dev.guoke.tradepost.market.VillageLevel.remapFrom(
                        xpEntry.getValue(), dev.guoke.tradepost.market.VillageLevel.XP_V3));
            }
        }
        if (ver < 2L) {
            for (Map<String, Double> m : s.stock.values()) {
                for (Map.Entry<String, Double> it : m.entrySet()) {
                    double cap = 64;
                    try {
                        cap = Bridge.capOf(it.getKey(), Bridge.basePrice(it.getKey()), false);
                    } catch (Throwable ignored) {
                        // 物品认不出来就按 64 算，宁可少回落一点
                    }
                    if (cap > 0 && it.getValue() > cap * 0.6) it.setValue(cap * 0.6);
                }
            }
        }
        return s;
    }
}
