package gaffer.physics;

/**
 * Effekt-Generatoren (W8, mit der FX-Liste der Aputure STORM). Jeder Effekt ist eine reine
 * Funktion von (Welt-Tick + Teiltick, Seed, Parameter) und liefert einen Helligkeitsfaktor
 * 0..1 sowie eine Farbverschiebung in Mired. Keine Systemzeit, keine Tageszeit: derselbe
 * Tick ergibt immer denselben Wert, live wie im Replay und im Export.
 *
 * <p>Die Zeitformen sind Festlegungen (keine Messwerte): Rauschen aus einem ganzzahligen
 * Hash, stückweise glatt interpoliert; Parameter sind vom Nutzer einstellbar.</p>
 */
public final class Effects
{
    /** Ticks pro Sekunde in Minecraft. */
    public static final double TPS = 20.0;

    private Effects()
    {}

    public enum Type
    {
        NONE, CANDLE, FIRE, TV, LIGHTNING, POLICE, STROBE, NEON_START,
        PAPARAZZI, FIREWORKS, FAULTY_BULB, CANNON, PULSE, EXPLOSION
    }

    /**
     * Parameter eines Effekts.
     *
     * @param speed      Grundfrequenz in Hz (Flackern, Blinken, Pulsieren)
     * @param depth      Stärke 0..1 (0 = kein Effekt)
     * @param colorSwing maximale Farbverschiebung in Mired (Feuer, Kerze, TV), 0 = keine
     * @param startTick  Auslöse-Tick für einmalige Effekte (Kanone, Explosion, Neon)
     * @param seed       Startwert des Rauschens; gleiche Seeds flackern gleich
     */
    public record Params(double speed, double depth, double colorSwing, long startTick, long seed)
    {}

    /** Wert eines Effekts zu einem Zeitpunkt. */
    public record Sample(double level, double miredShift)
    {
        static final Sample ON = new Sample(1.0, 0.0);
    }

