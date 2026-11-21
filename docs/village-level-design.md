# 村庄等级养成系统 · 设计稿 v2

> 目标（按 m07488 修正）：等级养成的**主要产出不是"更好的价格"，而是"资源的另一种获取方式"**。
> 典型场景：不想手挖下界合金 → 找一个矿业村 → 在这儿长期买卖把村养到 L4/L5 → 村里开始稳定供下界合金。
> 配套要求：**没有每日等级上限**（允许刷），但**新货要"在途"一段时间才到货**（不许升级即到）。

## 0. 物品稀有度：有，而且我们已经在用

原版每个物品自带 `minecraft:rarity` 组件，四档：`common / uncommon / rare / epic`。
本 mod 已经在 `mc/Bridge.java` 里读了它：

```java
static String rarityOf(String id)        // class_1792 → 组件 class_9334.field_50073
    common | uncommon | rare | epic
static double rarityFactor(String id)    // 1.0 / 2.0 / 5.0 / 15.0  ← 目前只用来算基价
```

现状：稀有度**只参与定价**，不参与"能不能上架"。货架上出现什么，靠产业 supply/demand 表 + 基础价 + 哈希。
且 `banRareUnpriced` 会把"稀有但没标签价"的货直接不收（`/tradepost why` 会打印原因）。

**v2 改法**：把稀有度直接当"货档"，比我原先按基础价分档更贴直觉，也更好解释：

| 档 | 原版键 | 价格系数（已有） | 上架等级 | 说明 |
|---|---|---|---|---|
| 普通 | common | ×1 | L1 | 现在的货架全是这档 |
| 罕见 | uncommon | ×2 | L2 | 铁具、附魔瓶、模组中级件 |
| 稀有 | rare | ×5 | L3 | 钻石、附魔书、龙息 |
| 史诗 | epic | ×15 | L4（部分 L5） | 下界合金、鞘翅级、模组神话件 |

## 1. 经验：无每日上限，允许刷

```
xp = 净绿宝石额 × 档位权重 × 方向系数
档位权重：common 0.4 / uncommon 1.0 / rare 2.5 / epic 6.0
方向系数：玩家买货 1.0（村里赚到钱）/ 玩家卖货 0.6
不设每日上限、不设同物品衰减 —— 想刷就刷
```

**为什么允许刷还不崩**：经验按"净绿宝石额"算，而"买进再卖回同村"必然亏价差（买价 ≈ 卖价 × 1.5 上下），
而且每笔都会推动该村库存与世界饱和（`worldSellK=2000`），越刷这个村价格越差。
所以"刷等级"本质是**用绿宝石买等级**：想快就花钱，想省就真做贸易。这正是我们要的经济出口，不需要额外的每日闸门。

等级曲线（累计经验，可按实测调）：

| Lv | 名称 | 累计经验 | 大致成本（按刷） |
|---|---|---|---|
| 1 | 露天集市 | 0 | — |
| 2 | 固定市集 | 400 | ≈ 1000 绿 |
| 3 | 商栈 | 1500 | ≈ 4000 绿 |
| 4 | 商会 | 5000 | ≈ 1.3 万绿 |
| 5 | 商都 | 14000 | ≈ 3.5 万绿 |

（按刷算：每次买卖往返亏约 35%，故实际花费 ≈ xp ÷ 0.4 × 1.5。）

## 2. 新货"在途"：升级不等于到货

用户要求的时间延迟，做成**订单在途**而不是随机延迟，玩家能看见、能等：

- 升级时立刻解锁"新货位/新货种"，但每件货有 `arriveDay = 升级日 + delay`：
  - uncommon 1 天、rare 2 天、epic 3 天；村里有驿站各 −1 天（最低 1 天）
- 在途期间货架上显示**空木牌 + 倒计时**：鼠标悬停「订货在途：2 天后到（稀有 · 钻石）」。
- 到货后进入正常货架，开始按库存/限量供货。
- 降级不存在（只升不降），所以 `arriveDay` 只需记一次。

这样"刷出下界合金"不是升级瞬间到手，而是**升级后还要等 3 天**，且要持续经营（见 §4 的每日限量）。

## 3. 等级带来的变化

| | L1 | L2 | L3 | L4 | L5 |
|---|---|---|---|---|---|
| 卖货位（村卖你买） | 4 | 5 | 6 | 7 | 8 |
| 收货位（你卖村买） | 2 | 2 | 3 | 4 | 4 |
| 可上架档位 | common | +uncommon | +rare | +epic | epic 增量 |
| 库存上限 | ×1.0 | ×1.1 | ×1.25 | ×1.4 | ×1.6 |
| 每日补货 | ×1.0 | ×1.0 | ×1.5 | ×1.5 | ×2.0 |
| 买价（你付） | ×1.00 | ×0.98 | ×0.97 | ×0.96 | ×0.94 |
| 卖价（你收） | ×1.00 | ×1.02 | ×1.03 | ×1.05 | ×1.07 |
| 新货在途 | — | 1 天 | 2 天 | 3 天 | 3 天 |

