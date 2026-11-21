# 接手 / 扩展这个模组

写给"不是我写的、但要改它"的人。按下面的顺序读，20 分钟能把该动的地方找全。

---

## 0. 想改什么 → 去哪

| 想做的事 | 去哪儿 | 要不要写代码 |
|---|---|---|
| 某个物品值多少钱 | `data/tradepost/market/values.json`（或 `tag_values.json` 按标签批量给） | 不用 |
| 某种物品不收 / 不进价目表 | `config/tradepost/market/blacklist.json` | 不用 |
| 加减一个秘宝 | `data/tradepost/market/relics/*.json` | 不用 |
| 加减一个村落产业（比如客栈） | `data/tradepost/market/industries/*.json` | 不用 |
| 买卖系数、行情波动、区域大小 | `config/tradepost/market/economy.json` | 不用 |
| 菜 / 酒的溢价 | `config/tradepost/market/cuisine.json` | 不用 |
| 稀有度的世界生成密度 | `data/tradepost/market/rarity.json` | 不用 |
| 换一种方式打开集市界面 | `TradepostApi.openBoard(player)` | 十行以内 |
| 加"每天刷新的一排货"（像装备铺/书铺/药铺） | 照 `market/GearShop.java` 抄一个 + 4 处接线 | 要 |
| 改界面长什么样 | `client/MarketScreen.java` | 要 |
| 改买卖怎么记账 / 村庄怎么认 | `mc/Bridge.java`（4098 行，见 §9） | 要 |

---

## 1. 数据放哪、怎么生效

两套来源，**外部目录优先，目录里有什么就读什么**（不靠 index.json 清单）：

```
<游戏目录>/config/tradepost/market/          ← 运行时可改，改完 /tradepost reload
├── values.json          物品底价（id → 绿宝石）
├── tag_values.json      标签底价（#c:ingots → 每件价）
├── categories.json      大类归类
├── rarity.json          稀有度
├── index.json           jar 内置清单（外部目录存在时基本用不到）
├── industries/*.json    村落产业（军需/纺织/灶房…）
├── relics/*.json        秘宝
├── economy.json         经济系数（买/卖/需求/自产/村癖/行情/日回/区域）
├── roles.json           用途大类（@weapon → 武器），供产业 supply/demand 引用
├── cuisine.json         食物与酒
└── blacklist.json       不收的东西（id / 标签 / 关键词 / 模组名）
```

读代码在 `market/MarketData.java`：`MarketData.load(dir)` —— dir 存在就用 dir，否则回落到 jar 内置的 `src/main/resources/data/tradepost/market/`。

**改完立刻生效**：游戏里敲

```
/tradepost reload
```

它会重读数据 + 清掉价目表/驿站/村庄缓存，并回一行"已重载：…（外部目录）/（jar 内置）"。
改 json 语法写错不会崩，坏掉的那一个文件被跳过（日志里能看到），其余照常。

---

## 2. 配方一：给一个物品定价

`config/tradepost/market/values.json`：

```json
{
  "minecraft:diamond": 45,
  "minecraft:netherite_ingot": 260,
  "mymod:magic_dust": 12
}
```

- 单位是"一价绿宝石"（可以是小数，0.1875 表示 16 件 = 3 颗）。
- 没写的东西不一定没价：`RecipePricer` 会顺着**合成表**反推（材料价 ÷ 产出数 + 一点加工费），所以新模组的物品经常"自己就有价"。
- 想确认某个 id 到底算出多少：`/tradepost cat <关键词>` 或 `/tradepost goods <id>`。

### 2.1 给一整类货定默认价（`categories.json` 的 `price`）

一件货的价按这个顺序取，**先命中的赢**：

1. `values.json` 手写价
2. `tag_values.json` 标签价（× 食物倍数）
3. 合成表反推的配方价
4. 稀有货（rare/epic 且无价）→ 不收
5. **类别默认价**（`categories.json` 里那个类别的 `price`）
6. 兜底 `default`（默认 0.05）

