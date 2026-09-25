# ExtraCityHall（扩展市政厅）

Minecraft Forge 1.20.1 的 **MineColonies** 附属模组。

- **作者**：zhaojunzhi
- **许可**：GNU General Public License v3.0（GPL-3.0），详见 [`LICENSE`](./LICENSE)
- **运行平台**：Minecraft 1.20.1 / Forge 47.1.3 / Java 17

## 功能概览

- **绿皮书**：手持普通书右键市政厅方块，将其转化为绑定殖民地的「绿皮书」；空中右键打开原版市政厅管理 GUI，并为所有市政厅分页窗口注入「就业」书签。
- **书记职业**：市政厅专属职业（主属性 知识 / 次属性 智力），上岗后按 `tanh` 公式为全殖民地市民提供全局经验加成。
- **经验系统**：被动工作经验曲线（与经验球无关），受住宅等级与技能等级约束。
- **职业最优自动分配**：基于最小费用最大流（SPFA）的市民↔职业最优匹配，支持每殖民地设置职业优先级与分配模式（原版 / 仅失业 / 全体最优），可在夜晚周期性自动重排。

## 依赖（运行时，需玩家另行安装）

| 模组 | 版本 | 许可 |
| --- | --- | --- |
| MineColonies | 1.20.1-1.1.605-BETA | GPL-3.0（ldtteam） |
| BlockUI | 1.20.1-1.0.139-BETA | GPL-3.0（ldtteam） |
| Structurize | 1.20.1-1.0.740-BETA | GPL-3.0（ldtteam） |
| Domum-Ornamentum | 1.20.1-1.0.184-BETA | GPL-3.0（ldtteam） |

本模组仅以**运行时依赖**方式使用上述模组（调用其公开 API，不打包、不分发其代码或二进制），符合 GPL-3.0 的"聚合/独立作品"关系；本模组自身源码以 GPL-3.0 发布。

## 衍生资源署名

以下贴图为 **MineColonies（ldtteam）的衍生作品**，依据 GPL-3.0 随本模组再分发并保持相同许可，特此署名：

```
src/main/resources/assets/minecolonies/textures/entity/citizen/default/secretarymale1_{a,b,d,w}.png
src/main/resources/assets/minecolonies/textures/entity/citizen/default/secretaryfemale1_{a,b,d,w}.png
```

这些贴图由 `tools/gen_secretary_skins.py` 采样 MineColonies 原版市民贴图（`citizenmale1_b` / `citizenfemale1_b`）生成，版权归属 ldtteam，本模组在此基础上以 GPL-3.0 再分发。

## 构建

```bash
# 客户端运行 / 服务端运行 / 资源生成
./gradlew runClient
./gradlew runServer
./gradlew runData

# 打包（生成 build/libs/extracityhall-<version>.jar）
./gradlew build
```

构建要求：Java 17（`gradle.properties` 中已指定 `org.gradle.java.home`）。

## 目录说明

- `src/main/`：模组源码与资源（语言文件、GUI、模型、书记贴图）。
- `tools/`：资源生成工具（`gen_secretary_skins.py` 生成书记贴图，`TexGen.java` 处理图标去背）。
  - `tools/_ref/`、`tools/ref/`：**未纳入版本控制**（`.gitignore` 已排除），其中为从 MineColonies jar 抽取的原版参考贴图（ldtteam 版权），仅本地重跑生成器时引用。
- `LICENSE`：GPL-3.0 全文。

## 配置

经验与自动重排参数见游戏内 `config/exhConfig.cfg`（修改后重启生效）：
`baseXpB`、`settleIntervalTicks`、`cutoffRatio`、`autoAssignEnabled`、`autoAssignPeriodDays`。
