# 跑商：全部产业 / 全部商品总表

数据来源：`/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/config/tradepost/market`（外部目录优先，改动后用 `/tradepost reload` 生效）

- 产业 27 个（手写 + auto_* 自动聚类），商品手写定价 94 个，标签基价 39 条，秘宝 6 个
- 库存上限公式：`cap = clamp(round(512 / 基础价), 16, 2048)`，驿站 ×2
- 买入基准 0.85×、卖出基准 1.18×、需求 1.35×、自产 0.9×、大类 1.18×

## 表 1 · 全部产业

| 产业 id | 名称 | 卖（本村产，买入便宜） | 收（本村缺，收购溢价） | 群系权重 | 大类 |
|---|---|---|---|---|---|
| `armory` | 军械 | iron_sword, shield, arrow, iron_chestplate, crossbow | iron_ingot, leather, flint, string, #c:ingots | #c:is_mountain=2, #c:is_plains=2, #c:is_savanna=2, default=1 | ingot, fiber, fuel |
| `auto_dusts` | 金属粉 | #c:dusts | #c:ores | default=1 | 哈希领 2 类 |
| `auto_dyes` | 染料 | #c:dyes | #c:crops | default=1 | 哈希领 2 类 |
| `auto_foods` | 食材 | #c:foods | #c:crops | default=1 | 哈希领 2 类 |
| `auto_glass` | 玻璃 | #c:glass_blocks | #c:stones | default=1 | 哈希领 2 类 |
| `auto_mushrooms` | 菌菇 | #c:mushrooms | #c:crops | default=1 | 哈希领 2 类 |
| `auto_ores` | 矿石 | #c:ores | #c:dusts | default=1 | 哈希领 2 类 |
| `auto_planks` | 木板 | #planks | #logs | default=1 | 哈希领 2 类 |
| `auto_seafood` | 水产 | #c:foods/raw_fish | #c:crops | default=1 | 哈希领 2 类 |
| `auto_seeds` | 种子 | #c:seeds | #c:crops | default=1 | 哈希领 2 类 |
| `auto_storage_blocks` | 材料块 | #c:storage_blocks | #c:ingots | default=1 | 哈希领 2 类 |
| `auto_wool` | 羊毛 | #wool | #c:dyes | default=1 | 哈希领 2 类 |
| `backwater` | 荒僻 | stick, flint, rotten_flesh | bread, iron_ingot, wheat, coal, leather | #is_overworld=1 | 全收 * |
| `brewing` | 酿造 | honey_bottle, sweet_berries, glass_bottle, potion | wheat, sugar, glass, #c:crops | #is_forest=2, #c:is_plains=2, default=1 | plant, food, glass |
| `coal` | 采煤 | coal, charcoal, coal_block | iron_ingot, rail, torch | #c:is_mountain=3, #is_overworld=1 | ingot, gear, wood |
| `commerce` | 商贸 | paper, book, emerald_block, map, cartography_table | gold_ingot, emerald, ink_sac | #c:is_plains=2, #c:is_beach=2, #is_overworld=1 | ingot, gem, study |
| `farming` | 农耕 | hay_block, golden_carrot, wheat, carrot, potato, beetroot, #c:crops | bone_meal, leather, iron_hoe | #is_overworld=1, #c:is_plains=3 | fuel, fiber, built |
| `fishing` | 渔猎 | cod, salmon, rabbit_hide, rabbit | string, feather, fishing_rod | #c:is_river=3, #c:is_beach=2, #c:is_ocean=2, default=1 | fiber, dye, food |
| `forestry` | 林业 | oak_log, spruce_log, birch_log, honey_bottle, charcoal, #logs | iron_ingot, coal, stone_axe | #is_forest=4, #is_taiga=2, default=1 | ingot, fuel, gear |
| `gems` | 宝石 | amethyst_shard, lapis_lazuli, emerald, quartz, #c:gems | iron_pickaxe, glass, iron_ingot | #c:is_mountain=3, default=1 | ingot, glass, gear |
| `herding` | 畜牧 | leather, white_wool, mutton, beef | wheat, hay_block, salt | #c:is_plains=3, #c:is_savanna=2, default=1 | plant, fuel, built |
| `kiln` | 窑业 | terracotta, glass, brick, white_terracotta | sand, clay_ball, coal | #c:is_badlands=4, #c:is_beach=2, #c:is_desert=2, default=1 | fuel, glass, plant |
| `orchard` | 果园 | apple, sweet_berries, glow_berries, honeycomb | barrel, glass, oak_slab | #is_forest=3, #c:is_plains=2, default=1 | built, glass, fiber |
| `quarrying` | 采石 | stone_bricks, deepslate, #c:stones | wheat, lead, ladder | #c:is_mountain=3, #is_overworld=1 | plant, wood, fiber |
| `scholar` | 学术 | book, bookshelf, map, lectern, enchanted_book | ink_sac, paper, gold_ingot | #is_forest=2, #is_overworld=1 | dye, fiber, gem |
| `smelting` | 冶炼 | iron_ingot, copper_ingot, gold_ingot, #c:ingots | coal, charcoal, raw_iron | #c:is_mountain=3, #c:is_badlands=2, default=1 | ore, fuel |
| `textile` | 纺织 | white_wool, white_carpet, string, leather, #c:strings | white_dye, red_dye, leather, shears | #c:is_plains=2, #is_forest=2, default=1 | fiber, dye, plant |

