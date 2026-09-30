# Abnahme

Ein Punkt gilt erst als erledigt, wenn ein Nachweis eingetragen ist: Testname mit
Messwert und Toleranz, bei Render-Funktionen zusätzlich der Screenshot-Pfad unter
`test-screenshots/`. Ein Screenshot allein ist kein Nachweis.

Status: `offen` | `in Arbeit` | `erledigt` | `blockiert (Grund)`

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
| W5 | Belichtungsmesser (Lux, Blende, Kontrast Key zu Fill) | offen | | | |
| W6 | False Color und Zebra, nie im Export | offen | | | |
| W7 | Gruppen, Kanäle, Cues mit Überblendzeit | offen | | | |
| W8 | Effekt-Generatoren, deterministisch am Replay-Tick | offen | | | |
| W9 | Licht an Entity mit Versatz | offen | | | |
| W10 | Setups (Dreipunkt, Rembrandt, Loop, Butterfly, Split, Clamshell, Gegenlicht) | offen | | | |
| W11 | Lichtplan-Export PNG und SVG | offen | | | |
| W12 | Rückgängig/Wiederherstellen für jede Änderung | offen | | | |

## Flashback

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| F1 | Jeder Parameter von Licht, Gruppe, Cue keyframebar | offen | | | |
| F2 | Spur steuert Gruppe oder Cue | offen | | | |
| F3 | Lichtszene pro Replay gespeichert und geladen | offen | | | |
| F4 | Export sieht aus wie die Vorschau | offen | | | |

## Querschnitt

| Punkt | Inhalt | Status | Test | Messwert | Screenshot |
|---|---|---|---|---|---|
| Performance | 20 Lichter, 8 mit Schatten, Complementary Ultra, mindestens 60 FPS auf Mittelklasse-GPU (VlProfiler) | offen | | | |
| Ohne Flashback | Gaffer startet und läuft ohne Flashback | offen | | | |
| Sprachen | Oberfläche Deutsch und Englisch | offen | | | |
| Packs | Photon, Complementary Reimagined, BSL, Bliss, Solas, IterationRP, Rethinking Voxels | offen | | | |
