package gaffer.physics;

/**
 * Kamera-Belichtung und Lichtmessung nach der Einfallslicht-Belichtungsgleichung
 * (ISO 2720:1974): {@code N² / t = E * S / C}.
 *
 * <p>N = Blendenzahl, t = Belichtungszeit in s, E = Beleuchtungsstärke in lx,
 * S = ISO-Empfindlichkeit (arithmetisch), C = Kalibrierkonstante des Messers.
 * Ein ND-Filter der Dichte D verlangt {@code 10^D}-mal mehr Licht.</p>
 *
 * <p>Die Werte von C sind in ISO 2720 als Bereiche festgelegt; die hier gewählten
 * Standardwerte stehen in {@link #C_FLAT} und {@link #C_DOME}. Prüfvermerk: In dieser
 * Arbeitsumgebung war die Norm nicht abrufbar, die Werte sind vor der Abnahme gegen
 * ISO 2720 bzw. das Datenblatt eines Sekonic-Messers zu prüfen (siehe docs/ABNAHME.md).</p>
 */
public final class Exposure
{
    /** C für flache Messscheibe (Kosinus-Empfänger). Prüfvermerk siehe Klassenkommentar. */
    public static final double C_FLAT = 250.0;
    /** C für Kalotte (halbkugeliger Empfänger). Prüfvermerk siehe Klassenkommentar. */
    public static final double C_DOME = 340.0;

    private Exposure()
    {}

    /**
     * Kamera-Einstellung.
     *
     * @param iso          Empfindlichkeit S
     * @param shutterDeg   Verschlusswinkel in Grad (z. B. 180)
     * @param fps          Bildrate
     * @param fNumber      Blende N
     * @param ndDensity    optische Dichte des ND-Filters (0.3 = etwa 1 Blende)
     */
    public record Camera(double iso, double shutterDeg, double fps, double fNumber, double ndDensity)
    {
        /** Belichtungszeit in s: {@code (Winkel / 360) / fps}. */
        public double exposureTime()
        {
            return (shutterDeg / 360.0) / fps;
        }

        /**
         * Beleuchtungsstärke, bei der eine 18-%-Graukarte genau richtig belichtet ist:
         * {@code E_ref = C * N² * 10^D / (t * S)}.
         */
        public double referenceLux(double c)
        {
            return c * fNumber * fNumber * Math.pow(10.0, ndDensity) / (exposureTime() * iso);
        }
    }

    /**
     * Blende, die ein Belichtungsmesser bei {@code lux} anzeigt:
     * {@code N = sqrt(E * t * S / (C * 10^D))}.
     */
    public static double fNumberFor(double lux, double iso, double exposureTime, double ndDensity, double c)
    {
        return Math.sqrt(lux * exposureTime * iso / (c * Math.pow(10.0, ndDensity)));
    }

    /** Lichtwert bei ISO 100 für eine Einfallsmessung: {@code EV = log2(E * 100 / C)}. */
    public static double ev100(double lux, double c)
    {
        return log2(lux * 100.0 / c);
    }

    /** Abstand zweier Beleuchtungsstärken in Blenden: {@code log2(a / b)}. */
    public static double stops(double luxA, double luxB)
    {
        return log2(luxA / luxB);
    }

    /**
     * Kontrast Key zu Fill als Abstand in Blenden, gemessen mit Key allein und Fill allein:
     * {@code log2(Key / Fill)}.
     */
    public static double keyFillStops(double keyLux, double fillLux)
    {
        return log2(keyLux / fillLux);
    }

    /** Klassisches Lichtverhältnis {@code (Key + Fill) : Fill}, nur als Nebenanzeige. */
    public static double keyFillRatio(double keyLux, double fillLux)
    {
        return (keyLux + fillLux) / fillLux;
    }

    /** Faktor, mit dem eine Beleuchtungsstärke in Bildhelligkeit relativ zu Mittelgrau übersetzt wird. */
    public static double exposureScale(double lux, Camera camera, double c)
    {
        return lux / camera.referenceLux(c);
    }

    static double log2(double v)
    {
        return Math.log(v) / Math.log(2.0);
    }
}
