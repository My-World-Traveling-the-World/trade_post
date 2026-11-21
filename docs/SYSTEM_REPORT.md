# 集市（tradepost）现行体系机制与公式报告

> 描述对象：`/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/mods/tradepost-0.1.0.jar`
> md5 `2e3287fc90fe1209e58bd1d0d9fecefc`，361,630 B（构建于 2026-11-13 16:29:38；同 md5 另有一份在 `/home/guoke/mc-linux/versions/WAYFARING1.7.0 20260928/mods/`）。
> 源码根：`/home/guoke/mc-linux/tradepost/src/main/java/dev/guoke/tradepost/`
> 本文只描述**当前实现**（机制、公式、常量、按真实配置算出的数值示例），不含评价、建议或改动方案。

---

## 1. 读取路径与分层

一次交易的价格由四层依次决定：

| 层 | 函数 | 位置 | 作用 |
|---|---|---|---|
| ① 基础价 | `Bridge.basePrice(String)` | `mc/Bridge.java:1618` | 物品的全图唯一定价（values.json / 标签价 / 配方成本 / 类别价 / 兜底） |
| ② 单村单价 | `VillageMarket.offers(...)`（买）、`VillageMarket.universalSellUnit(...)`（卖）、`VillageMarket.askUnit(...)`（封顶用的问价） | `market/VillageMarket.java:362 / :683 / :644` | 用村坐标哈希出来的产业、富庶度、商路、村癖、行情、库存，把基础价折成本村单价（double，绿宝石/件） |
| ③ 面板报价 | `Pricing.minimal`（买）/ `Lot.of`+`Lot.price`（卖） | `market/Pricing.java:79`、`market/Lot.java:26/45` | 压成"整数件 ↔ 整数绿宝石"的一次成交份 |
| ④ 成交钳制 | `MarketMerchant.add(...)` | `mc/MarketMerchant.java:755` | 按库存余量、银库余额、堆叠上限、金额槽上限，决定这一行能列几次 |

价格计算全部在服务端主线程完成；客户端只画服务端下发的 json（`MarketMerchant.rebuild()` → `send` → `MarketScreen.setPayload`）。

---

## 2. 基础价 basePrice

```
basePrice(id) = Tradepost.valueOf(id)            // values.json 手写价，>0 时直接返回
              = CATALOG.value(id)                // 否则走目录
```

`CATALOG.value`（`mc/Bridge.java:536`）：

```
raw = rawValue(id)
value = raw                                          // 手写价（第 539 行）
      = raw                                          // 配方反推价 且 不是菜/酒（第 543 行）
      = raw * Rarity.mult(rarity(id))                // 其余（标签价、类别价、兜底价）
```

### 2.1 rawValue 的取值顺序（`mc/Bridge.java:571-608`）

1. `Tradepost.valueOf(id)` 手写价（values.json）→ 直接返回，**不乘稀有度**（等同白名单）。
2. 黑名单命中（`blacklist.json` 的 tags/patterns/namespaces 等）→ 返回 0（不参与贸易）。
3. 标签价 `tag_values.json` 的派生表 `derived()` → `标签基价 × foodFactor(id)`。
4. 配方成本价 `recipePrices`（`RecipePricer.compute`，PASSES=4）→ `max(配方价, RECIPE_FLOOR=0.1)`。
5. `banRareUnpriced` 打开时，来源轴为 `rare`/`epic` 且无标签价 → 返回 0。
6. 类别默认价 `categories.json` 里的 `price` → `类别价 × foodFactor(id)`。
7. 兜底 `tag_values.json` 的 `"default"`（默认 0.05）→ `兜底 × foodFactor(id)`。

酒类地板：`rawValue` 在返回前对"带 `kaleidoscope_tavern:brew_level` 的酒"（`market/Drinks.java` 内置 40 个酒水 path + 运行期 `SEEN` 登记 + `_wine/_juice/_brew/_cocktail/_special` 后缀）做 `if (v < 8.0) v = 8.0`（`DRINK_FLOOR = 8.0`，`mc/Bridge.java:569`）。

### 2.2 foodFactor（`mc/Bridge.java:638`）

```
foodFactor(id) = 4.0   若 isEdible(id)      // Economy.foodMultiplier，默认 4.0
               = 1.0   其余
```

### 2.3 稀有度倍率

