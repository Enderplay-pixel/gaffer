# Plan

Grundlage: [ARCHITEKTUR.md](ARCHITEKTUR.md). Abnahme: [ABNAHME.md](ABNAHME.md).

## 0. Rahmen

- **Referenzpack: Photon v1.3b.** Vorerst wird nur Photon gepatcht (der vorhandene Patch
  `photon.irlights` ist gegen v1.3b geschrieben). Die gemeinsame GLSL-Quelle bleibt; die
  anderen sechs Packs folgen in einem eigenen Auftrag.
- **Arbeitsplatz:** lokal auf deinem PC mit NVIDIA-GPU. Damit entfallen die Netz- und
  GPU-Blocker der Cloud-Session. Render-Tests und FPS-Messung laufen dort.
- **Flashback:** `libs/flashback.jar` (Flashback 0.39.10 für 1.21.11) legst du ab. Die Datei
  ist gitignored, wird nur `modCompileOnly` eingebunden und für Tests lokal zur Laufzeit
  geladen, nie ins Jar oder Repo übernommen.
- **Shaderpack:** Photon v1.3b liegt für Tests in `run/shaderpacks/` (gitignored).
- **Paket** `gaffer.*`, **Maven-Gruppe** `io.github.enderplaypixel`.
- **Status:** Plan freigegeben.

## 1. Repo-Aufbau

```
gaffer/
  core/        Fork von irl-core (Licht-SSBO, Schatten, Patcher), Pakete org.qualet.irl.* bleiben
  mod/         der Gaffer-Mod (mod id "gaffer"), läuft auf Server und Client
    src/main/java/gaffer/...             Entities, Netzwerk, Rechte, Serverlogik (W7/W8/W9)
    src/client/java/org/qualet/...       übernommener Editor/Render-Code
    src/client/java/gaffer/...           neuer Client-Code (Editor W14, Gizmo W15, Messer ...)
    src/client/java/gaffer/flashback/    Flashback-Teil, eigene Mixin-Konfiguration
  physics/     reines Java ohne Minecraft: Fotometrie, Farbe, Farbmodi, Dimmer, Gels
  shaders/     eine GLSL-Quelle + Pack-Adapter; daraus wird photon.irlights generiert
  docs/  test-screenshots/  libs/ (gitignored)  run/ (gitignored)
```

- Flashback-Mixins nur über ein `IMixinConfigPlugin`, das bei
  `FabricLoader.isModLoaded("flashback")` lädt. Ohne Flashback wird keine Flashback-Klasse berührt.
- LICENSE von qualet und zqicev bleibt erhalten, dazu `NOTICE.md` mit Credits.
- `breaks: irl-redactor` (beide würden SSBO 7 belegen).
- Sprachen: `de_de.json`, `en_us.json`; `ru_ru.json` bleibt, ist aber nicht Standard.

## 2. M1: Multiplayer, Lampen als Entities (Grundsatz)

Der clientseitige Weg von IR Lights (`LightScene` + `LightStore`-JSON pro Client, kein
Netzwerkpaket) wird komplett ersetzt.

**Entities.** Drei Typen:
`gaffer:fixture` (Lampe), `gaffer:bounce` (Bounce-Fläche), `gaffer:flag` (Flag, Cutter,
Floppy, Net; Art als Parameter). Eigenschaften: keine Schwerkraft, keine Kollision, keine KI,
unverwundbar, nicht schiebbar, `canHit() = false` (kein Schlagen, kein Fadenkreuz-Ziel),
kein Ticken außer für W8/W9. Körper nur im Editor-Modus sichtbar (Client-Entscheidung
beim Rendern). Speicherung über die normalen Entity-Daten der Welt: überstehen
Chunk-Entladen und Neustart ohne eigenes Dateiformat.

**Synchronisation.** Alle Parameter als synchronisierte Entity-Daten (`DataTracker`). Da es
viele Parameter sind, bekommt jede Parametergruppe (Farbe, Dimmer, Form, Schatten, Dunst,
Effekt, Verknüpfung) einen eigenen `TrackedData`-Eintrag mit eigenem registrierten
Handler. Vanilla sendet nur geänderte Einträge, nur an Clients, die die Entity verfolgen.
Tracking-Reichweite groß genug, dass Licht von außerhalb des Bildes ankommt (Wert wird
gemessen und dokumentiert).

