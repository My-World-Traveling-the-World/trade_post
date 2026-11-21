#!/usr/bin/env python3
# 离线复算「全部物品」的真实价格，规则与游戏内 RecipePricer + Cuisine + Economy 一致
import json, glob, zipfile, os, collections, csv, re

T = chr(96)
DEV = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev"
CFG = DEV + "/config/tradepost/market"
OUT_MD = "/home/guoke/mc-linux/tradepost/docs/market-full-zh.md"
OUT_CSV = "/home/guoke/mc-linux/tradepost/docs/market-full-zh.csv"
VANILLA_LANG = "/home/guoke/mc-linux/assets/objects/f8/f87510f4509890eaf176e0de1430f6bb326a6800"
jars = glob.glob(DEV + "/mods/*.jar") + [DEV + "/.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar"]

raw_tags = collections.defaultdict(set)
items = set()
lang = {}
for p in jars:
    try:
        z = zipfile.ZipFile(p)
    except Exception:
        continue
    for n in z.namelist():
        if not n.startswith("assets/") and not n.startswith("data/"):
            continue
        if n.startswith("assets/") and "/models/item/" in n and n.endswith(".json"):
            parts = n.split("/")
            iid = parts[1] + ":" + "/".join(parts[4:-1] + [parts[-1][:-5]])
            items.add(iid)
        elif n.startswith("data/") and "/tags/item/" in n and n.endswith(".json"):
            parts = n.split("/")
            tid = parts[1] + ":" + "/".join(parts[4:-1] + [parts[-1][:-5]])
            try:
                d = json.loads(z.read(n).decode("utf-8"))
            except Exception:
                continue
            for v in (d.get("values") or []):
                if isinstance(v, str):
                    raw_tags[tid].add(v)
                elif isinstance(v, dict) and "id" in v:
                    raw_tags[tid].add(v["id"])
        elif n.startswith("assets/") and n.endswith("/lang/zh_cn.json"):
            try:
                for k, v in json.loads(z.read(n).decode("utf-8")).items():
                    if isinstance(v, str):
                        lang[k] = v
            except Exception:
                pass
try:
    for k, v in json.loads(open(VANILLA_LANG, encoding="utf-8").read()).items():
        if isinstance(v, str):
            lang.setdefault(k, v)
except Exception:
    pass

def zh(iid):
    ns, _, path = iid.partition(":")
    for k in ("item." + ns + "." + path, "block." + ns + "." + path):
        if k in lang:
            return lang[k]
    return ""

def expand(tag, seen=None):
    seen = seen if seen is not None else set()
    if tag in seen:
        return []
    seen.add(tag)
    out = []
    for v in raw_tags.get(tag, ()):
        if v.startswith("#"):
            out += expand(v[1:], seen)
        else:
            out.append(v)
    return out

eco = json.load(open(CFG + "/economy.json", encoding="utf-8"))
values = {k: float(v) for k, v in json.load(open(CFG + "/values.json", encoding="utf-8")).items() if isinstance(v, (int, float))}
tagvals = json.load(open(CFG + "/tag_values.json", encoding="utf-8"))
ban = json.load(open(CFG + "/blacklist.json", encoding="utf-8"))
BUY = float(eco.get("buyBase", 0.85)); SELL = float(eco.get("sellBase", 1.18))
FOODMUL = float(eco.get("foodMultiplier", 4.0)); CRAFT = float(eco.get("craftPenalty", 0.65))
FALLBACK = float(tagvals.get("default", 0.05))

# —— Cuisine 镜像 ——
CUISINE_NS = set("""kaleidoscope_cookery kaleidoscope_tavern farmersdelight ends_delight compatdelight
ubesdelight brewinandchewin vinery brewery candlelight delightful miners_delight culturaldelights
oceansdelight nethersdelight corn_delight cavedelight rusticdelight expandeddelight""".split())
TIERS = [
    ("酒水", 2.2, 4.0, "wine beer ale cider mead sake soju whisky whiskey vodka rum brandy cocktail liquor champagne cognac tequila gin_ moonshine schnapps liqueur".split()),
    ("陈酿", 3.0, 12.0, "aged vintage reserve grand premium masterwork legendary century".split()),
    ("宴席", 2.6, 8.0, "feast banquet platter tower deluxe royal golden dragon stuffed whole_".split()),
    ("精致料理", 2.0, 3.5, "soup stew broth curry noodle ramen dumpling roast steak pie cake pudding salad skewer sandwich burger sushi tempura hotpot casserole gratin".split()),
    ("家常菜", 1.6, 1.8, "cooked baked fried grilled smoked roasted steamed braised stirfried stir_fried seasoned pickled juice tea coffee smoothie jam honey_bread toast porridge".split()),
]
SLOT_BONUS = 0.20; ING_BONUS = 0.35; RARE = 6.0; CUISINE_MAX = 3.2

