package com.extracityhall.gui;

import com.extracityhall.jobs.ExtraJobEntries;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端：市政厅视图上的书记状态读取（仅客户端调用）。
 */
public final class SecretaryView {
    private SecretaryView() {
    }

    /** 该建筑上的书记工人模块视图（可能为空） */
    public static List<WorkerBuildingModuleView> secretaryModules(final IBuildingView view) {
        final List<WorkerBuildingModuleView> result = new ArrayList<>();
        if (view == null) {
            return result;
        }
        for (final WorkerBuildingModuleView module : view.getModuleViews(WorkerBuildingModuleView.class)) {
            if (module != null && module.getJobEntry() != null
                    && ExtraJobEntries.SECRETARY_ID.equals(module.getJobEntry().getKey())) {
                result.add(module);
            }
        }
        return result;
    }

    /** 市政厅是否有书记在岗 */
    public static boolean hasSecretary(final ITownHallView townHallView) {
        return !assignedSecretaries(townHallView).isEmpty();
    }

    /** 市政厅在岗的书记公民 */
    public static List<ICitizenDataView> assignedSecretaries(final ITownHallView townHallView) {
        final List<ICitizenDataView> result = new ArrayList<>();
        if (townHallView == null || townHallView.getColony() == null) {
            return result;
        }
        for (final WorkerBuildingModuleView module : secretaryModules(townHallView)) {
            for (final int citizenId : module.getAssignedCitizens()) {
                final ICitizenDataView citizen = townHallView.getColony().getCitizen(citizenId);
                if (citizen != null && !result.contains(citizen)) {
                    result.add(citizen);
                }
            }
        }
        return result;
    }

}
