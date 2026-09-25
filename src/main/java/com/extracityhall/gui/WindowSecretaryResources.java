package com.extracityhall.gui;

import com.extracityhall.jobs.JobSecretary;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 书记「所需资源」GUI：由第 2 页的「当前所需物资」横排按钮打开。
 * 列出书记上岗作业所需物品（书与笔），不是建造/升级材料。
 */
public class WindowSecretaryResources extends AbstractWindowSkeleton {
    private final List<ItemStack> neededItems;

    public WindowSecretaryResources(final BuildingTownHall.View townHallView) {
        super("extracityhall:gui/windowsecretaryresources.xml");
        this.neededItems = JobSecretary.getWorkNeeds();
    }

    @Override
    public void onOpened() {
        super.onOpened();

        final ScrollingList list = findPaneOfTypeByID("neededResources", ScrollingList.class);
        if (list != null) {
            list.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() {
                    return neededItems.size();
                }

                @Override
                public void updateElement(final int index, final Pane rowPane) {
                    final ItemStack stack = neededItems.get(index);
                    final ItemIcon icon = rowPane.findPaneOfTypeByID("resourceIcon", ItemIcon.class);
                    if (icon != null) {
                        icon.setItem(stack);
                    }
                    final Text name = rowPane.findPaneOfTypeByID("resourceName", Text.class);
                    if (name != null) {
                        name.setText(Component.literal(stack.getHoverName().getString()));
                    }
                    final Text qty = rowPane.findPaneOfTypeByID("resourceQty", Text.class);
                    if (qty != null) {
                        qty.setText(Component.literal("x" + stack.getCount()));
                    }
                }
            });
        }
    }
}
