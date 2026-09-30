package gaffer.physics;

import java.util.List;

/**
 * Die Farbmodi einer Lampe (Vorbild Aputure STORM 700x). Jeder Modus rechnet in die
 * kanonische {@link LightColor} und aus ihr zurück.
 */
public final class ColorModes
{
    /** Bereich des CCT-Modus (STORM). Die Farbtemperatur-Umrechnung selbst deckt 1700..20000 K ab. */
    public static final double CCT_MIN = 2500, CCT_MAX = 10000;

    private ColorModes()
    {}

    // ---------------------------------------------------------------- CCT

    /** CCT-Modus: Kelvin und Grün/Magenta in Prozent (-100..+100). Der Pegel bleibt unverändert (Constant Output). */
    public record Cct(double kelvin, double greenMagentaPercent)
    {
        public LightColor toColor(double level)
        {
            return LightColor.of(Cie.cctDuvToXy(kelvin, Tint.duv(greenMagentaPercent)), level);
        }

        public static Cct from(LightColor c)
        {
            Cie.CctDuv cd = Cie.xyToCctDuv(c.xy());
            return new Cct(cd.kelvin(), Tint.percent(cd.duv()));
        }

        /**
         * Ob die Farbe im CCT-Modus darstellbar ist (2500..10000 K, -100..+100 %). Stark gesättigte
         * Farben liegen weit neben der Planck-Kurve; ein Wechsel in CCT muss sie begrenzen, die
         * Oberfläche zeigt dann eine Warnung statt stillschweigend zu springen.
         */
        public static boolean representable(LightColor c)
        {
            Cct m = from(c);
            return m.kelvin() >= CCT_MIN - 1e-6 && m.kelvin() <= CCT_MAX + 1e-6
                && Math.abs(m.greenMagentaPercent()) <= 100 + 1e-6;
        }
    }

    // ---------------------------------------------------------------- HSI

    /**
     * HSI-Modus: Farbton 0..360°, Sättigung 0..1, Intensität 0..1 und Weißpunkt in Kelvin,
     * auf den Sättigung 0 fällt.
     *
     * <p><b>Festlegung:</b> Der Emitter-Farbraum der STORM ist nicht veröffentlicht. Sättigung 1
     * liegt deshalb auf dem Rand des Render-Farbraums Rec.709 in Richtung des Farbtons (Farbton
     * wie HSV auf den Rec.709-Primärvalenzen); dazwischen linear in CIE xy zwischen Weißpunkt und
     * Rand. Intensität = relativer Pegel Y.</p>
     */
    public record Hsi(double hueDeg, double saturation, double intensity, double whiteKelvin)
    {
        public LightColor toColor()
        {
            Cie.Xy w = Cie.planckXy(whiteKelvin);
            Cie.Xy p = hueBoundary(hueDeg);
            return new LightColor(w.x() + saturation * (p.x() - w.x()), w.y() + saturation * (p.y() - w.y()), intensity);
        }

        /** HSI-Regler zu einer Farbe, mit gegebenem Weißpunkt. Sättigung über 1 = außerhalb Rec.709. */
        public static Hsi from(LightColor c, double whiteKelvin)
        {
            Cie.Xy w = Cie.planckXy(whiteKelvin);
            double dx = c.x() - w.x(), dy = c.y() - w.y();
            double dist = Math.hypot(dx, dy);
            if (dist < 1e-12) return new Hsi(0, 0, c.level(), whiteKelvin);
            double target = Math.atan2(dy, dx);
            double h = hueForAngle(w, target);
            Cie.Xy p = hueBoundary(h);
            double s = dist / Math.hypot(p.x() - w.x(), p.y() - w.y());
            return new Hsi(h, s, c.level(), whiteKelvin);
        }

        /** Voll gesättigter Punkt eines Farbtons auf dem Rec.709-Rand. */
        public static Cie.Xy hueBoundary(double hueDeg)
        {
            double h = ((hueDeg % 360) + 360) % 360 / 60.0;
            int i = (int) Math.floor(h) % 6;
            double f = h - Math.floor(h);
            double[] rgb = switch (i)
            {
                case 0 -> new double[]{1, f, 0};
                case 1 -> new double[]{1 - f, 1, 0};
                case 2 -> new double[]{0, 1, f};
                case 3 -> new double[]{0, 1 - f, 1};
                case 4 -> new double[]{f, 0, 1};
                default -> new double[]{1, 0, 1 - f};
            };
            return Cie.Xy.fromXyz(Rec709.toXyz(rgb));
        }

        /** Farbton, dessen Randpunkt vom Weißpunkt aus unter dem Winkel {@code angle} liegt (Bisektion). */
        private static double hueForAngle(Cie.Xy w, double angle)
        {
            double a0 = angleOf(w, hueBoundary(0));
            double target = wrap(angle - a0);
            double lo = 0, hi = 360;
            for (int i = 0; i < 64; i++)
            {
                double mid = (lo + hi) / 2;
                double a = wrap(angleOf(w, hueBoundary(mid)) - a0);
                if (mid > 180 && a < 1e-9) a = 2 * Math.PI;   // Rand bei 360° = 0°
                if (a < target) lo = mid; else hi = mid;
            }
            return ((lo + hi) / 2) % 360;
        }

        private static double angleOf(Cie.Xy w, Cie.Xy p)
        {
            return Math.atan2(p.y() - w.y(), p.x() - w.x());
        }

        private static double wrap(double a)
        {
            double t = a % (2 * Math.PI);
            return t < 0 ? t + 2 * Math.PI : t;
        }
    }

