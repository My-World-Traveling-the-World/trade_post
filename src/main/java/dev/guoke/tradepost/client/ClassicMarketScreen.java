package dev.guoke.tradepost.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guoke.tradepost.market.BuyPayload;
import dev.guoke.tradepost.market.MarketPayload;
import dev.guoke.tradepost.market.SellPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1263;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_7923;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 集市面板（旧版自绘界面，兜底用；新版见 MarketScreen extends class_492）。纯客户端绘制，不碰 ScreenHandler（不可能复制物品）。
 *
 * 布局分三块，跟村民交易界面同一套空间逻辑（左货架 / 右柜台 / 底部背包）：
 *   左上 102x166  货架：一行一条交易，左图标 → 箭头 → 右图标，数字是原版角标
 *   右上 174x166  柜台：买页放数量与成交信息，卖页放天平（把背包的货放上去）
 *   底部 276x82   背包：9x4 格，卖页左键把一堆放到天平上、右键放一件
 *
 * 买：点货架一层直接成交（份数 1/2/3/4… 一次加一份）。
 * 卖：点亮货架或背包里的货 → 货跑到天平上 → 按"份"自动凑整，点兑换给钱；
 *     不够一份不成交，提示还差几件。零头留在天平上（背包里那几件也还在）。
 */
public class ClassicMarketScreen extends class_437 {

    private static final int W = 276;
    private static final int H = 250;
    private static final int TRADE_H = 166;
    private static final int SHELF = 102;

    /** 货架 7 层，行距 20px，第一层从 y=19 起（对齐原版 MerchantScreen 的行基线） */
    private static final int ROW_H = 20;
    private static final int ROW_TOP = 19;
    private static final int VIS_ROWS = 7;

    /** 行内三件：拿出去的 → 箭头 → 换回来的（坐标对齐原版 10 / 60 / 73） */
    private static final int GIVE_X = 10;
    private static final int ARROW_X = 60;
    private static final int GET_X = 73;

    /** 底部背包：9 列 x 4 行（3 行背包 + 1 行快捷栏），每格 18px */
    private static final int BAG_COLS = 9;
    private static final int BAG_ROWS = 4;
    private static final int SLOT = 18;
    private static final int BAG_PANEL_Y = 168;
    private static final int BAG_GRID_Y = 174;

    private static final int TAB_W = 31;
    private static final int TAB_H = 13;
    private static final String[] TABS = {"买", "卖"};

    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;

    private static final class_2960 TEX_ARROW =
            class_2960.method_60655("tradepost", "textures/gui/arrow.png");

    // 调色板取自 base_trades.png / base_counter.png
    private static final int C_INK = 0xFF2B1A0C;
    private static final int C_INK2 = 0xFF6B4A24;
    private static final int C_DIM = 0xFF8A6A4A;
    private static final int C_HL = 0x33FFD9A0;
    private static final int C_HL_SEL = 0x55FFD9A0;
    private static final int C_BTN = 0xFFD0A567;
    private static final int C_BTN_HOVER = 0xFFE2BE84;
    private static final int C_BTN_EDGE = 0xFF8A5B2E;
    private static final int C_BOARD = 0xE8D9BE8A;
    private static final int C_EDGE = 0xFF845937;
    private static final int C_EDGE_HI = 0xFFE3C089;
    private static final int C_SLOT = 0x55200F06;
    private static final int C_SLOT_HV = 0x66FFE0AE;
    private static final int C_WARN = 0xFF8B1E1E;

    private static final class_2960 TEX_SHELF = class_2960.method_60655("tradepost", "textures/gui/shelf.png");
    private static final class_2960 TEX_COUNTER = class_2960.method_60655("tradepost", "textures/gui/counter.png");

    private static final Map<String, class_1799> STACKS = new HashMap<>();

    private JsonObject root;
    private int tab = 0;
    private int left = 0;
    private int top = 0;
    private int scroll = 0;
    private final int[] scrollOf = {0, 0};
    /** 买页的份数：1 起，一次加一份 */
    private int buyQty = 1;
    /** 天平上那一堆：哪一行（-1 = 空） + 件数 */
    private int scaleRow = -1;
    private int scaleCount = 0;
    /** 红色提示（数量不足等），显示到什么时候 */
    private String hint = "";
    private long hintUntil = 0L;

