package com.extracityhall.gui;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.views.View;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 市政厅窗口的「就业」书签注入助手。
 *
 * 由 {@link com.extracityhall.event.GreenBookEvents#onScreenOpening} 在任意原版
 * 市政厅分页窗口打开时调用：
 * <ul>
 *   <li>向书签容器右侧镜像位置动态注入「就业」书签 + 右侧 ribbon；</li>
 *   <li>书签点击打开 {@link WindowEmploymentPage}；</li>
 *   <li>不覆盖原版书签跳转：原版跳转目标窗口同样会被统一拦截注入，保证任意页面下
 *       就业书签始终可达。</li>
 * </ul>
 */
public final class EmploymentTabHelper {
    /** 就业书签与右侧缎带的镜像坐标/尺寸：与 windowemployment.xml 右侧书签定义保持一致 */
    private static final int TAB_X = 445;
    private static final int TAB_Y = 71;
    private static final int TAB_SIZE = 17;
    private static final int RIBBON_X = 437;
    private static final int RIBBON_Y = 73;
    private static final int RIBBON_SIZE_X = 31;
    private static final int RIBBON_SIZE_Y = 14;
    /** hover 缎带标题栏坐标/尺寸（与原版 bookmark_medium_ribbon 镜像） */
    private static final int EXT_X = 341;
    private static final int EXT_Y = 73;
    private static final int EXT_SIZE_X = 104;
    private static final int EXT_SIZE_Y = 14;

    /** 动态注入纹理（与原版书签同源、水平翻转） */
    private static final ResourceLocation RIBBON_SHORT_TEX =
        new ResourceLocation("extracityhall", "textures/gui/bookmark_short_ribbon_right_01.png");
    private static final ResourceLocation RIBBON_MEDIUM_TEX =
        new ResourceLocation("extracityhall", "textures/gui/bookmark_medium_ribbon_right_01.png");
    /** 就业书签蜡印：本模组自绘的锤子图案（取自锤镰符号、去掉镰刀；沿用原版红蜡印阴刻风格），不再复用原版公民蜡印 */
    private static final ResourceLocation WAX_EMPLOYMENT_TEX =
        new ResourceLocation("extracityhall", "textures/gui/red_wax_employment.png");

    private EmploymentTabHelper() {
    }

    /**
     * 在指定分页窗口注入就业书签。
     *
     * @param window       当前已打开的市政厅分页窗口（AbstractWindowSkeleton 子类）
     * @param townHallView 市政厅视图，用于构造就业页
     */
    public static void inject(final AbstractWindowSkeleton window, final BuildingTownHall.View townHallView) {
        final Button anchor = window.findPaneOfTypeByID("actions", Button.class);
        if (anchor == null) {
            return;
        }
        final View root = anchor.getParent();
        if (root == null) {
            return;
        }

        // 就业书签放右侧：与左侧书签列（x=62）镜像，ribbon 用水平翻转后的右侧贴图
        final Image ribbon = new Image();
        ribbon.setID("employment0Right");
        ribbon.setImage(RIBBON_SHORT_TEX, false);
        ribbon.setPosition(RIBBON_X, RIBBON_Y);
        ribbon.setSize(RIBBON_SIZE_X, RIBBON_SIZE_Y);
        root.addChild(ribbon);

        // hover 缎带标题栏（紫色系，与原版书签 ribbon 同源、水平翻转）：默认隐藏，悬停就业书签时展开
        final ButtonImage employmentExt = new ButtonImage();
        employmentExt.setID("employmentExt");
        employmentExt.setImage(RIBBON_MEDIUM_TEX, false);
        employmentExt.setPosition(EXT_X, EXT_Y);
        employmentExt.setSize(EXT_SIZE_X, EXT_SIZE_Y);
        employmentExt.setText(Component.translatable("gui.extracityhall.employment.tab"));
        employmentExt.setTextAlignment(Alignment.MIDDLE_LEFT);
        employmentExt.setTextOffset(8, 1);
        employmentExt.setVisible(false);
        root.addChild(employmentExt);

        final ButtonImage employment = new ButtonImage();
        employment.setID("employment");
        employment.setImage(WAX_EMPLOYMENT_TEX, false);
        employment.setPosition(TAB_X, TAB_Y);
        employment.setSize(TAB_SIZE, TAB_SIZE);
        employment.setHandler(btn -> new WindowEmploymentPage(townHallView).open());
        employment.setHoverPane(employmentExt);
        // 必须先挂入窗口再设置 tooltip，否则 hover pane 无父窗口导致崩溃
        root.addChild(employment);
        PaneBuilders.singleLineTooltip(Component.translatable("gui.extracityhall.employment.tab"), employment);
    }
}
