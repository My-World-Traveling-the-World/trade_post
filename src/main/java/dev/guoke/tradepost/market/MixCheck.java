package dev.guoke.tradepost.market;

import dev.guoke.tradepost.mc.Bridge;
import net.minecraft.class_1799;
import net.minecraft.class_1914;
import net.minecraft.class_9306;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 混币自检：绿宝石与绿宝石块按价值混着付，算得对不对。
 *
 * <p>这些东西没有世界也能验：{@link Pay#value}、{@link Pay#split}、{@link Pay#price}
 * 都是纯函数，只碰 ItemStack / ItemCost。游戏里跑 `/tradepost mix` 就能拿到一张
 * ✔/✘ 清单 —— 改完价格、改完面额策略，先跑它，别等玩家在柜台前发现少给了钱。
 */
public final class MixCheck {

    private MixCheck() {
    }

    private static class_1799 emeralds(int n) {
        return new class_1799(Pay.emeraldItem(), n);
    }

    private static class_1799 blocks(int n) {
        return new class_1799(Pay.blockItem(), n);
    }

    /** 一格东西怎么写 —— "7 块" / "13 颗" / "空" */
    private static String describe(class_1799 st) {
        if (st == null || st.method_7960()) return "空";
        int n = st.method_7947();
        return (st.method_7909() == Pay.blockItem() ? n + " 块" : n + " 颗");
    }

    /** 造一条报价：主成本 b 块、第二成本 e 颗（0 = 没有第二成本） */
    private static class_1914 offer(int b, int e) {
        class_9306 first = new class_9306(Pay.blockItem(), Math.max(1, b));
        Optional<class_9306> second = e > 0 ? Optional.of(new class_9306(Pay.emeraldItem(), e))
                : Optional.empty();
        return new class_1914(first, second, emeralds(1), 1, 1, 1.0f);
    }

    /** 跑一遍，返回每行的 ✔/✘ 文本 */
    public static List<String> run() {
        List<String> out = new ArrayList<>();
        int[] bad = {0};
        java.util.function.BiConsumer<String, Boolean> chk = (name, ok) -> {
            out.add((ok ? "\u2714 " : "\u2718 ") + name);
            if (!ok) bad[0]++;
        };

        // ── 折算：块 = 9 颗 ──
        chk.accept("1 颗绿宝石 = 1", Pay.value(emeralds(1)) == 1);
        chk.accept("1 块绿宝石 = 9", Pay.value(blocks(1)) == 9);
        chk.accept("1 块 + 4 颗 = 13", Pay.value(blocks(1)) + Pay.value(emeralds(4)) == 13);
        chk.accept("8 块 = 72", Pay.value(blocks(8)) == 72);
        class_1799 dirt = new class_1799(Bridge.item("minecraft:dirt"), 5);
        chk.accept("不是钱的东西算 0（泥土 ×5）", Pay.value(dirt) == 0);

        // ── 面额拆分：结果格 + 第二格 ──
        int[][] cases = {
                {13, 13, 0},      // 小额：全散钱
                {64, 64, 0},      // 正好一摞散钱
                {65, 65, 0},      // 过了一摞也还是一摞（单格 9999）
                {70, 70, 0},
                {126, 126, 0},
                {576, 576, 0},
                {600, 600, 0},
                {1620, 1620, 0},  // 下界合金块：以前超过 639 根本摆不下
                {1152, 1152, 0}
        };
        for (int[] c : cases) {
            int amount = c[0];
            class_1799[] p = Pay.split(amount);
            String got = describe(p[0]) + " / " + describe(p[1]);
            String want = (c[1] == 0 ? "空" : c[1] + " 颗") + " / " + (c[2] == 0 ? "空" : c[2] + " 颗");
            chk.accept(amount + " 摆成 " + want, got.equals(want));
            chk.accept(amount + " 两格合计 = " + amount, Pay.shown(amount) == amount);
        }

        // ── 报价读价：块和颗要加在一起 ──
        chk.accept("报价 8 块 = 72", Pay.price(offer(8, 0)) == 72);
        chk.accept("报价 8 块 + 5 颗 = 77", Pay.price(offer(8, 5)) == 77);
        chk.accept("报价 1 块 + 4 颗 = 13", Pay.price(offer(1, 4)) == 13);

        out.add("\u2014\u2014 共 " + (out.size() - 1) + " 项，失败 " + bad[0] + " 项");
        return out;
    }
}