档位 `rarity(id) = Rarity.of(id, rawValue(id), rarityTable)`（`mc/Bridge.java:695`）：价格轴与来源轴取高。

- 价格轴（`Rarity.byValue`，`market/Rarity.java:116`）：`<2 → R1`，`<8 → R2`，`<40 → R3`，`>=40 → R4`。
- 来源轴：`rarity.json` 的 `t`（世界生成密度 / 战利品来源）。
- 倍率 `Rarity.MULT = {1.0, 1.0, 1.6, 2.4, 3.5}`（下标 1..4）。

稀有度另外三项作用：`LEVEL = {0,1,1,3,4}`（买它要求的村庄等级）、`USES = {0,64,16,2,1}`（每村每天成交笔数上限，R1/R2 交给库存曲线）、`saturationFree(tier>=3)`（R3/R4 不吃全球饱和）。

### 2.4 values.json 实测分布

`config/tradepost/market/values.json` 共 233 条，全部为数值：

| 统计 | 值 |
|---|---|
| 最小 | 0.03 |
| 中位数 | 0.8 |
| 最大 | 180（`minecraft:netherite_ingot`） |
| ≤0.1 | 17 条 |
| ≤1 | 166 条 |
| ≤2 | 187 条 |
| ≤8 | 213 条 |
| ≤40 | 225 条 |

`categories.json` 有 65 个类（grain / tuber / vegetable / fruit / berry …），部分带 `price`。

配方定价规则（`market/RecipePricer.java`）：只在这些条件同时成立时给产物定价 —— 产物不在手写价/标签价里、每一格材料都有价（一格多选时取最便宜）、同一产物有多条配方时取最便宜那条；迭代 4 轮穿透"原木→木板→木棍→栅栏"这类链。工业品成本乘 `Economy.craftPenalty`（默认 0.65）；菜与酒乘 `Cuisine.bonus` 或用 `craftPenalty`，并有 `Cuisine.minPrice` 地板。

---

## 3. 买价：村庄卖给玩家（`VillageMarket.offers`，`market/VillageMarket.java:362`）

### 3.1 每行单价

```
base   = basePrice(item) × worldIndex(item)          // withIndex()，R3/R4 不乘；下限 0.01
unit   = base
       × chainFactor(...)                             // 制作链联动，基础货 = 1.0
       × buyBase                         // 0.85
       × buyProsperity(=buyFactor())      // 见 3.2
       × roadFactor()                     // 见 3.2
       × quirk(seed,x,z,item)             // 1 ± quirkPct(0.12)
       × daily(seed,x,z,day,ind.id())     // 1 ± dailyPct(0.10)
       × marketDayFactor(buy)             // 集市日 1.10，否则 1.0
       × Stock.buyImpact(stock/cap)       // 见 3.3
```

特例：`minecraft:totem_of_undying` 固定 `unit = 64.0`（`TOTEM_PRICE`，`market/VillageMarket.java:114/421`）。

制作链 `chainFactor`（`market/Chain.java:68`）：只有"能用配方做出来"的物品才会偏离 1.0。

```
chainFactor = clamp( 本村成本 / 中立成本 , 0.6 , 2.0 )
本村成本：按配方递归到基础货，每件材料乘本村的 levelFactor（需求 1.35 / 本村出产 0.90 / 用途大类 1.18 / 菜酒再 ×1.35）
中立成本：同一套递归，levelFactor 固定 1.0
深度上限 MAX_DEPTH = 6；加成项（craftPenalty / 菜酒溢价 / 地板价）在分子分母中同时出现，互相抵消
```

### 3.2 村庄系数（`market/VillageMarket.java:142-227`）

```
houses()      = 结构片段栋数；拿不到时按占地估：area × wealthHouses / 3600
wealth        = clamp( houses/wealthHouses × variety , 0.30 , 2.60 )
variety       = min(1.20, 1 + wealthVariety(0.03) × (kinds − 5))，kinds<=0 时 =1.0
buyFactor()   = clamp(1.15 − 0.15 × wealth, 0.85, 1.35)      // 大村货多且便宜
sellFactor()  = clamp(0.85 + 0.15 × wealth, 0.80, 1.30)
stockScale()  = clamp(0.60 + 0.40 × wealth, 0.50, 1.60)
roadFactor()  = max(0.60, 1 − 0.04 × min(roadDeg, 3))        // 买卖两侧都乘
quirk         = 1 + 0.12 × signedHash(seed, vx, vz, itemHash)  // 固定村癖
daily         = 1 + 0.10 × signedHash(seed, vx, vz, day, categoryHash)
marketDay     = 每 6 天一个村被选中；那天卖给你 ×1.10、收你的 ×1.35
```

