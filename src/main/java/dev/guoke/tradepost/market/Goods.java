package dev.guoke.tradepost.market;

import com.google.gson.JsonObject;
import net.minecraft.class_1799;
import net.minecraft.class_2487;
import net.minecraft.class_2960;
import net.minecraft.class_7225;
import net.minecraft.class_7923;

/**
 * 组件货的造物器：面板上一行 json 说清楚"这是什么货"，这里把它拼成真物品。
 *
 * 为什么必须拼：真容器（原版商人界面）里玩家是从天平格子里把货拿走的，
 * 客户端列表图标也是同一份物品。只要报价里是白板，玩家摸到的就是白板 ——
 * 酒的 brew_level、附魔书的 stored_enchantments、药水的 potion_contents 都在组件里，
 * 光有 id 和数量等于卖了个空壳。
 *
 * 手法跟 GearStack 一样：拼 NBT 再用 ItemStack.fromNbt 反序列化，
 * 不碰 class_9304/class_9309 那套 builder，也不依赖具体模组的注册表类。
 */
public final class Goods {

    private Goods() {
    }

    /**
     * 主币栈：>64 绿宝石时改用绿宝石块（9 换 1）。
     *
     * <p>原版 {@code class_1914#method_19272} 会把成本数量夹到物品堆叠上限，
     * 一旦夹过就会跟原价比较出"差值"，于是在列表里画一条划线价 —— 看着像打折，
     * 实际照价扣款。拆成块之后每摞都不超过 64，划线自然消失。
     */
    public static class_1799 money(int emeralds) {
        int e = Math.max(1, emeralds);
        if (e <= 64) return GearStack.plain("minecraft:emerald", e);
        int blocks = Math.max(1, Math.min(64, e / 9));
        return GearStack.plain("minecraft:emerald_block", blocks);
    }

    /**
     * money() 的第二槽：≤64 的零头用绿宝石；超过 64 块的部分再补一摞块。
     * 576 以内（64 块）能精确表示；更大的价由 offerPrice 凑成 9 的倍数，两槽刚好摆下。
     */
    public static class_1799 moneyRest(int emeralds) {
        int e = Math.max(0, emeralds);
        if (e <= 64) return class_1799.field_8037;
        int blocks = e / 9;
        int rem = e % 9;
        if (blocks <= 64) {
            return rem <= 0 ? class_1799.field_8037 : GearStack.plain("minecraft:emerald", rem);
        }
        int rest = Math.min(64, blocks - 64);
        return rest <= 0 ? class_1799.field_8037 : GearStack.plain("minecraft:emerald_block", rest);
    }

    /** 预制车（automobility）→ 带 automobile_data 组件的机动车物品栈 */
    public static class_1799 carStack(String name, int count, class_7225.class_7874 lookup) {
        String wheel, engine;
        switch (name) {
            case "wooden_motorcar": wheel = "carriage"; engine = "stone"; break;
            case "copper_motorcar": wheel = "plated"; engine = "copper"; break;
            case "steel_motorcar": wheel = "street"; engine = "iron"; break;
            case "golden_motorcar": wheel = "gilded"; engine = "gold"; break;
            case "bejeweled_motorcar": wheel = "bejeweled"; engine = "diamond"; break;
            case "amethyst_rickshaw": wheel = "bejeweled"; engine = "stone"; break;
            case "quartz_rickshaw": wheel = "gilded"; engine = "gold"; break;
            case "prismarine_rickshaw": wheel = "plated"; engine = "copper"; break;
            case "echo_rickshaw": wheel = "street"; engine = "diamond"; break;
            case "red_tractor": case "yellow_tractor": case "green_tractor": case "blue_tractor":
                wheel = "tractor"; engine = "copper"; break;
            case "shopping_cart": wheel = "steel"; engine = "stone"; break;
            case "c_arr": wheel = "off_road"; engine = "diamond"; break;
            case "pineapple": wheel = "tractor"; engine = "gold"; break;
            default: wheel = "standard"; engine = "iron"; break;   // standard_* 16 色
        }
        try {
            class_2487 root = new class_2487();
            root.method_10582("id", "automobility:automobile");
            root.method_10569("count", Math.max(1, count));
            class_2487 comps = new class_2487();
            class_2487 data = new class_2487();
            data.method_10582("prefab_id", "automobility:" + name);
            data.method_10582("frame", "automobility:" + name);
            data.method_10582("wheels", "automobility:" + wheel);
            data.method_10582("engine", "automobility:" + engine);
            comps.method_10566("automobility:automobile_data", data);
            root.method_10566("components", comps);
            class_1799 built = class_1799.method_57359(lookup, root);
            if (built != null && !built.method_7960()) return built;
        } catch (Throwable ignored) {
        }
        return class_1799.field_8037;
    }

