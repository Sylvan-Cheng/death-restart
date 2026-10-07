# 死亡重启 / Death Restart：完整双语文案

这里列出当前模组的全部 41 条自有语言文案，可直接修改中文或 English 列。游戏实际读取 `src/main/resources/assets/deathrestart/lang/zh_cn.json` 与 `en_us.json`；修改此 Markdown 后，需要将修改同步到语言资源再构建。

当前公开与内部模组 ID 均为 `deathrestart`；设置与死亡榜分别保存在 `deathrestart.json` 和 `deathrestart/` 路径。

此前确认的中文表达已保留。死亡广播里的固定「10 秒」改为占位符，显示房主设置的倒计时时长。语言跟随每位玩家自己的 Minecraft 设置。

`%s` 表示运行时占位符，请保留数量与先后顺序。死亡广播依次是玩家名、秒数；新世界就绪依次是种子、端口；重连尝试依次是地址、尝试次数。其他带 `%s` 的文案只有一个参数（秒数、地址、端口或备份路径，见键名）。

## 死亡与死亡榜

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 1 | `deathrestart.death_broadcast` | %s 已死亡！世界将于 %s 秒后重置。 | %s died! The world will reset in %s seconds. |
| 2 | `deathrestart.countdown_subtitle` | %s 已死亡 · 即将重置世界 | %s died · World reset incoming |
| 3 | `deathrestart.countdown_seconds` | %s 秒 | %s seconds |
| 4 | `deathrestart.countdown_actionbar` | 世界重置倒计时：%s 秒 | World reset countdown: %s seconds |
| 5 | `deathrestart.leaderboard` | 死亡榜（累计） | Deaths (Total) |

## 重连界面

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 6 | `deathrestart.reconnect.title` | 正在重置世界 | Resetting World |
| 7 | `deathrestart.reconnect.cancel` | 取消自动重连 | Cancel Auto-Reconnect |
| 8 | `deathrestart.reconnect.waiting` | 房主正在生成新世界，稍后自动重连… | The host is generating a new world. Reconnecting soon… |
| 9 | `deathrestart.reconnect.timeout` | 重连超时，请从多人游戏列表手动重新加入。 | Reconnect timed out. Rejoin from the multiplayer list. |
| 10 | `deathrestart.reconnect.attempt` | 连接地址：%s · 第 %s 次尝试 | Address: %s · Attempt %s |
| 11 | `deathrestart.reconnect.address` | 连接地址：%s | Address: %s |

## 重置、备份与恢复

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 12 | `deathrestart.reset.saving` | 正在保存旧世界，新世界生成中… | Saving the old world and generating a new one… |
| 13 | `deathrestart.reset.restoring` | 正在恢复旧世界… | Restoring the old world… |
| 14 | `deathrestart.reset.failed` | 世界重置失败 | World Reset Failed |
| 15 | `deathrestart.reset.old_world_kept` | 旧世界仍保留在原存档目录。 | The old world is still in its original save folder. |
| 16 | `deathrestart.reset.old_world_restored` | 旧世界已恢复，重新进入原存档即可继续。 | The old world was restored. Reopen the original save to continue. |
| 17 | `deathrestart.reset.backup_path` | 旧世界备份路径：%s | Old world backup: %s |
| 18 | `deathrestart.reset.ready` | 新世界已就绪！种子：%s · 端口：%s | New world ready! Seed: %s · Port: %s |
| 19 | `deathrestart.reset.port_wait` | 新世界已生成，正在等待原端口 %s 释放，稍后自动重试… | New world generated. Waiting for original port %s to be released; retrying soon… |