### 3.3 库存曲线对买价（`market/Stock.java:57`）

```
buyImpact(r) = clamp(1 − 0.85 × (r − 0.5), 0.55, 1.45)     // r = stock/cap，中性 0.5 → 1.0
```

### 3.4 一份的件数与价格

- 单价 double → `Pricing.minimal(unit)`：`lot = Lot.of(unit)`、`price = Lot.price(unit, lot)`（见 §10）。
- 每行成交上限 `uses = round((驿站?64:16) × stockMult(lv))`，再被 `Stock.maxUsesFor(stock, 一份件数, uses) = min(uses, floor(stock/件数))` 压；R3/R4 再 `min(Rarity.uses(tier) + max(0, lv − gate(tier)))`。

### 3.5 面板这一行实际卖多少件（`mc/Bridge.java:3649-3667`）

```
bundle = min(64, max(一份件数, 8 + 6 × (lv − 1)))          // Lv1 8 件 … Lv5 32 件
bundle = min(bundle, 该物品堆叠上限)                        // 鸡蛋 16、药水 1 等
rawPay = round(一份价 × bundle / 一份件数)
pay    = offerPrice(rawPay, 品质档)                          // 见 §7
while (pay > 64 && bundle > 1) { bundle /= 2; 重算 }
```

`offerPrice`（`mc/Bridge.java:2741`）：`p = round(price × Quality.mult(level))`；`p > 576` 时抬到 `round(p/9)×9`。

---

## 4. 卖价：村庄从玩家收购（`Bridge.sellUnit`，`mc/Bridge.java:2312`）

### 4.1 公式

```
特例：totem_of_undying → 48.0（= 64 × 0.75），直接返回

base   = basePrice(item) × worldIndex(item)
unit   = base
       × chainFactor(...)
       × sellBase(1.18)
       × 需求系数      需求清单 1.35 / 用途大类 1.18 / 中性 1.00 / 本村出产 0.90
       × 菜酒溢价      菜或酒再 ×1.35
       × sellProsperity(= sellFactor())
       × roadFactor()
       × quirk
       × daily
       × marketDayFactor(sell)         // 集市日 1.35
       × Stock.sellImpact(r)           // r = clamp(库存比 + 区域压力×0.45, 0, 1)

ask    = askUnit(...)                  // 与买价同一套公式（含 buyImpact），不查货架
unit   = min(unit, ask × 0.75)         // LOCAL_SPREAD = 0.75
         （ask 算不出来时：unit ×= 0.75）
unit   ×= Quality.mult(level)          // level>0 时最后乘品质倍率
```

- `sellImpact(r) = clamp(1 − 0.65 × (r − 0.5), 0.68, 1.33)`（`market/Stock.java:67`）。
- `askUnit`（`market/VillageMarket.java:644`）与买价同公式，但**不依赖货架**：即使这件货已被买空，仍能算出本村此刻要价，从而 0.75 封顶始终有效。
- 买页货架上那口价（`Ctx.buyUnits()`）若比 ask 更低，则用架上价替代 ask（`mc/Bridge.java:2336-2340`）；带品质的货先把架上价除回无品质底价再比。
- 收购价全部走这一个函数：`Bridge.java:2859 / 3029 / 3128 / 3212 / 3304 / 3913` 六个调用点。

### 4.2 0.75 封顶在数值上的表现

标准村（wealth=1.0、无商路、无村癖/行情偏差、非集市日、库存 50%）下，卖价公式本身远高于封顶值，实际成交价一律等于 `ask × 0.75`：

| 需求档 | 卖价公式（base=2，铁锭） | 封顶值 | 实际取 |
|---|---|---|---|
| 需求清单 1.35 | 3.186 | 1.275 | 1.275 |
| 用途大类 1.18 | 2.785 | 1.275 | 1.275 |
| 中性 1.00 | 2.360 | 1.275 | 1.275 |
| 本村出产 0.90 | 2.124 | 1.275 | 1.275 |

