package gaffer.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FiltersDimmerTest
{
    // ---- Gels

    @Test
    void mired_verschiebung_hin_und_zurueck()
    {
        double shift = Filters.miredShift(6500, 3200);
        assertEquals(1e6 / 3200 - 1e6 / 6500, shift, 1e-9);
        assertEquals(3200, Filters.applyMired(6500, shift), 1e-9);
        assertEquals(6500, Filters.applyMired(3200, -shift), 1e-9);
    }

    @Test
    void nd_folien_nach_dichte()
    {
        assertEquals(0.99658, Filters.gel("nd_03").stops(), 1e-5);
        assertEquals(1.99316, Filters.gel("nd_06").stops(), 1e-5);
        assertEquals(2.98974, Filters.gel("nd_09").stops(), 1e-5);
        LightColor c = Filters.gel("nd_03").apply(5600, 0, 1.0);
        System.out.printf("MESS Gel ND 0.3: %.5f Blenden, Transmission %.5f (Soll 10^-0.3 = %.5f)%n",
            Filters.gel("nd_03").stops(), c.level(), Math.pow(10, -0.3));
        assertEquals(Math.pow(10, -0.3), c.level(), 1e-12);
        // ND ist neutral: Chromatizität unverändert
        Cie.Xy ohne = Cie.cctDuvToXy(5600, 0);
        assertEquals(ohne.x(), c.x(), 1e-12);
        assertEquals(ohne.y(), c.y(), 1e-12);
    }

    @Test
    void gel_ohne_datenblatt_wird_nicht_angeboten()
    {
        for (Filters.Gel g : Filters.LIBRARY)
        {
            if (g.key().startsWith("nd_")) assertTrue(g.verified(), g.key());
            if (!g.verified()) assertThrows(IllegalArgumentException.class, () -> ColorModes.GelMode.from(new LightColor(0.3, 0.3, 1), g));
        }
    }

    @Test
    void gel_modus_rechnet_grundfarbe_exakt_zurueck()
    {
        // Mechanik mit einer Test-Folie (keine Bibliothekswerte): +100 Mired, +0.003 Duv, 1 Blende
        Filters.Gel test = new Filters.Gel("test", 100, 0.003, 1.0, "Testwert");
        LightColor ziel = new ColorModes.Cct(3000, 10).toColor(0.4);
        ColorModes.GelMode gm = ColorModes.GelMode.from(ziel, test);
        LightColor wieder = gm.toColor(ColorModes.GelMode.baseLevel(ziel, test));
        assertEquals(ziel.x(), wieder.x(), 2e-5);
        assertEquals(ziel.y(), wieder.y(), 2e-5);
        assertEquals(ziel.level(), wieder.level(), 1e-12);
    }

    // ---- Scrims

    @Test
    void scrims_in_blenden()
    {
        System.out.printf("MESS Scrim Single = %.5f (Soll 2^-0.5 = %.5f), Double = %.5f (Soll 0.5)%n",
            Filters.Scrim.SINGLE.factor(), Math.pow(2, -0.5), Filters.Scrim.DOUBLE.factor());
        assertEquals(Math.pow(2, -0.5), Filters.Scrim.SINGLE.factor(), 1e-12);
        assertEquals(0.5, Filters.Scrim.DOUBLE.factor(), 1e-12);
        assertEquals(Math.pow(2, -1.5), Filters.scrimFactor(Filters.Scrim.SINGLE, Filters.Scrim.DOUBLE), 1e-12);
        assertEquals(1.0, Exposure.stops(1.0, Filters.Scrim.DOUBLE.factor()), 1e-12);
    }

    // ---- W4/W13 Dimmerkurven

    @Test
    void alle_vier_dimmerkurven()
    {
        for (Dimmer.Curve c : Dimmer.Curve.values())
        {
            assertEquals(0.0, c.apply(0), 1e-12, c.name());
            assertEquals(1.0, c.apply(1), 1e-12, c.name());
            double last = -1;
            for (int i = 0; i <= 1000; i++)
            {
                double v = c.apply(i / 1000.0);
                assertTrue(v >= last, c + " monoton");
                last = v;
            }
            System.out.printf("MESS W4 %s: 25%% -> %.4f, 50%% -> %.4f, 75%% -> %.4f%n", c, c.apply(.25), c.apply(.5), c.apply(.75));
        }
        assertEquals(0.5, Dimmer.Curve.LINEAR.apply(0.5), 1e-12);
        assertEquals(0.5, Dimmer.Curve.S_CURVE.apply(0.5), 1e-12);
        for (double x = 0; x <= 1; x += 0.01)
        {
            assertEquals(1 - Dimmer.Curve.S_CURVE.apply(x), Dimmer.Curve.S_CURVE.apply(1 - x), 1e-12, "S-Kurve punktsymmetrisch");
            assertEquals(x, Dimmer.Curve.LOGARITHMIC.apply(Dimmer.Curve.EXPONENTIAL.apply(x)), 1e-12, "log = Umkehr von exp");
        }
        assertTrue(Dimmer.Curve.EXPONENTIAL.apply(0.5) < 0.5);
        assertTrue(Dimmer.Curve.LOGARITHMIC.apply(0.5) > 0.5);
        assertEquals(0.0, Dimmer.Curve.LINEAR.apply(-0.2), 0.0, "geklemmt");
        assertEquals(1.0, Dimmer.Curve.LINEAR.apply(1.2), 0.0, "geklemmt");
    }
}