foods = set(expand("c:foods")) | set(expand("c:foods/raw_fish")) | set(expand("c:foods/vegetable")) \
        | set(expand("c:foods/fruit")) | set(expand("c:meat")) | set(expand("c:crops"))

def tier_of(iid):
    path = iid.split(":")[-1].lower()
    best = None
    for name, mult, floor, kws in TIERS:
        for k in kws:
            if k in path:
                if best is None or mult > best[1]:
                    best = (name, mult, floor)
                break
    return best

BLOCKISH = ("crate","bag","sack","block","plank","sign","slab","stair","fence","wall","door",
            "trapdoor","table","chair","stool","seat","painting","lamp","lantern","cabinet","shelf",
            "box","pan","knife","board","counter","basket","carpet","window","roof","pillar","beam",
            "stove","oven","plate","curtain","potted","candle","bucket","mug","glassware")

def is_dish(iid):
    ns, _, path = iid.partition(":")
    pl = path.lower()
    if any(k in pl for k in BLOCKISH):
        return False
    if ns in CUISINE_NS:
        return True
    if iid in foods:
        return tier_of(iid) is not None
    return False

def dish_bonus(iid, slots, price_of):
    t = tier_of(iid)
    mult = t[1] if t else 1.0
    floor = t[2] if t else 0.0
    b = mult
    if len(slots) > 2:
        b += SLOT_BONUS * (len(slots) - 2)
    top = 0.0
    for s in slots:
        for x in s:
            v = price_of(x)
            if v > top:
                top = v
    if top >= RARE:
        b += ING_BONUS
    return min(CUISINE_MAX, max(1.0, b)), floor

# —— 黑名单 ——
ban_tags = set(t.lstrip("#") for t in ban.get("tags", []))
ban_items = set(ban.get("items", []))
ban_kw = [k.lower() for k in ban.get("keywords", [])]
ban_ns = set(ban.get("namespaces", []))
banned_by_tag = set()
for t in ban_tags:
    banned_by_tag.update(expand(t))
ban_rare = bool(ban.get("banRareUnpriced", True))

def ban_reason(iid):
    if iid in ban_items: return "名单"
    ns, _, p = iid.partition(":")
    if ns in ban_ns: return "整模组"
    if iid in banned_by_tag: return "标签"
    pl = p.lower()
    for k in ban_kw:
        if k in pl: return "关键词 " + k
    return ""

# —— 权威价：手写 > 标签（含食物 ×4）——
derived = {}
for t, p in tagvals.items():
    if not t.startswith("#"):
        continue
    for i in expand(t[1:]):
        derived.setdefault(i, float(p) * (FOODMUL if i in foods else 1.0))

def authoritative(iid):
    if iid in values:
        return values[iid]
    return derived.get(iid)

# —— 配方 ——
recs = json.load(open("/tmp/demo/recs.json", encoding="utf-8"))["recs"]

def price_of(iid, calc):
    a = authoritative(iid)
    if a:
        return a
    c = calc.get(iid)
    if c and c > 0:
        return c
    return FALLBACK

calc = {}
for _ in range(4):
    for r in recs:
        out = r["o"]; cnt = max(1, r.get("c", 1)); slots = r.get("s") or []
        if not slots or out in values or out in derived:
            continue
        tot = 0.0; ok = True
        for s in slots:
            vals = []
            for x in s:
                if x.startswith("#"):
                    vals += [price_of(y, calc) for y in expand(x[1:])]
                else:
                    vals.append(price_of(x, calc))
            vals = [v for v in vals if v > 0]
            if not vals:
                ok = False
                break
            tot += min(vals)
        if not ok or tot <= 0:
            continue
        cand = tot / cnt
        if is_dish(out):
            b, floor = dish_bonus(out, slots, lambda y: price_of(y, calc))
            cand *= b
            if floor > cand:
                cand = floor
        else:
            cand *= CRAFT
        if out not in calc or cand < calc[out]:
            calc[out] = cand

