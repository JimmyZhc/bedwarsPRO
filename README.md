# bedwarsPRO
BedwarsPRO是基于 BedwarsRel 深度魔改的起床战争插件，本项目基于 GPL-3.0 许可证开源。
 
## 版权声明
Original BedwarsRel (C) 2015 Sebastian Binder (and other contributors)

Original BedwarsScoreBoardAddon (C) 2015 Ram

Original BedwarsItemAddon (C) 2015 Ram

Modifications and integration by JmmYm

二传作者联系方式：

邮箱：jimmyzhangzhc@gmail.com

作者：JmmYm (JimmyZhc)

## 关于本项目
本项目基于以下开源项目整合魔改而成：

BedwarsRel —— 起床战争核心插件

BedwarsScoreBoardAddon —— 计分板及功能扩展

BedwarsItemAddon —— 特殊道具扩展

为什么要整合？
三个插件原本各自独立运行，存在以下问题：

插件间依赖关系复杂，配置分散

部分功能存在冲突或重复

维护和更新不便

BedwarsPRO 将三者合并为一个独立插件，消除内部依赖，统一配置管理，保留全部原有功能。

✨ 魔改内容
🔗 三合一整合（核心改动）
将 BedwarsRel、BedwarsScoreBoardAddon、BedwarsItemAddon 三个独立插件合并为一个统一插件，消除内部依赖关系，统一配置管理。合并后插件名为 BedwarsPRO，包名统一为 io.jmmym.bedwarspro，主类为 BedwarsPRO。

🐛 Bug 修复
计分板闪烁问题

修复游戏开始时计分板短暂显示变量名（如 map:world playerxxx，wating）后恢复正常的问题

原因：BedwarsRel 与 bwsba 同时更新计分板产生时间差

修复：bwsba 在游戏开始时立即覆盖计分板，消除闪烁

铁砧输入框占位符未替换

修复 GUI 编辑界面中添加队伍时，{value} 占位符未被玩家输入值替换的问题

影响命令格式，导致 NumberFormatException

ShopListener 空指针异常

修复点击 NPC 时 ShopListener 将所有 NPC 当作商店 NPC 处理的问题

添加空值检查：若找不到对应 Arena 则直接返回

Arena 初始化空指针

修复 Arena 类中 Respawn、NoBreakBed、TimeTask 等子模块未正确初始化的问题

导致玩家移动、计分板更新、定时任务等多处空指针异常

编译错误修复

修复 Title.java 中 Bukkit cannot be resolved 编译错误

修复 Utils 类中 sendTitle、sendMessage、sendPlayerActionbar 等方法签名不匹配问题

配置文件加载失败

修复 Config.java 中 configSec 为 null 时直接调用 .contains() 导致的空指针异常

添加空值检查，配置缺失时使用默认值

WorldGuard 与 DeluxeHub 冲突

修复 DeluxeHub 与 WorldGuard 同时启用时，WorldGuard 保护失效的问题

原因：DeluxeHub 抢先拦截事件，WorldGuard 未收到事件通知

刷怪蛋兼容性修复

修复合并后蠹虫、骷髅、蜘蛛等刷怪蛋无法放置的问题

独立插件时功能正常，合并后失效的兼容性问题

NPC 交互优化

修复 CommandNPC 点击时 player 为 null 的空指针异常

支持 NPC 绑定命令执行

出生点强制传送

修复玩家在 Lobby 世界下线后，重新上线仍在上次位置的问题

改为每次进入服务器强制传送到 Lobby 出生点

一端多图支持

修复 BungeeCord 模式下 full-restart 和 spigot-restart 配置被重置的问题

支持同一服务器运行多张地图，游戏结束后自动切换

✨ 新增功能
任务系统

新增每日/每周任务系统，支持以下任务类型：

参与对局（PARTICIPATE）

击杀玩家（KILL）

破坏床（DESTROY_BED）

收集资源（COLLECT_RESOURCE）

获得胜利（WIN）

最终击杀（FINAL_KILL）

速胜大师（QUICK_WIN）

累计击杀（WEEKLY_KILL）

累计胜场（WEEKLY_WIN）

累计拆床（WEEKLY_DESTROY_BED）

任务 GUI

新增 /bwpro task gui 命令，打开任务系统图形界面

每日/每周任务配置

支持 daily-tasks 和 weekly-tasks 配置节点

支持任务随机分配（random-assign）

支持任务数量控制（count）

支持任务目标范围（target-min / target-max）