    /** 从面板行造物；认 gear/book/potion/car/普通货，q 是品质档（酒的 brew_level） */
    public static class_1799 row(JsonObject r, int count, class_7225.class_7874 lookup) {
        if (r == null) return class_1799.field_8037;
        String id = str(r, "id", "");
        if (id.startsWith("car:")) {
            return carStack(id.substring(4), count, lookup);
        }
        if (id.startsWith("gear:")) {
            String enchCode = str(r, "enchs", "");
            if (!enchCode.isEmpty()) {
                return GearStack.ofEnchanted(str(r, "base", ""), enchCode, count, lookup);
            }
            int lvl = r.has("esum") ? i(r, "esum", 0) : i(r, "elvl", 0);
            return GearStack.of(str(r, "base", ""), str(r, "ench", ""), lvl, count, lookup);
        }
        String base = str(r, "base", "");
        if (base.isEmpty()) base = id;
        // 附魔书：行里带 bookcode（"附魔|等级" 或 "附魔@等级;附魔@等级"）时按整本造，
        // 多附魔的书也就能跟玩家手上那本一模一样地对上
        if (base.endsWith("enchanted_book") && r.has("bookcode")) {
            return ofBook(base, count, str(r, "bookcode", ""), lookup);
        }
        return of(base, count, str(r, "ench", ""), i(r, "elvl", 0), str(r, "potion", ""),
                i(r, "q", 0), i(r, "flight", 0), str(r, "instrument", ""), lookup);
    }

    /**
     * 造一本附魔书。
     *
     * @param codes {@code 附魔|等级} 或 {@code 附魔@等级;附魔@等级}（跟 Bridge.bookCode 同一个写法）
     */
    public static class_1799 ofBook(String base, int count, String codes, class_7225.class_7874 lookup) {
        class_1799 plain = GearStack.plain(base, count);
        if (lookup == null || codes == null || codes.isEmpty()) return plain;
        try {
            class_2487 levels = new class_2487();
            int n = 0;
            for (String part : codes.split(";")) {
                if (part == null || part.isEmpty()) continue;
                int at = part.indexOf('@');
                if (at > 0) {
                    levels.method_10569(part.substring(0, at), Integer.parseInt(part.substring(at + 1)));
                    n++;
                    continue;
                }
                int bar = part.lastIndexOf('|');
                if (bar > 0) {
                    levels.method_10569(part.substring(0, bar), Integer.parseInt(part.substring(bar + 1)));
                    n++;
                }
            }
            if (n <= 0) return plain;
            class_2487 root = new class_2487();
            root.method_10582("id", base);
            root.method_10569("count", Math.max(1, count));
            class_2487 comps = new class_2487();
            class_2487 ech = new class_2487();
            ech.method_10566("levels", levels);
            comps.method_10566("minecraft:stored_enchantments", ech);
            root.method_10566("components", comps);
            class_1799 built = class_1799.method_57359(lookup, root);
            if (built != null && !built.method_7960()) return built;
        } catch (Throwable ignored) {
            // 写坏了就退回白板书，别把整行货弄没
        }
        return plain;
    }