价格幅度仍压在 ±6%：**等级的价值在"能买到什么"，不在"便宜多少"**。

## 4. 稀有资源：让"刷等级"变成稳定获取途径

### 4.1 产业 × 稀有度 的产出表（示例，可扩）

| 产业 | L3（rare） | L4（epic） | L5（epic 增量） |
|---|---|---|---|
| 矿业 mining | 钻石、青金石块 | **下界合金碎片**（1 件/天） | **下界合金锭**（1 件/2 天）、远古残骸（1 件/周） |
| 图书/library | 附魔书（随机 1 本） | 高级附魔书 | 经验修补类 |
| 炼金/alchemy | 药水（II 级） | 喷溅/滞留 | 龙息、幻翼膜 |
| 酒馆/tavern | 优质酒（品级 4） | 精酿酒（品级 5） | 典藏酒（品级 6） |
| 农业/farm | 金胡萝卜 | 金苹果 | 附魔金苹果（1/周） |
| 渔猎/hunt | 海洋之心 | 三叉戟 | 鞘翅残片（可选，慎） |

（表放在 `config/tradepost/market/rarity_table.json`，玩家可改；没配的产业就不出 epic 货。）

### 4.2 稀有货的定价：不走 rarityFactor

若照现有 `rarityFactor`，epic 会被乘 15 再叠其他系数，价格会炸到几千绿、且世界饱和一动就崩。
所以稀有货用**固定底价表** `rare_base`（每个物品一个数，写进配置）：

```
下界合金碎片 40 绿 / 下界合金锭 160 绿 / 远古残骸 120 绿
钻石 24 绿（沿用现有 base）/ 附魔书 30~60 绿（按附魔等级）
```

- 村卖你：`底价 × (1 + 0.05 × (等级−解锁等级)) × 村癖/每日行情（小幅度）`，不再乘 rarity。
- 你卖村：`底价 × 0.55`，且**限量收购**（每天最多 2 件）→ 堵住"村里买、卖回村里"的套利。
- 稀有货**不计入世界饱和**（或不参与 decompose 折算），否则一个满级村的钻石价会被刷崩。

### 4.3 限量与补货（"稳定"的定义）

```
每日到货：epic 1 件（L5 提到 2 件），rare 2~3 件
库存上限：epic 3 件，rare 6 件
收购上限：玩家卖给村里的稀有货，每天 2 件
```

→ 满级矿业村 ≈ 1 件下界合金碎片/天。要凑 4 个碎片做锭，得连续来 4 天（或等 L5 的锭，2 天 1 件）。
这就是"稳定但不白送"：不需要下矿，但需要持续经营与跑动。

### 4.4 门槛：不是所有村都能出

epic 货只在**产业匹配 + L4/L5 + 有驿站**的村出现。
驿站是玩家自己搭的（`tradepost:post_station` 战利品表箱子），所以"要下界合金？先给这村修个驿站"——
把资源获取和建造/跑商绑在一起。全图符合条件的村注定量很少，不会变成自动贩卖机。

## 5. 存档

`TradeStore` 新增：

```java
Map<String, Double>  xp;          // vkey → 累计经验
Map<String, Long>    levelUpDay;  // vkey → 最近一次升级的天（算在途）
Map<String, Integer> level;       // vkey → 等级（也可由 xp 推，存下来省算）
Map<String, Map<String, Long>> arriving;  // vkey → (itemId → 到货天)
```

NBT：`"vxp"` / `"vlv"` / `"vlv_day"` / `"varrive"`；`fromNbt` 缺键即空，老存档零迁移。

记账点：`Bridge.buyFromGui` / `sellFromGui` / `sellFromInv` **结算成功之后**，
`store.addXp(vkey, itemId, emeralds, buying)`；升级时写 `levelUpDay` 并为新解锁的货生成 `arriving`。

## 6. 界面（仍然零自绘窗口）

1. 原版绿经验条（`x+136,y+16,102×5`）画当前等级进度；
2. 顶部条：`矿业 · 集市 Lv.4`；满级 `商都 Lv.5 ★`；
3. 在途货位：空木牌，悬停显示「在途 · 稀有 · 下界合金碎片 · 2 天后到」；
4. 升级提示：聊天栏一行 + 顶部条闪一下（复用 `sayHint`）；
5. 悬停绿条：「距 商都 Lv.5 还差 3200 点」。

## 7. 防滥用（只在必要处设闸）

- 经验按净额，不按件数；买卖往返必亏价差 → 刷等级 = 烧绿宝石。
- 稀有货**收购限量 + 收购价 0.55×**：套利不成立。
- 稀有货不进世界饱和；因此也不会被"刷货"打崩价格。
- 已满级村继续交易只涨"经营度"（可选，用于大宗订单额度），不再涨等级。

## 8. 落地顺序

1. 存档 + 记账 + root json 加 `lv/lvname/xp/xpnext`（无界面变化）
2. `VillageMarket.offers` 加 `level` 入参：档位过滤、货位增量、追加式选货（老条目哈希不变）
3. 界面：绿条 → 村庄进度、顶部等级、在途货位与倒计时
4. 稀有资源表：`rarity_table.json` + `rare_base.json` + 限量/到货逻辑
5. 上等货（同一件货分品质，接 `Quality` + 酒 `brew_level`）—— 可选，最后做

