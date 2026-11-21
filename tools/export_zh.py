#!/usr/bin/env python3
# 把市场数据导出成中文表（物品名取自游戏语言文件：原版 assets + 各模组 jar）
import json, glob, os, zipfile, csv, collections, sys

CFG = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/config/tradepost/market"
VANILLA = "/home/guoke/mc-linux/assets/objects/f8/f87510f4509890eaf176e0de1430f6bb326a6800"
MODS = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/mods"
OUTDIR = "/home/guoke/mc-linux/tradepost/docs"
MD = f"{OUTDIR}/market-tables-zh.md"
CSV = f"{OUTDIR}/market-items-zh.csv"

lang = {}
def merge(obj):
    for k, v in obj.items():
        if isinstance(v, str): lang[k] = v

try:
    merge(json.load(open(VANILLA, encoding="utf-8")))
    print("原版 zh_cn:", len(lang), "条")
except Exception as e:
    print("原版语言文件读不了:", e)

files = 0
for p in glob.glob(f"{MODS}/*.jar"):
    try:
        z = zipfile.ZipFile(p)
    except Exception:
        continue
    for n in z.namelist():
        if n.startswith("assets/") and n.endswith("/lang/zh_cn.json"):
            try:
                merge(json.loads(z.read(n).decode("utf-8")))
                files += 1
            except Exception:
                pass
print("模组 zh_cn 文件:", files, "个，合并后词条:", len(lang))

def zh(item_id, fallback=None):
    if not item_id: return ""
    if item_id.startswith("#"): return item_id
    ns, _, path = item_id.partition(":")
    if not path: ns, path = "minecraft", ns
    for k in (f"item.{ns}.{path}", f"block.{ns}.{path}", f"item.{ns}.{path}.name"):
        if k in lang: return lang[k]
    return fallback or item_id.replace("minecraft:", "")

values = json.load(open(f"{CFG}/values.json", encoding="utf-8"))
eco = json.load(open(f"{CFG}/economy.json", encoding="utf-8"))
tagvals = json.load(open(f"{CFG}/tag_values.json", encoding="utf-8"))
roles = json.load(open(f"{CFG}/roles.json", encoding="utf-8"))
inds = []
for p in sorted(glob.glob(f"{CFG}/industries/*.json")):
    try: inds.append(json.load(open(p, encoding="utf-8")))
    except Exception: pass
relics = []
for p in sorted(glob.glob(f"{CFG}/relics/*.json")):
    try: relics.append(json.load(open(p, encoding="utf-8")))
    except Exception: pass

sup, dem = collections.defaultdict(list), collections.defaultdict(list)
for i in inds:
    for e in i.get("supply", []):
        if not e.startswith("#"): sup[e].append(i["id"])
    for e in i.get("demand", []):
        if not e.startswith("#"): dem[e].append(i["id"])

def cap(base, station=False):
    c = max(16, min(2048, round(512.0 / base) if base else 0))
    return c * (2 if station else 1)

def ind_name(iid):
    for i in inds:
        if i["id"] == iid: return i.get("display", iid)
    return iid

def role_name(r):
    return {"fuel":"燃料","ore":"矿石","ingot":"锭材","gem":"宝石","wood":"木材","plant":"作物",
            "food":"食物","fiber":"纤维","dye":"染料","glass":"玻璃","built":"建材","study":"文具","gear":"器具"}.get(r, r)

L = []
L.append("# 跑商 · 全部产业与商品（中文）\n")
L.append(f"- 数据：`{CFG}`（外部目录优先，改完 `/tradepost reload` 生效）")
L.append(f"- 物品中文名来自游戏语言文件（原版 + {files} 个模组的 `zh_cn.json`，共 {len(lang)} 条词条）")
L.append(f"- 产业 {len(inds)} 个 / 手写定价商品 {len(values)} 个 / 标签基价 {len(tagvals)} 条 / 秘宝 {len(relics)} 个")
L.append(f"- 买入 = 基础价 × {eco['buyBase']}，卖出 = 基础价 × {eco['sellBase']}；村癖 ±{eco['quirkPct']*100:.0f}%、行情 ±{eco['dailyPct']*100:.0f}%、库存 0.55–1.45 倍另算")
L.append(f"- 库存上限 = clamp(round(512 / 基础价), 16, 2048)，驿站 ×2\n")

