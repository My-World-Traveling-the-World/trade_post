package dev.guoke.tradepost.mc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.guoke.tradepost.Tradepost;
import net.minecraft.class_1645;
import net.minecraft.class_1657;
import net.minecraft.class_1725;
import net.minecraft.class_1728;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_1915;
import net.minecraft.class_1916;
import net.minecraft.class_2561;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_7225;
import net.minecraft.class_9306;

/**
 * 集市商人：借用原版村民交易那一整套机制，一行报价 = 一次真实交易。
 *
 * <p>为什么这么绕：原版村民交易是两端共用同一份报价表（{@code class_1916}）。
 * 玩家点左边第 i 行 → 客户端发 SelectTrade(i) → 服务端按第 i 条报价，从背包里
 * 把"要付的东西"搬到天平 0/1 号槽、把"能拿到的货"摆进 2 号槽；玩家点 2 号槽，
 * 原版 {@code class_1727#method_7667} 核对天平上的东西够不够，够就扣掉、把货给玩家、
 * 调 {@code merchant.method_8262(offer)}（= uses+1）。我们覆写 method_8262 记账。
 *
 * <p>所以这里最重要的一件事：**页面（买/卖、份数）一变，服务端重建报价并主动推给客户端**
 * （{@code player.method_17354}）。绝不能让客户端自己造一份报价 —— 那样两端的行号对不上，
 * 就会点第 3 行成交第 1 行，甚至客户端摆了钱服务端不知道（凭空吞绿宝石）。
 */
public class MarketMerchant extends class_1645 {

    /** 玩家 → 我们开的那份商人（一个玩家同时只可能开一个集市） */
    private static final java.util.Map<class_3222, MarketMerchant> OPEN =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 一次成交最多能摆的价钱：天平一格装 9999 颗（容量见 MerchantInventoryMixin.method_58350） */
    private static final int MAX_PAY = 9999;
    /** 与 MAX_PAY 相同：摆得下就不再凑 9 的倍数（保留名字，调用点不用改） */
    private static final int MAX_PAY_EXACT = 9999;
    /** 玩家拿到的钱也是一摞：结果格装 9999 颗 */
    private static final int MAX_INCOME = 9999;
    /** 一摞最多 64 件：结果/成本都不能超过它 */
    private static final int MAX_STACK = 64;

    /**
     * 天平右边"第二格货款"的容器槽下标：原版商人容器正好 39 格（0/1 付款、2 成品、3..38 背包）。
     * 这一格是我们自己加的，见 {@link #ensureSlot(class_1728)}。
     */
    public static final int PAYOUT_SLOT = 39;
    /** 第四格摆在哪：成品格 (220,37) 正下方 */
    public static final int PAYOUT_X = 220;   // 相对面板左上角（结果格 2 号正下方）
    public static final int PAYOUT_Y = 59;

    private static java.lang.reflect.Method ADD_SLOT;

    private final class_3222 player;
    private JsonObject root = new JsonObject();
    private int tab = 0;
    /** 最近一笔成交卖的是什么货（成交后自动续摆照它找行） */
    private class_1799 lastCost;
    private int qty = 1;
    private int syncId = -1;
    /** 上一次选中/自动选中的行号：成交后原版那格"当前报价"被清空，靠它把钱续摆回去 */
    private int lastSel = 0;
    /** 玩家最后点的那一行要什么：成交后行号会重排，靠这摞货把"刚才点的是哪一行"重新找回来 */
    private class_1799 lastClick;
    /** 玩家最后点的那一行给什么（买页用）：买页的成本是绿宝石，光看成本分不出是哪一行 */
    private class_1799 lastGet;
    private class_1916 offers = new class_1916();
    /** 与 offers 同序：这一行原始 json */
    private final java.util.List<JsonObject> rows = new java.util.ArrayList<>();
    /** 与 offers 同序：[0]=一行的件数/份量 [1]=实际单价 [2]=一次成交的份数 */
    private final java.util.List<int[]> deals = new java.util.ArrayList<>();
    /** 与 offers 同序：这一行属于哪一页（0=买 / 1=卖）。两页同屏时靠它分列 */
    private final java.util.List<Integer> rowTab = new java.util.ArrayList<>();
    /** 与 offers 同序：1 = 玩家手里不够一手（数量不足，点不动） */
    private final java.util.List<Integer> rowShort = new java.util.ArrayList<>();
    private boolean lastShort;

    public MarketMerchant(class_1657 player) {
        super(player);
        this.player = (class_3222) player;
    }

    // ---- 开 / 刷新 / 翻页 ----

    /** 开集市：按买页 1 份建报价 → 开原版商人容器 → 把完整价目表发给客户端 */
    public static boolean open(class_3222 player, JsonObject root, String json) {
        try {
            MarketMerchant m = new MarketMerchant(player);
            m.root = root;
            m.tab = 0;
            m.qty = 1;
            m.rebuild();
            m.method_8261(m.offers);
            m.method_17449(player, class_2561.method_43470("集市"), 0);
            // 必须在 method_17449 之后加第四格：那一步会先把"开界面这份全量同步"发出去，
            // 客户端那时还是 39 格；等它收到 MarketPayload 起界面时再补上第 40 格。
            ensurePayoutSlot(player);
            if (player.field_7512 != null) {
                m.syncId = player.field_7512.field_7763;
                OPEN.put(player, m);
            }
            // 发 root 而不是原始的 json：rebuild 刚把"行标记"(marks) 写进 root 里
            send(player, root.toString());
            Tradepost.debug("[tradepost] 集市开张 行数={} 页=买 份数=1", m.rows.size());
            return true;
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 开真容器失败，退回纯客户端界面", t);
            return false;
        }
    }

    /** 集市已经开着：只按新价目表重建报价（不重开容器，免得界面闪一下） */
    public static boolean refresh(class_3222 player, JsonObject root, String json) {
        MarketMerchant m = OPEN.get(player);
        if (m == null || player.field_7512 == null || player.field_7512.field_7763 != m.syncId) return false;
        m.root = root;
        m.rebuild();
        m.push(false);
        ensurePayoutSlot(player);
        send(player, root.toString());   // 同上：带上 rebuild 写的 marks
        return true;
    }

