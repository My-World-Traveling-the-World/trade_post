# tradepost

Minecraft 1.21.1 / Fabric 的村庄集市模组：蹲下右键钟打开集市，按村做动态定价、收购、汇兑、捐赠与秘宝。

## 构建

需要 JDK 21，以及 Fabric Loader 的 Minecraft 客户端 jar（去掉 Gradle 依赖，直接用 javac）。

```powershell
# 依赖路径都在脚本开头的 param 里，默认值是按本机 PCL2 目录写的，按需改
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-windows.ps1
```

产物：`build\libs\tradepost-0.2.1.jar`

### Jade 支持（可选）

`client/jade/TradepostJadePlugin.java` 需要 Jade 的 API 才能编译。
把 Jade 的 jar 复制成 `libs/jade-api.jar` 即可；没有它时脚本会跳过，但会编译失败——需要移除该文件或补上依赖。

## 配置文件

运行时配置在游戏实例的 `config/tradepost/` 下：

- `market/values.json` —— 手写价（**优先级最高，会盖过黑名单**）
- `market/economy.json` —— 经济数值
- `market/relics/*.json` —— 秘宝池（`price` = 绿宝石直购价，`weight` 同权即等概率）
- `market/blacklist.json` —— 黑名单（本仓库里黑名单在代码 `Banlist.java` 中）

改完 JSON 用 `/tradepost reload`；改代码要重启游戏。

## 许可

（待定）