# ExtraCityHall（扩展市政厅）

Minecraft Forge 1.20.1 的 **MineColonies** 附属模组，解决原版自动分配工作分配不是很理想与给了一个新职业以及工作获得XP以提升属性的机制。

## 功能概览

- **绿皮书**：手持普通书右键市政厅方块，将其转化为绑定殖民地的「绿皮书」；空中右键打开原版市政厅管理 GUI，并为所有市政厅分页窗口注入「就业」书签。
- **书记职业**：市政厅专属职业（主属性 知识 / 次属性 智力），上岗后按 `tanh` 公式为全殖民地市民提供全局经验加成。
- **经验系统**：被动工作经验曲线（与经验球无关），受住宅等级与技能等级约束。
- **职业最优自动分配**：基于最小费用最大流（SPFA）的市民↔职业最优匹配，支持每殖民地设置职业优先级与分配模式（原版 / 仅失业 / 全体最优），可在夜晚周期性自动重排。

## 前置

| 模组 | 版本 | 许可 |
| --- | --- | --- |
| MineColonies | 1.20.1-1.1.605-BETA | GPL-3.0（ldtteam） |
| BlockUI | 1.20.1-1.0.139-BETA | GPL-3.0（ldtteam） |
| Structurize | 1.20.1-1.0.740-BETA | GPL-3.0（ldtteam） |
| Domum-Ornamentum | 1.20.1-1.0.184-BETA | GPL-3.0（ldtteam） |

## 衍生资源

以下贴图为 **MineColonies（ldtteam）的衍生作品**，依据 GPL-3.0 随本模组再分发并保持相同许可，特此署名：

```
src/main/resources/assets/minecolonies/textures/entity/citizen/default/secretarymale1_{a,b,d,w}.png
src/main/resources/assets/minecolonies/textures/entity/citizen/default/secretaryfemale1_{a,b,d,w}.png
```

这些贴图由 `tools/gen_secretary_skins.py` 采样 MineColonies 原版市民贴图（`citizenmale1_b` / `citizenfemale1_b`）生成，版权归属 ldtteam，本模组在此基础上以 GPL-3.0 再分发。

## 配置

经验与自动重排参数见游戏内 `config/exhConfig.cfg`（修改后重启生效）：
`baseXpB`、`settleIntervalTicks`、`cutoffRatio`、`autoAssignEnabled`、`autoAssignPeriodDays`。

## 其他

你说的对，但是《模拟殖民地》是由Minecolonies团队研发的经营类Minecraft的模组。游戏发生在一个被称作「minecraft:overworld」的幻想维度，在这里，被神选中的人将被授予「建筑权杖」，导引幸福度之力。你将扮演一位名为「殖民长官」的神秘角色，在自由的旅行中邂逅性格各异、能力独特的同伴们，和他们一起击败强敌，找回失散的亲人——同时，逐步发掘「自动化」的真相。
Thanks for creating such a interesting mod like MineColonies.
