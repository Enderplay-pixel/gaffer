package gaffer.physics;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lichtpult (W7): Kanäle mit Pegel, Gruppen mit Master und Cues mit Überblendzeit.
 * Die Überblendung ist eine Funktion des Welt-Ticks (kein Echtzeit-Timer), damit Live-Spiel,
 * Replay und Export gleich aussehen.
 *
 * <p>Ausgabe eines Kanals = Kanalpegel · Produkt der Master aller Gruppen, die ihn enthalten.</p>
 */
public final class Console
{
    /** Gruppe mit Mitgliedskanälen und Master 0..1. */
    public record Group(String id, Set<String> channels, double master)
    {}

    /** Stimmung: gespeicherte Kanalpegel und Überblendzeit in Sekunden. */
    public record Cue(String id, Map<String, Double> levels, double fadeSeconds)
    {}

    private final Map<String, Double> manual = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();

    // Laufende Überblendung
    private Map<String, Double> fadeFrom = Map.of();
    private Cue activeCue;
    private long goTick;

    public void setChannel(String channel, double level)
    {
        manual.put(channel, clamp01(level));
    }

    public void putGroup(Group g)
    {
        groups.put(g.id(), g);
    }

    /**
     * Cue starten ("Go") am Tick {@code tick}. Ausgangspunkt der Überblendung sind die Pegel,
     * die in diesem Moment anliegen.
     */
    public void go(Cue cue, long tick)
    {
        Map<String, Double> now = new HashMap<>();
        for (String ch : allChannels(cue)) now.put(ch, channelLevel(ch, tick, 0f));
        fadeFrom = now;
        activeCue = cue;
        goTick = tick;
    }

    /** Pegel eines Kanals vor den Gruppen-Mastern. */
    public double channelLevel(String channel, long tick, float partial)
    {
        double base = manual.getOrDefault(channel, 0.0);
        if (activeCue == null) return base;
        Double target = activeCue.levels().get(channel);
        if (target == null) return base;
        double from = fadeFrom.getOrDefault(channel, base);
        double fade = activeCue.fadeSeconds();
        double x = fade <= 0 ? 1.0 : clamp01((tick - goTick + (double) partial) / Effects.TPS / fade);
        return from + (target - from) * x;
    }

    /** Endpegel eines Kanals mit allen Gruppen-Mastern. */
    public double output(String channel, long tick, float partial)
    {
        double v = channelLevel(channel, tick, partial);
        for (Group g : groups.values()) if (g.channels().contains(channel)) v *= clamp01(g.master());
        return v;
    }

    private Set<String> allChannels(Cue cue)
    {
        Set<String> s = new java.util.HashSet<>(manual.keySet());
        s.addAll(cue.levels().keySet());
        return s;
    }

    public List<Group> groups()
    {
        return List.copyOf(groups.values());
    }

    private static double clamp01(double v)
    {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
