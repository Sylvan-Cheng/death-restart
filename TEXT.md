# 死亡重启 / Death Restart：完整双语文案

这里列出当前模组的自有语言文案，可直接修改中文或 English 列。游戏实际读取 `src/main/resources/assets/deathrestart/lang/zh_cn.json` 与 `en_us.json`；修改此 Markdown 后，需要将修改同步到语言资源再构建。

当前公开与内部模组 ID 均为 `deathrestart`；设置与死亡榜分别保存在 `deathrestart.json` 和 `deathrestart/` 路径。

此前确认的中文表达已保留。死亡广播里的固定「10 秒」改为占位符，显示房主设置的倒计时时长。语言跟随每位玩家自己的 Minecraft 设置。

`%s` 表示运行时占位符，请保留数量与先后顺序。多参数文案的传入顺序见下表，其余带 `%s` 的文案只有一个参数。

| 语言键 | 占位符顺序 |
| --- | --- |
| `deathrestart.death_broadcast` | 玩家名、倒计时秒数 |
| `deathrestart.reconnect.attempt` | 连接地址、尝试次数 |
| `deathrestart.reset.ready` | 新种子、联机端口 |
| `deathrestart.command.status_details` | 统计身份、死亡榜显示状态、联机端口、正版验证状态 |
| `deathrestart.recovery.failures` | 未恢复的重置数量、备份目录 |

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
| 9 | `deathrestart.reconnect.timeout` | 重连超时，请从多人游戏列表手动重新加入。 | Reconnect timed out. Rejoin from the multiplayer menu. |
| 10 | `deathrestart.reconnect.attempt` | 连接地址：%s · 第 %s 次尝试 | Connecting to %s · Attempt %s |
| 11 | `deathrestart.reconnect.address` | 连接地址：%s | Address: %s |

## 重置、备份与恢复

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 12 | `deathrestart.reset.saving` | 正在保存旧世界，新世界生成中… | Saving old world, generating new world… |
| 13 | `deathrestart.reset.restoring` | 正在恢复旧世界… | Restoring old world… |
| 14 | `deathrestart.reset.failed` | 世界重置失败 | World Reset Failed |
| 15 | `deathrestart.reset.old_world_kept` | 旧世界仍保留在原存档目录。 | The old world is still preserved in its original save folder. |
| 16 | `deathrestart.reset.old_world_restored` | 旧世界已恢复，重新进入原存档即可继续游玩。 | The old world has been restored. Reopen the original save to continue. |
| 17 | `deathrestart.reset.backup_path` | 旧世界备份路径：%s | Old world backup path: %s |
| 18 | `deathrestart.reset.ready` | 新世界已就绪！种子：%s · 端口：%s | New world ready! Seed: %s · Port: %s |
| 19 | `deathrestart.reset.port_wait` | 新世界已生成，正在等待原端口 %s 释放，稍后自动重试… | New world generated. Waiting for port %s to be released; retrying soon… |