    public ClassicMarketScreen(MarketPayload payload) {
        super(class_2561.method_43470("集市"));
        this.root = parse(payload.json());
    }

    public void setPayload(MarketPayload payload) {
        this.root = parse(payload.json());
        if (rows().size() == 0) {
            for (int i = 0; i < TABS.length; i++) {
                if (rowsOf(i).size() > 0) {
                    this.tab = i;
                    this.scroll = this.scrollOf[i];
                    break;
                }
            }
        }
        if (this.scaleRow >= rowsOf(1).size()) {
            this.scaleRow = -1;
            this.scaleCount = 0;
        }
    }

    private static JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private static JsonArray arr(JsonObject o, String key) {
        try {
            JsonArray a = o.getAsJsonArray(key);
            return a == null ? new JsonArray() : a;
        } catch (Throwable t) {
            return new JsonArray();
        }
    }

    private String str(String key, String fallback) {
        try {
            return root.get(key).getAsString();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private JsonArray rowsOf(int index) {
        return index == 0 ? arr(root, "buy") : arr(root, "sell");
    }

    private JsonArray rows() {
        return rowsOf(tab);
    }

    private JsonObject row(int i) {
        try {
            return rows().get(i).getAsJsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private String cell(int i, String key, String fallback) {
        try {
            return row(i).get(key).getAsString();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int intOf(JsonObject o, String key) {
        try {
            return o.get(key).getAsInt();
        } catch (Throwable t) {
            return 0;
        }
    }

    private boolean flag(int i, String key) {
        try {
            return row(i).get(key).getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    private void text(class_332 ctx, String s, int x, int y, int color) {
        ctx.method_27535(this.field_22793, class_2561.method_43470(s), x, y, color);
    }

    private void text(class_332 ctx, class_2561 s, int x, int y, int color) {
        ctx.method_27535(this.field_22793, s, x, y, color);
    }

    private int textWidth(class_2561 t) {
        return this.field_22793.method_27525(t);
    }

    private void centerText(class_332 ctx, String s, int cx, int y, int color) {
        class_2561 t = class_2561.method_43470(s);
        text(ctx, t, cx - textWidth(t) / 2, y, color);
    }

    private void rightText(class_332 ctx, String s, int rightX, int y, int color) {
        class_2561 t = class_2561.method_43470(s);
        text(ctx, t, rightX - textWidth(t), y, color);
    }

    /** 原版角标：白字带阴影压在图标右下角，超宽等比缩小 */
    private void badge(class_332 ctx, String s, int iconX, int iconY) {
        if (s == null || s.isEmpty()) return;
        int w = this.field_22793.method_27525(class_2561.method_43470(s));
        float sc = w > 15 ? Math.max(0.5f, 15.0f / w) : 1.0f;
        var m = ctx.method_51448();
        m.method_22903();
        m.method_22905(sc, sc, 1.0f);
        m.method_22904(0.0, 0.0, 300.0);
        int x = Math.round((iconX + 16) / sc) - w;
        int y = Math.round((iconY + 7) / sc);
        ctx.method_27535(this.field_22793, class_2561.method_43470(s), x + 1, y + 1, 0xFF2B1A0C);
        ctx.method_27535(this.field_22793, class_2561.method_43470(s), x, y, 0xFFFFFFFF);
        m.method_22909();
    }

    private void sayHint(String s) {
        this.hint = s;
        this.hintUntil = System.currentTimeMillis() + 2500L;
    }

    // ---- 几何 ----

    private int rowY(int k) {
        return this.top + ROW_TOP + k * ROW_H;
    }

    private int plateX() {
        return this.left + 106;
    }

    private int plateY() {
        return this.top + 56;
    }

    private int minusX() {
        return plateX() + 118;
    }

    private int plusX() {
        return plateX() + 136;
    }

    private int qtyLineY() {
        return plateY() + 26;
    }

    private int exchangeY() {
        return plateY() + 72;
    }

    private int bagX() {
        return this.left + 4;
    }

    private int bagPanelY() {
        return this.top + BAG_PANEL_Y;
    }

    private int gridX() {
        return this.left + 8;
    }

    private int gridY() {
        return this.top + BAG_GRID_Y;
    }

    /** 背包格子 i（0..35，前 27 是背包主体，后 9 是快捷栏）在玩家背包里的槽位号 */
    private static int slotIndex(int i) {
        return i < 27 ? i + 9 : i - 27;
    }

    private class_1263 inventory() {
        try {
            return this.field_22787.field_1724.method_31548();
        } catch (Throwable t) {
            return null;
        }
    }

    private class_1799 bagStack(int i) {
        class_1263 inv = inventory();
        if (inv == null) return class_1799.field_8037;
        try {
            return inv.method_5438(slotIndex(i));
        } catch (Throwable t) {
            return class_1799.field_8037;
        }
    }

    private int bagSlotAt(double mx, double my) {
        for (int i = 0; i < BAG_COLS * BAG_ROWS; i++) {
            int x = gridX() + (i % BAG_COLS) * SLOT;
            int y = gridY() + (i / BAG_COLS) * SLOT;
            if (in(mx, my, x, y, x + SLOT - 1, y + SLOT - 1)) return i;
        }
        return -1;
    }

    private int rowAt(double mx, double my) {
        if (mx < this.left + 4 || mx >= this.left + 98) return -1;
        for (int k = 0; k < VIS_ROWS; k++) {
            int i = this.scroll + k;
            if (i >= rows().size()) break;
            int y = rowY(k);
            if (my >= y && my < y + ROW_H) return i;
        }
        return -1;
    }

    private int tabAt(double mx, double my) {
        if (my < this.top - TAB_H || my >= this.top) return -1;
        for (int i = 0; i < TABS.length; i++) {
            int x = this.left + 4 + i * (TAB_W + 2);
            if (mx >= x && mx < x + TAB_W) return i;
        }
        return -1;
    }

    private static boolean in(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private int perOf(int i) {
        return Math.max(1, intOf(row(i), this.tab == 0 ? "count" : "lot"));
    }

    private int unitOf(int i) {
        return Math.max(1, intOf(row(i), this.tab == 0 ? "pay" : "price"));
    }

    /** 买页：这一行按当前份数成交的量 {件数, 绿宝石数}；换不了返回 null */
    private int[] buyAmounts(int i) {
        int per = perOf(i);
        int pay = unitOf(i);
        int stock = intOf(row(i), "stock");
        int lots = Math.min(buyQty, Math.min(64, stock / per));
        if (lots <= 0) return null;
        return new int[]{per * lots, pay * lots};
    }

    // ---- 绘制 ----

    @Override
    public void method_25394(class_332 ctx, int mouseX, int mouseY, float delta) {
        this.left = (this.field_22789 - W) / 2;
        this.top = (this.field_22790 - H) / 2;
        int n = rows().size();
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, n - VIS_ROWS)));
        this.scrollOf[this.tab] = this.scroll;

        ctx.method_25302(TEX_SHELF, this.left, this.top, 0, 0, SHELF, TRADE_H);
        ctx.method_25302(TEX_COUNTER, this.left + SHELF, this.top, 0, 0, W - SHELF, TRADE_H);

        for (int i = 0; i < TABS.length; i++) {
            tabButton(ctx, i, this.left + 4 + i * (TAB_W + 2), this.top - TAB_H, mouseX, mouseY);
        }
        String ind = str("ind", "");
        if (!ind.isEmpty()) {
            rightText(ctx, ind, this.left + W - 4, this.top - TAB_H + 3, 0xFFF0D6A6);
        }

        int hover = rowAt(mouseX, mouseY);
        for (int k = 0; k < VIS_ROWS; k++) {
            int i = this.scroll + k;
            if (i >= n) break;
            drawRow(ctx, i, k, i == hover);
        }
        scrollbar(ctx, n);
        if (n == 0) drawEmpty(ctx);

        plate(ctx, mouseX, mouseY, hover);
        bag(ctx, mouseX, mouseY);

        if (hover >= 0) {
            class_1799 stack = stackOf(cell(hover, "id", ""));
            if (!stack.method_7960()) {
                ctx.method_51437(this.field_22793, tooltipLines(hover), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        int bs = bagSlotAt(mouseX, mouseY);
        if (bs >= 0) {
            class_1799 st = bagStack(bs);
            if (!st.method_7960()) {
                List<class_2561> lines = new ArrayList<>(class_437.method_25408(this.field_22787, st));
                int r = sellRowOf(itemIdOf(st));
                if (this.tab == 1) {
                    if (r < 0) lines.add(class_2561.method_43470("本村不收这件货"));
                    else lines.add(class_2561.method_43470("每份 " + perOf(r) + " 件 · " + unitOf(r)
                            + " 绿宝石 · 左键放上天平"));
                }
                ctx.method_51437(this.field_22793, lines, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<class_2561> tooltipLines(int i) {
        List<class_2561> lines = new ArrayList<>(class_437.method_25408(this.field_22787, stackOf(cell(i, "id", ""))));
        int per = perOf(i);
        int unit = unitOf(i);
        String qname = cell(i, "qname", "");
        if (!qname.isEmpty()) lines.add(class_2561.method_43470("品质：" + qname));
        lines.add(class_2561.method_43470("每份 " + per + " 件 · " + unit + " 绿宝石"));
        if (this.tab == 0) {
            int[] amt = buyAmounts(i);
            int stock = intOf(row(i), "stock");
            lines.add(class_2561.method_43470(amt == null
                    ? "库存 " + stock + " 件 · 不够一份"
                    : "本次 " + amt[0] + " 件 · " + amt[1] + " 绿宝石 · 库存 " + stock + " 件"));
        } else {
            int can = intOf(row(i), "lots");
            lines.add(class_2561.method_43470("背包 " + intOf(row(i), "count") + " 件 · 可卖 " + can + " 份 · 点一下放上天平"));
        }
        return lines;
    }

    private void tabButton(class_332 ctx, int index, int x, int y, int mouseX, int mouseY) {
        boolean on = this.tab == index;
        boolean hover = mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H;
        int fill = on ? 0xFFC19859 : (hover ? 0xFF8E5A2E : 0xFF6D4327);
        ctx.method_25294(x, y, x + TAB_W, y + TAB_H, fill);
        ctx.method_25294(x, y, x + TAB_W, y + 1, on ? 0xFFE3C089 : 0xFF8A6034);
        ctx.method_25294(x, y + TAB_H - 1, x + TAB_W, y + TAB_H, C_BTN_EDGE);
        ctx.method_25294(x, y, x + 1, y + TAB_H, C_BTN_EDGE);
        ctx.method_25294(x + TAB_W - 1, y, x + TAB_W, y + TAB_H, C_BTN_EDGE);
        centerText(ctx, TABS[index], x + TAB_W / 2, y + 3, on ? C_INK : 0xFFD9BE93);
    }

    private void drawRow(class_332 ctx, int i, int k, boolean hover) {
        int y = rowY(k);
        boolean sel = this.tab == 1 && this.scaleRow == i;
        if (sel) ctx.method_25294(this.left + 3, y, this.left + 98, y + ROW_H - 1, C_HL_SEL);
        else if (hover) ctx.method_25294(this.left + 3, y, this.left + 98, y + ROW_H - 1, C_HL);

        class_1799 stack = stackOf(cell(i, "id", ""));
        class_1799 emerald = stackOf("minecraft:emerald");

        if (this.tab == 0) {
            int[] amt = buyAmounts(i);
            if (amt == null) {
                if (!stack.method_7960()) ctx.method_51427(stack, this.left + GET_X, y + 2);
                ctx.method_25294(this.left + GET_X + 1, y + 9, this.left + GET_X + 9, y + 10, C_DIM);
                return;
            }
            ctx.method_51427(emerald, this.left + GIVE_X, y + 2);
            badge(ctx, String.valueOf(amt[1]), this.left + GIVE_X, y + 2);
            ctx.method_25302(TEX_ARROW, this.left + ARROW_X, y + 5, 0, 0, 10, 9);
            if (!stack.method_7960()) ctx.method_51427(stack, this.left + GET_X, y + 2);
            badge(ctx, String.valueOf(amt[0]), this.left + GET_X, y + 2);
        } else {
            // 卖页：这一行的收购价（每份几件 → 几绿宝石），点一下把背包这类货放上天平
            int lots = intOf(row(i), "lots");
            if (!stack.method_7960()) ctx.method_51427(stack, this.left + GIVE_X, y + 2);
            badge(ctx, String.valueOf(perOf(i)), this.left + GIVE_X, y + 2);
            ctx.method_25302(TEX_ARROW, this.left + ARROW_X, y + 5, 0, 0, 10, 9);
            ctx.method_51427(emerald, this.left + GET_X, y + 2);
            badge(ctx, String.valueOf(unitOf(i)), this.left + GET_X, y + 2);
            if (lots <= 0) {
                ctx.method_25294(this.left + GIVE_X + 1, y + 9, this.left + GIVE_X + 9, y + 10, C_DIM);
            }
        }
    }

    private void scrollbar(class_332 ctx, int n) {
        int x = this.left + 95;
        int trackTop = this.top + ROW_TOP;
        int trackH = VIS_ROWS * ROW_H - 2;
        if (n > VIS_ROWS) {
            ctx.method_25294(x, trackTop, x + 3, trackTop + trackH, 0x30000000);
            int thumbH = Math.max(12, trackH * VIS_ROWS / n);
            int thumbY = trackTop + (trackH - thumbH) * this.scroll / Math.max(1, n - VIS_ROWS);
            ctx.method_25294(x, thumbY, x + 3, thumbY + thumbH, 0xCC845937);
            ctx.method_25294(x, thumbY, x + 1, thumbY + thumbH, 0xCCE3C089);
        } else {
            ctx.method_25294(x, trackTop, x + 1, trackTop + trackH, 0x40000000);
        }
    }

    private void drawEmpty(class_332 ctx) {
        text(ctx, this.tab == 0 ? "本村现在没有在售的货" : "背包里没有本村肯收的货",
                this.left + 8, this.top + 30, 0xFF4A2C14);
        text(ctx, this.tab == 0 ? "换个村子看看，商路通了货更多" : "去砍点木头、挖点石头再来",
                this.left + 8, this.top + 42, C_INK2);
    }

    /** 右上柜台：买页 = 数量与成交预览，卖页 = 天平 */
    private void plate(class_332 ctx, int mouseX, int mouseY, int hover) {
        int px = plateX();
        int py = plateY();
        int pw = W - 106 - 8;
        int ph = 104;
        board(ctx, px, py, pw, ph);

        String sub = str("sub", "");
        if (!sub.isEmpty()) text(ctx, sub, px + 7, py + 6, C_INK2);

        if (this.tab == 0) {
            text(ctx, "数量 ×" + buyQty + " 份", px + 7, qtyLineY(), C_INK);
            smallButton(ctx, minusX(), qtyLineY(), "-", mouseX, mouseY);
            smallButton(ctx, plusX(), qtyLineY(), "+", mouseX, mouseY);
            if (hover >= 0) {
                int[] amt = buyAmounts(hover);
                text(ctx, amt == null ? "库存不够一份" : "本次 " + amt[0] + " 件 · " + amt[1] + " 绿宝石",
                        px + 7, qtyLineY() + 22, amt == null ? C_WARN : C_INK);
            } else {
                text(ctx, "点货架上的货直接买", px + 7, qtyLineY() + 22, C_INK2);
            }
        } else {
            scale(ctx, px, py, pw, mouseX, mouseY);
        }
        if (System.currentTimeMillis() < this.hintUntil) {
            text(ctx, this.hint, px + 7, py + ph - 12, C_WARN);
        }
    }

    /** 天平：托盘上的货 + 换算出的绿宝石 + 兑换按钮 */
    private void scale(class_332 ctx, int px, int py, int pw, int mouseX, int mouseY) {
        int trayX = px + 8;
        int trayY = py + 22;
        ctx.method_25294(trayX, trayY, trayX + 44, trayY + 44, C_SLOT);
        edge(ctx, trayX, trayY, 44, 44);
        boolean empty = this.scaleRow < 0 || this.scaleCount <= 0;

        if (empty) {
            text(ctx, "把背包里的货", trayX + 50, trayY + 12, C_INK2);
            text(ctx, "放上来", trayX + 50, trayY + 24, C_INK2);
            return;
        }
        int i = this.scaleRow;
        class_1799 st = stackOf(cell(i, "id", ""));
        if (!st.method_7960()) ctx.method_51427(st, trayX + 14, trayY + 14);
        badge(ctx, String.valueOf(this.scaleCount), trayX + 14, trayY + 14);

        int lot = perOf(i);
        int price = unitOf(i);
        int lots = this.scaleCount / lot;
        int pay = lots * price;
        ctx.method_25302(TEX_ARROW, trayX + 50, trayY + 18, 0, 0, 10, 9);
        ctx.method_51427(stackOf("minecraft:emerald"), trayX + 66, trayY + 14);
        badge(ctx, String.valueOf(pay), trayX + 66, trayY + 14);

        text(ctx, "每份 " + lot + " 件", trayX + 50, trayY + 26, C_INK2);
        text(ctx, lots + " 份 · " + pay + " 绿宝石", trayX + 50, trayY + 38, C_INK2);
        exchangeButton(ctx, px + 8, exchangeY(), pw - 16, mouseX, mouseY);
    }

    private void exchangeButton(class_332 ctx, int x, int y, int w, int mouseX, int mouseY) {
        boolean hv = in(mouseX, mouseY, x, y, x + w, y + 16);
        ctx.method_25294(x, y, x + w, y + 16, hv ? C_BTN_HOVER : C_BTN);
        ctx.method_25294(x, y, x + w, y + 1, 0xFFEFD3A0);
        ctx.method_25294(x, y + 15, x + w, y + 16, C_BTN_EDGE);
        centerText(ctx, "兑换", x + w / 2, y + 4, C_INK);
    }

    private void bag(class_332 ctx, int mouseX, int mouseY) {
        int bx = bagX();
        int by = bagPanelY();
        int bw = W - 8;
        int bh = 78;
        board(ctx, bx, by, bw, bh);

        int bs = bagSlotAt(mouseX, mouseY);
        class_1263 inv = inventory();
        for (int i = 0; i < BAG_COLS * BAG_ROWS; i++) {
            int x = gridX() + (i % BAG_COLS) * SLOT;
            int y = gridY() + (i / BAG_COLS) * SLOT;
            ctx.method_25294(x, y, x + SLOT - 1, y + SLOT - 1, i == bs ? C_SLOT_HV : C_SLOT);
            ctx.method_25294(x, y, x + SLOT - 1, y + 1, 0x33FFE9C0);
            ctx.method_25294(x, y, x + 1, y + SLOT - 1, 0x33FFE9C0);
            if (inv == null) continue;
            class_1799 st;
            try {
                st = inv.method_5438(slotIndex(i));
            } catch (Throwable t) {
                continue;
            }
            if (st.method_7960()) continue;
            ctx.method_51427(st, x + 1, y + 1);
            int show = st.method_7947();
            // 已经放上天平的那部分，从背包里"看不见"（其实还在背包里，关面板就回来）
            int earmarked = earmarkedFor(itemIdOf(st));
            if (earmarked > 0) {
                int take = Math.min(show, earmarked);
                show -= take;
                earmarked -= take;
            }
            if (show > 0) badge(ctx, String.valueOf(show), x + 1, y + 1);
            else ctx.method_25294(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, 0x55140A04);
        }
        if (this.tab == 1) {
            int rx = gridX() + BAG_COLS * SLOT + 6;
            text(ctx, "左键：整堆放上天平", rx, by + 8, C_INK2);
            text(ctx, "右键：放 1 件", rx, by + 20, C_INK2);
            text(ctx, "再点天平：全部收回", rx, by + 32, C_INK2);
        } else {
            int rx = gridX() + BAG_COLS * SLOT + 6;
            text(ctx, "这里是你的背包", rx, by + 8, C_INK2);
            text(ctx, "买到的货直接进背包", rx, by + 20, C_INK2);
            text(ctx, "背包满会掉在脚边", rx, by + 32, C_INK2);
        }
    }

    private void board(class_332 ctx, int x, int y, int w, int h) {
        ctx.method_25294(x, y, x + w, y + h, C_BOARD);
        ctx.method_25294(x, y, x + w, y + 1, C_EDGE_HI);
        ctx.method_25294(x, y + h - 1, x + w, y + h, C_EDGE);
        ctx.method_25294(x, y, x + 1, y + h, C_EDGE);
        ctx.method_25294(x + w - 1, y, x + w, y + h, C_EDGE);
    }

    private void edge(class_332 ctx, int x, int y, int w, int h) {
        ctx.method_25294(x, y, x + w, y + 1, 0x558A5B2E);
        ctx.method_25294(x, y + h - 1, x + w, y + h, 0x558A5B2E);
        ctx.method_25294(x, y, x + 1, y + h, 0x558A5B2E);
        ctx.method_25294(x + w - 1, y, x + w, y + h, 0x558A5B2E);
    }

    private void smallButton(class_332 ctx, int x, int y, String label, int mouseX, int mouseY) {
        boolean hv = in(mouseX, mouseY, x, y - 1, x + 14, y + 12);
        ctx.method_25294(x, y - 1, x + 14, y + 12, hv ? C_BTN_HOVER : C_BTN);
        ctx.method_25294(x, y - 1, x + 14, y, 0xFFEFD3A0);
        ctx.method_25294(x, y + 11, x + 14, y + 12, C_BTN_EDGE);
        centerText(ctx, label, x + 7, y + 1, C_INK);
    }

    private static class_1799 stackOf(String id) {
        if (id == null || id.isEmpty()) return class_1799.field_8037;
        class_1799 cached = STACKS.get(id);
        if (cached != null) return cached;
        class_1799 made = class_1799.field_8037;
        try {
            int c = id.indexOf(':');
            class_2960 rl = c < 0 ? class_2960.method_60655("minecraft", id)
                    : class_2960.method_60655(id.substring(0, c), id.substring(c + 1));
            made = new class_1799(class_7923.field_41178.method_10223(rl));
        } catch (Throwable ignored) {
            // 物品不存在就当空格子
        }
        STACKS.put(id, made);
        return made;
    }

    private static String itemIdOf(class_1799 st) {
        try {
            return class_7923.field_41178.method_10221(st.method_7909()).toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 卖页里某一行的物品在卖页列表中的下标（-1 = 本村不收） */
    private int sellRowOf(String id) {
        JsonArray sell = rowsOf(1);
        for (int i = 0; i < sell.size(); i++) {
            try {
                if (id.equals(sell.get(i).getAsJsonObject().get("id").getAsString())) return i;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /** 天平上这种货占了多少件（背包显示时要扣掉） */
    private int earmarkedFor(String id) {
        if (this.scaleRow < 0 || this.scaleCount <= 0) return 0;
        JsonArray sell = rowsOf(1);
        try {
            String staged = sell.get(this.scaleRow).getAsJsonObject().get("id").getAsString();
            if (staged.equals(id)) return this.scaleCount;
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // ---- 操作 ----

    private void stageRow(int i) {
        if (i < 0 || i >= rowsOf(1).size()) return;
        int have = intOf(row(i), "count");
        if (this.scaleRow != i) {
            this.scaleRow = i;
            this.scaleCount = 0;
        }
        this.scaleCount = Math.min(have, this.scaleCount + Math.max(1, have));
        if (this.scaleCount <= 0) {
            this.scaleRow = -1;
            sayHint("背包里没有这件货");
        }
    }

    private void stageStack(int bagSlot, int amount) {
        class_1799 st = bagStack(bagSlot);
        if (st.method_7960()) return;
        int r = sellRowOf(itemIdOf(st));
        if (r < 0) {
            sayHint("本村不收这件货");
            return;
        }
        int have = intOf(row(r), "count");
        if (this.scaleRow != r) {
            this.scaleRow = r;
            this.scaleCount = 0;
        }
        int add = amount <= 0 ? st.method_7947() : Math.min(amount, st.method_7947());
        this.scaleCount = Math.min(have, this.scaleCount + add);
    }

    private void clearScale() {
        this.scaleRow = -1;
        this.scaleCount = 0;
    }

    private void exchange() {
        if (this.scaleRow < 0 || this.scaleCount <= 0) {
            sayHint("天平上还没有货");
            return;
        }
        int i = this.scaleRow;
        String id = cell(i, "id", "");
        int lot = perOf(i);
        int price = unitOf(i);
        int lots = this.scaleCount / lot;
        if (lots <= 0) {
            sayHint("数量不足：还差 " + (lot - this.scaleCount) + " 件才能凑一份");
            return;
        }
        int sold = lots * lot;
        ClientPlayNetworking.send(new SellPayload(id, sold, intOf(row(i), "q")));
        this.scaleCount -= sold;
        if (this.scaleCount <= 0) clearScale();
    }

    private void doRow(int i) {
        if (this.tab == 0) {
            if (!flag(i, "sold")) ClientPlayNetworking.send(new BuyPayload(cell(i, "id", ""), buyQty));
        } else {
            stageRow(i);
        }
    }

    @Override
    public boolean method_25402(double mx, double my, int button) {
        int t = tabAt(mx, my);
        if (t >= 0 && button == 0) {
            this.scrollOf[this.tab] = this.scroll;
            this.tab = t;
            this.scroll = this.scrollOf[t];
            return true;
        }
        if (this.tab == 0) {
            if (button == 0) {
                if (in(mx, my, minusX(), qtyLineY() - 1, minusX() + 14, qtyLineY() + 12)) {
                    this.buyQty = Math.max(1, this.buyQty - 1);
                    return true;
                }
                if (in(mx, my, plusX(), qtyLineY() - 1, plusX() + 14, qtyLineY() + 12)) {
                    this.buyQty = Math.min(64, this.buyQty + 1);
                    return true;
                }
            }
        }
        if (this.tab == 1) {
            int py = exchangeY();
            if (button == 0 && in(mx, my, plateX() + 8, py, plateX() + 8 + (W - 106 - 8) - 16, py + 16)) {
                exchange();
                return true;
            }
            int trayX = plateX() + 8;
            int trayY = plateY() + 22;
            if (button == 0 && in(mx, my, trayX, trayY, trayX + 44, trayY + 44)) {
                clearScale();
                sayHint("收回背包");
                return true;
            }
        }
        int bs = bagSlotAt(mx, my);
        if (bs >= 0) {
            if (this.tab == 1) stageStack(bs, button == 0 ? 0 : 1);
            return true;
        }
        int i = rowAt(mx, my);
        if (i >= 0) {
            doRow(i);
            return true;
        }
        return true;
    }

    @Override
    public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int n = rows().size();
        if (n > VIS_ROWS && verticalAmount != 0.0) {
            int step = verticalAmount > 0 ? -1 : 1;
            this.scroll = Math.max(0, Math.min(this.scroll + step, n - VIS_ROWS));
            this.scrollOf[this.tab] = this.scroll;
        }
        return true;
    }

    @Override
    public boolean method_25404(int keyCode, int scanCode, int modifiers) {
        int n = rows().size();
        int page = Math.max(1, VIS_ROWS - 1);
        int next = this.scroll;
        if (keyCode == KEY_UP) next -= 1;
        else if (keyCode == KEY_DOWN) next += 1;
        else if (keyCode == KEY_PAGE_UP) next -= page;
        else if (keyCode == KEY_PAGE_DOWN) next += page;
        else return super.method_25404(keyCode, scanCode, modifiers);
        this.scroll = Math.max(0, Math.min(next, Math.max(0, n - VIS_ROWS)));
        this.scrollOf[this.tab] = this.scroll;
        return true;
    }

    @Override
    public boolean method_25421() {
        return false;
    }
}
