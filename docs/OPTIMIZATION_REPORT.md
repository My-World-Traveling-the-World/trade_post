# 集市（tradepost）优化建议报告

> 配套文档：`docs/SYSTEM_REPORT.md`（现行体系机制与公式）。
> 本报告基于源码逐行阅读（`src/main/java/dev/guoke/tradepost/` 下的 Bridge / TagCatalog / Chain / RecipePricer / Stock / TradeStore / VillageMarket / Banlist / DemandGraph）整理。
> 说明：整理时联网搜索不可用（web_search 缺 API key、外网 fetch 被拒），建议全部来自源码 + 配置数值 + 通用游戏经济/性能工程经验。

---

## 0. 优先级总览

| 优先级 | 项 | 类型 | 一句话 |
|---|---|---|---|
| ★★★ | P2 基础价无缓存 | 性能 | 每条报价反复重算整条 basePrice 链，主线程最大单项开销 |
| ★★★ | P1 黑名单每查询跑正则 | 性能 | `Banlist.isBanned` 对每个 id 迭代 36 个正则，无 memo |
| ★★★ | P7 库存日结算用错基础价 | 正确性 bug | `TradeStore.settle` 用 `Tradepost.valueOf` 而非全价算 cap |
| ★★ | M1 卖价 0.75 封顶吃掉整套卖价公式 | 机制 | 需求/菜酒溢价在卖侧几乎永远失效 |
| ★★ | P4 `Chain.factor` 全局锁 + 512 全清 | 性能/机制 | 递归记忆化被粗粒度锁和整表清空拖累 |
| ★ | P3 `isEdible` 无缓存 / P6 `listedIn` 展开 / M6 死代码 | 性能/整洁 | 顺手修 |

---

## 一、算法层（改「怎么算」，均带行号）

### P1 黑名单正则每次查询都重跑

`Bridge.java:488` 的 `bannedItem(id)` → `Banlist.isBanned(id)`（`Banlist.java:168-198`）每次调用都遍历 `namespaces`（equalsIgnoreCase）、`keywords`（toLowerCase + contains）、`compiledPatterns`（36 个 `Pattern.matcher(path).find()`）。

它被 `rawValueInner`（`Bridge.java:580`）与 `isRecipePriced`（`Bridge.java:551`）各调一次，等于**每个 id 的每次基础价查询跑两遍全量正则**。`Bridge.java:300-303` 的注释已记录过同类问题（开页 2.3s 全耗在黑名单），当时只缓存了标签展开（`expandCache`），但按 id 的 `bannedItem` 路径没缓存。

建议：给 `bannedItem(id)` 加 `ConcurrentHashMap<String,Boolean>`（仿 `tierCache`，reload 时清空）。黑白名单判定是纯配置函数，与村/天/库存无关，可安全缓存。

### P2 `value()/rawValue()` 整条链无缓存

`baseOf`（`VillageMarket.java:596`）→ `catalog.value(item)` → `rawValue`（`Bridge.java:571-608`）这条链每个 id 每次查询都重跑：

- `Tradepost.valueOf(id)`（values.json 查找）
- `bannedItem(id)`（P1 的正则）
- `derived().get(id)`（标签派生表）
- `recipePrices.get(id)`
- `categoryPrice(id)` → `Tradepost.data()` + `categories.priceOf`（每次查全局 data）
- `fallbackBase()`
- `foodFactor(id)` → `isEdible(id)`（读物品 FOOD 组件，无缓存）
- `isDrinkItem(id)`（有 DRINK_CACHE，OK）
- `rarity(id)`（有 tierCache，OK）

链条里 `isDrinkItem`、`rarity` 已缓存，但 `value()` 本体没缓存。`offers()` 里同一件货会被 `picks()`（`VillageMarket.java:573`）、`baseOf()`（`:403`）、`chainFactor` 的递归材料（`Chain.cost` → `baseOf` lambda）重复计算多次，而 `value()` 结果只依赖配置，不依赖村/天/库存。

建议：给 `value(id)`（或 `rawValueInner`）加 `ConcurrentHashMap<String,Double>` 缓存，reload 时随 `TagCatalog.clear()`（`Bridge.java:396-407`，已清 `derived/recipePrices/tierCache`）一起失效。纯收益、零风险，warm 后整条基础价链坍缩为一次 map 查表。

### P3 `isEdible` 无缓存

`Bridge.java:2834-2841` 每次读 `item.method_57347().method_57829(FOOD)`，被 `foodFactor`（每条价格查询）、`isDish`（`value` 配方分支、`chainFactor` 的 dish 判断）反复触发。仿照 `isDrinkItem` 的 `DRINK_CACHE`（`Bridge.java:2682`）加一个 `Map<String,Boolean>`。`categoryPrice`（`:649`）每次 `Tradepost.data()` 也会被 P2 缓存顺带覆盖。

