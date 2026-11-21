# 升级 Minecraft 时怎么改（UPGRADING）

这个模组有 4 个 mixin 直接改原版村民交易界面，还用了大量 `class_1799` / `method_7947` 这种 intermediary 名字。
原版一升级，编译不会报错，**进游戏启动时才炸**（`InvalidMixinException: ... was not located in the target class`），
或者更糟：点了没反应、钱不对。下面是按顺序走的核对单。

## 0. 三条底线（先看这个）

1. `src/main/resources/tradepost.mixins.json` 里 `"required": false` 要一直保持 —— mixin 写错时只 log ERROR、
   不改造那个类，游戏还能开；否则一个 `method_` 改名就等于启动崩溃。
2. 所有 `@Inject(method = ...)` 必须用 `compat/VanillaNames` 里的常量，不许再写裸字符串。
3. 装包前在测试存档跑一遍：开集市 → 买一笔 → 卖一笔（带附魔的装备）→ 关掉重开。

## 1. 换 intermediary 名（机械活）

```bash
JAVAP=~/.jdks/jdk-21.0.12.1+1/bin/javap
JAR="<实例>/.fabric/remappedJars/minecraft-<版本>/client-intermediary.jar"
mkdir -p /tmp/jx && cd /tmp/jx && unzip -o -q "$JAR" 'net/minecraft/*.class'

# 已知的类：看方法还在不在、签名变没变
$JAVAP -p -cp /tmp/jx net.minecraft.class_1728 | grep -E 'method_20215|method_7595|method_20595|method_7611'
$JAVAP -p -cp /tmp/jx net.minecraft.class_1725 | grep -E 'method_7645|method_5447'
$JAVAP -p -cp /tmp/jx net.minecraft.class_1727 | grep -E 'method_7667'
$JAVAP -p -cp /tmp/jx net.minecraft.class_1914 | grep -E 'method_19272|method_19279|method_57556'
$JAVAP -p -cp /tmp/jx net.minecraft.class_492  | grep -E 'field_19161|field_19163|method_25394'
```

找不到的方法 = 这次升级要改的地方。顺带核一遍 `docs/INTERMEDIARY.md` 里那 45 个类还在不在。

## 2. 4 个 mixin 各自"假设了原版什么"

| mixin | 注入点（VanillaNames 常量） | 我们假设原版的行为 | 假设破了会怎样 |
|---|---|---|---|
| `MerchantInventoryMixin` | `MERCHANT_INV_TICK` = `class_1725#method_7645`（TAIL） | 每 tick 重算天平与结果格；`field_7843` 是"原版认可的那组报价"，`method_5447(int, ItemStack)` 写槽 | 天平不摆钱 / 成品格不亮 |
| `MerchantResultSlotMixin` | `TRADE_OUTPUT_ON_TAKE` = `class_1727#method_7667`（HEAD，可取消） | **先塞货 → 再清槽 → 才回调**；回调里 `ci.cancel()` 能拦住后续 | 买一给二 / 卖不扣货（印钞） |
| `MerchantScreenHandlerMixin` | `HANDLER_CLICK_OFFER`=`method_20215`、`HANDLER_ON_CLOSED`=`method_7595`、`HANDLER_QUICK_MOVE`=`method_20595`、`HANDLER_QUICK_TRANSFER`=`method_7601` | 点行会调 `method_7643(index)` 选行；`field_7863` 是商人、`field_7861` 是库存；`method_20595` 会 `(class_1297) field_7863` 强转；`method_7601`（transferSlot，shift 快速移动）**只往背包搬、从不往交易格塞**，而且它会先清空结果格再回调 `method_7667` | 点行不摆钱 / shift 点击丢包 / shift 取货款不结算 / 关界面吞钱 |
| `OfferClampMixin` | `OFFER_FIRST_COST` = `class_1914#method_19272`（HEAD，可取消） | 原版在这里把第一成本数量夹到 `getMaxCount()` | 列表价格只显示到 64 |

改任何一个注入点时，**同时**改 `compat/VanillaNames` 里那条常量（这样 diff 一眼能看出来动了哪）。

## 3. 原版几何（界面像素）

```
class_492.method_25394：x0=(field_22789-field_2792)/2、y0=(field_22790-field_2779)/2
第 i 行 y = y0+19+20i；第一成本 x0+10、第二成本 x0+40、成品 x0+73
列表整体 method_46416(0,0,100)；物品图标自带 translate(...,150) -> 自绘文字要 z>250 才压得住
```

升级后先开一次集市、全屏截图，量行距和"绿宝石格"的位置；差 1~2 px 就够让数字和堆叠数叠在一起。
（截图：`spectacle --fullscreen --background --nonotify --output /tmp/g.png`，放大用 `magick crop + -filter point -resize 800%`。）

## 4. 数据/行为契约（跟版本无关，但升级时容易碰坏）

- **价格标记**：我们自己的报价把总价塞在 `class_1914` 的 xp 通道里、**取负**当"这是我们的"标记（`method_19279() < 0`）。
  原版永远不会为负。读价先判负号，再判 `instanceof MarketMerchant`。
- **结算判据**：卖页（成本栈价值 ≤0）交回原版；买页才由我们接管。`Pay.price(offer)` 单参 = 成本栈的价值。
- **一手/份数**：`class_9306` 是 record，`comp_2424()` 是 `Holder<Item>`、`comp_2425()` 是数量、`comp_2427()` 是原型栈。
- **扣件数**：`class_1799.method_7934(n)` 是**减**、`method_7939(n)` 是设、`method_7933(n)` 是加。
- **大数量**：先挂 `max_stack_size` 组件（`class_9334.field_50071`）再 `method_7939`，顺序反了会被夹到 64。
- **shift 取结果格的坑**：`method_7601` 把结果格那一摞塞进背包时用的是同一个对象，塞完数量归零，回调 `method_7667` 拿到的是**空栈**（普通点击拿到的是满的）。所以 `mc/QuickTake.java` 在 `method_7601` HEAD 存一份快照，结算优先用它。删掉它就会重新出现"shift 取货款不结算"。
- **加格子**：必须在全量同步 `method_17449` **之后**加（原版 `method_7610` 按发出列表的长度循环，服务端多一格客户端就下标越界崩）。

## 5. 自检三板斧

1. 启动日志没有 `InvalidMixinException`、没有 `at dev.guoke` 堆栈。
2. `/tradepost audit`：注册件数、可交易件数、黑名单拦多少，跟上一版比不该突变。
3. 三笔账（每笔都看日志里的 `[tradepost]` 行）：
   - 买一笔 13 颗的东西 → `结算 价=13 付=13 找零=0`，天平摆的是 1 块 + 4 颗；
   - 卖一笔带附魔的装备 → `扣货 ... 前=1 后=0`、`集市成交 卖 ...`，货真的少了；
   - 卖一笔高价货（>64）→ 结果格是一摞（数量=货款），落地时拆成普通 64 摞。