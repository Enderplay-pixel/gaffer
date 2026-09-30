package gaffer.physics;

import java.util.List;

/**
 * Belichtungsmesser (Einfallslicht, W5). Jede Lampe liefert ihren Beitrag als
 * Beleuchtungsstärke senkrecht zur Einfallsrichtung ({@code normalLux}) und die Richtung, aus
 * der das Licht kommt. Verdeckung (Blöcke, Flags) rechnet der Aufrufer vorher ein.
 *
 * <p>Messmodi:</p>
 * <ul>
 *   <li>Scheibe (flacher Empfänger): {@code E = Σ E_n · max(0, cos θ)}</li>
 *   <li>Kalotte (Näherung): {@code E = Σ E_n · (1 + cos θ) / 2}</li>
 * </ul>
 * <p>θ ist der Winkel zwischen Empfänger-Ausrichtung und Richtung zur Lampe.</p>
 */
public final class Meter
{
    private Meter()
    {}

    public enum Receptor
    {
        DISC, DOME
    }

    /** Beitrag einer Lampe. {@code toLight} zeigt vom Messpunkt zur Lampe. */
    public record Contribution(String lightId, double normalLux, double[] toLight)
    {}

    public static double lux(List<Contribution> lights, double[] facing, Receptor receptor)
    {
        double[] f = AreaLight.normalize(facing);
        double sum = 0;
        for (Contribution c : lights)
        {
            double cos = AreaLight.dot(f, AreaLight.normalize(c.toLight()));
            sum += c.normalLux() * (receptor == Receptor.DISC ? Math.max(0.0, cos) : (1.0 + cos) * 0.5);
        }
        return sum;
    }

    /** Messwert einer Auswahl (Solo): nur die genannten Lampen. */
    public static double luxOf(List<Contribution> lights, java.util.Set<String> ids, double[] facing, Receptor receptor)
    {
        return lux(lights.stream().filter(c -> ids.contains(c.lightId())).toList(), facing, receptor);
    }
}