第 5 步就是给"归类清楚、但没人给价"的货用的：模组的树苗、花、树叶、石材、砖，过去全掉到 0.05（卖掉几乎等于白送）。现在只要给类别写一个 `price`，整类就有价：

```json
{
  "sapling": { "display": "树苗", "tags": ["#minecraft:saplings"], "names": ["sapling$"], "price": 0.5 },
  "flower":  { "display": "花卉", "tags": ["#minecraft:flowers"],  "names": ["flower$"],  "price": 0.3 }
}
```

- 只影响"上面 1~4 步都没价"的货：手写价、标签价、配方价、稀有拦截都不会被它改。
- 不写 `price` = 该类继续走兜底 0.05（老行为）。
- `misc`（杂项）类不参与：那是"谁都不认领"的垃圾桶，价格应该由具体类别或手写价决定。
- 想知道现在有多少货是靠类别价、多少还在吃 0.05：`/tradepost audit`（看"价格来源"那行）或 `/tradepost audit csv`（CSV 多了 `src` 列）。

---

## 3. 配方二：加一个秘宝

秘宝 = "这个村只收实物、拿一件稀有货跟你换"的那种交易，出现在买页上方单独一栏。

文件：`data/tradepost/market/relics/<随便起名>.json`

```json
{
  "id": "ancient_debris",
  "item": "minecraft:ancient_debris",
  "count": 1,
  "cost": [
    { "tag": "#c:ingots", "value": 20 },
    { "item": "minecraft:obsidian", "value": 10 }
  ],
  "weight": 6,
  "value_multiplier": 18
}
```

| 字段 | 含义 |
|---|---|
| `id` | 内部名，随便起，重载后日志里能看到 |
| `item` | 换给玩家的东西 |
| `count` | 一次给几件（配 `value_multiplier` 用不到的话写 1 就行） |
| `cost` | 最多两项，正好用满原版两个成本槽 |
| `cost[].item` / `cost[].tag` | 二选一：要具体物品，或要"标签里任意一种" |
| `cost[].count` | 写死件数（换整合包要重调） |
| `cost[].value` | **推荐**：写"值多少钱"，运行时按这个包里该物品的实际底价折算件数，换整合包不用改 |
| `weight` | 抽中的权重（相对值） |
| `value_multiplier` | 这件东西值多少倍等价物，影响定价与"划不划算" |

规则（代码在 `market/VillageMarket.relicOffer`）：

- 约 **25%** 的村有秘宝（`RELIC_CHANCE = 25`，按 100 取余），每 **7 天**换一批（`RELIC_ROTATION_DAYS = 7`）。
- 成本里的物品这个整合包里不存在 → 这条秘宝自动跳过，不报错。
- 想关掉全部秘宝：把 `relics/` 清空或删掉。

验证：`/tradepost reload` 之后面板买页上方应该出现"秘宝"一栏（没有就换个村子/换一天，概率问题）。

---

## 4. 配方三：加一个村落产业（以"客栈"为例）

文件：`data/tradepost/market/industries/inn.json`

```json
{
  "id": "inn",
  "display": "客栈",
  "supply": ["@food", "@drink"],
  "demand": ["@grain", "@meat", "@fiber"],
  "biome_weights": {
    "#c:is_plains": 3,
    "default": 1
  },
  "prosperity_bias": 1
}
```

| 字段 | 含义 |
|---|---|
| `id` | 产业 id（存档里按它记，改了等于换产业） |
| `display` | 面板信息条上显示的中文名 |
| `supply` | 这个村**卖**什么（自产，便宜）——写物品 id 或 `@大类` |
| `demand` | 这个村**收**什么（缺货，给价高） |
| `biome_weights` | 什么样的群系更容易出这个产业，`default` 兜底；权重越大越常见 |
| `prosperity_bias` | 富庶程度偏移（正数=更容易变富） |