## 表 2 · 全部商品（手写定价）

| 商品 | 基础价 | 买入价 | 卖出价 | 库存上限 | 村/驿站 | 哪些产业产它 | 哪些产业收它 |
|---|---|---|---|---|---|---|---|
| `rotten_flesh` | 0.03 | 0.03 | 0.04 | 2048 | 2048/4096 | backwater | — |
| `andesite` | 0.05 | 0.04 | 0.06 | 2048 | 2048/4096 | — | — |
| `cobblestone` | 0.05 | 0.04 | 0.06 | 2048 | 2048/4096 | — | — |
| `sand` | 0.05 | 0.04 | 0.06 | 2048 | 2048/4096 | — | kiln |
| `stick` | 0.05 | 0.04 | 0.06 | 2048 | 2048/4096 | backwater | — |
| `gravel` | 0.06 | 0.05 | 0.07 | 2048 | 2048/4096 | — | — |
| `clay_ball` | 0.08 | 0.07 | 0.09 | 2048 | 2048/4096 | — | kiln |
| `deepslate` | 0.08 | 0.07 | 0.09 | 2048 | 2048/4096 | quarrying | — |
| `sweet_berries` | 0.08 | 0.07 | 0.09 | 2048 | 2048/4096 | brewing, orchard | — |
| `arrow` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | armory | — |
| `bone_meal` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | — | farming |
| `carrot` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | farming | — |
| `feather` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | — | fishing |
| `glass_bottle` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | brewing | — |
| `oak_slab` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | — | orchard |
| `potato` | 0.1 | 0.09 | 0.12 | 2048 | 2048/4096 | farming | — |
| `beetroot` | 0.12 | 0.10 | 0.14 | 2048 | 2048/4096 | farming | — |
| `torch` | 0.12 | 0.10 | 0.14 | 2048 | 2048/4096 | — | coal |
| `wheat` | 0.12 | 0.10 | 0.14 | 2048 | 2048/4096 | farming | backwater, brewing, herding, quarrying |
| `apple` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | orchard | — |
| `charcoal` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | coal, forestry | smelting |
| `rabbit_hide` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | fishing | — |
| `string` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | textile | armory, fishing |
| `sugar` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | — | brewing |
| `terracotta` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | kiln | — |
| `white_carpet` | 0.15 | 0.13 | 0.18 | 2048 | 2048/4096 | textile | — |
| `coal` | 0.18 | 0.15 | 0.21 | 2048 | 2048/4096 | coal | backwater, forestry, kiln, smelting |
| `white_terracotta` | 0.18 | 0.15 | 0.21 | 2048 | 2048/4096 | kiln | — |
| `brick` | 0.2 | 0.17 | 0.24 | 2048 | 2048/4096 | kiln | — |
| `cod` | 0.2 | 0.17 | 0.24 | 2048 | 2048/4096 | fishing | — |
| `flint` | 0.2 | 0.17 | 0.24 | 2048 | 2048/4096 | backwater | armory |
| `ladder` | 0.2 | 0.17 | 0.24 | 2048 | 2048/4096 | — | quarrying |
| `rabbit` | 0.2 | 0.17 | 0.24 | 2048 | 2048/4096 | fishing | — |
| `stone_bricks` | 0.2 | 0.17 | 0.24 | 2048 | 2048/4096 | quarrying | — |
| `birch_log` | 0.25 | 0.21 | 0.29 | 2048 | 2048/4096 | forestry | — |
| `glow_berries` | 0.25 | 0.21 | 0.29 | 2048 | 2048/4096 | orchard | — |
| `mutton` | 0.25 | 0.21 | 0.29 | 2048 | 2048/4096 | herding | — |
| `oak_log` | 0.25 | 0.21 | 0.29 | 2048 | 2048/4096 | forestry | — |
| `salmon` | 0.25 | 0.21 | 0.29 | 2048 | 2048/4096 | fishing | — |
| `spruce_log` | 0.25 | 0.21 | 0.29 | 2048 | 2048/4096 | forestry | — |
| `beef` | 0.3 | 0.26 | 0.35 | 1707 | 1707/3414 | herding | — |
| `glass` | 0.3 | 0.26 | 0.35 | 1707 | 1707/3414 | kiln | brewing, gems, orchard |
| `ink_sac` | 0.3 | 0.26 | 0.35 | 1707 | 1707/3414 | — | commerce, scholar |
| `paper` | 0.3 | 0.26 | 0.35 | 1707 | 1707/3414 | commerce | scholar |
| `red_dye` | 0.3 | 0.26 | 0.35 | 1707 | 1707/3414 | — | textile |
| `white_dye` | 0.3 | 0.26 | 0.35 | 1707 | 1707/3414 | — | textile |
| `bread` | 0.35 | 0.30 | 0.41 | 1463 | 1463/2926 | — | backwater |
| `stone_axe` | 0.35 | 0.30 | 0.41 | 1463 | 1463/2926 | — | forestry |
| `honeycomb` | 0.4 | 0.34 | 0.47 | 1280 | 1280/2560 | orchard | — |
| `leather` | 0.4 | 0.34 | 0.47 | 1280 | 1280/2560 | herding, textile | armory, backwater, farming, textile |
| `rail` | 0.4 | 0.34 | 0.47 | 1280 | 1280/2560 | — | coal |
| `redstone` | 0.4 | 0.34 | 0.47 | 1280 | 1280/2560 | — | — |
| `white_wool` | 0.45 | 0.38 | 0.53 | 1138 | 1138/2276 | herding, textile | — |
| `barrel` | 0.5 | 0.42 | 0.59 | 1024 | 1024/2048 | — | orchard |
| `fishing_rod` | 0.5 | 0.42 | 0.59 | 1024 | 1024/2048 | — | fishing |
| `honey_bottle` | 0.55 | 0.47 | 0.65 | 931 | 931/1862 | brewing, forestry | — |
| `copper_ingot` | 0.6 | 0.51 | 0.71 | 853 | 853/1706 | smelting | — |
| `lead` | 0.6 | 0.51 | 0.71 | 853 | 853/1706 | — | quarrying |
| `quartz` | 0.6 | 0.51 | 0.71 | 853 | 853/1706 | gems | — |
| `shears` | 0.6 | 0.51 | 0.71 | 853 | 853/1706 | — | textile |
| `book` | 0.65 | 0.55 | 0.77 | 788 | 788/1576 | commerce, scholar | — |
| `iron_hoe` | 0.7 | 0.59 | 0.83 | 731 | 731/1462 | — | farming |
| `lectern` | 0.9 | 0.77 | 1.06 | 569 | 569/1138 | scholar | — |
| `cartography_table` | 1 | 0.85 | 1.18 | 512 | 512/1024 | commerce | — |
| `golden_carrot` | 1 | 0.85 | 1.18 | 512 | 512/1024 | farming | — |
| `iron_ingot` | 1 | 0.85 | 1.18 | 512 | 512/1024 | smelting | armory, backwater, coal, forestry, gems |
| `hay_block` | 1.1 | 0.94 | 1.30 | 465 | 465/930 | farming | herding |
| `lapis_lazuli` | 1.2 | 1.02 | 1.42 | 427 | 427/854 | gems | — |
| `iron_sword` | 1.4 | 1.19 | 1.65 | 366 | 366/732 | armory | — |
| `amethyst_shard` | 1.5 | 1.27 | 1.77 | 341 | 341/682 | gems | — |
| `map` | 1.5 | 1.27 | 1.77 | 341 | 341/682 | commerce, scholar | — |
| `obsidian` | 1.5 | 1.27 | 1.77 | 341 | 341/682 | — | — |
| `shield` | 1.5 | 1.27 | 1.77 | 341 | 341/682 | armory | — |
| `bookshelf` | 1.6 | 1.36 | 1.89 | 320 | 320/640 | scholar | — |
| `coal_block` | 1.6 | 1.36 | 1.89 | 320 | 320/640 | coal | — |
| `gold_ingot` | 1.6 | 1.36 | 1.89 | 320 | 320/640 | smelting | commerce, scholar |
| `iron_pickaxe` | 2.2 | 1.87 | 2.60 | 233 | 233/466 | — | gems |
| `potion` | 3 | 2.55 | 3.54 | 171 | 171/342 | brewing | — |
| `blaze_rod` | 4 | 3.40 | 4.72 | 128 | 128/256 | — | — |
| `iron_chestplate` | 5.5 | 4.67 | 6.49 | 93 | 93/186 | armory | — |
| `ender_pearl` | 6 | 5.10 | 7.08 | 85 | 85/170 | — | — |
| `crossbow` | 8 | 6.80 | 9.44 | 64 | 64/128 | armory | — |
| `iron_block` | 9 | 7.65 | 10.62 | 57 | 57/114 | — | — |
| `emerald` | 12 | 10.20 | 14.16 | 43 | 43/86 | gems | commerce |
| `name_tag` | 12 | 10.20 | 14.16 | 43 | 43/86 | — | — |
| `gold_block` | 14.4 | 12.24 | 16.99 | 36 | 36/72 | — | — |
| `golden_apple` | 18 | 15.30 | 21.24 | 28 | 28/56 | — | — |
| `diamond` | 20 | 17.00 | 23.60 | 26 | 26/52 | — | — |
| `enchanted_book` | 20 | 17.00 | 23.60 | 26 | 26/52 | scholar | — |
| `ancient_debris` | 60 | 51.00 | 70.80 | 16 | 16/32 | — | — |
| `emerald_block` | 100 | 85.00 | 118.00 | 16 | 16/32 | commerce | — |
| `enchanted_golden_apple` | 120 | 102.00 | 141.60 | 16 | 16/32 | — | — |
| `netherite_ingot` | 180 | 153.00 | 212.40 | 16 | 16/32 | — | — |
| `totem_of_undying` | 200 | 170.00 | 236.00 | 16 | 16/32 | — | — |

