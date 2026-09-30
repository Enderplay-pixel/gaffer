package gaffer.physics;

/**
 * Kanonische Lichtfarbe, auf die alle Farbmodi abbilden: Chromatizität (x, y) nach
 * CIE 1931 und relativer Farbpegel {@code level} (Y relativ zur Nennleistung der
 * Lampe, 1 = Nennleistung). Der Dimmer wirkt zusätzlich und getrennt.
 *
 * <p>Weil jeder Modus aus dieser einen Darstellung seine Regler zurückrechnet, gibt es
 * beim Moduswechsel keinen Farbsprung.</p>
 */
public record LightColor(double x, double y, double level)
{
    public Cie.Xy xy()
    {
        return new Cie.Xy(x, y);
    }

    public static LightColor of(Cie.Xy xy, double level)
    {
        return new LightColor(xy.x(), xy.y(), level);
    }

    /** Normfarbwerte XYZ (Y = level). */
    public double[] toXyz()
    {
        return xy().toXyz(level);
    }

    /** Lineares Rec.709 ohne Weißabgleich (Bezugsweiß D65). */
    public double[] toLinearRec709()
    {
        return Rec709.fromXyz(toXyz());
    }

    /**
     * Lineares Rec.709, wie es die Kamera mit Weißabgleich {@code camera} sieht:
     * Bradford-Anpassung vom Kamera-Weiß auf das Bezugsweiß D65. Eine Lampe mit genau der
     * Farbe des Weißabgleichs wird neutral grau.
     */
    public double[] toCameraRgb(WhiteBalance camera)
    {
        return Rec709.fromXyz(Rec709.bradford(toXyz(), camera.xy(), Rec709.WHITE));
    }
}
