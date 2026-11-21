#!/usr/bin/env python3
# 生成驿站结构 nbt（普通/沙漠/雪地三套）+ lithostitched 注入配置
import gzip, io, json, os, struct, zipfile, glob
from collections import Counter

ROOT = "/home/guoke/mc-linux/tradepost"
RES = os.path.join(ROOT, "src/main/resources/data/tradepost")
OUT_MODDIR = os.path.join(RES, "lithostitched/worldgen_modifier/post_station")
MODS = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/mods"
VJAR = "/home/guoke/mc-linux/versions/WAYFARING1.7.0-dev/WAYFARING1.7.0-dev.jar"
DATA_VERSION = 3955
TARGET_P = 0.15   # 每栋房子槽位出现驿站的概率

# ---------------- NBT 写入 ----------------
class T:
    def __init__(s, tag, val): s.tag, s.val = tag, val
def Byte(v):  return T(1, v)
def Short(v): return T(2, v)
def Int(v):   return T(3, v)
def Long(v):  return T(4, v)
def Float(v): return T(5, v)
def Double(v):return T(6, v)
def Str(v):   return T(8, v)
def IntArray(v): return T(11, v)
class L:
    def __init__(s, etype, items): s.etype, s.items = etype, items
def StrList(v): return L(8, v)
def CompList(v): return L(10, v)
def IntList(v): return L(3, v)

def _wstr(out, s):
    b = s.encode("utf-8"); out.write(struct.pack(">H", len(b))); out.write(b)

def _payload(out, tag, val):
    if tag == 1: out.write(struct.pack(">b", val))
    elif tag == 2: out.write(struct.pack(">h", val))
    elif tag == 3: out.write(struct.pack(">i", val))
    elif tag == 4: out.write(struct.pack(">q", val))
    elif tag == 5: out.write(struct.pack(">f", val))
    elif tag == 6: out.write(struct.pack(">d", val))
    elif tag == 8: _wstr(out, val)
    elif tag == 10:
        for k, v in val.items(): _write_tag(out, k, v)
        out.write(b"\x00")
    elif tag == 11:
        out.write(struct.pack(">i", len(val)))
        for x in val: out.write(struct.pack(">i", x))
    elif tag == 12:
        out.write(struct.pack(">i", len(val)))
        for x in val: out.write(struct.pack(">q", x))
    else:
        raise ValueError("tag %d" % tag)

def _write_tag(out, name, v):
    if isinstance(v, L):
        out.write(bytes([9])); _wstr(out, name); out.write(bytes([v.etype]))
        out.write(struct.pack(">i", len(v.items)))
        for it in v.items:
            if v.etype == 10: _payload(out, 10, it)
            elif v.etype == 3: out.write(struct.pack(">i", it))
            elif v.etype == 8: _wstr(out, it)
            else: raise ValueError(v.etype)
    elif isinstance(v, T):
        out.write(bytes([v.tag])); _wstr(out, name); _payload(out, v.tag, v.val)
    elif isinstance(v, dict):
        out.write(bytes([10])); _wstr(out, name); _payload(out, 10, v)
    elif isinstance(v, str):
        out.write(bytes([8])); _wstr(out, name); _payload(out, 8, v)
    elif isinstance(v, bool):
        out.write(bytes([1])); _wstr(out, name); _payload(out, 1, 1 if v else 0)
    elif isinstance(v, int):
        out.write(bytes([3])); _wstr(out, name); _payload(out, 3, v)
    else:
        raise TypeError(type(v))

def write_nbt(root, path):
    buf = io.BytesIO()
    buf.write(b"\x0a"); _wstr(buf, "")
    _payload(buf, 10, root)
    data = gzip.compress(buf.getvalue(), 9)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f: f.write(data)
    return len(data)

# ---------------- 驿站结构 ----------------
W = 9; H = 6; D = 9     # x 0..8, y 0..5, z 0..8