### P4 `Chain.factor`：全局锁 + 512 全清

`Chain.java:68-92` 里 `factor` 是 `synchronized`，整个递归（`cost` 深度遍历 + `baseOf`/`levelFactor` 回调）都持全局锁；`LOCAL.size() > 512` 时清空所有村缓存（`:79-82`），村多时抖动。

改进点：

1. 递归部分不持锁：`LOCAL.computeIfAbsent(villageKey, ...)` 拿到 map 后递归无锁进行（村键含 day，天然按天隔离），只对 map put 做并发保护。
2. 淘汰策略改按天/按村 LRU，而非 `>512` 一键清空。村键 `seed:x:z:day` 含天，旧天条目天然不再命中，可按「同一天只保留最近访问的 N 个村」淘汰。
3. P2 落地后这里顺带受益：`cost` 里每次 `baseOf(id)`/`levelFactor(id)` 回调命中 P2 缓存，材料成本从重算价格链降为查表。

`RecipePricer.compute`（`RecipePricer.java:63-100`）是 `O(PASSES × 配方数 × 格数 × 候选)`，且 `priceOf` 反复调 `fallbackOf`（= `Bridge.seedValue`，含 `rarity`/`foodFactor`）没内部 memo。它只在 load/reload 跑一次（结果存 `recipePrices`），是启动期成本，不影响每帧；若整合包配方上万条可顺手给 `fallbackOf` 加临时 map。

### P5 `offers()` 报价无缓存（可选）

`offers`（`VillageMarket.java:362-527`）每次开面板/成交重开都全量重算，代码已有 ≥100ms 的分段计时日志（`:520`）。整张单子对 `(村, 天, 库存版本)` 是确定性的，只有库存/银库/等级是变量。可考虑按 `(vkey, day, store.version())` 缓存「静态部分」（选出的条目 + 基础系数），展示时只叠库存 `buyImpact` 和 `uses`。这会引入缓存一致性复杂度，建议先做 P1–P4 把单次成本压下去，再看是否还需要。

### P6 `listedIn` / 产业清单每次展开