L.append("## 表 1 · 全部产业\n")
L.append("| 产业 | id | 卖（本村产，买便宜） | 收（本村缺，收得贵） | 群系权重 |")
L.append("|---|---|---|---|---|")
for i in inds:
    s = "、".join(zh(x) for x in i.get("supply", [])) or "—"
    d = "、".join(zh(x) for x in i.get("demand", [])) or "—"
    bw = "、".join(f"{k.replace('#minecraft:','#mc:').replace('#','')}={v}" for k, v in (i.get("biome_weights") or {}).items()) or "—"
    L.append(f"| **{i.get('display','')}** | `{i['id']}` | {s} | {d} | {bw} |")
L.append("")

L.append("## 表 2 · 全部商品（手写定价）\n")
L.append("| 商品 | id | 基础价 | 买入 | 卖出 | 村库存 | 驿站库存 | 产于 | 收购于 |")
L.append("|---|---|---|---|---|---|---|---|---|")
rows = []
for item, base in sorted(values.items(), key=lambda kv: (kv[1], kv[0])):
    name = zh(item)
    rows.append((name, item, round(base,3), round(base*eco["buyBase"],3), round(base*eco["sellBase"],3), cap(base), cap(base, True),
                 "、".join(ind_name(x) for x in sup.get(item, [])) or "—",
                 "、".join(ind_name(x) for x in dem.get(item, [])) or "—"))
    L.append(f"| {name} | `{item.replace('minecraft:','')}` | {base:g} | {base*eco['buyBase']:.2f} | "
             f"{base*eco['sellBase']:.2f} | {cap(base)} | {cap(base, True)} | "
             f"{rows[-1][7]} | {rows[-1][8]} |")
L.append("")

L.append("## 表 3 · 标签基价（模组物品按类定价）\n")
L.append("| 标签 | 类基价 | 普通/罕见/稀有/史诗 | 被引用 |")
L.append("|---|---|---|---|")
refs = collections.defaultdict(list)
for i in inds:
    for e in i.get("supply", []):
        if e.startswith("#"): refs[e].append(ind_name(i["id"]) + "(产)")
    for e in i.get("demand", []):
        if e.startswith("#"): refs[e].append(ind_name(i["id"]) + "(收)")
for tag, base in tagvals.items():
    if tag == "default": continue
    L.append(f"| `{tag}` | {base:g} | {base:g} / {base*2:g} / {base*5:g} / {base*15:g} | {'、'.join(refs.get(tag, [])) or '—'} |")
L.append(f"\n兜底价 `default = {tagvals.get('default', 0.05)}` × 稀有度乘子。\n")

L.append("## 表 4 · 秘宝（只收物，25% 的村有，7 天轮换）\n")
L.append("| 秘宝 | id | 件数 | 需要 | 折价倍率 | 权重 |")
L.append("|---|---|---|---|---|---|")
for r in relics:
    cost = " + ".join(f"{c.get('count') or '按价'}×{zh(c.get('item') or c.get('tag',''))}" for c in r.get("cost", []))
    L.append(f"| {zh(r['item'])} | `{r.get('id','')}` | {r.get('count',1)} | {cost} | ×{r.get('value_multiplier',1):g} | {r.get('weight',1)} |")
L.append("")

L.append("## 表 5 · 用途大类（决定\"任何村都收\"时的加成）\n")
L.append("| 大类 | 标签 | 吃这个加成的产业 |")
L.append("|---|---|---|")
by_tag = collections.defaultdict(list)
for tag, r in roles.get("tags", {}).items():
    by_tag[r].append(tag)
by_ind = collections.defaultdict(list)
for i, rs in roles.get("industries", {}).items():
    for r in rs: by_ind[r].append(ind_name(i))
for r in roles.get("roles", []):
    L.append(f"| {role_name(r)} `{r}` | {'、'.join('`'+t+'`' for t in by_tag.get(r, [])) or '—'} | {'、'.join(by_ind.get(r, [])) or '—'} |")
L.append(f"\n另有 {len(roles.get('keywords', {}))} 条关键词兜底规则（标签认不出的物品按 id 关键字归类）。\n")

os.makedirs(OUTDIR, exist_ok=True)
open(MD, "w", encoding="utf-8").write("\n".join(L))
with open(CSV, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(["中文名", "id", "基础价", "买入价", "卖出价", "村库存上限", "驿站库存上限", "产于", "收购于"])
    for r in rows: w.writerow(r)
print("写出:", MD, os.path.getsize(MD), "字节")
print("写出:", CSV, os.path.getsize(CSV), "字节")
