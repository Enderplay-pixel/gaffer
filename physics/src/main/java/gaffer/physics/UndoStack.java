package gaffer.physics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rückgängig/Wiederherstellen (W12). Jede abgeschlossene Änderung im Editor wird als Befehl
 * mit Hin- und Rückweg abgelegt. Ein neuer Befehl löscht den Wiederherstellen-Stapel.
 */
public final class UndoStack
{
    /** Eine umkehrbare Änderung. {@code label} erscheint im Menü ("Dimmer Key 1"). */
    public interface Command
    {
        String label();

        void apply();

        void revert();
    }

    private final Deque<Command> undo = new ArrayDeque<>();
    private final Deque<Command> redo = new ArrayDeque<>();
    private final int limit;

    public UndoStack(int limit)
    {
        this.limit = limit;
    }

    /** Führt den Befehl aus und legt ihn ab. */
    public void execute(Command c)
    {
        c.apply();
        undo.push(c);
        redo.clear();
        while (undo.size() > limit) undo.removeLast();
    }

    public boolean undo()
    {
        if (undo.isEmpty()) return false;
        Command c = undo.pop();
        c.revert();
        redo.push(c);
        return true;
    }

    public boolean redo()
    {
        if (redo.isEmpty()) return false;
        Command c = redo.pop();
        c.apply();
        undo.push(c);
        return true;
    }

    public boolean canUndo()
    {
        return !undo.isEmpty();
    }

    public boolean canRedo()
    {
        return !redo.isEmpty();
    }

    public String undoLabel()
    {
        return undo.isEmpty() ? null : undo.peek().label();
    }
}
