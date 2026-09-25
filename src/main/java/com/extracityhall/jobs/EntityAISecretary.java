package com.extracityhall.jobs;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.extracityhall.modules.SecretaryWorkModule;
import com.extracityhall.util.SecretaryXp;
import com.extracityhall.util.SecretaryXpMultiplier;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * 书记工人 AI：完全参照 MineColonies 标准工人（如学生 EntityAIWorkPupil）的实现模式。
 *
 * <p>继承 {@link AbstractEntityAIInteract}（而非最底层的 AbstractAISkeleton），
 * 从而获得基类的全部标准行为：INIT→IDLE 状态迁移、NEEDS_ITEM 到货取件、
 * PAUSED/INVENTORY_FULL 处理，以及每 tick 把当前 AI 状态写入名牌
 * （{@code job.setNameTag(getState().toString())}，名牌显示 Name[状态]）。
 *
 * <p>状态流：IDLE → START_WORKING：用 RequestSystem 请求 1 个书与笔
 * （{@code checkIfRequestForItemExistOrCreateAsync}，到货进入市民背包、作为工具不消耗），
 * 到货后走向市政厅 → WORK：驻守办公，每周期把「本殖民地经验倍率」写入注册表
 * （由 {@code SecretarySkillHandlerWrapper} 在市民自然干活时按倍率乘算，GUI 正常显示）。
 * 书被取走则回到 START_WORKING 重新请求。
 */
public class EntityAISecretary extends AbstractEntityAIInteract<JobSecretary, BuildingTownHall> {

    /**
     * 书记自有状态：驻守办公。名牌会显示 "WORK"。
     * 其余阶段直接复用标准状态 IDLE / START_WORKING（与标准工人一致）。
     */
    private enum SecretaryState implements IAIState {
        WORK;

        @Override
        public boolean isOkayToEat() {
            return true;
        }
    }

    /**
     * Constructor for the AI.
     *
     * @param job the job to fulfill
     */
    public EntityAISecretary(@NotNull final JobSecretary job) {
        super(job);
        super.registerTargets(
            new AITarget(IDLE, START_WORKING, 1),
            new AITarget(START_WORKING, this::startWorkingAtTownHall, TICKS_SECOND),
            new AITarget(SecretaryState.WORK, this::work, TICKS_SECOND)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingTownHall> getExpectedBuildingClass() {
        return BuildingTownHall.class;
    }

    /**
     * 确保持有书与笔（不足则通过 RequestSystem 发起异步请求，等待送货员送达），
     * 然后进入驻守办公状态。
     *
     * @return next state to go to.
     */
    private IAIState startWorkingAtTownHall() {
        if (building == null) {
            resetMultiplier();
            return getState();
        }
        // 背包无书与笔时自动发起异步请求并等待（返回 false），到货后返回 true
        if (!checkIfRequestForItemExistOrCreateAsync(new ItemStack(Items.WRITABLE_BOOK))) {
            return getState();
        }
        // 书已到手即进入驻守办公。书记本职是给全殖民地发放全局被动经验增益，
        // 办公地点即市政厅本身，无需市民寻路站到市政厅方块脚下——
        // MineColonies 的 walkToBuilding() 对市政厅常因寻路/到达范围过紧永远返回 false，
        // 会导致 AI 死循环在 START_WORKING，故此处跳过该硬性门槛。
        return SecretaryState.WORK;
    }

    /**
     * 驻守办公：确认书与笔仍在背包（被取走则回到 START_WORKING 重新请求），
     * 并每周期把本殖民地的经验倍率写入注册表。
     *
     * @return next state to go to.
     */
    private IAIState work() {
        if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), Items.WRITABLE_BOOK)) {
            resetMultiplier();
            return START_WORKING;
        }
        updateMultiplier();
        worker.decreaseSaturationForContinuousAction();
        return getState();
    }

    /** 每周期按知识/智力算 tanh 倍率写入本殖民地映射；factor<=1.0 时置 1.0（无加成） */
    private void updateMultiplier() {
        final IColony colony = job.getColony();
        if (colony == null) {
            return;
        }
        final double factor = SecretaryXp.xpFactor(job.getKnowledge(), job.getMana());
        if (factor <= 1.0) {
            SecretaryXpMultiplier.reset(colony);
            setModuleFactor(1.0);
            return;
        }
        SecretaryXpMultiplier.setMultiplier(colony, factor);
        setModuleFactor(factor);
    }

    /** 书记离岗/丢书：立即清除本殖民地倍率，避免残留加成 */
    private void resetMultiplier() {
        final IColony colony = job.getColony();
        if (colony != null) {
            SecretaryXpMultiplier.reset(colony);
        }
        setModuleFactor(1.0);
    }

    /** 把服务端真实生效倍率写入本市政厅书记模块，随 colony 数据同步到客户端 GUI */
    private void setModuleFactor(final double factor) {
        if (building != null) {
            final Collection<SecretaryWorkModule> modules = building.getModulesByType(SecretaryWorkModule.class);
            if (!modules.isEmpty()) {
                modules.iterator().next().setSecretaryXpFactor(factor);
            }
        }
    }
}
