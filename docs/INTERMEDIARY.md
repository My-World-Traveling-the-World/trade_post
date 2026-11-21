# intermediary 名对照表（读这个仓库的钥匙）

这个仓库是**对着 Minecraft 的 intermediary 名**（`class_1799` / `method_7947`）编的，
因为离线构建时只有 intermediary jar 可用。看代码时把这张表放旁边就顺了。

> 表里只列**本仓库真的用到**、且核对过用法的名字。没列到的按 §3 自己查。
> 版本：Minecraft 1.21.1 / Fabric（intermediary）。换版本这些数字会变（语义不变）。

## 1. 类

| intermediary | Yarn | 一句话 |
|---|---|---|
| `class_1799` | ItemStack | 一摞物品（整个模组出现 218 次，最核心） |
| `class_1792` | Item | 物品 |
| `class_2487` | NbtCompound | NBT |
| `class_2960` | Identifier | `minecraft:diamond` 那种 id |
| `class_2561` | Text | 文本组件 |
| `class_5250` | MutableText | 可拼接的文本 |
| `class_124` | Formatting | 颜色枚举（`field_1065` 白、`field_1080` 红、`field_1077` 绿…） |
| `class_1937` / `class_3218` | World / ServerWorld | 世界 / 服务端世界 |
| `class_1657` / `class_3222` | PlayerEntity / ServerPlayerEntity | 玩家 / 服务端玩家 |
| `class_1263` | Inventory | 容器接口（`method_5439` 格数、`method_5438` 取、`method_5447` 放） |
| `class_1703` | ScreenHandler | 容器基类（槽列表 `field_7761`、光标 `method_34255`） |
| `class_1728` | MerchantScreenHandler | 村民交易容器 |
| `class_1725` | MerchantInventory | 商人那三个格（0/1 付款、2 成品） |
| `class_1727` | TradeOutputSlot | 成品格（`method_7667` = onTakeItem） |
| `class_1735` | Slot | 一个格子 |
| `class_1914` | MerchantOffer | 一条报价 |
| `class_1915` | Merchant | 商人（我们的 `MarketMerchant` 继承它） |
| `class_1916` | TradeOfferList | 报价表 |
| `class_9306` | ItemCost | 报价的一项成本（record：物品 / 件数 / 栈） |
| `class_9334` | DataComponentTypes | 组件类型表（最大堆叠、附魔…） |
| `class_9331` | ComponentType | 组件类型 |
| `class_492` | MerchantScreen | 原版交易界面（我们的 `MarketScreen` 继承它） |
| `class_437` / `class_310` | Screen / MinecraftClient | 界面基类 / 客户端单例 |
| `class_332` / `class_327` | DrawContext / TextRenderer | 画东西 / 量字宽 |
| `class_4587` | MatrixStack | 变换栈（z 序靠它） |
| `class_2168` | ServerCommandSource | 命令上下文 |
| `class_8710` / `class_8710.class_9154` | CustomPayload / CustomPayload.Id | 自定义包 |
| `class_9129` / `class_9139` | PacketByteBuf / PacketCodec | 编解码 |
| `class_1269` | ActionResult | 交互结果枚举（`field_5811` = PASS） |
| `class_2338` / `class_1923` / `class_243` | BlockPos / ChunkPos / Vec3d | 坐标 |
| `class_2378` / `class_7924` | Registry / RegistryKeys | 注册表 / 注册表键 |
| `class_6862` | TagKey | 标签键（`#c:ingots` 那种） |
| `class_7225` / `class_7225.class_7874` | RegistryWrapper / WrapperLookup | 拿附魔、物品用 |
| `class_3195` | Structure | 结构（找村庄用） |
| `class_3449` / `class_3443` | StructureStart / StructurePiece | 村庄生成出来的那些房子 |
| `class_3341` | Structure.BoundingBox | 房子的包围盒（算户数） |
| `class_7059` | StructureSet | 结构集 |

## 2. 方法（最容易踩的几个先记）