（需求系数在"库存中性"时被封顶吃掉；它只在库存偏空、ask 被 buyImpact 抬高时才反映到收购价上。）

集市日（base=2、库存 50%）：ask = 1.870（buyBase 0.85 × marketDayBuy 1.10 × 2），封顶 1.403，实际 1.403。

### 4.3 收购量的三道限制（`mc/MarketMerchant.java:794-819`）

```
structural = min(lots, 1152 / 单价, 该物品堆叠上限 / lot)      // MAX_INCOME = 1152
avail      = min(structural, floor((cap − 库存) / lot))         // 非装备行
avail      = min(structural, floor((银库余额 + 玩家零头) / 单价)) // 装备行
avail <= 0 → 仍列这一行，但标成"售罄"（原版 uses = maxUses，画红叉、点不动）
```

装备回收另有每村每天 2 件的额度（`GEAR_PER_DAY = 2`，`mc/Bridge.java:2403`）。

### 4.4 卖页行序与"特收"

- 行序 = 背包装进来的顺序（不按价钱排序），最多 40 行（`while (rows.size() > 40) rows.remove(rows.size()-1)`，`mc/Bridge.java:3598`）。
- 桶规则（`countInto`）：普通货按 `id`；带品质按 `id#q`；附魔书按 `book:附魔|等级` 或 `book:a@1;b@2`；装备按 `gear:成色指纹`。天平两格（槽 0/1）上的货同样计入"你有的"。
- 特收：`tag == "[缺]"` 的行标 `special=1` 并提到最前，行数 `specialMax = clamp(1 + lv, 2, 6)`（`mc/Bridge.java:3574`）。

---

## 5. 库存（`market/Stock.java`）

```
cap(base) = clamp( round( 512 / max(0.05, base) × stockScale() × stockMult(lv) ) , 16 , 4096 )
capFor(base, station)        = min(4096, cap × 2)        // 驿站翻倍
capFor(base, station, food)  = min(capFor, 64)           // 走标签/配方价的食材、菜、酒
Bridge.capOf(...)            = capFor(...)；酒再 max(c, 128)
ratio      = clamp(stock / cap, 0, 1)
advance(days): 目标 = cap × 0.5
   stock < 目标 → 每天 + cap × stockRegen(0.02)，最多到 0.5
   stock > 目标 → 每天 − cap × stockRegen × SURPLUS_DRAIN(2.0)，最多回落到 0.5
```

标准村（wealth=1、Lv1）下 cap：base 0.03→4096（上限夹取）、0.12→4096、0.36→1422、0.8→640、2→256、8→64、24→21、180→16（下限夹取）。

---

## 6. 银库（`Bridge.Ctx.walletCap`，`mc/Bridge.java:2207`）

```
walletCap = walletT2(1200) × clamp(wealth, 0.33, 2.5) × walletMult(lv)
walletMult(lv) = 1 + 0.25 × (lv − 1)
每天回血 walletRegen = 0.03（按上限比例）；新村子初始 walletStart = 0.6
卖货付款上限 = 银库余额 + 玩家零头账（TradeStore.credit）
买货收款：玩家付的钱进本村银库（walletAdd）
```

零头账（`mc/TradeStore.java:184/199`）：卖货 `receive(exact)` 向下取整付现、余数记账；买货 `charge(exact)` 先用账上零头抵扣、不足部分向上取整。

---

## 7. 品质（酒）与市场饱和

### 7.1 品质倍率（`market/Quality.java`）

```
MULT  = {1.0, 0.45, 0.70, 1.00, 1.60, 2.60, 4.00}   // 下标 0..6；0 = 无品质组件
NAMES = {"", "难以下咽", "劣质", "普通", "优质", "精酿", "典藏"}
档位 = Quality.forVillage(vkey, itemId, day)（按村/物品/周确定性）；买页至少 2 档，Lv4 起至少 3 档
买价：offerPrice(price, 档) = round(price × MULT[档])
卖价：sellUnit 末尾 × MULT[档]（先按无品质价封顶，再乘倍率）
```

### 7.2 全球饱和（`mc/TradeStore.java:257`，`market/VillageMarket.java:609`）