## 表 3 · 标签基价（模组物品靠这一层定价）

| 标签 | 类基价 | 稀有度乘子后（COMMON/UNCOMMON/RARE/EPIC） | 被哪些产业引用 |
|---|---|---|---|
| `#c:ingots/netherite` | 20 | 20 / 40 / 100 / 300 | — |
| `#c:ingots/iron` | 1 | 1 / 2 / 5 / 15 | — |
| `#c:ingots/copper` | 0.7 | 0.7 / 1.4 / 3.5 / 10.5 | — |
| `#c:ingots/gold` | 1.6 | 1.6 / 3.2 / 8 / 24 | — |
| `#c:ingots/bronze` | 1.2 | 1.2 / 2.4 / 6 / 18 | — |
| `#c:ingots/steel` | 1.5 | 1.5 / 3 / 7.5 / 22.5 | — |
| `#c:ingots/silver` | 1.1 | 1.1 / 2.2 / 5.5 / 16.5 | — |
| `#c:ingots/tin` | 0.8 | 0.8 / 1.6 / 4 / 12 | — |
| `#c:ingots/lead` | 0.8 | 0.8 / 1.6 / 4 / 12 | — |
| `#c:ingots/zinc` | 0.7 | 0.7 / 1.4 / 3.5 / 10.5 | — |
| `#c:ingots/nickel` | 1 | 1 / 2 / 5 / 15 | — |
| `#c:ingots/platinum` | 6 | 6 / 12 / 30 / 90 | — |
| `#c:ingots/electrum` | 2 | 2 / 4 / 10 / 30 | — |
| `#c:ingots/uranium` | 3 | 3 / 6 / 15 / 45 | — |
| `#c:ingots` | 1.2 | 1.2 / 2.4 / 6 / 18 | armory(收), auto_storage_blocks(收), smelting(产) |
| `#c:storage_blocks` | 8 | 8 / 16 / 40 / 120 | auto_storage_blocks(产) |
| `#c:nuggets` | 0.15 | 0.15 / 0.3 / 0.75 / 2.25 | — |
| `#c:dusts` | 0.9 | 0.9 / 1.8 / 4.5 / 13.5 | auto_dusts(产), auto_ores(收) |
| `#c:plates` | 1.5 | 1.5 / 3 / 7.5 / 22.5 | — |
| `#c:ores` | 0.8 | 0.8 / 1.6 / 4 / 12 | auto_dusts(收), auto_ores(产) |
| `#c:raw_materials` | 0.6 | 0.6 / 1.2 / 3 / 9 | — |
| `#c:gems` | 4 | 4 / 8 / 20 / 60 | gems(产) |
| `#c:rods/wooden` | 0.1 | 0.1 / 0.2 / 0.5 / 1.5 | — |
| `#c:strings` | 0.6 | 0.6 / 1.2 / 3 / 9 | textile(产) |
| `#c:leathers` | 0.6 | 0.6 / 1.2 / 3 / 9 | — |
| `#c:dyes` | 0.3 | 0.3 / 0.6 / 1.5 / 4.5 | auto_dyes(产), auto_wool(收) |
| `#c:crops` | 0.3 | 0.3 / 0.6 / 1.5 / 4.5 | auto_dyes(收), auto_foods(收), auto_mushrooms(收), auto_seafood(收), auto_seeds(收), brewing(收), farming(产) |
| `#c:seeds` | 0.2 | 0.2 / 0.4 / 1 / 3 | auto_seeds(产) |
| `#minecraft:wool` | 0.4 | 0.4 / 0.8 / 2 / 6 | auto_wool(产) |
| `#minecraft:planks` | 0.25 | 0.25 / 0.5 / 1.25 / 3.75 | auto_planks(产) |
| `#minecraft:logs` | 0.3 | 0.3 / 0.6 / 1.5 / 4.5 | auto_planks(收), forestry(产) |
| `#minecraft:coals` | 0.2 | 0.2 / 0.4 / 1 / 3 | — |
| `#c:gears` | 2 | 2 / 4 / 10 / 30 | — |
| `#c:rods` | 0.6 | 0.6 / 1.2 / 3 / 9 | — |
| `#c:foods` | 0.4 | 0.4 / 0.8 / 2 / 6 | auto_foods(产) |
| `#c:mushrooms` | 0.3 | 0.3 / 0.6 / 1.5 / 4.5 | auto_mushrooms(产) |
| `#c:bones` | 0.4 | 0.4 / 0.8 / 2 / 6 | — |
| `#c:glass_blocks` | 0.5 | 0.5 / 1 / 2.5 / 7.5 | auto_glass(产) |
| `#c:foods/raw_fish` | 0.5 | 0.5 / 1 / 2.5 / 7.5 | auto_seafood(产) |