**只加文件就够，不用改代码**：产业列表是"目录里有什么读什么"，信息条、品类、买卖价都会跟着走。
`@food` / `@weapon` 这些大类在 `roles.json` 里定义（关键字 → 大类），想加新大类去那儿加。

---

## 5. 配方四：用别的方式打开集市

`dev.guoke.tradepost.api.TradepostApi`：

```java
import dev.guoke.tradepost.api.TradepostApi;
import net.minecraft.server.network.ServerPlayerEntity;

// 服务端调用（isClient 为 false 那边）
if (TradepostApi.openBoard(sp)) {
    // true = 打开了；false = 玩家不在村里，什么都没发生
}
```

配套的两个只读入口，做行情牌/告示板很有用：

```java
String vk   = TradepostApi.villageOf(sp);        // 村 id，野外 null
String json = TradepostApi.boardJson(sp);        // 这个村此刻的完整价目表（就是界面吃的那份）
double p    = TradepostApi.basePrice("minecraft:diamond");
```

注意：**必须在服务端调**。打开的是"这个村此刻"的集市，和右键村民看到的是同一份数据，
所以别的模组开店也不会出现两套价。

---

## 6. 配方五：加一种"每天刷新的一排货"

现有三种（照着抄就行）：`market/GearShop.java`（军需铺成品装备）、`BookShop.java`（附魔书）、`PotionShop.java`（药水）。
它们的共同形状：

```java
public record Thing(String ..., int price, int quota) {
    public String code() { return "前缀:..."; }   // 面板回传的购买码
}
public static List<Thing> list(String vkey, int level, long day) { ... }  // 纯逻辑，不碰 Minecraft 类型
```

接线四处（少一处就会"看得见买不到"或"买了不记账"）：

1. **货架**：`Bridge.boardJson` 买页那段，`for (Thing t : ThingShop.list(c.vkey, vlv, c.day))` 造行，
   `id` 填 `t.code()`、`base` 填真实物品 id、`pay` 填价、`stock` 填 `quota - store.gearSold(...)`。
   `stock <= 0` 的行保留但打 `sold: true`（界面画红叉，玩家知道今天卖完了）。
2. **成栈**：`market/Goods.row(...)` 按行字段造真实物品栈（带附魔/药水效果的那种）。
3. **能不能买**：`MarketMerchant.add()` 里 `boolean special = code.startsWith("gear:")` 这类前缀判断 —— 跳过库存上限检查、只夹银库。
4. **记账**：`Bridge.marketTradeDone` 的 `tab == 0` 分支，前缀命中的走 `store.gearTake(vkey, day, code, n)` 扣当日额度；
   没命中的才进普通库存池 `store.add(...)`。

额度是按"村 + 日期 + 购买码"记的，所以同一个码明天自动回满，不用自己写重置。

---

## 7. 面板 JSON（界面吃的那份）

`String json = Bridge.boardJson(player)` 返回的根对象：

| 字段 | 含义 |
|---|---|
| `title` / `ind` / `lv` / `lvname` | 村名 / 产业 / 等级 / 等级名 |
| `xp` / `xpnext` / `xpr` | 经验、下一级门槛、本级进度 0..1 |
| `wallet` | 村银库余钱（收购付得出多少就看它） |
| `sub` | 一行小字摘要 |
| `buy` / `sell` / `relics` / `rumors` | 四组行 |

行（`sell` 为例）：`id` `base` `name` `key` `count`（一次几件）`price`（整行的钱）`pay` `stock` `sold` `gear` `sig` `ench` `elvl`。
买页的行额外有 `base`（真实物品 id）和 `costs`（秘宝的两项成本）。

界面上"钱"永远显示成**一摞绿宝石，数量就是这个价**（`class_1914` 的第一成本格/成品格），
真正的金额塞在报价的 xp 通道里（负数），见 `MarketMerchant.offer` 与 `mixin/OfferClampMixin`。

---

## 8. 调平衡的地方

