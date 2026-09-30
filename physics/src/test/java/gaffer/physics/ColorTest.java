package gaffer.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorTest
{
    // ---- W1 Planck und CIE-Tageslicht gegen Tabellenwerte der CIE

    @Test
    void planck_2856K_ist_normlichtart_A()
    {
        // Normlichtart A: Planck-Strahler 2848 K mit c2 = 1.435e-2 m·K = 2856 K mit c2 = 1.4388e-2;
        // CIE 015: x = 0.44758, y = 0.40745. Beide Formen müssen dasselbe ergeben.
        Cie.Xy a = Cie.planckXy(2856, Cie.C2);
        Cie.Xy a2 = Cie.planckXy(2848, Cie.C2_ILLUMINANT_A);
        // 2848 * 1.4388 / 1.435 = 2855.5 K, nicht exakt 2856 K: Toleranz 5e-5
        assertEquals(a.x(), a2.x(), 5e-5);
        assertEquals(a.y(), a2.y(), 5e-5);
        System.out.printf("MESS W1 Planck 2856K: x = %.5f y = %.5f (Soll 0.44758 0.40745)%n", a.x(), a.y());
        assertEquals(0.44758, a.x(), 5e-5);
        assertEquals(0.40745, a.y(), 5e-5);
    }

    @Test
    void cie_tageslicht_d65()
    {
        // D65 = CIE-Tageslicht bei 6500 K * 1.4388/1.4380 (Änderung von c2); CIE 015: x = 0.31271, y = 0.32902.
        // Die Tabellenwerte sind aus der gerundeten Spektraltabelle berechnet; die Tageslichtformel
        // selbst liefert y = 0.32913 (gegengeprüft mit colour-science 0.4.6). Toleranz daher 2e-4.
        Cie.Xy d = Cie.daylightXy(6500 * 1.4388 / 1.4380);
        System.out.printf("MESS W1 D65: x = %.5f y = %.5f (Soll 0.31271 0.32902)%n", d.x(), d.y());
        assertEquals(0.31271, d.x(), 1e-4);
        assertEquals(0.32902, d.y(), 2e-4);
    }

    @Test
    void planck_bereich_1700_bis_20000_monoton()
    {
        double lastX = 1;
        for (int k = 1700; k <= 20000; k += 100)
        {
            Cie.Xy p = Cie.planckXy(k);
            assertTrue(p.x() < lastX, "x sinkt mit steigender Temperatur bei " + k);
            lastX = p.x();
        }
    }

    // ---- W1 Grün/Magenta

    @Test
    void cct_duv_hin_und_zurueck()
    {
        for (double k : new double[]{1700, 2500, 3200, 4300, 5600, 10000, 20000})
        {
            for (double duv : new double[]{-0.02, -0.005, 0, 0.005, 0.02})
            {
                Cie.CctDuv back = Cie.xyToCctDuv(Cie.cctDuvToXy(k, duv));
                assertEquals(k, back.kelvin(), k * 1e-3, "K bei " + k + "/" + duv);
                assertEquals(duv, back.duv(), 2e-4, "Duv bei " + k + "/" + duv);
            }
        }
    }

    @Test
    void plus_gruen_liegt_ueber_der_planck_kurve()
    {
        Cie.Xy neutral = Cie.cctDuvToXy(5600, 0);
        Cie.Xy gruen = Cie.cctDuvToXy(5600, Tint.duv(100));
        Cie.Xy magenta = Cie.cctDuvToXy(5600, Tint.duv(-100));
        assertTrue(gruen.y() > neutral.y());
        assertTrue(magenta.y() < neutral.y());
        double[] g = LightColor.of(gruen, 1).toCameraRgb(new WhiteBalance(5600, 0));
        System.out.printf("MESS W1 5600K +100%% Grün bei WB 5600K: R=%.4f G=%.4f B=%.4f%n", g[0], g[1], g[2]);
        assertTrue(g[1] > g[0] && g[1] > g[2], "Grün überwiegt");
    }

    // ---- W1 Kelvin mit Weißabgleich

    @Test
    void lampe_gleich_weissabgleich_ist_neutral()
    {
        for (double k : new double[]{2500, 3200, 4300, 5600, 10000})
        {
            for (double tint : new double[]{-50, 0, 50})
            {
                LightColor c = new ColorModes.Cct(k, tint).toColor(1);
                double[] rgb = c.toCameraRgb(new WhiteBalance(k, tint));
                assertEquals(1.0, rgb[0], 1e-9, k + "K");
                assertEquals(1.0, rgb[1], 1e-9, k + "K");
                assertEquals(1.0, rgb[2], 1e-9, k + "K");
            }
        }
    }

    @Test
    void tageslicht_bei_kunstlicht_weissabgleich_ist_blau()
    {
        double[] rgb = new ColorModes.Cct(5600, 0).toColor(1).toCameraRgb(new WhiteBalance(3200, 0));
        double[] warm = new ColorModes.Cct(3200, 0).toColor(1).toCameraRgb(WhiteBalance.DEFAULT);
        System.out.printf("MESS W1 5600K @WB3200: %.3f %.3f %.3f | 3200K @WB5600: %.3f %.3f %.3f%n",
            rgb[0], rgb[1], rgb[2], warm[0], warm[1], warm[2]);
        assertTrue(rgb[2] > rgb[0], "5600 K unter 3200-K-Abgleich ist blau");
        assertTrue(warm[0] > warm[2], "3200 K unter 5600-K-Abgleich ist orange");
    }

    @Test
    void weissabgleich_standard_5600()
    {
        assertEquals(5600, WhiteBalance.DEFAULT.kelvin());
        assertEquals(0, WhiteBalance.DEFAULT.tintPercent());
    }

    @Test
    void rec709_weiss_ist_1_1_1()
    {
        double[] rgb = Rec709.fromXyz(Rec709.WHITE.toXyz(1));
        assertArrayEquals(new double[]{1, 1, 1}, rgb, 1e-12);
    }

    @Test
    void srgb_kodierung_hin_und_zurueck()
    {
        for (int i = 0; i <= 255; i++)
        {
            double e = i / 255.0;
            assertEquals(e, Rec709.encode(Rec709.decode(e)), 1e-12);
        }
    }
}