```
idx(件数) = clamp( 1 / (1 + (累计净件数 / 2000)^2 ) , 0.02 , 1.0 )
每天按 worldDecay = 0.005 消退（约 138 天减半）
只有 basePrice >= worldMinBase(1.0) 的货才记账；R3/R4 不乘 idx
idx 只乘价格，不改库存上限（单位：件数，不看单价）
```

### 7.3 区域压力（`mc/TradeStore.java:154`）

```
pressure = min(1, 片区内该物品净流入 / max(16, cap × areaFullCaps(2.0)))
片区内所有村子共享；面积半径 areaRadius = 1024 格；每天消退 areaDecay = 0.05
卖价侧：r = clamp(库存比 + pressure × areaSpread(0.45), 0, 1)
```

---

## 8. 村庄等级（`market/VillageLevel.java`）

```
XP 门槛 = {0, 600, 3000, 12000, 45000}（Lv1..Lv5）
单笔经验 = min(40, 2 × √绿宝石额) × (卖货 ? 0.6 : 1.0)      // MAX_XP_TRADE = 40
存档版本 v3（快曲线 {0,200,700,1800,4000}）读档时按"等级+级内进度"折算到新曲线
```

| 项 | Lv1 | Lv2 | Lv3 | Lv4 | Lv5 |
|---|---|---|---|---|---|
| 买页额外行（`sellSlots − sellSlots(1)`） | 0 | +2 | +5 | +9 | +14 |
| 每两级杂货 +1（`extraMisc`） | 0 | 0 | +1 | +1 | +2 |
| 库存上限倍率 `stockMult` | 1.00 | 1.15 | 1.30 | 1.45 | 1.60 |
| 银库倍率 `walletMult` | 1.00 | 1.25 | 1.50 | 1.75 | 2.00 |
| 学术铺/军需铺价格 `buyMul` | 1.000 | 0.985 | 0.970 | 0.955 | 0.940 |
| 可买最高稀有度 `topTier` | R1 | R2 | R3 | R4 | R4 |
| 买页行数预算（villageOffers 14 + SUPPLY_EXTRA 5 + 菜 3 + 杂货 4 的口径） | 19 | 21 | 25 | 29 | 35 |

行数预算（`market/VillageMarket.java:370-390`）：

```
cap        = (station ? 24 : 14) + roadDeg × 1
totalCap   = cap + 5 + extraSupply + extraMisc
supplyCap  = max(1, cap − (菜位 + 杂货位)) + 5 + extraSupply
菜位 = 3（驿站 +1）；杂货位 = 4（驿站 +2）+ extraMisc
```

---

## 9. 四条专用通道

### 9.1 装备回收（黑名单装备；`mc/Bridge.java:2416-2581`）

```
资格：CATALOG.banned(id) 为真 且 gearBase(id) > 0
gearBase(id) = max( recipePrices[id] , gearMaterial(id) )      // 取配方价与"按材料清单重算"的较大者
gearPrice    = round( gearBase × 0.55 × gearDur × (1 + 0.08 × min(24, 附魔总等级)) )，夹在 1..1152
gearDur      = clamp(1 − damage/maxDamage, 0.35, 1.0)
成色指纹 gearSig = id + "/e" + 附魔总等级 + "/d" + round(耐久×100) + "/p" + 回收价
每村每天额度 2 件；按回收价降序挑；单价 < 10 的行不占额度
```

### 9.2 附魔书（`market/BookShop.java`）

```
I 级基准价（节选）：sharpness 5 / protection 5 / efficiency 5 / unbreaking 6 / power 5
                    fortune 14 / silk_touch 12 / mending 18 / looting 10 / thorns 7
表外附魔（模组附魔等）基准价 UNKNOWN_BASE = 6.0
等级倍率 LMULT = {1.0, 1.0, 1.8, 3.0}（I / II / III）
基准价 = I 级价 × LMULT[等级]
多附魔一本：各附魔基准价相加
收购价 = max(1, round(基准价 × 0.75))
学术铺售价 = round(基准价 × buyMul(村等级))，每村每天 1~2 本、最多 4 行、按（村, 周, 等级）确定性生成
```

### 9.3 军需铺成品装备（`market/GearShop.java`）

