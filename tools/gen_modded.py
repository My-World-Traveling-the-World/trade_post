#!/usr/bin/env python3
# 离线扫出整合包里哪些模组物品会进贸易：解析所有 jar 的 item 标签，按 tag_values/roles 归类
import json, glob, os, zipfile, collections

DEV = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev"
CFG = DEV + "/config/tradepost/market"
OUT = "/home/guoke/mc-linux/tradepost/docs/market-modded-zh.md"
VANILLA_LANG = "/home/guoke/mc-linux/assets/objects/f8/f87510f4509890eaf176e0de1430f6bb326a6800"
T = chr(96)

jars = glob.glob(DEV + "/mods/*.jar") + [DEV + "/.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar"]
raw_tags = collections.defaultdict(set)
existing = set()
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
        elif n.startswith("assets/") and "/models/item/" in n and n.endswith(".json"):
            parts = n.split("/")
            ns = parts[1]; sub = "/".join(parts[4:-1]); name = parts[-1][:-5]
            existing.add(ns + ":" + sub + "/" + name if sub else ns + ":" + name)
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
    seen = seen or set()
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
roles = json.load(open(CFG + "/roles.json", encoding="utf-8"))
inds = []
for p in sorted(glob.glob(CFG + "/industries/*.json")):
    try:
        inds.append(json.load(open(p, encoding="utf-8")))
    except Exception:
        pass

used = []
def price_of(key):
    return tagvals.get("#" + key, tagvals.get("default", 0.05))
order = []
for t in tagvals:
    if t != "default":
        order.append((t.lstrip("#"), "标签基价表"))
for t in roles.get("tags", {}):
    order.append((t.lstrip("#"), "用途大类"))
for i in inds:
    for e in (i.get("supply", []) + i.get("demand", [])):
        if e.startswith("#"):
            order.append((e.lstrip("#"), "产业引用"))
seen_tag = set()
used = []
for t, why in order:
    if t in seen_tag:
        continue
    seen_tag.add(t)
    used.append((t, price_of(t), why))

rows = []
for tag, base, why in used:
    items = [x for x in dict.fromkeys(expand(tag)) if not x.startswith("#")]
    modded = [x for x in items if not x.startswith("minecraft:")]
    if not items:
        continue
    rows.append((tag, base, why, items, modded))

uniq = set()
for r in rows:
    uniq.update(r[4])

# —— 黑名单：和游戏里同一份 config/tradepost/market/blacklist.json ——
ban = json.load(open(CFG + "/blacklist.json", encoding="utf-8"))
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

kept = {i for i in uniq if not ban_reason(i)}
banned = {i for i in uniq if ban_reason(i)}

L = []
L.append("# 整合包里的模组物品 · 哪些会进贸易\n")
L.append("- 扫描：" + str(len(jars)) + " 个 jar（mods 目录 + 原版 remapped jar）")
L.append("- item 标签：" + str(len(raw_tags)) + " 个；有模型文件（约等于已注册物品）：" + str(len(existing)) + " 个")
L.append("- 被贸易规则引用的标签：" + str(len(rows)) + " 个；去重后命中模组物品 " + str(len(uniq)) + " 个，其中 " + str(len(kept)) + " 个可交易、" + str(len(banned)) + " 个被黑名单拦掉")
L.append("- 价格 = 该标签在 tag_values.json 的基价，再乘稀有度（普通x1 / 罕见x2 / 稀有x5 / 史诗x15）")
L.append("- 稀有度离线读不到；真实价请进游戏跑 /tradepost dump，生成 config/tradepost/items.generated.json")
L.append("- 黑名单文件：config/tradepost/market/blacklist.json（改完 /tradepost reload 生效；values.json 里手写价的物品不受影响）\n")

for tag, base, why, items, modded in sorted(rows, key=lambda r: -len(r[4])):
    modded = [x for x in modded if not ban_reason(x)]
    if not modded:
        continue
    L.append("## " + T + tag + T + " · 基价 " + format(base, "g") + " · " + str(len(modded)) + " 个模组物品（" + why + "）\n")
    L.append("| 中文名 | 物品 id | 基价 |")
    L.append("|---|---|---|")
    for it in sorted(modded):
        L.append("| " + (zh(it) or "（缺中文名）") + " | " + T + it + T + " | " + format(base, "g") + " |")
    L.append("")


L.append("## 被黑名单拦掉的 " + str(len(banned)) + " 个物品\n")
reasons = {}
for it in sorted(banned):
    reasons.setdefault(ban_reason(it), []).append(it)
for why2 in sorted(reasons, key=lambda k: -len(reasons[k])):
    L.append("### " + why2 + "（" + str(len(reasons[why2])) + " 个）")
    L.append("、".join(T + x + T for x in reasons[why2][:40]) + ("…" if len(reasons[why2]) > 40 else ""))
    L.append("")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf-8").write("\n".join(L))
print("标签:", len(raw_tags), " 有模型物品:", len(existing))
print("引用标签:", len(rows), " 去重模组物品:", len(uniq), " 带模组物品的标签:", sum(1 for r in rows if r[4]))
print("可交易:", len(kept), " 拉黑:", len(banned))
print("写出:", OUT, os.path.getsize(OUT), "字节")
for it in sorted(uniq)[:12]:
    print("  ", it, "->", zh(it) or "?")