## 9. 仍需你拍板

1. 稀有资源产出表按上面这版（矿业→下界合金，图书→附魔书…）？要不要加/删产业？
2. epic 是否强制要求"有驿站"？还是只要产业+等级？（驿站是玩家手搭的，门槛更高）
3. 下界合金的限量：碎片 1/天、锭 1/2 天、残骸 1/周 —— 这个节奏够不够"稳定"，会不会太慢？
4. 满级后继续交易要不要给"大宗订单额度"（每天可下 1 单稀有货，次日到）？


---

# 附录 A · 稀有度到底怎么定（522 个模组、1.6 万件物品）

## A.0 现状与问题

- 本 mod 已经扫描出 **16,224 件物品**，每件都有 base（基础价）：
  手写价 94 / 标签价 923 / 配方推导 9,238 / 兜底 5,081；另有 888 件被 Banlist 排除。
- 原版 `minecraft:rarity` 组件**只是模组作者自己填的**：绝大多数模组物品都填 `common`（哪怕它要 8 个下界合金），
  所以**不能只靠组件**。反过来，原版原件的 rare/epic 是准的（附魔金苹果、下界之星）。

结论：稀有度必须是"算出来的"，不是"读出来的"。四层判定，从高到低：

## A.1 四层判定流水线

```
① 人工表  config/tradepost/market/rarity.json      （最高优先，逐条覆盖）
② 原版组件 minecraft:rarity                        （原件准，模组件多数为 common）
③ 价格分位 全库 base 排序取分位                     （主力，覆盖 1.6 万件）
④ 修正规则 堆叠/来源/类型                          （把"贵但不稀有"和"便宜但珍贵"掰正）
```

### ① 人工表（可覆盖一切）
```json
{ "minecraft:netherite_ingot": "epic",
  "minecraft:diamond": "rare",
  "immersive_aircraft:biplane": "epic",
  "niftycarts:wagon": "uncommon" }
```
只写"必须掰正"的几百条，别写全表。

### ② 原版组件
`rarityOf(id)` 已有（读 `class_9334.field_50073`）。规则：
- 组件是 `rare/epic` → **直接采信**（原版作者不会乱标）
- 组件是 `common` 且 modid 不是 `minecraft` → **不算数**，往下走 ③（这正是现在的问题所在）

### ③ 价格分位（主力）
把 16,224 件物品的 base 取对数后排序，按分位切四档（长尾分布，绝不能用线性均值）：

```
P0  – P50   → common     （一半的物品，日常货）
P50– P85   → uncommon   （铁、玻璃、面包那档）
P85– P96   → rare       （钻石、附魔书、龙息）
P96– P100  → epic       （下界合金、鞘翅级、模组神话件）
```

具体阈值由 `/tradepost rarity build` 跑一次生成，写进 `rarity_cache.json`（含每件物品的 base 与命中哪一层），
以后开界面不再重算。示例量级（需实测校准）：common < 4 绿、uncommon 4–14、rare 14–45、epic > 45。

### ④ 修正规则（把直觉掰正）
```
不可堆叠（maxCount=1）的装备/工具   → 档位 +1（贵在"一件"，不是"一堆"）
食物/药水/酒                        → 最多 uncommon（消耗品不该占 epic 位）
原版组件已经是 uncommon/rare/epic    → 至少保持该档，不许被价格分位降级
战利品/结构专属（有 loot table 引用）→ +1（拿不到的东西才稀罕）
配方可量产（配方树能追到 3 层内）    → 最多 rare
被 Banlist 排除                      → 不上架，不参与分位统计
```

### 审核机制（重要）
`/tradepost rarity audit` 导出 `rarity_report.csv`：`id, 中文名, base, 组件档, 分位档, 最终档, 命中层`。
人工抽查前 200 条 + 所有 epic，不对的就写进 ① 人工表。**先审后上线**，别让"泥土卖 3 绿"或"下界合金 4 绿"这种错误进经济。

---

# 附录 B · 载具商品化（模组载具怎么上货架）

## B.0 先看清包里有什么（实测 jar）

| 模组 | 物品形态 | 可卖的货 | 建议档 |
|---|---|---|---|
| `niftycarts` | 82 个 item model | 手推车、马车、犁、补给车、收割车 | uncommon |
| `smallships` | 66 个 item model | 划艇、帆船、战船（按木材分） | uncommon/rare |
| `immersive_aircraft` | 27 个 item model | 双翼机、飞艇、货艇、锅炉、引擎 | rare/epic |
| `automobility` | 22 个 item model | 车架、引擎、轮子、装配台（整车需装配） | rare |
| `TerracartReloaded` | 有 | 陆行车 | uncommon |
| `obsidianboat` | 有 | 黑曜石船 | uncommon |
| `caravans` | **0 个 item model** | 无物品形态 → 不做商品（可做世界事件/商队 NPC） | — |
| `shippy_ships` | 4 个（造船台） | 卖"造船台/图纸"而不是船 | rare |

