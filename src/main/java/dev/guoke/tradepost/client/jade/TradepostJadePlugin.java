package dev.guoke.tradepost.client.jade;

import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** 给 Jade 挂一条钟的提示：蹲下右键开集市。只在装了 Jade 的客户端加载。 */
@WailaPlugin("tradepost")
public class TradepostJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerBlockComponent(new BellHint(), class_2248.class);
    }

    public static class BellHint implements IBlockComponentProvider {

        private static final class_2960 UID = class_2960.method_60655("tradepost", "bell_hint");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            // 运行时类名是 class_2575（中间名），不能靠 "BellBlock" 判断；
            // BlockState.toString() 里带 "minecraft:bell"，用它最稳
            try {
                String s = String.valueOf(accessor.getBlockState());
                if (!s.contains("minecraft:bell")) return;
            } catch (Throwable t) {
                return;
            }
            tooltip.add(class_2561.method_43470("处于村庄时，蹲下并右键打开该村庄的市场"));
        }

        @Override
        public class_2960 getUid() {
            return UID;
        }
    }
}