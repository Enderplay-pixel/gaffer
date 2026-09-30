package gaffer.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ColorModesTest
{
    private static final double Q = 1.0 / 255.0;

    // ---- W13 RGB hin und zurück

    @Test
    void rgb_hin_und_zurueck_exakt_auf_1_255()
    {
        int maxErr = 0, n = 0;
        for (int r = 0; r < 256; r += 5)
            for (int g = 0; g < 256; g += 5)
                for (int b = 0; b < 256; b += 5)
                {
                    ColorModes.Rgb in = new ColorModes.Rgb(r, g, b);
                    ColorModes.Rgb out = ColorModes.Rgb.from(in.toColor());
                    maxErr = Math.max(maxErr, Math.max(Math.abs(r - out.r()), Math.max(Math.abs(g - out.g()), Math.abs(b - out.b()))));
                    n++;
                }
        System.out.printf("MESS W13 RGB->Farbe->RGB: %d Farben, größte Abweichung %d/255%n", n, maxErr);
        assertTrue(maxErr <= 1);
    }

    @Test
    void hex_hin_und_zurueck()
    {
        ColorModes.Rgb c = ColorModes.Rgb.fromHex("#FF8040");
        assertEquals("#FF8040", c.hex());
        assertEquals(255, c.r());
        assertEquals(128, c.g());
        assertEquals(64, c.b());
    }

    // ---- W13 HSI hin und zurück

    @Test
    void hsi_hin_und_zurueck_auf_1_255()
    {
        double maxH = 0, maxS = 0, maxI = 0;
        for (double white : new double[]{2500, 3200, 5600, 10000})
            for (int h = 0; h < 360; h += 7)
                for (double s = 0.05; s <= 1.0001; s += 0.05)
                {
                    double i = 0.37;
                    ColorModes.Hsi in = new ColorModes.Hsi(h, s, i, white);
                    ColorModes.Hsi out = ColorModes.Hsi.from(in.toColor(), white);
                    double dh = Math.abs(in.hueDeg() - out.hueDeg());
                    dh = Math.min(dh, 360 - dh) / 360.0;
                    maxH = Math.max(maxH, dh);
                    maxS = Math.max(maxS, Math.abs(in.saturation() - out.saturation()));
                    maxI = Math.max(maxI, Math.abs(in.intensity() - out.intensity()));
                }
        System.out.printf("MESS W13 HSI hin/zurück: max dH = %.2e, dS = %.2e, dI = %.2e (Grenze %.2e)%n", maxH, maxS, maxI, Q);
        assertTrue(maxH <= Q && maxS <= Q && maxI <= Q);
    }

    @Test
    void hsi_saettigung_null_ist_weisspunkt()
    {
        LightColor c = new ColorModes.Hsi(123, 0, 1, 3200).toColor();
        Cie.Xy w = Cie.planckXy(3200);
        assertEquals(w.x(), c.x(), 1e-12);
        assertEquals(w.y(), c.y(), 1e-12);
    }

    // ---- W13 xy hin und zurück, Gamut

    @Test
    void xy_hin_und_zurueck_und_markierung()
    {
        double max = 0;
        for (double x = 0.2; x <= 0.6; x += 0.01)
            for (double y = 0.1; y <= 0.55; y += 0.01)
            {
                ColorModes.Xy in = new ColorModes.Xy(x, y);
                if (in.outOfGamut()) continue;
                ColorModes.Xy out = ColorModes.Xy.from(in.toColor(0.5));
                max = Math.max(max, Math.max(Math.abs(x - out.x()), Math.abs(y - out.y())));
            }
        System.out.printf("MESS W13 xy hin/zurück: größte Abweichung %.2e%n", max);
        assertTrue(max <= Q);

        ColorModes.Xy aussen = new ColorModes.Xy(0.1, 0.8);   // Spektralgrün, außerhalb Rec.709
        assertTrue(aussen.outOfGamut());
        LightColor geclippt = aussen.toColor(1);
        assertTrue(Rec709.inGamut(geclippt.xy(), 1e-9), "abgeschnitten auf den Rand");
        assertFalse(new ColorModes.Xy(0.3127, 0.329).outOfGamut());
    }

    // ---- W13 Moduswechsel ohne Farbsprung

    @Test
    void moduswechsel_ohne_farbsprung()
    {
        Random rnd = new Random(42);
        List<LightColor> starts = new ArrayList<>();
        int abgelehnt = 0;
        while (starts.size() < 200)
        {
            // Nur Farben, die in allen Modi darstellbar sind (CCT-Modus 2500..10000 K, +-100 %)
            LightColor c = new ColorModes.Rgb(20 + rnd.nextInt(236), 20 + rnd.nextInt(236), 20 + rnd.nextInt(236)).toColor();
            if (ColorModes.Cct.representable(c)) starts.add(c); else abgelehnt++;
        }
        System.out.printf("MESS W13 Moduswechsel: %d RGB-Zufallsfarben im CCT-Modus nicht darstellbar (Warnung statt Umrechnung)%n", abgelehnt);
        int cctStarts = 0;
        while (cctStarts < 50)
        {
            // CCT-Startfarben nur innerhalb von Rec.709; außerhalb schneidet der xy-Modus laut Vorgabe ab
            LightColor c = new ColorModes.Cct(2500 + rnd.nextDouble() * 7500, rnd.nextDouble() * 200 - 100).toColor(0.2 + rnd.nextDouble() * 0.3);
            if (Rec709.inGamut(c.xy(), 0)) { starts.add(c); cctStarts++; }
        }
        double max = 0;
        for (LightColor c : starts)
        {
            // CCT -> HSI -> RGB -> xy -> Gel(ND) -> CCT, nach jedem Schritt mit dem Start vergleichen
            LightColor viaCct = new ColorModes.Cct(ColorModes.Cct.from(c).kelvin(), ColorModes.Cct.from(c).greenMagentaPercent()).toColor(c.level());
            max = Math.max(max, diff(c, viaCct));
            ColorModes.Hsi hsi = ColorModes.Hsi.from(viaCct, 5600);
            LightColor viaHsi = hsi.toColor();
            max = Math.max(max, diff(c, viaHsi));
            ColorModes.Xy xy = ColorModes.Xy.from(viaHsi);
            LightColor viaXy = xy.toColor(viaHsi.level());
            max = Math.max(max, diff(c, viaXy));
            Filters.Gel nd = Filters.gel("nd_06");
            ColorModes.GelMode gm = ColorModes.GelMode.from(viaXy, nd);
            LightColor viaGel = gm.toColor(ColorModes.GelMode.baseLevel(viaXy, nd));
            max = Math.max(max, diff(c, viaGel));
        }
        // RGB ist quantisiert (8 Bit); der Wechsel in RGB und zurück darf höchstens eine Stufe kosten
        double maxRgb = 0;
        for (LightColor c : starts)
        {
            if (ColorModes.Rgb.clipped(c)) continue;
            LightColor viaRgb = ColorModes.Rgb.from(c).toColor();
            maxRgb = Math.max(maxRgb, diffEncoded(c, viaRgb));
        }
        System.out.printf("MESS W13 Moduswechsel: max Farbabweichung %.2e (linear), RGB-Umweg %.2e (kodiert), Grenze %.2e%n", max, maxRgb, Q);
        assertTrue(max <= Q, "kein Sprung über kontinuierliche Modi");
        assertTrue(maxRgb <= 0.5 * Q + 1e-12, "RGB-Umweg höchstens Rundung auf 8 Bit");
    }

    private static double diff(LightColor a, LightColor b)
    {
        double[] x = a.toLinearRec709(), y = b.toLinearRec709();
        return Math.max(Math.abs(x[0] - y[0]), Math.max(Math.abs(x[1] - y[1]), Math.abs(x[2] - y[2])));
    }

    private static double diffEncoded(LightColor a, LightColor b)
    {
        double[] x = a.toLinearRec709(), y = b.toLinearRec709();
        double m = 0;
        for (int i = 0; i < 3; i++) m = Math.max(m, Math.abs(Rec709.encode(x[i]) - Rec709.encode(y[i])));
        return m;
    }

    @Test
    void gesaettigtes_blau_ist_im_cct_modus_nicht_darstellbar()
    {
        assertFalse(ColorModes.Cct.representable(new ColorModes.Rgb(40, 20, 255).toColor()));
        assertTrue(ColorModes.Cct.representable(new ColorModes.Cct(3200, 20).toColor(1)));
    }

    // ---- W13 Constant Output

    @Test
    void constant_output_gleicher_pegel_ueber_alle_kelvin()
    {
        double min = Double.MAX_VALUE, max = 0;
        for (int k = (int) ColorModes.CCT_MIN; k <= ColorModes.CCT_MAX; k += 100)
        {
            double y = new ColorModes.Cct(k, 0).toColor(1.0).toXyz()[1];
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        System.out.printf("MESS W13 Constant Output 2500..10000 K: Y min %.12f max %.12f%n", min, max);
        assertEquals(1.0, min, 1e-12);
        assertEquals(1.0, max, 1e-12);
    }

    // ---- Source Match

    @Test
    void source_match_nur_mit_quelle()
    {
        for (ColorModes.Source s : ColorModes.SOURCES)
        {
            if (s.verified()) assertTrue(Rec709.inGamut(s.toColor(1).xy(), 0.01) || s.key().equals("sodium_vapour"), s.key());
        }
        assertTrue(ColorModes.SOURCES.stream().filter(ColorModes.Source::verified).count() >= 4);
    }
}