## 设置页

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 20 | `deathrestart.config.title` | 死亡重启设置 | Death Restart Settings |
| 21 | `deathrestart.config.scope` | 房主设置影响全体玩家；客机仅可调整本机重连。主菜单可预设下一次对局。 | Host settings apply to all players. Guests can adjust local reconnect only. Configure your next session from the main menu. |
| 22 | `deathrestart.config.seconds` | %s 秒 | %s seconds |
| 23 | `deathrestart.config.reset_countdown` | 世界重置倒计时 | World Reset Countdown |
| 24 | `deathrestart.config.countdown_start_sound` | 倒计时开始提示音 | Countdown Start Sound |
| 25 | `deathrestart.config.countdown_final_seconds_sound` | 最后 3 秒提示音 | Final Countdown Sound |
| 26 | `deathrestart.config.automatic_reconnect` | 自动重连 | Auto-Reconnect |
| 27 | `deathrestart.config.reconnect_interval` | 重连重试间隔 | Reconnect Interval |
| 28 | `deathrestart.config.reconnect_timeout` | 重连超时时长 | Reconnect Timeout |
| 29 | `deathrestart.config.death_leaderboard` | 死亡榜显示 | Death Leaderboard |
| 30 | `deathrestart.config.statistics_mode` | 死亡统计身份 | Track Deaths By |
| 31 | `deathrestart.config.statistics_mode.username` | 玩家名（默认） | Player Name (Default) |
| 32 | `deathrestart.config.statistics_mode.uuid` | UUID | UUID |
| 33 | `deathrestart.config.reset_countdown.tooltip` | 房主设置：下次玩家死亡时生效的时长，不影响当前进行中的倒计时。 | Host setting: Duration used for the next death countdown. Ongoing countdowns are not affected. |
| 34 | `deathrestart.config.countdown_start_sound.tooltip` | 房主设置：倒计时开始时，为全体在线玩家播放提示音。 | Host setting: Plays an alert sound for all online players when the countdown begins. |
| 35 | `deathrestart.config.countdown_final_seconds_sound.tooltip` | 房主设置：倒计时最后 3 秒内，每秒播放一次提示音。 | Host setting: Plays a tick sound every second during the final 3 seconds. |
| 36 | `deathrestart.config.automatic_reconnect.tooltip` | 客机设置：收到房主的世界重置通知后，自动尝试重新连接原地址，于下次重置生效。 | Guest setting: Automatically reconnects to the host address after a world reset. Takes effect on the next reset. |
| 37 | `deathrestart.config.reconnect_interval.tooltip` | 客机设置：断开后首次连接的等待时长，以及后续每次重试的最短间隔。前一次尝试未结束前不会并行重连。 | Guest setting: Initial delay and minimum interval between reconnect attempts. Ongoing connections will not retry concurrently. |
| 38 | `deathrestart.config.reconnect_timeout.tooltip` | 客机设置：断开连接后的最长等待时间。超时后请从多人游戏列表手动加入。 | Guest setting: Maximum time to wait before timing out. Rejoin manually from the multiplayer menu if expired. |
| 39 | `deathrestart.config.death_leaderboard.tooltip` | 房主设置：关闭后对全体玩家隐藏死亡榜，但后台仍会继续累计死亡次数。 | Host setting: When disabled, hides the death leaderboard for all players. Death totals will still accumulate in the background. |
| 40 | `deathrestart.config.statistics_mode.tooltip` | 房主设置：玩家名模式忽略大小写且同名共享次数；UUID 模式按账号独立统计且改名后保留数据。两种模式分别独立保存，切换不会合并数据。 | Host setting: Player Name mode is case-insensitive and merges identical names. UUID mode tracks by account across name changes. Stored separately; switching will not merge data. |
| 41 | `deathrestart.config.reset_countdown_group` | 重置规则 | Reset Rules |
| 42 | `deathrestart.config.backup_group` | 旧世界备份 | Old World Backups |
| 43 | `deathrestart.config.backup_retention` | 旧世界备份保留数量 | Old World Backups Retained |
| 44 | `deathrestart.config.backup_retention.unlimited` | 无限保留 | Unlimited |
| 45 | `deathrestart.config.backup_retention.count` | 保留 %s 个 | Keep %s |
| 46 | `deathrestart.config.backup_retention.tooltip` | 房主设置：新世界成功启动后，自动清理更早的常规备份；失败世界和未完成重置的备份不会自动清理。默认无限保留。 | Host setting: Automatically deletes older standard backups after a new world starts. Failed-world and unfinished-reset backups are never deleted automatically. Defaults to unlimited. |
| 47 | `deathrestart.config.reconnect_group` | 客机重连 | Guest Reconnect |
| 48 | `deathrestart.config.display_group` | 死亡榜与统计 | Deaths & Statistics |
| 49 | `deathrestart.config.data_actions_group` | 数据操作 | Data Actions |
| 50 | `deathrestart.config.backup_port_note` | 每次重置均会备份旧世界，并复用原联机端口。 | Every reset preserves the old world backup and reuses the original LAN port. |
| 51 | `deathrestart.config.reset` | 恢复默认 | Reset to Defaults |
| 52 | `deathrestart.config.save_failed` | 保存设置失败 | Failed to Save Settings |
| 53 | `deathrestart.config.save_failed_details` | 无法写入 config/deathrestart.json。当前更改未生效，请检查游戏目录写入权限后重试。 | Cannot write to config/deathrestart.json. Changes were not applied. Please verify folder write permissions and retry. |