**Pult-Zustand.** Gruppen, Kanäle und Cues (W7) liegen in einer unsichtbaren
`gaffer:console`-Entity pro Dimension. So läuft alles über denselben Aufzeichnungsweg
wie die Lampen, und der Flashback-Test deckt es mit ab.

**Änderungen.** Editor schickt ein Paket `gaffer:edit` (Entity-UUID, geänderte Parameter).
Server prüft Rechte, klemmt jeden Wert auf seinen Wertebereich (Tabelle in `physics/`,
dieselbe wie die UI), schreibt in die Entity-Daten, Vanilla verteilt. Konflikte: letzte
Änderung gewinnt. Neue Objekte: Paket `gaffer:spawn`, Löschen: `gaffer:remove`.

**Rechte.** Standard Operator-Level 2. Serverkonfiguration `config/gaffer-server.json`
mit optionaler Spielerliste (UUIDs), die das Op-Level ersetzt. Ohne Rechte: Lichter
sichtbar, Editor schreibgeschützt, Pakete werden abgewiesen und protokolliert.

**Beitritt.** Gaffer ist Pflicht auf beiden Seiten; ein Client ohne Gaffer scheitert an
der Registry-Synchronisation (unbekannte Entity-Typen). Das ist gewollt.

**Uhr.** Effekte (W8) und Cues (W7) sind Funktionen des fortlaufenden Tick-Zählers der
Welt (`world.getTime()` + Teiltick), **niemals der Tageszeit** (`getTimeOfDay()`). Die
Tageszeit wird beim Dreh oft mit `doDaylightCycle false` eingefroren und in Flashback per
Time-of-Day-Keyframe verstellt; beides darf Effekte weder anhalten noch springen lassen.
Live läuft der Zähler am Server-Tick, im Replay am Replay-Tick.

**W9.** Lampe speichert UUID der Ziel-Entity und Versatz. Der Server setzt die Position
jeden Tick nach (Bewegungspakete wie bei jeder Entity); der Client rendert die Lampe
relativ zur interpolierten Position des Ziels, damit sie nicht einen Tick hinterherhängt.

**Flashback.**
1. **Zuerst ein Test**: Aufnahme mit Flashback, Lampe setzen, Dimmer während der Aufnahme
   fahren, Replay öffnen, Entity-Daten pro Tick gegen die aufgezeichneten Werte vergleichen
   (auch Pult-Entity und `getTime()`). Zusätzlich im selben Test:
   - Effekt läuft bei eingefrorener Tageszeit (`doDaylightCycle false`) weiter,
   - ein Time-of-Day-Keyframe in Flashback verändert den Effektverlauf nicht,
   - Wirkung von Flashbacks Freeze- und Tickrate-Keyframes auf die Effekte wird gemessen.
   Ergebnis in ARCHITEKTUR.md, **bevor** weitergebaut wird.
2. Im Replay sind die aufgezeichneten Entities die Grundlage. Flashback-Keyframes (F1) und
   Änderungen im Replay-Editor liegen als **Überschreibung** darüber (Schlüssel:
   Entity-UUID + Parameter) und werden pro Replay gespeichert (Datei neben dem Replay,
   Schlüssel: Replay-UUID aus den Flashback-Metadaten). Das ersetzt F3. Am Server ändert
   sich nichts.
3. Fällt der Test negativ aus (z. B. eigene `TrackedData`-Handler werden nicht aufgezeichnet),
   entscheiden wir mit dem Messergebnis neu.

**Kamera-Belichtung und Weißabgleich** sind Kamera-Einstellungen, also pro Client (bzw.
pro Replay in der Überschreibung), nicht Teil der Server-Szene.

## 3. W15: Gizmos und Hilfslinien in 3D

Befunde (a) bis (d) sind im Code bestätigt (ARCHITEKTUR.md, Abschnitt 8).

1. **Zuerst Test für (d):** Gizmo-Mitte gegen die mit der echten Render-Matrix projizierte
   Lampenposition, mit View Bobbing, Sprinten, FOV-Effekten 100 % und Flashback-FOV-Keyframe.
   Messwert vor dem Fix festhalten.