    /**
     * 造一件组件货。
     *
     * @param base       物品 id（"minecraft:potion"、"minecraft:enchanted_book"…）
     * @param ench       附魔 id（含命名空间）；空 = 不附魔
     * @param enchLevel  附魔等级
     * @param potion     药水效果 id（"minecraft:strong_healing"）；空 = 不带
     * @param brew       酒的品质档 1..6；0 = 不带
     * @param flight     烟花飞行时长；0 = 不带
     * @param instrument 山羊角乐器 id；空 = 不带
     */
    public static class_1799 of(String base, int count, String ench, int enchLevel, String potion,
                                int brew, int flight, String instrument, class_7225.class_7874 lookup) {
        class_1799 plain = GearStack.plain(base, count);
        if (lookup == null) return plain;
        boolean hasEnch = ench != null && !ench.isEmpty() && enchLevel > 0;
        boolean hasPotion = potion != null && !potion.isEmpty();
        boolean hasBrew = brew > 0 && componentExists("kaleidoscope_tavern", "brew_level");
        boolean hasFlight = flight > 0;
        boolean hasHorn = instrument != null && !instrument.isEmpty();
        if (!hasEnch && !hasPotion && !hasBrew && !hasFlight && !hasHorn) return plain;
        try {
            class_2487 root = new class_2487();
            root.method_10582("id", base);
            root.method_10569("count", Math.max(1, count));
            class_2487 comps = new class_2487();
            if (hasEnch) {
                // 附魔书用 stored_enchantments（附在书上），其它物品用 enchantments（直接生效）
                String key = base.endsWith("enchanted_book")
                        ? "minecraft:stored_enchantments" : "minecraft:enchantments";
                class_2487 ech = new class_2487();
                class_2487 levels = new class_2487();
                levels.method_10569(ench, enchLevel);
                ech.method_10566("levels", levels);
                comps.method_10566(key, ech);
            }
            if (hasPotion) {
                class_2487 pc = new class_2487();
                pc.method_10582("potion", potion);
                comps.method_10566("minecraft:potion_contents", pc);
            }
            if (hasBrew) {
                comps.method_10569("kaleidoscope_tavern:brew_level", Quality.clamp(brew));
            }
            if (hasFlight) {
                class_2487 fw = new class_2487();
                fw.method_10569("flight_duration", Math.max(1, Math.min(3, flight)));
                comps.method_10566("minecraft:fireworks", fw);
            }
            if (hasHorn) {
                comps.method_10582("minecraft:instrument", instrument);
            }
            root.method_10566("components", comps);
            class_1799 built = class_1799.method_57359(lookup, root);
            if (built != null && !built.method_7960()) {
                if (hasBrew) {
                    // 面板上的名字直接带上品质档（"典藏 葡萄酒"）：原版列表只画物品名，
                    // 不写出来玩家根本看不出这一行是几档酒，也分不清两行酒差在哪
                    try {
                        net.minecraft.class_2561 nm = net.minecraft.class_2561.method_43470(
                                Quality.name(brew) + " ").method_10852(built.method_7964());
                        built.method_57379(net.minecraft.class_9334.field_49631, nm);
                    } catch (Throwable ignored) {
                        // 名字加不上就照旧显示物品名，不影响价格和成交
                    }
                }
                return built;
            }
        } catch (Throwable ignored) {
            // 组件写坏了就退回白板，别把整行货弄没
        }
        return plain;
    }

    /** 这个组件类型在当前整合包里存不存在（模组没装就别往 NBT 里写，写错整件货会反序列化失败） */
    @SuppressWarnings("unchecked")
    private static boolean componentExists(String ns, String path) {
        try {
            class_2960 rl = class_2960.method_60655(ns, path);
            return class_7923.field_49658.method_10223(rl) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.get(key).getAsString();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int i(JsonObject o, String key, int fallback) {
        try {
            return o.get(key).getAsInt();
        } catch (Throwable t) {
            return fallback;
        }
    }
}
