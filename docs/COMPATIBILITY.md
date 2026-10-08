# 版本兼容与维护

维护范围为 Mojang 官方要求 Java 25 的 Minecraft Java Edition 正式版，不包含快照、预发布版和候选版。新版本经构建及实际联机验证后才加入支持列表，未来正式版不会自动视为兼容。

## 当前版本矩阵

| Minecraft | Fabric Loader | Fabric API | 可选 ModMenu | 适配实现 |
| --- | --- | --- | --- | --- |
| 26.1 | >=0.19.5 | 0.155.3+26.1.2 | 18.0.2 | 26.1 |
| 26.1.1 | >=0.19.5 | 0.155.3+26.1.2 | 18.0.2 | 26.1 |
| 26.1.2 | >=0.19.5 | 0.155.3+26.1.2 | 18.0.2 | 26.1 |
| 26.2 | >=0.19.5 | 0.161.0+26.2 | 20.0.3 | 26.2 |
| 26.3 | >=0.19.5 | 0.162.0+26.3 | 21.0.0 | 26.3 |

版本号、游戏依赖及 ModMenu 依赖由 Gradle 从 `gradle/minecraft-versions.json` 生成。每个 JAR 只声明一个确切 Minecraft 版本，并只包含该版的适配类。共同逻辑保持在 `src/main` 和 `src/client`，不使用反射猜测 Minecraft 接口。

Java 25 是运行条件，不是游戏 API 兼容保证。26.2 改变局域网发布参数及界面管理，26.3 再次改变发布参数和客机命令权限读取；这些差异集中于 `src/compat`。测试截图适配仅纳入联机测试源码，不打包进正式模组。

## 构建与验证

```powershell
# 构建所有维护目标：需要 Java 25 JDK 和 PowerShell 7
.\scripts\build-java25-versions.ps1

# 只构建指定目标
.\scripts\build-java25-versions.ps1 -MinecraftVersion 26.1,26.3

# 原生 Gradle 单版本构建
.\gradlew.bat build compileIntegrationJava '-Pminecraft_version=26.3'
```

输出位于 `build/versions/<Minecraft>/libs/`。单元测试报告位于对应目录的 `reports/tests/test/`。构建脚本在任一目标失败时返回非零退出码；不将失败目标标记为通过。

构建通过只代表编译与单元测试通过。双客户端测试验证客机与房主死亡、倒计时、更换种子、保存备份、复用端口、累计死亡榜及自动重连；具体记录见 [TESTING.md](TESTING.md)。

## 新版本维护流程

1. 使用 Mojang 官方版本清单确认版本类型为 `release`，详情中的 `javaVersion.majorVersion` 为 `25`。
2. 查询 Fabric 的 Loader 版本及 Fabric API、ModMenu 的目标游戏版本，向 `gradle/minecraft-versions.json` 添加固定依赖和适配目录。
3. 运行全版本构建。若 Minecraft API 改变，在 `src/compat/<版本>/` 增加最小适配，避免修改已验证版本的实现。
4. 在新版本运行真实双客户端联机测试，并检查其余受支持游戏版本的回归结果。维护说明分别记录构建与联机验证状态。
5. 发布独立 JAR，并在 Modrinth 仅选择已验证的对应游戏版本。只允许房主与客机在同一 Minecraft 版本中联机。

## 信息来源

- [Mojang 官方版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)，各详情文件的 `type` 与 `javaVersion` 字段。
- [Fabric Loader 官方版本接口](https://meta.fabricmc.net/v2/versions/loader/26.3)。
- [Fabric API 版本列表](https://modrinth.com/mod/fabric-api/versions)与 [ModMenu 版本列表](https://modrinth.com/mod/modmenu/versions)，按游戏版本与 Fabric 筛选。
- Minecraft 接口直接核对 Loom 下载的 Mojang 游戏 JAR 类签名及实现，并由每个目标的编译检查验证。
