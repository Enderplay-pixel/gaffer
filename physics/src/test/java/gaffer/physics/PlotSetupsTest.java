package gaffer.physics;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlotSetupsTest
{
    @Test
    void lichtplan_svg_und_png() throws Exception
    {
        var fixtures = List.of(
            new LightPlot.Fixture("Key 1", "Fresnel", 2, 1, 135, 3200, 80),
            new LightPlot.Fixture("Fill 1", "Softbox", -2, 1, 225, 5600, 35));
        var plot = new LightPlot(fixtures, List.of(new LightPlot.Subject("Figur", 0, 0)), new LightPlot.Camera(0, 4, 180), 60);
        String svg = plot.toSvg();
        for (var f : fixtures)
        {
            assertTrue(svg.contains(LightPlot.label(f)), "SVG enthält " + LightPlot.label(f));
        }
        assertTrue(svg.contains("class=\"camera\""));
        assertEquals(2, svg.split("class=\"fixture\"").length - 1);

        Path out = Path.of("build/test-plot");
        Files.createDirectories(out);
        Files.writeString(out.resolve("lichtplan.svg"), svg);
        BufferedImage img = plot.toImage();
        File png = out.resolve("lichtplan.png").toFile();
        assertTrue(ImageIO.write(img, "png", png));
        BufferedImage back = ImageIO.read(png);
        assertEquals(plot.width(), back.getWidth());
        // Kamera-Pfeil ist rot: mindestens ein rein roter Pixel
        int rot = 0;
        for (int y = 0; y < back.getHeight(); y++)
            for (int x = 0; x < back.getWidth(); x++)
                if ((back.getRGB(x, y) & 0xFFFFFF) == 0xDD0000) rot++;
        System.out.printf("MESS W11 Lichtplan %dx%d px, %d Lampen im SVG, %d Pixel Kamerapfeil%n", back.getWidth(), back.getHeight(), 2, rot);
        assertTrue(rot > 20);
    }

    @Test
    void setups_zielen_auf_den_kopf_und_liegen_auf_abstand()
    {
        double[] head = {10, 65.6, -3}, cam = {10, 65.6, 2};
        for (Setups.Kind k : Setups.Kind.values())
        {
            for (Setups.Placement p : Setups.place(k, head, cam, 2.0))
            {
                double dx = head[0] - p.position()[0], dy = head[1] - p.position()[1], dz = head[2] - p.position()[2];
                assertEquals(2.0, Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-9, k + " Abstand");
                assertEquals(dx / 2.0, p.direction()[0], 1e-9);
                assertEquals(dy / 2.0, p.direction()[1], 1e-9);
                assertEquals(dz / 2.0, p.direction()[2], 1e-9);
            }
        }
        // Butterfly: Key genau über der Kameraachse; Gegenlicht: auf der von der Kamera abgewandten Seite
        var bf = Setups.place(Setups.Kind.BUTTERFLY, head, cam, 2.0).get(0);
        assertEquals(10.0, bf.position()[0], 1e-9);
        assertTrue(bf.position()[2] > head[2] && bf.position()[1] > head[1]);
        var back = Setups.place(Setups.Kind.BACKLIGHT, head, cam, 2.0).get(0);
        assertTrue(back.position()[2] < head[2]);
        var split = Setups.place(Setups.Kind.SPLIT, head, cam, 2.0).get(0);
        assertEquals(head[2], split.position()[2], 1e-9, "Split: 90° seitlich");
    }
}