```
行数 = min(5, 村等级)；每天额度 = 1（L1-2）/ 2（L3-4）/ 3（L5）
价格 = MAT_COST[材质] × KIND_COST[部位] × markup(附魔) × buyMul(村等级)
MAT_COST  = {木 1, 石 2, 皮 3, 锁 4, 铁 7, 金 10, 钻 26, 下界合金 64}
KIND_COST = {剑 2.2, 镐 2.6, 斧 2.6, 锹 1.4, 锄 1.4, 盔 3.2, 胸 5.0, 腿 4.4, 靴 2.6}
markup    = 1.0 / 1.15 / 1.40 / 2.00（按附魔等级）× 1.5（时运/精准/抢夺/经验修补）
```

### 9.4 秘宝（`market/VillageMarket.java:255`）

```
25% 的村有；每 7 天换一批；每批最多换走 4 次（驿站 8 次）
成本只收物品（可用标签，按 baseOf 折算件数，1..64），不给绿宝石
```

---

## 10. 取整规则

```
Lot.of(unit)   ：在 1..64 件里找第一个满足 |unit×k − round(unit×k)| / (unit×k) <= 5% 的 k
Lot.price(unit, lot) = max(1, round(unit × lot))
Pricing.minimal(unit) = Quote(Lot.of(unit), Lot.price(unit, lot))
Pricing.buyQuote(unit) ：items = clamp(floor(64/unit), 1, 64)，emeralds = clamp(round(items×unit), 1, 64)（旧接口，面板不用）
MarketMerchant：买价 <= 576 原样；> 576 抬到 9 的倍数，上限 639
                卖价 <= 1152 原样（MAX_INCOME）
```

---

## 11. 按真实配置算出的数值示例

统一假设（下称"标准村"）：`wealth = 1.00`（富庶度 1.0，对应 `wealthHouses = 12` 栋）、`roadDeg = 0`、`quirk = 1.00`、`daily = 1.00`、非集市日、`lv = 1`、库存比 0.50。此时 `buyFactor = sellFactor = stockScale = 1.00`、`buyImpact = sellImpact = 1.00`。

### 11.1 单件价格（买 / 卖 / 一份）

| basePrice | 库存上限 cap | 买单价 | 买一份 | 卖单价 | 卖一份 | 卖/买 |
|---|---|---|---|---|---|---|
| 0.03 | 4096 | 0.0255 | 38 件 = 1 颗（0.026/件） | 0.0191 | 50 件 = 1 颗（0.020/件） | 0.75 |
| 0.12 | 4096 | 0.102 | 10 件 = 1 颗（0.100/件） | 0.0765 | 13 件 = 1 颗（0.077/件） | 0.75 |
| 0.36 | 1422 | 0.306 | 10 件 = 3 颗（0.300/件） | 0.2295 | 9 件 = 2 颗（0.222/件） | 0.75 |
| 0.80 | 640 | 0.680 | 3 件 = 2 颗（0.667/件） | 0.510 | 2 件 = 1 颗（0.500/件） | 0.75 |
| 2.00（铁锭） | 256 | 1.700 | 3 件 = 5 颗（1.667/件） | 1.275 | 3 件 = 4 颗（1.333/件） | 0.75 |
| 8.00 | 64 | 6.800 | 1 件 = 7 颗 | 5.100 | 1 件 = 5 颗 | 0.75 |
| 24.00（钻石） | 21 | 20.40 | 1 件 = 20 颗 | 15.30 | 1 件 = 15 颗 | 0.75 |
| 180.0（下界合金锭） | 16 | 153.0 | 1 件 = 153 颗 | 114.75 | 1 件 = 115 颗 | 0.75 |

### 11.2 库存对同一件货的影响（铁锭 base = 2）

| 库存比 r | buyImpact | sellImpact | 要价 | 收购价 | 一份 |
|---|---|---|---|---|---|
| 0.10 | 1.340 | 1.260 | 2.278 | 1.708 | 3 件 = 5 颗 |
| 0.20 | 1.255 | 1.195 | 2.133 | 1.600 | 3 件 = 5 颗 |
| 0.50 | 1.000 | 1.000 | 1.700 | 1.275 | 3 件 = 4 颗 |
| 0.80 | 0.745 | 0.805 | 1.266 | 0.950 | 11 件 = 10 颗 |
| 1.00 | 0.575 | 0.680 | 0.977 | 0.733 | 4 件 = 3 颗 |

### 11.3 酒（base 经 DRINK_FLOOR 抬到 8.0，六档）

