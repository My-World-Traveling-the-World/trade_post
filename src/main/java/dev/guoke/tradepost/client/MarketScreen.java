package dev.guoke.tradepost.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guoke.tradepost.market.MarketPayload;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_9306;
import net.minecraft.class_1728;

/**
 * 集市主界面（v3 布局）：左「买入」右「卖出」两列整数报价，玩家背包在下半屏。
 *
 * <p>容器仍然是原版 {@code MerchantScreenHandler}：0/1 号槽是成本、2 号槽是结果，
 * 3..38 是玩家背包。这里只做两件事：
 * <ol>
 *   <li>把 39 个槽位的显示坐标重新摆到我们的布局上（x/y 是 final，只能反射写；写不动就保持原版位置，不崩）；</li>
 *   <li>自己画面板、两列报价、标题与提示，并接管「点一行」的命中测试 —— 点击后走原版那两步
 *       {@code method_7650(i)} + {@code method_20215(i)}，摆货/摆钱与结算全交给现有 mixin。</li>
 * </ol>
 */
public class MarketScreen extends class_465<class_1728> {

    private static final int BASE_W = 560;
    private static final int BASE_H = 480;
    private static final int ROW_H = 20;
    private static final int ROW_H_D = 30;
    private boolean detail;
    /** 底部一闪而过的提示（货币不足之类） */
    private String notice;
    private int walletCache;
    private int donateN = 1;
    /** 0=全部 1=缺货 2=可成交 */
    private int filter;
    private long sellAllArmed;
    private String relicId;
    private String relicName;
    private String relicCost;
    private boolean relicOk;
    private static boolean relicWarned;
    private boolean relicOpen;
    private int relicLeft = -1;
    private int relicNext = -1;
    private final String[] relicCostIds = new String[]{"", ""};
    private final int[] relicCostN = new int[]{0, 0};
    private long noticeAt;
    private int bandH = 132;

    // —— EMI：集市界面里暂时关掉它（EmiConfig.enabled 是 public static，反射置 false，关界面时还原）——
    private static java.lang.reflect.Field F_EMI_ENABLED;
    private static boolean EMI_LOOKED;
    private Object emiPrev;