**只卖有物品形态的载具**：能进背包、能放置、能被原版容器同步。
没有物品形态的（caravans）不要硬造，否则要自己实现实体生成与存档，风险高。

## B.1 上架规则

```
新增货类 vehicle，独立配置 config/tradepost/market/vehicles.json：
  { "item": "niftycarts:oak_wagon", "tier": "uncommon", "base": 55,
    "industry": ["transport", "farm"], "level": 2, "stock": 2, "restockDays": 5 }
```

- **产业门槛**：默认要求该村产业是 `transport`（新增"运输/车马"产业）或 `farm`（农用车）；
  矿业村卖矿车、渔村卖船。
- **驿站门槛**：L3 起载具位 = 1，L4 = 2，L5 = 3；**没有驿站的村不卖载具**（驿站是玩家手搭的，正好当门槛）。
- **等级门槛**：uncommon L2+、rare L3+、epic L5（飞艇/飞机）。
- **在途**：沿用 §2 的到货延迟（rare 2 天、epic 3 天）。
- **限量**：每村每 `restockDays` 天补 `stock` 台（epic 5 天 1 台）。

## B.2 定价

载具**不走 rarityFactor**（否则 ×15 直接炸），走 vehicles.json 里的 `base`，再叠现有小幅系数：
```
村卖你 = base × (1 + 0.05 × (等级 − 解锁等级)) × 村癖 × 每日行情
你卖村 = base × 0.55，且每天最多收 1 台（防套利）
```
参考价（按包里配方材料粗算，上线前用 `/tradepost why` 核对）：
手推车 25–45 绿 / 马车 60–90 / 划艇 30–50 / 帆船 90–140 / 车架+引擎套件 80–120 / 双翼机 260–360 / 飞艇 400–600。

## B.3 为什么这样不破坏玩法

- 载具是**交通能力**，直接服务于"跑商"本身：买了车 = 跑得更远、商路加成更多，是正反馈不是跳过玩法。
- 但飞行载具会让"距离"失去意义，所以：只 L5、必须驿站、5 天 1 台、贵（400+ 绿）、且**燃料/引擎单独卖**（immersive_aircraft 的 boiler/engine 也在货架上），逼玩家继续经营。
- automobility 的整车要在装配台组装：**只卖部件**（车架/引擎/轮子），不卖"组装好的车"，
  避免 NBT 复杂物品在原版容器里同步出问题。

## B.4 落地顺序（在等级系统之后）

1. 物品普查脚本：扫 522 个 jar 的 `assets/*/models/item/*.json` + 注册表，导出候选载具清单 → 人工筛 → `vehicles.json`
2. `rarity_table.json` 里给每个载具定档
3. offers 增加货类 `vehicle`（占 1 个货位，与普通货同池竞争）
4. 交付走现有"进背包 / 满了掉脚边"


---

# 附录 C · 能不能直接卖"成品车"（用户 m07519 追问）

## C.0 结论

**能，而且四个载具模组里三个天生就是成品。** 只有 `automobility` 需要塞一个组件，而模组自己就提供了成品构造 API。

## C.1 四个模组逐个体检（javap 字节码实测）

| 模组 | 物品类 | 放置行为 | 成品？ |
|---|---|---|---|
| `niftycarts` | `net.jmb19905.niftycarts.item.CartItem extends Item`，字段 `woodType`+`cartType`，`method_7836`=use | 直接生成对应马车实体 | ✅ 物品即成品 |
| `smallships` | `com.talhanation.smallships.world.item.ShipItem extends class_1749`(BoatItem) | `method_42296` 生成 `Ship` 子类实体 | ✅ 物品即成品（木材由物品决定） |
| `immersive_aircraft` | `immersive_aircraft.item.AircraftItem extends VehicleItem`，构造参数带 `AircraftConstructor` | `method_7836` 调 constructor 生成飞机实体 | ✅ 本体即成品；引擎/锅炉是**独立物品**，装到飞机背包的升级槽 |
| `automobility` | `io.github.foundationgames.automobility.item.AutomobileItem extends Item` | `method_7884`(useOn) 读组件 `AutomobilityItems.COMPONENT_AUTOMOBILE_DATA`（`class_9331<AutomobileData>`）→ 生成 `AutomobileEntity`，frame/wheel/engine 全从这份数据取 | ⚠️ 需要组件，但模组有现成 API |

## C.2 automobility 的成品构造（不用 mixin、不用手写 NBT）

```java
// AutomobileData 是 record：prefabName(Optional<Identifier>), frame, wheel, engine（都是 class_5321 注册表键）
AutomobileData data = AutomobileData.prefab(id, frameKey, wheelKey, engineKey);
ItemStack car = data.asStack();   // ← 成品车物品，直接进背包/货架
```

`asStack()` 字节码已核：`AUTOMOBILE.method_7854()` 建 stack → `method_57379(COMPONENT_AUTOMOBILE_DATA, this)` 写组件 → 若 `prefabName` 存在再写 `class_9334.field_50239`（物品名）为 `"prefab.<ns>.<path>"` 翻译键 → 返回。
所以卖出去的车，玩家拿到手**就是一个装好车架/轮子/引擎的成品**，放下即可开。

