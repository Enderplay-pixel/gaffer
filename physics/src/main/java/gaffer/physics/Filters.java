package gaffer.physics;

import java.util.List;

/**
 * Gels, ND-Filter und Scrims.
 *
 * <p>Farbkorrektur-Gels wirken als Mired-Verschiebung ({@code 1e6/K' = 1e6/K + shift}),
 * Grün/Magenta-Gels als Duv-Verschiebung; jede Folie kostet ihre Transmission in Blenden.</p>
 */
public final class Filters
{
    private Filters()
    {}

    /** Farbtemperatur nach einer Mired-Verschiebung. */
    public static double applyMired(double kelvin, double miredShift)
    {
        return 1e6 / (1e6 / kelvin + miredShift);
    }

    /** Mired-Verschiebung, die {@code fromK} nach {@code toK} bringt. */
    public static double miredShift(double fromK, double toK)
    {
        return 1e6 / toK - 1e6 / fromK;
    }

    /** Transmissionsfaktor für einen Verlust in Blenden: {@code 2^-stops}. */
    public static double transmission(double stops)
    {
        return Math.pow(2.0, -stops);
    }

    /** Blenden eines ND-Filters der Dichte D: {@code D / log10(2)}. */
    public static double ndStops(double density)
    {
        return density / Math.log10(2.0);
    }

    /**
     * Eine Folie der Gel-Bibliothek. {@code source} nennt das Datenblatt; ohne Quelle ist
     * {@code verified} false, die Werte sind NaN und die Folie wird in der Oberfläche nicht
     * angeboten, bis das Datenblatt eingetragen ist.
     */
    public record Gel(String key, double miredShift, double duvShift, double stops, String source)
    {
        public boolean verified()
        {
            return source != null && !Double.isNaN(miredShift) && !Double.isNaN(duvShift) && !Double.isNaN(stops);
        }

        /** Farbe nach der Folie; der Pegel sinkt um die Transmission. */
        public LightColor apply(double kelvin, double duv, double level)
        {
            Cie.Xy xy = Cie.cctDuvToXy(applyMired(kelvin, miredShift), duv + duvShift);
            return LightColor.of(xy, level * transmission(stops));
        }
    }

    private static Gel nd(String key, double density)
    {
        // ND ist über seine optische Dichte definiert (T = 10^-D); spektral neutral.
        return new Gel(key, 0, 0, ndStops(density), "Definition optische Dichte: T = 10^-D");
    }

    private static Gel pending(String key)
    {
        // Datenblatt (Lee/Rosco) in dieser Arbeitsumgebung nicht abrufbar: keine Zahl erfinden.
        return new Gel(key, Double.NaN, Double.NaN, Double.NaN, null);
    }

    /** Die Gel-Bibliothek mit generischen Namen (Schlüssel für die Übersetzung). */
    public static final List<Gel> LIBRARY = List.of(
        pending("full_cto"), pending("half_cto"), pending("quarter_cto"),
        pending("full_ctb"), pending("half_ctb"), pending("quarter_ctb"),
        pending("plus_green"), pending("minus_green"),
        nd("nd_03", 0.3), nd("nd_06", 0.6), nd("nd_09", 0.9)
    );

    public static Gel gel(String key)
    {
        for (Gel g : LIBRARY) if (g.key().equals(key)) return g;
        throw new IllegalArgumentException(key);
    }

    /** Scrims im Scheinwerfer. Single = 1/2 Blende, Double = 1 Blende (Vorgabe des Auftrags, Branchenkonvention). */
    public enum Scrim
    {
        NONE(0.0), SINGLE(0.5), DOUBLE(1.0);

        public final double stops;

        Scrim(double stops)
        {
            this.stops = stops;
        }

        public double factor()
        {
            return transmission(stops);
        }
    }

    /** Mehrere Scrims hintereinander: Blenden addieren sich. */
    public static double scrimFactor(Scrim... scrims)
    {
        double s = 0;
        for (Scrim sc : scrims) s += sc.stops;
        return transmission(s);
    }
}
