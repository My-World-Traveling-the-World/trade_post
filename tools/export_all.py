#!/usr/bin/env python3
# 全量价格表：手写价 + 标签价，算出买/卖/库存，输出 md + csv
import json, glob, os, zipfile, collections, csv

T = chr(96)
DEV = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev"
CFG = DEV + "/config/tradepost/market"
OUT_MD = "/home/guoke/mc-linux/tradepost/docs/market-all-zh.md"
OUT_CSV = "/home/guoke/mc-linux/tradepost/docs/market-all-zh.csv"
VANILLA_LANG = "/home/guoke/mc-linux/assets/objects/f8/f87510f4509890eaf176e0de1430f6bb326a6800"

jars = glob.glob(DEV + "/mods/*.jar") + [DEV + "/.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar"]
raw_tags = collections.defaultdict(set)
lang = {}
for p in jars:
    try:
        z = zipfile.ZipFile(p)
    except Exception:
        continue
    for n in z.namelist():
        if n.startswith("data/") and "/tags/item/" in n and n.endswith(".json"):
            parts = n.split("/")
            ns = parts[1]; sub = "/".join(parts[4:-1]); name = parts[-1][:-5]
            tag = ns + ":" + sub + "/" + name if sub else ns + ":" + name
            try:
                d = json.loads(z.read(n).decode("utf-8"))
            except Exception:
                continue
            for v in (d.get("values") or []):
                if isinstance(v, str):
                    raw_tags[tag].add(v)
                elif isinstance(v, dict) and "id" in v:
                    raw_tags[tag].add(v["id"])
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

tagvals = json.load(open(CFG + "/tag_values.json", encoding="utf-8"))
values = json.load(open(CFG + "/values.json", encoding="utf-8"))
ban = json.load(open(CFG + "/blacklist.json", encoding="utf-8"))
roles = json.load(open(CFG + "/roles.json", encoding="utf-8"))
eco = json.load(open(CFG + "/economy.json", encoding="utf-8"))
inds = []
for p in sorted(glob.glob(CFG + "/industries/*.json")):
    try:
        inds.append(json.load(open(p, encoding="utf-8")))
    except Exception:
        pass

ban_tags = set(t.lstrip("#") for t in ban.get("tags", []))
ban_items = set(ban.get("items", []))
ban_kw = [k.lower() for k in ban.get("keywords", [])]
ban_ns = set(ban.get("namespaces", []))
banned_by_tag = set()
for t in ban_tags:
    banned_by_tag.update(expand(t))

def ban_reason(iid):
    if iid in ban_items: return "名单"
    ns, _, p = iid.partition(":")
    if ns in ban_ns: return "整模组"
    if iid in banned_by_tag: return "标签"
    pl = p.lower()
    for k in ban_kw:
        if k in pl: return "关键词 " + k
    return ""

foods = set(expand("c:foods")) | set(expand("c:foods/raw_fish")) | set(expand("c:foods/vegetable")) \
        | set(expand("c:foods/fruit")) | set(expand("c:meat")) | set(expand("c:crops"))

BUY = float(eco.get("buyBase", 0.85))
SELL = float(eco.get("sellBase", 1.18))
FOODMUL = float(eco.get("foodMultiplier", 4.0))

def cap_of(base, station):
    v = max(0.05, base)
    c = int(round(512.0 / v))
    c = max(16, min(2048, c))
    return min(4096, c * 2) if station else c

def price_of_tag(key):
    return float(tagvals.get("#" + key, tagvals.get("default", 0.05)))

src = {}
for t in list(tagvals.keys()) + list(roles.get("tags", {}).keys()):
    if t == "default":
        continue
    k = t.lstrip("#")
    for it in expand(k):
        src.setdefault(it, k)
for i in inds:
    for e in (i.get("supply", []) + i.get("demand", [])):
        if e.startswith("#"):
            for it in expand(e.lstrip("#")):
                src.setdefault(it, e.lstrip("#"))

prod = collections.defaultdict(list)
cons = collections.defaultdict(list)
for i in inds:
    for e in i.get("supply", []):
        if e.startswith("#"):
            for it in expand(e[1:]):
                prod[it].append(i["id"])
        elif e.startswith("@"):
            continue
        else:
            prod[e].append(i["id"])
    for e in i.get("demand", []):
        if e.startswith("#"):
            for it in expand(e[1:]):
                cons[it].append(i["id"])
        elif e.startswith("@"):
            continue
        else:
            cons[e].append(i["id"])

rows = []
for iid, base in values.items():
    if not isinstance(base, (int, float)):
        continue
    rows.append((iid, float(base), "手写价", iid in foods, ""))