## 设置页

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 20 | `deathrestart.config.title` | 死亡重启设置 | Death Restart Settings |
| 21 | `deathrestart.config.scope` | 重置规则与死亡榜由房主决定；重连选项仅影响当前客机。 | The host controls reset rules and the leaderboard. Reconnect options apply to this guest only. |
| 22 | `deathrestart.config.seconds` | %s 秒 | %s seconds |
| 23 | `deathrestart.config.reset_countdown` | 世界重置倒计时 | World reset countdown |
| 24 | `deathrestart.config.automatic_reconnect` | 自动重连 | Auto-reconnect |
| 25 | `deathrestart.config.reconnect_interval` | 重连间隔 | Reconnect interval |
| 26 | `deathrestart.config.reconnect_timeout` | 重连超时 | Reconnect timeout |
| 27 | `deathrestart.config.death_leaderboard` | 显示死亡榜 | Show death leaderboard |
| 28 | `deathrestart.config.reset_countdown.tooltip` | 房主设置：下一次玩家死亡时使用此时长。正在进行的倒计时不受影响。 | Host setting: duration used for the next player death. An active countdown is not affected. |
| 29 | `deathrestart.config.automatic_reconnect.tooltip` | 客机设置：收到房主的世界重置通知后，自动尝试重新加入原地址。下一次重置时生效。 | Guest setting: automatically rejoin the original address after the host announces a world reset. Applies to the next reset. |
| 30 | `deathrestart.config.reconnect_interval.tooltip` | 客机设置：断开后首次连接的等待时间，以及后续两次连接开始之间的最短间隔。连接未结束时不会并行重试。 | Guest setting: delay before the first connection and minimum time between later attempts. An ongoing connection is allowed to finish before retrying. |
| 31 | `deathrestart.config.reconnect_timeout.tooltip` | 客机设置：从断开连接起最多等待多久。超时后可从多人游戏列表手动加入。 | Guest setting: maximum wait after disconnecting. After a timeout, rejoin from the multiplayer list. |
| 32 | `deathrestart.config.death_leaderboard.tooltip` | 房主设置：隐藏死亡榜时仍累计死亡次数。关闭后恢复被死亡榜替换的侧边栏目标；该目标需仍存在。 | Host setting: deaths are still counted while hidden. Turning this off restores the sidebar replaced by the leaderboard, if that objective still exists. |
| 33 | `deathrestart.config.reset_countdown_group` | 重置规则 | Reset Rules |
| 34 | `deathrestart.config.reconnect_group` | 客机重连 | Guest Reconnect |
| 35 | `deathrestart.config.display_group` | 数据与显示 | Data and Display |
| 36 | `deathrestart.config.backup_port_note` | 每次重置均保留旧世界备份，并复用原联机端口。 | Every reset keeps an old-world backup and reuses the original LAN port. |
| 37 | `deathrestart.config.reset` | 恢复默认 | Restore Defaults |
| 38 | `deathrestart.config.save_failed` | 设置保存失败 | Could Not Save Settings |
| 39 | `deathrestart.config.save_failed_details` | 无法写入 config/deathrestart.json。当前设置尚未应用，请检查游戏目录是否可写后重试。 | Could not write config/deathrestart.json. Your changes have not been applied. Check that the game folder is writable, then try again. |

## ModMenu 模组信息

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 40 | `modmenu.nameTranslation.deathrestart` | 死亡重启 | Death Restart |
| 41 | `modmenu.descriptionTranslation.deathrestart` | 局域网联机中，一人死亡即开始倒计时，随后换新种子重建世界，并复用原端口。支持累计死亡榜、自动重连与可配置倒计时。 | In LAN multiplayer, a player death starts a countdown, then rebuilds the world with a new seed on the same port. Includes a cumulative death leaderboard, auto-reconnect, and a configurable countdown. |

## 原版通用按钮

「完成」「返回」以及开关的「开 / 关」沿用 Minecraft 自带的 `gui.done`、`gui.back`、`options.on`、`options.off` 翻译。这些通用按钮随游戏语言变化，不重复定义到模组资源。

日志与异常详情用于诊断，保留英文或底层异常原文；玩家名、种子、地址、端口及文件路径是运行数据，不做翻译。
