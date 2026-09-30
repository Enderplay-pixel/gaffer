package gaffer.physics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Lichtplan in der Draufsicht (W11): Lampen mit Name, Typ, Kelvin und Dimmer, dazu Figuren,
 * Kamera-Position und Blickrichtung. Ausgabe als SVG-Text und als PNG-Bild.
 *
 * <p>Koordinaten in m (Welt x/z), Richtungen als Gier-Winkel in Grad wie in Minecraft
 * (0° = +z, 90° = -x).</p>
 */
public final class LightPlot
{
    public record Fixture(String name, String type, double x, double z, double yawDeg, double kelvin, double dimmerPercent)
    {}

    public record Subject(String name, double x, double z)
    {}

    public record Camera(double x, double z, double yawDeg)
    {}

    private final List<Fixture> fixtures;
    private final List<Subject> subjects;
    private final Camera camera;
    private final double minX, minZ, scale;
    private final int width, height;
    private static final int MARGIN = 60;

    public LightPlot(List<Fixture> fixtures, List<Subject> subjects, Camera camera, double pixelsPerMeter)
    {
        this.fixtures = fixtures;
        this.subjects = subjects;
        this.camera = camera;
        double x0 = camera.x(), x1 = camera.x(), z0 = camera.z(), z1 = camera.z();
        for (Fixture f : fixtures) { x0 = Math.min(x0, f.x()); x1 = Math.max(x1, f.x()); z0 = Math.min(z0, f.z()); z1 = Math.max(z1, f.z()); }
        for (Subject s : subjects) { x0 = Math.min(x0, s.x()); x1 = Math.max(x1, s.x()); z0 = Math.min(z0, s.z()); z1 = Math.max(z1, s.z()); }
        this.minX = x0 - 1;
        this.minZ = z0 - 1;
        this.scale = pixelsPerMeter;
        this.width = (int) Math.ceil((x1 - x0 + 2) * scale) + 2 * MARGIN;
        this.height = (int) Math.ceil((z1 - z0 + 2) * scale) + 2 * MARGIN;
    }

    public int width() { return width; }
    public int height() { return height; }

    double px(double x) { return MARGIN + (x - minX) * scale; }
    double py(double z) { return MARGIN + (z - minZ) * scale; }

    /** Blickrichtung in der Draufsicht (Pixel-Richtung) aus dem Minecraft-Gierwinkel. */
    static double[] dir(double yawDeg)
    {
        double r = Math.toRadians(yawDeg);
        return new double[]{-Math.sin(r), Math.cos(r)};
    }

    public static String label(Fixture f)
    {
        return String.format(Locale.ROOT, "%s | %s | %.0f K | %.0f %%", f.name(), f.type(), f.kelvin(), f.dimmerPercent());
    }

    public String toSvg()
    {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n", width, height, width, height));
        s.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        // 1-m-Raster
        s.append("<g stroke=\"#e0e0e0\" stroke-width=\"1\">\n");
        for (double gx = Math.ceil(minX); px(gx) <= width - MARGIN; gx++)
            s.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%d\" x2=\"%.1f\" y2=\"%d\"/>\n", px(gx), MARGIN, px(gx), height - MARGIN));
        for (double gz = Math.ceil(minZ); py(gz) <= height - MARGIN; gz++)
            s.append(String.format(Locale.ROOT, "<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\"/>\n", MARGIN, py(gz), width - MARGIN, py(gz)));
        s.append("</g>\n");
        for (Subject sub : subjects)
        {
            s.append(String.format(Locale.ROOT, "<circle class=\"subject\" cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"#bbbbbb\" stroke=\"#333\"/>\n", px(sub.x()), py(sub.z()), 0.3 * scale));
            s.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" font-size=\"12\" text-anchor=\"middle\">%s</text>\n", px(sub.x()), py(sub.z()) + 0.3 * scale + 14, esc(sub.name())));
        }
        for (Fixture f : fixtures)
        {
            double[] d = dir(f.yawDeg());
            double cx = px(f.x()), cy = py(f.z()), r = 0.25 * scale;
            s.append(String.format(Locale.ROOT, "<g class=\"fixture\"><circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"#ffd24a\" stroke=\"#333\"/>", cx, cy, r));
            s.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#333\" stroke-width=\"2\"/>", cx, cy, cx + d[0] * r * 2.5, cy + d[1] * r * 2.5));
            s.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" font-size=\"12\">%s</text></g>\n", cx + r + 4, cy - r, esc(label(f))));
        }
        double[] cd = dir(camera.yawDeg());
        double cx = px(camera.x()), cy = py(camera.z());
        s.append(String.format(Locale.ROOT, "<g class=\"camera\"><rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"#333\"/>", cx - 8, cy - 8, 16.0, 16.0));
        s.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#d00\" stroke-width=\"3\"/>", cx, cy, cx + cd[0] * 40, cy + cd[1] * 40));
        s.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" font-size=\"12\">Kamera</text></g>\n", cx + 12, cy + 20));
        s.append("</svg>\n");
        return s.toString();
    }

    public BufferedImage toImage()
    {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(0xE0E0E0));
        for (double gx = Math.ceil(minX); px(gx) <= width - MARGIN; gx++) g.drawLine((int) px(gx), MARGIN, (int) px(gx), height - MARGIN);
        for (double gz = Math.ceil(minZ); py(gz) <= height - MARGIN; gz++) g.drawLine(MARGIN, (int) py(gz), width - MARGIN, (int) py(gz));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Subject sub : subjects)
        {
            int r = (int) (0.3 * scale);
            g.setColor(new Color(0xBBBBBB));
            g.fillOval((int) px(sub.x()) - r, (int) py(sub.z()) - r, 2 * r, 2 * r);
            g.setColor(new Color(0x333333));
            g.drawString(sub.name(), (int) px(sub.x()) - 10, (int) py(sub.z()) + r + 14);
        }
        for (Fixture f : fixtures)
        {
            double[] d = dir(f.yawDeg());
            int cx = (int) px(f.x()), cy = (int) py(f.z()), r = (int) (0.25 * scale);
            g.setColor(new Color(0xFFD24A));
            g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            g.setColor(new Color(0x333333));
            g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
            g.setStroke(new BasicStroke(2));
            g.drawLine(cx, cy, (int) (cx + d[0] * r * 2.5), (int) (cy + d[1] * r * 2.5));
            g.drawString(label(f), cx + r + 4, cy - r);
        }
        double[] cd = dir(camera.yawDeg());
        int cx = (int) px(camera.x()), cy = (int) py(camera.z());
        g.setColor(new Color(0x333333));
        g.fillRect(cx - 8, cy - 8, 16, 16);
        g.setColor(new Color(0xDD0000));
        g.setStroke(new BasicStroke(3));
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(cx, cy);
        arrow.lineTo(cx + cd[0] * 40, cy + cd[1] * 40);
        g.draw(arrow);
        g.setColor(new Color(0x333333));
        g.drawString("Kamera", cx + 12, cy + 20);
        g.dispose();
        return img;
    }

    private static String esc(String s)
    {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
