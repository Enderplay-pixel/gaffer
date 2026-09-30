package gaffer.physics;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EffectsConsoleUndoTest
{
    private static final Effects.Params P = new Effects.Params(4.0, 0.8, 50, 1000, 12345);

    // ---- W8 Effekte

    @Test
    void jeder_effekt_ist_deterministisch_und_im_bereich()
    {
        for (Effects.Type t : Effects.Type.values())
        {
            for (long tick = 900; tick < 1400; tick++)
            {
                for (float part : new float[]{0f, 0.37f, 0.99f})
                {
                    Effects.Sample a = Effects.evaluate(t, P, tick, part);
                    Effects.Sample b = Effects.evaluate(t, P, tick, part);
                    assertEquals(a, b, t + " bei " + tick);   // bitgleich
                    assertTrue(a.level() >= 0 && a.level() <= 1, t + " Pegel " + a.level());
                    assertTrue(Math.abs(a.miredShift()) <= 50 + 1e-9, t + " Mired");
                }
            }
        }
    }

    @Test
    void flackern_zwei_durchlaeufe_gleicher_tick_identisch()
    {
        // Zwei getrennte Durchläufe über dieselben Ticks in verschiedener Reihenfolge
        double[] vorwaerts = new double[200], rueckwaerts = new double[200];
        for (int i = 0; i < 200; i++) vorwaerts[i] = Effects.evaluate(Effects.Type.CANDLE, P, 5000 + i, 0.5f).level();
        for (int i = 199; i >= 0; i--) rueckwaerts[i] = Effects.evaluate(Effects.Type.CANDLE, P, 5000 + i, 0.5f).level();
        assertArrayEquals(vorwaerts, rueckwaerts, 0.0);
        double min = 1, max = 0;
        for (double v : vorwaerts) { min = Math.min(min, v); max = Math.max(max, v); }
        System.out.printf("MESS W8 Kerze 200 Ticks: Pegel %.3f..%.3f, zwei Durchläufe bitgleich%n", min, max);
        assertTrue(max - min > 0.1, "flackert sichtbar");
    }

    @Test
    void stroboskop_frequenz()
    {
        // 5 Hz Stroboskop, 20 Messpunkte pro Tick über 4 s: Anzahl Einschaltflanken
        var p = new Effects.Params(5.0, 1.0, 0, 0, 1);
        int flanken = 0;
        boolean an = false;
        for (int i = 0; i < 80 * 20; i++)
        {
            long tick = i / 20;
            float part = (i % 20) / 20f;
            boolean now = Effects.evaluate(Effects.Type.STROBE, p, tick, part).level() > 0.5;
            if (now && !an) flanken++;
            an = now;
        }
        System.out.printf("MESS W8 Stroboskop 5 Hz über 4 s: %d Blitze (Soll 20)%n", flanken);
        assertEquals(20, flanken);
    }

    @Test
    void einmalige_effekte_haengen_am_ausloesetick()
    {
        var p = new Effects.Params(1.0, 1.0, 0, 200, 9);
        assertEquals(0.0, Effects.evaluate(Effects.Type.CANNON, p, 199, 0f).level(), 1e-12, "vor dem Schuss dunkel");
        assertEquals(1.0, Effects.evaluate(Effects.Type.CANNON, p, 200, 0f).level(), 1e-12, "Mündungsblitz");
        assertTrue(Effects.evaluate(Effects.Type.CANNON, p, 240, 0f).level() < 0.01, "nach 2 s abgeklungen");
        assertEquals(1.0, Effects.evaluate(Effects.Type.NEON_START, p, 200 + 25, 0f).level(), 1e-12, "Neon nach 1/f s voll an");
    }

    @Test
    void effekt_haengt_nur_am_welt_tick()
    {
        // Die API nimmt nur Tick und Teiltick; eine eingefrorene Tageszeit kann den Effekt daher nicht anhalten.
        double a = Effects.evaluate(Effects.Type.FIRE, P, 24000, 0f).level();
        double b = Effects.evaluate(Effects.Type.FIRE, P, 24001, 0f).level();
        assertNotEquals(a, b, "läuft weiter, auch wenn die Tageszeit (tick % 24000) gleich bliebe");
    }

    // ---- W7 Pult

    @Test
    void cue_ueberblendung_am_tick()
    {
        Console c = new Console();
        c.setChannel("1", 0.2);
        c.setChannel("2", 1.0);
        Console.Cue cue = new Console.Cue("Nacht", Map.of("1", 1.0, "2", 0.0), 3.0);
        c.go(cue, 100);
        assertEquals(0.2, c.channelLevel("1", 100, 0f), 1e-12);
        assertEquals(0.6, c.channelLevel("1", 130, 0f), 1e-12, "Hälfte nach 1.5 s");
        assertEquals(0.5, c.channelLevel("2", 130, 0f), 1e-12);
        assertEquals(1.0, c.channelLevel("1", 160, 0f), 1e-12, "fertig nach 3 s");
        assertEquals(1.0, c.channelLevel("1", 999, 0f), 1e-12);
        System.out.printf("MESS W7 Cue 3 s: Kanal 1 bei 0/1.5/3 s = %.2f/%.2f/%.2f%n",
            c.channelLevel("1", 100, 0f), c.channelLevel("1", 130, 0f), c.channelLevel("1", 160, 0f));
    }

    @Test
    void gruppen_master_multipliziert()
    {
        Console c = new Console();
        c.setChannel("key", 0.8);
        c.putGroup(new Console.Group("Front", Set.of("key", "fill"), 0.5));
        c.putGroup(new Console.Group("Alle", Set.of("key"), 0.5));
        assertEquals(0.2, c.output("key", 0, 0f), 1e-12);
    }

    // ---- W12 Undo

    @Test
    void undo_redo()
    {
        double[] dimmer = {0.5};
        UndoStack s = new UndoStack(100);
        s.execute(set(dimmer, 0.8));
        s.execute(set(dimmer, 0.3));
        assertEquals(0.3, dimmer[0]);
        assertTrue(s.undo());
        assertEquals(0.8, dimmer[0]);
        assertTrue(s.undo());
        assertEquals(0.5, dimmer[0]);
        assertFalse(s.undo());
        assertTrue(s.redo());
        assertEquals(0.8, dimmer[0]);
        s.execute(set(dimmer, 0.1));
        assertFalse(s.canRedo(), "neue Änderung löscht Wiederherstellen");
    }

    private static UndoStack.Command set(double[] target, double value)
    {
        double old = target[0];
        return new UndoStack.Command()
        {
            public String label() { return "Dimmer"; }
            public void apply() { target[0] = value; }
            public void revert() { target[0] = old; }
        };
    }
}