2. View- und Projektionsmatrix per Mixin im Render-Pfad abgreifen (die Matrizen, mit denen
   der Frame tatsächlich gerendert wird) statt aus `getFov()`/Pitch/Yaw nachbauen.
3. Hilfslinien und Lampenkörper als echte 3D-Geometrie im Welt-Render mit Tiefentest statt
   ImGui-Background-Drawlist. Clipping an Near-Plane und Bildrand statt Verwerfen.
   "Durch Wände zeigen": zweiter Durchgang ohne Tiefentest, gestrichelt, höchstens 25 %
   Deckkraft, Standard aus.
4. Gizmo: Größe in der Welt 0,5 m Radius, Pixelgröße aus der echten Projektion; unter
   24 px bleibt es bei 24 px. Mitte exakt auf der Lampenposition. Die Interaktion bleibt
   bei ImGuizmo, gefüttert mit den abgegriffenen Matrizen und einer pro Frame berechneten
   Clip-Space-Größe; der sichtbare Teil wird zusätzlich tiefengetestet in 3D gezeichnet.
   Ob ImGuizmo dafür reicht, zeigt der Test (b); sonst eigenes Gizmo.
5. Auswahl durch Anklicken in der Welt (Strahl aus der echten Matrix gegen die
   Körper der Gaffer-Entities), gebraucht von W14.
6. Im Flashback-Export werden Körper, Hilfslinien und Gizmo nicht gezeichnet.

## 4. Physik und Einheiten

**Einheiten.** 1 Block = 1 m. Punkt/Spot: Lichtstärke `I` in cd (Eingabe wahlweise lm,
`I = Φ / Ω`). Fläche: Leuchtdichte `L` in cd/m². Richtungslicht: Beleuchtungsstärke `E`
in lx. Dimmer, Gel, Scrim, Diffusion und Effekt werden in Java zu `I_eff` bzw. `L_eff`
verrechnet; der Shader bekommt fertige Werte.

**P1.** `E = I_eff * cos(θ) / d² * w(d/r)`, `w(x) = (1 - x⁴)²` nur als weicher
Culling-Rand; `r` aus der Leistung (Abstand, an dem `E` unter 1/256 von `E_ref` fällt).

**P2.** ISO 2720: `N² / t = E * S / C`, `C = 250`, `t = (Verschlusswinkel / 360) / fps`,
ND-Dichte `D`: `E_ref = C * N² * 10^D / (t * S)`. Graukarte 18 % bei `E_ref` ergibt
Mittelgrau. Shader skaliert mit `k_pack / E_ref`, `k_pack` gemessen.
**Linearität geprüft (Code):** Die Einspeisestelle in `d4_deferred_shading.fsh` liegt vor
Nebel, Belichtung und Tonemapping und ist linear; Auto-Belichtung in Photon standardmäßig
aus (ARCHITEKTUR.md 9.1). Vor der Kalibrierung noch per Render-Messung bestätigen.
Beispiel: ISO 800, 180°, 24 fps, f/4, ohne ND ergibt `E_ref = 240 lx`.

**W1 Farbe.** Planck-Kurve mit CIE-1931-2°-Normspektralwerten (CIE 015:2018), Grün/Magenta als
`Duv` senkrecht zur Planck-Kurve (CIE 1960 uv), CIE-D für Tageslicht-Vorlagen. Ausgabe
linear Rec.709, Bradford-Anpassung an den **Kamera-Weißabgleich (Kelvin + Tint, Standard
5600 K)**. Gels: CTO/CTB als Mired, ND als Dichte, Plus/Minus Green als Duv/CC, mit
Transmission in Blenden, Werte aus Lee/Rosco-Datenblättern mit Quelle, generische Namen.

**W13 Farbmodi** (STORM-700x-Vorbild), pro Licht umschaltbar, alle in `physics/`:
- CCT 2500 bis 10000 K, Grün/Magenta -100 bis +100 % (Abbildung % auf Duv wird dokumentiert
  und mit Quelle belegt, sonst als Festlegung gekennzeichnet).
- HSI: Farbton, Sättigung, Intensität, Weißpunkt in Kelvin. Sättigung interpoliert im
  CIE-xy zwischen Weißpunkt und dem Rand des Render-Farbraums (Rec.709) in Richtung des
  Farbtons. Festlegung, weil der Emitter-Farbraum der STORM nicht veröffentlicht ist.
