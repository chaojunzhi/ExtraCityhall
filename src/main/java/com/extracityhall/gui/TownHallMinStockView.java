package com.extracityhall.gui;

import com.minecolonies.core.colony.buildings.moduleviews.MinimumStockModuleView;

/**
 * 市政厅的最低存量模块视图。
 *
 * 覆写 {@link #isPageVisible()} 为 false：{@code AbstractModuleWindow} 会给每个可见模块视图
 * 在窗口左缘生成侧边标签（图标+按钮），且只要存在任一可见模块还会额外生成 "main" 标签，
 * 导致原版各分页（非就业书签）上多出不属于它们的入口。最低存量的内容在就业次页已有实现，
 * 因此这里只保留数据能力、不参与原版侧边标签渲染。
 */
public class TownHallMinStockView extends MinimumStockModuleView {
    @Override
    public boolean isPageVisible() {
        return false;
    }
}
