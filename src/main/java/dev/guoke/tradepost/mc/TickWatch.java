package dev.guoke.tradepost.mc;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 服务端 tick 卡死抓栈器。
 *
 * 背景：2026-11-13 03:11 那次整局卡死，服务端连每 60 秒一次的分块遥测都停了，
 * 但因为进程被强制关闭，jstack 抓不到、也没有 crash-report，只能靠猜。
 * 这个类就住在游戏进程里：END_SERVER_TICK 每 tick 调一次 beat()，
 * 独立守护线程发现超过 8 秒没有 beat 就把所有线程栈写进 logs/tradepost-hang-*.txt。
 * 只读、只写文件，不改任何游戏状态；开销是每 tick 一次 volatile 写。
 */
public final class TickWatch {
    private static final long STALL_MS = 8000L;      // 服务端 8 秒没 tick 就当卡住
    private static final long REPEAT_MS = 30000L;    // 同一次卡顿里最多每 30 秒抓一份

    private static volatile long last = System.nanoTime();
    private static volatile boolean armed = false;
    private static volatile long lastDump = 0L;
    private static boolean started = false;

    private TickWatch() { }

    /** 服务端每 tick 报一次平安。第一次调用时顺手把守护线程拉起来。 */
    public static void beat() {
        last = System.nanoTime();
        if (!armed) {
            armed = true;
            start();
        }
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(TickWatch::loop, "tradepost-tickwatch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                return;
            }
            if (!armed) continue;
            long ms = (System.nanoTime() - last) / 1_000_000L;
            if (ms < STALL_MS) continue;
            long now = System.nanoTime();
            if (lastDump != 0L && (now - lastDump) / 1_000_000L < REPEAT_MS) continue;
            lastDump = now;
            try {
                dump(ms);
            } catch (Throwable ignored) {
                // 抓栈失败绝不能反过来影响游戏
            }
        }
    }

    private static void dump(long stallMs) {
        String stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        Path dir = Paths.get("logs");
        Path file = dir.resolve("tradepost-hang-" + stamp + ".txt").toAbsolutePath();
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        List<Thread> ts = new ArrayList<>(all.keySet());
        ts.sort(Comparator.comparing(Thread::getName));
        Runtime rt = Runtime.getRuntime();
        StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("=== tradepost 卡顿快照 ===\n");
        sb.append("服务端已经 ").append(stallMs).append(" ms 没走过一个 tick\n");
        sb.append("堆：").append((rt.totalMemory() - rt.freeMemory()) >> 20).append(" / ")
          .append(rt.maxMemory() >> 20).append(" MiB，线程数 ").append(ts.size()).append("\n\n");
        for (Thread th : ts) {
            StackTraceElement[] st = all.get(th);
            if (st == null) st = new StackTraceElement[0];
            sb.append('"').append(th.getName()).append("\" #").append(th.threadId()).append(' ').append(th.getState());
            if (th.isDaemon()) sb.append(" daemon");
            sb.append('\n');
            boolean key = th.getName().equals("Server thread") || th.getName().equals("Render thread");
            int lim = key ? 90 : 14;
            for (int i = 0; i < st.length && i < lim; i++) sb.append("\tat ").append(st[i]).append('\n');
            if (st.length > lim) sb.append("\t... 共 ").append(st.length).append(" 帧\n");
            sb.append('\n');
        }
        try {
            Files.createDirectories(dir);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
                w.print(sb);
            }
        } catch (Throwable t) {
            dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 卡顿快照写不出去", t);
            return;
        }
        dev.guoke.tradepost.Tradepost.LOGGER.warn("[tradepost] 服务端卡了 {} ms，线程快照写到 {}", stallMs, file);
    }
}
