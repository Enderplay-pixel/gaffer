package gaffer.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Beleuchtungsstärke durch ein Flächenlicht (Lambert-Strahler) mit dem exakten
 * Polygon-Integral (Lambert 1760): {@code E = L/2 * Σ θ_i (n · γ_i)}, wobei θ_i der Winkel
 * zwischen den Richtungen zu zwei benachbarten Ecken und γ_i die Normale der von ihnen
 * aufgespannten Ebene ist. Vor der Summe wird das Polygon am Horizont des Empfängers
 * abgeschnitten. Dieselbe Rechnung läuft im Shader; diese Klasse ist die Referenz.
 *
 * <p>Vektoren als {@code double[3]}, Längen in m, Leuchtdichte L in cd/m², Ergebnis in lx.</p>
 */
public final class AreaLight
{
    private AreaLight()
    {}

    /**
     * Rechteck mit Mittelpunkt {@code center}, halben Kantenvektoren {@code halfU}/{@code halfV};
     * die Abstrahlseite ist {@code normalize(halfU x halfV)}.
     */
    public static List<double[]> rectangle(double[] center, double[] halfU, double[] halfV)
    {
        List<double[]> p = new ArrayList<>(4);
        p.add(add(center, add(scale(halfU, -1), scale(halfV, -1))));
        p.add(add(center, add(halfU, scale(halfV, -1))));
        p.add(add(center, add(halfU, halfV)));
        p.add(add(center, add(scale(halfU, -1), halfV)));
        return p;
    }

    /** Scheibe als regelmäßiges n-Eck (umschrieben flächengleich ist nicht nötig: Fehler sinkt mit n²). */
    public static List<double[]> disc(double[] center, double[] axisU, double[] axisV, double radius, int segments)
    {
        double[] u = normalize(axisU), v = normalize(axisV);
        List<double[]> p = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++)
        {
            double a = 2 * Math.PI * i / segments;
            p.add(add(center, add(scale(u, radius * Math.cos(a)), scale(v, radius * Math.sin(a)))));
        }
        return p;
    }

    /**
     * Beleuchtungsstärke im Punkt {@code at} mit Flächennormale {@code normal} durch ein
     * einseitig strahlendes Polygon der Leuchtdichte {@code luminance}.
     */
    public static double illuminance(List<double[]> polygon, double luminance, double[] at, double[] normal)
    {
        double[] n = normalize(normal);
        // Emitter einseitig: Punkt muss vor der Abstrahlseite liegen.
        double[] emitN = polygonNormal(polygon);
        if (dot(emitN, sub(at, polygon.get(0))) <= 0) return 0.0;

        // Relativ zum Empfänger, dann am Horizont (n · x > 0) abschneiden.
        List<double[]> rel = new ArrayList<>(polygon.size());
        for (double[] p : polygon) rel.add(sub(p, at));
        List<double[]> clipped = clipAbovePlane(rel, n);
        if (clipped.size() < 3) return 0.0;

        double sum = 0;
        int k = clipped.size();
        for (int i = 0; i < k; i++)
        {
            double[] a = normalize(clipped.get(i));
            double[] b = normalize(clipped.get((i + 1) % k));
            double c = Math.max(-1.0, Math.min(1.0, dot(a, b)));
            double theta = Math.acos(c);
            double[] g = cross(a, b);
            double gl = length(g);
            if (gl < 1e-15) continue;
            sum += theta * dot(n, g) / gl;
        }
        // Die Eckreihenfolge des Emitters läuft vom Empfänger aus gesehen im Uhrzeigersinn -> Betrag.
        return luminance * 0.5 * Math.abs(sum);
    }

    /** Sutherland-Hodgman gegen die Ebene n · x = 0, behält n · x >= 0. */
    static List<double[]> clipAbovePlane(List<double[]> poly, double[] n)
    {
        List<double[]> out = new ArrayList<>();
        int k = poly.size();
        for (int i = 0; i < k; i++)
        {
            double[] a = poly.get(i), b = poly.get((i + 1) % k);
            double da = dot(n, a), db = dot(n, b);
            if (da >= 0) out.add(a);
            if ((da >= 0) != (db >= 0))
            {
                double t = da / (da - db);
                out.add(add(a, scale(sub(b, a), t)));
            }
        }
        return out;
    }

    static double[] polygonNormal(List<double[]> p)
    {
        double[] n = cross(sub(p.get(1), p.get(0)), sub(p.get(2), p.get(0)));
        return normalize(n);
    }

    /** Fläche eines ebenen, konvexen Polygons in m². */
    public static double area(List<double[]> p)
    {
        double[] s = {0, 0, 0};
        for (int i = 1; i + 1 < p.size(); i++)
        {
            s = add(s, cross(sub(p.get(i), p.get(0)), sub(p.get(i + 1), p.get(0))));
        }
        return 0.5 * length(s);
    }

    // ---- Vektorhilfen
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] scale(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b)
    {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    static double length(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] normalize(double[] a) { double l = length(a); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
