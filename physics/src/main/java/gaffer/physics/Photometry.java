package gaffer.physics;

/**
 * Photometrische Grundformeln. 1 Block = 1 m.
 *
 * <p>Einheiten: Lichtstärke I in cd, Lichtstrom in lm, Beleuchtungsstärke E in lx.</p>
 */
public final class Photometry
{
    private Photometry()
    {}

    /**
     * Beleuchtungsstärke einer punktförmigen Quelle nach dem photometrischen
     * Abstandsgesetz: {@code E = I * cos(einfall) / d²}.
     *
     * @param candela       Lichtstärke in Richtung des Empfängers (cd)
     * @param distance      Abstand in m, &gt; 0
     * @param cosIncidence  Kosinus des Einfallswinkels zur Flächennormalen (0..1)
     */
    public static double illuminance(double candela, double distance, double cosIncidence)
    {
        if (distance <= 0) throw new IllegalArgumentException("distance must be > 0");
        return candela * Math.max(0.0, cosIncidence) / (distance * distance);
    }

    /**
     * Weiches Abschneiden für das Culling: {@code (1 - (d/r)^4)^2}, 0 ab {@code d >= r}.
     * Wird nur als Rand verwendet; die Lichtabnahme selbst ist 1/d².
     */
    public static double cullWindow(double distance, double cullRadius)
    {
        double x = distance / cullRadius;
        double f = 1.0 - x * x * x * x;
        return f <= 0 ? 0.0 : f * f;
    }

    /** Abstandsgesetz mit Culling-Rand, genau so wie im Shader. */
    public static double illuminanceCulled(double candela, double distance, double cosIncidence, double cullRadius)
    {
        return illuminance(candela, distance, cosIncidence) * cullWindow(distance, cullRadius);
    }

    /**
     * Culling-Radius: der Abstand, an dem die Beleuchtungsstärke auf {@code minLux}
     * fällt ({@code sqrt(I / Emin)}).
     */
    public static double cullRadius(double candela, double minLux)
    {
        return Math.sqrt(candela / minLux);
    }

    /** Raumwinkel (sr) eines Kegels mit vollem Öffnungswinkel in Grad: {@code 2π(1 - cos(θ/2))}. */
    public static double coneSolidAngle(double fullAngleDeg)
    {
        return 2.0 * Math.PI * (1.0 - Math.cos(Math.toRadians(fullAngleDeg) * 0.5));
    }

    /**
     * Mittlere Lichtstärke bei gleichmäßiger Verteilung des Lichtstroms im Kegel:
     * {@code I = Φ / Ω}. Festlegung für Quellen ohne IES-Daten.
     */
    public static double candelaFromLumen(double lumen, double fullAngleDeg)
    {
        return lumen / coneSolidAngle(fullAngleDeg);
    }

    /** Lichtstärke aus einer Messung "E lx auf d m" (Datenblatt-Angabe): {@code I = E * d²}. */
    public static double candelaFromLuxAt(double lux, double distance)
    {
        return lux * distance * distance;
    }

    /** Leuchtdichte (cd/m²) eines Lambert-Strahlers aus Lichtstärke senkrecht zur Fläche. */
    public static double luminanceFromCandela(double normalCandela, double areaM2)
    {
        return normalCandela / areaM2;
    }
}