未命中任何标签的物品走兜底价 `default = 0.05 × 稀有度乘子`。

## 表 4 · 秘宝（只收物，25% 的村有，7 天轮换）

| id | 给什么 | 件数 | 成本 | 价值倍率 | 权重 |
|---|---|---|---|---|---|
| `ancient_debris` | ancient_debris | 1 | 按价x #c:ingots + 按价x obsidian | ×18 | 6 |
| `diamond_cache` | diamond | 8 | 按价x #c:ingots + 按价x coal_block | ×6 | 14 |
| `golden_apple_secret` | enchanted_golden_apple | 1 | 按价x #c:ingots + 按价x honey_bottle | ×12 | 10 |
| `mending_book` | enchanted_book | 1 | 按价x lapis_lazuli + 按价x book | ×15 | 8 |
| `name_tag_hoard` | name_tag | 2 | 按价x paper + 按价x gold_ingot | ×5 | 16 |
| `totem_trade` | totem_of_undying | 1 | 按价x emerald_block + 按价x gold_block | ×20 | 4 |

## 表 5 · 用途大类（决定"任何村都收货"的加成）

| 大类 | 覆盖标签 | 收它加成的产业 |
|---|---|---|
| `fuel` | `c:coals`, `minecraft:coals`, `c:fuels` | farming, herding, forestry, smelting, kiln, armory |
| `ore` | `c:ores`, `c:raw_materials` | smelting |
| `ingot` | `c:ingots`, `c:nuggets`, `c:dusts`, `c:plates` | forestry, coal, gems, commerce, armory |
| `gem` | `c:gems` | commerce, scholar |
| `wood` | `minecraft:logs`, `minecraft:planks` | quarrying, coal |
| `plant` | `c:crops`, `c:seeds`, `c:mushrooms` | herding, quarrying, kiln, textile, brewing |
| `food` | `c:foods`, `c:foods/raw_fish` | fishing, brewing |
| `fiber` | `c:leathers`, `c:strings`, `minecraft:wool`, `minecraft:feather_equivalents` | farming, orchard, fishing, quarrying, textile, scholar, armory |
| `dye` | `c:dyes` | fishing, textile, scholar |
| `glass` | `c:glass_blocks`, `c:glass_panes` | orchard, gems, kiln, brewing |
| `built` | `c:stones` | farming, orchard, herding |
| `study` | `minecraft:bookshelf_books` | commerce |
| `gear` | `c:tools`, `c:rods` | forestry, coal, gems |

关键词兜底 73 条（标签认不出的物品按 id 关键字归类）。