- RGB 0-255 und Hex (sRGB-kodiert, intern linear).
- xy: außerhalb Rec.709 sichtbar markiert und auf den Rand abgeschnitten.
- Gel (Bibliothek aus W1), Source Match (Natriumdampf, Quecksilberdampf, Leuchtstoffröhre,
  Glühlampe, Kerze, Tageslicht bewölkt, Mondlicht-Look) mit CIE-Normlichtarten
  (A, D-Reihe, F-Reihe, HP-Reihe aus CIE 015:2018) bzw. Literatur mit Quelle.
- Modus-Wechsel: interne Farbe ist immer CIE-xy + Leuchtstärke; jeder Modus rechnet daraus
  seine Regler zurück, kein Sprung.
- Ausgabe "Max Output" oder "Constant Output". Constant hält `I` über alle Kelvin gleich.
  Max braucht Herstellerwerte pro Kelvin; wo keine vorliegen, ist nur Constant wählbar.

**W4 Dimmer.** Genau vier Kurven: linear, S-Kurve, exponentiell, logarithmisch (Formeln im
Code festgelegt und getestet). Glühlampen: Dimmer -> Spannung, Lichtstrom `~ V^3.38`,
Farbtemperatur `~ V^0.42` (Quelle im Code).

**W5 Kontrast.** Hauptanzeige `log2(E_key / E_fill)` in Blenden, gemessen mit Key allein und
Fill allein (Solo/Stumm aus W14); `(Key + Fill) : Fill` klein daneben. Messmodi: flache
Scheibe (Kosinus) und Kalotte (`E (1 + cosθ) / 2`).

**W2 STORM 700x** mit 35°-Reflektor: 16.730 lx auf 3 m bei 5600 K, Quelle laut deiner Angabe
Newsshooter-Review vom 02.04.2026 bzw. Aputure-Datenblatt. Ich prüfe die Quelle beim Umsetzen
und zitiere sie im Code; ergibt `I = 16730 * 9 = 150.570 cd`.

**W8 FX** um die STORM-Liste ergänzt: Paparazzi, Feuerwerk, defekte Glühbirne, Kanone,
Blitz, TV, Pulsieren, Stroboskop, Explosion, Feuer, dazu Kerze, Blaulicht, Neonröhre beim
Einschalten. Alle als Funktion von (Weltzeit + Teiltick, Seed, Parameter).

**W16 Umgebungslicht.** Drei globale Regler (Himmel, Sonne/Mond, Vanilla-Blocklicht), je
0 bis 100 %, angezeigt in Blenden (`log2(Anteil)`, 0 % = "aus"). Pro Client bzw. pro
Replay-Überschreibung, keyframebar wie jeder andere Parameter (F1). Photon trennt alle drei
Anteile (ARCHITEKTUR.md 9.2); die Faktoren kommen über den Globals-UBO in
`get_diffuse_lighting` und die Glanzanteile. Höhlen-Mindestlicht und Himmelsspiegelung
hängen am Himmelsregler, Sonnenglanz am Sonne/Mond-Regler.

**W17 Praktikabels.** Eine Lampe kann an einen leuchtenden Vanilla-Block gebunden werden
(Laterne, Kerze, Fackel, Redstone-Lampe). Die Bindung liegt in der Lampen-Entity (M1). Der
Server setzt die Leuchtkraft dieser Position in seiner Lichtengine auf 0 (Mixin in die
Blocklicht-Quelle), die Clients bekommen die Lichtdaten wie üblich; der Photon-Patch
überspringt die Position zusätzlich in der LPV-Voxelisierung (Positionsliste im SSBO).
Wird die Bindung gelöst oder die Lampe gelöscht, wird die Position neu beleuchtet.

## 5. Was in Java passiert, was im Shader

