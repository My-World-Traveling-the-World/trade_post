package dev.guoke.tradepost.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 面板外观：主题配色 + 尺寸，来自 config/tradepost/client.json（客户端读，服务端不用） */
public final class Style {

    public final String theme;
    public final int bg;        // 面板底
    public final int bar;       // 顶部装饰条
    public final int title;     // 标题字
    public final int text;      // 正文字
    public final int dim;       // 次要字
    public final int good;      // 收/可换
    public final int bad;       // 缺料/售罄
    public final int gold;      // 价格
    public final int hover;     // 悬停底色
    public final int panelW;
    public final int rowH;

    private Style(String theme, int bg, int bar, int title, int text, int dim, int good, int bad,
                  int gold, int hover, int panelW, int rowH) {
        this.theme = theme;
        this.bg = bg;
        this.bar = bar;
        this.title = title;
        this.text = text;
        this.dim = dim;
        this.good = good;
        this.bad = bad;
        this.gold = gold;
        this.hover = hover;
        this.panelW = panelW;
        this.rowH = rowH;
    }

    public static final String[] THEMES = {"dark", "paper", "vanilla"};

    public static Style of(String theme, int panelW, int rowH) {
        String t = theme == null ? "dark" : theme.toLowerCase();
        return switch (t) {
            case "paper" -> new Style("paper", 0xF0EDE4D2, 0xFF9A7440, 0xFF3A2C16, 0xFF2A2418,
                    0xFF6A6250, 0xFF2E6B2E, 0xFF8A3A2A, 0xFF8A6A1A, 0x30000000, panelW, rowH);
            case "vanilla" -> new Style("vanilla", 0xF0C6C6C6, 0xFF555555, 0xFF2B2B2B, 0xFF202020,
                    0xFF5A5A5A, 0xFF1F6F1F, 0xFF8A2020, 0xFF5A4000, 0x22FFFFFF, panelW, rowH);
            default -> new Style("dark", 0xF0121216, 0xFFC09040, 0xFFF0C060, 0xFFE8E8E8,
                    0xFF8A8A8A, 0xFF6AF06A, 0xFFB06060, 0xFFF0C060, 0x40FFFFFF, panelW, rowH);
        };
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tradepost").resolve("client.json");
    }

    /** 读配置；文件不存在就写一份默认的出来 */
    public static Style load() {
        int panelW = 380;
        int rowH = 14;
        String theme = "dark";
        try {
            Path p = file();
            if (!Files.isRegularFile(p)) {
                Files.createDirectories(p.getParent());
                String def = "{\n"
                        + "  \"theme\": \"dark\",\n"
                        + "  \"panel_width\": 380,\n"
                        + "  \"row_height\": 14,\n"
                        + "  \"_themes\": [\"dark\"(深色,默认), \"paper\"(羊皮纸), \"vanilla\"(原版灰)]\n"
                        + "}\n";
                Files.write(p, def.getBytes(StandardCharsets.UTF_8));
            }
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            if (o.has("theme")) theme = o.get("theme").getAsString();
            if (o.has("panel_width")) panelW = Math.max(240, Math.min(600, o.get("panel_width").getAsInt()));
            if (o.has("row_height")) rowH = Math.max(10, Math.min(24, o.get("row_height").getAsInt()));
        } catch (Throwable t) {
            // 配置坏了就用默认外观，绝不让它挡住面板
        }
        return of(theme, panelW, rowH);
    }
}
