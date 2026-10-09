package com.gj.llm.base.util;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 滑动验证码拼图生成器（纯 Java2D，零第三方依赖）。
 *
 * <p>生成程序绘制的背景图（渐变 + 随机圆弧 + 噪点）与拼图块（圆角矩形 + 上下随机凸起的洞洞板形状），
 * 背景在缺口处叠加暗洞，拼图块从背景对应位置裁剪内容。拼图块内容不含缺口位置信息——
 * 位置只能从背景暗洞目测，这是滑动验证码的机制本身。</p>
 *
 * <h3>坐标系约定</h3>
 * <ul>
 *   <li>拼图块画布为 {@value #CANVAS}x{@value #CANVAS}，四周留 {@value #PAD}px 内边距容纳凸起；
 *       主体方块在画布内位于 ({@value #PAD}, {@value #PAD})</li>
 *   <li>{@code targetX} = 对齐时拼图块<b>画布左缘</b>的 x 坐标（前端拖动滑块的目标值，直接存 Redis 校验）</li>
 *   <li>{@code canvasY} = 拼图块画布的固定 y 坐标（前端按此摆放，纵向不参与拖动）</li>
 * </ul>
 *
 * <p><b>前端必须按 1:1 渲染图片（禁止 CSS 缩放）</b>，否则 CSS 像素与图像像素错位导致永远校验不过。</p>
 *
 * @author gj-llm
 */
public final class SlideCaptchaGenerator {

    private SlideCaptchaGenerator() {
    }

    // ==================== 几何常量 ====================

    /** 背景图宽度（像素） */
    public static final int BG_WIDTH = 300;

    /** 背景图高度（像素） */
    public static final int BG_HEIGHT = 100;

    /** 拼图主体方块边长（像素） */
    private static final int PIECE = 44;

    /** 拼图画布内边距（容纳上下凸起） */
    private static final int PAD = 10;

    /** 拼图画布边长 = 主体 + 两侧内边距 */
    private static final int CANVAS = PIECE + 2 * PAD;

    /** 缺口主体允许的 x 范围下限（右侧留出拼图块初始拖动空间） */
    private static final int GAP_X_MIN = 70;

    /** 缺口主体允许的 x 范围上限 */
    private static final int GAP_X_MAX = BG_WIDTH - PIECE - 10;

    /** 缺口主体允许的 y 范围下限（上下留凸起空间） */
    private static final int GAP_Y_MIN = PAD + 4;

    /** 缺口主体允许的 y 范围上限 */
    private static final int GAP_Y_MAX = BG_HEIGHT - PIECE - PAD - 4;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成结果：两张 Base64 PNG + 前端布局与后端校验所需坐标。
     *
     * @param bgBase64     背景图（Base64 PNG）
     * @param puzzleBase64 拼图块（Base64 PNG，透明背景）
     * @param targetX      对齐时拼图画布左缘 x 坐标（校验目标值）
     * @param canvasY      拼图画布固定 y 坐标
     * @param width        背景宽（像素）
     * @param height       背景高（像素）
     */
    public record SlidePuzzle(String bgBase64, String puzzleBase64,
                              int targetX, int canvasY, int width, int height) {
    }

    /**
     * 生成一帧滑动验证码拼图。
     *
     * @return 背景/拼图 Base64 与校验坐标
     * @throws IOException PNG 编码失败（实践上不会发生，调用方兜底）
     */
    public static SlidePuzzle generate() throws IOException {
        int gapX = GAP_X_MIN + RANDOM.nextInt(GAP_X_MAX - GAP_X_MIN + 1);
        int gapY = GAP_Y_MIN + RANDOM.nextInt(GAP_Y_MAX - GAP_Y_MIN + 1);
        // 缺口主体左上角 (gapX, gapY) → 拼图画布左上角 = 主体左上角 - PAD
        int canvasX = gapX - PAD;
        int canvasY = gapY - PAD;

        BufferedImage bg = drawBackground(gapX, gapY);
        BufferedImage piece = drawPiece(bg, canvasX, canvasY);

        return new SlidePuzzle(
                toBase64Png(bg),
                toBase64Png(piece),
                canvasX,
                canvasY,
                BG_WIDTH,
                BG_HEIGHT);
    }

    // ==================== 背景绘制 ====================

    /**
     * 绘制背景：纵向渐变 + 随机半透明圆形/弧线 + 噪点，并在缺口处叠加暗洞与高光描边。
     */
    private static BufferedImage drawBackground(int gapX, int gapY) {
        BufferedImage bg = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 纵向渐变（随机色相）
        float hue = RANDOM.nextFloat();
        Color top = Color.getHSBColor(hue, 0.32f, 0.78f);
        Color bottom = Color.getHSBColor((hue + 0.08f) % 1f, 0.38f, 0.50f);
        g.setPaint(new GradientPaint(0, 0, top, 0, BG_HEIGHT, bottom));
        g.fillRect(0, 0, BG_WIDTH, BG_HEIGHT);

        // 随机半透明装饰圆（亮/暗交替）
        for (int i = 0; i < 10; i++) {
            int r = 8 + RANDOM.nextInt(22);
            int x = RANDOM.nextInt(BG_WIDTH);
            int y = RANDOM.nextInt(BG_HEIGHT);
            g.setColor(RANDOM.nextBoolean()
                    ? new Color(255, 255, 255, 20 + RANDOM.nextInt(25))
                    : new Color(0, 0, 0, 15 + RANDOM.nextInt(20)));
            g.fillOval(x, y, r, r);
        }
        // 随机弧线
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(255, 255, 255, 30 + RANDOM.nextInt(30)));
            int x = RANDOM.nextInt(BG_WIDTH);
            int y = RANDOM.nextInt(BG_HEIGHT);
            int d = 20 + RANDOM.nextInt(50);
            g.drawOval(x, y, d, d);
        }
        // 噪点
        for (int i = 0; i < 80; i++) {
            g.setColor(new Color(255, 255, 255, 40 + RANDOM.nextInt(60)));
            g.fillRect(RANDOM.nextInt(BG_WIDTH), RANDOM.nextInt(BG_HEIGHT), 1, 1);
        }

        // 缺口暗洞：同拼图形状，平移到背景缺口位置（Area.transform 为就地变换，puzzleShape() 每次返回新实例）
        Area hole = puzzleShape();
        hole.transform(java.awt.geom.AffineTransform.getTranslateInstance(gapX - PAD, gapY - PAD));
        g.setColor(new Color(0, 0, 0, 90)); // rgba(0,0,0,0.35)
        g.fill(hole);
        g.setColor(new Color(255, 255, 255, 150)); // 内描边高光
        g.setStroke(new BasicStroke(1.5f));
        g.draw(hole);

        g.dispose();
        return bg;
    }

    // ==================== 拼图块绘制 ====================

    /**
     * 从背景裁剪拼图块内容并加白色描边，输出透明背景画布。
     */
    private static BufferedImage drawPiece(BufferedImage bg, int canvasX, int canvasY) {
        BufferedImage piece = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Shape shape = puzzleShape();

        // 裁剪到拼图形状后，把背景按画布偏移绘制进来（内容取自缺口处）
        g.setClip(shape);
        g.drawImage(bg, -canvasX, -canvasY, null);
        g.setClip(null);

        // 白色描边（轻微阴影提升辨识度）
        g.setColor(new Color(255, 255, 255, 230));
        g.setStroke(new BasicStroke(2f));
        g.draw(shape);

        g.dispose();
        return piece;
    }

    /**
     * 拼图形状（画布坐标系，主体位于 ({@value #PAD}, {@value #PAD})）：
     * 圆角矩形 + 上下各一个随机半径的外凸圆。
     */
    private static Area puzzleShape() {
        Area area = new Area(new RoundRectangle2D.Float(PAD, PAD, PIECE, PIECE, 6, 6));
        int centerX = PAD + PIECE / 2;
        int topR = 6 + RANDOM.nextInt(4);    // 凸起半径 6-9，均 < PAD
        int bottomR = 6 + RANDOM.nextInt(4);
        area.add(new Area(new Ellipse2D.Float(centerX - topR, PAD - topR, 2 * topR, 2 * topR)));
        area.add(new Area(new Ellipse2D.Float(centerX - bottomR, PAD + PIECE - bottomR, 2 * bottomR, 2 * bottomR)));
        return area;
    }

    // ==================== 编码 ====================

    private static String toBase64Png(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        }
    }
}
