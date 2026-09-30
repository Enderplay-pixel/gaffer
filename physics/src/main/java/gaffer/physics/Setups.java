package gaffer.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Licht-Setups auf Knopfdruck (W10), relativ zur Figur (Kopfposition) und zur Kamera.
 * Ergebnis sind Lampenpositionen und Zielrichtungen; danach normal editierbar.
 *
 * <p>Winkel: {@code side} = horizontaler Winkel von der Kameraachse aus (positiv = Seite der
 * Key-Seite, 180° = hinter der Figur), {@code up} = Höhenwinkel über Augenhöhe.
 * <b>Festlegung:</b> Die Winkel folgen der üblichen Lehrbuchbeschreibung der Setups
 * (Butterfly: Key über der Kameraachse; Split: Key 90° seitlich; Rembrandt: 45° seitlich und
 * 45° hoch; Loop: etwa 30° seitlich, leicht erhöht; Clamshell: Key über, Fill unter der
 * Kameraachse; Gegenlicht: hinter der Figur). Eine konkrete Literaturstelle war in dieser
 * Arbeitsumgebung nicht abrufbar; die Werte sind Startpunkte, keine Messwerte.</p>
 */
public final class Setups
{
    public enum Kind
    {
        THREE_POINT, REMBRANDT, LOOP, BUTTERFLY, SPLIT, CLAMSHELL, BACKLIGHT
    }

    /** Rolle der Lampe im Setup, zugleich Übersetzungsschlüssel und Namensbasis. */
    public enum Role
    {
        KEY, FILL, BACK, BOTTOM_FILL
    }

    /** Eine Lampe: Weltposition und Einheitsrichtung auf den Kopf der Figur. */
    public record Placement(Role role, double[] position, double[] direction)
    {}

    private record Spec(Role role, double sideDeg, double upDeg)
    {}

    private Setups()
    {}

    private static List<Spec> specs(Kind k)
    {
        return switch (k)
        {
            case THREE_POINT -> List.of(new Spec(Role.KEY, 45, 30), new Spec(Role.FILL, -45, 15), new Spec(Role.BACK, 160, 40));
            case REMBRANDT -> List.of(new Spec(Role.KEY, 45, 45));
            case LOOP -> List.of(new Spec(Role.KEY, 30, 20));
            case BUTTERFLY -> List.of(new Spec(Role.KEY, 0, 40));
            case SPLIT -> List.of(new Spec(Role.KEY, 90, 0));
            case CLAMSHELL -> List.of(new Spec(Role.KEY, 0, 30), new Spec(Role.BOTTOM_FILL, 0, -25));
            case BACKLIGHT -> List.of(new Spec(Role.BACK, 180, 35));
        };
    }

    /**
     * @param head      Kopfposition der Figur (Welt, m)
     * @param camera    Kameraposition (Welt, m)
     * @param distance  Abstand Lampe zu Kopf in m
     */
    public static List<Placement> place(Kind kind, double[] head, double[] camera, double distance)
    {
        // Horizontale Achse Kopf -> Kamera
        double ax = camera[0] - head[0], az = camera[2] - head[2];
        double al = Math.hypot(ax, az);
        if (al < 1e-9) { ax = 0; az = 1; al = 1; }
        ax /= al; az /= al;
        List<Placement> out = new ArrayList<>();
        for (Spec s : specs(kind))
        {
            double side = Math.toRadians(s.sideDeg()), up = Math.toRadians(s.upDeg());
            // Drehung der Kameraachse um die Hochachse
            double hx = ax * Math.cos(side) - az * Math.sin(side);
            double hz = ax * Math.sin(side) + az * Math.cos(side);
            double[] off = {hx * Math.cos(up) * distance, Math.sin(up) * distance, hz * Math.cos(up) * distance};
            double[] pos = {head[0] + off[0], head[1] + off[1], head[2] + off[2]};
            double[] dir = {-off[0] / distance, -off[1] / distance, -off[2] / distance};
            out.add(new Placement(s.role(), pos, dir));
        }
        return out;
    }
}
