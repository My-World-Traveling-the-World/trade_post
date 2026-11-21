package dev.guoke.tradepost.mc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.class_1923;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_3195;
import net.minecraft.class_3218;
import net.minecraft.class_6862;
import net.minecraft.class_6872;
import net.minecraft.class_6874;
import net.minecraft.class_7059;
import net.minecraft.class_7924;

/**
 * 只靠世界种子 + 结构放置参数（spacing / separation / salt）推演村庄起点区块。
 *
 * 不加载区块、不生成地形、不读存档：RandomSpreadStructurePlacement.getStartChunk 是纯哈希运算。
 * structurify 的全局间距修改 mixin 打在 spacing/separation 的 getter 与起始区块计算上，
 * 所以我们调用同一批方法时拿到的是"改过之后"的值，与真实生成结果一致。
 *
 * 局限：这里只判断"起点区块落在哪"，不判断生物群系是否合法、也不管结构 set 内部的加权选择，
 * 所以是"候选"。要精确还得配合 getStructureStart；对弱预报来说候选已经够用。
 */
public final class VillageRadar {

    private VillageRadar() {
    }

    private static final class_6862<class_3195> VILLAGE_TAG =
            class_6862.method_40092(class_7924.field_41246, class_2960.method_60654("minecraft:village"));

    /** 一个候选村庄起点。 */
    public static final class Hit {
        public final int chunkX;
        public final int chunkZ;
        public final int blockX;
        public final int blockZ;
        public final int spacing;
        public final String setId;
        /** 这个结构集里包含的村庄结构（用来做群系合法性判断）。 */
        public final java.util.List<class_3195> structures;

        Hit(int chunkX, int chunkZ, int spacing, String setId, java.util.List<class_3195> structures) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = chunkX * 16 + 8;
            this.blockZ = chunkZ * 16 + 8;
            this.spacing = spacing;
            this.setId = setId;
            this.structures = structures;
        }
    }

    /** 扫描结果统计，方便命令层打印。 */
    public static final class Result {
        public final List<Hit> hits = new ArrayList<>();
        public int villageSets;
        public int spreadSets;
        public int skippedSets;
    }

    public static Result scan(class_3218 world, int centerChunkX, int centerChunkZ, int radiusChunks) {
        Result result = new Result();
        Set<Long> seen = new LinkedHashSet<>();
        long seed = world.method_8412();
        int radius = Math.max(1, Math.min(radiusChunks, 512));

        class_2378<class_7059> sets;
        try {
            sets = world.method_30349().method_30530(class_7924.field_41248);
        } catch (Throwable t) {
            return result;
        }
        if (sets == null) {
            return result;
        }

        sets.method_10220().forEach(set -> {
            if (set == null) {
                return;
            }
            try {
                if (!isVillageSet(set)) {
                    return;
                }
                result.villageSets++;
                java.util.List<class_3195> structs = new java.util.ArrayList<>();
                for (class_7059.class_7060 entry : set.comp_510()) {
                    if (entry != null) {
                        structs.add(entry.comp_512().comp_349());
                    }
                }
                class_6874 placement = set.comp_511();
                if (!(placement instanceof class_6872)) {
                    result.skippedSets++;
                    return;
                }
                class_6872 spread = (class_6872) placement;
                int spacing = spread.method_41632();
                if (spacing <= 0) {
                    result.skippedSets++;
                    return;
                }
                result.spreadSets++;
                String id = String.valueOf(sets.method_10221(set));
                int rx0 = Math.floorDiv(centerChunkX - radius, spacing);
                int rx1 = Math.floorDiv(centerChunkX + radius, spacing);
                int rz0 = Math.floorDiv(centerChunkZ - radius, spacing);
                int rz1 = Math.floorDiv(centerChunkZ + radius, spacing);
                for (int rx = rx0; rx <= rx1; rx++) {
                    for (int rz = rz0; rz <= rz1; rz++) {
                        class_1923 start = spread.method_40169(seed, rx * spacing, rz * spacing);
                        if (start == null) {
                            continue;
                        }
                        int cx = start.field_9181;
                        int cz = start.field_9180;
                        if (Math.abs(cx - centerChunkX) > radius || Math.abs(cz - centerChunkZ) > radius) {
                            continue;
                        }
                        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                        if (!seen.add(key)) {
                            continue;
                        }
                        result.hits.add(new Hit(cx, cz, spacing, id, structs));
                    }
                }
            } catch (Throwable ignored) {
                // 单个 set 出问题不影响其它
            }
        });
        return result;
    }

    private static boolean isVillageSet(class_7059 set) {
        for (class_7059.class_7060 entry : set.comp_510()) {
            if (entry == null) {
                continue;
            }
            if (entry.comp_512().method_40220(VILLAGE_TAG)) {
                return true;
            }
        }
        return false;
    }
}
