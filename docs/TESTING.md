# 测试与验证

环境：Windows，Minecraft 26.1.2，Fabric Loader 0.19.5，Fabric API 0.155.3+26.1.2，Java 25。

## 运行测试

构建并执行单元测试，同时检查联机测试代码是否能够编译：

```powershell
.\gradlew.bat clean build compileIntegrationJava
```

在两个终端分别运行以下命令，可使用真实开发客户端复现联机测试：

```powershell
.\gradlew.bat runLanTestHost
.\gradlew.bat runLanTestGuest
```

测试使用项目 `build/` 下的独立游戏目录。重跑前，将已有的 `build/lan-test-sync`、`build/lan-test-host` 和 `build/lan-test-guest` 目录改名移开，避免读到前次测试的通信文件或存档。通过标记为 `build/lan-test-sync/host-passed.txt` 与 `guest-passed.txt`。

## 1.1 自动化验证

20 项 JUnit 测试全部通过：6 项倒计时测试、5 项世界备份/恢复测试、3 项累计死亡统计测试、6 项配置读写与迁移测试。

新增验证包括：5、45、60 秒倒计时在到期前不触发、到期后只触发一次；非正倒计时时长被拒绝；设置保存后能完整重载；缺字段保留默认值；小数、整数溢出、非法预设值及错误 JSON 类型逐字段回退；损坏 JSON 保留原文件；保存失败清理临时文件。

ModMenu 与语言资源：完整构建通过，正式 JAR 包含可选 `modmenu` 入口、设置界面、`zh_cn.json` 与 `en_us.json`。两套资源均为合法 JSON，键及占位符数量一致。ModMenu 名称与描述使用其标准翻译键；模组不内嵌 ModMenu，未安装时使用配置文件。

内部 ID 迁移验证：正式 JAR 的 `fabric.mod.json` 使用 `deathrestart`，资源命名空间、网络 payload、Mixin 配置、Java 包和 ModMenu 翻译键均使用新 ID；旧 `deathreset` 配置与死亡榜路径由启动时兼容迁移。

1.1 的设置页、双语支持与 ID 迁移尚未进行真实客户端的界面交互和双客户端回归；下方联机记录来自 1.0，不能视为 1.1 的实测记录。

## 1.0 双客户端联机记录

使用两个普通开发客户端与独立游戏目录，运行 `runLanTestHost` 和 `runLanTestGuest`。没有对服务器线程进行 GameTest 同步调度。

| 回合 | 种子 | 联机端口 | 结果 |
| --- | --- | --- | --- |
| 初始回合 | 123456789 | 1420 | 客机加入后死亡，启动 10 秒倒计时 |
| 第二回合 | 6834511318581205074 | 1420 | 新世界成功生成，客机自动重连，房主随后死亡 |
| 第三回合 | -3681451861947512587 | 1420 | 第二次重开成功，客机再次自动重连 |

已验证：

- 客机和房主的实际死亡都能触发重置。
- 每次至少等待 10 秒，再关闭旧世界并生成新世界。
- 两次重开都沿用同一个存档目录、同一个联机端口。
- 原服务器正常停止并释放世界文件锁。
- `keep_inventory=true` 的情况下，双方的新局背包和经验仍然清空。
- 旧局测试建筑未出现在新世界中。
- 困难难度及修改过的游戏规则保留。
- 右侧显示累计死亡榜，换种子后客机死亡次数仍为 1。
- 最终统计中，房主与客机累计死亡各为 1。
- 客机的两次重新加入由模组自动执行，未使用测试代码代替重连。
- 原世界完整保留在两个独立的备份目录中。

两项 Gradle 启动任务均正常结束，返回 `BUILD SUCCESSFUL`。验证截图：[死亡倒计时](screenshots/countdown.png)、[新一局开始](screenshots/new-round.png)。原始测试输出属于本地构建产物，清理后可通过上述命令重新生成。

验证范围为本模组与 Fabric API 的组合。现有大型整合包中的其他模组尚未逐个进行兼容性实测。