| 档 | 倍率 | 买单价 | 卖单价 | 卖一份 |
|---|---|---|---|---|
| 1 难以下咽 | 0.45 | 3.06 | 2.29 | 3 件 = 7 颗 |
| 2 劣质 | 0.70 | 4.76 | 3.57 | 2 件 = 7 颗 |
| 3 普通 | 1.00 | 6.80 | 5.10 | 1 件 = 5 颗 |
| 4 优质 | 1.60 | 10.88 | 8.16 | 1 件 = 8 颗 |
| 5 精酿 | 2.60 | 17.68 | 13.26 | 1 件 = 13 颗 |
| 6 典藏 | 4.00 | 27.20 | 20.40 | 1 件 = 20 颗 |

### 11.4 装备回收

| 装备 | 材料价 gearBase | 附魔总等级 | 耐久 | 回收价 |
|---|---|---|---|---|
| netherite_sword | 265.87 | 4（锋利 IV） | 1.00 | 193 |
| netherite_sword | 265.87 | 0（白板） | 1.00 | 146 |
| netherite_leggings | 234.63 | 0 | 1.00 | 129 |
| mace | 0.60 | 0 | 1.00 | 1 |

### 11.5 附魔书（收购价 = 基准 × 0.75 取整）

| 附魔 | I 级基准 / 收 | II 级基准 / 收 | III 级基准 / 收 |
|---|---|---|---|
| sharpness（表内 5） | 5.00 / 4 | 9.00 / 7 | 15.00 / 11 |
| fortune（表内 14） | 14.00 / 11 | 25.20 / 19 | 42.00 / 32 |
| mending（表内 18） | 18.00 / 14 | 32.40 / 24 | 54.00 / 41 |
| 表外附魔（6.0） | 6.00 / 5 | 10.80 / 8 | 18.00 / 14 |

### 11.6 全球饱和（`worldSellK = 2000`、`worldCurveP = 2.0`）

| 累计净件数 | 0 | 500 | 1000 | 2000 | 4000 | 8000 |
|---|---|---|---|---|---|---|
| 价格指数 | 1.000 | 0.941 | 0.800 | 0.500 | 0.200 | 0.059 |

### 11.7 银库与经验

| 项 | Lv1 标准村 | Lv5 标准村 |
|---|---|---|
| 银库上限 | 1200 | 2400 |
| 银库初始 | 720 | 1440 |
| 每天回血 | +36 | +72 |
| 升 Lv2 需要 | 600 经验（单笔封顶 40，至少 15 笔大额；卖 193 颗的剑 16.7/笔 → 约 36 笔） | — |
| 单笔经验示例 | 卖 193 颗的剑：min(40, 2×√193)=27.8 ×0.6 = 16.7 | — |

---

## 12. 常量速查（`config/tradepost/market/economy.json`，全部可改，`/tradepost reload` 生效）

| 键 | 值 | 用途 |
|---|---|---|
| buyBase | 0.85 | 买价基准 |
| sellBase | 1.18 | 卖价基准 |
| demandBonus | 1.35 | 需求清单溢价 |
| roleDemand | 1.18 | 用途大类溢价 |
| supplyPenalty | 0.90 | 本村出产压价 |
| quirkPct / dailyPct | 0.12 / 0.10 | 村癖 / 每日行情幅度 |
| buySlope / buyGlutMin / buyScarcityMax | 0.85 / 0.55 / 1.45 | 买价库存曲线 |
| sellSlope / sellGlutMin / sellScarcityMax | 0.65 / 0.68 / 1.33 | 卖价库存曲线 |
| spreadFloor | 0.75 | 收购价封顶（代码里 `LOCAL_SPREAD`） |
| stockRegen | 0.02 | 库存日回补（高于半仓按 2 倍速度回落） |
| areaRadius / areaSpread / areaFullCaps / areaDecay | 1024 / 0.45 / 2.0 / 0.05 | 区域市场压力 |
| marketDayPeriod / marketDaySell / marketDayBuy | 6 / 1.35 / 1.10 | 集市日 |
| villageOffers / stationOffers | 14 / 24 | 货架预算基数 |
| itemsPerEntry / miscSlots / cuisineSlots | 4 / 4 / 3 | 每条目件数 / 杂货位 / 菜酒位 |
| dishPremium / craftPenalty / foodMultiplier | 1.35 / 0.65 / 4.0 | 菜酒溢价 / 配方折扣 / 食物倍数 |
| walletT1 / T2 / T3 / walletRegen / walletStart | 400 / 1200 / 3000 / 0.03 / 0.6 | 银库 |
| worldSellK / worldCurveP / worldDecay / worldMinIndex / worldMinBase | 2000 / 2.0 / 0.005 / 0.02 / 1.0 | 全球饱和 |
| roadPriceDrop / roadMaxDegree / roadExtraOffers | 0.04 / 3 / 1 | 商路 |
| wealthHouses / wealthVariety | 12 / 0.03 | 富庶度标定 |

