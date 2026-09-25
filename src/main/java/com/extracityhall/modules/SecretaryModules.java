package com.extracityhall.modules;

import com.extracityhall.gui.SecretaryWorkModuleView;
import com.extracityhall.gui.TownHallMinStockView;
import com.extracityhall.jobs.ExtraJobEntries;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;

import java.util.List;

/**
 * 给市政厅挂载「书记工人模块」「最低存量模块」「建造资源模块」。
 *
 * 市政厅的 {@link BuildingEntry} 由 MineColonies 在静态初始化期注册，只挂了
 * TOWNHALL_SETTINGS 一个模块；其 {@code buildingModuleProducers} 是普通 ArrayList，
 * 因此在 common setup（注册表就绪后、存档加载前）追加生产者即可让后续新建的市政厅
 * 自动带上这些模块。
 *
 * <p>模块视图一律使用自定义子类并覆写 {@code isPageVisible() = false}：
 * {@code AbstractModuleWindow} 会给每个可见模块视图在窗口左缘生成侧边标签（图标+按钮），
 * 并在存在任一可见模块时额外生成 "main" 标签，这些入口在原版各分页上属于多余渲染——
 * 对应功能都已在就业次页实现。
 */
public final class SecretaryModules {
    /** 模块 key，不可与 BuildingEntry.ModuleProducer.ALL_MODULES 中已有 key 重复 */
    public static final String SECRETARY_MODULE_KEY = "townhallsecretarywork";
    private static final String MIN_STOCK_MODULE_KEY = "townhallminstock";

    /** 市政厅书记工人模块：1 个岗位，主属性 Knowledge、次属性 Mana，可雨天工作，并携带同步到客户端的经验倍率 */
    public static final BuildingEntry.ModuleProducer<SecretaryWorkModule, SecretaryWorkModuleView> TOWNHALL_SECRETARY_WORK =
        new BuildingEntry.ModuleProducer<>(SECRETARY_MODULE_KEY,
            () -> new SecretaryWorkModule(ExtraJobEntries.SECRETARY.get(), Skill.Knowledge, Skill.Mana, true, building -> 1),
            () -> SecretaryWorkModuleView::new);

    /** 市政厅最低存量模块：数据与原版一致，视图隐藏侧边标签 */
    public static final BuildingEntry.ModuleProducer<MinimumStockModule, TownHallMinStockView> TOWNHALL_MIN_STOCK =
        new BuildingEntry.ModuleProducer<>(MIN_STOCK_MODULE_KEY,
            () -> new MinimumStockModule(),
            () -> TownHallMinStockView::new);

    private static boolean attached = false;

    private SecretaryModules() {
    }

    /**
     * 把书记/最低存量/建造资源三个模块挂到市政厅的建筑条目上。幂等。
     * 必须在注册表就绪之后、任何殖民地建筑对象被创建之前调用。
     */
    public static synchronized void attachToTownHall() {
        if (attached) {
            return;
        }
        attached = true;
        try {
            final List<BuildingEntry.ModuleProducer> producers = ModBuildings.townHall.get().getModuleProducers();
            addIfAbsent(producers, TOWNHALL_SECRETARY_WORK);
            addIfAbsent(producers, TOWNHALL_MIN_STOCK);
        } catch (final Exception e) {
            Log.getLogger().error("[ExtraCityHall] Failed to attach secretary module to town hall", e);
        }
    }

    private static void addIfAbsent(final List<BuildingEntry.ModuleProducer> producers,
                                    final BuildingEntry.ModuleProducer producer) {
        if (producers == null || producers.contains(producer)) {
            return;
        }
        producers.add(producer);
    }
}
