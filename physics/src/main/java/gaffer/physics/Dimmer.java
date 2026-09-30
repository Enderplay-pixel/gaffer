package gaffer.physics;

/**
 * Dimmerkurven: Reglerstellung 0..1 wird zu Lichtausgabe 0..1.
 *
 * <p>Genau vier Kurven wie an der Aputure STORM: linear, S-Kurve, exponentiell,
 * logarithmisch. <b>Festlegung:</b> Hersteller veröffentlichen die exakte Form nicht. Hier:
 * S-Kurve = {@code 3x² - 2x³}; exponentiell = {@code (e^(kx) - 1) / (e^k - 1)} mit
 * {@code k = } {@value #K}; logarithmisch = deren Umkehrfunktion
 * {@code ln(1 + (e^k - 1) x) / k}. Alle Kurven gehen durch (0,0) und (1,1).</p>
 */
public final class Dimmer
{
    public static final double K = 4.0;

    private Dimmer()
    {}

    public enum Curve
    {
        LINEAR, S_CURVE, EXPONENTIAL, LOGARITHMIC;

        public double apply(double level)
        {
            double x = Math.max(0.0, Math.min(1.0, level));
            return switch (this)
            {
                case LINEAR -> x;
                case S_CURVE -> x * x * (3.0 - 2.0 * x);
                case EXPONENTIAL -> Math.expm1(K * x) / Math.expm1(K);
                case LOGARITHMIC -> Math.log1p(Math.expm1(K) * x) / K;
            };
        }
    }

    /**
     * Ausgabemodus. {@code CONSTANT}: gleiche Lichtstärke über alle Kelvin-Werte.
     * {@code MAX}: die je Kelvin maximal mögliche Leistung der Lampe, nur wählbar, wenn das
     * Datenblatt der Lampe Werte pro Kelvin angibt.
     */
    public enum OutputMode
    {
        MAX, CONSTANT
    }
}