代码内常量：`LOCAL_SPREAD 0.75`、`GEAR_BUYBACK 0.55`、`GEAR_ENCH 0.08`、`GEAR_DUR_FLOOR 0.35`、`GEAR_PER_DAY 2`、`TOTEM_PRICE 64`、`DRINK_FLOOR 8.0`、`RECIPE_FLOOR 0.1`、`SUPPLY_EXTRA 5`、`MAX_PAY 639`、`MAX_PAY_EXACT 576`、`MAX_INCOME 1152`、`MAX_LOT 64`、`Lot.TOL 0.05`、`MAX_XP_TRADE 40`、`RecipePricer.PASSES 4`、`Chain.MAX_DEPTH 6`。

---

## 13. 数据与存档

配置文件目录 `config/tradepost/market/`：`values.json`（手写价 233 条）、`categories.json`（65 类，部分带 price）、`tag_values.json`（标签价 + `default` 0.05）、`industries.json`（产业 supply/demand/biomeWeights）、`rarity.json`（来源轴档位）、`relics.json`（秘宝表）、`economy.json`（上表）、`blacklist.json`（tags / patterns / namespaces / allow / banRareUnpriced）。

存档 `data/tradepost_market.dat`（版本字段 `v = 4`）：

| 字段 | 内容 |
|---|---|
| `village_xp` | 村键 → 累计经验（村键形如 `minecraft:overworld/s29_91_90_79`） |
| `wallets` | 村键 → 银库余额 |
| `stock` / `stock_day` | 村键+物品 → 库存件数 / 最后结算日 |
| `world` / `world_day` | 物品 → 累计净件数 / 最后结算日 |
| `area` / `area_day` | 片区键 → 物品 → 净流入 |
| `gear` | 村键+日 → 当日已回收件数（按 key 分：`tradepost:gear` 与军需铺各行 code） |
| `seen_price` / `seen_days` | 村键+行码 → 上次单价（分）/ 日期（涨跌箭头用；同一天不覆盖） |

---

## 14. 实现事实清单（只陈述代码现状）

1. 收购价的实际生效值几乎总是 `ask × 0.75`：卖价公式在库存 50% 时对任何价位都高于封顶（见 §4.2 与 §11.1 的"卖/买"列恒为 0.75）。
2. `VillageLevel.sellMul(lv)`（`1 + 0.0175×(lv−1)`）与 `VillageLevel.buySlots(lv)`、`VillageLevel.restockMult(lv)` 在全仓没有调用点；等级对价格的影响来自 `walletMult`、`stockMult`、`buyMul`（只作用于学术铺/军需铺售价）与买页行数。
3. `Rarity.mult` 只对"非手写价、非配方反推价"的基础价生效（`mc/Bridge.java:539/543`）。
4. `worldMinBase = 1.0` 以下的货不记全球饱和（`mc/Bridge.java:821`、`mc/Bridge.java:3044`）。
5. 驿站（`station = true`）的影响：货架预算 24、库存上限 ×2、每行成交次数 64、菜位 +1、杂货位 +2、秘宝次数 ×2。
6. 买页每行的"件数"随等级变（`8 + 6×(lv−1)`），因此同一件货的"每份价钱"会随等级变化，而"每件单价"不变（箭头按每件单价比较，`mc/Bridge.java:3671`）。
7. 卖页的行数由背包内容决定（最多 40 行），村子的 `ind.demand()` 只影响溢价与"特收"前置，不影响行数。
8. 装备回收行不进本村库存池（`MarketMerchant.add` 里 `gearRow` 分支只查银库），普通货与附魔书则受"库存余量 + 银库"双重限制。
