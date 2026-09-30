package gaffer.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhotometryExposureTest
{
    // ---- P1 Abstandsgesetz

    @Test
    void abstandsgesetz_2m_zu_4m_ist_4_zu_1()
    {
        double i = 1000;   // cd
        double ratio = Photometry.illuminance(i, 2, 1) / Photometry.illuminance(i, 4, 1);
        System.out.printf("MESS P1 E(2m)/E(4m) = %.6f (Soll 4.0)%n", ratio);
        assertEquals(4.0, ratio, 1e-12);
    }

    @Test
    void abstandsgesetz_mit_cullingrand_bleibt_innerhalb_1_prozent()
    {
        // Culling-Radius wie im Shader: E fällt auf 1/256 der Referenz (hier 240 lx).
        double i = 1000;
        double r = Photometry.cullRadius(i, 240.0 / 256.0);
        double ratio = Photometry.illuminanceCulled(i, 2, 1, r) / Photometry.illuminanceCulled(i, 4, 1, r);
        System.out.printf("MESS P1 mit Rand: r = %.2f m, E(2m)/E(4m) = %.5f%n", r, ratio);
        assertEquals(4.0, ratio, 0.04);
    }

    @Test
    void kosinus_einfall()
    {
        assertEquals(25.0, Photometry.illuminance(200, 2, Math.cos(Math.toRadians(60))), 1e-9);   // 200 cd / 4 m² * 0.5
        assertEquals(0.0, Photometry.illuminance(200, 2, -0.3), 0.0);
    }

    @Test
    void lumen_zu_candela_kugel_und_datenblatt()
    {
        // Punktquelle 4π sr: I = Φ / 4π
        assertEquals(1000 / (4 * Math.PI), Photometry.candelaFromLumen(1000, 360), 1e-9);
        // Datenblattangabe "16730 lx auf 3 m" -> 150570 cd
        assertEquals(150570, Photometry.candelaFromLuxAt(16730, 3), 1e-9);
    }

    // ---- P2 Belichtung nach ISO 2720

    @Test
    void iso2720_ev0_bei_iso100_ist_2_5_lux()
    {
        // N²/t = E S / C  ->  N = 1, t = 1 s, S = 100, C = 250  ->  E = 2.5 lx
        double n = Exposure.fNumberFor(2.5, 100, 1.0, 0, Exposure.C_FLAT);
        System.out.printf("MESS P2 f-Zahl bei 2.5 lx, ISO 100, 1 s = %.6f (Soll 1.0)%n", n);
        assertEquals(1.0, n, 1e-12);
        assertEquals(0.0, Exposure.ev100(2.5, Exposure.C_FLAT), 1e-12);
    }

    @Test
    void referenz_lux_iso800_180grad_24fps_f4()
    {
        var cam = new Exposure.Camera(800, 180, 24, 4.0, 0.0);
        assertEquals(1.0 / 48.0, cam.exposureTime(), 1e-15);
        double eRef = cam.referenceLux(Exposure.C_FLAT);
        System.out.printf("MESS P2 E_ref(ISO800, 180°, 24fps, f/4) = %.3f lx (Soll 240)%n", eRef);
        assertEquals(240.0, eRef, 1e-9);
        // Umkehrung: bei E_ref zeigt der Messer genau f/4
        assertEquals(4.0, Exposure.fNumberFor(eRef, 800, cam.exposureTime(), 0, Exposure.C_FLAT), 1e-12);
    }

    @Test
    void doppelte_beleuchtung_ist_eine_blende()
    {
        double n1 = Exposure.fNumberFor(240, 800, 1.0 / 48, 0, Exposure.C_FLAT);
        double n2 = Exposure.fNumberFor(480, 800, 1.0 / 48, 0, Exposure.C_FLAT);
        assertEquals(Math.sqrt(2), n2 / n1, 1e-12);
        assertEquals(1.0, Exposure.stops(480, 240), 1e-12);
    }

    @Test
    void nd_filter_verlangt_mehr_licht()
    {
        var ohne = new Exposure.Camera(800, 180, 24, 4.0, 0.0);
        var nd09 = new Exposure.Camera(800, 180, 24, 4.0, 0.9);
        double stops = Exposure.stops(nd09.referenceLux(Exposure.C_FLAT), ohne.referenceLux(Exposure.C_FLAT));
        System.out.printf("MESS P2 ND 0.9 = %.4f Blenden%n", stops);
        assertEquals(0.9 / Math.log10(2), stops, 1e-9);
    }

    // ---- W5 Kontrast

    @Test
    void key_fill_in_blenden_log2()
    {
        double stops = Exposure.keyFillStops(800, 200);
        System.out.printf("MESS W5 Key 800 lx / Fill 200 lx = %.4f Blenden (Soll 2.0), (K+F):F = %.2f%n",
            stops, Exposure.keyFillRatio(800, 200));
        assertEquals(2.0, stops, 1e-12);
        assertEquals(5.0, Exposure.keyFillRatio(800, 200), 1e-12);
        assertEquals(-1.0, Exposure.keyFillStops(100, 200), 1e-12);
    }
}