`listedIn`（`VillageMarket.java:766+`）在 `levelFactor`/`universalSellUnit` 里对每件货遍历 `ind.demand()/supply()` 并逐条 `expandEntry`（`expand` 本身有缓存）。`DemandGraph` 已是缓存实例（`DemandGraph.java:14/29`）。可预建 `Industry → Set<itemId>`（demand/supply 各一张），把成员判断从 O(#条目) 降 O(1)。收益次于 P1/P2。

---

## 二、正确性 bug（建议优先修）

### P7 `TradeStore.settle` 用错基础价算库存上限

`TradeStore.java:111-113` 的每日库存回补：

```java
for (Map.Entry<String, Double> e : m.entrySet()) {
    e.setValue(Stock.advance(e.getValue(), capOf(e.getKey()), today - last));
}
```

而 `capOf`（`TradeStore.java:118-121`）：

```java
private static double capOf(String item) {
    double base = dev.guoke.tradepost.Tradepost.valueOf(item);   // 只查 values.json 手写价！
    return base <= 0 ? 64 : Stock.cap(base);
}
```

系统其他所有地方（`Stock.capFor`、`Bridge.capOf`、`offers` 里的 `Stock.cap(raw)`）用的都是**完整 basePrice**（手写 → 标签 → 配方 → 类别 → 兜底）。于是：

- 凡是不在 `values.json`、靠标签价/配方价/类别价定价的物品，`Tradepost.valueOf` 返回 0 → `capOf` 一律当 64；
- 真实 cap 可能是 256（base 2）、4096（base 0.12）等；
- 结果：这类货的库存回补目标 = `64×0.5=32`，远低于真实半仓，长期停在低库存 → 买侧 `buyImpact` 永远偏向涨价端（`Stock.buyImpact` 在 r<0.5 时 >1）。

建议：`capOf` 改为 `Stock.cap(Bridge.basePrice(item))`，或直接复用 `Bridge.capOf(...)` 的口径，与读侧（`get()` 传入的 cap）对齐。只影响日结算回补方向，不破坏存档格式。

---

## 三、机制层（改「模型」，可选但影响玩法深度）

### M1 卖价 0.75 封顶吃掉整套卖价公式（最重要）

`SYSTEM_REPORT.md` §4.2 + §11.1 已量化：标准村下「卖/买」恒等于 0.75，四个需求档的实际成交价完全一样（都是 1.275）。数学上，只要 `sellBase(1.18) × 需求系数` 大于 `buyBase(0.85) × 0.75 = 0.6375`，封顶就永远生效——而最低的需求档（本村出产 0.90）也有 `1.18×0.90=1.062 > 0.6375`。所以卖侧的 `demandBonus/dishPremium/sellProsperity` 在库存中性时全是死代码，只在库存偏空、`ask` 被 `buyImpact` 抬起来时才漏一点出来。

三个方向（任选或组合）：

1. **动态价差替代常数 0.75**：`min(unit, ask × spread(r))`，`spread(r)` 随库存比 r 在 `[0.60, 0.95]` 间变化（空仓收紧防套利、满仓放宽）。卖价公式真正生效，跑商能看出村与村需求差，而不是恒定 75 折。
2. **调平常数**：把 `sellBase` 压低或把 `spreadFloor` 抬到接近中性，让卖价公式在 50% 库存时 ≈ `ask×0.85~0.90`，需求系数成为主要区分器。
3. **重新定义 ask**：让 `askUnit` 代表「村子的重置成本/边际价值」而非「买侧要价」，卖价 = `ask − 边际利润`，用绝对/比例毛利约束套利，而不是粗暴 ×0.75。

推荐方向 1（动态价差）：既保留防倒卖的闸门，又把卖侧信号还回来，改动集中在 `Bridge.sellUnit` 的 `min(unit, ask*0.75)` 一处。

### M2 卖价与「买侧要价 ask」强耦合

`universalSellUnit`（`VillageMarket.java:683-710`）的封顶基准是 `askUnit`（买侧同一套公式），导致卖价永远跟着买价走。若 M1 采用方向 3，这里顺便解耦：卖侧独立算「本村对该货的边际价值」，套利边界用成本/毛利约束。

### M3 全球饱和曲线的两个可调点

`worldIndex = 1/(1+(n/2000)^p)`（`TradeStore.java:257-266`，p=2）本身是合理的软饱和。可考虑增强：

- **K 按类别/稀有度分档**：大宗货（圆石、木板）K 给更高（如 20000），奢侈品给更低，避免「低价值品卖 2000 件就崩」的体感失真。当前只有 `worldMinBase=1.0` 一个闸（<1.0 不记账）。
- **地板抬高**：`worldMinIndex=0.02` 可抬到 0.05，避免彻底一文不值。恢复速度（`worldDecay=0.005`，约 138 天减半）已符合「崩得快、回得慢」，保持即可。

### M4 稀有度倍率覆盖面

`Rarity.mult` 只对「非手写价、非配方反推价」生效（`Bridge.java:539/543`，报告 fact 3）。这是有意的（手写价=白名单、配方价材料里已含稀有度）。但 `netherite_ingot=180` 这种手写价 Legendary 货不走倍率，与「稀有度轴」直觉略有割裂。可加「values.json 声明 tier 则叠倍率」的可选规则，但默认保持现状更安全，避免打破手写价的锚定意义。

### M5 库存/区域压力联动（一处微调）

`area` 只保留正净流入并 clamp 到 0（`TradeStore.java:141-145`），「倾销有压力、买空自然回弹」，符合直觉。`areaRadius=1024`、`areaFullCaps=2.0` 合理。唯一建议：`pressure` 里 `Math.max(16, cap×2.0)`（`TradeStore.java:161`）对高 cap 大宗货（cap 4096）阈值 8192 件偏大，可加 `min` 一个绝对上限，否则便宜货很难触顶区域饱和。

### M6 死代码清理

`VillageLevel.sellMul(lv)`、`buySlots(lv)`、`restockMult(lv)` 全仓无调用点（报告 fact 2）。等级对价格的实际影响只剩 `walletMult`/`stockMult`/`buyMul`/行数。建议删掉或接上——若想让等级直接作用于买卖价，`sellMul` 正是现成挂点，接到 `sellUnit`/`universalSellUnit` 末尾即可（顺带解决 M1 之后等级在卖侧无感的问题）。

---

## 四、建议落地顺序与风险

1. **先修 P7**（正确性，一行改动，影响日结算）：`TradeStore.capOf` 改用完整 basePrice。
2. **做 P1 + P2 + P3**（性能，纯缓存，零玩法变化）：`bannedItem`/`value`/`isEdible` 各加 reload 时清空的 map。三条合起来预计能把「开面板/成交」的主线程耗时砍掉一大半（日志里那类 ≥100ms 的货架重算主要就是这三条反复横跳）。
3. **做 P4**（Chain 锁粒度 + 淘汰策略）。
4. **机制层 M1/M6** 单独评估——涉及玩法手感，建议先在配置里调 `spreadFloor`/`sellBase` 试跑，不与性能改动混在一起上线，便于回滚对比。

风险提示：

- 性能缓存类（P1/P2/P3/P4）唯一要保证的是 **`/tradepost reload` 时全部失效**（`Bridge.clearCatalogCache`/`TagCatalog.clear` 已有一条龙清空入口，新缓存放进去即可），否则改配置不生效是唯一可能的坑。
- 机制改动（M1）影响经济平衡，建议用报告 §11 的「标准村数值示例」先做一次手算对照再上线。
