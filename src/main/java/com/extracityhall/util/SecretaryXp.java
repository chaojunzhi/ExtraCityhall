package com.extracityhall.util;

/**
 * 书记经验加成公式（纯数学，双端共用）。
 *
 * 公式：XPincr. = (1 + 0.5 * tanh((x + 0.5 * y) / 100)) * 原本XPincr.
 * 其中 x = Knowledge(知识主属性, 权重1)，y = Mana(智力次属性, 权重0.5)，
 * tanh 结果截断保留两位小数（向下取整）。
 */
public final class SecretaryXp {
    /** tanh 截断时保留的小数位数对应的缩放因子 */
    private static final double TRUNCATE_SCALE = 100.0;
    /** 次属性权重 */
    private static final double SECONDARY_WEIGHT = 0.5;
    /** tanh 输出系数 */
    private static final double TANH_MULTIPLIER = 0.5;
    /** 公式中的线性缩放分母 */
    private static final double SCALE_DIVISOR = 100.0;

    private SecretaryXp() {
    }

    /** 截断保留两位小数（非四舍五入） */
    public static double truncate2(final double value) {
        return Math.floor(value * TRUNCATE_SCALE) / TRUNCATE_SCALE;
    }

    /** tanh((x + 0.5y)/100)，截断两位小数 */
    public static double truncatedTanh(final double knowledge, final double mana) {
        return truncate2(Math.tanh((knowledge + SECONDARY_WEIGHT * mana) / SCALE_DIVISOR));
    }

    /** 经验增量倍率 */
    public static double xpFactor(final double knowledge, final double mana) {
        return 1.0 + TANH_MULTIPLIER * truncatedTanh(knowledge, mana);
    }
}