for iid, tag in src.items():
    if iid in values or iid.startswith("#"):
        continue
    reason = ban_reason(iid)
    is_food = iid in foods
    base = price_of_tag(tag) * (FOODMUL if is_food else 1.0)
    rows.append((iid, base, "标签 #" + tag, is_food, reason))

rows.sort(key=lambda r: (-r[1], r[0]))

def num(x):
    return format(round(x, 3), "g")

lines = []
lines.append("# 跑商 · 全部商品价格表（中文）")
lines.append("")
lines.append("- 数据来源：" + T + CFG + T + "（外部目录优先，改完 " + T + "/tradepost reload" + T + " 生效）")
lines.append("- 物品 " + str(len(rows)) + " 条：手写价 " + str(len(values)) + " 条 + 标签价 " + str(len(rows) - len(values)) + " 条")
lines.append("- 买入 = 基础价 × " + num(BUY) + "，卖出 = 基础价 × " + num(SELL) + "（半仓、无村癖、无行情、非集市日的中位价）")
lines.append("- 基础价已含：标签基价 × 食物 " + num(FOODMUL) + " 倍（可食用才算）。不含稀有度倍率（普通×1 / 罕见×2 / 稀有×5 / 史诗×15）——稀有度离线读不到，进游戏 " + T + "/tradepost dump" + T + " 才是准的")
lines.append("- 库存上限 = clamp(round(512 / 基础价), 16, 2048)，驿站 ×2；**模组食物（走标签价/配方价、没有手写价的可食用物品）一律压到 64**，原版手写价食物不受影响")
lines.append("- 实际成交价再乘：繁荣度（买 T1 ×1.15 / T3 ×0.92，卖反过来）× 村癖 ±12% × 每日行情 ±10% × 库存曲线（买 0.55–1.45 / 卖 0.68–1.33）× 集市日（卖 ×1.35）")
lines.append("")
lines.append("## 判断口径")
lines.append("")
lines.append("| 结论 | 成交价 ÷ 基础价 |")
lines.append("|---|---|")
lines.append("| 便宜，值得囤 | < 0.6 |")
lines.append("| 正常 | 0.6 – 1.0 |")
lines.append("| 别买 | > 1.1 |")
lines.append("| 卖得好 | > 1.3 |")
lines.append("| 赶紧出手 | > 1.6 |")
lines.append("")
lines.append("## 全部商品（按基础价从高到低）")
lines.append("")
lines.append("| 中文名 | 物品 id | 基础价 | 买入 | 卖出 | 村库存 | 驿库存 | 食物 | 本村产 | 本村收 | 来源 | 备注 |")
lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|")
for iid, base, srcname, is_food, reason in rows:
    cv = cap_of(base, False); sv = cap_of(base, True)
    if is_food and iid not in values:
        cv = min(cv, 64); sv = min(sv, 64)
    lines.append("| " + (zh(iid) or "?") + " | " + T + iid + T
                 + " | " + num(base) + " | " + num(base * BUY) + " | " + num(base * SELL)
                 + " | " + str(cv) + " | " + str(sv)
                 + " | " + ("是" if is_food else "")
                 + " | " + ("、".join(list(dict.fromkeys(prod.get(iid, [])))) or "-")
                 + " | " + ("、".join(list(dict.fromkeys(cons.get(iid, [])))) or "-")
                 + " | " + srcname + " | " + (("黑名单：" + reason) if reason else "") + " |")
lines.append("")

os.makedirs(os.path.dirname(OUT_MD), exist_ok=True)
open(OUT_MD, "w", encoding="utf-8").write("\n".join(lines))

with open(OUT_CSV, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(["中文名", "物品id", "基础价", "买入", "卖出", "村庄库存", "驿站库存", "可食用", "本村产", "本村收", "价格来源", "备注"])
    for iid, base, srcname, is_food, reason in rows:
        cv = cap_of(base, False); sv = cap_of(base, True)
        if is_food and iid not in values:
            cv = min(cv, 64); sv = min(sv, 64)
        w.writerow([zh(iid) or "?", iid, round(base, 3), round(base * BUY, 3), round(base * SELL, 3),
                    cv, sv, "是" if is_food else "", "、".join(list(dict.fromkeys(prod.get(iid, [])))), "、".join(list(dict.fromkeys(cons.get(iid, [])))),
                    srcname, ("黑名单：" + reason) if reason else ""])

print("物品总数", len(rows), "手写价", len(values), "标签价", len(rows) - len(values))
print("写出:", OUT_MD, os.path.getsize(OUT_MD))
print("写出:", OUT_CSV, os.path.getsize(OUT_CSV))
