# Abnahme

Referenzpack für alle Render-Tests: **Photon v1.3b**.

Ein Punkt gilt erst als erledigt, wenn ein Nachweis eingetragen ist: Testname mit
Messwert und Toleranz, bei Render-Funktionen zusätzlich der Screenshot-Pfad unter
`test-screenshots/`. Ein Screenshot allein ist kein Nachweis.

Status: `offen` | `in Arbeit` | `erledigt` | `blockiert (Grund)`

**Stand der Cloud-Session (ohne GPU, ohne Zugang zu maven.fabricmc.net, api.modrinth.com,
piston-meta.mojang.com, libraries.minecraft.net):** Gebaut und getestet ist nur das reine
Java-Modul `physics/` (`./gradlew test`, 50 Tests, 0 Fehler). Alles, was Minecraft, Iris,
Photon, Flashback, einen Server oder eine GPU braucht, ist nicht begonnen und steht auf
`offen`, auch wenn der rechnerische Kern schon getestet ist ("Kern" in der Spalte Test).
Kein Punkt ist `erledigt`.

## Grundsatz

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| M1 | Lampen, Bounces, Flags, Floppys als Server-Entities; Pakete mit Rechte- und Bereichsprüfung; Op-Level 2 oder Spielerliste; übersteht Chunk-Entladen und Neustart; Flashback zeichnet auf, Replay-Änderungen als Überschreibung pro Replay | offen | | | |
| M1-FB | Vorab-Test: Flashback zeichnet Gaffer-Entity-Daten vollständig auf und spielt sie ab | offen | | | |
| M1-uhr | Effekt läuft bei eingefrorener Tageszeit weiter; Time-of-Day-Keyframe in Flashback ändert den Effektverlauf nicht; Freeze/Tickrate-Wirkung dokumentiert | offen | | | |
| M1-a | Multiplayer: Client A setzt/ändert Licht, Client B sieht identische Werte | offen | | | |
| M1-b | Multiplayer: Client ohne Rechte wird abgewiesen | offen | | | |
| M1-c | Multiplayer: Lichter überstehen Server-Neustart und Chunk-Entladen | offen | | | |
| M1-d | Flashback-Aufnahme von Client B enthält Lichter und gefahrene Dimmer-Änderung, nichts wird neu gesetzt | offen | | | |

## Physik

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| P1 | Abstandsgesetz 1/d², Fenster nur als Culling-Rand | in Arbeit (Kern fertig, Shader offen) | Kern: `PhotometryExposureTest.abstandsgesetz_*` | E(2m)/E(4m) = 4.000000; mit Rand 4.00169 | offen (Render-Test 4:1) |
| P2 | Lumen/Candela/Lux, Kamera-Belichtung (ISO, Verschlusswinkel, Blende, ND) | in Arbeit (Kern fertig, Shader/Kalibrierung offen; C-Werte gegen ISO 2720 prüfen) | Kern: `PhotometryExposureTest.iso2720_*`, `referenz_lux_*`, `nd_filter_*` | f/1.000000 bei 2.5 lx ISO 100 1 s; E_ref = 240.000 lx | offen |
| P3 | Flächenlichter Rechteck/Scheibe, Schattenweichheit aus echter Größe | in Arbeit (CPU-Referenz fertig, Shader offen) | Kern: `AreaLightMeterTest` | Scheibe -0.008 % gegen exakt; kleine Fläche -0.013 % gegen 1/d² | offen |
| P4 | Richtungslicht mit Schatten | offen | | | |
| P5 | Bounce-Flächen (weiß, silber, gold) als sekundäre Flächenlichter | offen | | | |
| P6 | Flags/Cutter/Floppys als Schattenwerfer, Scrims/Nets in Blenden | offen | | | |

