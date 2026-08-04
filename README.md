# bedwarsPRO
BedwarsPRO (Modifications and integration by JmmYm) 是基于 BedwarsRel 深度魔改的起床战争插件，本项目基于 GPL-3.0 许可证开源。

### 在使用本源代码进行二次开发或拓展时，请务必保留原始作者信息，包括 Ram 与 JmmYm，不得以任何形式删除或隐藏。
 
### 版权声明
Original BedwarsRel (C) 2015 Sebastian Binder (and other contributors)

Original BedwarsScoreBoardAddon (C) 2015 Ram

Original BedwarsItemAddon (C) 2015 Ram

Original BedwarsPRO (C) 2026 JmmYm

作者联系方式：

邮箱：jimmyzhangzhc@gmail.com

作者：JmmYm (JimmyZhc)

### 关于本项目
本项目基于以下开源项目整合魔改而成：

BedwarsRel —— 起床战争核心插件

BedwarsScoreBoardAddon —— 计分板及功能扩展

BedwarsItemAddon —— 特殊道具扩展

### 为什么要整合？
三个插件原本各自独立运行，存在以下问题：

插件间依赖关系复杂，配置分散

部分功能存在冲突或重复

维护和更新不便

BedwarsPRO 将三者合并为一个独立插件，消除内部依赖，统一配置管理，保留全部原有功能。

### 魔改内容
将 BedwarsRel、BedwarsScoreBoardAddon、BedwarsItemAddon 三个独立插件合并为一个统一插件，消除内部依赖关系，统一配置管理。合并后插件名为 BedwarsPRO，包名统一为 io.jmmym.bedwarspro，主类为 BedwarsPRO。

### Bug 修复
独立插件时功能正常，修复了合并后失效的兼容性问题

修复了目前已知的漏洞

修复同一服务器运行多张地图，游戏结束后没有正常返回大厅的BUG (详见下文指令）

## 新增功能
### 任务系统

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


原功能特性
核心游戏机制（来自 BedwarsRel）
支持最多 15 个队伍的多人对战

全地图资源生成器系统，产出铁、金、钻石、资源

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

快速加入游戏功能

队伍选择菜单

物品 / 队伍商店 NPC

重生倒计时

凋零弓

游戏结束统计信息

自定义 Actionbar 信息

玩家 Tag 自定义

特殊道具（来自 BedwarsItemAddon）【以下功能均默认关闭】
火球（Fireball）

TNT / TNT 发射器

降落伞（Parachute）

蹦床（Trampoline）

搭桥蛋（Bridge Egg）

末影珍珠（Ender Pearl Chair）

蠹虫（Bedbug）

防爆玻璃（Explosion-Proof Glass）

行走平台（Walk Platform）

紧凑型塔（Compact Pop-up Tower）

魔法牛奶（Magic Milk）

## 安装
确保服务器为 Spigot/Paper 1.8.8

将 BedwarsPRO.jar 放入服务器的 plugins 文件夹

删除原有的 BedwarsRel.jar、BedwarsScoreBoardAddon.jar、BedwarsItemAddon.jar（避免冲突）

启动服务器，插件会自动生成默认配置文件

在 plugins/BedwarsPRO/config.yml 中设置 license-key

## 前置依赖

ProtocolLib	必需	数据包操作库

Citizens	  必需	NPC 支持

栖云居定制插件	可选	经济系统支持

Multiverse-Core	可选	多世界支持

## 命令与权限
### BedwarsPRO 新增命令

/bwpro reload 【bwpro.task.use】 重载所有配置文件

/bwpro task gui 【bwpro.task.use】 打开 每日菜单 

/bwpro task info 【bwpro.task.use】 查看任务进度 

/bwpro task on 【bwpro.task.admin】 开启每日任务 

/bwpro task off 【bwpro.task.admin】 关闭每日任务 

/bwpro task weekly on 【bwpro.task.admin】 开启每周任务 

/bwpro task weekly off 【bwpro.task.admin】 关闭每周任务 

/bwpro task reload 【bwpro.task.reload】 重载 tasks.yml 配置 

/bwpro task publish <name> 【bwpro.task.publish】 发布追杀令

/bwpro task clear 【bwpro.task.admin】 清空所有特殊任务

/bwpro task random on 【bwpro.task.admin】 打开每日任务随机池

/bwpro task random on 【bwpro.task.admin】 打开每日任务随机池

/bwpro task wrandom on 【bwpro.task.admin】 打开每周任务随机池

/bwpro task wrandom off 【bwpro.task.admin】 打开每日任务随机池

/bw setmainlobby <name> 修复一端多图模式下无法返回大厅的BUG

### 原 bedwarsRel 命令（创建游戏的备选方案）

/bw addgame <名称> <最大人数>		【bw.setup】 创建游戏

/bw addteam <游戏> <队伍名> <颜色> <人数>		【bw.setup】 添加队伍

/bw setregion <游戏> loc1/loc2		【bw.setup】设置游戏区域

/bw setlobby <游戏>		【bw.setup】设置大厅出生点

/bw setspawn <游戏> <队伍>		【bw.setup】 设置队伍出生点

/bw setbed <游戏> <队伍>		【bw.setup】 设置床的位置

/bw setspawner <游戏> <类型>		【bw.setup】 设置资源点

/bw start <游戏>	【bw.setup / bw.vip.forcestart】 强制开始游戏

/bw stop <游戏>	【bw.setup】 停止游戏	

/bw join <游戏>		【bw.join】 加入游戏

/bw leave		【bw.leave】 离开游戏

/bw list		【bw.base】 查看游戏列表

### 原 BedwarsScoreBoardAddon 命令

/bwsba reload		【bedwarsscoreboardaddon.reload】 重载配置

/bwsba shop list		【bedwarsscoreboardaddon.shop.list】 查看商店列表

/bwsba shop set item <游戏>		【bedwarsscoreboardaddon.shop.set】 设置物品商店

/bwsba shop set team <游戏>		【bedwarsscoreboardaddon.shop.set】 设置队伍商店

/bwsba shop remove <ID>		【bedwarsscoreboardaddon.shop.remove】 移除商店

### 原 BedwarsItemAddon 命令

/bwia reload		【bedwarsitemaddon.reload】 重载配置

/bwia upcheck		【bedwarsitemaddon.updatecheck】 检查更新

## 克隆项目
git clone https://github.com/yourusername/BedwarsPRO.git
cd BedwarsPRO

## 使用 Maven 构建
mvn clean package
构建产物位于 target/BedwarsPRO.jar