frame/wheel/engine 的键**不能硬编码**（datapack 驱动）：运行时从玩家世界的 `DynamicRegistryManager`（`world.method_30349()`）按 `AutomobileFrame.REGISTRY` / `AutomobileWheel.REGISTRY` / `AutomobileEngine.REGISTRY` 枚举，配置里只写"要哪一档"（例如 cheapest/normal/best），找不到就退回卖部件。

## C.3 飞机要不要"整机套件"

`immersive_aircraft` 的引擎（`engine`/`eco_engine`/`boiler`）是独立物品，得放进飞机的升级槽才飞得动。
两种卖法：
- **A 裸机**：只卖飞机本体，引擎另上架（便宜、诚实、玩家自己配）
- **B 整机套件**：一次成交给 2 个 stack（飞机 + 引擎），两个都进背包；价格 = 飞机 + 引擎 ×0.9

建议 B 只对 epic（双翼机/飞艇）做，且**必须背包有空位**，否则提示"背包放不下整机套件"（比掉地上两台更不容易丢）。

## C.4 成本与风险（诚实版）

- 组件同步：真容器（`class_1728`）同步 ItemStack 时组件一起过去，automobility 车身数据很小（3 个注册表键 + 可选名），**不是**那种几 KB 的 NBT，安全。
- 载具都是**不可堆叠**（count=1），货架上一格一辆，价格高，正好当"大件"。
- 客户端渲染：车身靠组件选模型，玩家客户端有同一个 mod，能正常显示。
- 万一某个整合包删了某个 frame/wheel/engine 注册项，`asStack()` 的实体生成会走 fallback（字节码里 `orElseGet` 有兜底），但我们取键时要先判存在。

## C.5 设计修订（按用户「先别搞驿站了」）

- **取消驿站门槛**：载具上架只看「产业 + 村庄等级」。
- 产业门槛：`transport`（车马行）/ `farm`（农用车）/ `fisher`（船）三类村才进货。
- 等级门槛：uncommon L2 / rare L3 / epic L5。
- 成品优先，部件兜底：优先上"整车"；若该模组成品构造失败，退化为卖车架/引擎/轮子。
- 限量：每村每 5 天 1 台 rare，10 天 1 台 epic。
- 定价仍走 `vehicles.json` 的固定 base（不走 rarityFactor），整车 = base，套件 = 本体 + 引擎×0.9。


---

# 附录 D · 成品车怎么拼、会不会缺零件（用户 m07547 追问）

## D.1 不是随机拼

**不做随机。** 每台成品车在 `vehicles.json` 里写死三件套（车架 / 轮子 / 引擎）+ 价格 + 解锁等级：

```json
{ "id": "automobility:automobile", "name": "工坊小货车",
  "frame": "automobility:standard_frame",
  "wheel": "automobility:standard_wheel",
  "engine": "automobility:copper_engine",
  "base": 120, "tier": "rare", "level": 3, "industry": ["transport"],
  "stock": 1, "restockDays": 5 }
```

选择是**确定性的**：同一个村、同一等级永远卖同一台（哈希只用来决定"这个村卖哪几台车型"，不用来决定零件组合）。
理由：随机零件会让玩家买到"看着像车、跑不起来"的东西，这比不卖更糟。

## D.2 缺零件会怎样（javap 实测，不是推测）

**automobility 有两条真实风险：**

1. **键不存在 → 空壳车。** `AutomobileItem.method_7884` 里对 frame/wheel/engine 各做 `registry.method_40264(key).map(holder -> holder).orElseGet(lambda)`，
   而三个 lambda 分别回退到 `AutomobileFrame.EMPTY` / `AutomobileWheel.EMPTY` / `AutomobileEngine.EMPTY`。
   EMPTY 不是"默认款式"，是**没有模型/没有属性的占位件** → 生成的车外观残缺、开不动。
2. **裸物品直接 NPE。** 物品不带 `COMPONENT_AUTOMOBILE_DATA` 时，`method_57824(...)` 返回 null，下一行 `data.frame()` 就空指针。
   所以 **绝不能上架 `automobility:automobile` 这个裸物品**，只能上 `AutomobileData.prefab(...).asStack()` 造出来的成品。

**结论：校验必须是硬门槛。** 上架前逐项检查三个键：
```java
var rm = world.method_30349();                       // DynamicRegistryManager
boolean ok = rm.method_30530(AutomobileFrame.REGISTRY).method_40264(frameKey).isPresent()
          && rm.method_30530(AutomobileWheel.REGISTRY).method_40264(wheelKey).isPresent()
          && rm.method_30530(AutomobileEngine.REGISTRY).method_40264(engineKey).isPresent();
if (!ok) { log.warn("车型 {} 的零件缺失，跳过上架", name); return; }
```
三个都在才进"可售清单"；缺一个就整台不卖（**不允许降级成部件混过去**，那正是缺件车的来源）。