    /** 客户端翻页/换份数：重建报价并推给客户端（两边始终共用同一份报价表） */
    public static void switchPage(class_3222 player, int tab, int qty) {
        MarketMerchant m = OPEN.get(player);
        if (m == null || !(player.field_7512 instanceof class_1728 h)) return;
        if (h.field_7763 != m.syncId) return;
        m.tab = tab <= 0 ? 0 : 1;
        m.qty = Math.max(1, Math.min(MAX_STACK, qty));
        m.rebuild();
        m.push(true);
        ensurePayoutSlot(player);
        // 换页也要把 root（含 rebuild 写的 marks）重发一份，否则客户端画的还是上一页的箭头/金框。
        // 顺手去掉上一页残留的"选中行"，免得客户端把选中拨到越界的地方。
        m.root.remove("sel");
        send(player, m.root.toString());
        Tradepost.debug("[tradepost] 集市翻页 页={} 份数={} 行数={}",
                m.tab == 0 ? "买" : "卖", m.qty, m.rows.size());
    }

    private void push(boolean resetCurrent) {
        try {
            if (player.field_7512 instanceof class_1728 h) h.method_17437(this.offers);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 服务端换报价失败", t);
        }
        try {
            player.method_17354(this.syncId, this.offers, 0, 0, false, false);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 推送报价失败", t);
        }
        // 换页后把"当前这一条"指回第一行：否则原版容器还捏着上一页的报价，
        // 玩家不点行就直接点 2 号槽，会按旧价成交（我们这边已经认不出那条报价，账就记漏了）
        if (resetCurrent && !this.offers.isEmpty()) {
            try {
                if (player.field_7512 instanceof class_1728 h) h.method_7650(0);
            } catch (Throwable ignored) {
                // 指不过去也不影响玩家点行
            }
        }
    }

