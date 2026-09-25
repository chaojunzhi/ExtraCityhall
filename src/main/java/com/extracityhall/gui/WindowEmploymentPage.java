package com.extracityhall.gui;

import com.extracityhall.data.ClientJobPriorities;
import com.extracityhall.data.ColonyJobConfig;
import com.extracityhall.network.ExtraCityHallNetwork;
import com.extracityhall.network.RequestJobPriorityMessage;
import com.extracityhall.network.SetAssignModeMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.Tooltip;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.View;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.Network;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.minecolonies.core.client.gui.WindowHireWorker;
import com.minecolonies.core.client.gui.WindowHutAllInventory;
import com.minecolonies.core.client.gui.WindowHutNameEntry;
import com.minecolonies.core.client.gui.modules.MinimumStockModuleWindow;
import com.minecolonies.core.client.gui.townhall.WindowAlliancePage;
import com.minecolonies.core.client.gui.townhall.WindowCitizenPage;
import com.minecolonies.core.client.gui.townhall.WindowInfoPage;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import com.minecolonies.core.client.gui.townhall.WindowPermissionsPage;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.client.gui.townhall.WindowStatsPage;
import com.minecolonies.core.colony.buildings.moduleviews.MinimumStockModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import com.minecolonies.core.network.messages.server.colony.building.ChangeDeliveryPriorityMessage;
import com.minecolonies.core.network.messages.server.colony.building.ForcePickupMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.RecallCitizenMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 市政厅「就业」页。
 * <ul>
 *   <li>左侧：常驻殖民地地图（市政厅为原点，色点表示各建筑相对位置）</li>
 *   <li>右侧总览：标题「总览」，下方显示「当前居民平均匹配度」，再下方为全部公民评分列表（仅展示，隐藏雇佣/解雇按钮，未就业匹配度记 0）</li>
 *   <li>右侧建筑详情：标题为建筑本地化名，下方显示建筑等级与坐标，再下方为该建筑公民列表（可解雇）+ 未分配公民（可雇佣/跨建筑调岗）</li>
 *   <li>评分 = 1 * 主属性等级 + 0.5 * 次属性等级；排序按评分降序（同分按姓名升序）</li>
 * </ul>
 *
 * 书签跳转直接指向原版窗口：原版窗口打开时会被
 * {@link com.extracityhall.event.GreenBookEvents#onScreenOpening} 统一拦截并注入
 * 就业书签，无需再经过本模组包装类。
 */
public class WindowEmploymentPage extends AbstractWindowSkeleton {
    private final BuildingTownHall.View townHallView;
    private final IColonyView colony;
    private final List<ICitizenDataView> allCitizens = new ArrayList<>();
    private final List<IBuildingView> workerBuildings = new ArrayList<>();
    private final List<CitizenRow> currentCitizens = new ArrayList<>();
    /** 预构建缓存：公民id → 当前工作模块，避免每次刷新全量遍历建筑×模块×在职列表 */
    private final Map<Integer, WorkerBuildingModuleView> citizenToModule = new HashMap<>();

    private Text contentTitle;    // 右侧视图标题（总览 / 建筑本地化名 · 职业名）
    private ScrollingList citizenList;
    private Text infoLine;

    // 第 2 页右侧：分配模式按钮（原版 / 未就业最优 / 全体最优），点击循环切换
    private ButtonImage assignModeButton;
    /** 玩家刚选择、尚未被服务端同步确认的模式：避免显示被回包“弹回”上一个值。 */
    private Integer pendingMode = null;

    /** 分配模式的语言键，顺序必须与 {@link ColonyJobConfig} 的 MODE_* 常量一致。 */
    private static final String[] MODE_LABEL_KEYS = {
        "gui.extracityhall.employment.mode.vanilla",
        "gui.extracityhall.employment.mode.unemployed",
        "gui.extracityhall.employment.mode.all"
    };

    // 地图视图控件
    private View mapPane;
    private View mapLayer;

    // 第 2 页（市政厅管理 / 书记）控件
    private Text secretaryTitle;
    private Text secretaryCitizens;
    private Text prioValue;
    /** 第 1 页的「就业」书签标题条：第 2 页不再显示 */
    private ButtonImage titleRibbon;
    private int prio;

    /** 循环视图索引：0=总览，1..N=各建筑（由 ↺ 按钮递增循环）；殖民地地图为左侧常驻面板，不参与右侧循环 */
    private int viewIndex = 0;
    private IBuildingView selectedBuilding;
    /** 选中建筑内的第几个「职业岗位」：点击同一建筑时在其多个职业间循环（林务员 → 果园管理员 → 林务员…） */
    private int selectedJobIndex = 0;

    /** 地图色点调色板（ARGB），按建筑类型名哈希取色，同类型同色 */
    private static final int[] DOT_COLORS = {
        0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00,
        0xFF8E24AA, 0xFF00ACC1, 0xFFD81B60, 0xFF6D4C41,
        0xFF3949AB, 0xFFFDD835
    };

    /** 地图色块热区按钮纹理：1×1 全透明，仅承载悬浮 Tooltip 与点击跳转，不覆盖色块颜色 */
    private static final ResourceLocation HIT_TEX =
        new ResourceLocation("extracityhall", "textures/gui/transparent_pixel.png");

    /** 地图刻度线纹理：1×1 灰色像素点 */
    private static final ResourceLocation GRAY_TEX =
        new ResourceLocation("extracityhall", "textures/gui/gray_pixel.png");

    // ---------- 地图几何常量（与 windowemployment.xml 中 mapPane 170×170 对齐） ----------
    private static final int MAP_GRID = 44;   // 44×44 网格
    private static final int MAP_CELL = 4;    // 每格 4×4 方块
    private static final int MAP_HALF = 88;   // 展示范围 ±88 格
    private static final int MAP_CELL_PX = 3; // 每格 3px
    private static final int MAP_PX = MAP_GRID * MAP_CELL_PX;         // 132px
    private static final int MAP_START = (170 - MAP_PX) / 2;          // 19（网格在 170 画布内居中）
    private static final int MAP_CENTER = MAP_START + (MAP_GRID / 2) * MAP_CELL_PX; // 85（网格轴心）
    private static final int MAP_TICK = 16;   // 刻度间隔（方块）

    /** 一行公民条目：公民 + 评分（预格式化）+ 各视图相关的模块引用（构建行时一次算好，渲染直接取用） */
    private static class CitizenRow {
        final ICitizenDataView citizen;
        final double score;
        final String scoreText;
        final WorkerBuildingModuleView module;         // 当前工作模块（总览行 / 建筑详情已分配行）
        final WorkerBuildingModuleView bestModule;     // 建筑详情中评分最高的可分配模块（用于雇佣/调岗）
        final WorkerBuildingModuleView assignedModule; // 建筑详情中实际分配到的模块（用于解雇）
        final String ownScoreText;                     // 当前职业匹配度（建筑详情行内红色显示；无职业/当前员工为 null 不显示）

        CitizenRow(final ICitizenDataView citizen, final double score, final WorkerBuildingModuleView module,
                   final WorkerBuildingModuleView bestModule, final WorkerBuildingModuleView assignedModule,
                   final String ownScoreText) {
            this.citizen = citizen;
            this.score = score;
            this.scoreText = String.format("%.1f", score);
            this.module = module;
            this.bestModule = bestModule;
            this.assignedModule = assignedModule;
            this.ownScoreText = ownScoreText;
        }
    }

    public WindowEmploymentPage(final BuildingTownHall.View townHallView) {
        super("extracityhall:gui/windowemployment.xml");
        this.townHallView = townHallView;
        this.colony = townHallView.getColony();
    }

    // ---------- 数据 ----------

    private void loadData() {
        allCitizens.clear();
        allCitizens.addAll(colony.getCitizens().values());

        workerBuildings.clear();
        for (final IBuildingView building : colony.getBuildings()) {
            if (!building.getModuleViews(WorkerBuildingModuleView.class).isEmpty()) {
                workerBuildings.add(building);
            }
        }

        // 预构建 公民id → 工作模块 映射，避免每次刷新全量遍历（公民只会在一个模块工作，putIfAbsent 安全）
        citizenToModule.clear();
        for (final IBuildingView building : workerBuildings) {
            for (final WorkerBuildingModuleView module : building.getModuleViews(WorkerBuildingModuleView.class)) {
                for (final int citizenId : module.getAssignedCitizens()) {
                    citizenToModule.putIfAbsent(citizenId, module);
                }
            }
        }
    }

    /** 评分 = 1 * 主属性 + 0.5 * 次属性 */
    private double score(final ICitizenDataView citizen, final WorkerBuildingModuleView module) {
        if (module == null) {
            return 0.0;
        }
        final Skill primary = module.getPrimarySkill();
        final Skill secondary = module.getSecondarySkill();
        double result = 0.0;
        if (primary != null) {
            result += citizen.getCitizenSkillHandler().getLevel(primary);
        }
        if (secondary != null) {
            result += 0.5 * citizen.getCitizenSkillHandler().getLevel(secondary);
        }
        return result;
    }

    /** 公民当前工作模块（查预构建缓存），无职业返回 null */
    private WorkerBuildingModuleView findModuleForCitizen(final ICitizenDataView citizen) {
        return citizenToModule.get(citizen.getId());
    }

    /**
     * 当前选中建筑的第 {@code selectedJobIndex} 个职业模块（如林务员小屋的「林务员」/「果园管理员」）。
     *
     * <p>索引越界（建筑被摧毁、模块数变化）时自动回退到第 0 个；无模块返回 null。
     */
    private WorkerBuildingModuleView selectedModule() {
        if (selectedBuilding == null) {
            return null;
        }
        final List<WorkerBuildingModuleView> modules = selectedBuilding.getModuleViews(WorkerBuildingModuleView.class);
        if (modules.isEmpty()) {
            return null;
        }
        if (selectedJobIndex < 0 || selectedJobIndex >= modules.size()) {
            selectedJobIndex = 0;
        }
        return modules.get(selectedJobIndex);
    }

    // ---------- 窗口生命周期 ----------

    @Override
    public void onOpened() {
        super.onOpened();

        contentTitle = findPaneOfTypeByID("contentTitle", Text.class);
        citizenList = findPaneOfTypeByID("citizenList", ScrollingList.class);
        infoLine = findPaneOfTypeByID("infoLine", Text.class);
        mapPane = findPaneOfTypeByID("mapPane", View.class);
        mapLayer = findPaneOfTypeByID("mapLayer", View.class);

        secretaryTitle = findPaneOfTypeByID("secretaryTitle", Text.class);
        secretaryCitizens = findPaneOfTypeByID("secretaryCitizens", Text.class);
        prioValue = findPaneOfTypeByID("prioValue", Text.class);
        titleRibbon = findPaneOfTypeByID("titleRibbon", ButtonImage.class);
        assignModeButton = findPaneOfTypeByID("assignMode", ButtonImage.class);

        // 书签全部跳原版窗口：原版窗口由 ScreenEvent.Opening 统一拦截注入就业书签，就业书签始终可达
        registerButton("actions", () -> new WindowMainPage(townHallView).open());
        registerButton("infopage", () -> new WindowInfoPage(townHallView).open());
        registerButton("permissions", () -> new WindowPermissionsPage(townHallView).open());
        registerButton("citizens", () -> new WindowCitizenPage(townHallView).open());
        registerButton("happiness", () -> new WindowStatsPage(townHallView).open());
        registerButton("alliances", () -> new WindowAlliancePage(townHallView).open());
        registerButton("settings", () -> new WindowSettings(townHallView).open());

        // 本模组接管原版自动分配：最优分配按钮 + 职业优先级滑块入口
        registerButton("optimizeAssign", this::onOptimizeAssign);
        registerButton("jobPriority", this::onOpenJobPriority);

        // 第 2 页右侧「分配模式」循环按钮；请求服务端快照（优先级 + 模式）以初始化显示
        registerButton("assignMode", this::onCycleAssignMode);
        updateAssignModeLabel();
        ExtraCityHallNetwork.CHANNEL.sendToServer(new RequestJobPriorityMessage(colony.getID()));

        setupSecretaryPage();

        loadData();
        setupLists();
        refresh();
        // 左侧殖民地地图常驻：打开窗口即渲染一次
        renderMap();
        // 初始化翻页状态：AbstractWindowSkeleton 已按 XML 的 pages/nextPage/prevPage 自动绑定；
        // 第 1 页隐藏 prevPage，第 2 页显示 prevPage，pageNum 显示 当前页/总页数
        setPage(false, 0);
    }

    // ---------- 第 2 页：市政厅管理（书记） ----------

    /** 注册第 2 页按钮，行为与原版工人小屋一致 */
    private void setupSecretaryPage() {
        this.prio = townHallView.getBuildingDmPrio();

        // 左页：管理工人 / 召回工人；标题行小图标：物品栏汇总 / 信息 / 重命名
        // 说明：市政厅本身已是完整建筑，不再提供「建筑选项」入口
        registerButton("workerHire", this::hireWorkerClicked);
        registerButton("recall", () -> Network.getNetwork().sendToServer(new RecallCitizenMessage(townHallView)));
        registerButton("info", () -> new WindowSecretaryHelp(townHallView).open());
        registerButton("editName", () -> new WindowHutNameEntry(townHallView).open());
        registerButton("allinventory", () -> new WindowHutAllInventory(townHallView, this).open());

        // 右页：最低存量 / 取货优先级
        registerButton("minstock", this::openMinStockWindow);
        registerButton("neededResourcesBtn", () -> new WindowSecretaryResources(townHallView).open());
        registerButton("requestPickup", () -> Network.getNetwork().sendToServer(new ForcePickupMessage(townHallView)));
        registerButton("deliveryPrioUp", () -> changeDeliveryPriority(true));
        registerButton("deliveryPrioDown", () -> changeDeliveryPriority(false));

    }

    /** 最优分配：在客户端发起 {@code /exh assign}（服务端求解并落地指派，全量重排） */
    private void onOptimizeAssign() {
        final net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand("exh assign");
        }
    }

    /** 打开「职业优先级」滑块窗口（随殖民地存档持久化的 G） */
    private void onOpenJobPriority() {
        Minecraft.getInstance().setScreen(new JobPriorityScreen(Minecraft.getInstance().screen, colony.getID()));
    }

    /** 当前生效的分配模式：玩家刚点的优先，否则用服务端同步回来的值。 */
    private int currentMode() {
        if (pendingMode != null) {
            return pendingMode;
        }
        return ClientJobPriorities.colonyId() == colony.getID()
            ? ClientJobPriorities.mode() : ColonyJobConfig.MODE_ALL;
    }

    /** 点击「分配模式」按钮：在 原版 → 未就业最优 → 全体最优 之间循环并同步到服务端。 */
    private void onCycleAssignMode() {
        final int next = (currentMode() + 1) % MODE_LABEL_KEYS.length;
        pendingMode = next;
        updateAssignModeLabel();
        ExtraCityHallNetwork.CHANNEL.sendToServer(new SetAssignModeMessage(colony.getID(), next));
    }

    /** 把当前模式名显示到按钮上。 */
    private void updateAssignModeLabel() {
        if (assignModeButton == null) {
            return;
        }
        final int mode = currentMode();
        assignModeButton.setText(Component.translatable(MODE_LABEL_KEYS[mode]));
    }

    /** 每帧校正显示：以玩家刚选的为准，服务端同步确认后交还给同步值。 */
    @Override
    public void onUpdate() {
        super.onUpdate();
        if (assignModeButton == null) {
            return;
        }
        if (pendingMode != null && pendingMode == ClientJobPriorities.mode()
                && ClientJobPriorities.colonyId() == colony.getID()) {
            pendingMode = null; // 与服务端一致，交还给同步值
        }
        updateAssignModeLabel();
    }

    /** 书记列表（每次刷新第 2 页时重建） */
    private final List<ICitizenDataView> secretaryWorkers = new ArrayList<>();

    private void openMinStockWindow() {
        final List<MinimumStockModuleView> moduleViews = townHallView.getModuleViews(MinimumStockModuleView.class);
        if (moduleViews.isEmpty()) {
            return;
        }
        new MinimumStockModuleWindow(townHallView, moduleViews.get(0)).open();
    }

    private void hireWorkerClicked() {
        if (!townHallView.allowsAssignment()) {
            Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("com.minecolonies.coremod.gui.workerhuts.level0"), false);
            return;
        }
        new WindowHireWorker(colony, townHallView.getPosition()).open();
    }

    private void changeDeliveryPriority(final boolean up) {
        if (up) {
            if (prio != 10) {
                prio++;
            }
        } else {
            if (prio != 0) {
                prio--;
            }
        }
        Network.getNetwork().sendToServer(new ChangeDeliveryPriorityMessage(townHallView, up));
        updatePriorityLabel();
    }

    private void updatePriorityLabel() {
        if (prioValue == null) {
            return;
        }
        final MutableComponent text = prio == 0
            ? Component.translatable("com.minecolonies.coremod.gui.workerhuts.deliveryprio.never")
            : Component.literal(prio + "/10");
        prioValue.setText(text);
    }

    /** 刷新第 2 页内容：标题(市政厅 等级)、就职公民、当前所需物资、书记倍率、取货优先级 */
    private void refreshSecretaryPage() {
        secretaryWorkers.clear();
        secretaryWorkers.addAll(SecretaryView.assignedSecretaries(townHallView));

        if (secretaryTitle != null) {
            secretaryTitle.setText(Component.translatable(townHallView.getBuildingDisplayName())
                .append(Component.literal(" " + townHallView.getBuildingLevel())));
        }

        if (secretaryCitizens != null) {
            final StringBuilder names = new StringBuilder();
            for (final ICitizenDataView citizen : secretaryWorkers) {
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(citizen.getName());
            }
            secretaryCitizens.setText(Component.translatable("gui.extracityhall.secretary.citizens",
                names.length() == 0 ? Component.translatable("gui.extracityhall.secretary.none").getString() : names.toString()));
        }

        updatePriorityLabel();
    }

    @Override
    public void setPage(final boolean relative, final int page) {
        super.setPage(relative, page);
        // 「就业」书签标题条属于第 1 页，次页不遗留
        if (titleRibbon != null && switchView != null) {
            final Pane current = switchView.getCurrentView();
            titleRibbon.setVisible(current == null || "pageEmployment".equals(current.getID()));
        }
        refreshSecretaryPage();
    }

    private void setupLists() {
        // 右侧公民列表（总览 / 建筑详情共用）
        citizenList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return currentCitizens.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane) {
                final CitizenRow row = currentCitizens.get(index);

                final Text name = rowPane.findPaneOfTypeByID("citizenName", Text.class);
                final Text job = rowPane.findPaneOfTypeByID("citizenJob", Text.class);

                // 公民名（A 图样式）；职业名与匹配度同行：总览蓝色整行，建筑详情为 建筑匹配度(绿) + 当前职业匹配度(红)
                name.setText(Component.literal(row.citizen.getName()));
                String jobName;
                if (row.module != null && row.module.getJobEntry() != null) {
                    jobName = Component.translatable(row.module.getJobEntry().getTranslationKey()).getString();
                } else {
                    jobName = Component.translatable("gui.extracityhall.employment.unemployed").getString();
                }
                final MutableComponent jobText = Component.literal(jobName);
                if (viewIndex == 0) {
                    jobText.append(Component.literal("    " + row.scoreText));
                } else {
                    jobText.append(Component.literal("  " + row.scoreText).withStyle(ChatFormatting.GREEN));
                    if (row.ownScoreText != null) {
                        jobText.append(Component.literal("  " + row.ownScoreText).withStyle(ChatFormatting.RED));
                    }
                }
                job.setText(jobText);

                final Button fire = rowPane.findPaneOfTypeByID("fire", Button.class);
                final Button hire = rowPane.findPaneOfTypeByID("hire", Button.class);

                if (viewIndex == 0) {
                    // 总览：仅展示信息，隐藏雇佣/解雇按钮
                    fire.setVisible(false);
                    hire.setVisible(false);
                    return;
                }

                // 建筑详情：已分配公民可解雇；未分配公民可雇佣（含其他建筑在职，允许调岗）
                fire.setVisible(true);
                hire.setVisible(true);
                final WorkerBuildingModuleView assigned = row.assignedModule;
                final boolean canFire = assigned != null;
                fire.setEnabled(canFire);
                fire.setHandler(btn -> {
                    if (assigned != null && assigned.getAssignedCitizens().contains(row.citizen.getId())) {
                        assigned.removeCitizen(row.citizen);
                        loadData();
                        refresh();
                    }
                });

                final WorkerBuildingModuleView best = row.bestModule;
                final boolean canHire = !canFire && best != null && !best.isFull() && best.canAssign(row.citizen);
                hire.setEnabled(canHire);
                hire.setHandler(btn -> {
                    if (canHire) {
                        best.addCitizen(row.citizen);
                        loadData();
                        refresh();
                    }
                });
            }
        });

        // 列表就绪后无需额外初始化：onOpened 末尾 refresh() 会渲染首屏（总览）
    }

    // ---------- 视图刷新 ----------

    private void refresh() {
        currentCitizens.clear();

        // 由循环索引派生当前视图；选中建筑若已失效（被摧毁等）自动回退总览。
        // 注意：viewIndex 合法范围为 1..size（viewIndex-1 为建筑列表索引），
        // 最后一个建筑的 viewIndex 恰等于 size，必须用 > 判断越界，>= 会把末位建筑误判回总览。
        if (viewIndex > workerBuildings.size()) {
            viewIndex = 0;
        }
        selectedBuilding = viewIndex == 0 ? null : workerBuildings.get(viewIndex - 1);

        if (viewIndex == 0) {
            // 总览：全部公民按当前职业评分降序（高分在前），信息行显示当前居民平均匹配度
            for (final ICitizenDataView citizen : allCitizens) {
                final WorkerBuildingModuleView module = findModuleForCitizen(citizen);
                currentCitizens.add(new CitizenRow(citizen, score(citizen, module), module, null, null, null));
            }
            sortRows(currentCitizens);
            contentTitle.setText(Component.translatable("gui.extracityhall.employment.overview"));

            double sum = 0.0;
            int employed = 0;
            for (final CitizenRow row : currentCitizens) {
                if (row.module != null) {
                    sum += row.score;
                    employed++;
                }
            }
            final double avg = employed == 0 ? 0.0 : sum / employed;
            infoLine.setText(Component.translatable("gui.extracityhall.employment.avgmatch", String.format("%.1f", avg)));
        } else {
            // 建筑详情：一切都围绕「当前选中的职业岗位」（多职业建筑可点击地图循环切换）
            final WorkerBuildingModuleView sel = selectedModule();

            // 右侧标题：建筑名 · 当前职业名（如「林务员小屋 · 果园管理员」）；信息行为等级与坐标
            final MutableComponent title = Component.translatable(selectedBuilding.getBuildingDisplayName());
            if (sel != null && sel.getJobEntry() != null) {
                title.append(Component.literal(" · "))
                    .append(Component.translatable(sel.getJobEntry().getTranslationKey()));
            }
            contentTitle.setText(title);
            infoLine.setText(Component.literal("LV" + selectedBuilding.getBuildingLevel()
                + " (" + selectedBuilding.getPosition().getX()
                + "," + selectedBuilding.getPosition().getZ()
                + "," + selectedBuilding.getPosition().getY() + ")"));

            if (sel != null) {
                // 该职业的在职员工（可解雇）
                final Set<Integer> seen = new HashSet<>();
                for (final int citizenId : sel.getAssignedCitizens()) {
                    if (!seen.add(citizenId)) {
                        continue;
                    }
                    final ICitizenDataView citizen = colony.getCitizens().get(citizenId);
                    if (citizen != null) {
                        currentCitizens.add(new CitizenRow(citizen, score(citizen, sel), sel, null, sel, null));
                    }
                }
                // 其余公民：按对「当前选中职业」的匹配度排序，雇佣即进入该职业
                for (final ICitizenDataView citizen : allCitizens) {
                    if (seen.contains(citizen.getId())) {
                        continue;
                    }
                    final WorkerBuildingModuleView own = findModuleForCitizen(citizen);
                    final String ownScoreText = own == null ? null : String.format("%.1f", score(citizen, own));
                    currentCitizens.add(new CitizenRow(citizen, score(citizen, sel), own, sel, null, ownScoreText));
                }
                // 排序：第 1 位永远是当前员工（内部按匹配度降序），其余公民同样按匹配度降序
                currentCitizens.sort((a, b) -> {
                    final boolean aCur = a.assignedModule != null;
                    final boolean bCur = b.assignedModule != null;
                    if (aCur != bCur) {
                        return aCur ? -1 : 1;
                    }
                    return Double.compare(b.score, a.score);
                });
            }
        }

        citizenList.refreshElementPanes();
    }

    /**
     * 以市政厅为中心绘制左侧常驻殖民地地图：仅展示 ±88 格（176×176）坐标范围，
     * 划分为 44×44 网格（每格 4×4 方块），网格内有建筑中心则整格上色（按建筑名哈希取色）。
     * 地图画布(mapPane 的 mapLayer)尺寸 170x170，网格 44×44×3px=132px 居中，轴心(85,85)。
     */
    private void renderMap() {
        // BlockUI View 无 clearChildren()，逐个移除旧色块
        for (final Pane child : new ArrayList<>(mapLayer.getChildren())) {
            mapLayer.removeChild(child);
        }

        final BlockPos origin = townHallView.getPosition();

        // 网格内容表：仅记录含建筑中心的格子（范围外忽略），多建筑同格取首个
        final Map<Integer, IBuildingView> cellBuildings = new HashMap<>();
        for (final IBuildingView b : colony.getBuildings()) {
            if (b == null || b.getPosition() == null) {
                continue;
            }
            final int dx = b.getPosition().getX() - origin.getX();
            final int dz = b.getPosition().getZ() - origin.getZ();
            if (Math.abs(dx) > MAP_HALF || Math.abs(dz) > MAP_HALF) {
                continue;
            }
            final int gx = (dx + MAP_HALF) / MAP_CELL;
            final int gz = (dz + MAP_HALF) / MAP_CELL;
            if (gx < 0 || gx >= MAP_GRID || gz < 0 || gz >= MAP_GRID) {
                continue;
            }
            cellBuildings.putIfAbsent(gz * MAP_GRID + gx, b);
        }

        // 轴刻度线：每 16 方块一条，按世界坐标 16 倍数对齐（刻度间距即方块距离，不标数字），灰色 1px 细线
        addTickLines(origin, true);   // X 轴刻度（竖直短线）
        addTickLines(origin, false);  // Z 轴刻度（水平短线）

        // 比例尺：标注刻度间距=16 方块（左下角灰色小字）
        final Text scaleLabel = new Text();
        scaleLabel.setSize(70, 5);
        scaleLabel.setPosition(3, 158);
        scaleLabel.setTextScale(0.5);
        scaleLabel.setColors(0xFFA0A0A0);
        scaleLabel.setText(Component.literal("每刻度16方块"));
        scaleLabel.setVisible(true);
        mapLayer.addChild(scaleLabel);

        // 市政厅标记：原点橙色方块（3×3 与网格一致，不放大）+「市政厅」文字
        final Text thCell = new Text();
        thCell.setSize(MAP_CELL_PX, MAP_CELL_PX);
        thCell.setPosition(MAP_CENTER, MAP_CENTER);
        thCell.setTextScale(0.33);
        thCell.setColors(0xFFFB8C00);
        thCell.setText(Component.literal("\u2588"));
        thCell.setVisible(true);
        mapLayer.addChild(thCell);

        final Text thLabel = new Text();
        thLabel.setSize(16, 5);
        thLabel.setPosition(MAP_CENTER + MAP_CELL_PX + 1, MAP_CENTER);
        thLabel.setTextScale(0.5);
        thLabel.setColors(0xFF000000);
        thLabel.setText(Component.literal("市政厅"));
        thLabel.setVisible(true);
        mapLayer.addChild(thLabel);

        // 色块与热区按钮：最后添加（children 尾部优先命中点击），确保不被刻度线/比例尺/市政厅标记遮挡
        for (final Map.Entry<Integer, IBuildingView> e : cellBuildings.entrySet()) {
            final int idx = e.getKey();
            final int gx = idx % MAP_GRID;
            final int gz = idx / MAP_GRID;
            final IBuildingView building = e.getValue();
            final int color = DOT_COLORS[Math.floorMod(building.getBuildingDisplayName().hashCode(), DOT_COLORS.length)];
            final int px = MAP_START + gx * MAP_CELL_PX;
            final int pz = MAP_START + gz * MAP_CELL_PX;

            final Text cell = new Text();
            cell.setSize(MAP_CELL_PX, MAP_CELL_PX);
            cell.setPosition(px, pz);
            cell.setTextScale(0.33); // 字号 9px×0.33≈3px，与容器等高，避免裁剪不可见
            cell.setColors(color);
            cell.setText(Component.literal("\u2588"));
            cell.setVisible(true);
            mapLayer.addChild(cell);

            // 色块热区：1×1 透明按钮叠在色块上，悬浮显示「建筑名 + 世界坐标」（Tooltip），点击跳转右侧对应建筑详情
            final ButtonImage hit = new ButtonImage();
            hit.setImage(HIT_TEX, false);
            hit.setSize(MAP_CELL_PX, MAP_CELL_PX);
            hit.setPosition(px, pz);
            hit.setText(Component.empty());
            hit.setHandler(btn -> {
                // 跳转目标：先重拉数据保证 workerBuildings 为最新，再按建筑坐标 ID 匹配新索引。
                // 不能沿用旧 indexOf 索引——列表重建/顺序变化/视图实例刷新后旧索引会失效，
                // 轻则指向错误建筑，重则越界回退总览。
                final BlockPos target = building.getPosition();
                loadData();
                int bi = -1;
                for (int i = 0; i < workerBuildings.size(); i++) {
                    if (target.equals(workerBuildings.get(i).getPosition())) {
                        bi = i;
                        break;
                    }
                }
                if (bi >= 0) {
                    final int newView = bi + 1;
                    if (newView == viewIndex) {
                        // 再次点击同一建筑：在该建筑的多个职业之间循环切换
                        // （林务员 → 果园管理员 → 林务员…），解决「同一楼第二个职业雇不了」的问题
                        final int count = workerBuildings.get(bi).getModuleViews(WorkerBuildingModuleView.class).size();
                        if (count > 1) {
                            selectedJobIndex = (selectedJobIndex + 1) % count;
                        }
                    } else {
                        viewIndex = newView;
                        selectedJobIndex = 0; // 换建筑时回到它的第 1 个职业
                    }
                    refresh();
                    renderMap();
                    // 跳转到第 1 页（建筑详情位于第 1 页）
                    setPage(false, 0);
                }
            });
            mapLayer.addChild(hit);
            final Tooltip tip = new Tooltip();
            tip.setText(Component.translatable(building.getBuildingDisplayName())
                .append(Component.literal("  (" + building.getPosition().getX() + ", " + building.getPosition().getZ() + ")")));
            hit.setHoverPane(tip);
        }
    }

    /**
     * 沿坐标轴绘制刻度短线：每 MAP_TICK 方块一条，按世界坐标 MAP_TICK 倍数对齐（不标数字）。
     * 短线垂直于对应轴：X 轴刻度为竖直 1×5，Z 轴刻度为水平 5×1，均以网格轴心 MAP_CENTER 居中。
     *
     * @param origin 市政厅中心
     * @param onX    true 沿 X 轴（off 按 X 坐标），false 沿 Z 轴（off 按 Z 坐标）
     */
    private void addTickLines(final BlockPos origin, final boolean onX) {
        final int center = onX ? origin.getX() : origin.getZ();
        final int min = center - MAP_HALF;
        final int max = center + MAP_HALF;
        final int first = (int) Math.ceil((double) min / MAP_TICK) * MAP_TICK;
        final int last = (int) Math.floor((double) max / MAP_TICK) * MAP_TICK;
        final int tickCenter = MAP_CENTER - 2; // 5px 短线以轴心 85 居中（覆盖 83~87）
        for (int w = first; w <= last; w += MAP_TICK) {
            final int off = w - center;
            final int px = MAP_START + ((off + MAP_HALF) / MAP_CELL) * MAP_CELL_PX;
            final Image tick = new Image();
            tick.setImage(GRAY_TEX, false);
            tick.setSize(onX ? 1 : 5, onX ? 5 : 1);
            tick.setPosition(onX ? px : tickCenter, onX ? tickCenter : px);
            tick.setVisible(true);
            mapLayer.addChild(tick);
        }
    }

    /** 评分降序，同分按姓名升序，保证排序稳定 */
    private void sortRows(final List<CitizenRow> rows) {
        rows.sort(Comparator.comparingDouble((CitizenRow r) -> r.score).reversed()
            .thenComparing(r -> r.citizen.getName()));
    }
}