# —— 汇总全表 ——
rows = []
for iid in sorted(items):
    reason = ban_reason(iid)
    base = values.get(iid)
    if base is not None:
        src = "手写"
    elif iid in derived:
        src = "标签"
        base = derived[iid]
    elif iid in calc and not reason:
        src = "配方"
        base = calc[iid]
    else:
        src = "兜底" if not reason else "黑名单"
        base = FALLBACK if not reason else 0.0
    if reason and base is None:
        base = 0.0
    rows.append((iid, base, src, reason, iid in foods, is_dish(iid), zh(iid)))

def cap_of(base, station):
    if base <= 0:
        return 0
    c = max(16, min(2048, int(round(512.0 / max(0.05, base)))))
    return min(4096, c * 2) if station else c

rows.sort(key=lambda r: (-r[1], r[0]))
cnt = collections.Counter(r[2] for r in rows)

lines = []
lines.append("# 跑商 · 全部物品价格表（离线复算，与游戏同一套规则）")
lines.append("")
lines.append("- 物品 " + str(len(rows)) + " 个（= 整合包里所有有模型的物品 id）")
lines.append("- 价格来源：手写 " + str(cnt["手写"]) + " ｜ 标签 " + str(cnt["标签"]) + " ｜ 配方 " + str(cnt["配方"])
             + " ｜ 兜底 " + str(cnt["兜底"]) + " ｜ 黑名单（不收）" + str(cnt["黑名单"]))
lines.append("- 规则：手写价 > 标签价（食物 ×" + str(FOODMUL) + "）> 配方价（工业品 ×" + str(CRAFT) + "，菜酒按等级溢价 + 保底价）> 兜底 " + str(FALLBACK))
lines.append("- 库存：clamp(round(512/基础价),16,2048)，驿站 ×2；模组食物（无手写价的可食用物品）一律 64")
lines.append("- 与游戏内 " + T + "/tradepost dumpall" + T + " 导出的 CSV 对齐；差别只在稀有度（离线按普通 ×1 算）")
lines.append("")
lines.append("| 分类 | 数量 |")
lines.append("|---|---|")
for k in ("手写", "标签", "配方", "兜底", "黑名单"):
    lines.append("| " + k + " | " + str(cnt[k]) + " |")
lines.append("| **合计** | **" + str(len(rows)) + "** |")
lines.append("")
lines.append("## 全部物品（按基础价从高到低）")
lines.append("")
lines.append("| 中文名 | 物品 id | 基础价 | 买入 | 卖出 | 村库存 | 来源 | 食物 | 菜/酒 | 备注 |")
lines.append("|---|---|---|---|---|---|---|---|---|---|")
for iid, base, src, reason, food, dish, name in rows:
    cv = cap_of(base, False)
    if food and iid not in values and cv:
        cv = min(cv, 64)
    lines.append("| " + (name or "?") + " | " + T + iid + T + " | " + format(round(base, 3), "g")
                 + " | " + (format(round(base * BUY, 3), "g") if base else "-")
                 + " | " + (format(round(base * SELL, 3), "g") if base else "-")
                 + " | " + (str(cv) if cv else "-") + " | " + src
                 + " | " + ("是" if food else "") + " | " + ("是" if dish else "")
                 + " | " + (("黑名单：" + reason) if reason else "") + " |")
lines.append("")
open(OUT_MD, "w", encoding="utf-8").write("\n".join(lines))

with open(OUT_CSV, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(["中文名", "物品id", "基础价", "买入", "卖出", "村庄库存", "驿站库存", "价格来源", "可食用", "菜酒", "备注"])
    for iid, base, src, reason, food, dish, name in rows:
        cv = cap_of(base, False); sv = cap_of(base, True)
        if food and iid not in values:
            cv = min(cv, 64) if cv else 0
            sv = min(sv, 64) if sv else 0
        w.writerow([name or "?", iid, round(base, 3), round(base * BUY, 3) if base else "", round(base * SELL, 3) if base else "",
                    cv, sv, src, "是" if food else "", "是" if dish else "", ("黑名单：" + reason) if reason else ""])

print("物品", len(rows), dict(cnt))
print("写出:", OUT_MD, os.path.getsize(OUT_MD))
print("写出:", OUT_CSV, os.path.getsize(OUT_CSV))