    private static void send(class_3222 player, String json) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                .canSend(player, dev.guoke.tradepost.market.MarketPayload.ID)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, new dev.guoke.tradepost.market.MarketPayload(json));
        }
    }

    // ---- 天平右边第二格货款 ----

    /**
     * 给商人容器补上第四格（下标 {@link #PAYOUT_SLOT}）。两端都要调，而且顺序不能反：
     *
     * <p>原版的全量同步 {@code class_1703.method_7610} 是**按发出去那份列表的长度**循环的
     * （{@code for i < list.size(): method_7611(i).method_7673(...)}）。服务端槽比客户端多一个，
     * 客户端 {@code method_7611(39)} 就是下标越界、当场崩。所以服务端只在自己的"开界面全量同步"
     * 发完之后再加（见 {@link #open}），客户端收到 MarketPayload、起界面时自己补同样下标的格子。
     *
     * <p>加槽走 {@code method_7621}（protected 的 addSlot），它会顺带把 {@code field_7764}
     * 那张"已同步栈"表也扩一格 —— 直接改 {@code field_7761} 会让同步错位。
     */
    public static void ensureSlot(class_1728 handler) {
        try {
            if (handler == null) return;
            if (handler.field_7761.size() > PAYOUT_SLOT) return;      // 已经有了
            if (ADD_SLOT == null) {
                ADD_SLOT = net.minecraft.class_1703.class.getDeclaredMethod("method_7621", class_1735.class);
                ADD_SLOT.setAccessible(true);
            }
            ADD_SLOT.invoke(handler, new dev.guoke.tradepost.market.PayoutSlot(
                    new dev.guoke.tradepost.market.PayoutSlot.Bag(), 0, PAYOUT_X, PAYOUT_Y, handler));
            Tradepost.debug("[tradepost] 天平第二格已就位（槽 {}）", PAYOUT_SLOT);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 第二格没加上：{}", t.toString());
        }
    }

    /** class_1728.field_7861（private final 的 class_1725）—— 第二格要靠它判断"成交了没有" */
    public static class_1725 merchantInv(class_1728 handler) {
        try {
            if (MERCHANT_INV == null) {
                MERCHANT_INV = class_1728.class.getDeclaredField("field_7861");
                MERCHANT_INV.setAccessible(true);
            }
            return (class_1725) MERCHANT_INV.get(handler);
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.reflect.Field MERCHANT_INV;

    /** class_1728.field_7863（private final 的 class_1915）—— 第二格成交时要用它记账 */
    public static class_1915 merchantOf(class_1728 handler) {
        try {
            if (MERCHANT == null) {
                MERCHANT = class_1728.class.getDeclaredField("field_7863");
                MERCHANT.setAccessible(true);
            }
            return (class_1915) MERCHANT.get(handler);
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.reflect.Field MERCHANT;

    /** 现在一摞绿宝石就装得下整笔货款（数量 = 价钱），不再多加第四格，界面保持原版三格 */
    public static void ensurePayoutSlot(class_3222 player) {
        try {
            if (player == null || !(player.field_7512 instanceof class_1728 h)) return;
            ensureSlot(h);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 第二格没加上：{}", t.toString());
        }
    }

    /**
     * 第二格该摆什么：这一行货款里结果格摆不下的那部分。
     *
     * <p>摆出来就能看见（跟 2 号格一样是"预览"），但拿不拿得到由 {@code PayoutSlot.method_7674} 决定：
     * 2 号格空了 = 成交了 = 解锁。买页的钱在玩家手上，所以只有卖页（成本是货）才摆。
     */
    /** 界面上的数量：原版画堆叠数不限上限，只要别是 0/负数 */
    private static int showCount(int price) {
        return Math.max(1, Math.min(9999, price));
    }

    public static class_1799 payoutFor(class_1914 offer) {
        try {
            if (offer == null) return class_1799.field_8037;
            // 一摞装得下任何价（结果格不夹上限），所以不再需要第二格
            return class_1799.field_8037;
        } catch (Throwable t) {
            return class_1799.field_8037;
        }
    }

    /**
     * 直接写第二格（服务端是权威，客户端会跟着同步）。
     * 写之前先把上一笔没取走的钱塞给玩家 —— 否则玩家卖两次、第二次覆盖第一次，钱就没了。
     */
    public static void setPayout(class_3222 player, class_1799 stack) {
        try {
            if (player == null || !(player.field_7512 instanceof class_1728 h)) return;
            if (h.field_7761.size() <= PAYOUT_SLOT) return;   // 没有第二格（当前版本就是没有）
            class_1735 slot = h.method_7611(PAYOUT_SLOT);
            if (slot == null) return;
            class_1799 old = slot.method_7677();
            if (old != null && !old.method_7960()) {
                dev.guoke.tradepost.mc.Bridge.giveOrDrop(player, old.method_7972());
                Tradepost.debug("[tradepost] 第二格旧货款 {} × {} 先塞进背包",
                        old.method_7947(), old.method_7909());
            }
            slot.method_53512(stack == null ? class_1799.field_8037 : stack);
            Tradepost.debug("[tradepost] 第二格写入 {}", stack == null || stack.method_7960()
                    ? "空" : stack.method_7947() + " × " + stack.method_7909());
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 第二格写入失败", t);
        }
    }

    /** 玩家点了第二格：把这笔货款收进背包（槽里的东西是服务端权威，客户端只是画了张虚影） */
    public static void collect(class_3222 player) {
        try {
            if (player == null || !(player.field_7512 instanceof class_1728 h)) return;
            if (h.field_7761.size() <= PAYOUT_SLOT) {
                Tradepost.debug("[tradepost] 收第二格：这一侧根本没有第二格");
                return;
            }
            class_1735 slot = h.method_7611(PAYOUT_SLOT);
            class_1799 st = slot == null ? null : slot.method_7677();
            if (st == null || st.method_7960()) {
                Tradepost.debug("[tradepost] 收第二格：空的（这一单两格就装得下）");
                return;
            }
            Bridge.giveOrDrop(player, st.method_7972());
            slot.method_53512(class_1799.field_8037);
            player.field_7512.method_7623();      // 让客户端立刻看到格子空掉（sendContentUpdates）
            Tradepost.debug("[tradepost] 收第二格：{} × {} 进背包", st.method_7947(), st.method_7909());
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 收第二格失败", t);
        }
    }

    // ---- 成交回调：原版扣完钱、给完货之后调这里（uses+1） ----

    @Override
    public void method_8262(class_1914 offer) {
        super.method_8262(offer);
        try {
            int i = indexOf(offer);
            if (i < 0 || i >= this.rows.size() || i >= this.deals.size()) {
                Tradepost.LOGGER.warn("[tradepost] 集市成交了但认不出是哪一条报价（不记账）");
                return;
            }
            int[] d = this.deals.get(i);
            // 两页同屏：这一行属于哪一页，就以哪一页的身份记账（不能用 this.tab，它只是"上次翻到哪页"）
            int rowT = (i < this.rowTab.size() && this.rowTab.get(i) != null) ? this.rowTab.get(i) : this.tab;
            this.tab = rowT;
            if (rowT == 1 && offer.method_57556() != null) {
                this.lastCost = offer.method_57556().comp_2427().method_7972();
            }
            Bridge.marketTradeDone(this.player, rowT, this.rows.get(i), d[0], d[1], d[2]);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 集市成交记账失败", t);
        }
        repricing();
    }

    /**
     * 成交之后按最新库存/银库重算整页报价。
     *
     * <p>不重算的后果是实测出来的：卖 236 一颗的下界合金锭，村子银库只有 943（够 3 次），
     * 界面上那条"可成交 3 次"却因为没人检查 {@code offer.method_8255()}（售罄）而形同虚设，
     * 一口气卖了 14 笔、给出去 3304 颗，银库被夹到 0 —— 剩下的钱是凭空印出来的。
     * 现在每笔成交都重算：库存涨了、银库薄了、价变了，下一笔立刻照新的来。
     */
    private void repricing() {
        try {
            if (!(this.player.field_7512 instanceof class_1728 h) || h.field_7763 != this.syncId) return;
            // 玩家刚才选中的是哪一行：重算后要指回同一行，否则点一次买一次就被弹回第一行
            int keep = -1;
            try {
                class_1725 mi = merchantInv(h);
                class_1914 cur = mi == null ? null : mi.method_7642();
                if (cur != null) keep = indexOf(cur);
            } catch (Throwable ignored) {
            }
            // 成交后原版那格"当前报价"是空的（点一次就清一次），只靠它就一路 fallback 到第 0 行 ——
            // 实测点了第 4 行买生肉，成交后钱却按第 0 行（羊毛，3 颗）摆回去，玩家还得再点一次。
            // 用"刚才点的那一摞货"在重建后的列表里重新找行，才能真正接着买同一件东西。
            // 卖页认"要的货"，买页认"给的货"：买页的成本全是绿宝石，拿成本去找只会找到第 0 行
            if (keep < 0) keep = this.tab == 1 ? indexOfStack(this.lastClick) : indexOfResult(this.lastGet);
            String json = Bridge.boardJson(this.player);
            if (json == null) return;
            this.root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            this.rebuild();
            boolean keepOk = keep >= 0 && keep < this.offers.size();
            int sel = keepOk ? keep : 0;
            this.lastSel = sel;
            this.push(!keepOk);
            if (keepOk) {
                try {
                    h.method_7650(keep);
                } catch (Throwable ignored) {
                }
            }
            // 报价格式表也带一句"服务端现在指着第几行"：只改服务端的选中行会让两端错位 ——
            // 客户端按自己的行号在成品格画出 A，服务端按自己的行号发货给 B（实测：点可可豆、
            // 到手却是书）。客户端收到后把选中行也拨到同一行，两边才算真的对上。
            this.root.addProperty("sel", sel);
            // 银库/等级/经验都变了：把新价目表也发一份，客户端只 setPayload 刷新数字，不重开界面
            send(this.player, this.root.toString());
            ensurePayoutSlot(this.player);
            // 续摆推迟到本 tick 结束（见 tickPending）：网络一卡，客户端会把同一格点两下，
            // 两个点击包在同一 tick 里先后落地。要是成交后立刻把货/钱摆回去，第二个包就能
            // 对着新摆上的东西再结一次账 —— 玩家看到"卖了两份，货只少一份"。
            // 推迟到 tick 末尾，重复的包全部落空（天平还是空的），只有真正的下一次点击才算数。
            PENDING.add(this);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 成交后重算报价失败", t);
        }
    }

    /**
     * 成交完、天平空了，就照着刚才那一行把钱再摆回去。
     *
     * <p>不摆的话"连着买"每次都得重新点行（原版成交会把成本格清空）。只有两个成本格都空着、
     * 玩家自己没往里放东西时才动手；钱还是从背包扣，关界面原版会把成本格的东西退回背包。
     */
    /** 天平 0/1 两格上"要的就是这一摞"的件数合计 */
    private static int onScale(class_1725 mi, class_1799 want) {
        int n = 0;
        for (int i = 0; i < 2; i++) {
            class_1799 st = mi.method_5438(i);
            if (!st.method_7960() && Bridge.sameFamily(st, want)) n += st.method_7947();
        }
        return n;
    }

    /**
     * 天平上放了货、可当前选的行并不收这堆货时，自动找出"收这堆货"的那一行。
     *
     * <p>附加功能：不用先点列表，手动拖或 shift 把货放上天平就能看到价钱。
     * 玩家手点的行如果确实收这堆货，这里完全不动 —— 原来的操作方法一切照旧。
     *
     * @return 该改选哪一行；-1 = 不用动
     */
    public int autoRowForScale(class_1725 mi, int current) {
        if (this.tab != 1) return -1;                       // 只有卖页认货（买页的成本都是钱，认不出来）
        if (current >= 0 && current < this.offers.size()) {
            class_1914 cur = this.offers.get(current);
            if (cur != null && !cur.method_8255() && matchesScale(mi, cur)) return -1;   // 玩家选的就是对的
        }
        for (int s = 0; s < 2; s++) {
            class_1799 st = mi.method_5438(s);
            if (st == null || st.method_7960()) continue;
            int idx = rowForStack(st);
            if (idx < 0) idx = looseRowForScale(st);         // 装备成色随行情漂，严格比会找不到
            if (idx < 0) continue;
            class_1914 offer = this.offers.get(idx);
            if (offer == null || offer.method_8255()) continue;
            if (!matchesScale(mi, offer)) continue;          // 件数不够就别点亮，免得只付钱不扣货
            Tradepost.LOGGER.info("[tradepost] 自动认货：天平上的 {} 认到第 {} 行", Bridge.describe(st), idx);
            return idx;
        }
        return -1;
    }

    /** 天平上的货够不够这一行要的量 */
    private static boolean matchesScale(class_1725 mi, class_1914 offer) {
        try {
            class_9306 cost = offer.method_57556();
            if (cost == null) return false;
            class_1799 want = cost.comp_2427();
            if (want == null || want.method_7960()) return false;
            return onScale(mi, want) >= Math.max(1, cost.comp_2425());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 按"同一族"再找一遍行（不比耐久/回收价：行情一漂，严格比就找不到自己刚摆上去的货） */
    private int looseRowForScale(class_1799 st) {
        for (int i = 0; i < this.offers.size(); i++) {
            try {
                class_9306 c = this.offers.get(i).method_57556();
                class_1799 want = c == null ? null : c.comp_2427();
                if (want != null && Bridge.sameFamily(st, want)) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /** 玩家点了第 index 行：记下这一行要的货，成交后照着它把行找回来 */
    public void noteClick(int index) {
        if (index < 0) return;
        this.lastSel = index;
        try {
            if (index >= this.offers.size()) return;
            class_9306 c = this.offers.get(index).method_57556();
            this.lastClick = c == null ? null : c.comp_2427().method_7972();
            class_1799 got = this.offers.get(index).method_8250();
            this.lastGet = got == null || got.method_7960() ? null : got.method_7972();
        } catch (Throwable ignored) {
        }
    }

    /** 在现在的报价列表里找"给的就是这一摞货"的行（买页；找不到返回 -1） */
    public int indexOfResult(class_1799 st) {
        if (st == null) return -1;
        for (int i = 0; i < this.offers.size(); i++) {
            try {
                class_1799 got = this.offers.get(i).method_8250();
                if (got != null && Bridge.sameKind(got, st)) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /** 在现在的报价列表里找"要的就是这一摞货"的行（找不到返回 -1） */
    public int indexOfStack(class_1799 st) {
        if (st == null) return -1;
        for (int i = 0; i < this.offers.size(); i++) {
            try {
                class_9306 c = this.offers.get(i).method_57556();
                if (c != null && Bridge.sameKind(c.comp_2427(), st)) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private void restageIfPossible() {
        if (this.tab != 0) return;                       // 只有买页要预摆钱
        try {
            if (!(this.player.field_7512 instanceof class_1728 h)) return;
            class_1725 mi = merchantInv(h);
            if (mi == null) return;
            for (int i = 0; i < 2; i++) {
                class_1799 s = mi.method_5438(i);
                if (!s.method_7960()) {
                    Tradepost.LOGGER.info("[tradepost] 买页续摆跳过：天平 {} 上有玩家自己放的东西", i);
                    return;                                  // 玩家自己摆了东西：别插手
                }
            }
            class_1914 cur = mi.method_7642();
            boolean fallback = false;
            if (cur == null && this.lastSel >= 0 && this.lastSel < this.offers.size()) {
                // 成交后重建报价，原版那格"当前报价"会被清空；用刚才选中的那一行顶上，
                // 否则钱不会自己摆回去，玩家得再点一下行才知道点哪 —— 这就是"不能连续买"
                cur = this.offers.get(this.lastSel);
                h.method_7650(this.lastSel);
                fallback = true;
            }
            if (cur == null) {
                Tradepost.LOGGER.info("[tradepost] 买页续摆跳过：没有可用的行（上次行 {}，现在共 {} 行）",
                        this.lastSel, this.offers.size());
                return;
            }
            if (cur.method_8255()) {
                Tradepost.LOGGER.info("[tradepost] 买页续摆跳过：行 {} 已售罄（村里这件没货了）", this.lastSel);
                return;
            }
            int need = dev.guoke.tradepost.market.Pay.price(cur, true);
            if (need <= 0) {
                Tradepost.LOGGER.info("[tradepost] 买页续摆跳过：行 {} 算不出价钱", this.lastSel);
                return;
            }
            if (!canPay(need)) {
                Tradepost.LOGGER.info("[tradepost] 买页续摆跳过：钱包不够（要 {}）", need);
                return;
            }
            dev.guoke.tradepost.market.Pay.stage(this.player, mi, need);
            ensurePayoutSlot(this.player);
            Tradepost.LOGGER.info("[tradepost] 买页续摆 行{} 价{} 天平={} 靠上次行={}",
                    this.lastSel, need, mi.method_5438(0).method_7947(), fallback);
        } catch (Throwable t) {
            Tradepost.LOGGER.info("[tradepost] 买页续摆失败（不影响手点）：{}", t.toString());
        }
    }

    /**
     * 从玩家背包往天平某槽里搬 want 件"和 ref 同一种"的货，返回搬了多少。
     * 摆货（mixin 点行）与成交后自动续摆都走这一份实现，免得两边认货的口径跑偏。
     */
    public static int stageInto(class_1728 h, int target, class_1799 ref, int want) {
        if (h == null || want <= 0 || ref == null || ref.method_7960()) return 0;
        class_1725 mi = merchantInv(h);
        if (mi == null) return 0;
        int have = 0;
        class_1799 cur = mi.method_5438(target);
        if (!cur.method_7960()) {
            if (!Bridge.sameKind(cur, ref)) return 0;
            have = cur.method_7947();
        }
        int room = 64 - have;
        int take = 0;
        class_1799 merged = cur.method_7960() ? class_1799.field_8037 : cur.method_7972();
        for (int i = 3; i < 39 && take < want && room > 0; i++) {
            class_1735 slot = h.method_7611(i);
            if (slot == null) continue;
            class_1799 st = slot.method_7677();
            if (!Bridge.sameKind(st, ref)) continue;
            int n = Math.min(Math.min(st.method_7947(), want - take), room);
            class_1799 part;
            if (n >= st.method_7947()) {
                part = st;                                  // 整摞搬走：组件原样跟着走
                slot.method_53512(class_1799.field_8037);
            } else {
                part = st.method_7972();
                part.method_7939(n);
                class_1799 left = st.method_7972();
                left.method_7939(st.method_7947() - n);
                slot.method_53512(left);
            }
            if (merged.method_7960()) merged = part;
            else merged.method_7939(merged.method_7947() + n);
            take += n;
            room -= n;
        }
        if (take > 0) mi.method_5447(target, merged);
        return take;
    }

    /**
     * 卖页成交完、天平空了，就把这一行的货再摆一批上去 —— 玩家可以连点结果格连着卖，
     * 不用每卖一次都回头点一遍列表。背包里没货了自然什么都不做。
     */
    private void restageSell() {
        try {
            if (this.tab != 1) return;
            if (!(this.player.field_7512 instanceof class_1728 h)) return;
            class_1725 mi = merchantInv(h);
            if (mi == null) return;
            boolean scaleEmpty = true;
            for (int i = 0; i < 2; i++) {
                if (!mi.method_5438(i).method_7960()) scaleEmpty = false;
            }
            // 按"刚才卖掉的那摞货"重新找行：这一行可能因为售罄/村子收满已经从列表里消失，
            // 直接拿旧下标会摆成别的货
            class_1799 sold = this.lastCost;
            if (sold == null || sold.method_7960()) return;
            int idx = rowForStack(sold);
            if (idx < 0) {
                Tradepost.LOGGER.info("[tradepost] 卖页续摆跳过：列表里已经没有要 {} 的行", Bridge.shortId(Bridge.itemId(sold)));
                return;
            }
            class_1914 offer = this.offers.get(idx);
            if (offer != null && offer.method_8255()) {         // 这行已售罄：别再往天平上摆货
                Tradepost.LOGGER.info("[tradepost] 卖页续摆跳过：第 {} 行今天收满了", idx);
                return;
            }
            class_9306 cost = offer == null ? null : offer.method_57556();
            class_1799 want = cost == null ? null : cost.comp_2427();
            if (want == null || want.method_7960()) return;
            int n = Math.min(64, Math.max(1, cost.comp_2425()));
            // 天平上已经有这一行的货（玩家把整摞都放上去了）：原版成交会把"当前报价"清空，
            // 结果格跟着空掉 —— 玩家看着天平上满满一堆货，怎么点都没反应。这里只把行选回来，
            // 结果格就重新出现，接着点就能一份一份连着卖，不必每卖一次都重摆一遍。
            if (!scaleEmpty) {
                int on = onScale(mi, want);
                if (on >= n) {
                    h.method_7650(idx);
                    h.method_7623();
                    Tradepost.LOGGER.info("[tradepost] 卖页续摆：天平上还有 {} 件，选回第 {} 行接着卖", on, idx);
                }
                return;
            }
            int a = stageInto(h, 0, want, n);
            int moved = a + (a < n ? stageInto(h, 1, want, n - a) : 0);
            if (moved <= 0) {
                Tradepost.LOGGER.info("[tradepost] 卖页续摆跳过：背包里没有要 {}×{} 了", Bridge.shortId(Bridge.itemId(want)), n);
                return;
            }
            h.method_7650(idx);
            h.method_7623();                                 // 立刻同步格子，别等下一 tick
        } catch (Throwable ignored) {
            // 续摆失败不影响玩家手点
        }
    }

    /** 本 tick 结束后要续摆的集市（见 repricing 里的说明） */
    private static final java.util.List<MarketMerchant> PENDING = new java.util.ArrayList<>();

    /** 每 tick 末尾把所有待续摆的集市处理一遍（由 Tradepost 的 END_SERVER_TICK 调用） */
    public static void tickPending() {
        if (PENDING.isEmpty()) return;
        java.util.List<MarketMerchant> batch = new java.util.ArrayList<>(PENDING);
        PENDING.clear();
        for (MarketMerchant m : batch) {
            try {
                if (!(m.player.field_7512 instanceof class_1728 h) || h.field_7763 != m.syncId) continue;
                m.restageIfPossible();                       // 买页：把钱再摆回去
                m.restageSell();                             // 卖页：把货再摆一批上去
            } catch (Throwable t) {
                Tradepost.debug("[tradepost] 续摆失败（不影响手点）：{}", t.toString());
            }
        }
    }

    /** 当前是不是收购（卖）页 */
    public boolean selling() {
        return this.tab == 1;
    }

    /**
     * 第 i 条**真列出来**的报价对应的那行 json（涨跌箭头 / 特收金框要用）。
     * 注意下标是"列出来的顺序"，不是 boardJson 里那页的原始下标 ——
     * 有些行造不出报价会被跳过，界面只认这里的顺序。
     */
    public com.google.gson.JsonObject rowAt(int i) {
        if (i < 0 || i >= this.rows.size()) return null;
        return this.rows.get(i);
    }

    /** 这一摞货该卖给第几行（认 id + 附魔/品质/耐久档位）；-1 = 这页不收它 */
    public int rowForStack(class_1799 st) {
        if (st == null || st.method_7960()) return -1;
        for (int i = 0; i < this.offers.size(); i++) {
            class_1914 o = this.offers.get(i);
            if (o == null || o.method_57556() == null) continue;
            class_1799 want = o.method_57556().comp_2427();
            if (want == null || want.method_7960()) continue;
            if (Bridge.sameKind(st, want)) return i;
        }
        return -1;
    }

    /** 这个村现在掏得出这么多钱吗（收购页按钱算；买页是玩家付钱，不用问村子） */
    public boolean canPay(int price) {
        if (price <= 0) return true;
        Bridge.Ctx c = ctx();
        if (c == null) return false;
        return c.store.wallet(c.vkey, c.walletCap(), c.day) >= price;
    }

    private int indexOf(class_1914 offer) {
        for (int i = 0; i < this.offers.size(); i++) {
            if (this.offers.get(i) == offer) return i;      // 认实例不认 equals：同一行重复不会有歧义
        }
        return -1;
    }

    // ---- 价目表 → 原版报价 ----

    private void rebuild() {
        class_1916 list = new class_1916();
        this.rows.clear();
        this.deals.clear();
        this.rowTab.clear();
        this.rowShort.clear();
        Bridge.Ctx c = ctx();
        // 两页一起下发：界面是左右两列，买/卖各占一列，行序 = 先买页后卖页
        for (int tab = 0; tab <= 1; tab++) {
            JsonArray arr = array(this.root, tab == 0 ? "buy" : "sell");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject r = object(arr, i);
                try {
                    if (add(list, r, c, tab)) {
                        this.rows.add(r);
                        this.rowTab.add(tab);
                        this.rowShort.add(lastShort ? 1 : 0);
                    }
                } catch (Throwable t) {
                    Tradepost.LOGGER.warn("[tradepost] 集市这一行报价坏掉了：" + r, t);
                }
            }
        }
        this.offers = list;
        // 行上的小标记（客户端要画）：与 offers 同序，每位 = 箭头(0 无/1 跌/2 涨) + 金框(4)。
        // 客户端只有这一份 root，拿不到 MarketMerchant（那是服务端对象），所以标记随价目表一起发。
        com.google.gson.JsonArray marks = new com.google.gson.JsonArray();
        for (JsonObject r : this.rows) {
            int tr = r.has("tr") ? r.get("tr").getAsInt() : 0;
            boolean special = r.has("special") && r.get("special").getAsInt() != 0;
            marks.add((tr > 0 ? 2 : (tr < 0 ? 1 : 0)) + (special ? 4 : 0));
        }
        this.root.add("marks", marks);
        // 每行属于哪一页：客户端靠它把行分到左右两列（与 offers 同序）
        com.google.gson.JsonArray tabs = new com.google.gson.JsonArray();
        for (Integer t : this.rowTab) tabs.add(t == null ? 0 : t);
        this.root.add("tabs", tabs);
        com.google.gson.JsonArray sh = new com.google.gson.JsonArray();
        for (Integer sv : this.rowShort) sh.add(sv == null ? 0 : sv);
        this.root.add("shorts", sh);
        // 取证用 INFO：一行人话说明这一页到底列了哪几行（附魔书/装备收不收，一眼能看出来）
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < this.rows.size() && i < 12; i++) {
            if (i > 0) ids.append(" | ");
            ids.append(string(this.rows.get(i), "id"));
        }
        if (this.rows.size() > 12) ids.append(" …共").append(this.rows.size());
        Tradepost.LOGGER.info("[tradepost] 合并价目表 {} 条（买/卖两页）：{}", this.rows.size(), ids);
        if (Tradepost.LOGGER.isInfoEnabled()) {
            for (int i = 0; i < this.rows.size(); i++) {
                int[] d = this.deals.get(i);
                Tradepost.debug("[tradepost] 报价[{}] {} 每份{}件 单价{} 一次{}份 可成交{}次",
                        i, string(this.rows.get(i), "id"), d[0], d[1], d[2], d[3]);
            }
        }
    }

    /** 一行 json → 一条报价；返回 false 表示这行现在做不了（没货/收满/银库没钱），干脆不列 */
    private boolean add(class_1916 list, JsonObject r, Bridge.Ctx c, int tab) {
        this.lastShort = false;
        String id = string(r, "id");
        // 纯净绿宝石不再走普通货架：它改由"汇兑"专柜处理（90 买 / 70 卖）
        if (id != null && id.startsWith("pureemeraldtools:pure_emerald")) return false;
        if (id.isEmpty()) return false;
        class_7225.class_7874 lk = lookup();
        if (tab == 0) {
            int per = Math.max(1, integer(r, "count", 1));
            int stock = Math.max(0, integer(r, "stock", 0));
            int unit = buyPrice(integer(r, "pay", 1));
            // 一件货的堆叠上限不一定是 64（鸡蛋 16、药水 1、装备 1）：
            // 结果格塞不下的数量原版给不出来，先按这货自己的上限算
            class_1799 probe = dev.guoke.tradepost.market.Goods.row(r, per, lk);
            int stackCap = probe.method_7960() ? MAX_STACK : Math.max(1, probe.method_7914());
            int avail = Math.min(stock / per, Math.min(MAX_PAY / unit, stackCap / per));
            // 装备/附魔书/药水都不能叠：一次只成交一份，多买就多按几次（免得给出一摞 64 把剑）
            if (r.has("base")) avail = Math.min(avail, 1);
            // 货架空了的行**不删**：挂红叉留在原位（跟原版村民交易一个样）。
            // 删行的后果是列表缩水、行号乱跳，玩家只能关界面重开（m16382）。
            boolean soldOut = avail <= 0;
            if (soldOut) avail = 1;     // 只用来造一条"画得出来但点不动"的行
            int units = Math.min(this.qty, avail);
            int pay = unit * units;
            class_1799 item = units == 1 ? probe : dev.guoke.tradepost.market.Goods.row(r, per * units, lk);
            // 钱栈的"数量"直接写成价钱：原版画第二成本格/成品格时照着 count 画，不看上限，
            // 所以界面上就是一颗绿宝石 + 281 这样的数字（不用自己叠文字，也不会被块/颗搞晕）。
            // 放第二成本格是因为第一成本格走 method_19272，会被夹到堆叠上限 64。
            // 列表里的钱永远是"一颗绿宝石"（价钱由界面自己写数字，不摆绿宝石块）；
            // 真钱由 Pay.stage 在天平上摆（块 + 散钱），跟这一格无关
            // 列表上的价：一摞绿宝石，数量就是价格，原版自己把数字画出来。
            // 原版会把第一成本格的数量夹到 64，我们用自己的报价标记（负 xp）让 OfferClampMixin 放过它。
            class_1799 c1 = dev.guoke.tradepost.market.Pay.emeralds(showCount(pay));
            class_1799 c2 = class_1799.field_8037;
            if (item.method_7960() || c1.method_7960()) return false;
            class_1914 built = offer(c1, c2, item, avail / units, pay);
            if (built == null) return false;
            if (soldOut) markSoldOut(built);   // 红叉：看得见、点不动（村子这件货卖光了）
            list.add(built);
            this.deals.add(new int[]{per, unit, units, avail / units});
            return true;
        }
        boolean gearRow = r.has("gear");            // 装备回收行：不进库存池，走按天额度
        // 附魔书的行号是 book:附魔|等级，库存仍然记在"附魔书"这个物品上
        String stockId = id.startsWith("book:") ? "minecraft:enchanted_book" : id;
        int lot = Math.max(1, integer(r, "lot", 1));
        int lots = Math.max(0, integer(r, "lots", 0));
        int unit = sellPrice(integer(r, "price", 1));
        // 同理：这货本身堆叠上限 16 的话，一次最多只能收 16 件（原版槽位放不下更多）
        class_1799 sellProbe = dev.guoke.tradepost.market.Goods.row(r, lot, lk);
        int sellCap = sellProbe.method_7960() ? MAX_STACK : Math.max(1, sellProbe.method_7914());
        int structural = Math.min(lots, Math.min(MAX_INCOME / unit, sellCap / lot));
        boolean shortQty = lots <= 0;   // 玩家手里不够一份：保留，别当成"卖不了"
        this.lastShort = shortQty;
        if (structural <= 0 && !shortQty) return false;   // 摆不出来（超堆叠/钱格）才删
        int avail = structural;
        if (c != null && !gearRow) {
            // 村子收得下、也付得起，这一行才列出来（不然玩家白跑一趟）
            double base = Bridge.basePrice(stockId);
            double cap = Bridge.capOf(stockId, base, c.station);
            int room = (int) Math.max(0, Math.floor(cap - c.store.get(c.vkey, stockId, cap, c.day)));
            double purse = c.store.wallet(c.vkey, c.walletCap(), c.day)
                    + c.store.credit(player.method_5667().toString());
            avail = Math.min(avail, Math.min(room / lot, (int) Math.floor(purse / unit)));
            // 每日收购预算：只压贵货（单价 ≥ BUDGET_MIN_UNIT 的行）。
            // 便宜货只看库存与银库 —— 否则「卖三块下界合金 → 一整天连一本书都收不了」。
            if (unit >= BUDGET_MIN_UNIT) {
                avail = Math.min(avail, budgetUnits(c.store.budgetLeft(c.vkey, c.walletCap(), c.day), unit));
            }
        } else if (c != null) {
            // 装备回收只看银库够不够付，不占本村库存（收来的装备另作它用）
            double purse = c.store.wallet(c.vkey, c.walletCap(), c.day)
                    + c.store.credit(player.method_5667().toString());
            avail = Math.min(avail, (int) Math.floor(purse / unit));
            if (unit >= BUDGET_MIN_UNIT) {
                avail = Math.min(avail, budgetUnits(c.store.budgetLeft(c.vkey, c.walletCap(), c.day), unit));
            }
        }
        // 村子这一刻收不下（库存到顶/银库没钱）：**不删行**，改成"售罄"红叉留在原位。
        // 删行的后果是玩家每次成交都看到列表缩水、选中行乱跳，只能关掉界面重开（m16382）。
        boolean soldOut = avail <= 0 && !shortQty;   // 缺数量不算"售罄"：村子愿意收，是你货不够
        if (soldOut || shortQty) {
            // 玩家手里真有这货、村子却不收：留个 INFO 便于查为什么（缺数量不记，太吵）
            if (soldOut && c != null && lots > 0) {
                double cp = Bridge.capOf(stockId, Bridge.basePrice(stockId), c.station);
                double hv = c.store.get(c.vkey, stockId, cp, c.day);
                double pu = c.store.wallet(c.vkey, c.walletCap(), c.day)
                        + c.store.credit(player.method_5667().toString());
                Tradepost.LOGGER.info("[tradepost] 卖行被丢弃 {} 单价{} 一次{}件 你有{}件 库存{}/上限{} 银库{} 今日预算剩{}",
                        id, unit, lot, lots * lot, String.format(java.util.Locale.ROOT, "%.1f", hv), (int) cp, (int) pu,
                        unit >= BUDGET_MIN_UNIT
                                ? String.format(java.util.Locale.ROOT, "%.0f", c.store.budgetLeft(c.vkey, c.walletCap(), c.day))
                                : "不受限");
            }
            avail = 1;      // 只用来造一条"摆得出来但不能成交"的行（缺数量不画红叉）
        }
        int units = Math.min(this.qty, avail);
        int pay = unit * units;
        int sold = lot * units;
        class_1799 goods = units == 1 ? sellProbe : dev.guoke.tradepost.market.Goods.row(r, sold, lk);
        // 成品栈就是给玩家的钱：一摞绿宝石，数量 = 货款。原版照着数量画字，
        // 所以列表和天平上都只出现"一颗绿宝石 + 数字"，不再有绿宝石块
        // 成品格就是一摞绿宝石，数量 = 货款（结果格不夹上限，原版照数画）
        class_1799 money = dev.guoke.tradepost.market.Pay.emeralds(showCount(pay));
        if (goods.method_7960() || money.method_7960()) {
            // 静默丢弃最难查：货栈造不出来（id 认不出/组件写坏）整行就没了，玩家只看到"行不见了"
            Tradepost.LOGGER.info("[tradepost] 卖行造不出货 {} lot={} sold={} units={} 货栈空={} 钱栈空={}",
                    id, lot, sold, units, goods.method_7960(), money.method_7960());
            return false;
        }
        class_1914 built = offer(goods, class_1799.field_8037, money, avail / units, pay);
        if (built == null) {
            Tradepost.LOGGER.info("[tradepost] 卖行报价造不出来 {} lot={} sold={} units={} pay={}", id, lot, sold, units, pay);
            return false;
        }
        if (soldOut || shortQty) markSoldOut(built);   // 红叉：看得见、点不动（收满/缺钱/数量不足）
        list.add(built);
        this.deals.add(new int[]{lot, unit, units, avail / units});
        if (Tradepost.LOGGER.isInfoEnabled()) {
            Tradepost.debug("[tradepost] 卖行 {} 牌价{} 单价{} 一次{}件 份数{} 共{} 钱格{}颗",
                    id, integer(r, "price", 1), unit, lot, units, pay, money.method_7947());
        }
        return true;
    }

    /**
     * 造一条报价。
     *
     * <p>坑：{@code class_1914(ItemCost, Optional, ItemStack, int, int, float)} 这个 6 参构造，
     * 第 4 个 int 是 <b>maxUses</b>（第 5 个是每次交易给商人的经验），uses 写死 0。
     * 之前按 (uses, maxUses) 的顺序传，等于每一行的 maxUses 都是 0 →
     * 整页报价全被判成"已售罄"，画红叉、点了没反应。
     *
     * <p>第 5 个 int（原版叫 xp，只用来给商人加经验，我们不用那套经验）塞<b>这一行的真实总价</b>：
     * 结果格只放得下一摞，价钱过 64 只能摊成绿宝石块，零头在槽里根本显示不出来，
     * 客户端要靠这个数才知道"本行到底多少钱"——鼠标悬停时写准确数字用。
     * 原版 {@code class_1914.field_48371} 的网络编码里有这一项，客户端读 {@code method_19279()} 就拿到。
     */
    /**
     * 把一件货包成报价里的"成本"。
     *
     * <p>原版 {@code new class_9306(item, count)} 只记住 <b>物品类型 + 数量</b>，组件一个不留：
     * 附魔书于是变成白板书（列表里没词条、卖页比对不上，怎么点都"搬上 0"），
     * 酒变成没品质的裸瓶（列表没有"典藏"字样、价钱也按裸瓶算）。
     * 这里改用带"确切物品栈"的那个构造器，把整份栈原样带进报价 ——
     * 列表图标、悬停提示、天平比对都看这一份。
     */
    private static class_9306 cost(class_1799 st) {
        if (st == null || st.method_7960()) return null;
        try {
            return new class_9306(st.method_7909().method_40131(), st.method_7947(),
                    net.minecraft.class_9329.field_49597, st.method_7972());
        } catch (Throwable t) {
            // 兜底退回只有类型+数量的老写法，至少这一行还能用
            return new class_9306(st.method_7909(), st.method_7947());
        }
    }

    /** MerchantOffer 里的 uses / maxUses（intermediary 名，运行时按字段名反射拿） */
    private static java.lang.reflect.Field F_USES, F_MAX_USES;
    static {
        try {
            F_USES = class_1914.class.getDeclaredField("field_9147");
            F_USES.setAccessible(true);
            F_MAX_USES = class_1914.class.getDeclaredField("field_9144");
            F_MAX_USES.setAccessible(true);
        } catch (Throwable t) {
            Tradepost.LOGGER.warn("[tradepost] 拿不到报价的 uses 字段，售罄行只能当正常行显示", t);
        }
    }

    /** 把这一行标成"已售罄"：原版 method_8255 判 uses >= maxUses，列表会画红叉 */
    private static void markSoldOut(class_1914 offer) {
        try {
            if (F_USES != null && F_MAX_USES != null) F_USES.setInt(offer, F_MAX_USES.getInt(offer));
        } catch (Throwable ignored) {
            // 标不上就算了，服务端结算处还有一道拦截
        }
    }
    private static class_1914 offer(class_1799 cost, class_1799 cost2, class_1799 result,
                                    int maxUses, int totalPrice) {
        class_9306 c1 = cost(cost);
        if (c1 == null) return null;
        class_9306 c2 = cost(cost2);
        java.util.Optional<class_9306> second = c2 == null
                ? java.util.Optional.empty() : java.util.Optional.of(c2);
        // xp 通道写**负数**：负号是"这是我们自己的报价"的标记（原版永远不会是负的），
        // 绝对值就是本行真实总价。这样列表/天平/结算三处都能不靠类型判断认出它
        return new class_1914(c1, second, result, Math.max(1, maxUses), -Math.max(1, totalPrice), 1.0f);
    }

    /**
     * 单价 ≥ 这个数的行才吃每日收购预算（酒、附魔书、装备、下界合金这类「一件顶一天」的货）。
     * 20 颗以下（小麦、羊毛、杂货、书）只受库存与银库约束。
     */
    private static final int BUDGET_MIN_UNIT = 100;

    /**
     * 每日收购预算能成交几件。
     *
     * <p>额度不够一件时保底给 1 件：169 颗的下界合金在银库 500 的小村按 10% 预算只有 50 颗，
     * 要是直接判 0，玩家看到的就是「这村不收下界合金」——真正的硬上限仍是银库余额，
     * 这里放宽一件不会让他多赚，只会让他能卖。
     */
    private static int budgetUnits(double budget, int unit) {
        if (budget <= 0) return 0;
        if (budget == Double.MAX_VALUE) return Integer.MAX_VALUE;   // rate<=0：闸关掉了
        int n = (int) Math.floor(budget / Math.max(1, unit));
        return Math.max(1, n);
    }

    /**
     * 买入单价：天平一摞就摆得下（单格 9999），价多少摆多少，不再凑 9 的倍数。
     *
     * <p>以前上限 639（64 块 + 63 颗），超过就只能取整成 9 的倍数；
     * 单格放开到 9999 之后这条限制没了，1620 颗的方块价也能原样摆。
     */
    private static int buyPrice(int pay) {
        return Math.max(1, Math.min(MAX_PAY, pay));
    }

    /**
     * 卖出单价：照实给，不再凑整。
     *
     * <p>以前这里把 >64 的价四舍五入到 9 的倍数（理由：结果格只放得下一摞，只能整块整块给），
     * 于是 126 变 14 块、369 变 41 块 —— 大额永远显示成"绿宝石块"，玩家看不出到底多少绿宝石。
     * 现在一摞就摆到 9999（单格容量见 MerchantInventoryMixin），原价精确保留。
     */
    private static int sellPrice(int pay) {
        int p = Math.max(1, pay);
        return Math.min(MAX_INCOME, p);
    }

    // ---- 小工具 ----

    /** 玩家现在站的这座村的上下文（不在村里就 null：卖页就不做库存/银库的收紧） */
    private Bridge.Ctx ctx() {
        try {
            if (player.method_5770() instanceof class_3218 w) {
                int x = (int) Math.floor(player.method_23317());
                int y = (int) Math.floor(player.method_23318());
                int z = (int) Math.floor(player.method_23321());
                if (Bridge.villageKey(w, x, y, z) != null) return Bridge.ctx(w, x, y, z);
            }
        } catch (Throwable ignored) {
            // 拿不到就当前面那句没写
        }
        return null;
    }

    private class_7225.class_7874 lookup() {
        try {
            return player.method_5770().method_30349();
        } catch (Throwable t) {
            return null;
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        try {
            JsonArray a = o.getAsJsonArray(key);
            return a == null ? new JsonArray() : a;
        } catch (Throwable t) {
            return new JsonArray();
        }
    }

    private static JsonObject object(JsonArray a, int i) {
        try {
            return a.get(i).getAsJsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private static String string(JsonObject o, String key) {
        try {
            return o.get(key).getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static int integer(JsonObject o, String key, int fallback) {
        try {
            return o.get(key).getAsInt();
        } catch (Throwable t) {
            return fallback;
        }
    }
}
