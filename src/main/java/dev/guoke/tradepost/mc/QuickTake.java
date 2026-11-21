package dev.guoke.tradepost.mc;

import net.minecraft.class_1799;

/**
 * "这一次点击，结果格原本有什么"的快照。
 *
 * <p>为什么要它：shift 快速取物走的是 {@code class_1703.method_7601}（transferSlot）。
 * 它先把结果格那一摞塞进背包，那一摞**就是结果格里的同一个对象**，塞完数量归零，
 * 再把格子设成空气，最后才回调 {@code method_7667}（onTakeItem）—— 所以我们回调里
 * 拿到的 {@code stack} 是空栈。空栈既没法判断"到底成交了几件"，也没法算"该收多少钱"。
 *
 * <p>普通鼠标点击则相反：点击流程先把那一摞塞进光标，再清格子，回调里拿到的是满的。
 *
 * <p>所以在 {@code method_7601} 开头（index==2 时）把结果格先复制一份存这里，
 * 结算时优先用它，拿不到再退回回调给的那个栈。
 */
public final class QuickTake {

    /** 5 秒还没被消费就作废：防止某次快速移动半路失败，留个旧快照坑下一笔。 */
    private static final long TTL_MS = 5_000L;

    private static class_1799 snap;
    private static long at;

    private QuickTake() {
    }

    /** 记一份快照（null = 清掉，普通快速移动每 tick 都会来清一次）。 */
    public static void begin(class_1799 stack) {
        snap = stack;
        at = System.currentTimeMillis();
    }

    /** 取出并清掉快照；没有或过期返回 null。 */
    public static class_1799 take() {
        class_1799 s = snap;
        snap = null;
        if (s == null) return null;
        if (System.currentTimeMillis() - at > TTL_MS) return null;
        return s;
    }
}
