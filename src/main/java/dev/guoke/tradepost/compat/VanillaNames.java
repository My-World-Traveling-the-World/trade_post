package dev.guoke.tradepost.compat;

/**
 * 原版（intermediary）方法名集中在这里 —— 升级 Minecraft 时只看这一个文件。
 *
 * <p>为什么值得多这一步：mixin 里的 {@code @Inject(method = "method_20215")} 是一段裸字符串，
 * 没有编译期检查，也没有 IDE 跳转。原版一改名，编译照样过、jar 照样打，进游戏才在启动时
 * 炸出 "was not located in the target class"。把名字收成常量之后，改一处就够，
 * 而且在 {@code docs/UPGRADING.md} 的清单里能对着版本逐条核对。
 *
 * <p>注意：class_ 类名没法做成常量（{@code @Mixin(class_1728.class)} 要的是 Class 字面量），
 * 那份对照表在 {@code docs/INTERMEDIARY.md}。
 */
public final class VanillaNames {

    private VanillaNames() {
    }

    /** {@code class_1725#method_7645} —— 商人库存每 tick 重算天平与成品格（我们的摆钱和点亮都在 TAIL）。 */
    public static final String MERCHANT_INV_TICK = "method_7645";

    /** {@code class_1727#method_7667} —— 从结果格把东西拿走（HEAD 可取消：我们在这里结算与扣货）。 */
    public static final String TRADE_OUTPUT_ON_TAKE = "method_7667";

    /** {@code class_1728#method_20215} —— 点某一行报价（HEAD 可取消：我们在这里自动摆钱）。 */
    public static final String HANDLER_CLICK_OFFER = "method_20215";

    /** {@code class_1728#method_7595} —— 界面关闭（HEAD：把天平/第二格剩下没拿的还回背包）。 */
    public static final String HANDLER_ON_CLOSED = "method_7595";

    /** {@code class_1728#method_20595} —— shift 快速移动之后的音效（会强转成实体，我们的商人不是实体）。 */
    public static final String HANDLER_QUICK_MOVE = "method_20595";

    /** {@code class_1728#method_7601} —— shift 快速移动（transferSlot）；我们在这里做"快速塞进天平"和结果格快照。 */
    public static final String HANDLER_QUICK_TRANSFER = "method_7601";

    /** {@code class_1914#method_19272} —— 第一成本格的显示数量（原版在这里夹到堆叠上限，我们绕开）。 */
    public static final String OFFER_FIRST_COST = "method_19272";
}
