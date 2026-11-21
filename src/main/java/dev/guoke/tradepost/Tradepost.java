package dev.guoke.tradepost;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.guoke.tradepost.market.MarketData;
import dev.guoke.tradepost.mc.Bridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.class_2168;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tradepost implements ModInitializer {
    public static final String MOD_ID = "tradepost";
    public static final Logger LOGGER = LoggerFactory.getLogger("tradepost");

    /** 细节日志开关：JVM 参数加 -Dtradepost.debug=true 才打（发布包默认安静，只留成交与启动摘要） */
    public static final boolean DEBUG = Boolean.getBoolean("tradepost.debug");

    /** 细节日志：价目表逐行、摆货/收格、扣货核对这些排查用的东西走这里 */
    public static void debug(String fmt, Object... args) {
        if (DEBUG) LOGGER.info(fmt, args);
    }

    private static MarketData DATA;

    public static MarketData data() {
        return DATA;
    }

    /** 某物品的基础价（未加载数据时为 0，调用方按缺省处理） */
    public static double valueOf(String item) {
        MarketData d = DATA;
        return d == null ? 0.0 : d.valueOf(item);
    }

    /** 数据目录：<config>/tradepost/market/ */
    public static Path dataDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("market");
    }

    /** 经济系数：<config>/tradepost/market/economy.json，缺失时写出默认值 */
    public static dev.guoke.tradepost.market.Economy reloadEconomy() {
        Path dir = dataDir();
        try {
            Path file = dir.resolve("economy.json");
            if (dev.guoke.tradepost.market.Economy.exportDefault(file)) {
                LOGGER.info("[tradepost] 首次启动：已写出默认经济系数 {}", file);
            }
            dev.guoke.tradepost.market.Economy e = dev.guoke.tradepost.market.Economy.load(file);
            dev.guoke.tradepost.market.Economy.set(e);
            LOGGER.info("[tradepost] 经济系数：买 {}x / 卖 {}x / 需求 {} / 自产 {} / 村癖 ±{}% / 行情 ±{}% / 日回 {}% / 区域 {} 格 x{}",
                    e.buyBase, e.sellBase, e.demandBonus, e.supplyPenalty,
                    Math.round(e.quirkPct * 100), Math.round(e.dailyPct * 100),
                    Math.round(e.stockRegen * 100), e.areaRadius, e.areaSpread);
            return e;
        } catch (Throwable t) {
            LOGGER.warn("[tradepost] 经济系数加载失败，用内置默认值", t);
            return dev.guoke.tradepost.market.Economy.current();
        }
    }

    /** 用途大类表：<config>/tradepost/market/roles.json，缺失时写出默认值 */
    public static void reloadRoles(Path dir) {
        try {
            Path file = dir.resolve("roles.json");
            if (dev.guoke.tradepost.market.Roles.exportDefault(file)) {
                LOGGER.info("[tradepost] 首次启动：已写出用途大类表 {}", file);
            }
            dev.guoke.tradepost.market.Roles.set(dev.guoke.tradepost.market.Roles.load(file));
            dev.guoke.tradepost.market.DemandGraph.clear();
            dev.guoke.tradepost.market.Roles r = dev.guoke.tradepost.market.Roles.current();
            LOGGER.info("[tradepost] 用途大类：{} 个产业配置 / {} 个标签 / {} 个关键词（{}）",
                    r.industries.size(), r.tags.size(), r.keywords.size(), file);
        } catch (Throwable t) {
            LOGGER.warn("[tradepost] 用途大类表加载失败，用内置默认", t);
        }
    }

    /** 黑名单：太特殊的物品不参与贸易。<config>/tradepost/market/blacklist.json */
    public static void reloadBanlist(Path dir) {
        try {
            Path file = dir.resolve("blacklist.json");
            if (dev.guoke.tradepost.market.Banlist.exportDefault(file)) {
                LOGGER.info("[tradepost] 首次启动：已写出默认黑名单 {}", file);
            }
            boolean ok = dev.guoke.tradepost.market.Banlist.load(file);
            dev.guoke.tradepost.market.Banlist b = dev.guoke.tradepost.market.Banlist.current();
            LOGGER.info("[tradepost] 黑名单：{} 个 id / {} 个标签 / {} 个关键词 / {} 个模组（{}）",
                    b.items.size(), b.tags.size(), b.keywords.size(), b.namespaces.size(),
                    ok ? file : "内置默认");
        } catch (Throwable t) {
            LOGGER.warn("[tradepost] 黑名单加载失败，用内置默认", t);
        }
    }

    /** 菜与酒的溢价表：<config>/tradepost/market/cuisine.json */
    public static void reloadCuisine(Path dir) {
        try {
            Path file = dir.resolve("cuisine.json");
            java.nio.file.Files.createDirectories(dir);
            dev.guoke.tradepost.market.Cuisine.exportDefault(file);
            dev.guoke.tradepost.market.Cuisine.Config c = dev.guoke.tradepost.market.Cuisine.load(file);
            LOGGER.info("[tradepost] 珍馐酒水：{} 个料理模组 / {} 个等级（最高 x{}）/ 工序 +{} / 贵材料 +{}（{}）",
                    c.namespaces.size(), c.tiers.size(), c.max,
                    Math.round(c.slotBonus * 100), Math.round(c.ingredientBonus * 100), file);
        } catch (Throwable t) {
            LOGGER.warn("[tradepost] 珍馐酒水表加载失败，用内置默认", t);
        }
    }

    /** 读取市场数据：外部目录优先，第一次启动先把 jar 内置的导出去。 */
    public static MarketData reloadData() {
        Path dir = dataDir();
        reloadEconomy();
        reloadRoles(dir);
        reloadBanlist(dir);
        reloadCuisine(dir);
        try {
            if (!Files.exists(dir.resolve("values.json"))) {
                int n = MarketData.exportDefaults(dir);
                LOGGER.info("[tradepost] 首次启动：已把默认市场数据导出到 {}（{} 个文件），改完用 /tradepost reload 生效", dir, n);
            }
            MarketData d = MarketData.load(dir);
            DATA = d;
            Bridge.applyCategories(d.categories);
            Bridge.applyRarity(d.rarity);
            LOGGER.info("[tradepost] 市场数据加载完成：{}（来源：{}）",
                    d.summary(), d.external ? "外部目录 " + dir : "jar 内置");
            return d;
        } catch (Throwable t) {
            LOGGER.error("[tradepost] 市场数据加载失败，交易系统将退回原版", t);
            return DATA;
        }
    }

    @Override
    public void onInitialize() {
        reloadData();

        // 物品标签要等数据包加载完才读得到（mod init 阶段是空的），所以拖到服务器启动后再扫
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var res = Bridge.generateAuto(false);
                if (res != null && !res.drafts().isEmpty()) {
                    reloadData();
                    Bridge.clearCatalogCache();
                    LOGGER.info("[tradepost] 自动产业生效：{}", res.drafts().size());
                }
            } catch (Throwable t) {
                LOGGER.warn("[tradepost] 自动产业生成失败（不影响交易系统）", t);
            }
            // 配方定价是"第一次开集市"最重的一步，但绝不能在这时候算：
            // 这会儿客户端还在建 TagKey，两边挤同一把锁会把"进入世界"卡死。
            // 改成等玩家真正进世界、再过 5 秒才排队（见下面 JOIN 监听 + Bridge.requestWarmUp）。
        });

        // 玩家进了世界才开始预热配方价（躲开登录那几十秒的标签创建高峰）
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                Bridge.requestWarmUp(server.method_30002(), 100);      // 100 tick = 5 秒
            } catch (Throwable t) {
                LOGGER.warn("[tradepost] 配方定价预热没能排上（不影响功能）", t);
            }
        });

        // 配方定价预热：每 tick 推一小片（绝不放后台线程，见 Bridge.beginWarmUp 的注释）
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 卡死抓栈：每 tick 报一次平安，8 秒没 tick 就把线程栈写进 logs/
            try {
                dev.guoke.tradepost.mc.TickWatch.beat();
            } catch (Throwable ignored) { }
            try {
                Bridge.tickWarmUp();
            } catch (Throwable t) {
                LOGGER.warn("[tradepost] 配方定价预热中断（不影响功能）", t);
            }
            // 成交后的"续摆"统一放到 tick 末尾：同一 tick 里重复落地的点击包才不会多结一次账
            try {
                dev.guoke.tradepost.mc.MarketMerchant.tickPending();
            } catch (Throwable t) {
                LOGGER.warn("[tradepost] 成交后续摆失败（不影响手点）", t);
            }
        });

        // 潜行右键村民 = 把手里的货卖给他（全物品收购，不走交易界面）
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.method_8608()) return net.minecraft.class_1269.field_5811;                    // 客户端：交给原版
            if (!(entity instanceof net.minecraft.class_1646)) return net.minecraft.class_1269.field_5811;
            if (!player.method_5715()) return net.minecraft.class_1269.field_5811;                  // 不潜行：正常开交易
            return Bridge.sellHeld(player, hand)
                    ? net.minecraft.class_1269.field_5812                                               // 已收购 → 取消原版交互
                    : net.minecraft.class_1269.field_5811;
        });

        // 潜行右键钟 = 村中心集市：列出本村当前收购价，点一行直接卖（普通右键照旧敲钟）
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.method_8608()) return net.minecraft.class_1269.field_5811;                    // 客户端：交给原版
            if (!player.method_5715()) return net.minecraft.class_1269.field_5811;                  // 不潜行：正常敲钟
            if (!Bridge.isBell(world, hit.method_17777())) return net.minecraft.class_1269.field_5811;
            Bridge.openBoard((net.minecraft.class_3222) player);
            return net.minecraft.class_1269.field_5812;                                              // 已开集市 → 不响铃
        });

        // 集市 GUI 的网络包：S2C 价目表 / C2S 卖出（客户端没装本模组时服务端退化成聊天栏）
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(dev.guoke.tradepost.market.MarketPayload.ID,
                        dev.guoke.tradepost.market.MarketPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.SellPayload.ID,
                        dev.guoke.tradepost.market.SellPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.BuyPayload.ID,
                        dev.guoke.tradepost.market.BuyPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.SellPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> Bridge.sellFromGui(context.player(), payload.itemId(), payload.count(),
                                payload.level())));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.ExchangePayload.ID,
                        dev.guoke.tradepost.market.ExchangePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.ExchangePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> Bridge.exchange(context.player(), payload.dir())));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.DonatePayload.ID,
                        dev.guoke.tradepost.market.DonatePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.DonatePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> Bridge.donate(context.player(), Integer.parseInt(payload.count()))));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.PagePayload.ID,
                        dev.guoke.tradepost.market.PagePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.PagePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> dev.guoke.tradepost.mc.MarketMerchant.switchPage(
                                context.player(), payload.tab(), payload.qty())));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.CollectPayload.ID,
                        dev.guoke.tradepost.market.CollectPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.CollectPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> dev.guoke.tradepost.mc.MarketMerchant.collect(context.player())));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(dev.guoke.tradepost.market.TradePayload.ID,
                        dev.guoke.tradepost.market.TradePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.BuyPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> Bridge.buyFromGui(context.player(), payload.itemId(), payload.units())));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                dev.guoke.tradepost.market.TradePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> Bridge.tradeRelic(context.player(), payload.itemId())));

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                dispatcher.register(LiteralArgumentBuilder.<class_2168>literal("tradepost")
                        .then(LiteralArgumentBuilder.<class_2168>literal("here")
                                .executes(ctx -> Bridge.reportHere(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("info")
                                .executes(ctx -> Bridge.reportInfo(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("mix")
                                .executes(ctx -> Bridge.cmdMix(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("forecast")
                                .executes(ctx -> Bridge.reportForecast(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("scan")
                                .executes(ctx -> Bridge.reportScan(ctx.getSource(), 64))
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, Integer>argument(
                                                "radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> Bridge.reportScan(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("where")
                                .executes(ctx -> Bridge.reportWhere(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("villages")
                                .executes(ctx -> Bridge.reportVillages(ctx.getSource(), 4))
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, Integer>argument(
                                                "radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> Bridge.reportVillages(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("dumpall")
                                .executes(ctx -> Bridge.dumpAll(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("dump")
                                .executes(ctx -> Bridge.dumpCatalog(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("propose")
                                .executes(ctx -> Bridge.propose(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("board")
                                .executes(ctx -> Bridge.cmdBoard(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("sellall")
                                .executes(ctx -> Bridge.cmdSellAll(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("gear")
                                .executes(ctx -> Bridge.cmdGear(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("sell")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, String>argument(
                                                "item", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> Bridge.cmdSell(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item"), 0))
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, Integer>argument(
                                                        "count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> Bridge.cmdSell(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("why")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, String>argument(
                                                "item", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> Bridge.explain(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item")))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("last")
                                .executes(ctx -> Bridge.explainLast(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("uplevel")
                                .executes(ctx -> Bridge.cmdUplevel(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("setlevel")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, Integer>argument(
                                                "level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> Bridge.cmdSetlevel(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level")))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("cat")
                                .executes(ctx -> Bridge.cmdCategories(ctx.getSource(), null))
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<class_2168, String>argument(
                                                "category", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> Bridge.cmdCategories(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "category")))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("rarity")
                                .executes(ctx -> Bridge.cmdRarity(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("goods")
                                .executes(ctx -> Bridge.cmdGoods(ctx.getSource())))
                        .then(LiteralArgumentBuilder.<class_2168>literal("audit")
                                .executes(ctx -> Bridge.audit(ctx.getSource()))
                                .then(LiteralArgumentBuilder.<class_2168>literal("csv")
                                        .executes(ctx -> Bridge.auditCsv(ctx.getSource()))))
                        .then(LiteralArgumentBuilder.<class_2168>literal("reload")
                                .executes(ctx -> {
                                    MarketData d = reloadData();
                                    Bridge.clearCatalogCache();
                                    Bridge.clearStationCache();
                                    Bridge.clearVillageCache();
                                    ctx.getSource().method_9226(() -> net.minecraft.class_2561.method_43470(
                                            "已重载：" + (d == null ? "失败，看日志" : d.summary())
                                            + (d != null && d.external ? "（外部目录）" : "（jar 内置）")), false);
                                    return 1;
                                }))));

        LOGGER.info("[tradepost] 初始化完成 (v0.1.0 垂直切片)");
    }
}