**immersive_aircraft：** 本体能放，但 `EngineVehicle` 里 `ENGINE` 状态 + `fuel[]` 数组 + `cautions` 警告决定了它需要**引擎物品 + 燃料**才飞得起来。
所以飞机按"整机套件"卖：本体 + 引擎（+ 一叠煤当启动燃料），并在 tooltip 写清"需自备燃料"。

**smallships / niftycarts：** 物品自带木种/车型全部数据，放置即完整实体，不存在缺件。

## D.3 价格一致性（防套利）

部件和整车共用同一张价目：
```
整车价 = 部件价之和 × 1.15（组装费）   ← 保证买整车永远比买散件贵 15%
收车价 = 整车价 × 0.55，且每村每天最多收 1 台
```
否则玩家会"买整车 → 拆成部件卖回"套利（automobility 的拆车台能拆）。

## D.4 玩家看到什么

- 物品名：`asStack()` 会自动写 `prefab.<ns>.<path>` 物品名组件，显示模组自带的车型名；没有 lang 时退化成"成品车"。
- tooltip 三行：车架 / 轮子 / 引擎 的名称 + 一行"可直接驾驶"。
- 缺件车型永不出现（校验在服务端做，客户端拿不到假数据）。

---

# 附录 E · 稀有度分类的落地流程（用户 m07547 再问「怎么做」）

附录 A 讲的是"依据哪几层"，这里讲"具体怎么跑、产出什么、怎么审"。

## E.1 一次性普查（`/tradepost rarity build`，约 1–2 秒）

遍历 `Registries.ITEM`，对每件物品收集 8 个字段：