    // ---------------------------------------------------------------- RGB

    /** RGB-Modus: 8-Bit-Werte 0..255, sRGB-kodiert wie ein Hex-Code in Grafikprogrammen (Festlegung). */
    public record Rgb(int r, int g, int b)
    {
        public LightColor toColor()
        {
            double[] lin = {Rec709.decode(r / 255.0), Rec709.decode(g / 255.0), Rec709.decode(b / 255.0)};
            double[] xyz = Rec709.toXyz(lin);
            double sum = xyz[0] + xyz[1] + xyz[2];
            if (sum < 1e-12) return LightColor.of(Rec709.WHITE, 0.0);
            return new LightColor(xyz[0] / sum, xyz[1] / sum, xyz[1]);
        }

        /** RGB-Regler einer Farbe; Kanäle außerhalb 0..255 werden abgeschnitten ({@link #clipped}). */
        public static Rgb from(LightColor c)
        {
            double[] lin = c.toLinearRec709();
            return new Rgb(to8(lin[0]), to8(lin[1]), to8(lin[2]));
        }

        /** Ob die Farbe im RGB-Modus nicht darstellbar ist und abgeschnitten wurde. */
        public static boolean clipped(LightColor c)
        {
            double[] lin = c.toLinearRec709();
            for (double v : lin) if (v < -1e-9 || v > 1 + 1e-9) return true;
            return false;
        }

        public String hex()
        {
            return String.format("#%02X%02X%02X", r, g, b);
        }

        public static Rgb fromHex(String hex)
        {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() != 6) throw new IllegalArgumentException(hex);
            int v = Integer.parseInt(h, 16);
            return new Rgb((v >> 16) & 255, (v >> 8) & 255, v & 255);
        }

        private static int to8(double linear)
        {
            double e = Rec709.encode(Math.max(0.0, Math.min(1.0, linear)));
            return (int) Math.round(e * 255.0);
        }
    }

    // ---------------------------------------------------------------- xy

    /** xy-Modus: CIE-1931-Koordinaten. Außerhalb von Rec.709 wird markiert und abgeschnitten. */
    public record Xy(double x, double y)
    {
        public boolean outOfGamut()
        {
            return !Rec709.inGamut(new Cie.Xy(x, y), 1e-9);
        }

        /** Renderbare Farbe (abgeschnitten, falls außerhalb). Pegel bleibt. */
        public LightColor toColor(double level)
        {
            return LightColor.of(Rec709.clipToGamut(new Cie.Xy(x, y)), level);
        }

        public static Xy from(LightColor c)
        {
            return new Xy(c.x(), c.y());
        }
    }

    // ---------------------------------------------------------------- Gel

    /**
     * Gel-Modus: eine Grund-Farbtemperatur (mit Duv) plus eine Folie der Gel-Bibliothek.
     * Beim Wechsel in diesen Modus wird die Grundfarbe so zurückgerechnet, dass Folie + Grund
     * exakt die bisherige Farbe ergeben.
     */
    public record GelMode(double baseKelvin, double baseDuv, Filters.Gel gel)
    {
        public LightColor toColor(double baseLevel)
        {
            return gel.apply(baseKelvin, baseDuv, baseLevel);
        }

        public static GelMode from(LightColor c, Filters.Gel gel)
        {
            if (!gel.verified()) throw new IllegalArgumentException("Gel ohne Datenblatt: " + gel.key());
            Cie.CctDuv cd = Cie.xyToCctDuv(c.xy());
            double baseK = Filters.applyMired(cd.kelvin(), -gel.miredShift());
            return new GelMode(baseK, cd.duv() - gel.duvShift(), gel);
        }

        /** Grundpegel vor der Folie, damit nach der Folie der Pegel von {@code c} herauskommt. */
        public static double baseLevel(LightColor c, Filters.Gel gel)
        {
            return c.level() / Filters.transmission(gel.stops());
        }
    }

    // ---------------------------------------------------------------- Source Match

    /**
     * Source Match: Nachbildung typischer Lichtquellen. Werte = Chromatizität der
     * CIE-Normlichtarten (CIE 015:2018, Tabellen der Normlichtarten). Einträge ohne
     * belegbare Quelle haben {@code source == null} und werden nicht angeboten.
     * Ein Wechsel in Source Match wählt eine Quelle (Voreinstellung), er rechnet nicht um.
     */
    public record Source(String key, double x, double y, String source)
    {
        public boolean verified()
        {
            return source != null;
        }

        public LightColor toColor(double level)
        {
            return new LightColor(x, y, level);
        }
    }

    public static final List<Source> SOURCES = List.of(
        new Source("sodium_vapour", 0.533, 0.415, "CIE 015:2018, Normlichtart HP1 (Natriumdampf-Hochdrucklampe)"),
        new Source("fluorescent", 0.3721, 0.3751, "CIE 015:2018, Normlichtart FL2 (Leuchtstofflampe kaltweiß)"),
        new Source("tungsten", 0.44758, 0.40745, "CIE 015:2018, Normlichtart A (Glühlampe)"),
        new Source("daylight_overcast", 0.3127, 0.3290, "CIE 015:2018, Normlichtart D65; Zuordnung zu bewölktem Tageslicht ist eine Festlegung"),
        new Source("mercury_vapour", Double.NaN, Double.NaN, null),
        new Source("candle", Double.NaN, Double.NaN, null),
        new Source("moonlight_look", Double.NaN, Double.NaN, null)
    );
}
