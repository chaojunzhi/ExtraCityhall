package com.extracityhall.jobs;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 书记 (Secretary)：市政厅专属职业。
 *
 * 主属性 Knowledge(知识)、次属性 Mana(智力) 由 {@code SecretaryModules#TOWNHALL_SECRETARY_WORK}
 * 声明；本职业本身不执行采集/制造类 AI，仅驻守市政厅，为殖民地提供全局经验加成。
 */
public class JobSecretary extends AbstractJob<EntityAISecretary, JobSecretary> {
    public JobSecretary(final ICitizenData entity) {
        super(entity);
    }

    @NotNull
    @Override
    public ResourceLocation getModel() {
        return ExtraJobEntries.SECRETARY_ID;
    }

    @Override
    public EntityAISecretary generateAI() {
        return new EntityAISecretary(this);
    }

    /** 书记主/次技能等级，供经验加成公式使用（x=知识，y=智力） */
    public int getKnowledge() {
        return this.getCitizen().getCitizenSkillHandler().getLevel(com.minecolonies.api.entity.citizen.Skill.Knowledge);
    }

    public int getMana() {
        return this.getCitizen().getCitizenSkillHandler().getLevel(com.minecolonies.api.entity.citizen.Skill.Mana);
    }

    /**
     * 书记工作所需的物品：书与笔（minecraft:writable_book）。
     *
     * 语义等同「林务员小屋需要斧头才能正常工作」——是上岗作业的必备物品，
     * 不是建造/升级材料。当前仅用于 GUI 展示（暂且不消耗）。
     */
    public static List<ItemStack> getWorkNeeds() {
        return List.of(new ItemStack(Items.WRITABLE_BOOK));
    }
}