    private void emiDisable() {
        try {
            if (!EMI_LOOKED) {
                EMI_LOOKED = true;
                try {
                    F_EMI_ENABLED = Class.forName("dev.emi.emi.config.EmiConfig").getField("enabled");
                } catch (Throwable ignored) {
                    F_EMI_ENABLED = null;
                }
            }
            if (F_EMI_ENABLED == null) return;
            this.emiPrev = F_EMI_ENABLED.get(null);
            F_EMI_ENABLED.setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }

    private void emiRestore() {
        try {
            if (F_EMI_ENABLED != null && this.emiPrev instanceof Boolean b) {
                F_EMI_ENABLED.setBoolean(null, b.booleanValue());
                this.emiPrev = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private static final int C_SURF = 0xFFE9DDC2;
    private static final int C_HI = 0xFFFBF4E4;
    private static final int C_SH = 0xFFC3B08A;
    private static final int C_EDGE = 0xFF2A2117;
    private static final int C_INK = 0xFF3A2E1F;
    private static final int C_DIM = 0xFF7A6A52;
    private static final int C_DIS = 0xFFA89C84;
    private static final int C_HOVER = 0xFFF3EAD4;
    private static final int C_SLOT = 0xFF8B8B8B;
    private static final int C_SLOT_TL = 0xFF373737;
    private static final int C_WHITE = 0xFFFFFFFF;

    private static java.lang.reflect.Field F_SLOT_X;
    private static java.lang.reflect.Field F_SLOT_Y;

    private final class_1728 mh;
    private JsonObject root = new JsonObject();
    private int[] tabs = new int[0];
    private int[] marks = new int[0];
    private int[] shorts = new int[0];
    private final java.util.List<Object[]> pendingCounts = new java.util.ArrayList<>();
    /** 货架上画出去的物品格：{x, y, ItemStack}，用来做 hover tooltip */
    private final java.util.List<Object[]> pendingTips = new java.util.ArrayList<>();
    private int scrollBuy = 0;
    private int scrollSell = 0;

    public static class_437 create(MarketPayload p) {
        try {
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 != null && mc.field_1724.field_7512 instanceof class_1728 real) {
                return new MarketScreen(real, p);
            }
        } catch (Throwable ignored) {
            // 掉到下面的旧界面
        }
        return new ClassicMarketScreen(p);
    }

    public MarketScreen(class_1728 handler, MarketPayload p) {
        super(handler, class_310.method_1551().field_1724.method_31548(), class_2561.method_43470(""));
        this.mh = handler;
        // 现有 mixin 的"第二格货款"用 39 号槽；不补这一格，点行时 syncPayout 会下标越界
        try { dev.guoke.tradepost.mc.MarketMerchant.ensureSlot(handler); } catch (Throwable ignored) { }
        this.root = parse(p.json());
        readArrays();
    }

    /** 服务端又发来一份价目表：只更新数据，不动容器 */
    public void setPayload(MarketPayload p) {
        this.root = parse(p.json());
        readArrays();
    }

    private void readArrays() {
        this.tabs = readIntArray("tabs");
        readRelic();
        this.marks = readIntArray("marks");
        this.shorts = readIntArray("shorts");
    }

    private int[] readIntArray(String key) {
        try {
            var arr = this.root.has(key) ? this.root.getAsJsonArray(key) : null;
            if (arr == null) return new int[0];
            int[] out = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i).getAsInt();
            return out;
        } catch (Throwable t) {
            return new int[0];
        }
    }

    /** boardJson 里已经带了 relics 段，直接取第一条画成卡片 */
    private void readRelic() {
        this.relicId = null;
        try {
            com.google.gson.JsonArray arr = this.root.has("relics") ? this.root.getAsJsonArray("relics") : null;
            if (arr == null || arr.size() == 0) return;
            com.google.gson.JsonObject o = arr.get(0).getAsJsonObject();
            this.relicId = o.get("id").getAsString();
            this.relicName = o.has("key")
                    ? net.minecraft.class_2561.method_43471(o.get("key").getAsString()).getString()
                    : o.get("name").getAsString();
            this.relicCost = o.has("cost") ? o.get("cost").getAsString() : "";
            this.relicOk = o.has("ok") && o.get("ok").getAsBoolean();
            this.relicLeft = o.has("left") ? o.get("left").getAsInt() : -1;
            this.relicNext = o.has("nextDays") ? o.get("nextDays").getAsInt() : -1;
            if (o.has("costs")) {
                com.google.gson.JsonArray cs = o.getAsJsonArray("costs");
                for (int i = 0; i < Math.min(2, cs.size()); i++) {
                    com.google.gson.JsonObject e = cs.get(i).getAsJsonObject();
                    this.relicCostIds[i] = e.has("id") ? e.get("id").getAsString() : "";
                    this.relicCostN[i] = e.has("count") ? e.get("count").getAsInt() : 0;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private int sideW() {
        int w = (this.field_2792 - 162) / 2 - 12;
        return Math.max(80, Math.min(104, w));
    }

    private int[] relicPanel() {
        return new int[]{this.field_2776 + 8, bandTop() + 2, sideW(), Math.max(56, this.bandH - 4)};
    }

    private int[] sellPanel() {
        return new int[]{this.field_2776 + this.field_2792 - 8 - sideW(), bandTop() + 2, sideW(), Math.max(56, this.bandH - 4)};
    }

    private int[] relicOverlay() {
        return new int[]{this.field_2776 + 8, listTop(), Math.min(300, this.field_2792 - 24),
                Math.max(80, listBottom() - listTop())};
    }

    /** 玩家背包里某件物品有多少（含副手/快捷栏） */
    private int countInInv(String id) {
        int n = 0;
        try {
            class_310 mc = class_310.method_1551();
            net.minecraft.class_1263 inv = mc.field_1724.method_31548();
            for (int i = 0; i < inv.method_5439(); i++) {
                class_1799 st = inv.method_5438(i);
                if (st.method_7960()) continue;
                String sid = net.minecraft.class_7923.field_41178.method_10221(st.method_7909()).toString();
                if (id.equals(sid)) n += st.method_7947();
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    private int[] relicAction() {
        int[] rp = relicPanel();
        return new int[]{rp[0] + 6, rp[1] + rp[3] - 20, rp[2] - 12, 16};
    }

    private int[] sellAction() {
        int[] sp = sellPanel();
        return new int[]{sp[0] + 6, sp[1] + sp[3] - 20, sp[2] - 12, 16};
    }

    private int tabOf(int i) {
        return i >= 0 && i < this.tabs.length ? this.tabs[i] : 0;
    }

    // ───────────── 布局 ─────────────

    @Override
    protected void method_25426() {
        int sw = this.field_22789;
        int sh = this.field_22790;
        // 按用户分辨率留边：四周各留约 5.5%，再夹在合理范围内，面板永远居中且不接壤屏幕边缘
        int mx = Math.max(14, (int) Math.round(sw * 0.055));
        int my = Math.max(14, (int) Math.round(sh * 0.055));
        this.field_2792 = Math.max(300, Math.min(BASE_W, sw - 2 * mx));
        this.field_2779 = Math.max(260, Math.min(BASE_H, sh - 2 * my));
        this.bandH = Math.max(104, Math.min(128, (int) Math.round(this.field_2779 * 0.36)));
        super.method_25426();
        placeSlots();
        emiDisable();
    }

    private int listTop() { return this.field_2800 + 64; }

    private int bandTop() { return this.field_2800 + this.field_2779 - this.bandH - 6; }

    private int listBottom() { return bandTop() - 4; }

    private int colW() { return (this.field_2792 - 30) / 2; }

    private int leftCol() { return this.field_2776 + 10; }

    private int rightCol() { return this.field_2776 + 20 + colW(); }

    private int rowH() { return this.detail ? ROW_H_D : ROW_H; }

    /** 背包里绿宝石折合多少颗（块按 9 算），和结算用的是同一个 Pay.value */
    private int walletEmeralds() {
        int n = 0;
        try {
            class_310 mc = class_310.method_1551();
            for (int i = 0; i < mc.field_1724.method_31548().method_5439(); i++) {
                n += dev.guoke.tradepost.market.Pay.value(mc.field_1724.method_31548().method_5438(i));
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    private void say(String msg) {
        this.notice = msg;
        this.noticeAt = System.currentTimeMillis();
    }

    private int visibleRows() {
        int h = listBottom() - listTop() - rowH();
        return Math.max(1, h / rowH());
    }

    // ───────────── 槽位重定位 ─────────────

    private class_1735 slotAt(int idx) {
        try {
            return this.mh.method_7611(idx);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 用「第 3 格与第 4 格哪个字段变了」来判定 x/y 字段，避免猜错 */
    private void initSlotFields() throws Exception {
        if (F_SLOT_X != null) return;
        java.lang.reflect.Field a = class_1735.class.getDeclaredField("field_7872");
        java.lang.reflect.Field b = class_1735.class.getDeclaredField("field_7873");
        a.setAccessible(true);
        b.setAccessible(true);
        class_1735 s3 = slotAt(3);
        class_1735 s4 = slotAt(4);
        if (s3 == null || s4 == null) return;
        if (a.getInt(s3) != a.getInt(s4)) {
            F_SLOT_X = a;
            F_SLOT_Y = b;
        } else {
            F_SLOT_X = b;
            F_SLOT_Y = a;
        }
    }

    private void place(int idx, int screenX, int screenY) {
        class_1735 s = slotAt(idx);
        if (s == null) return;
        try {
            F_SLOT_X.setInt(s, screenX - this.field_2776);
            F_SLOT_Y.setInt(s, screenY - this.field_2800);
        } catch (Throwable ignored) {
            // 写不动就保持原版坐标：布局难看，但交易照常
        }
    }

    private void placeSlots() {
        try {
            initSlotFields();
            if (F_SLOT_X == null) return;
            int cx = this.field_2776 + this.field_2792 / 2;
            int sy = bandTop() + 4;
            place(0, cx - 62, sy);
            place(1, cx - 40, sy);
            place(2, cx + 8, sy);
            int gx = this.field_2776 + (this.field_2792 - 9 * 18) / 2;
            int gy = bandTop() + 26;
            for (int i = 0; i < 27; i++) {
                place(3 + i, gx + (i % 9) * 18, gy + (i / 9) * 18);
            }
            for (int i = 0; i < 9; i++) {
                place(30 + i, gx + i * 18, gy + 62);
            }
            class_1735 probe = slotAt(3);
            if (probe != null) {
                dev.guoke.tradepost.Tradepost.LOGGER.info("[tradepost] 槽位重定位 OK：背包首格 x={} y={} 面板 {}x{}", F_SLOT_X.getInt(probe), F_SLOT_Y.getInt(probe), this.field_2792, this.field_2779);
            }
        } catch (Throwable t) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 背包槽位重定位失败（用原版位置）", t);
        }
    }

    // ───────────── 绘制 ─────────────

    @Override
    protected void method_2389(class_332 ctx, float delta, int mx, int my) {
        this.pendingCounts.clear();
        this.pendingTips.clear();
        this.walletCache = walletEmeralds();
        int x0 = this.field_2776, y0 = this.field_2800;
        int mw = this.field_2792, mh = this.field_2779;
        class_327 f = class_310.method_1551().field_1772;

        fill(ctx, x0, y0, x0 + mw, y0 + mh, C_SURF);
        fill(ctx, x0, y0, x0 + mw, y0 + 1, C_HI);
        fill(ctx, x0, y0, x0 + 1, y0 + mh, C_HI);
        fill(ctx, x0, y0 + mh - 1, x0 + mw, y0 + mh, C_SH);
        fill(ctx, x0 + mw - 1, y0, x0 + mw, y0 + mh, C_SH);
        fill(ctx, x0, y0, x0 + mw, y0 + 1, C_EDGE);

        int[] db = detailBtn();
        fill(ctx, db[0], db[1], db[0] + db[2], db[1] + db[3], this.detail ? 0xFFD8C79E : 0xFFCFC0A0);
        fill(ctx, db[0], db[1], db[0] + db[2], db[1] + 1, C_WHITE);
        fill(ctx, db[0], db[1], db[0] + 1, db[1] + db[3], C_WHITE);
        text(ctx, f, this.detail ? "详情：开" : "详情：关", db[0] + 6, db[1] + 5, C_INK, false);
        int[] fb = filterBtn();
        fill(ctx, fb[0], fb[1], fb[0] + fb[2], fb[1] + fb[3], this.filter == 0 ? 0xFFCFC0A0 : 0xFFD8C79E);
        fill(ctx, fb[0], fb[1], fb[0] + fb[2], fb[1] + 1, C_WHITE);
        fill(ctx, fb[0], fb[1], fb[0] + 1, fb[1] + fb[3], C_WHITE);
        text(ctx, f, "筛选：" + (this.filter == 1 ? "缺货" : this.filter == 2 ? "可成交" : "全部"), fb[0] + 6, fb[1] + 5, C_INK, false);
        text(ctx, f, "集市 · 铁匠村", x0 + 156, y0 + 5, C_INK, false);
        String sub = str("sub", "");
        if (!sub.isEmpty()) {
            int w = f.method_1727(sub);
            text(ctx, f, sub, x0 + mw - 12 - w, y0 + 5, 0xFF8A6A20, false);
        }
        fill(ctx, x0 + 2, y0 + 17, x0 + mw - 2, y0 + 18, C_SH);

        // 村情条
        String ind = str("ind", "无产业");
        String lv = str("lvname", "");
        text(ctx, f, ind + (lv.isEmpty() ? "" : " · " + lv), x0 + 12, y0 + 20, C_INK, false);
        String wallet = "银库 " + num("wallet", 0);
        int ww = f.method_1727(wallet);
        // 银库已经在顶栏显示，第二行右侧留给捐赠控件，不再重复画
        drawDonate(ctx, f, x0, y0, mw);
        int xpNext = (int) Math.round(numD("xpnext", 0));
        if (xpNext > 0) {
            text(ctx, f, "还差 " + xpNext + " 经验升级", x0 + 12, y0 + 34, C_DIM, false);
            int bx = x0 + 132, by = y0 + 36, bw = 90;
            fill(ctx, bx, by, bx + bw, by + 4, 0xFFB9A987);
            double pr = numD("xpr", 0);
            int fw = (int) Math.round(bw * Math.max(0, Math.min(1, pr)));
            if (fw > 1) fill(ctx, bx + 1, by + 1, bx + fw - 1, by + 3, 0xFF5AAA3C);
        }
        fill(ctx, x0 + 2, y0 + 44, x0 + mw - 2, y0 + 45, C_SH);

        // 列头
        text(ctx, f, "买入", leftCol(), y0 + 48, C_INK, false);
        if (num("fair", 0) != 0) {
            String fx = "集市日价格 x1.1！";
            text(ctx, f, fx, leftCol() + 26, y0 + 48, 0xFFB8860B, false);
        }
        text(ctx, f, "卖出", rightCol(), y0 + 48, C_INK, false);
        if (num("fair", 0) != 0) {
            String fx = "集市日价格 x1.1！";
            text(ctx, f, fx, rightCol() + 26, y0 + 48, 0xFFB8860B, false);
        }
        if (num("fair", 0) == 0) text(ctx, f, "你付绿宝石", leftCol() + 46, y0 + 49, C_DIM, false);
        if (num("fair", 0) == 0) text(ctx, f, "你收绿宝石", rightCol() + 46, y0 + 49, C_DIM, false);
        scrollButtons(ctx, f, leftCol(), y0 + 45);
        scrollButtons(ctx, f, rightCol(), y0 + 45);
        fill(ctx, x0 + 2, y0 + 62, x0 + mw - 2, y0 + 63, C_SH);

        // 下半屏先画，避免盖住第 3 行
        // 下半屏
        text(ctx, f, "柜台", x0 + mw / 2 - 100, bandTop() + 6, C_DIM, false);
        text(ctx, f, "背包", x0 + mw / 2 - 100, bandTop() + 30, C_DIM, false);
        drawSidePanels(ctx, f);        drawSlots(ctx);
        if (false && this.relicOpen && this.relicId != null) {
            int[] ov = relicOverlay();
            fill(ctx, ov[0], ov[1], ov[0] + ov[2], ov[1] + ov[3], 0xFFE6D8B8);
            fill(ctx, ov[0], ov[1], ov[0] + ov[2], ov[1] + 1, 0xFFFFFFFF);
            fill(ctx, ov[0], ov[1], ov[0] + 1, ov[1] + ov[3], 0xFFFFFFFF);
            text(ctx, f, "秘宝 · 本周", ov[0] + 8, ov[1] + 6, C_INK, false);
            text(ctx, f, "收起 ▾", ov[0] + ov[2] - 46, ov[1] + 6, C_DIM, false);
            int bigX = ov[0] + 12, bigY = ov[1] + 26;
            slotBox(ctx, bigX, bigY);
            class_1799 rs = stackOf(this.relicId, 1);
            if (!rs.method_7960()) {
                ctx.method_51427(rs, bigX + 1, bigY + 1);
                this.pendingTips.add(new Object[]{Integer.valueOf(bigX), Integer.valueOf(bigY), rs});
            }
            text(ctx, f, fit(f, this.relicName, ov[2] - 46), bigX + 24, bigY + 5, C_INK, false);
            int cy2 = bigY + 28;
            for (int i = 0; i < 2; i++) {
                if (this.relicCostIds[i] == null || this.relicCostIds[i].isEmpty()) continue;
                int cx2 = ov[0] + 12 + i * 24;
                slotBox(ctx, cx2, cy2);
                class_1799 cs = stackOf(this.relicCostIds[i], this.relicCostN[i]);
                if (!cs.method_7960()) {
                    ctx.method_51427(cs, cx2 + 1, cy2 + 1);
                    this.pendingTips.add(new Object[]{Integer.valueOf(cx2), Integer.valueOf(cy2), cs});
                }
                int have = countInInv(this.relicCostIds[i]);
                boolean ok = have >= this.relicCostN[i];
                text(ctx, f, have + "/" + this.relicCostN[i], cx2 + 20, cy2 + 5,
                        ok ? 0xFF1F6B2C : 0xFF9A4A2A, false);
            }
            int[] ra = relicAction();
            int rx = ov[0] + 12, ry = ov[1] + ov[3] - 24;
            fill(ctx, rx, ry, rx + 90, ry + 16, this.relicOk ? 0xFF9C7B34 : 0xFF9A8C74);
            text(ctx, f, this.relicOk ? "换取" : "材料不足", rx + 6, ry + 4,
                    this.relicOk ? 0xFF2B1A0C : 0xFFEDE6D8, false);
        }
        drawExchangeRow(ctx, f, mx, my);
        if (this.notice != null && System.currentTimeMillis() - this.noticeAt < 2600L) {
            int tx = x0 + mw / 2 + 34;
            int ty = bandTop() + 9;
            int maxW = x0 + mw - 14 - tx;
            String msg = this.notice;
            while (f.method_1727(msg) > maxW && msg.length() > 1) msg = msg.substring(0, msg.length() - 1);
            int nw = f.method_1727(msg);
            fill(ctx, tx - 4, ty - 2, tx + nw + 4, ty + 12, 0xFF3A2A18);
            text(ctx, f, msg, tx, ty, 0xFFE8CE9A, false);
        }
        // 两列报价
        int li = 0, ri = 0;
        int rows = Math.min(this.mh.method_17438() == null ? 0 : this.mh.method_17438().size(), 200);
        for (int i = 0; i < rows; i++) {
            if (!visibleRow(i)) continue;
            int tab = tabOf(i);
            int n = tab == 0 ? li++ : ri++;
            int vis = n - (tab == 0 ? scrollBuy : scrollSell);
            if (vis < 0 || vis >= visibleRows()) continue;
            int x = tab == 0 ? leftCol() : rightCol();
            int y = listTop() + rowH() + vis * rowH();
            drawRow(ctx, f, i, x, y, colW(), mx, my);
        }


    }

    /** 容器槽位的底框（原版是从背景贴图里带的，我们自绘背景就得自己画） */
    /** 由物品 id 生成一个展示用栈 */
    private static class_1799 stackOf(String id, int n) {
        try {
            if (id == null || id.isEmpty()) return class_1799.field_8037;
            String ns = "minecraft", path = id.trim();
            int colon = path.indexOf(':');
            if (colon > 0) {
                ns = path.substring(0, colon);
                path = path.substring(colon + 1);
            }
            // 用 of(ns, path) 自己拆，别依赖 "ns:path" 一次性解析（那个入口在某些版本会直接抛）
            net.minecraft.class_2960 ident = net.minecraft.class_2960.method_60655(ns, path);
            if (ident == null) return class_1799.field_8037;
            net.minecraft.class_1792 item = net.minecraft.class_7923.field_41178.method_10223(ident);
            if (item == null) return class_1799.field_8037;
            class_1799 st = new class_1799(item, Math.max(1, n));
            return st.method_7960() ? class_1799.field_8037 : st;
        } catch (Throwable t) {
            return class_1799.field_8037;
        }
    }

    /** 原版样式的 18×18 格子（底 + 左上内凹 + 右下高光） */
    private void slotBox(class_332 ctx, int x, int y) {
        fill(ctx, x, y, x + 18, y + 18, C_SLOT);
        fill(ctx, x, y, x + 18, y + 1, C_SLOT_TL);
        fill(ctx, x, y, x + 1, y + 18, C_SLOT_TL);
        fill(ctx, x, y + 17, x + 18, y + 18, C_WHITE);
        fill(ctx, x + 17, y, x + 18, y + 18, C_WHITE);
    }

    /** 下半屏两侧的小窗：左侧秘宝（标题 / 秘宝槽 / 两个材料槽），右侧清仓 */
    private void drawSidePanels(class_332 ctx, class_327 f) {
        int[] rp = relicPanel();
        fill(ctx, rp[0], rp[1], rp[0] + rp[2], rp[1] + rp[3], 0xFFDCCBA8);
        fill(ctx, rp[0], rp[1], rp[0] + rp[2], rp[1] + 1, C_WHITE);
        fill(ctx, rp[0], rp[1], rp[0] + 1, rp[1] + rp[3], C_WHITE);
        text(ctx, f, "秘宝 · 本周", rp[0] + 5, rp[1] + 4, C_INK, false);
        if (this.relicId != null) {
            int sy = rp[1] + 15;
            int sx = rp[0] + (rp[2] - 18) / 2;
            slotBox(ctx, sx, sy);
            class_1799 rs = stackOf(this.relicId, 1);
            if (!rs.method_7960()) {
                ctx.method_51427(rs, sx + 1, sy + 1);
                this.pendingTips.add(new Object[]{Integer.valueOf(sx), Integer.valueOf(sy), rs});
            }
            int cy2 = sy + 20;
            int lx = rp[0] + 5;
            text(ctx, f, "需要", lx, cy2 + 5, C_DIM, false);
            int cx2 = lx + 22;
            for (int i = 0; i < 2; i++) {
                slotBox(ctx, cx2, cy2);
                class_1799 cs = stackOf(this.relicCostIds[i], this.relicCostN[i]);
                if (!cs.method_7960()) {
                    ctx.method_51427(cs, cx2 + 1, cy2 + 1);
                    this.pendingTips.add(new Object[]{Integer.valueOf(cx2), Integer.valueOf(cy2), cs});
                }
                cx2 += 22;
            }
            String info = this.relicNext > 0
                    ? (this.relicLeft >= 0 ? "剩 " + this.relicLeft + " 次 · " : "") + this.relicNext + " 天后换批"
                    : "每周轮换一次";
            info = fit(f, info, rp[2] - 10);
            text(ctx, f, info, rp[0] + (rp[2] - f.method_1727(info)) / 2, cy2 + 24, C_DIM, false);
            int[] ra = relicAction();
            fill(ctx, ra[0], ra[1], ra[0] + ra[2], ra[1] + ra[3], this.relicOk ? 0xFF9C7B34 : 0xFF9A8C74);
            text(ctx, f, this.relicOk ? "换取" : "材料不足", ra[0] + 4, ra[1] + 4,
                    this.relicOk ? 0xFF2B1A0C : 0xFFEDE6D8, false);
        } else {
            text(ctx, f, "本周无", rp[0] + 5, rp[1] + 18, C_DIM, false);
        }

        int[] sp = sellPanel();
        fill(ctx, sp[0], sp[1], sp[0] + sp[2], sp[1] + sp[3], 0xFFDCCBA8);
        fill(ctx, sp[0], sp[1], sp[0] + sp[2], sp[1] + 1, C_WHITE);
        fill(ctx, sp[0], sp[1], sp[0] + 1, sp[1] + sp[3], C_WHITE);
        text(ctx, f, "清仓", sp[0] + 5, sp[1] + 4, C_INK, false);
        int sellRows = 0, gross = 0;
        try {
            if (this.mh.method_17438() != null) {
                int n = this.mh.method_17438().size();
                for (int i = 0; i < n; i++) {
                    if (tabOf(i) != 1) continue;
                    // 只算"真能成交"的行：缺货、数量不够一手、已被服务端打红叉的都不算
                    if (this.marks.length > i && (this.marks[i] & 4) != 0) continue;
                    if (i < this.shorts.length && this.shorts[i] != 0) continue;
                    class_1914 off = this.mh.method_17438().get(i);
                    if (off == null || off.method_8255()) continue;
                    sellRows++;
                    gross += off.method_18019().method_7947();
                }
            }
        } catch (Throwable ignored) {
        }
        text(ctx, f, sellRows + " 种可卖", sp[0] + 5, sp[1] + 18, C_INK, false);
        text(ctx, f, "预计收获", sp[0] + 5, sp[1] + 31, C_DIM, false);
        if (gross > 0) {
            int ex = sp[0] + 5, ey = sp[1] + 43;
            class_1799 em = stackOf("minecraft:emerald", Math.min(9999, gross));
            if (!em.method_7960()) {
                ctx.method_51427(em, ex, ey);
                this.pendingTips.add(new Object[]{Integer.valueOf(ex), Integer.valueOf(ey), em});
                this.pendingCounts.add(new Object[]{em, Integer.valueOf(ex), Integer.valueOf(ey), String.valueOf(gross)});
            }
        }
        int[] sa = sellAction();
        boolean armed = System.currentTimeMillis() - this.sellAllArmed < 4000L;
        fill(ctx, sa[0], sa[1], sa[0] + sa[2], sa[1] + sa[3], armed ? 0xFFB85C3A : 0xFFCFC0A0);
        text(ctx, f, armed ? "确认？" : "卖出全部", sa[0] + 4, sa[1] + 4,
                armed ? 0xFFFFF0E0 : C_INK, false);
    }
    private static final String PURE = "pureemeraldtools:pure_emerald";

    private int[] donateMinus(int x0, int y0, int mw) {
        return new int[]{x0 + mw - 156, y0 + 20, 14, 14};
    }

    private int[] donatePlus(int x0, int y0, int mw) {
        int[] m = donateMinus(x0, y0, mw);
        return new int[]{m[0] + 36, m[1], 14, 14};
    }

    private int[] donateGo(int x0, int y0, int mw) {
        int[] m = donateMinus(x0, y0, mw);
        return new int[]{m[0] + 54, m[1] - 1, 34, 16};
    }

    private void drawDonate(class_332 ctx, class_327 f, int x0, int y0, int mw) {
        int have = countInInv(PURE);
        int max = Math.max(0, Math.min(12, have));
        if (this.donateN > max) this.donateN = max;
        if (this.donateN < 0) this.donateN = 0;
        int[] m = donateMinus(x0, y0, mw);
        int[] p = donatePlus(x0, y0, mw);
        int[] g = donateGo(x0, y0, mw);
        fill(ctx, m[0], m[1], m[0] + m[2], m[1] + m[3], 0xFFCFC0A0);
        text(ctx, f, "-", m[0] + 5, m[1] + 3, C_INK, false);
        fill(ctx, p[0], p[1], p[0] + p[2], p[1] + p[3], 0xFFCFC0A0);
        text(ctx, f, "+", p[0] + 5, p[1] + 3, C_INK, false);
        int sx = m[0] + 16, sy = m[1] - 2;
        slotBox(ctx, sx, sy);
        class_1799 st = stackOf(PURE, Math.max(1, this.donateN));
        if (!st.method_7960() && this.donateN > 0) {
            ctx.method_51427(st, sx + 1, sy + 1);
            this.pendingCounts.add(new Object[]{st, Integer.valueOf(sx), Integer.valueOf(sy), String.valueOf(this.donateN)});
            this.pendingTips.add(new Object[]{Integer.valueOf(sx), Integer.valueOf(sy), st});
        }
        boolean ok = this.donateN > 0 && this.donateN <= have;
        fill(ctx, g[0], g[1], g[0] + g[2], g[1] + g[3], ok ? 0xFF9C7B34 : 0xFF9A8C74);
        text(ctx, f, "捐献", g[0] + 5, g[1] + 4, ok ? 0xFF2B1A0C : 0xFFEDE6D8, false);
        String xp = "+" + (this.donateN * 250) + " 经验";
        text(ctx, f, xp, g[0] + 40, g[1] + 4, ok ? 0xFF7A5A20 : C_DIM, false);
    }

    /** 两列的第一行固定是汇兑：左买纯净（90 绿宝石）、右卖纯净（70 绿宝石） */
    private void drawExchangeRow(class_332 ctx, class_327 f, int mx, int my) {
        int y = listTop();
        int lx = leftCol(), rx = rightCol(), w = colW();
        boolean hovL = mx >= lx && mx < lx + w && my >= y && my < y + rowH() - 1;
        boolean hovR = mx >= rx && mx < rx + w && my >= y && my < y + rowH() - 1;
        fill(ctx, lx, y, lx + w, y + rowH() - 1, hovL ? 0xFFFFF6DF : 0x00000000);
        int sy = y + 1;
        class_1799 pay = stackOf("minecraft:emerald", 90);
        class_1799 get = stackOf("PURE", 1);
        if (!pay.method_7960()) {
            ctx.method_51427(pay, lx + 3, sy);
            this.pendingCounts.add(new Object[]{pay, Integer.valueOf(lx + 3), Integer.valueOf(sy), "90"});
            this.pendingTips.add(new Object[]{Integer.valueOf(lx + 3), Integer.valueOf(sy), pay});
        }
        arrow(ctx, lx + 24, y + rowH() / 2);
        class_1799 pure = stackOf(PURE, 1);
        if (!pure.method_7960()) {
            ctx.method_51427(pure, lx + 32, sy);
            this.pendingTips.add(new Object[]{Integer.valueOf(lx + 32), Integer.valueOf(sy), pure});
        }
        text(ctx, f, "汇兑", lx + 54, y + 2, C_INK, false);
        text(ctx, f, this.walletCache >= 90 ? "买 1 纯净" : "绿宝石不足", lx + 54, y + 11,
                this.walletCache >= 90 ? C_DIM : 0xFF9A4A2A, false);

        fill(ctx, rx, y, rx + w, y + rowH() - 1, hovR ? 0xFFFFF6DF : 0x00000000);
        if (!pure.method_7960()) {
            ctx.method_51427(pure, rx + 3, sy);
            this.pendingCounts.add(new Object[]{pure, Integer.valueOf(rx + 3), Integer.valueOf(sy), "1"});
            this.pendingTips.add(new Object[]{Integer.valueOf(rx + 3), Integer.valueOf(sy), pure});
        }
        arrow(ctx, rx + 24, y + rowH() / 2);
        class_1799 out = stackOf("minecraft:emerald", 70);
        if (!out.method_7960()) {
            ctx.method_51427(out, rx + 32, sy);
            this.pendingCounts.add(new Object[]{out, Integer.valueOf(rx + 32), Integer.valueOf(sy), "70"});
            this.pendingTips.add(new Object[]{Integer.valueOf(rx + 32), Integer.valueOf(sy), out});
        }
        int havePure = countInInv(PURE);
        text(ctx, f, "汇兑", rx + 54, y + 2, C_INK, false);
        text(ctx, f, havePure > 0 ? "卖 1 纯净" : "没有纯净绿宝石", rx + 54, y + 11,
                havePure > 0 ? C_DIM : 0xFF9A4A2A, false);
        fill(ctx, lx, y + rowH() - 1, lx + w, y + rowH(), C_SH);
        fill(ctx, rx, y + rowH() - 1, rx + w, y + rowH(), C_SH);
    }

    private void drawSlots(class_332 ctx) {
        if (F_SLOT_X == null) return;
        for (int i = 0; i < 39; i++) {
            class_1735 s = slotAt(i);
            if (s == null) continue;
            try {
                int sx = this.field_2776 + F_SLOT_X.getInt(s) - 1;
                int sy = this.field_2800 + F_SLOT_Y.getInt(s) - 1;
                fill(ctx, sx, sy, sx + 18, sy + 18, C_SLOT);
                fill(ctx, sx, sy, sx + 18, sy + 1, C_SLOT_TL);
                fill(ctx, sx, sy, sx + 1, sy + 18, C_SLOT_TL);
                fill(ctx, sx, sy + 17, sx + 18, sy + 18, C_WHITE);
                fill(ctx, sx + 17, sy, sx + 18, sy + 18, C_WHITE);
            } catch (Throwable ignored) {
            }
        }
        // 成本格 → 结果格 之间画个箭头
        class_1735 c1 = slotAt(1);
        class_1735 r = slotAt(2);
        if (c1 != null && r != null && F_SLOT_X != null) {
            try {
                int ax = this.field_2776 + F_SLOT_X.getInt(c1) + 20;
                int ay = this.field_2800 + F_SLOT_Y.getInt(c1) + 8;
                for (int i = 0; i < 4; i++) {
                    int hw = 3 - i;
                    fill(ctx, ax + i, ay - hw, ax + i + 1, ay + hw + 1, 0xFF6A5A46);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 左上角「详情」按钮：{x, y, w, h} */
    private int[] detailBtn() {
        return new int[]{this.field_2776 + 8, this.field_2800 + 1, 58, 18};
    }

    /** 鼠标悬在柜台/背包的格子上，给一个物品名提示（原版的槽位 tooltip 依赖 focusedSlot，我们自绘更稳） */
    private void drawHoverTip(class_332 ctx, class_327 f, int mx, int my) {
        // 先看货架上的两列物品（这些不是真槽位，原版 focusedSlot 管不到）
        for (Object[] o : this.pendingTips) {
            int x = ((Integer) o[0]).intValue();
            int y = ((Integer) o[1]).intValue();
            if (mx < x || mx >= x + 18 || my < y || my >= y + 18) continue;
            class_1799 st = (class_1799) o[2];
            if (st.method_7960()) continue;
            ctx.method_51434(f, this.method_51454(st), mx, my);
            return;
        }
        if (F_SLOT_X == null) return;
        for (int i = 0; i < 39; i++) {
            class_1735 s = slotAt(i);
            if (s == null) continue;
            try {
                int sx = this.field_2776 + F_SLOT_X.getInt(s);
                int sy = this.field_2800 + F_SLOT_Y.getInt(s);
                if (mx < sx || mx >= sx + 16 || my < sy || my >= sy + 16) continue;
                class_1799 st = s.method_7677();
                if (st.method_7960()) continue;
                // 用原版的物品 tooltip：会带上附魔、药水效果、模组自己加的说明（EMI 这时已被关掉，不会抢）
                // 原版完整 tooltip（名称/附魔/药水/模组说明全在）：class_465.method_51454 = getTooltipFromItem
                java.util.List<net.minecraft.class_2561> tipLines = this.method_51454(st);
                ctx.method_51434(f, tipLines, mx, my);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private int[] filterBtn() {
        return new int[]{this.field_2776 + 70, this.field_2800 + 1, 78, 18};
    }

    private int[] sellAllBtn(int x0, int y0, int mw, int mh) {
        return new int[]{x0 + mw - 92, y0 + mh - 18, 82, 14};
    }

    /** 当前筛选下这一行要不要显示 */
    private boolean visibleRow(int i) {
        if (this.filter == 0) return true;
        try {
            class_1914 off = this.mh.method_17438().get(i);
            if (off == null) return false;
            if (this.filter == 1) return i < this.marks.length && (this.marks[i] & 4) != 0;
            boolean sold = off.method_8255();
            boolean sh = i < this.shorts.length && this.shorts[i] != 0;
            boolean sellRow = tabOf(i) == 1;
            int price = sellRow ? off.method_18019().method_7947()
                    : (off.method_57556() == null ? 0 : off.method_57556().comp_2425());
            return !sold && !sh && (sellRow || price <= this.walletCache);
        } catch (Throwable t) {
            return true;
        }
    }

    private void drawRow(class_332 ctx, class_327 f, int i, int x, int y, int w, int mx, int my) {
        class_1914 offer = this.mh.method_17438().get(i);
        if (offer == null) return;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH() - 1;
        if (hover) {
            fill(ctx, x, y, x + w, y + rowH() - 1, 0xFFFFF6DF);
            fill(ctx, x, y, x + w, y + 1, 0xFFE0BE7A);
            fill(ctx, x, y + rowH() - 2, x + w, y + rowH() - 1, 0xFFE0BE7A);
        }
        fill(ctx, x, y + rowH() - 1, x + w, y + rowH(), C_SH);

        class_9306 cost = offer.method_57556();
        class_1799 costStack = cost == null ? class_1799.field_8037 : cost.comp_2427();
        int costCount = cost == null ? 1 : cost.comp_2425();
        class_1799 resStack = offer.method_18019();
        boolean sold = offer.method_8255();
        boolean sellRow = tabOf(i) == 1;
        class_1799 goods = sellRow ? costStack : resStack;

        int sy = y + 1;
        drawCell(ctx, f, costStack, costCount, x + 3, sy, sold);
        arrow(ctx, x + 24, y + rowH() / 2);

        drawCell(ctx, f, resStack, resStack.method_7947(), x + 32, sy, sold);

        String name = goods.method_7960() ? "-" : goods.method_7964().getString();
        int nx = x + 56;
        int tw = 0;
        boolean shortQty = i < this.shorts.length && this.shorts[i] != 0;
        String badge = shortQty ? "数量不足" : (sold ? "售罄" : "");
        if (!badge.isEmpty()) {
            int bc = shortQty ? 0xFFB8860B : 0xFFB03A2E;
            tw = f.method_1727(badge) + 4;
            text(ctx, f, badge, x + w - 6 - f.method_1727(badge), y + 7, bc, false);
        }
        text(ctx, f, fit(f, name, w - 62 - tw), nx, y + (this.detail ? 3 : 6), sold ? C_DIS : C_INK, false);
        if (this.detail) {
            int lotN = sellRow ? costCount : resStack.method_7947();
            int priceN = sellRow ? resStack.method_7947() : costCount;
            int i2 = i < this.marks.length ? this.marks[i] : 0;
            String tr = (i2 & 2) != 0 ? "涨" : ((i2 & 1) != 0 ? "跌" : "");
            String line = "一手 " + lotN + " 件 = " + priceN + " 颗";
            if (!tr.isEmpty()) line += " · " + tr;
            if ((i2 & 4) != 0) line += " · 缺货";
            text(ctx, f, fit(f, line, w - 62), nx, y + 17, C_DIM, false);
        }
    }

    private void drawCell(class_332 ctx, class_327 f, class_1799 stack, int count, int x, int y, boolean dim) {
        fill(ctx, x, y, x + 18, y + 18, C_SLOT);
        fill(ctx, x, y, x + 18, y + 1, C_SLOT_TL);
        fill(ctx, x, y, x + 1, y + 18, C_SLOT_TL);
        if (!stack.method_7960()) {
            ctx.method_51427(stack, x + 1, y + 1);
            this.pendingTips.add(new Object[]{Integer.valueOf(x), Integer.valueOf(y), stack});
        }
        if (count > 1) {
            String s = count > 9999 ? "999+" : String.valueOf(count);
            // 物品是用 ItemRenderer 画的、会在文字之后才 flush，所以数字必须留到 super.render 之后再画
            this.pendingCounts.add(new Object[]{stack, Integer.valueOf(x + 1), Integer.valueOf(y + 1), s});
        }
    }

    private void arrow(class_332 ctx, int x, int cy) {
        for (int i = 0; i < 4; i++) {
            int hw = 3 - i;
            fill(ctx, x + i, cy - hw, x + i + 1, cy + hw + 1, 0xFF6A5A46);
        }
    }

    private void fill(class_332 ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.method_25294(x1, y1, x2, y2, color);
    }

    private void text(class_332 ctx, class_327 f, String s, int x, int y, int color, boolean shadow) {
        ctx.method_51433(f, s, x, y, color, shadow);
    }

    private String fit(class_327 f, String s, int maxW) {
        if (f.method_1727(s) <= maxW) return s;
        while (s.length() > 1 && f.method_1727(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private String str(String k, String def) {
        try {
            return this.root.has(k) ? this.root.get(k).getAsString() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private int num(String k, int def) {
        try {
            return this.root.has(k) ? (int) this.root.get(k).getAsDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private double numD(String k, double def) {
        try {
            return this.root.has(k) ? this.root.get(k).getAsDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    // ───────────── 交互 ─────────────

    private int rowAt(double mx, double my) {
        int rows = this.mh.method_17438() == null ? 0 : this.mh.method_17438().size();
        int li = 0, ri = 0;
        for (int i = 0; i < rows; i++) {
            if (!visibleRow(i)) continue;
            int tab = tabOf(i);
            int n = tab == 0 ? li++ : ri++;
            int vis = n - (tab == 0 ? scrollBuy : scrollSell);
            if (vis < 0 || vis >= visibleRows()) continue;
            int x = tab == 0 ? leftCol() : rightCol();
            int y = listTop() + rowH() + vis * rowH();
            if (mx >= x && mx < x + colW() && my >= y && my < y + rowH() - 1) return i;
        }
        return -1;
    }

    @Override
    public boolean method_25401(double mx, double my, double hAmount, double vAmount) {
        try {
            int step = vAmount > 0 ? -1 : (vAmount < 0 ? 1 : 0);
            if (step != 0) {
                if (mx < this.field_2776 + this.field_2792 / 2) {
                    scrollBuy = Math.max(0, scrollBuy + step);
                } else {
                    scrollSell = Math.max(0, scrollSell + step);
                }
                return true;
            }
        } catch (Throwable ignored) {
        }
        return super.method_25401(mx, my, hAmount, vAmount);
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        super.method_25394(ctx, mx, my, delta);
        class_327 f = class_310.method_1551().field_1772;
        for (Object[] o : this.pendingCounts) {
            // 走原版 drawStackOverlay(文字重载)：这样"堆叠数字优化"这类 mod 的注入才会对我们这两列生效
            ctx.method_51432(f, (class_1799) o[0],
                    ((Integer) o[1]).intValue(), ((Integer) o[2]).intValue(), (String) o[3]);
        }
        drawHoverTip(ctx, f, mx, my);
    }

    @Override
    public boolean method_25402(double mx, double my, int btn) {
        int[] db = detailBtn();
        if (mx >= db[0] && mx < db[0] + db[2] && my >= db[1] && my < db[1] + db[3]) {
            this.detail = !this.detail;
            return true;
        }
        int[] dm = donateMinus(this.field_2776, this.field_2800, this.field_2792);
        int[] dp = donatePlus(this.field_2776, this.field_2800, this.field_2792);
        int[] dg = donateGo(this.field_2776, this.field_2800, this.field_2792);
        if (mx >= dm[0] && mx < dm[0] + dm[2] && my >= dm[1] && my < dm[1] + dm[3]) {
            this.donateN = Math.max(0, this.donateN - 1);
            return true;
        }
        if (mx >= dp[0] && mx < dp[0] + dp[2] && my >= dp[1] && my < dp[1] + dp[3]) {
            this.donateN = Math.min(12, this.donateN + 1);
            return true;
        }
        if (mx >= dg[0] && mx < dg[0] + dg[2] && my >= dg[1] && my < dg[1] + dg[3]) {
            if (this.donateN > 0) {
                try {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new dev.guoke.tradepost.market.DonatePayload(String.valueOf(this.donateN)));
                } catch (Throwable ignored) {
                }
            }
            return true;
        }
        int[] fb = filterBtn();
        if (mx >= fb[0] && mx < fb[0] + fb[2] && my >= fb[1] && my < fb[1] + fb[3]) {
            this.filter = (this.filter + 1) % 3;
            return true;
        }
        int[] sbb = sellAction();
        if (mx >= sbb[0] && mx < sbb[0] + sbb[2] && my >= sbb[1] && my < sbb[1] + sbb[3]) {
            if (System.currentTimeMillis() - this.sellAllArmed < 4000L) {
                this.sellAllArmed = 0L;
                try {
                    class_310 mc = class_310.method_1551();
                    if (mc.method_1562() != null) mc.method_1562().method_45731("tradepost sellall");
                } catch (Throwable ignored) {
                }
            } else {
                this.sellAllArmed = System.currentTimeMillis();
            }
            return true;
        }
        if (false && this.relicOpen) {
            int[] ov = relicOverlay();
            if (mx >= ov[0] + ov[2] - 52 && mx < ov[0] + ov[2] && my >= ov[1] && my < ov[1] + 18) {
                this.relicOpen = false;
                return true;
            }
            int rx = ov[0] + 12, ry = ov[1] + ov[3] - 24;
            if (mx >= rx && mx < rx + 90 && my >= ry && my < ry + 16) {

                if (!this.relicOk) {
                    say("秘宝：" + this.relicCost + "（材料不足）");
                } else {
                    try {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new dev.guoke.tradepost.market.TradePayload(this.relicId));
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            }
            if (mx >= ov[0] && mx < ov[0] + ov[2] && my >= ov[1] && my < ov[1] + ov[3]) return true;
            this.relicOpen = false;
        }
        if (this.relicId != null) {
            int[] rc = relicPanel();
            int[] ra = relicAction();
            if (false) {
                this.relicOpen = true;
                return true;
            }
            if (mx >= ra[0] && mx < ra[0] + ra[2] && my >= ra[1] && my < ra[1] + ra[3]) {

                if (!this.relicOk) {
                    say("秘宝：" + this.relicCost + "（材料不足）");
                } else {
                    try {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new dev.guoke.tradepost.market.TradePayload(this.relicId));
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            }
        }
        int sc = scrollHit(mx, my);
        if (sc >= 0) {
            int step = (sc % 2 == 0) ? -1 : 1;
            if (sc / 2 == 0) scrollBuy = Math.max(0, scrollBuy + step);
            else scrollSell = Math.max(0, scrollSell + step);
            return true;
        }
        int ey = listTop();
        if (my >= ey && my < ey + rowH() - 1) {
            if (mx >= leftCol() && mx < leftCol() + colW()) {
                try {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new dev.guoke.tradepost.market.ExchangePayload("0"));
                } catch (Throwable ignored) {
                }
                return true;
            }
            if (mx >= rightCol() && mx < rightCol() + colW()) {
                try {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new dev.guoke.tradepost.market.ExchangePayload("1"));
                } catch (Throwable ignored) {
                }
                return true;
            }
        }
        int hit = rowAt(mx, my);
        if (hit >= 0 && tabOf(hit) == 0) {
            // 买页：钱不够就别让他点出一片空白，直接把差额说出来
            try {
                class_1914 off = this.mh.method_17438().get(hit);
                class_9306 c0 = off == null ? null : off.method_57556();
                int price = c0 == null ? 0 : c0.comp_2425();
                int have = walletEmeralds();
                if (price > 0 && have < price) {
                    say("货币不足：要 " + price + " 颗，你有 " + have + " 颗");
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (hit >= 0 && hit < this.shorts.length && this.shorts[hit] != 0) {
            return true;       // 数量不足：点不动，也不发包
        }
        if (hit >= 0) {
            try {
                this.mh.method_7650(hit);
                this.mh.method_20215(hit);
                // 关键：还要把"选中第几条"告诉服务端（原版 class_492 也是这么做的），
                // 否则服务端的 selectedOffer 停在 0，结果格的 canTakeItems 会对不上 → 绿宝石拿不出来
                net.minecraft.class_634 nh = class_310.method_1551().method_1562();
                if (nh != null) nh.method_52787(new net.minecraft.class_2863(hit));
            } catch (Throwable t) {
                dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 选行失败", t);
            }
            return true;
        }
        return super.method_25402(mx, my, btn);
    }

    @Override
    public boolean method_25404(int key, int scancode, int mods) {
        if (key == 256 && this.relicOpen) {
            this.relicOpen = false;
            return true;
        }
        if (key == 68) {   // D：切详情（一手件数/单价/涨跌）
            this.detail = !this.detail;
            return true;
        }
        return super.method_25404(key, scancode, mods);
    }

    @Override
    public void method_25432() {
        emiRestore();
        super.method_25432();
    }

    @Override
    public boolean method_25421() {
        return false;      // 开着集市也照常走世界 tick（原版村民界面就是这么做的）
    }

    /** 列头上两个小箭头：点一下翻一行（滚轮没做，先给个能用的） */
    private void scrollButtons(class_332 ctx, class_327 f, int x, int y) {
        int bx = x + colW() - 30;
        fill(ctx, bx, y, bx + 13, y + 13, 0xFFCFC0A0);
        fill(ctx, bx + 15, y, bx + 28, y + 13, 0xFFCFC0A0);
        text(ctx, f, "▲", bx + 3, y + 2, C_INK, false);
        text(ctx, f, "▼", bx + 18, y + 2, C_INK, false);
    }

    /** 返回 0=左列上 1=左列下 2=右列上 3=右列下，-1=没点中 */
    private int scrollHit(double mx, double my) {
        int y = this.field_2800 + 63;
        if (my < y || my >= y + 13) return -1;
        for (int c = 0; c < 2; c++) {
            int x = (c == 0 ? leftCol() : rightCol()) + colW() - 30;
            if (mx >= x && mx < x + 13) return c * 2;
            if (mx >= x + 15 && mx < x + 28) return c * 2 + 1;
        }
        return -1;
    }

    private static JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }
}