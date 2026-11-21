#!/usr/bin/env python3
"""yarn 1.21.1 -> intermediary 查询。
用法: python3 tools/mapnames.py net/minecraft/entity/passive/VillagerEntity [关键词...]
无关键词则全部列出。
"""
import sys

TINY = "/home/guoke/tools/yarn/out/mappings/mappings.tiny"

def load():
    cls, members, cur = {}, {}, None
    with open(TINY, encoding="utf-8") as f:
        for ln in f:
            ln = ln.rstrip("\n")
            if ln.startswith("c\t"):
                p = ln.split("\t")
                cur = p[3] if len(p) > 3 else None
                if cur:
                    cls[cur] = p[2]
                    members[cur] = []
            elif cur and ln.startswith("\t") and len(ln.split("\t")) >= 6:
                p = ln.split("\t")
                members[cur].append((p[1], p[5], p[4], p[2]))  # kind, named, intermediary, desc
    return cls, members

def main():
    cls, members = load()
    filt = sys.argv[2:]
    for name in sys.argv[1].split(","):
        name = name.strip()
        if name not in cls:
            print("!! not found:", name, file=sys.stderr); continue
        print("CLASS", name, "->", cls[name])
        for kind, named, inter, desc in members[name]:
            if filt and not any(k.lower() in named.lower() for k in filt):
                continue
            print("   ", kind, named, "->", inter, desc)

if __name__ == "__main__":
    main()
