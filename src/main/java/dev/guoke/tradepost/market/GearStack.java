package dev.guoke.tradepost.market;

import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2487;
import net.minecraft.class_2960;
import net.minecraft.class_7225;
import net.minecraft.class_2378;
import net.minecraft.class_7923;

/**
 * 把「基础物品 + 附魔 + 等级」拼成真正的 ItemStack。
 *
 * 服务端（结算/发货）和客户端（列表图标）都要用，所以放在 market 包里，
 * 只依赖 class_1799/class_2487，不碰 Bridge 那一堆服务端类。
 *
 * 原理：1.21 的物品栈可以从 NBT 反序列化（ItemStack.fromNbt，class_1799.method_57359），
 * 组件写成 components.minecraft:enchantments.levels.{附魔id:int} 即可，
 * 不用手搓 class_9304 那套 builder，也不用管附魔注册表长什么样。
 */
public final class GearStack {

    private GearStack() {
    }

    /** 按 "id@等级;id@等级" 造一件带全部附魔的栈（图标/悬停显示所有词条） */
    public static class_1799 ofEnchanted(String base, String enchCode, int count, class_7225.class_7874 lookup) {
        class_1799 plain = plain(base, count);
        if (enchCode == null || enchCode.isEmpty() || lookup == null) return plain;
        try {
            class_2487 root = new class_2487();
            root.method_10582("id", base);
            root.method_10569("count", Math.max(1, count));
            class_2487 comps = new class_2487();
            class_2487 ech = new class_2487();
            class_2487 levels = new class_2487();
            for (String part : enchCode.split(";")) {
                int at = part.lastIndexOf('@');
                if (at <= 0) continue;
                int lv;
                try { lv = Integer.parseInt(part.substring(at + 1)); }
                catch (NumberFormatException e) { continue; }
                if (lv > 0) levels.method_10569(part.substring(0, at), lv);
            }
            ech.method_10566("levels", levels);
            comps.method_10566("minecraft:enchantments", ech);
            root.method_10566("components", comps);
            class_1799 built = class_1799.method_57359(lookup, root);
            if (built != null && !built.method_7960()) return built;
        } catch (Throwable ignored) {
            // 反序列化失败就退回白板，至少别把整行货弄没
        }
        return plain;
    }

    public static class_1799 of(String base, String ench, int level, int count, class_7225.class_7874 lookup) {
        class_1799 plain = plain(base, count);
        if (ench == null || ench.isEmpty() || level <= 0 || lookup == null) return plain;
        try {
            class_2487 root = new class_2487();
            root.method_10582("id", base);
            root.method_10569("count", Math.max(1, count));
            class_2487 comps = new class_2487();
            class_2487 ech = new class_2487();
            class_2487 levels = new class_2487();
            levels.method_10569(ench, Math.max(1, level));
            ech.method_10566("levels", levels);
            comps.method_10566("minecraft:enchantments", ech);
            root.method_10566("components", comps);
            class_1799 built = class_1799.method_57359(lookup, root);
            if (built != null && !built.method_7960()) return built;
        } catch (Throwable ignored) {
            // 反序列化失败就退回白板，至少别把整行货弄没
        }
        return plain;
    }

    @SuppressWarnings("unchecked")
    public static class_1799 plain(String id, int count) {
        try {
            class_2378<class_1792> items = (class_2378<class_1792>) class_7923.field_41178;
            class_1792 item = items.method_10223(class_2960.method_60654(id));
            if (item == null) return class_1799.field_8037;
            return new class_1799(item, Math.max(1, count));
        } catch (Throwable t) {
            return class_1799.field_8037;
        }
    }
}