| 想调 | 位置 | 现值 |
|---|---|---|
| 收购价相对买价 | `Bridge.LOCAL_SPREAD` | 0.75 |
| 装备回收折扣 | `Bridge.GEAR_BUYBACK` | 0.55 |
| 每级附魔加价 | `Bridge.GEAR_ENCH` | 0.08 |
| 装备耐久下限 | `Bridge.GEAR_DUR_FLOOR` | 0.35 |
| 每村每天收几件装备 | `Bridge.GEAR_PER_DAY` | 2 |
| 附魔书 I 级基准价 | `market/BookShop.POOL` | 保护 5 / 耐久 6 / 节肢 4 / 时运 14 / 修补 18 |
| 书等级倍率 | `BookShop.LMULT` | {1.0, 1.0, 1.8, 3.0} |
| 药水形态倍率 | `market/PotionShop.FORMS` | 普通 1.0 / 喷溅 1.6 / 箭 1.4 / 滞留 2.2 |
| 村庄等级门槛与经验 | `market/VillageLevel` | 5 级 |
| 买卖全局系数、行情幅度 | `config/.../economy.json` | 见文件注释 |

---

## 9. 代码结构

```
dev.guoke.tradepost
├── Tradepost.java            入口：命令注册、数据目录、各种 reload
├── api/TradepostApi.java     对外只读 API + openBoard（别人从这里接）
├── client/TradepostClient.java  收服务端包 → 开界面
├── client/MarketScreen.java  集市界面：页签、份数加减、信息条、标题
├── market/                   纯逻辑，不碰 Minecraft 类型（好测、好抄）
│   ├── MarketData            json 读取
│   ├── VillageMarket         这个村此刻有什么货（秘宝、风声）
│   ├── Pricing/Lot/Goods     单价 → 原版交易格式
│   ├── GearShop/BookShop/PotionShop   三张"每日货架"
│   ├── Pay                  收钱找零、摆钱、拆分大小写
│   └── VillageLevel/Economy/Catalog/Stock/... 系数与库存
├── mc/Bridge.java            4098 行：村庄识别、价目表拼装、成交记账（见下）
└── mixin/                    四处改动原版商人
```

`Bridge.java` 的三个大块（改之前先看注释块标题）：

- 村庄识别 / 缓存 / 命令（`villageKey` `ctx` `clearVillageCache` …）
- 价目表（`basePrice` `sellUnit` `boardJson`）
- 成交记账（`marketTradeDone`，买/卖两个分支，`gear:` `book:` `potion:` 前缀）

> 这一坨是历史原因长成的上帝类，拆它要连带改 4 个 mixin 的调用点，没有回归测试。
> 建议单独一轮做，用三笔账验证：买一件、卖一件、卖一件带附魔的装备。

---

## 10. 升级 Minecraft 时要检查的 5 个 mixin

| mixin | 改的原版类 | 方法 | 干什么 | 失效的表现 |
|---|---|---|---|---|
| `MerchantInventoryMixin` | `class_1725`（商人库存） | `method_7645` | 按价值点亮成品格；卖页把真钱摆进结果格 | 天平摆好了但成品格不亮 / 卖页看不到钱 |
| `MerchantResultSlotMixin` | `class_1727`（结果槽） | `method_7667` | 成交：收钱、找零、扣货、补发 | 收钱不给货，或货不扣 |
| `MerchantScreenHandlerMixin` | `class_1728`（交易容器） | `method_20215` `method_7595` `method_20595` | 点行自动摆钱；关界面抢救；绕开"商人必须是实体" | 点行没反应 / shift 点击报 ClassCastException |
| `OfferClampMixin` | `class_1914`（报价） | `method_19272` | 让第一成本格不被夹到 64 件 | 几百块的东西只显示 64 |

`tradepost.mixins.json` 里 `"required": false`：mixin 写错只会 log 一条 ERROR 然后**退化成原版行为**，不会启动崩。
升级后先在日志里搜 `InvalidMixinException` / `tradepost` 的 WARN。

