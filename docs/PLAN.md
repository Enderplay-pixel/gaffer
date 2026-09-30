# Plan

Grundlage: [ARCHITEKTUR.md](ARCHITEKTUR.md). Abnahme: [ABNAHME.md](ABNAHME.md).

## 0. Blocker in dieser Umgebung (vor dem Start zu klären)

1. **Netzwerk.** Die Egress-Policy dieser Cloud-Session sperrt `maven.fabricmc.net`,
   `api.modrinth.com`, `piston-meta.mojang.com`, `libraries.minecraft.net` und
   `maven.gegy.dev` (403 am Proxy). Erreichbar sind Maven Central, das Gradle-Plugin-Portal
   und `services.gradle.org`. Ohne die gesperrten Hosts baut kein Fabric-Mod (Loom lädt
   Minecraft, Mappings, Fabric API; Iris/Sodium kommen von Modrinth).
   Lösung: Die Hosts in den Umgebungseinstellungen freigeben (Network access, siehe
   https://code.claude.com/docs/en/claude-code-on-the-web). Bis dahin kann ich nur den
   reinen Java-Physikkern (Schritt 1 unten) bauen und testen.
2. **Flashback-Jar.** `libs/flashback.jar` muss von dir kommen oder von Modrinth geladen
   werden dürfen. Die Datei steht in `.gitignore` und wird nie committet.
3. **Keine GPU.** Die Session hat keine Grafikkarte, aber Mesa llvmpipe (Software-GL 4.5)
   und Xvfb. Screenshot-Tests laufen damit, **FPS-Messungen nicht**. Die
   60-FPS-Messung (20 Lichter, 8 mit Schatten, Complementary Ultra) muss auf deiner
   Mittelklasse-GPU laufen. Ich liefere dafür eine Benchmark-Szene und einen Befehl, der
   den VlProfiler-Bericht in eine Datei schreibt.
4. **Shaderpacks** (Complementary usw.) dürfen nicht ins Repo. Die Render-Tests lesen sie
   aus `run/shaderpacks/` (gitignored); herunterladen braucht Modrinth-Zugang.

## 1. Repo-Aufbau

```
gaffer/
  core/        Fork von irl-core (Licht-SSBO, Schatten, Patcher), Pakete org.qualet.irl.* bleiben
  mod/         Fork von irl-editor-fixes-fork = der Gaffer-Mod (mod id "gaffer")
    src/client/java/org/qualet/...       übernommener Code, möglichst wenig geändert
    src/client/java/gaffer/...           neuer Code
    src/client/java/gaffer/flashback/    Flashback-Teil (aus flashback-addon-irl übernommen)
  physics/     reines Java ohne Minecraft: Fotometrie, Farbe, Dimmer, Gels, LTC-Referenz
  shaders/     eine GLSL-Quelle + Pack-Adapter, daraus werden die 7 .irlights generiert
  docs/  test-screenshots/  libs/ (gitignored)
```

- Flashback-Teil im selben Jar, aber als eigene Mixin-Konfiguration mit
  `IMixinConfigPlugin`, das nur bei `FabricLoader.isModLoaded("flashback")` lädt. Ohne
  Flashback wird keine Flashback-Klasse berührt. Flashback nur `modCompileOnly`.
- `physics/` hat keine Minecraft-Abhängigkeit, damit alle Physik- und Farbtests schnell
  mit JUnit laufen und dieselben Klassen im Spiel (Belichtungsmesser, Bounce) rechnen.
- LICENSE von qualet und zqicev bleibt erhalten, dazu `NOTICE.md` mit Credits.
- `breaks: irl-redactor` in `fabric.mod.json` (beide würden SSBO 7 belegen).
- Sprachen: `de_de.json` und `en_us.json`. `ru_ru.json` fällt weg, weil fast alle Texte neu sind.

## 2. Physik und Einheiten (Kern von P1, P2, W1, W4)

**Einheiten.** 1 Block = 1 m. Punkt/Spot: Lichtstärke `I` in cd (Eingabe wahlweise lm:
`I = Φ / Ω` mit dem Raumwinkel des Kegels, bei Punkt `Ω = 4π`). Fläche: Leuchtdichte
`L` in cd/m² (Eingabe lm oder cd senkrecht: `L = I_n / A`). Richtungslicht: Beleuchtungsstärke
`E` in lx. Alle Faktoren (Dimmer, Gel, Scrim, Diffusion, Effekt) werden in Java zu einer
Zahl `I_eff` bzw. `L_eff` verrechnet; der Shader bekommt nur fertige Werte.

**P1 Abstandsgesetz.** `E = I_eff * cos(θ) / d² * w(d/r)`, mit `w(x) = (1 - x⁴)²` nur als
weichem Culling-Rand. `r` wird aus der Leistung berechnet: der Abstand, an dem `E` unter
1/256 der aktuellen Belichtungsreferenz fällt, gedeckelt. So ist `w` innerhalb der
Messabstände praktisch 1 und verfälscht das Verhältnis 2 m zu 4 m nicht.

**P2 Belichtung.** Einfallslicht-Belichtungsgleichung nach ISO 2720:
`N² / t = E * S / C`, `C = 250` (Kalotte), `t = (Verschlusswinkel / 360) / fps`,
ND-Filter mit Dichte `D` verlangt `10^D` mal mehr Licht. Referenz-Beleuchtungsstärke
`E_ref = C * N² * 10^D / (t * S)`. Ein Graukarte-Pixel (Reflexion 18 %) bei `E_ref` soll
genau "Mittelgrau" des Packs ergeben. Der Shader skaliert jedes Licht mit
`k_pack / E_ref`. `k_pack` ist eine **gemessene** Kalibrierkonstante pro Pack (Render-Test:
graue Wand, nur ein Gaffer-Licht, Pack-Himmel/Sonne aus, Pixelwert gegen Sollwert).
Beispiel für den Test: ISO 800, 180°, 24 fps, f/4, kein ND ergibt `E_ref = 250*16/(1/48*800) = 240 lx`.

**W1 Farbe.** Farbtemperatur als Planck-Kurve (CIE 1931 2°-Normspektralwerte, 1-nm-Tabelle
aus CIE 015:2018), Grün/Magenta als `Duv` senkrecht zur Planck-Kurve in CIE 1960 uv.
Tageslicht-Voreinstellungen (Sonne, HMI) nutzen die CIE-D-Formel (CIE 015:2018) und
tragen ihr eigenes `Duv`. Ausgabe linear Rec.709, chromatisch angepasst (Bradford) auf den
Kamera-Weißabgleich. Tests gegen tabellierte Werte (z. B. Planck 2856 K = Normlicht A,
x=0.4476, y=0.4074; D65 x=0.3127, y=0.3290), Toleranz 0.0005.
Gels: CTO/CTB als Mired-Verschiebung, ND als Dichte, Plus/Minus Green als Duv bzw. CC,
jeweils mit Transmission in Blenden. Werte nur aus Hersteller-Datenblättern (Lee/Rosco)
mit Quelle im Code, Oberfläche mit generischen Namen.

**W4 Dimmer.** Kurven Linear, Quadratisch, S-Kurve (Quelle: Konsolen-Handbuch, z. B. ETC).
Glühlampen: Dimmer -> Spannung, Lichtstrom `~ V^3.38`, Farbtemperatur `~ V^0.42`
(Exponenten aus der Lampen-Fachliteratur, Quelle im Code). Test: 50 % Spannung auf 3200 K
ergibt etwa 2390 K und 9.6 % Lichtstrom.

## 3. Was in Java passiert, was im Shader

| Punkt | Java (CPU) | Shader (GPU) |
|---|---|---|
| P1 | Culling-Radius aus Leistung | `I cosθ / d²` mal Fenster |
| P2 | Belichtungsgleichung, `E_ref`, `k_pack` ins UBO | Multiplikation mit `k_pack / E_ref` |
| P3 | Ecken der Fläche, `L_eff`, Schattengröße = echte Größe | Diffus: exaktes Polygon-Integral (Lambert) mit Horizont-Clipping, Scheibe als 16-Eck; Glanz: LTC mit LUT-Textur; Schatten: vorhandenes PCSS mit echter Größe |
| P4 | Richtung, `E` in lx, orthografische Schattenkarte | `E cosθ`, ortho Shadow-Lookup |
| P5 | Einfall auf den Bounce messen (gleiche Formeln + Block-Raycast), als Flächenlicht `L = ρE/π` ausgeben, Bounce wirft auch Schatten | wie P3 |
| P6 | Flags/Cutter/Floppys als Schattenwerfer über `ShadowCasterSource`; Scrim am Scheinwerfer = Faktor `2^-Blenden` | Nets auf Stativ: bis zu N Rechtecke pro Licht, Strahl-Rechteck-Test, Faktor `2^-Blenden` |
| W1, W4, Gels | komplette Rechnung, ergibt RGB und `I_eff` | nichts Neues |
| W2 | Bibliothek, IES-Parser (LM-63) | IES als 2D-Textur-Array, Lookup nach Winkel |
| W3 | Torblenden-Ebenen, Snoot, Wabe, Diffusion (größere Fläche, Verlust in Blenden) | 4 Schnittebenen mit weicher Kante, Wabe als zusätzlicher Kegel |
| W5 | Messung komplett auf der CPU mit denselben `physics`-Formeln + Block-Raycast | nichts |
| W6 | Ein-/Ausschalter, im Export aus | eigener Nachpass über das fertige Bild (Signalwerte wie ein Monitor) |
| W7, W9, W10, W11, W12 | komplett Java | nichts |
| W8 | Effekt = Funktion von (Replay-Tick + Teiltick, Seed) | Volumetrik-Rauschen liest Szenenzeit aus UBO statt `frameTimeCounter` |
| F1 bis F4 | Flashback-Keyframes, Speicherung, Export-Modus | Export-Modus: keine Overlays |

Neue GPU-Daten: ein weiterer Anhang an SSBO 7 (Muster `LightProfilesBuffer`) mit Typ,
Flächenachsen, Torblenden, IES-Layer, Net-Liste; neue Sampler `irl_iesArray` und
`irl_ltcLut` über `IrlSamplers`.

## 4. Reihenfolge

Jeder Schritt endet mit Build, Tests, Eintrag in ABNAHME.md und einem Commit.

1. **Physikkern `physics/`** (P1/P2-Formeln, W1 Farbe, Gels, ND, Scrims, W4 Dimmer,
   Polygon-Integral und LTC-Referenz als Java). Geht sofort, braucht nur Maven Central.
2. **Repo-Aufbau** (Abschnitt 1), Build grün, Test-Gerüst: JUnit, Client-Gametest
   (`fabric-client-gametest-api-v1`, Verfügbarkeit für 1.21.11 prüfen, sonst eigener
   Screenshot-Test über `runClient`), feste Testwelt mit Figur und grauer Wand (18 %).
3. **Neues Lichtmodell + Szenenuhr + Undo (W12).** `Fixture` ersetzt `PlacedLight`
   (Lesen alter Dateien bleibt), Undo-Stapel über Szenen-Snapshots bei jedem
   abgeschlossenen Edit, Szenenuhr aus Replay-Tick (Flashback) oder Client-Tick.
   Undo und Uhr zuerst, weil alle späteren Funktionen darauf aufbauen.
4. **P1 + P2 im Shader**, zuerst nur Complementary Reimagined. Kalibrierung `k_pack`,
   Render-Test 2 m zu 4 m = 4:1 ±10 %, Test "Blende 4".
5. **W1, W4, Gels, Scrims im Editor** (Kelvin, Tint, Dimmer-Kurven, Gel-Bibliothek).
6. **W2 Scheinwerfer + IES, W3 Lichtformer.**
7. **P3 Flächenlichter, P4 Richtungslicht, P5 Bounce, P6 Flags/Nets.**
8. **W5 Belichtungsmesser, W6 False Color/Zebra.**
9. **W7 Pult, W8 Effekte, W9 an Entity, W10 Setups, W11 Lichtplan.**
10. **F1 bis F4 Flashback.**
11. **Übrige sechs Packs** aus der gemeinsamen GLSL-Quelle generieren, pro Pack
    kalibrieren und den 4:1-Test wiederholen.
12. **Performance-Messung** mit VlProfiler (auf deiner GPU), Werte in ABNAHME.md.

## 5. Risiken

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| Netzwerksperre (Abschnitt 0) | ohne Freigabe kein Mod-Build, keine Render-Tests | Hosts freigeben; bis dahin nur `physics/` |
| Packs haben eigene Tonemapper, teils Auto-Belichtung, Complementary mischt Licht nach `sqrt` | "Blende 4" stimmt nur, wenn Gaffer-Licht an der linearen Stelle einfließt und kalibriert ist | pro Pack die lineare Stelle suchen, `k_pack` messen, Auto-Belichtung des Packs im Gaffer-Modus abschalten |
| Sonne, Himmel und Blocklicht des Packs haben keine fotometrische Einheit | gemischte Szenen sind nicht in Lux vergleichbar | dokumentieren; P4 liefert eine eigene Sonne in Lux; für exakte Arbeit Pack-Sonne/Himmel absenkbar |
| 7 Packs mit je eigener Lichtbibliothek | Aufwand x7, Fehler pro Pack | eine GLSL-Quelle, Generator, Render-Test pro Pack |
| Orthografische Schatten (P4) im 2681-Zeilen-`ShadowBaker` | hohes Regressionsrisiko | eigener Kachel-Typ im Spot-Atlas, Render-Tests vorher/nachher |
| Texture-Sampler-Grenzen einzelner Packs | neue Sampler (IES, LTC) passen evtl. nicht | pro Pack prüfen, notfalls IES und LTC in eine Textur packen |
| LTC-LUT-Lizenz (Heitz et al.) | Daten evtl. nicht übernehmbar | Lizenz prüfen; sonst eigene Anpassung der LUT mit eigenem Fit-Code. Diffus braucht keine LUT (exaktes Integral) |
| Messwerte ohne Quelle (Fackel, Kerze, Bounce-Reflexionsgrade) | Regel "keine erfundenen Messwerte" | nur mit Quelle; ohne Quelle kein Zahlenwert, sondern freie Eingabe, in der UI als "ohne Messwert" gekennzeichnet |
| Hersteller-Datenblätter evtl. nicht abrufbar (Netz) | Bibliothek unvollständig | fehlende Einträge in ABNAHME als offen führen |
| Flashback-Interna (Klassen/Methoden) ändern sich zwischen Versionen | Mixins brechen | gegen deine `libs/flashback.jar` bauen, Mixins mit `require = 1`, Gaffer läuft ohne Flashback weiter |
| Export = Vorschau (F4): gedrosselte Schatten-Bakes, Frame-Zähler, Overlays | Export weicht ab | im Export-Modus Bake-Drosselung aus, alle Zeitquellen an den Replay-Tick, Overlays aus; Pixelvergleich Vorschau gegen Export als Test |
| Licht an Entity (W9): Handknochen liegen im Render-State, nicht in der Entity | Fackel folgt der Hand nicht exakt | Versatz relativ zu Körper-Yaw und Augenhöhe; exakte Knochen nur, wenn du es brauchst |
| llvmpipe ist langsam | Render-Tests dauern Minuten | kleine Testwelt, feste Auflösung, nur nötige Frames |

## 6. Offene Fragen an dich

1. **Kamera-Weißabgleich** (Kelvin an der Kamera, z. B. 3200 K oder 5600 K): Ohne ihn ist
   "3200 K" nur relativ zu einem festen Weiß sinnvoll. Ich würde ihn in die globale
   Kamera-Belichtung aufnehmen (Standard 5600 K). Einverstanden?
2. **Belichtungsmesser-Kalotte:** Ich modelliere zwei Modi: flache Scheibe (Kosinus) und
   Kalotte (Näherung `E (1 + cosθ) / 2`). Kontrast Key zu Fill wird wie am Set als
   `log2((Key + Fill) / Fill)` in Blenden angezeigt. Passt das?
3. **Paketname** für neuen Code: Vorschlag `gaffer.*` bzw. Maven-Gruppe
   `io.github.enderplaypixel`. Einverstanden?
