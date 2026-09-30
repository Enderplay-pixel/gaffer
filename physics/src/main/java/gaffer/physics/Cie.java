package gaffer.physics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CIE-Farbmetrik: Normspektralwerte, Planck-Kurve, CIE-Tageslicht, CIE-1960-uv und
 * Farbtemperatur mit Abstand zur Planck-Kurve (Duv).
 *
 * <p>Normspektralwerte: CIE 1931 2°-Normbeobachter, 360 bis 830 nm, 1 nm
 * (Ressource {@code cie1931_2deg_1nm.csv}, Quelle CIE 015:2018).</p>
 */
public final class Cie
{
    /** Zweite Strahlungskonstante c2 = h c / k in m·K (CODATA, wie in CIE 015:2018 verwendet). */
    public static final double C2 = 1.4388e-2;
    /**
     * c2 der historischen Definition von Normlichtart A: A ist der Planck-Strahler bei 2848 K
     * mit diesem c2, gleichbedeutend mit 2856 K bei {@link #C2}.
     */
    public static final double C2_ILLUMINANT_A = 1.435e-2;

    private static final int LAMBDA0 = 360;
    private static final double[] XBAR, YBAR, ZBAR;

    static
    {
        List<double[]> rows = new ArrayList<>();
        try (InputStream in = Cie.class.getResourceAsStream("cie1931_2deg_1nm.csv"))
        {
            if (in == null) throw new IllegalStateException("cie1931_2deg_1nm.csv fehlt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null)
            {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(",");
                rows.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Double.parseDouble(p[3])});
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
        int n = rows.size();
        XBAR = new double[n]; YBAR = new double[n]; ZBAR = new double[n];
        for (int i = 0; i < n; i++)
        {
            double[] row = rows.get(i);
            if ((int) row[0] != LAMBDA0 + i) throw new IllegalStateException("CMF-Tabelle nicht lückenlos bei " + row[0]);
            XBAR[i] = row[1]; YBAR[i] = row[2]; ZBAR[i] = row[3];
        }
    }

    private Cie()
    {}

    /** Chromatizität (x, y). */
    public record Xy(double x, double y)
    {
        public Uv toUv()
        {
            double d = -2.0 * x + 12.0 * y + 3.0;
            return new Uv(4.0 * x / d, 6.0 * y / d);
        }

        /** Normfarbwerte mit Hellbezugswert Y. */
        public double[] toXyz(double bigY)
        {
            return new double[]{x * bigY / y, bigY, (1.0 - x - y) * bigY / y};
        }

        public static Xy fromXyz(double[] xyz)
        {
            double s = xyz[0] + xyz[1] + xyz[2];
            return new Xy(xyz[0] / s, xyz[1] / s);
        }
    }

    /** CIE 1960 UCS (u, v). */
    public record Uv(double u, double v)
    {
        public Xy toXy()
        {
            double d = 2.0 * u - 8.0 * v + 4.0;
            return new Xy(3.0 * u / d, 2.0 * v / d);
        }
    }

    /** Relative spektrale Strahldichte eines Planck-Strahlers (λ in nm, c2 in m·K). */
    public static double planck(double lambdaNm, double kelvin, double c2)
    {
        double l = lambdaNm * 1e-9;
        return 1.0 / (Math.pow(l, 5) * (Math.exp(c2 / (l * kelvin)) - 1.0));
    }

    /** Chromatizität eines Planck-Strahlers (Integration über die 1-nm-Tabelle). */
    public static Xy planckXy(double kelvin, double c2)
    {
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < XBAR.length; i++)
        {
            double m = planck(LAMBDA0 + i, kelvin, c2);
            x += m * XBAR[i];
            y += m * YBAR[i];
            z += m * ZBAR[i];
        }
        double s = x + y + z;
        return new Xy(x / s, y / s);
    }

    public static Xy planckXy(double kelvin)
    {
        return planckXy(kelvin, C2);
    }

    /**
     * Chromatizität des CIE-Tageslichts (CIE 015:2018, Gleichungen für x_D und y_D),
     * definiert für 4000 K bis 25000 K.
     */
    public static Xy daylightXy(double kelvin)
    {
        if (kelvin < 4000 || kelvin > 25000) throw new IllegalArgumentException("CIE-Tageslicht nur 4000..25000 K");
        double t = kelvin, t2 = t * t, t3 = t2 * t;
        double x = kelvin <= 7000
            ? -4.6070e9 / t3 + 2.9678e6 / t2 + 0.09911e3 / t + 0.244063
            : -2.0064e9 / t3 + 1.9018e6 / t2 + 0.24748e3 / t + 0.237040;
        double y = -3.000 * x * x + 2.870 * x - 0.275;
        return new Xy(x, y);
    }

    /**
     * Punkt mit Farbtemperatur {@code kelvin} und Abstand {@code duv} zur Planck-Kurve
     * (senkrecht in CIE 1960 uv). Positives Duv liegt oberhalb der Kurve (grünlich),
     * negatives unterhalb (magenta).
     */
    public static Xy cctDuvToXy(double kelvin, double duv)
    {
        Uv p = planckXy(kelvin).toUv();
        if (duv == 0.0) return p.toXy();
        double dt = kelvin * 1e-4;
        Uv a = planckXy(kelvin - dt).toUv();
        Uv b = planckXy(kelvin + dt).toUv();
        double tu = b.u() - a.u(), tv = b.v() - a.v();
        double len = Math.hypot(tu, tv);
        double nu = -tv / len, nv = tu / len;
        if (nv < 0) { nu = -nu; nv = -nv; }   // Normale zeigt nach oben (grün)
        return new Uv(p.u() + duv * nu, p.v() + duv * nv).toXy();
    }

    /** Farbtemperatur und Duv eines Punktes. */
    public record CctDuv(double kelvin, double duv)
    {}

    /**
     * Nächster Punkt auf der Planck-Kurve in CIE 1960 uv (Suche in Mired, danach
     * Goldener Schnitt), Suchbereich 1000 K bis 40000 K.
     */
    public static CctDuv xyToCctDuv(Xy xy)
    {
        Uv t = xy.toUv();
        double best = 0, bestD = Double.MAX_VALUE;
        for (double mired = 25; mired <= 1000; mired += 5)
        {
            double d = uvDist2(t, 1e6 / mired);
            if (d < bestD) { bestD = d; best = mired; }
        }
        double lo = Math.max(25, best - 5), hi = Math.min(1000, best + 5);
        final double g = (Math.sqrt(5) - 1) / 2;
        double c = hi - g * (hi - lo), d = lo + g * (hi - lo);
        double fc = uvDist2(t, 1e6 / c), fd = uvDist2(t, 1e6 / d);
        for (int i = 0; i < 80; i++)
        {
            if (fc < fd) { hi = d; d = c; fd = fc; c = hi - g * (hi - lo); fc = uvDist2(t, 1e6 / c); }
            else { lo = c; c = d; fc = fd; d = lo + g * (hi - lo); fd = uvDist2(t, 1e6 / d); }
        }
        double kelvin = 1e6 / ((lo + hi) / 2);
        Uv p = planckXy(kelvin).toUv();
        double dist = Math.hypot(t.u() - p.u(), t.v() - p.v());
        return new CctDuv(kelvin, t.v() >= p.v() ? dist : -dist);
    }

    private static double uvDist2(Uv t, double kelvin)
    {
        Uv p = planckXy(kelvin).toUv();
        double du = t.u() - p.u(), dv = t.v() - p.v();
        return du * du + dv * dv;
    }
}