| 字段 | 来源 |
|---|---|
| id / 中文名 | 注册名 / `getName()` |
| base | 现有定价管线（手写 > 标签 > 配方 > 兜底） |
| maxCount | `ItemStack.getMaxCount()`（1 或 16/64） |
| 类型 | 食物/药水/工具/装备/方块/其他（按 item class + 标签判定） |
| 有配方 | 配方索引里是否存在 |
| 战利品来源 | 是否有 loot table 引用（构建时扫 data/*/loot_table） |
| 原版组件档 | `minecraft:rarity` |
| 是否被 ban | Banlist |

## E.2 自动分档（三步，顺序不能换）

```
step1  只对"可上架"物品算 log(base)，取分位：P50 / P85 / P96
step2  按分位切四档：common / uncommon / rare / epic
step3  依次套修正规则（后一条可覆盖前一条）：
        · 原版组件是 uncommon/rare/epic  → 至少该档（不许降）
        · maxCount == 1 的装备/工具      → +1 档
        · 食物 / 药水 / 种子 / 燃料       → 最高 uncommon
        · 战利品表专属                   → +1 档
        · 能靠配方量产的                 → 最高 rare
        · 人工表命中                     → 直接定档（最高优先）
```

预期量级（上线前必须用真实分布校准，这里只是数量级）：base < 1 绿约一半、1–4 绿约三成、4–15 绿约一成、>15 绿约几个百分点。
所以阈值大概率落在 **0.8 / 3 / 12 / 40** 附近，而不是"45 以上才是 epic"——钻石 24、下界合金锭 20 这种应该稳进 epic。

## E.3 产出两个文件

```
config/tradepost/market/rarity_cache.json   ← 运行时只读这个（id → tier），开界面不重算
config/tradepost/market/rarity.json         ← 人工覆盖表（手写，优先于 cache）
output/rarity_report.csv                    ← 审核用
  列：id, name, base, logbase, component_tier, pct_tier, final_tier, hit_layer, flags
```

## E.4 审核（这一步不做完不许上线）

1. 先把 **epic 全量**过一遍（预计几百条）：有没有"便宜但被标成 epic"的（多半是组件乱标）→ 写人工表降档。
2. 抽查 uncommon/rare 各 100 条：有没有"明显该稀有却埋在 common"的（模组里的神器）。
3. 反向抽查：base > 20 但 tier < rare 的，全部人工确认。
4. 改完 `/tradepost rarity build` 重跑 + `/tradepost reload`，界面立刻生效。

## E.5 稀有度在玩法里的四个用途（现在只有第 1 个）

| 用途 | 现状 | 改成 |
|---|---|---|
| 定价因子 `rarityFactor` | 1 / 2 / 5 / 15 | 压到 **1 / 1.6 / 2.4 / 3.5**（×15 会把 epic 炸成天价，且与"稀有货固定底价表"冲突） |
| 上架权重 | 未使用 | epic 只在 L4/L5 + 对应产业出现；货位按 tier 抽 |
| 村庄经验权重 | 设计值 0.4 / 1.0 / 2.5 / 6.0 | 按此实装 |
| 收购限量 | 未使用 | rare 每村每天收 2 件、epic 1 件 |

## E.6 运行时开销

cache 是一个 `Map<String,Integer>`（1.6 万条，约 1 MB 内存），启动时读一次；
`/tradepost reload` 重读；**不在每次开界面时遍历注册表**（这是之前卡死的教训之一）。


---

# 附录 F · 载具产业「车马行」（用户 m07575 定案）

## F.1 现在有多少产业

- **手写产业 16 个**（`data/tradepost/market/index.json` 的 industries 列表）：
  农耕 farming / 果园 orchard / 牧养 herding / 渔业 fishing / 林业 forestry / 采石 quarrying /
  冶炼 smelting / 煤炭 coal / 宝石 gems / 窑炉 kiln / 纺织 textile / 酿造 brewing /
  商贸 commerce / 学者 scholar / 军械 armory / 荒僻 backwater
- **自动产业**：`AutoIndustry` 从通用标签生成（`auto_*`），同一标签至少 6 件物品才建。
  本存档实际生成 **11 个**：auto_dusts / auto_dyes / auto_foods / auto_glass / auto_mushrooms /
  auto_ores / auto_planks / auto_seafood / auto_seeds / auto_storage_blocks / auto_wool。
- **合计 27 个**（不同存档/模组组合会变；`/tradepost info` 会打印真实数量）。
- 加车马行后：手写 17、本存档合计 **28**。

## F.2 车马行（id `vehicle`，display「车马行」）

```json
{
  "id": "vehicle",
  "display": "车马行",
  "supply": [
    "niftycarts:oak_wagon",
    "niftycarts:oak_hand_cart",
    "niftycarts:oak_supply_cart",
    "smallships:oak_rowboat",
    "smallships:oak_cog",
    "immersive_aircraft:biplane",
    "immersive_aircraft:engine",
    "immersive_aircraft:boiler",
    "automobility:automobile_frame",
    "automobility:automobile_wheel",
    "automobility:automobile_engine",
    "shippy_ships:ship_builder"
  ],
  "demand": [
    "#c:ingots/iron", "#c:ingots/copper", "#c:plates", "#c:gears", "#c:rods",
    "#minecraft:logs", "#c:leathers", "#c:coals", "minecraft:redstone", "#c:glass_blocks"
  ],
  "biome_weights": {
    "#c:is_plains": 3, "#c:is_desert": 2, "#c:is_savanna": 2, "#minecraft:is_overworld": 1
  }
}
```

- `supply` 里写的零件/工具按普通货走现有定价（配方成本）；
- **成品车不写在 supply 里**，写在 `vehicles.json`（要三件套 + 校验 + 等级门槛），由车马行在"高级货位"里上架。

## F.3 货物分三层

| 层 | 内容 | 上架条件 | 数量 |
|---|---|---|---|
| 零件 | 车架 / 轮子 / 引擎 / 船帆 / 火炮 / 造船台 | 产业=车马行 且 L1+ | 每天 2–4 件 |
| 小成品 | 手推车、划艇、黑曜石船、农用车 | L2+，在途 1 天 | 每 3 天 1 台 |
| 大成品 | 马车、帆船、战船、汽车、双翼机、飞艇 | L3+/L5（飞行），在途 2–3 天，每台都要过三件套校验 | 每 5–10 天 1 台 |

飞行器（epic）只在 L5 车马行出现；汽车成品（automobility）L3 起。

## F.4 与等级系统的接口

- 车马行村的 `VehicleStock` 挂在 `TradeStore`：`Map<String, Long> vehicleDay`（上次补货日）、`Map<String,Integer> vehicleStock`。
- 升级时**只解锁货位，不立刻给车**：成品车同样走"在途"（`arriveDay`），到货当天货架上是一块空木牌 + 悬停倒计时。
- 车马行的经验权重照常：卖车给村算 epic 档（权重 6.0 × 0.6），买整车算 6.0 × 1.0。

## F.5 平衡与防刷

- 每村同时最多 3 台成品车库存（L5），售出后按 `restockDays` 补。
- 收购成品车：每天 1 台、价 0.55×，且**拆成部件卖更亏**（部件收购价合计 ≈ 整车 0.5×），堵掉"买车拆件卖"套利。
- 成品车不进世界饱和（world_qty）——它本来就是限量货，再叠饱和会变成"买过一次就永远不值钱"。
- 车马行不卖燃料；燃料（煤/木炭）在煤炭村买，保持产业分工。


---

# 附录 G · 产业总表 v2（去重 + 排除装饰方块）

## G.1 整理原则

1. **一件货只有一个卖方产业**：用标签归属，不靠"两个村都卖、玩家自己比价"。
2. **装饰/建筑外观件一律不交易**：陶瓦、地毯、楼梯、台阶、墙、栅栏、门、活板门、玻璃板、彩色玻璃、书架、讲台、制图台、木桶、梯子、花盆、画、旗帜、告示牌等。
3. **工具/护甲本来就不交易**（blacklist 里已封 `#c:tools`/`#c:armors`），军械改成"军需"。
4. **自动产业去重**：`auto_*` 的标签成员有 ≥60% 已被手写产业供应 → 不生成、不加载。
5. **标签优先，具体 id 只补缺**；标签不存在就当没写（`readTag` 返回空表，不报错）。

## G.2 十九个产业（16 旧 + 灶房 + 酒肆 + 车马行）

| # | 产业 | 卖（supply） | 收（demand） | 出现地 |
|---|---|---|---|---|
| 1 | 农耕 | `#c:crops`、干草块、甘蔗 | 骨粉、`#c:seeds` | 平原 |
| 2 | 果园 | 苹果、`#c:foods/fruit`、甜浆果、发光浆果、蜂蜜瓶、蜜脾 | 骨粉、玻璃瓶、木棍 | 森林、平原 |
| 3 | 畜牧 | 皮革、`#c:foods/raw_meat`、`#minecraft:wool`、蛋、羽毛 | 小麦、干草块、盐 | 平原、草原 |
| 4 | 渔猎 | `#c:foods/raw_fish`、鱿鱼、海带 | 线、燧石、腐肉（鱼饵） | 河流、海滩、海洋 |
| 5 | 林业 | `#minecraft:logs`、`#minecraft:saplings` | 铁锭、煤 | 森林、针叶林 |
| 6 | 采石 | 圆石、石头、深板岩、沙砾、燧石、`#c:stones`（去掉砖类） | 木棍、火把、绳子 | 山地 |
| 7 | 冶炼 | `#c:ingots`、`#c:nuggets` | 煤、木炭、`#c:raw_materials`、`#c:ores` | 山地、恶地 |
| 8 | 采煤 | 煤、木炭、煤块 | 铁锭、铁轨、火把 | 山地 |
| 9 | 宝石 | `#c:gems`、紫水晶、青金石、绿宝石、石英、红石 | 铁锭、火把、木棍 | 山地 |
| 10 | 窑业 | 砖、玻璃 | 沙子、黏土球、煤 | 恶地、沙漠、海滩 |
| 11 | 纺织 | `#c:strings`（线/绳/布） | 染料、羊毛 | 平原、森林 |
| 12 | 酿造 | 药水、玻璃瓶、蜂蜜瓶 | 小麦、糖、浆果 | 森林、平原 |
| 13 | 商贸 | 纸、绿宝石块、指南针、时钟 | 金锭、绿宝石、墨囊 | 平原、海滩 |
| 14 | 学术 | 书、附魔书、书与笔 | 墨囊、纸、金锭、青金石 | 森林 |
| 15 | 军需（原军械） | 箭、盾、火药 | 铁锭、皮革、燧石、线、火药 | 山地、平原、草原 |
| 16 | 荒僻 | 木棍、燧石、腐肉、杂物 | 面包、铁锭、小麦、煤、皮革 | 到处 |
| 17 | 灶房 | `@cuisine` 里**非酒**的菜 | `#c:crops`、生肉、生鱼、`#c:mushrooms` | 平原、森林 |
| 18 | 酒肆 | `@cuisine` 里**酒类**（`Cuisine.isDrink`） | 作物、水果、蜂蜜、玻璃瓶 | 平原、森林 |
| 19 | 车马行 | 成品车 + 车架/轮子/引擎/船帆/造船台 | 铁锭、铜锭、`#c:plates`、`#c:gears`、`#c:rods`、原木、皮革、煤、红石、玻璃 | 平原、沙漠、草原 |

自动产业保留（不与上表冲突的）：矿石、金属粉、材料块、木板、食材、种子、菌菇、染料、皮革。去掉：水产（并入渔猎）、玻璃（并入窑业）、羊毛（并入畜牧/纺织）。

## G.3 装饰方块排除（三层）

**① 配置层** `blacklist.json` 增关键词：
```
terracotta, glazed, concrete, stained_glass, _pane, _stairs, _slab, _wall,
_fence, _fence_gate, _door, _trapdoor, _carpet, _banner, _sign, _button,
_pressure_plate, bookshelf, lectern, cartography_table, barrel, ladder,
flower_pot, chiseled, polished, cut_, _tiles, shingles, mosaic, pillar,
column, lamp, chain, bars, grate, trellis, pergola, awning, planter,
bench, shelf, cabinet, wardrobe, nightstand
```
**② 供应层**：把 `farming/kiln/quarrying/textile/scholar/commerce/orchard` 里现有的装饰件（陶瓦、石砖、地毯、书架、讲台、制图台、木桶、梯子、地图）从 supply 里删掉。

**③ 代码层兜底**：`Bridge.tradeableItems` 里加后缀过滤（`_stairs/_slab/_wall/_fence/_door/_trapdoor/_pane/_carpet/_banner/_sign/_button/_pressure_plate`），
自动产业展开标签时同样过滤，避免整合包新增模组又把装饰块塞进来。

**保留的"材料性方块"**：原木、木板、羊毛、玻璃、砖、石头/深板岩（原石）、铁块等 9 合 1 块——它们是原料或中间件，不是装饰。

## G.4 老存档怎么迁移

产业配置是"首次运行复制到 `config/tradepost/market/`"的，改 jar 里的默认值不会动老存档。迁移步骤：
1. 备份 `config/tradepost/market/industries/` 整个目录；
2. 删掉该目录（含 `auto_*.json`）；
3. 重进游戏或 `/tradepost reload`，会按新默认值重新生成（自动产业也重跑，去重规则生效）；
4. 如果改过 `values.json`/`economy.json`，它们不受影响（只删 industries 目录）。
