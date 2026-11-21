package dev.guoke.tradepost.market;

import dev.guoke.tradepost.mc.Bridge;
import net.minecraft.class_1263;
import net.minecraft.class_1657;
import net.minecraft.class_1725;
import net.minecraft.class_1728;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_1915;
import net.minecraft.class_3468;
import net.minecraft.class_9306;

/**
 * 天平右边的"第二格货款"：跟 2 号格一样是<b>真容器槽</b>，摆出来就能看见，点一下就能拿。
 *
 * <p>原版结果格只放得下一摞（64 件），一笔货款超过 64 块绿宝石块（576）就摆不下。
 * 所以货款按 {@link Pay#split(int)} 摊成两摞：结果格一摞、这一格一摞，两边都是"真钱"。
 *
 * <p>拿这一格 = 完成这一笔交易（跟点 2 号格是同一件事）：天平上的货收走、2 号格那份钱也一起给你。
 * 天平上的货不齐时这一格拿不动 —— 那是原版的规矩（货不齐本来就不能成交），不是额外的锁。
 */
public class PayoutSlot extends class_1735 {

    private final class_1728 handler;

    public PayoutSlot(class_1263 inv, int index, int x, int y, class_1728 handler) {
        super(inv, index, x, y);
        this.handler = handler;
    }

    @Override
    public boolean method_7680(class_1799 stack) {
        return false;                       // 只能出不能进
    }

    @Override
    public boolean method_7674(class_1657 player) {
        return ready();                     // 天平上的货摆齐了才拿得动
    }

    /** 天平 0/1 号槽里有没有这一行要的货（件数够不够） */
    private boolean ready() {
        try {
            class_1914 offer = offer();
            if (offer == null) return false;
            class_9306 cost = offer.method_57556();
            if (cost == null) return false;
            class_1792 item = cost.comp_2424().comp_349();
            int want = Math.max(1, cost.comp_2425());
            int have = count(0, item) + count(1, item);
            return have >= want;
        } catch (Throwable t) {
            return false;
        }
    }

    private int count(int slot, class_1792 item) {
        try {
            class_1799 st = inv().method_5438(slot);
            if (st.method_7960() || st.method_7909() != item) return 0;
            return st.method_7947();
        } catch (Throwable t) {
            return 0;
        }
    }

    private class_1725 inv() {
        return dev.guoke.tradepost.mc.MarketMerchant.merchantInv(this.handler);
    }

    private class_1914 offer() {
        class_1725 inv = inv();
        return inv == null ? null : inv.method_7642();
    }

    @Override
    public void method_7667(class_1657 player, class_1799 stack) {
        try {
            if (player == null || player.method_37908() == null) return;
            if (player.method_37908().method_8608()) return;        // 客户端只是预测，真正的账在服务端
            class_1725 inv = inv();
            class_1914 offer = inv == null ? null : inv.method_7642();
            if (offer == null) return;
            class_9306 cost = offer.method_57556();
            if (cost == null) return;
            class_1792 item = cost.comp_2424().comp_349();
            int want = Math.max(1, cost.comp_2425());
            if (count(0, item) + count(1, item) < want) return;
            take(0, item, want);
            // 2 号格那份钱也一起给（它是同一笔货款的前半截）
            class_1799 first = inv.method_5438(2);
            if (!first.method_7960()) {
                Bridge.giveOrDrop((net.minecraft.class_3222) player, first.method_7972());
                inv.method_5447(2, class_1799.field_8037);
            }
            class_1915 merchant = dev.guoke.tradepost.mc.MarketMerchant.merchantOf(this.handler);
            if (merchant != null) {
                merchant.method_8262(offer);                        // 使用次数 +1（顺带记账）
                merchant.method_19271(merchant.method_19269() + Math.max(0, -offer.method_19279()) / 10);
            }
            player.method_7281(class_3468.field_15378);
        } catch (Throwable t) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 第二格成交失败", t);
        }
    }

    /** 从天平某槽里按件数扣掉 item（不够就跨到另一槽） */
    private void take(int slot, class_1792 item, int need) {
        class_1725 inv = inv();
        if (inv == null) return;
        int left = need;
        for (int i = 0; i < 2 && left > 0; i++) {
            class_1799 st = inv.method_5438(i);
            if (st.method_7960() || st.method_7909() != item) continue;
            int n = Math.min(left, st.method_7947());
            class_1799 rest = st.method_7972();
            rest.method_7939(st.method_7947() - n);
            inv.method_5447(i, rest.method_7947() <= 0 ? class_1799.field_8037 : rest);
            left -= n;
        }
    }

    /** 一格容器：只存这一摞（两端各造一个，内容由服务端同步过来） */
    public static final class Bag implements class_1263 {

        private class_1799 stack = class_1799.field_8037;

        @Override
        public int method_5439() {
            return 1;
        }

        @Override
        public boolean method_5442() {
            return this.stack.method_7960();
        }

        @Override
        public class_1799 method_5438(int index) {
            return index == 0 ? this.stack : class_1799.field_8037;
        }

        @Override
        public class_1799 method_5434(int index, int amount) {
            if (index != 0 || this.stack.method_7960() || amount <= 0) return class_1799.field_8037;
            int n = Math.min(amount, this.stack.method_7947());
            class_1799 out = this.stack.method_7972();
            out.method_7939(n);
            this.stack.method_7939(this.stack.method_7947() - n);
            if (this.stack.method_7947() <= 0) this.stack = class_1799.field_8037;
            return out;
        }

        @Override
        public class_1799 method_5441(int index) {
            if (index != 0) return class_1799.field_8037;
            class_1799 out = this.stack;
            this.stack = class_1799.field_8037;
            return out;
        }

        @Override
        public void method_5447(int index, class_1799 s) {
            this.stack = s == null ? class_1799.field_8037 : s;
        }

        @Override
        public void method_5431() {
            // 没东西要标脏
        }

        @Override
        public boolean method_5443(class_1657 player) {
            return true;
        }

        @Override
        public void method_5448() {
            this.stack = class_1799.field_8037;
        }
    }
}
