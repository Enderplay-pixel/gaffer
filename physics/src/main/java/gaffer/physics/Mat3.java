package gaffer.physics;

/** Kleine 3x3-Matrixhilfe. */
final class Mat3
{
    private Mat3()
    {}

    static double[] mul(double[][] m, double[] v)
    {
        return new double[]{
            m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]
        };
    }

    static double[][] inverse(double[][] m)
    {
        double a = m[0][0], b = m[0][1], c = m[0][2];
        double d = m[1][0], e = m[1][1], f = m[1][2];
        double g = m[2][0], h = m[2][1], i = m[2][2];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-300) throw new ArithmeticException("singulär");
        return new double[][]{
            {A / det, -(b * i - c * h) / det, (b * f - c * e) / det},
            {B / det, (a * i - c * g) / det, -(a * f - c * d) / det},
            {C / det, -(a * h - b * g) / det, (a * e - b * d) / det}
        };
    }
}