⚙️ 配置优化
合并三个插件的配置文件为统一的 config.yml

使用 bw、bwsba、bwia 作为顶级键区分不同模块配置

支持 game-cycle 地图自动轮换配置

支持 bungeecord 跨服模式配置

🎮 原功能特性
核心游戏机制（来自 BedwarsRel）
支持最多 15 个队伍的多人对战

全地图资源生成器系统，产出铁、金、钻石、绿宝石等资源

村民商店 / GUI 商店双模式

自动地图重置与游戏循环

旁观者模式支持

BungeeCord 多服务器支持

MySQL / YAML 数据存储

队伍大小可自定义

计分板与界面增强（来自 BedwarsScoreBoardAddon）
自定义大厅 / 游戏计分板

Tab 列表玩家血量显示

游戏开始 / 结束 / 胜利标题动画

资源点升级系统

资源点全息 holographic 显示

床全息 holographic 显示

快速加入游戏功能

队伍选择菜单

物品 / 队伍商店 NPC

重生倒计时

凋零弓

游戏结束统计信息

自定义 Actionbar 信息

玩家 Tag 自定义

特殊道具（来自 BedwarsItemAddon）
火球（Fireball）

TNT / TNT 发射器

降落伞（Parachute）

蹦床（Trampoline）

搭桥蛋（Bridge Egg）

末影珍珠椅（Ender Pearl Chair）

梦魇守卫（Dream Defender Golem）

蠹虫（Bedbug）

防爆玻璃（Explosion-Proof Glass）

行走平台（Walk Platform）

紧凑型弹出塔（Compact Pop-up Tower）

魔法牛奶（Magic Milk）

📦 安装
确保服务器为 Spigot/Paper 1.8.8

将 BedwarsPRO.jar 放入服务器的 plugins 文件夹

删除原有的 BedwarsRel.jar、BedwarsScoreBoardAddon.jar、BedwarsItemAddon.jar（避免冲突）

启动服务器，插件会自动生成默认配置文件

在 plugins/BedwarsPRO/config.yml 中设置 license-key

🔧 前置依赖
插件	必需性	说明
ProtocolLib	必需	数据包操作库
Citizens	必需	NPC 支持
Vault	可选	经济系统支持
Multiverse-Core	可选	多世界支持
📝 命令与权限
BedwarsPRO 命令（原 bw 命令）
命令	说明	权限
/bw addgame <名称> <最大人数>	创建游戏	bw.setup
/bw addteam <游戏> <队伍名> <颜色> <人数>	添加队伍	bw.setup
/bw setregion <游戏> loc1/loc2	设置游戏区域	bw.setup
/bw setlobby <游戏>	设置大厅出生点	bw.setup
/bw setspawn <游戏> <队伍>	设置队伍出生点	bw.setup
/bw setbed <游戏> <队伍>	设置床的位置	bw.setup
/bw setspawner <游戏> <类型>	设置资源点	bw.setup
/bw start <游戏>	强制开始游戏	bw.setup / bw.vip.forcestart
/bw stop <游戏>	停止游戏	bw.setup
/bw join <游戏>	加入游戏	bw.join
/bw leave	离开游戏	bw.leave
/bw list	查看游戏列表	bw.base
计分板扩展命令（原 bwsba 命令）
命令	说明	权限
/bwsba reload	重载配置	bedwarsscoreboardaddon.reload
/bwsba shop list	查看商店列表	bedwarsscoreboardaddon.shop.list
/bwsba shop set item <游戏>	设置物品商店	bedwarsscoreboardaddon.shop.set
/bwsba shop set team <游戏>	设置队伍商店	bedwarsscoreboardaddon.shop.set
/bwsba shop remove <ID>	移除商店	bedwarsscoreboardaddon.shop.remove
道具扩展命令（原 bwia 命令）
命令	说明	权限
/bwia reload	重载配置	bedwarsitemaddon.reload
/bwia upcheck	检查更新	bedwarsitemaddon.updatecheck
🛠️ 构建
bash
# 克隆项目
git clone https://github.com/yourusername/BedwarsPRO.git
cd BedwarsPRO

# 使用 Maven 构建
mvn clean package
构建产物位于 target/BedwarsPRO.jar


🤝 贡献
欢迎提交 Issue 和 Pull Request。

Fork 本仓库

创建你的功能分支 (git checkout -b feature/AmazingFeature)

提交你的修改 (git commit -m 'Add some AmazingFeature')

推送到分支 (git push origin feature/AmazingFeature)

打开一个 Pull Request