| Punkt | Java (CPU) | Shader (GPU) |
|---|---|---|
| M1 | Entities, Pakete, Rechte, Speicherung, Replay-Überschreibung | nichts |
| W15 | Matrizen abgreifen, 3D-Linien/Körper, Klick-Auswahl | Linien/Körper mit Tiefentest (Vanilla-Render) |
| P1 | Culling-Radius aus Leistung | `I cosθ / d²` mal Fenster |
| P2 | Belichtungsgleichung, `E_ref`, `k_pack` ins UBO | Multiplikation mit `k_pack / E_ref` |
| P3 | Ecken, `L_eff`, Schattengröße = echte Größe | Diffus: exaktes Polygon-Integral mit Horizont-Clipping, Scheibe als 16-Eck; Glanz: LTC; Schatten: PCSS mit echter Größe |
| P4 | Richtung, `E`, ortho Schattenkarte | `E cosθ`, ortho Lookup |
| P5 | Einfall auf den Bounce (Formeln + Block-Raycast), Ausgabe als Fläche `L = ρE/π`, wirft Schatten | wie P3 |
| P6 | Flags/Cutter/Floppys als Schattenwerfer (`ShadowCasterSource`), Scrim am Kopf `2^-Blenden` | Nets: Strahl-Rechteck-Test pro Licht, `2^-Blenden` |
| W1, W4, W13, Gels | komplette Farb- und Helligkeitsrechnung | nichts |
| W2, W3 | Bibliothek, IES-Parser, Torblenden-Ebenen, Diffusion | IES-Textur-Array, 4 Schnittebenen, Wabe |
| W5, W7, W9 bis W12, W14 | komplett Java | nichts |
| W6 | Schalter, im Export aus | Nachpass über das fertige Bild |
| W16 | Regler, Keyframes, Werte in den UBO | Faktoren auf Himmel-, Sonne/Mond- und Blocklicht-Summanden in Photon |
| W17 | Bindung, Leuchtkraft 0 in der Server-Lichtengine | Position in der LPV-Voxelisierung überspringen |
| W8 | Effektwerte aus Weltzeit | Volumetrik-Rauschen aus UBO-Zeit statt `frameTimeCounter` |
| F1, F2, F4 | Keyframes als Überschreibung, Export-Modus | im Export keine Overlays |

## 6. Reihenfolge

Jeder Schritt endet mit Build, Tests, Eintrag in ABNAHME.md und einem Commit.

1. **Repo-Aufbau und Test-Gerüst**: Build grün, JUnit, Client-Gametests
   (`fabric-client-gametest-api-v1`, Verfügbarkeit für 1.21.11 prüfen), feste Testwelt mit
   Figur und grauer Wand (18 %), Multiplayer-Testaufbau (dedizierter Server + zwei
   Client-Prozesse, gesteuert von einem Gradle-Task), Sprachtest (jeder Schlüssel in
   de_de und en_us, jeder Zahlenregler mit Einheit).
2. **M1**, beginnend mit dem Flashback-Aufzeichnungstest.
3. **W15**, beginnend mit dem Matrix-Test (d).
4. **Physikkern `physics/`**: P1/P2-Formeln, W1, W13, W4, Gels, ND, Scrims mit Unit-Tests.
5. **P1 + P2 im Photon-Shader**: Linearitätsprüfung, Kalibrierung, 4:1-Test, Blende-4-Test.
6. **W12 Undo** (Editor-Befehle als Pakete, lokaler Undo-Stapel pro Spieler).
7. **W14 Editor neu** mit den Reitern Farbe, Dimmer, Form, Schatten, Dunst und Fenster
   "Technik". **Danach Pause: Screenshots aller Reiter an dich, warten auf dein Okay.**
8. **W2 + IES, W3 Lichtformer.**
9. **P3, P4, P5, P6.**
10. **W5 Messer, W6 False Color/Zebra.**
11. **W7 Pult, W8 FX, W9 an Entity, W10 Setups, W11 Lichtplan, W16 Umgebungslicht,
    W17 Praktikabels.**
12. **F1, F2, F4.**
13. **Performance-Messung** mit VlProfiler auf deiner GPU, Werte in ABNAHME.md.

