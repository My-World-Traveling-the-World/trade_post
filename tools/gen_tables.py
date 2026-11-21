#!/usr/bin/env python3
# 生成"全部产业 / 全部商品"总表（读 config/tradepost/market），输出 markdown
import json, glob, os, sys, collections

CFG = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/config/tradepost/market"
OUT = sys.argv[1] if len(sys.argv) > 1 else "/home/guoke/mc-linux/tradepost/docs/market-tables.md"

values = json.load(open(f"{CFG}/values.json", encoding="utf-8"))
tagvals = json.load(open(f"{CFG}/tag_values.json", encoding="utf-8"))
roles = json.load(open(f"{CFG}/roles.json", encoding="utf-8"))
eco = json.load(open(f"{CFG}/economy.json", encoding="utf-8"))
inds = []
for p in sorted(glob.glob(f"{CFG}/industries/*.json")):
    try:
        inds.append(json.load(open(p, encoding="utf-8")))
    except Exception as e:
        print("skip", p, e)
relics = []
for p in sorted(glob.glob(f"{CFG}/relics/*.json")):
    try:
        relics.append(json.load(open(p, encoding="utf-8")))
    except Exception as e:
        print("skip", p, e)

def short(x):
    if not x:
        return x
    if x.startswith("#"):
        return "#" + x[1:].replace("minecraft:", "")
    return x.replace("minecraft:", "")

def cap(base, station=False):
    c = round(512.0 / base) if base > 0 else 0
    c = max(16, min(2048, c))
    return c * (2 if station else 1)

# 物品 -> 产业归属
as_supply = collections.defaultdict(list)
as_demand = collections.defaultdict(list)
for ind in inds:
    for e in ind.get("supply", []):
        if not e.startswith("#"): as_supply[e].append(ind["id"])
    for e in ind.get("demand", []):
        if not e.startswith("#"): as_demand[e].append(ind["id"])

tag_role = roles.get("tags", {})
def tags_of(e):
    # 反向：哪些标签把这个条目算进大类（粗略，仅展示用）
    return []

L = []
L.append("# 跑商：全部产业 / 全部商品总表\n")
L.append(f"数据来源：`{CFG}`（外部目录优先，改动后用 `/tradepost reload` 生效）\n")
L.append(f"- 产业 {len(inds)} 个（手写 + auto_* 自动聚类），商品手写定价 {len(values)} 个，标签基价 {len(tagvals)} 条，秘宝 {len(relics)} 个")
L.append(f"- 库存上限公式：`cap = clamp(round(512 / 基础价), 16, 2048)`，驿站 ×2")
L.append(f"- 买入基准 {eco['buyBase']}×、卖出基准 {eco['sellBase']}×、需求 {eco['demandBonus']}×、自产 {eco['supplyPenalty']}×、大类 {eco['roleDemand']}×\n")

# ---------- 表 1 产业 ----------
L.append("## 表 1 · 全部产业\n")
L.append("| 产业 id | 名称 | 卖（本村产，买入便宜） | 收（本村缺，收购溢价） | 群系权重 | 大类 |")
L.append("|---|---|---|---|---|---|")
for ind in inds:
    bw = ", ".join(f"{short(k)}={v}" for k, v in (ind.get("biome_weights") or {}).items()) or "—"
    sc = ", ".join(short(x) for x in ind.get("supply", [])) or "—"
    dm = ", ".join(short(x) for x in ind.get("demand", [])) or "—"
    ro = roles.get("industries", {}).get(ind["id"])
    if ro == ["*"]:
        ro = "全收 *"
    elif ro:
        ro = ", ".join(ro)
    else:
        ro = "哈希领 2 类"
    L.append(f"| `{ind['id']}` | {ind.get('display','')} | {sc} | {dm} | {bw} | {ro} |")
L.append("")

# ---------- 表 2 商品 ----------
L.append("## 表 2 · 全部商品（手写定价）\n")
L.append("| 商品 | 基础价 | 买入价 | 卖出价 | 库存上限 | 村/驿站 | 哪些产业产它 | 哪些产业收它 |")
L.append("|---|---|---|---|---|---|---|---|")
for item, base in sorted(values.items(), key=lambda kv: (kv[1], kv[0])):
    buy = base * eco["buyBase"]
    sell = base * eco["sellBase"]
    L.append(f"| `{short(item)}` | {base:g} | {buy:.2f} | {sell:.2f} | {cap(base)} | {cap(base)}/{cap(base, True)} | "
             f"{', '.join(short(x) for x in as_supply.get(item, [])) or '—'} | {', '.join(short(x) for x in as_demand.get(item, [])) or '—'} |")
L.append("")

# ---------- 表 3 标签 ----------
L.append("## 表 3 · 标签基价（模组物品靠这一层定价）\n")
L.append("| 标签 | 类基价 | 稀有度乘子后（COMMON/UNCOMMON/RARE/EPIC） | 被哪些产业引用 |")
L.append("|---|---|---|---|")
refs = collections.defaultdict(list)
for ind in inds:
    for e in ind.get("supply", []):
        if e.startswith("#"): refs[e].append(ind["id"] + "(产)")
    for e in ind.get("demand", []):
        if e.startswith("#"): refs[e].append(ind["id"] + "(收)")
for tag, base in tagvals.items():
    if tag == "default": continue
    mul = f"{base:g} / {base*2:g} / {base*5:g} / {base*15:g}"
    L.append(f"| `{tag}` | {base:g} | {mul} | {', '.join(refs.get(tag, [])) or '—'} |")
L.append(f"\n未命中任何标签的物品走兜底价 `default = {tagvals.get('default', 0.05)} × 稀有度乘子`。\n")

# ---------- 表 4 秘宝 ----------
L.append("## 表 4 · 秘宝（只收物，25% 的村有，7 天轮换）\n")
L.append("| id | 给什么 | 件数 | 成本 | 价值倍率 | 权重 |")
L.append("|---|---|---|---|---|---|")
for r in relics:
    cost = " + ".join(f"{c.get('count') or '按价'}x {short(c.get('item') or c.get('tag',''))}" for c in r.get("cost", []))
    L.append(f"| `{r['id']}` | {short(r['item'])} | {r.get('count',1)} | {cost} | ×{r.get('value_multiplier',1):g} | {r.get('weight',1)} |")
L.append("")

# ---------- 表 5 大类 ----------
L.append("## 表 5 · 用途大类（决定\"任何村都收货\"的加成）\n")
L.append("| 大类 | 覆盖标签 | 收它加成的产业 |")
L.append("|---|---|---|")
by_role = collections.defaultdict(lambda: {"tags": [], "inds": []})
for tag, role in tag_role.items():
    if isinstance(role, str): by_role[role]["tags"].append(tag)
for ind, rs in roles.get("industries", {}).items():
    for r in rs:
        by_role[r]["inds"].append(ind)
for r in roles.get("roles", []):
    t = ", ".join(f"`{x}`" for x in by_role[r]["tags"][:6]) or "—"
    i = ", ".join(by_role[r]["inds"]) or "—"
    L.append(f"| `{r}` | {t} | {i} |")
L.append(f"\n关键词兜底 {len(roles.get('keywords', {}))} 条（标签认不出的物品按 id 关键字归类）。\n")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf-8").write("\n".join(L))
print("写出", OUT, os.path.getsize(OUT), "字节")
print("产业", len(inds), "商品", len(values), "标签", len(tagvals), "秘宝", len(relics))
