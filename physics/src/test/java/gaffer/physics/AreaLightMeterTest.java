package gaffer.physics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AreaLightMeterTest
{
    private static final double[] UP = {0, 1, 0};

    /** Leuchtfläche in Höhe h über dem Ursprung, strahlt nach unten. */
    private static List<double[]> squareAbove(double side, double h)
    {
        // halfU x halfV muss nach unten zeigen: x cross z = -y
        return AreaLight.rectangle(new double[]{0, h, 0}, new double[]{side / 2, 0, 0}, new double[]{0, 0, side / 2});
    }

    @Test
    void kleine_flaeche_geht_ins_abstandsgesetz_ueber()
    {
        double L = 10000, side = 0.1, h = 5;
        double e = AreaLight.illuminance(squareAbove(side, h), L, new double[]{0, 0, 0}, UP);
        double punkt = Photometry.illuminance(L * side * side, h, 1);
        System.out.printf("MESS P3 kleine Fläche: E = %.6f lx, Punktnäherung %.6f lx, Abweichung %.4f %%%n", e, punkt, 100 * (e / punkt - 1));
        assertEquals(punkt, e, punkt * 1e-3);
    }

    @Test
    void scheibe_auf_der_achse_exakt()
    {
        // Exakt: E = π L sin²α, sin α = R / sqrt(R² + d²)
        double L = 5000, r = 0.6, d = 1.2;
        // Achsen x, z: x × z = -y, die Scheibe strahlt nach unten zum Messpunkt
        var disc = AreaLight.disc(new double[]{0, d, 0}, new double[]{1, 0, 0}, new double[]{0, 0, 1}, r, 256);
        double e = AreaLight.illuminance(disc, L, new double[]{0, 0, 0}, UP);
        double exakt = Math.PI * L * r * r / (r * r + d * d);
        // 256-Eck ist kleiner als der Kreis: Flächenverhältnis n/(2π) sin(2π/n)
        double flaeche = 256 / (2 * Math.PI) * Math.sin(2 * Math.PI / 256);
        System.out.printf("MESS P3 Scheibe 256-Eck: E = %.4f lx, exakt %.4f lx, Abweichung %.4f %% (Polygonfläche %.4f %% kleiner)%n",
            e, exakt, 100 * (e / exakt - 1), 100 * (1 - flaeche));
        assertEquals(exakt, e, exakt * 1e-3);
    }

    @Test
    void grosse_flaeche_nah_ist_schwaecher_als_punktnaeherung()
    {
        // Softbox 1 m x 1 m in 0.5 m: Punktnäherung überschätzt stark (Kosinus der Randbereiche)
        double L = 2000;
        double e = AreaLight.illuminance(squareAbove(1, 0.5), L, new double[]{0, 0, 0}, UP);
        double punkt = Photometry.illuminance(L * 1, 0.5, 1);
        System.out.printf("MESS P3 Softbox 1x1 m in 0.5 m: Fläche %.1f lx, Punktnäherung %.1f lx%n", e, punkt);
        assertTrue(e < punkt && e > 0);
        assertTrue(e < Math.PI * L, "nie mehr als die unendliche Ebene");
    }

    @Test
    void horizont_und_rueckseite()
    {
        double L = 1000;
        // Empfänger zeigt nach unten, Lampe oben: nichts
        assertEquals(0.0, AreaLight.illuminance(squareAbove(1, 2), L, new double[]{0, 0, 0}, new double[]{0, -1, 0}), 1e-12);
        // Punkt über der Lampe (Rückseite): nichts
        assertEquals(0.0, AreaLight.illuminance(squareAbove(1, 2), L, new double[]{0, 3, 0}, new double[]{0, -1, 0}), 1e-12);
        // Empfänger senkrecht: Hälfte der Lampe über dem Horizont, Wert zwischen 0 und dem frontalen Wert
        double seitlich = AreaLight.illuminance(squareAbove(1, 2), L, new double[]{0, 0, 0}, new double[]{1, 0, 0});
        assertTrue(seitlich > 0);
    }

    // ---- W5 Belichtungsmesser

    @Test
    void messer_scheibe_und_kalotte()
    {
        var key = new Meter.Contribution("key", 800, new double[]{1, 0, 1});   // 45° seitlich
        var fill = new Meter.Contribution("fill", 200, new double[]{-1, 0, 0}); // 90° andere Seite
        double[] zurKamera = {0, 0, 1};
        double disc = Meter.lux(List.of(key, fill), zurKamera, Meter.Receptor.DISC);
        double dome = Meter.lux(List.of(key, fill), zurKamera, Meter.Receptor.DOME);
        double c45 = Math.cos(Math.toRadians(45));
        System.out.printf("MESS W5 Scheibe %.2f lx, Kalotte %.2f lx%n", disc, dome);
        assertEquals(800 * c45, disc, 1e-9);
        assertEquals(800 * (1 + c45) / 2 + 200 * 0.5, dome, 1e-9);
    }

    @Test
    void key_allein_fill_allein_in_blenden()
    {
        var key = new Meter.Contribution("key", 1600, new double[]{0, 0, 1});
        var fill = new Meter.Contribution("fill", 400, new double[]{0, 0, 1});
        var alle = List.of(key, fill);
        double[] f = {0, 0, 1};
        double k = Meter.luxOf(alle, Set.of("key"), f, Meter.Receptor.DOME);
        double fl = Meter.luxOf(alle, Set.of("fill"), f, Meter.Receptor.DOME);
        System.out.printf("MESS W5 Key allein %.0f lx, Fill allein %.0f lx -> %.3f Blenden, (K+F):F %.2f%n",
            k, fl, Exposure.keyFillStops(k, fl), Exposure.keyFillRatio(k, fl));
        assertEquals(2.0, Exposure.keyFillStops(k, fl), 1e-12);
    }
}
