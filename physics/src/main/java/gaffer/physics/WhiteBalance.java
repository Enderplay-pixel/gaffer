package gaffer.physics;

/**
 * Kamera-Weißabgleich in Kelvin plus Tint (Grün/Magenta in Prozent, gleiche Skala wie
 * bei den Lampen, siehe {@link Tint}). Standard 5600 K, Tint 0.
 */
public record WhiteBalance(double kelvin, double tintPercent)
{
    public static final WhiteBalance DEFAULT = new WhiteBalance(5600, 0);

    public Cie.Xy xy()
    {
        return Cie.cctDuvToXy(kelvin, Tint.duv(tintPercent));
    }
}
