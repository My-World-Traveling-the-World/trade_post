package dev.guoke.tradepost.market;

import java.util.ArrayList;
import java.util.List;

/**
 * 酿造铺的药水：一瓶一档，效果与形态按（村, 周, 等级）定，同一天重开不变。
 *
 * 以前药水行只有 id（minecraft:potion）和数量，没有 potion_contents，
 * 买到的其实是"水瓶"——没有效果。这里按等级出真药水：
 *   L1 普通药水 → L3 喷溅 + 药箭 → L4 滞留药水。
 * 药箭一份 8 支，价按一发算。
 */
public final class PotionShop {

    /** 一行药：code 是面板回传的购买码 potion:效果|形态 */
    public record Potion(String effect, String form, String item, int count, int price, int quota) {
        public String code() {
            return "potion:" + effect + "|" + form;
        }
    }

    private record Family(String path, double price, boolean strong, boolean longer) {
    }

    /** 药水家族：path 是原版药水的 base id，价格按普通形态算 */
    private static final Family[] FAMILIES = {
            new Family("healing", 5, true, false),
            new Family("regeneration", 8, true, true),
            new Family("strength", 7, true, true),
            new Family("swiftness", 5, true, true),
            new Family("fire_resistance", 5, false, true),
            new Family("water_breathing", 5, false, true),
            new Family("night_vision", 5, false, true),
            new Family("invisibility", 10, false, true),
            new Family("leaping", 4, true, true),
            new Family("slow_falling", 7, false, true),
            new Family("poison", 4, true, true),
            new Family("harming", 5, true, false),
            new Family("turtle_master", 12, true, true)
    };

    private record Form(String key, String item, int minLevel, double mult, int count, int quota) {
    }

    private static final Form[] FORMS = {
            new Form("potion", "minecraft:potion", 1, 1.0, 1, 3),
            new Form("splash", "minecraft:splash_potion", 3, 1.6, 1, 3),
            new Form("arrow", "minecraft:tipped_arrow", 3, 1.4, 8, 4),
            new Form("lingering", "minecraft:lingering_potion", 4, 2.2, 1, 2)
    };

    private PotionShop() {
    }

    /** 本村今天摆哪几瓶（最多 3 行，按等级解锁形态） */
    public static List<Potion> list(String vkey, int level, long day) {
        List<Potion> out = new ArrayList<>();
        if (level <= 0) return out;
        int lv = Math.min(level, 5);
        long vh = vkey == null ? 0 : vkey.hashCode();
        long week = day / 7;
        int row = 0;
        for (Form f : FORMS) {
            if (f.minLevel > lv || row >= 3) continue;
            long h = Hashing.of(vh, week, lv, row, 307);
            Family fam = FAMILIES[Hashing.below(FAMILIES.length, vh, week, lv, row, 311)];
            // L1–2 只有普通；L3 起一半概率强化；L4 起再一半概率延时
            boolean strong = f.minLevel < 4 && fam.strong && lv >= 3 && (h & 1L) != 0;
            boolean longer = !strong && fam.longer && lv >= 4 && (h & 2L) != 0;
            String prefix = strong ? "strong_" : (longer ? "long_" : "");
            String effect = "minecraft:" + prefix + fam.path;
            double p = fam.price * f.mult * VillageLevel.buyMul(lv)
                    * (strong ? 1.5 : (longer ? 1.3 : 1.0));
            out.add(new Potion(effect, f.key, f.item, f.count, Math.max(1, (int) Math.round(p)), f.quota));
            row++;
        }
        return out;
    }

    /** 面板回传的码 → 今天这瓶；对不上就是过期/伪造的，直接 null */
    public static Potion find(String vkey, int level, long day, String code) {
        for (Potion p : list(vkey, level, day)) {
            if (p.code().equals(code)) return p;
        }
        return null;
    }
}
