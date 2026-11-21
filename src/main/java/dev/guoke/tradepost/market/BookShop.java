package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.List;

/**
 * 学术铺的附魔书：一本一档，附魔种类与等级按（村, 周, 等级）定，同一天重开不变。
 *
 * 以前书架上摆的是"白板附魔书"——id 对，但 stored_enchantments 是空的，
 * 买回去打不开也附不了。这里把"卖哪本"变成确定性生成，配合 Goods 拼 NBT 输出真书。
 *
 * 等级门槛：L1 只有基础附魔，L3 起出珍稀（时运/精准/抢夺），L5 才卖经验修补。
 * 每天每行有额度，免得一本书无限刷。
 */
public final class BookShop {

    /** 一行书：code 是面板回传的购买码 book:附魔|等级 */
    public record Book(String ench, int level, int price, int quota) {
        public String code() {
            return "book:" + ench + "|" + level;
        }
    }

    private record Def(String ench, double price, int minLevel, int maxLvl) {
    }

    /** 附魔池：价格是 I 级基准价（绿宝石），珍稀的贵，经验修补最贵 */
    private static final Def[] POOL = {
            new Def("minecraft:sharpness", 5, 1, 5),
            new Def("minecraft:protection", 5, 1, 4),
            new Def("minecraft:efficiency", 5, 1, 5),
            new Def("minecraft:unbreaking", 6, 1, 3),
            new Def("minecraft:power", 5, 1, 5),
            new Def("minecraft:feather_falling", 4, 2, 4),
            new Def("minecraft:knockback", 4, 2, 2),
            new Def("minecraft:smite", 4, 2, 5),
            new Def("minecraft:bane_of_arthropods", 4, 2, 5),
            new Def("minecraft:respiration", 6, 2, 3),
            new Def("minecraft:thorns", 7, 3, 3),
            new Def("minecraft:fire_aspect", 9, 3, 2),
            new Def("minecraft:looting", 10, 3, 3),
            new Def("minecraft:depth_strider", 7, 3, 3),
            new Def("minecraft:aqua_affinity", 6, 3, 1),
            new Def("minecraft:swift_sneak", 10, 4, 3),
            new Def("minecraft:soul_speed", 8, 4, 3),
            new Def("minecraft:fortune", 14, 4, 3),
            new Def("minecraft:silk_touch", 12, 4, 1),
            new Def("minecraft:mending", 18, 5, 1)
    };

    /** 等级倍率：I / II / III */
    private static final double[] LMULT = {1.0, 1.0, 1.8, 3.0};

    private BookShop() {
    }

    /** 本村今天摆哪几本书（行数随等级，最多 4 行） */
    public static List<Book> list(String vkey, int level, long day) {
        List<Book> out = new ArrayList<>();
        if (level <= 0) return out;
        int lv = Math.min(level, 5);
        int rows = Math.min(4, lv);
        long vh = vkey == null ? 0 : vkey.hashCode();
        long week = day / 7;

        List<Def> pool = new ArrayList<>();
        for (Def d : POOL) {
            if (d.minLevel <= lv) pool.add(d);
        }
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = Hashing.below(i + 1, vh, week, lv, i, 211);
            Def t = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, t);
        }

        int quota = lv <= 2 ? 1 : 2;
        for (int r = 0; r < rows && r < pool.size(); r++) {
            Def d = pool.get(r);
            long h = Hashing.of(vh, week, lv, r, 213);
            int want = lv <= 2 ? 1 : (lv == 3 ? 1 + (int) (h & 1L) : (lv == 4 ? 2 : 2 + (int) (h % 2)));
            int lvl = Math.max(1, Math.min(d.maxLvl, Math.min(3, want)));
            out.add(new Book(d.ench, lvl, priceOf(d, lvl, lv), quota));
        }
        return out;
    }

    /** 面板回传的码 → 今天这本；对不上就是过期/伪造的，直接 null */
    public static Book find(String vkey, int level, long day, String code) {
        for (Book b : list(vkey, level, day)) {
            if (b.code().equals(code)) return b;
        }
        return null;
    }

    private static int priceOf(Def d, int lvl, int villageLevel) {
        double p = value(d.ench, lvl) * VillageLevel.buyMul(villageLevel);
        return Math.max(1, (int) Math.round(p));
    }

    /**
     * 一本附魔书的"基准价"：I 级价 × 等级倍率，不含村庄倍率。
     * 收购页按这个价的 75% 收（跟其它货同一个口径），所以同一本书永远是买贵卖便宜。
     */
    public static double value(String ench, int level) {
        if (ench == null || ench.isEmpty()) return 0;
        for (Def d : POOL) {
            if (d.ench.equals(ench)) return d.price * LMULT[Math.max(0, Math.min(3, level))];
        }
        return 0;
    }

    /**
     * 表里没有的附魔（模组附魔、以后版本新加的）按这个 I 级基准价算。
     * 以前 value() 返回 0 就等于"这本不收" —— 玩家拿到一把锋利 V 的附魔书却无处可卖。
     */
    public static final double UNKNOWN_BASE = 6.0;

    /** 基准价，但认不出的附魔也照等级给个价（不再当 0） */
    public static double valueOr(String ench, int level) {
        double v = value(ench, level);
        if (v > 0) return v;
        if (ench == null || ench.isEmpty()) return 0;
        return UNKNOWN_BASE * LMULT[Math.max(0, Math.min(3, level))];
    }

    /**
     * 面板码 → 基准价。两种写法：
     * <ul><li>{@code book:附魔|等级} —— 单附魔</li>
     * <li>{@code book:附魔@等级;附魔@等级} —— 一本多附魔，价按各附魔之和</li></ul>
     * 白板返回 0。
     */
    public static double valueOf(String code) {
        if (code == null || !code.startsWith("book:")) return 0;
        String body = code.substring(5);
        if (body.indexOf(';') >= 0) {
            double sum = 0;
            for (String part : body.split(";")) {
                int at = part.indexOf('@');
                if (at <= 0) continue;
                try {
                    sum += valueOr(part.substring(0, at), Integer.parseInt(part.substring(at + 1)));
                } catch (NumberFormatException ignored) {
                    // 这一项认不出就不算它
                }
            }
            return sum;
        }
        int bar = body.lastIndexOf('|');
        if (bar < 0) return 0;
        try {
            return valueOr(body.substring(0, bar), Integer.parseInt(body.substring(bar + 1)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