    public static Sample evaluate(Type type, Params p, long tick, float partialTick)
    {
        double t = (tick + (double) partialTick) / TPS;             // s seit Weltbeginn
        double since = (tick - p.startTick() + (double) partialTick) / TPS; // s seit Auslösung
        double d = clamp01(p.depth());
        double f = Math.max(1e-3, p.speed());
        long s = p.seed();
        return switch (type)
        {
            case NONE -> Sample.ON;
            case CANDLE -> flicker(t, f, d, p.colorSwing(), s, 2);
            case FIRE -> flicker(t, f, d, p.colorSwing(), s, 3);
            case TV ->
            {
                // Szenenschnitte: Stufen mit zufälliger Dauer, dazu leichtes Flimmern
                double step = Math.floor(t * f);
                double lvl = 0.35 + 0.65 * hash01(s, (long) step);
                double shimmer = 0.05 * (noise1(s + 7, t * 12.0) - 0.5);
                double col = (hash01(s + 3, (long) step) - 0.5) * 2.0 * p.colorSwing();
                yield new Sample(mix(1.0, clamp01(lvl + shimmer), d), col);
            }
            case LIGHTNING ->
            {
                // Pro Intervall 1/f zufällig ein Einschlag mit 2..3 kurzen Nachblitzen
                double period = 1.0 / f;
                long idx = (long) Math.floor(t / period);
                double local = t - idx * period;
                double at = hash01(s, idx) * period * 0.8;
                double x = local - at;
                double flash = 0;
                for (int k = 0; k < 3; k++)
                {
                    double off = k * (0.08 + 0.05 * hash01(s + k, idx));
                    flash = Math.max(flash, pulseDecay(x - off, 0.05) * (k == 0 ? 1.0 : 0.6));
                }
                yield new Sample(mix(1.0, flash, d), 0);
            }
            case POLICE ->
            {
                // Rundumlicht: zwei Doppelblitze pro Periode
                double ph = frac(t * f);
                double on = (ph < 0.1 || (ph > 0.15 && ph < 0.25)) ? 1.0 : 0.0;
                yield new Sample(mix(1.0, on, d), 0);
            }
            case STROBE -> new Sample(mix(1.0, frac(t * f) < 0.5 ? 1.0 : 0.0, d), 0);
            case PULSE -> new Sample(mix(1.0, 0.5 - 0.5 * Math.cos(2 * Math.PI * f * t), d), 0);
            case NEON_START ->
            {
                // Röhre zündet: unregelmäßiges Zucken, nach 1/f Sekunden voll an
                double dur = 1.0 / f;
                if (since < 0) yield new Sample(1.0 - d, 0);
                if (since >= dur) yield Sample.ON;
                long slot = (long) Math.floor(since * 15.0);
                double on = hash01(s, slot) < since / dur ? 1.0 : 0.1;
                yield new Sample(mix(1.0, on, d), 0);
            }
            case PAPARAZZI ->
            {
                // Viele Fotoblitze: pro 1/20 s Zeitfenster Blitz mit Wahrscheinlichkeit ~ speed/20
                long slot = (long) Math.floor(t * 20.0);
                double x = t - slot / 20.0;
                double on = hash01(s, slot) < f / 20.0 ? pulseDecay(x, 0.02) : 0;
                yield new Sample(mix(1.0, on, d), 0);
            }
            case FIREWORKS ->
            {
                long idx = (long) Math.floor(t * f);
                double x = t - idx / f;
                double burst = pulseDecay(x - hash01(s, idx) * 0.3 / f, 0.4) * (0.5 + 0.5 * hash01(s + 1, idx));
                double col = (hash01(s + 2, idx) - 0.5) * 2.0 * p.colorSwing();
                yield new Sample(mix(1.0, burst, d), col);
            }
            case FAULTY_BULB ->
            {
                // Meist an, gelegentlich kurze Aussetzer
                long slot = (long) Math.floor(t * 10.0);
                double off = hash01(s, slot) < 0.15 * f ? 0.0 : 1.0;
                yield new Sample(mix(1.0, off, d), 0);
            }
            case CANNON -> new Sample(mix(1.0, since < 0 ? 0 : pulseDecay(since, 0.15), d), 0);
            case EXPLOSION ->
            {
                double core = since < 0 ? 0 : pulseDecay(since, 0.6);
                double crackle = since < 0 ? 0 : 0.25 * noise1(s, since * 20.0) * pulseDecay(since, 1.5);
                yield new Sample(mix(1.0, clamp01(core + crackle), d), since < 0 ? 0 : p.colorSwing() * pulseDecay(since, 1.0));
            }
        };
    }

    private static Sample flicker(double t, double f, double d, double swing, long seed, int octaves)
    {
        double n = 0, amp = 0.5, norm = 0;
        for (int o = 0; o < octaves; o++)
        {
            n += amp * noise1(seed + o * 101L, t * f * (1 << o));
            norm += amp;
            amp *= 0.5;
        }
        n /= norm;   // 0..1
        return new Sample(mix(1.0, 1.0 - n, d), (n - 0.5) * 2.0 * swing);
    }

    /** Glattes 1D-Werterauschen 0..1 aus einem ganzzahligen Hash (deterministisch). */
    static double noise1(long seed, double x)
    {
        long i = (long) Math.floor(x);
        double fr = x - i;
        double u = fr * fr * (3 - 2 * fr);
        return mix(hash01(seed, i), hash01(seed, i + 1), u);
    }

    /** SplitMix64-Hash nach 0..1. */
    static double hash01(long seed, long i)
    {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    private static double pulseDecay(double x, double tau)
    {
        return x < 0 ? 0 : Math.exp(-x / tau);
    }

    private static double frac(double x)
    {
        return x - Math.floor(x);
    }

    private static double mix(double a, double b, double t)
    {
        return a + (b - a) * t;
    }

    private static double clamp01(double v)
    {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
