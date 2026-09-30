# Abnahme

Referenzpack für alle Render-Tests: **Photon v1.3b**.

Ein Punkt gilt erst als erledigt, wenn ein Nachweis eingetragen ist: Testname mit
Messwert und Toleranz, bei Render-Funktionen zusätzlich der Screenshot-Pfad unter
`test-screenshots/`. Ein Screenshot allein ist kein Nachweis.

Status: `offen` | `in Arbeit` | `erledigt` | `blockiert (Grund)`

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
| P1 | Abstandsgesetz 1/d², Fenster nur als Culling-Rand | offen | | | |
| P2 | Lumen/Candela/Lux, Kamera-Belichtung (ISO, Verschlusswinkel, Blende, ND) | offen | | | |
| P3 | Flächenlichter Rechteck/Scheibe, Schattenweichheit aus echter Größe | offen | | | |
| P4 | Richtungslicht mit Schatten | offen | | | |
| P5 | Bounce-Flächen (weiß, silber, gold) als sekundäre Flächenlichter | offen | | | |
| P6 | Flags/Cutter/Floppys als Schattenwerfer, Scrims/Nets in Blenden | offen | | | |

## Licht-Werkzeug

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| W1 | Kelvin 1700 bis 20000 + Grün/Magenta, Gel-Bibliothek | offen | | | |
| W2 | Scheinwerfer-Bibliothek nach Typ, IES-Import | offen | | | |
| W3 | Torblenden, Snoot, Wabe, Diffusion, Gobo | offen | | | |
| W4 | Dimmer mit Kurve, Glühlampe wird wärmer | offen | | | |
| W5 | Belichtungsmesser (Lux, Blende; Kontrast log2(Key/Fill) in Blenden, Key und Fill einzeln gemessen) | offen | | | |
| W6 | False Color und Zebra, nie im Export | offen | | | |
| W7 | Gruppen, Kanäle, Cues mit Überblendzeit | offen | | | |
| W8 | Effekt-Generatoren inkl. STORM-FX, deterministisch am Server-Tick bzw. Replay-Tick | offen | | | |
| W9 | Licht an Entity mit Versatz | offen | | | |
| W10 | Setups (Dreipunkt, Rembrandt, Loop, Butterfly, Split, Clamshell, Gegenlicht) | offen | | | |
| W11 | Lichtplan-Export PNG und SVG | offen | | | |
| W12 | Rückgängig/Wiederherstellen für jede Änderung | offen | | | |
| W13 | Farbmodi CCT, HSI, RGB, xy, Gel, Source Match; kein Farbsprung beim Wechsel; Dimmerkurven linear/S/exponentiell/logarithmisch; Max/Constant Output; STORM-FX; STORM 700x in der Bibliothek | offen | | | |
| W13-t | Unit-Tests: HSI/RGB/xy hin und zurück bis 1/255, CCT + Grün/Magenta, Moduswechsel ohne Sprung, vier Dimmerkurven, Constant Output | offen | | | |
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