## 游戏内控制命令

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 54 | `deathrestart.command.usage` | 用法：`/deathrestart <restart [秒数\|confirm]\|cancel\|status\|leaderboard <on\|off\|clear [confirm]>\|countdown <秒数>>` | Usage: `/deathrestart <restart [seconds\|confirm]\|cancel\|status\|leaderboard <on\|off\|clear [confirm]>\|countdown <seconds>>` |
| 55 | `deathrestart.command.not_lan` | 请先将世界开放至局域网，再使用重置命令。 | Please open the world to LAN before using reset commands. |
| 56 | `deathrestart.command.invalid_countdown` | 倒计时秒数必须为：5、10、15、20、30、45 或 60。 | Countdown must be one of: 5, 10, 15, 20, 30, 45, or 60 seconds. |
| 57 | `deathrestart.command.save_failed` | 设置保存失败，未应用任何更改。 | Failed to save settings; no changes were applied. |
| 58 | `deathrestart.command.restart_broadcast` | 房主发起了手动重置，世界将于 %s 秒后重置。 | The host initiated a manual reset. The world will reset in %s seconds. |
| 59 | `deathrestart.command.restart_subtitle` | 房主发起了手动重置 | Host initiated a manual reset |
| 60 | `deathrestart.command.already_scheduled` | 世界重置倒计时进行中，剩余 %s 秒。 | A world reset is already counting down: %s seconds remaining. |
| 61 | `deathrestart.command.reset_started` | 世界重置已在进行中，无法取消。 | World reset has already started and cannot be cancelled. |
| 62 | `deathrestart.command.cancelled` | 房主取消了世界重置。 | The host cancelled the world reset. |
| 63 | `deathrestart.command.no_countdown` | 当前没有等待中的世界重置。 | There is no pending world reset. |
| 64 | `deathrestart.command.status_idle` | 当前没有正在进行的世界重置。 | No world reset is currently scheduled. |
| 65 | `deathrestart.command.status_countdown` | 世界重置倒计时：%s 秒。 | World reset countdown: %s seconds. |
| 66 | `deathrestart.command.status_not_lan` | 未开放局域网 | Not opened to LAN |
| 67 | `deathrestart.command.status_reset_started` | 世界重置进行中 | World reset in progress |
| 68 | `deathrestart.command.status_countdown_line` | 重置状态：%s | Reset Status: %s |
| 69 | `deathrestart.command.status_details` | 统计身份：%s · 死亡榜显示：%s · 端口：%s · 正版验证：%s | Identity Mode: %s · Leaderboard: %s · Port: %s · Account Authentication: %s |
| 70 | `deathrestart.command.status_not_open` | 未开放 | Not open |
| 71 | `deathrestart.command.enabled` | 开启 | Enabled |
| 72 | `deathrestart.command.disabled` | 关闭 | Disabled |
| 73 | `deathrestart.command.online_mode.enabled` | 开启 | Enabled |
| 74 | `deathrestart.command.online_mode.disabled` | 关闭 | Disabled |
| 75 | `deathrestart.command.leaderboard_enabled` | 死亡榜已对全体玩家显示，死亡次数将继续累计。 | Death leaderboard is now visible to everyone. Deaths will continue to be counted. |
| 76 | `deathrestart.command.leaderboard_disabled` | 死亡榜已对全体玩家隐藏，死亡次数将继续累计。 | Death leaderboard is now hidden from everyone. Deaths will continue to be counted. |
| 77 | `deathrestart.command.countdown_updated` | 默认重置倒计时已设为 %s 秒，将于下次玩家死亡时生效。 | Default reset countdown updated to %s seconds. Takes effect on the next death. |
| 78 | `deathrestart.command.leaderboard.usage` | 用法：`/deathrestart leaderboard <on\|off\|clear [confirm]>` | Usage: `/deathrestart leaderboard <on\|off\|clear [confirm]>` |
| 79 | `deathrestart.command.restart_confirmation_prompt` | 已准备手动重置世界，确认后将开始 %s 秒倒计时。请在 60 秒内执行 /deathrestart restart confirm。 | Manual reset prepared. Confirming will start a %s-second countdown. Run /deathrestart restart confirm within 60 seconds. |
| 80 | `deathrestart.command.restart_confirmation_expired` | 没有有效的手动重置请求，请重新执行 /deathrestart restart [秒数]。 | There is no valid manual reset request. Run /deathrestart restart [seconds] again. |
| 81 | `deathrestart.command.restart_confirmation_cancelled` | 已取消待确认的手动重置。 | Pending manual reset cancelled. |
| 82 | `deathrestart.command.restart_confirmation_pending` | 已有待确认的手动重置，请先执行 /deathrestart restart confirm 或 /deathrestart cancel。 | A manual reset is already awaiting confirmation. Run /deathrestart restart confirm or /deathrestart cancel first. |
| 83 | `deathrestart.command.status_confirmation` | 等待手动重置确认（确认后倒计时 %s 秒）。 | Awaiting manual reset confirmation (%s-second countdown after confirmation). |

