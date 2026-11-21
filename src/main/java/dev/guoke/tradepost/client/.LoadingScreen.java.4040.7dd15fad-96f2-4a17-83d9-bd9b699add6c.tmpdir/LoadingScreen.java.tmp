package dev.guoke.tradepost.client;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_437;

/**
 * 「正在计算商品价格」的占位界面：点钟的同一刻就能看到反馈，等服务端把价目表算完
 * （{@code MarketPayload}）再被 {@link MarketScreen} 顶掉。
 *
 * <p>算价在这个整合包里可能要几秒（12801 条配方），期间服务端在主线程跑，
 * 客户端不显示任何东西会被当成"没反应"。这里只画一行字 + 呼吸点，不做任何请求。
 */
public class LoadingScreen extends class_437 {

    public LoadingScreen() {
        super(class_2561.method_43470("集市"));
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        // 不画世界模糊背景：只压一层半透明，保持"正在处理"的轻量感
        ctx.method_25294(0, 0, this.field_22789, this.field_22790, 0x66000000);
        class_327 f = class_310.method_1551().field_1772;
        String s = "正在计算商品价格…";
        int w = f.method_1727(s);
        int x = (this.field_22789 - w) / 2;
        int y = this.field_22790 / 2 - 6;
        ctx.method_25294(x - 12, y - 12, x + w + 12, y + 22, 0xCC1E1610);
        ctx.method_25294(x - 12, y - 12, x + w + 12, y - 11, 0xFFE8CE9A);
        ctx.method_51433(f, s, x, y, 0xFFE8CE9A, false);
        String tip = "请稍候";
        String tip2 = "按 Esc 可取消";
        int tw = f.method_1727(tip);
        ctx.method_51433(f, tip, (this.field_22789 - tw) / 2, y + 40, 0xFFA0825A, false);
        int t2 = f.method_1727(tip2);
        ctx.method_51433(f, tip2, (this.field_22789 - t2) / 2, y + 54, 0xFF7A6A52, false);
    }

    @Override
    public boolean method_25421() {
        return false;      // 世界照常 tick，别把服务端算价冻住
    }
}
