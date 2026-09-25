package com.extracityhall.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Random;

/**
 * 透明委托包装：持有原 {@link CitizenSkillHandler} 作为 delegate，所有读/写方法转发到它，
 * 仅在 {@link #addXpToSkill} 时按市民所属殖民地的书记倍率乘算。
 *
 * <p>必须 <b>extends CitizenSkillHandler</b>：{@code CitizenData} 的字段是
 * {@code private final CitizenSkillHandler}（具体类），反射替换需类型匹配；原始 handler 作为
 * delegate 保留全部真实数据，故 GUI 读取的是被放大后的真实字段，显示正常。
 *
 * <p>防重复包装由 {@code SecretaryXpEventHandler} 在替换前以 {@code instanceof} 检测本类型实现。
 */
public class SecretarySkillHandlerWrapper extends CitizenSkillHandler {
    private final CitizenSkillHandler delegate;

    public SecretarySkillHandlerWrapper(@NotNull final CitizenSkillHandler delegate) {
        super();
        this.delegate = delegate;
    }

    @Override
    public void addXpToSkill(@NotNull final Skill skill, final double xp, @NotNull final ICitizenData citizen) {
        final IColony colony = citizen.getColony();
        final double factor = SecretaryXpMultiplier.getMultiplier(colony);
        delegate.addXpToSkill(skill, xp * factor, citizen);
    }

    @Override
    public void removeXpFromSkill(@NotNull final Skill skill, final double xp, @NotNull final ICitizenData citizen) {
        delegate.removeXpFromSkill(skill, xp, citizen);
    }

    @Override
    public int getLevel(@NotNull final Skill skill) {
        return delegate.getLevel(skill);
    }

    @Override
    public void incrementLevel(@NotNull final Skill skill, final int amount) {
        delegate.incrementLevel(skill, amount);
    }

    @Override
    public boolean tryLevelUpIntelligence(@NotNull final Random random, final double chance,
                                           @NotNull final ICitizenData citizen) {
        return delegate.tryLevelUpIntelligence(random, chance, citizen);
    }

    @Override
    public void levelUp(@NotNull final ICitizenData citizen) {
        delegate.levelUp(citizen);
    }

    @Override
    public double getTotalXP() {
        return delegate.getTotalXP();
    }

    @Override
    public Map<Skill, Tuple<Integer, Double>> getSkills() {
        return delegate.getSkills();
    }

    @Override
    public void init(final int level) {
        delegate.init(level);
    }

    @Override
    public void init(final IColony colony, final ICitizenData oldCitizen, final ICitizenData newCitizen,
                     final Random random) {
        delegate.init(colony, oldCitizen, newCitizen, random);
    }

    @Override
    public CompoundTag write() {
        return delegate.write();
    }

    @Override
    public void read(@NotNull final CompoundTag tag) {
        delegate.read(tag);
    }
}