| intermediary | Yarn | 要点 |
|---|---|---|
| `method_7960` | `isEmpty()` | 空栈判断 |
| `method_7947` | `getCount()` | 件数 |
| `method_7939(n)` | `setCount(n)` | **设成** n |
| `method_7934(n)` | `decrement(n)` | **减** n（写错就"扣了没扣"） |
| `method_7933(n)` | `increment(n)` | 加 n |
| `method_7972` | `copy()` | 复制一摞 |
| `method_7909` | `getItem()` | 物品 |
| `method_7914` / `method_7919` / `method_7936` | maxCount / maxDamage / damage | 堆叠上限 / 最大耐久 / 已损 |
| `method_5438` / `method_5447` / `method_5439` | getStack / setStack / size | 容器三件套 |
| `method_5444` | `Inventory#getMaxCount()` | 容器默认 **99**（不是 64） |
| `method_31548` | `Player#getInventory()` | 玩家背包 |
| `method_7611` / `method_7621` | getSlot / addSlot | 容器找槽 / 加槽（按列表位置，不按 `slot.index`） |
| `method_43470` | `Text.literal` | 造文本 |
| `method_51433` | `DrawContext#drawText` | 画字（最后一个参数 true = 带阴影） |
| `method_51427` | `DrawContext#drawItem` | 画物品图标 |
| `method_25294` | `DrawContext#fill` | 画色块 |
| `method_51448` / `method_22903` / `method_22909` | getMatrices / push / pop | 变换栈 |
| `method_46416` | `MatrixStack#translate` | 平移（z 序靠第三个参数） |
| `method_9226` | `sendFeedback` | 回一句话给玩家 |
| `method_23317/23318/23321` | getX / getY / getZ | 坐标 |
| `method_5770` | `getServer()` | 拿服务端（判客户端/服务端常用） |

### 2.1 三个会**丢数据**的坑（真踩过，别重犯）

| 写法 | 实际语义 | 正确写法 |
|---|---|---|
| `stack.method_57381(type)` | **不是读！** 转发到 `ComponentMap.method_57939`，先写时复制、再 `map.put(type, Optional.empty())`／`map.remove(type)`，**然后把旧值返回**。每读一次物品就少一个组件（附魔书被剥成白板就是这么来的） | `Bridge.compRead(stack, type)` → `stack.method_57353().method_57829(type)`（纯 `map.get` + 回落物品默认值） |
| `stack.method_7972()` 当快照 | `copy()` 只把"共享"标记置 true，**底下的组件表是同一张**，原件被改副本跟着变 | 要快照就抓住组件对象本身（组件是不可变值），参考 `Bridge.bookSnap` |
| `stack.method_57380()` 的返回值 | 拿到的补丁**和这摞共用一张 map**，不是冻结的副本 | 只用来打印/判断，别缓存、别当快照 |

> 这三条的共同点：原版很多 getter 返回的是**活对象**。把所有"读"都当成"看一眼"来写，
> 在 1.21 的组件系统上就会变成"顺手改掉"。写完读数据的代码，用 `javap -p -c` 看一眼
> 方法体里有没有 `put` / `remove` 再收工。

## 3. 表里没有的，自己查（30 秒）

```bash
JAVAP=~/.jdks/jdk-21.0.12.1+1/bin/javap
JAR="/path/to/versions/<版本>/.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar"

$JAVAP -cp "$JAR" -p net.minecraft.class_1728            # 看这个类声明了什么
$JAVAP -cp "$JAR" -c -p net.minecraft.class_1728 | less  # 看方法体（行号、字节码顺序）
```

读字节码的实用技巧：

1. **看返回值 + 参数** 基本能猜出语义（`(I)Lnet/minecraft/class_1735;` 就是 `getSlot(int)`）。
2. **看调用点**：想知道 `method_20215` 是什么，就 `grep -rn method_20215`，看谁调它、传什么。
3. **查原版顺序**（这个模组踩过的坑都在这儿）：`class_1703` 取物品是
   1151 判 `method_7674`（能不能拿）→ 1161 把格子内容塞进光标 → 1179 清空格子 → **1193 才调 `method_7667`**。
   也就是"货在回调之前就已经给出去了"，回调里再补一份就是双倍。
4. 想查 Yarn 名：能连网就查 [linkie](https://linkie.shedaniel.dev/mappings)、或对着 1.21.1 的 Yarn 映射文件搜数字。