# 三套风格：不同村落用不同材质，形状完全一致（同一套拼图接口）
MATERIALS = {
    "normal": dict(
        base="minecraft:stone_bricks", floor="minecraft:spruce_planks",
        wall="minecraft:spruce_planks", pillar="minecraft:spruce_log",
        roof_rim="minecraft:stone_bricks", roof_inner="minecraft:spruce_planks",
        light="minecraft:sea_lantern", carpet="minecraft:red_carpet",
        barrel="minecraft:barrel", table="minecraft:crafting_table",
    ),
    "desert": dict(
        base="minecraft:cut_sandstone", floor="minecraft:smooth_sandstone",
        wall="minecraft:sandstone", pillar="minecraft:chiseled_sandstone",
        roof_rim="minecraft:cut_sandstone", roof_inner="minecraft:smooth_sandstone",
        light="minecraft:lantern", carpet="minecraft:orange_carpet",
        barrel="minecraft:barrel", table="minecraft:crafting_table",
    ),
    "snowy": dict(
        base="minecraft:cobblestone", floor="minecraft:spruce_planks",
        wall="minecraft:spruce_planks", pillar="minecraft:stripped_spruce_log",
        roof_rim="minecraft:snow_block", roof_inner="minecraft:spruce_planks",
        light="minecraft:lantern", carpet="minecraft:light_blue_carpet",
        barrel="minecraft:barrel", table="minecraft:crafting_table",
    ),
}

