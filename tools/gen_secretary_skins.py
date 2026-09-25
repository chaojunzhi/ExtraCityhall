#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成书记职业（JobSecretary）的 8 张 128x64 换装贴图：中山装（无帽，默认发型）。

设计依据（用户给定）：
  中山装：立翻领（封闭式翻领）、前襟五粒扣、四个贴袋（上小下大、带袋盖、倒山字形笔架式袋盖）、
          袖口三粒扣、后背整片无破缝。
  女款：参照官方建筑工（builder）男女都是裤装 —— 官方 builderfemale1_b 的 dress 采样区
        1344 格里只有 118 格不透明（farmer 女款是 880 格），所以把 dress（与 breast）整块擦空，
        女书记即与男款一样穿长裤。

模型结构（tools/_parse_model.py 从 jar 内 javap 字节码还原，1.20.1-1.1.605-BETA）：
  head 内层(0,0) deform0 / 外层(32,0) deform0.5（原版=头发）
  body 内(16,16) d0 / 外(16,32) d0.25；right_arm 内(40,16)/外(40,32)；left_arm 内(32,48)/外(48,48)
  right_leg 内(0,16)/外(0,32)；left_leg 内(16,48)/外(0,48)   ——   男臂 4 宽，女臂 3 宽
  女款另有 dress(86,14/92,6/96,0)、breast(64,49/64,55)、HairExtension(56,0)、马尾(86,48)(88,55)

两条关键结论（进游戏截图核对过）：
  1. 外层 deform 0.25 完全罩住内层，原版外层是零散衣褶/袖口装饰且左右臂形状不同 ——
     换装前必须把外层整块擦透明，否则正面两侧手臂会各留一片不对称浅色方块。
  2. head 外层（原版头发层）被中山装内层盖住，人头回到默认发型，不再画任何帽子。

UV 对照（本脚本用到的所有面）：
  躯干 内层(16,16,8,12,4)：上(20,16)-(28,20) 下(28,16)-(36,20) 右(16,20)-(20,32)
                            左(28,20)-(32,32) 前(20,20)-(28,32) 后(32,20)-(40,32)
  右臂 内层(40,16,4,12,4)：外(40,20)-(44,32) 前(44,20)-(48,32) 内(48,20)-(52,32) 后(52,20)-(56,32)
  左臂 内层(32,48,4,12,4)：外(32,52)-(36,64) 前(36,52)-(40,64) 内(40,52)-(44,64) 后(44,52)-(48,64)
  右腿 内层(0,16,4,12,4)：外(0,20) 前(4,20) 内(8,20) 后(12,20)（各 4 宽 x 12 高）
  左腿 内层(16,48,4,12,4)：外(16,52) 前(20,52) 内(24,52) 后(28,52)
  头 外层(32,0)：上(40,0)-(48,8) 下(48,0)-(56,8) 右(32,8)-(40,16) 左(48,8)-(56,16)
                 前(40,8)-(48,16) 后(56,8)-(64,16)
