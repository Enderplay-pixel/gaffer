package gaffer.physics;

/**
 * Grün/Magenta-Achse. Umrechnung Prozent in Duv (Abstand zur Planck-Kurve in CIE 1960 uv).
 *
 * <p><b>Festlegung, kein Messwert:</b> +100 % entspricht Duv +{@value #FULL_DUV} (grün),
 * -100 % entspricht Duv -{@value #FULL_DUV} (magenta), linear dazwischen. Hersteller
 * veröffentlichen keine Duv-Skala ihrer G/M-Regler; die Zuordnung ist daher eine
 * dokumentierte Setzung, die einheitlich für Lampen und Kamera-Weißabgleich gilt.</p>
 */
public final class Tint
{
    public static final double FULL_DUV = 0.02;

    private Tint()
    {}

    public static double duv(double percent)
    {
        return percent / 100.0 * FULL_DUV;
    }

    public static double percent(double duv)
    {
        return duv / FULL_DUV * 100.0;
    }
}