## 7. Risiken

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| Flashback zeichnet eigene `TrackedData`-Handler oder die Pult-Entity nicht vollständig auf | M1-Konzept für Replays trägt nicht | Test zuerst (Abschnitt 2), Entscheidung mit Messwert |
| Zwei Clients + dedizierter Server im automatischen Test | Fabric-Gametests starten nur einen Client | eigener Gradle-Orchestrator mit getrennten Prozessen und Dateiaustausch |
| Viele Parameter pro Entity | große Datenpakete bei jeder Änderung | Gruppen-Einträge, nur geänderte Gruppe wird gesendet; Paketgröße messen |
| Geprüfter Photon-Commit (`v1.3-maintenance` `90451cc`) ist nicht exakt v1.3b | Zeilenangaben/Anker weichen ab | lokal gegen das Pack-Zip v1.3b abgleichen |
| Nebel, Wolken oder Volumetrik bringen eigenes Himmelslicht ein | W16 "Himmel 0 %" nicht ganz dunkel | Gametest W16-a misst es; ggf. zusätzliche Faktoren |
| W17: Lichtengine-Mixin muss auf Server und integriertem Server greifen, Lichtupdates bei Bindung auslösen | Doppellicht oder Lichtreste | Gametest W17-b, Lichtupdate der Position beim Binden/Lösen |
| Sonne/Himmel/Blocklicht des Packs haben keine Einheit | Mischszenen nicht in Lux vergleichbar | dokumentieren; P4 liefert eigene Sonne in Lux |
| Orthografische Schatten (P4) im 2681-Zeilen-`ShadowBaker` | Regressionen | eigener Kacheltyp im Spot-Atlas, Render-Tests vorher/nachher |
| ImGuizmo zeichnet ohne Tiefe | W15 verlangt Tiefentest | Interaktion bei ImGuizmo, Darstellung in 3D; notfalls eigenes Gizmo |
| LTC-LUT-Lizenz | Daten evtl. nicht übernehmbar | Lizenz prüfen, sonst eigener Fit; Diffus braucht keine LUT |
| Messwerte ohne Quelle (Fackel, Kerze, Bounce-Reflexionsgrade, STORM-Emitterfarbraum, Mondlicht-Look) | Regel "keine erfundenen Messwerte" | nur mit Quelle; sonst freie Eingabe, in der UI als "ohne Messwert" bzw. "Festlegung" gekennzeichnet |
| Flashback-Interna ändern sich | Mixins brechen | gegen 0.39.10 bauen, `require = 1`, Gaffer läuft ohne Flashback weiter |
| Export weicht von Vorschau ab (gedrosselte Bakes, Frame-Zähler, Overlays) | F4 | Export-Modus: Bake-Drosselung aus, alle Zeitquellen an Weltzeit, Overlays aus; Pixelvergleich |

## 8. Entscheidungen

1. Maven-Gruppe `io.github.enderplaypixel`, Paket `gaffer.*`.
2. Solo, Stumm und Sichtbar (W14) wirken nur lokal auf dem eigenen Client (Vorschau und
   Belichtungsmesser), gehen nicht an Server oder andere Spieler und wirken nie im Export.
3. Kamera-Weißabgleich Kelvin + Tint, Standard 5600 K, pro Client.
4. Kontrast Key zu Fill: `log2(Key/Fill)` in Blenden, Key allein und Fill allein gemessen.
5. Effekt- und Cue-Uhr: `world.getTime()` + Teiltick, nie `getTimeOfDay()`.

## 9. Stand nach der Cloud-Session (Übergabe an den lokalen PC)

Fertig und getestet (`./gradlew test`, Modul `physics/`): Fotometrie, Belichtung, CIE-Farbe,
Farbmodi, Dimmerkurven, Filter, Flächenlicht-Referenz, Messer, Effekte, Pult, Undo,
Lichtplan, Setup-Geometrie.

Vor der Abnahme auf dem PC mit Netzzugang zu erledigen:
1. Quellen eintragen, die hier nicht abrufbar waren (Egress-Sperre): Lee/Rosco-Datenblätter
   für CTO/CTB/Plus/Minus Green (Werte in `Filters.LIBRARY` sind leer), ISO 2720 bzw.
   Sekonic-Datenblatt für `Exposure.C_FLAT`/`C_DOME`, Glühlampen-Exponenten (W4), Source
   Match Quecksilber/Kerze/Mondlicht, STORM-700x-Wert (Newsshooter/Aputure).
2. Ab Schritt 1 in Abschnitt 6 (Repo-Aufbau mit `core/` und `mod/`) weiterarbeiten.