## Licht-Werkzeug

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| W1 | Kelvin 1700 bis 20000 + Grün/Magenta, Gel-Bibliothek | in Arbeit (Farbe fertig; CTO/CTB/Green ohne Datenblatt, daher leer) | Kern: `ColorTest`, `FiltersDimmerTest` | Planck 2856 K: x 0.44754 y 0.40743 (Soll 0.44758/0.40745); D65 0.31272/0.32913; ND 0.3 = 0.99658 Blenden | - |
| W2 | Scheinwerfer-Bibliothek nach Typ, IES-Import | offen | | | |
| W3 | Torblenden, Snoot, Wabe, Diffusion, Gobo | offen | | | |
| W4 | Dimmer mit Kurve, Glühlampe wird wärmer | in Arbeit (vier Kurven fertig; Glühlampen-Exponenten ohne abrufbare Quelle, offen) | Kern: `FiltersDimmerTest.alle_vier_dimmerkurven` | exp 50 % -> 0.1192, log 50 % -> 0.8313, S 50 % -> 0.5 | - |
| W5 | Belichtungsmesser (Lux, Blende; Kontrast log2(Key/Fill) in Blenden, Key und Fill einzeln gemessen) | in Arbeit (Messmodell fertig, Werkzeug im Spiel offen) | Kern: `AreaLightMeterTest.messer_*`, `key_allein_*` | Key 1600 / Fill 400 lx = 2.000 Blenden | offen |
| W6 | False Color und Zebra, nie im Export | offen | | | |
| W7 | Gruppen, Kanäle, Cues mit Überblendzeit | in Arbeit (Logik fertig, Server-Entity und UI offen) | Kern: `EffectsConsoleUndoTest.cue_*`, `gruppen_*` | Cue 3 s: 0.20 / 0.60 / 1.00 | - |
| W8 | Effekt-Generatoren inkl. STORM-FX, deterministisch am Server-Tick bzw. Replay-Tick | in Arbeit (13 Effekte fertig, Anbindung offen) | Kern: `EffectsConsoleUndoTest` | 200 Ticks bitgleich; Stroboskop 5 Hz = 20 Blitze in 4 s | offen |
| W9 | Licht an Entity mit Versatz | offen | | | |
| W10 | Setups (Dreipunkt, Rembrandt, Loop, Butterfly, Split, Clamshell, Gegenlicht) | in Arbeit (Geometrie fertig, Winkel als Festlegung; Anbindung offen) | Kern: `PlotSetupsTest.setups_*` | alle Lampen exakt 2.0 m vom Kopf | - |
| W11 | Lichtplan-Export PNG und SVG | in Arbeit (Export fertig, Daten aus der Welt offen) | Kern: `PlotSetupsTest.lichtplan_svg_und_png` | 480x480 px, 2 Lampen, 129 px Kamerapfeil | `physics/build/test-plot/lichtplan.png` (Testausgabe) |
| W12 | Rückgängig/Wiederherstellen für jede Änderung | in Arbeit (Stapel fertig, Editor-Anbindung offen) | Kern: `EffectsConsoleUndoTest.undo_redo` | - | - |
| W13 | Farbmodi CCT, HSI, RGB, xy, Gel, Source Match; kein Farbsprung beim Wechsel; Dimmerkurven linear/S/exponentiell/logarithmisch; Max/Constant Output; STORM-FX; STORM 700x in der Bibliothek | offen | | | |
| W13-t | Unit-Tests: HSI/RGB/xy hin und zurück bis 1/255, CCT + Grün/Magenta, Moduswechsel ohne Sprung, vier Dimmerkurven, Constant Output | erledigt (nur die Unit-Tests) | `ColorModesTest`, `ColorTest.cct_duv_*`, `FiltersDimmerTest` | RGB 0/255; HSI dH 3e-15; xy 6e-17; Moduswechsel < 1/255 (nicht in CCT darstellbare Farben werden gewarnt); Constant Output Y = 1.000000000000 | - |
| W14 | Editor neu: Lampenliste mit Gruppen, Suche, Sichtbar/Solo/Stumm; Inspector-Reiter Farbe, Dimmer, Form, Schatten, Dunst; Fenster "Technik"; Einheiten und Tooltips; Auswahl per Klick in der Welt; Namen nach Typ | offen | | | |
| W14-ok | Screenshots aller Reiter, Okay durch dich (kein Test misst Übersichtlichkeit) | offen | | | |
| W15 | Gizmo und Hilfslinien in 3D: echte Render-Matrizen, 0,5 m Weltgröße (min. 24 px), Tiefentest, "Durch Wände zeigen", Clipping, Lampenkörper, nie im Export | offen | | | |
| W15-a | Gizmo-Mitte höchstens 2 px neben projizierter Lampe (Bobbing, Sprinten, FOV-Effekte 100 %, Flashback-FOV-Keyframe) | offen | | | |
| W15-b | Gizmo-Radius bei 2 m zu 10 m = 5:1 ±10 %, nie unter 24 px | offen | | | |
| W15-c | Hilfslinien hinter einer Wand: 0 Pixel im Wandbereich | offen | | | |
| W15-d | Kamera im Lichtkegel, 72 Bilder in 5°-Schritten: Hilfslinien in keinem Bild ganz weg | offen | | | |
| W15-e | Export-Screenshot ohne Lampenkörper und Hilfslinien | offen | | | |

## Flashback

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| F1 | Jeder Parameter von Licht, Gruppe, Cue keyframebar | offen | | | |
| F2 | Spur steuert Gruppe oder Cue | offen | | | |
| F3 | Ersetzt durch M1: aufgezeichnete Entities + Überschreibung pro Replay | ersetzt durch M1 | | | |
| F4 | Export sieht aus wie die Vorschau | offen | | | |

## Querschnitt

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| Performance | 20 Lichter, 8 mit Schatten, Photon v1.3b (höchste Stufe), mindestens 60 FPS auf Mittelklasse-GPU (VlProfiler) | offen | | | |
| Ohne Flashback | Gaffer startet und läuft ohne Flashback | offen | | | |
| Sprachen | Jeder Oberflächen-Text in de_de und en_us, jeder Zahlenregler mit Einheit (automatischer Test) | offen | | | |
| Packs | Nur Photon v1.3b; die übrigen sechs Packs folgen in einem eigenen Auftrag | offen | | | |