def build_station(style, out_path):
    m = MATERIALS[style]
    blocks = {}
    def put(x, y, z, name, props=None, nbt=None):
        blocks[(x, y, z)] = (name, props, nbt)
    def border(x, z):
        return x in (0, W-1) or z in (0, D-1)

    # 地面 y=0
    for x in range(W):
        for z in range(D):
            put(x, 0, z, m["base"] if border(x, z) else m["floor"])

    # 墙体 y=1..3（四角立柱）
    for y in (1, 2, 3):
        for x in range(W):
            for z in range(D):
                if not border(x, z): continue
                corner = (x, z) in {(0,0),(W-1,0),(0,D-1),(W-1,D-1)}
                if corner: put(x, y, z, m["pillar"], {"axis": "y"} if style != "desert" else None)
                elif y == 1: put(x, y, z, m["base"])
                else: put(x, y, z, m["wall"])

    # 窗（y=3，玻璃板）
    for x in (2, 4, 6):
        put(x, 3, 0, "minecraft:glass_pane")
        put(x, 3, D-1, "minecraft:glass_pane")
    for z in (2, 4, 6):
        put(W-1, 3, z, "minecraft:glass_pane")
    for z in (2, 6):
        put(0, 3, z, "minecraft:glass_pane")

    # 门洞（西墙 z=4，高 2）—— y=1 是入口拼图块，y=2 留空
    blocks.pop((0, 2, 4), None)

    # y=4 顶圈 + 室内天花板 + 灯
    for x in range(W):
        for z in range(D):
            if border(x, z): put(x, 4, z, m["pillar"], {"axis": "y"} if style != "desert" else None)
            else: put(x, 4, z, m["wall"])
    put(2, 4, 2, m["light"])
    put(6, 4, 6, m["light"])

    # y=5 屋顶
    for x in range(W):
        for z in range(D):
            put(x, 5, z, m["roof_rim"] if border(x, z) else m["roof_inner"])

    # 家具
    # 这个箱子的战利品表字段就是"此村是驿站"的标记，代码靠它识别（Bridge.stationNear）
    put(1, 1, 1, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
        {"LootTable": Str("tradepost:post_station")})
    put(2, 1, 1, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"})
    put(7, 1, 1, m["barrel"], {"facing": "up", "open": "false"})
    put(1, 1, 7, m["table"])
    put(7, 1, 6, "minecraft:bookshelf")
    put(7, 1, 7, "minecraft:bookshelf")
    put(4, 1, 7, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    put(4, 1, 4, m["carpet"])

    # 拼图块：入口（西墙）
    put(0, 1, 4, "minecraft:jigsaw", {"orientation": "west_up"}, {
        "joint": "aligned",
        "name": "minecraft:building_entrance",
        "pool": "minecraft:empty",
        "target": "minecraft:building_entrance",
        "final_state": "minecraft:structure_void",
    })
    # 拼图块：屋内地面，用来刷一个村民
    put(2, 0, 2, "minecraft:jigsaw", {"orientation": "up_north"}, {
        "joint": "rollable",
        "name": "minecraft:bottom",
        "pool": "minecraft:village/plains/villagers",
        "target": "minecraft:bottom",
        "final_state": m["floor"],
    })

    # 组装 palette / blocks
    palette = []
    pal_index = {}
    def key_of(name, props):
        if props:
            return name + "|" + "|".join("%s=%s" % (k, props[k]) for k in sorted(props))
        return name

    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        name, props, nbt = blocks[pos]
        k = key_of(name, props)
        if k not in pal_index:
            e = {"Name": name}
            if props: e["Properties"] = dict(props)
            pal_index[k] = len(palette)
            palette.append(e)

    block_list = []
    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        x, y, z = pos
        name, props, nbt = blocks[pos]
        entry = {"pos": IntArray([x, y, z]), "state": Int(pal_index[key_of(name, props)])}
        if nbt: entry["nbt"] = nbt
        block_list.append(entry)

    root = {
        "size": IntList([W, H, D]),
        "entities": CompList([]),
        "blocks": CompList(block_list),
        "palette": CompList(palette),
        "DataVersion": Int(DATA_VERSION),
    }
    size = write_nbt(root, out_path)
    print("[结构] %-58s %5d 字节  palette=%2d  blocks=%d" % (os.path.relpath(out_path, ROOT), size, len(palette), len(block_list)))

for style in MATERIALS:
    name = "post_station.nbt" if style == "normal" else "post_station_%s.nbt" % style
    build_station(style, os.path.join(RES, "structure", name))

def structure_id_for(pool_id):
    """按池名挑风格：沙漠/红土/热带草原 → 沙漠版；雪地/寒带 → 雪地版；其余普通版。"""
    path = pool_id.lower()
    if any(k in path for k in ("desert", "badlands", "mesa", "savanna", "acacia", "sandstone", "red_rock")):
        return "tradepost:post_station_desert"
    if any(k in path for k in ("snow", "frozen", "taiga", "cold", "ice", "grove")):
        return "tradepost:post_station_snowy"
    return "tradepost:post_station"

# ---------------- 扫描所有房屋池 ----------------
pool_json = {}   # pool id -> (json dict, jar)
jars = sorted(glob.glob(MODS + "/*.jar")) + [VJAR]
for j in jars:
    try:
        z = zipfile.ZipFile(j); names = z.namelist()
    except Exception:
        continue
    for n in names:
        if "worldgen/template_pool/" in n and n.endswith(".json"):
            parts = n.split("/")
            if len(parts) < 6: continue
            pid = parts[1] + ":" + "/".join(parts[4:])[:-5]
            if pid in pool_json: continue
            try:
                pool_json[pid] = (json.loads(z.read(n)), os.path.basename(j))
            except Exception:
                pass
    z.close()
print("[扫描] 模板池总数 %d" % len(pool_json))

def is_house_pool(pid):
    ns, path = pid.split(":", 1)
    if "village" not in pid: return False
    if "zombie" in path: return False
    if "layer" in path: return False          # natures_spirit adobe 分层池，先跳过
    if "terminator" in path: return False
    if path.startswith(("base/a", "base/-")): return False   # lios 编号池，先跳过
    low = path.lower()
    return low.endswith("/houses") or low.endswith("/house") or "house" in low.split("/")[-1]

selected = sorted([p for p in pool_json if is_house_pool(p)])
print("[筛选] 房屋池 %d 个" % len(selected))

os.makedirs(OUT_MODDIR, exist_ok=True)
for f in glob.glob(OUT_MODDIR + "/*.json"): os.remove(f)

made = []
for pid in selected:
    pj, jar = pool_json[pid]
    total = sum(e.get("weight", 1) for e in pj.get("elements", []))
    if total <= 0: total = 1
    w = max(1, round(total * TARGET_P / (1 - TARGET_P)))
    doc = {
        "type": "lithostitched:add_template_pool_elements",
        "template_pools": pid,
        "elements": [{
            "weight": w,
            "element": {
                "element_type": "minecraft:single_pool_element",
                "projection": "rigid",
                "processors": "minecraft:empty",
                "location": structure_id_for(pid),
            },
        }],
    }
    fn = pid.replace(":", "__").replace("/", "_-_") + ".json"
    with open(os.path.join(OUT_MODDIR, fn), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
    made.append((pid, total, w))

c = Counter(structure_id_for(p).split(":")[1] for p, _, _ in made)
print("[生成] modifier 文件 %d 个，风格分布：%s" % (len(made), dict(c)))
c2 = Counter(p.split(":")[0] for p, _, _ in made)
print("[生成] 命名空间分布：%s" % dict(sorted(c2.items(), key=lambda t: -t[1])))
print("[权重] 最高 %s / 最低 %s" % (max(made, key=lambda t: t[2])[2], min(made, key=lambda t: t[2])[2]))
for pid, total, w in made[:6]:
    print("   %-55s W=%-4d w=%-3d p=%.1f%% -> %s" % (pid, total, w, 100*w/(total+w), structure_id_for(pid)))
print("   ... 共 %d 条" % len(made))