自查方法（不用开 IDE）：

```bash
JAVAP=~/.jdks/jdk-21.0.12.1+1/bin/javap
JAR="/path/to/versions/<版本>/.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar"
$JAVAP -cp "$JAR" -p net.minecraft.class_1728 | grep -n method_
```

名字看不懂 → 见 `docs/INTERMEDIARY.md`（`class_1799` = ItemStack 之类）。

---

## 11. 踩过的坑（都是花时间换来的）

1. `class_1799.method_7934(n)` 是**减** n，`method_7939(n)` 才是设成 n，`method_7933(n)` 是加。写错会"扣了没扣"。
2. 空 ItemStack 不能当报价的成本：界面会画成"空气"并显示一堆属性。要么真物品，要么别成行。
3. `class_1914.method_19272()` 会把第一成本的数量夹到 64 —— 想显示大数字必须绕过（`OfferClampMixin`）。
4. 给 `ItemStack` 设超过 64 的数量：先挂 `max_stack_size` 组件再 `method_7939`，顺序反了会被夹。
5. 服务端容器的格子数必须 ≥ 客户端：加格子要在全量同步（`method_17449`）**之后**加，否则客户端下标越界崩。
6. 别在游戏进程运行时直接覆盖 mods 里的 jar：会 `ZipFile invalid LOC header`。做法：`cp` 成 `.tradepost-new.jar` 再 `mv -f`（换 inode）。
7. 改界面像素位置先量：`class_492.method_25394` 里行 y = `y0+19+20i`，第一成本 x0+10、第二成本 x0+40、成品 x0+73；物品图标自带 z≈150，列表整体在 z=100。
---

## 12. 让别人"真的交易"，而不只是看一眼：`TradeApi`

`TradepostApi` 只查底价。要让别的入口（自己的方块、NPC、任务奖励、脚本）真的按本村行情买卖，
用 `dev.guoke.tradepost.api.TradeApi` —— 它问的价和集市界面那一行是同一份算式，成交也走同一套记账
（库存、银库、经验、全世界货量）。

```java
// 问价：这个村此刻收一颗钻石什么价、能收几手（不改任何状态）
TradeApi.Quote q = TradeApi.quote(player, "minecraft:diamond", TradeApi.Side.SELL, 3);
if (q == null) { /* 不在村里 */ }
else if (!q.ok()) { player.sendMessage(Text.literal(q.reason())); }
else { player.sendMessage(Text.literal(q.unit() + " 颗/件 · 共 " + q.total() + " 颗 · 还能 " + q.packs() + " 手")); }

// 成交（服务端）：成功返回 null，失败返回人话原因
String why = TradeApi.trade(player, "minecraft:diamond", TradeApi.Side.SELL, 3);

// 成交通知：界面点击、命令、TradeApi.trade 都会触发
TradeApi.addListener(e -> LOGGER.info("{} 在 {} {} {} 件 -> {} 绿宝石",
        e.player(), e.village(), e.side(), e.count(), e.total()));
```

- `Side.BUY` = 玩家花钱买货（村里卖给他）；`Side.SELL` = 玩家卖货（村里掏银库）。
- `Quote` 字段：`lot` 一手几件、`unit` 每件几颗、`packs` 最多几手、`count`/`total` 按请求取整后的实际值、`ok`/`reason`。
- 带附魔/效果的货（`gear:` / `book:` / `potion:` 这些码）不在 `trade` 里买卖 —— 它们要连数据一起看，
  用 `TradepostApi.openBoard(player)` 让玩家自己在集市里点。
- 只在服务端调；客户端问价得到 `null`。
- 想加自己的交易类型（比如"拍卖行"），照 §6 加一种每日货架，再在外面包一层自己的门面即可；
  别另写一套价格公式 —— 两处各算各的，迟早差一颗绿宝石，那就是刷钱口子。