## ModMenu 模组信息

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 84 | `modmenu.nameTranslation.deathrestart` | 死亡重启 | Death Restart |
| 85 | `modmenu.descriptionTranslation.deathrestart` | 在局域网联机中，任一玩家死亡即触发倒计时，随后使用新种子重建世界并复用原端口。支持累计死亡榜、自动重连与自定义倒计时。 | In LAN multiplayer, any player's death triggers a countdown, automatically rebuilding the world with a new seed while reusing the same LAN port. Features cumulative death leaderboards, auto-reconnect, and configurable timers. |

## 重置事务恢复

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 86 | `deathrestart.recovery.title` | 中断重置恢复 | Interrupted Reset Recovery |
| 87 | `deathrestart.recovery.restored` | 已恢复 %s 个异常中断的世界。重新进入原存档即可继续游玩；未完成的新世界文件已保留在 failed-* 目录中。 | Restored %s interrupted world(s). Reopen the original save to continue; unfinished world data is preserved in failed-* folders. |
| 88 | `deathrestart.recovery.failures` | 有 %s 个中断的重置未能恢复，相关文件已保留。请检查游戏日志与备份目录：%s | Failed to recover %s transaction(s). Files were preserved. Check game logs and backup folder: %s |
| 89 | `deathrestart.recovery.commit_failed` | 未能保存重置完成记录，备份已保留，模组将自动重试。请检查游戏日志。 | Failed to save the reset completion record. Backups are preserved; the mod will retry automatically. Check the game logs. |

## 手动重启确认设置

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 90 | `deathrestart.config.manual_restart_confirmation` | 手动重启需要确认 | Confirm Manual Restart |
| 91 | `deathrestart.config.manual_restart_confirmation.tooltip` | 房主设置：默认开启，手动重启须在 60 秒内执行 /deathrestart restart confirm。关闭后直接开始倒计时。已创建的请求和倒计时不受影响，玩家死亡仍会自动触发重置。 | Host setting: Enabled by default. Manual restarts require /deathrestart restart confirm within 60 seconds. When disabled, the countdown starts immediately. Existing requests and countdowns are unchanged. Deaths still trigger automatic resets. |

## 清除死亡榜

| 编号 | 语言键 | 中文 | English |
| --- | --- | --- | --- |
| 92 | `deathrestart.config.clear_leaderboard` | 清除死亡榜 | Clear Death Leaderboard |
| 93 | `deathrestart.config.clear_leaderboard.tooltip` | 仅房主在存档中可用。清除当前存档的用户名与 UUID 两套死亡统计，在线玩家从 0 重新累计；其他存档不受影响。 | Only available to the host while a world is open. Clears both player-name and UUID death statistics for this save. Online players start again at 0. Other saves are unaffected. |
| 94 | `deathrestart.config.clear_leaderboard.confirm` | 确定清除当前存档的死亡榜吗？用户名与 UUID 两套统计都会清除，在线玩家从 0 重新累计。此操作无法撤销。 | Clear this save's death leaderboard? Both player-name and UUID statistics will be cleared. Online players start again at 0. This cannot be undone. |
| 95 | `deathrestart.command.leaderboard.clear_prompt` | 将清除当前存档的用户名与 UUID 两套死亡统计。此操作无法撤销，请在 60 秒内执行 /deathrestart leaderboard clear confirm。 | This will clear both player-name and UUID death statistics for this save. This cannot be undone. Run /deathrestart leaderboard clear confirm within 60 seconds. |
| 96 | `deathrestart.command.leaderboard.clear_expired` | 没有有效的死亡榜清除请求，请重新执行 /deathrestart leaderboard clear。 | No valid leaderboard clear request. Run /deathrestart leaderboard clear again. |
| 97 | `deathrestart.command.leaderboard.cleared` | 当前存档的用户名与 UUID 死亡统计已清除，在线玩家从 0 重新累计。 | Player-name and UUID death statistics for this save have been cleared. Online players start again at 0. |
| 98 | `deathrestart.command.leaderboard.clear_failed` | 死亡榜清除失败，统计未清除。请检查统计文件与游戏目录写入权限，详情见游戏日志。 | Could not clear the leaderboard. Statistics were not cleared. Check the statistics file and game folder write permissions. See the game logs for details. |

## 原版通用按钮

「完成」「返回」「取消」沿用 Minecraft 自带的 `gui.done`、`gui.back`、`gui.cancel` 翻译；开关使用原版复选框，勾选状态由原版绘制和朗读。这些通用控件随游戏语言变化，不重复定义到模组资源。

日志与异常详情用于诊断，保留英文或底层异常原文；玩家名、种子、地址、端口及文件路径是运行数据，不做翻译。