"""
import os
from PIL import Image

# ---------------------------------------------------------------- 调色板（中山装）
SUIT     = (44, 47, 54, 255)      # 中山装衣身：深灰近黑
SUIT_D   = (33, 35, 41, 255)      # 暗面
SEAM     = (25, 27, 32, 255)      # 缝线：领口开缝、门襟压边、袋口、下摆
BUTTON   = (116, 121, 130, 255)   # 扣子（深色衣身上用浅灰才看得见）
COLLAR   = (62, 67, 76, 255)      # 立翻领（靠一道亮带把领子从衣身上分出来）
# 贴袋是缝在衣身外面的一块布，用比衣身亮的色才分得出来；1 texel ≈ 1 单位已无描边余地
POCKET   = (50, 54, 61, 255)      # 袋身
FLAP     = (62, 67, 76, 255)      # 袋盖
TROUSER  = (47, 50, 57, 255)      # 长裤
TROUSER_L = (57, 61, 69, 255)     # 裤缝
SHOE     = (22, 22, 25, 255)      # 皮鞋

SUFFIXES = ["_a", "_b", "_d", "_w"]

# 外层（原版零散衣饰，左右不对称）——整块擦透明，只留内层制服
OUTER_LAYERS = [
    (16, 32, 40, 48),   # body 外层
    (40, 32, 56, 48),   # right_arm 外层
    (48, 48, 64, 64),   # left_arm 外层
    (0, 32, 16, 48),    # right_leg 外层
    (0, 48, 16, 64),    # left_leg 外层
]
# 内层（制服本体）：第四项 skip_skin 表示是否保留原版肤色像素。
# 女款原版是连衣裙 —— 小臂与整条腿都是裸皮，中山装是长袖长裤，必须整片盖住，
# 所以一律 skip_skin=False；手另外按 UV 显式补回（见 paint_hands）。
INNER_LAYERS = [
    (16, 16, 40, 32, SUIT, False),      # body
    (40, 16, 56, 32, SUIT, False),      # right_arm
    (32, 48, 48, 64, SUIT, False),      # left_arm（女臂 3 宽，多出的列无几何，无害）
    (0, 16, 16, 32, TROUSER, False),    # right_leg
    (16, 48, 32, 64, TROUSER, False),   # left_leg
]
# 女款的裙与胸饰：官方 builder 女款正是把 dress 区留空，改成裤装
FEMALE_REMOVE = [
    (86, 0, 128, 32),    # dress 采样区（三大块）
    (64, 49, 86, 61),    # breast 采样区
]


# ---------------------------------------------------------------- 基础工具
def is_skin(p):
    r, g, b, _ = p
    return r > 120 and g > 80 and b < 150 and (r - b) > 30


def tint(p, suffix):
    if suffix == "_b" or not is_skin(p):
        return p
    r, g, b, a = p
    if suffix == "_a":                       # 偏亮
        r, g, b = min(255, r + 20), min(255, g + 14), min(255, b + 8)
    elif suffix == "_d":                     # 偏暗
        f = 0.80
        r, g, b = int(r * f), int(g * f), int(b * f)
    elif suffix == "_w":                     # 苍白
        f = 0.50
        r = int(r + (235 - r) * f)
        g = int(g + (205 - g) * f)
        b = int(b + (175 - b) * f)
    return (r, g, b, a)


def sample_skin(base):
    """从原版贴图的脸部取一个肤色像素，作为手掌颜色（4 个后缀各自的肤色本来就不同）。"""
    for (x, y) in ((10, 10), (13, 10), (9, 14), (14, 14), (10, 9), (13, 9)):
        p = base.getpixel((x, y))
        if len(p) == 3:
            p = p + (255,)
        if p[3] >= 25 and is_skin(p):
            return p
    return (201, 151, 106, 255)


def tint_head(img, suffix):
    if suffix == "_b":
        return
    px = img.load()
    for y in range(0, 16):
        for x in range(0, 64):
            px[x, y] = tint(px[x, y], suffix)


def shade(target, lum):
    """按原图亮度把目标色映射到深->浅，保留原版衣物的褶皱结构。

    中山装是挺括的制服，明暗起伏要比原版便服克制，故系数收在 0.85~1.12。
    """
    f = max(0.0, min(1.0, (lum - 25) / 165))
    k = 0.85 + 0.27 * f
    return tuple(min(255, max(0, int(c * k))) for c in target[:3])


def fill(px, x0, y0, x1, y1, col):
    """填充半开区间 [x0,x1) x [y0,y1)。"""
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = col


def erase(px, x0, y0, x1, y1):
    fill(px, x0, y0, x1, y1, (0, 0, 0, 0))


def strip_outer_layers(img):
    px = img.load()
    for (x0, y0, x1, y1) in OUTER_LAYERS:
        erase(px, x0, y0, x1, y1)


def fill_inner_layers(img):
    """内层面铺底：不透明像素按原亮度重染，透明像素直接填底色；skip_skin 的区域连肤色一起盖。"""
    px = img.load()
    for (x0, y0, x1, y1, target, skip_skin) in INNER_LAYERS:
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, a = px[x, y]
                if a < 25:
                    px[x, y] = target
                elif not (skip_skin and is_skin((r, g, b, a))):
                    lum = r * 0.299 + g * 0.587 + b * 0.114
                    nr, ng, nb = shade(target, lum)
                    px[x, y] = (nr, ng, nb, a)


# ---------------------------------------------------------------- 中山装
def patch_pocket(px, x0, w, y_flap, body_rows):
    """画一个贴袋：袋盖 2 行（下沿中央挖倒山字形缺口）+ 袋身。

    1 texel ≈ 1 单位，袋盖只能占 2 texel：上沿整条受光，下沿中间一格挖空成缺口，
    读起来就是"倒山字形笔架式袋盖"。
    """
    fill(px, x0, y_flap, x0 + w, y_flap + 1, FLAP)                    # 袋盖受光面
    fill(px, x0, y_flap + 2, x0 + w, y_flap + 2 + body_rows, POCKET)  # 袋身
    mid = x0 + w // 2
    for x in range(x0, x0 + w):
        # 袋盖下沿中间挖掉一格＝倒山字形（笔架式）袋盖；用袋身色而非缝线色，免得看着像破洞
        px[x, y_flap + 1] = POCKET if (w % 2 == 1 and x == mid) else FLAP


def paint_jacket(px):
    """躯干四面 + 五粒扣 + 四个贴袋。"""
    # —— 立翻领：整圈最上两行都是领子，故四个侧面一致 ——
    for (sx0, sy0, sx1, sy1) in ((20, 16, 28, 20), (28, 16, 36, 20),
                                 (16, 20, 20, 32), (28, 20, 32, 32),
                                 (20, 20, 28, 32), (32, 20, 40, 32)):
        row_from = sy0 if sy0 >= 20 else 20
        fill(px, sx0, row_from, sx1, row_from + 2, COLLAR)
    fill(px, 20, 16, 28, 20, COLLAR)     # 肩顶（衣身顶面）也算领区
    px[23, 20] = SEAM                    # 正中央的领口开缝
    px[24, 20] = SEAM

    # —— 后背整片无破缝：抹掉原版后背的竖向明暗，只保留很淡的褶皱 ——
    for y in range(22, 31):
        for x in range(32, 40):
            lum = min(px[x, y][0], 110)
            nr, ng, nb = shade(SUIT, lum)
            px[x, y] = (nr, ng, nb, 255)

    # —— 门襟：中央两列，右列压边；五粒扣从左列自上而下 ——
    fill(px, 23, 22, 25, 31, SUIT)
    fill(px, 24, 22, 25, 31, SEAM)
    for y in (21, 23, 25, 27, 29):
        px[23, y] = BUTTON

    # —— 四个贴袋：上小（3 行）下大（5 行），贴袋前留 1 格空隙 ——
    for x0 in (20, 25):
        patch_pocket(px, x0, 3, 22, 1)   # 上袋：袋盖 2 行 + 袋身 1 行
        patch_pocket(px, x0, 3, 26, 3)   # 下袋：袋盖 2 行 + 袋身 3 行

    # —— 下摆 ——
    fill(px, 20, 31, 28, 32, SEAM)


def paint_sleeve(px, front_x, side_faces, cuff_row, btn_x):
    """袖子：袖口缝线 + 袖口三粒扣（沿袖缝外侧一列，末粒压在袖口线上）。"""
    fill(px, front_x, cuff_row, front_x + 4, cuff_row + 1, SEAM)
    for (sx0, _sy0, sx1, _sy1) in side_faces:
        fill(px, sx0, cuff_row, sx1, cuff_row + 1, SEAM)
    for r in (cuff_row - 2, cuff_row - 1, cuff_row):
        px[btn_x, r] = BUTTON


def paint_hands(px, hand):
    """手掌：袖子内层最下两行 + 腕口底面（原版是裸皮，被制服盖住后必须补回）。"""
    fill(px, 40, 30, 56, 32, hand)     # 右臂内层下端（含四个侧面）
    fill(px, 48, 16, 52, 20, hand)     # 右臂腕口底面
    fill(px, 32, 62, 48, 64, hand)     # 左臂内层下端
    fill(px, 40, 48, 44, 52, hand)     # 左臂腕口底面


def paint_arms(px):
    # 右臂内层：外(40,20) 前(44,20) 内(48,20) 后(52,20)，袖口在第 29 行
    paint_sleeve(px, 44, [(40, 20, 44, 32), (48, 20, 52, 32), (52, 20, 56, 32)], 29, 44)
    # 左臂内层：外(32,52) 前(36,52) 内(40,52) 后(44,52)，袖口在第 61 行
    paint_sleeve(px, 36, [(32, 52, 36, 64), (40, 52, 44, 64), (44, 52, 48, 64)], 61, 39)


def paint_legs(px):
    """长裤：前中缝压线 + 皮鞋。"""
    for (fx, fy) in ((4, 20), (20, 52)):     # 右腿/左腿内层正面
        for y in range(fy, fy + 10):
            px[fx + 2, y] = TROUSER_L        # 裤前中缝
    fill(px, 0, 30, 16, 32, SHOE)            # 右腿（含四个侧面）最下两行＝鞋
    fill(px, 16, 62, 32, 64, SHOE)           # 左腿


# ---------------------------------------------------------------- 女款
def clear_female_garment_uv(img):
    """清掉女款底图里的连衣裙与胸饰像素。

    女书记在 Java 侧直接复用男款几何，于是原版女市民的 dress(86,0)-(128,32) 与
    breast(64,49)-(86,61) 区域会落到男款手臂/身体几何上 —— 不清掉就会在
    身上浮出一片裙料／胸前布料。
    """
    px = img.load()
    for (x0, y0, x1, y1) in FEMALE_REMOVE:
        erase(px, x0, y0, x1, y1)


# ---------------------------------------------------------------- 输出
def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ref_dir = os.path.join(here, "_ref")
    out_dir = os.path.join(
        os.path.dirname(here), "src", "main", "resources", "assets", "minecolonies",
        "textures", "entity", "citizen", "default",
    )
    if not os.path.isdir(out_dir):
        raise SystemExit("目录不存在: " + out_dir)

    for suf in SUFFIXES:
        for gender, tag in (("male", "male"), ("female", "female")):
            ref = os.path.join(
                ref_dir,
                "textures__entity__citizen__default__citizen%s1_b.png" % gender,
            )
            base = Image.open(ref).convert("RGBA")
            assert base.size == (128, 64), base.size
            hand = tint(sample_skin(base), suf)
            out = base.copy()
            tint_head(out, suf)          # 1. 肤色微调（发色/眼白保留）
            strip_outer_layers(out)      # 2. 擦掉原版外层零散衣饰
            fill_inner_layers(out)       # 3. 内层铺中山装底色（连原版裸皮一起盖住）
            px = out.load()
            paint_jacket(px)             # 4. 立翻领 / 五粒扣 / 四个贴袋 / 后背整片
            paint_arms(px)               # 5. 袖口三粒扣
            paint_hands(px, hand)        # 6. 补回手掌
            paint_legs(px)               # 7. 裤中缝 + 皮鞋
            if gender == "female":
                clear_female_garment_uv(out)   # 8. 女款底图是连衣裙/胸饰，擦空改裤装
            path = os.path.join(out_dir, "secretary%s1%s.png" % (tag, suf))
            out.save(path, "PNG")
            print("written:", os.path.relpath(path))
    print("done.")


if __name__ == "__main__":
    main()
