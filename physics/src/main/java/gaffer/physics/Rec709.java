package gaffer.physics;

/**
 * Render-Farbraum: lineares Rec.709 (ITU-R BT.709 Primärvalenzen, Weiß D65) und die
 * sRGB-Kodierung (IEC 61966-2-1) für 8-Bit-Eingaben. Dazu die Bradford-Anpassung
 * für den Kamera-Weißabgleich.
 */
public final class Rec709
{
    public static final Cie.Xy R = new Cie.Xy(0.64, 0.33);
    public static final Cie.Xy G = new Cie.Xy(0.30, 0.60);
    public static final Cie.Xy B = new Cie.Xy(0.15, 0.06);
    /** D65 wie in ITU-R BT.709 angegeben. */
    public static final Cie.Xy WHITE = new Cie.Xy(0.3127, 0.3290);

    public static final double[][] RGB_TO_XYZ;
    public static final double[][] XYZ_TO_RGB;

    private static final double[][] BRADFORD = {
        {0.8951, 0.2664, -0.1614},
        {-0.7502, 1.7135, 0.0367},
        {0.0389, -0.0685, 1.0296}
    };
    private static final double[][] BRADFORD_INV = Mat3.inverse(BRADFORD);

    static
    {
        double[][] p = {R.toXyz(1), G.toXyz(1), B.toXyz(1)};
        double[][] m = {
            {p[0][0], p[1][0], p[2][0]},
            {p[0][1], p[1][1], p[2][1]},
            {p[0][2], p[1][2], p[2][2]}
        };
        double[] s = Mat3.mul(Mat3.inverse(m), WHITE.toXyz(1));
        RGB_TO_XYZ = new double[3][3];
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                RGB_TO_XYZ[r][c] = m[r][c] * s[c];
        XYZ_TO_RGB = Mat3.inverse(RGB_TO_XYZ);
    }

    private Rec709()
    {}

    public static double[] toXyz(double[] linearRgb)
    {
        return Mat3.mul(RGB_TO_XYZ, linearRgb);
    }

    public static double[] fromXyz(double[] xyz)
    {
        return Mat3.mul(XYZ_TO_RGB, xyz);
    }

    /** sRGB-Kodierung eines linearen Wertes 0..1. */
    public static double encode(double linear)
    {
        return linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
    }

    /** sRGB-Dekodierung eines kodierten Wertes 0..1. */
    public static double decode(double encoded)
    {
        return encoded <= 0.04045 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /** Liegt die Chromatizität im Rec.709-Dreieck (Rand eingeschlossen, Toleranz eps)? */
    public static boolean inGamut(Cie.Xy p, double eps)
    {
        return side(R, G, p) >= -eps && side(G, B, p) >= -eps && side(B, R, p) >= -eps;
    }

    private static double side(Cie.Xy a, Cie.Xy b, Cie.Xy p)
    {
        return (b.x() - a.x()) * (p.y() - a.y()) - (b.y() - a.y()) * (p.x() - a.x());
    }

    /**
     * Schneidet eine Chromatizität außerhalb des Dreiecks auf dessen Rand ab, entlang der
     * Linie zum Weißpunkt D65 (Farbton bleibt, Sättigung sinkt).
     */
    public static Cie.Xy clipToGamut(Cie.Xy p)
    {
        if (inGamut(p, 0)) return p;
        double best = 1.0;
        Cie.Xy[][] edges = {{R, G}, {G, B}, {B, R}};
        for (Cie.Xy[] e : edges)
        {
            double t = segmentHit(WHITE, p, e[0], e[1]);
            if (t >= 0 && t < best) best = t;
        }
        return new Cie.Xy(WHITE.x() + best * (p.x() - WHITE.x()), WHITE.y() + best * (p.y() - WHITE.y()));
    }

    /** Parameter t auf W->P, an dem die Kante a-b geschnitten wird, oder -1. */
    static double segmentHit(Cie.Xy w, Cie.Xy p, Cie.Xy a, Cie.Xy b)
    {
        double rx = p.x() - w.x(), ry = p.y() - w.y();
        double sx = b.x() - a.x(), sy = b.y() - a.y();
        double den = rx * sy - ry * sx;
        if (Math.abs(den) < 1e-15) return -1;
        double qx = a.x() - w.x(), qy = a.y() - w.y();
        double t = (qx * sy - qy * sx) / den;
        double u = (qx * ry - qy * rx) / den;
        return (u >= -1e-12 && u <= 1 + 1e-12) ? t : -1;
    }

    /**
     * Chromatische Anpassung nach Bradford: Farbe, die unter Weiß {@code from} gesehen wird,
     * so umrechnen, als stünde sie unter Weiß {@code to}.
     */
    public static double[] bradford(double[] xyz, Cie.Xy from, Cie.Xy to)
    {
        double[] ls = Mat3.mul(BRADFORD, from.toXyz(1));
        double[] ld = Mat3.mul(BRADFORD, to.toXyz(1));
        double[] c = Mat3.mul(BRADFORD, xyz);
        double[] a = {c[0] * ld[0] / ls[0], c[1] * ld[1] / ls[1], c[2] * ld[2] / ls[2]};
        return Mat3.mul(BRADFORD_INV, a);
    }

    /** Luminanz (relativ) eines linearen Rec.709-Wertes = Y. */
    public static double luminance(double[] linearRgb)
    {
        return toXyz(linearRgb)[1];
    }
}
