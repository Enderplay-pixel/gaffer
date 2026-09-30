# Architektur: Wie Licht vom Editor in den Shader fließt

Stand: Analyse der Ausgangsprojekte vor jeder Änderung.

| Repo | Branch | Commit | Lizenz |
|---|---|---|---|
| quaIett/irl-core | `1.21.11` (einen Branch `port/1.21.11` gibt es dort nicht) | `7c42992` | MIT, © 2026 qualet |
| quaIett/irl-editor | `port/1.21.11` | `cff57bb` | MIT, © 2026 qualet |
| zqicev/irl-editor-fixes-fork | `port/1.21.11-flashback` | `57773dd` | MIT, © 2026 qualet (Fork) |
| zqicev/flashback-addon-irl | `port/1.21.11` | `505b3ab` | MIT, © 2026 zqicev |

`irl-editor-fixes-fork` ist `irl-editor` plus genau ein Commit: die Klasse
`api/IrlFlashbackBridge` und das Feld `PlacedLight.uid`. Alles andere ist identisch.

Toolchain der Vorlage: Minecraft 1.21.11, Yarn `1.21.11+build.6`, Fabric Loader 0.19.3,
Fabric API `0.141.4+1.21.11`, Loom 1.15.5, Java 21, Iris `1.10.7+1.21.11`,
Sodium `mc1.21.11-0.8.7`, imgui-java 1.89.0 (mit ImGuizmo).

---

## 1. Überblick

```
 ImGui-Editor (LightEditorPanel)                         Flashback-Timeline
   |  Widgets schreiben in LightState (Scratch)            |  IrlLightKeyframe (18 Werte)
   |  LightSync.push() jedes Frame                         |  KeyframeChangeIrlLight.apply()
   v                                                       v  (Reflection)
 PlacedLight  <------------------------------------  IrlFlashbackBridge.apply(uid, double[18])
   |  in LightScene (statische ArrayList, Main-Thread)
   |  LightStore: JSON pro Welt (config/irl-redactor/lights/<welt>.json)
   v
 GameRendererLightMixin @HEAD renderWorld
   -> FramePipeline.frame()
        LightDriver.collect(): VlGlobalsBuffer.set(...), registerPoint/registerSpot
        LightRegistry.prioritize(kamera)
        ShadowBaker.bake()  -> Shadow-Tile pro Licht
 GameRendererLightMixin nach updateCameraState()
   -> FramePipeline.uploadIfPending()
        LightRegistry.flush(kamera): kamera-relativ, Cap, Pack in LightBuffer
        LightBuffer.upload(): SSBO Binding 7 (+ Profile), VlGlobalsBuffer: UBO Binding 7
 Iris rendert das Shaderpack
   gepatchte GLSL (patches/*.irlights) liest SSBO 7, UBO 7, Shadow-Atlanten,
   Gobo-Array und addiert Licht in die Beleuchtung des Packs
```

## 2. Editor-Seite (Java, `irl-editor`)

**Datenmodell.** `light/PlacedLight` ist das einzige Modell eines Lichts und zugleich
das JSON-Schema (Gson reflektiv, Feldnamen = Dateiformat). Felder:
Typ `POINT|SPOT`, Name, Weltposition (double), Richtung, Farbe `r,g,b,a` (linear 0..1),
`intensity` (0..20, ohne Einheit), `radius` (Punkt-Reichweite in Blöcken), `range`
(Spot-Reichweite), `outerAngleDeg`/`innerAngleDeg`, Volumetrik (`beamStrength`,
`anisotropy`, `vlDensity`), `bulbSize` (Schattenweichheit in Blöcken), Masken
`entitiesOnly`/`blocksOnly`, `shadows`, Gobo (`cookie`, Rotation, Skalierung, Invert).
`id` ist transient (Schlüssel der Shadow-Caches), `uid` ist die persistente Identität
(nur im Flashback-Fork).

**Szene.** `light/LightScene` ist eine statische `ArrayList<PlacedLight>`, nur vom
Client-Main-Thread benutzt. Es gibt keine Gruppen, keine Hierarchie, kein Undo.

**Editor.** `editor/LightEditorScreen` öffnet `LightEditorPanel` (ImGui, 1243 Zeilen).
Bei Auswahl eines Lichts kopiert `LightSync.pull` das `PlacedLight` in den
ImGui-Puffer `LightState`; solange es ausgewählt ist, schreibt `LightSync.push` den
Puffer in **jedem Frame** zurück (kein "Übernehmen"). Nicht 1:1 übersetzt werden:
Spot-Kegel (UI: Winkel + Weichheit, Engine: außen + innen), Volumetrik (UI: an/aus +
Stärke, Engine: nur Stärke) und der Gobo-Winkel (UI Grad, Engine Radiant).
`drawGizmo` nutzt ImGuizmo auf der Background-Drawlist mit der MC-Kameramatrix.
`client/FreeCamera` + `CameraFreeMixin` liefern die Free-Cam.

**Persistenz.** `LightStore` speichert die Szene als JSON unter
`config/irl-redactor/lights/<weltKey>.json`, geladen bei `ClientPlayConnectionEvents.JOIN`,
gespeichert bei `DISCONNECT` (`IRLRedactorClient`). Der Weltschlüssel ist der
Speicherordner (Einzelspieler) bzw. die Serveradresse. In einem Flashback-Replay ist
das der Name der temporären Replay-Welt, also nicht pro Replay eindeutig (relevant für F3).

**Auto-Lichter.** `light/auto/AutoLightManager` scannt Blöcke mit Lichtemission
(Fackel, Glowstone ...) und erzeugt Punktlichter, die nie gespeichert werden.

**Einstellungen.** `light/LightConfig`: statische Felder für Schatten-Qualität,
Bake-Budget, Volumetrik-Globals, Outline usw.

## 3. Pro Frame (Java, `irl-core`)

1. **`GameRendererLightMixin` @HEAD `renderWorld`** startet `VlProfiler.frameTick()` und
   klammert die Bake-Zeit mit einem GL-Timer, dann `FramePipeline.frame(...)`.
2. **Shader aus?** `IrisShadersState.shadersDisabled()` -> Registry leeren, SSBO einmal
   auf 0 Lichter setzen, Shadow-Texturen freigeben, ruhen.
3. **`LightDriver.collect`** schreibt die globalen Volumetrik-, Outline- und
   Schatten-Parameter in `VlGlobalsBuffer` und meldet jedes `PlacedLight` bei
   `LightRegistry.registerPoint/registerSpot` an (absolute Position in double,
   Kegelwinkel als Halbwinkel-Cosinus über `LightMath.cone`, Gobo über
   `CookieArray.resolve` als Texture-Array-Layer). Danach Auto-Lichter bis zur
   Kapazitätsgrenze, Schatten nur für die nächsten.
4. **`LightRegistry.prioritize`** sortiert nach Abstand Kamera zu Lichtoberfläche
   (Hysterese 8 Blöcke, Volumetrik-Bonus 64 Blöcke).
5. **`ShadowBaker.bake`** rendert Tiefenkarten (eigener Raw-GL-Pfad, weil 1.21.5/1.21.9 den
   alten Immediate-Pfad entfernt haben):
   - Spot: perspektivische Kachel im `SpotlightDepthAtlas` (Quadtree 8 volle + 24 halbe +
     32 Viertel-Kacheln = 64), dazu `SpotShadowPyramid` (Min/Max) und `SpotShadowEvsm`.
   - Punkt: Würfel-Block im `PointDepthAtlas` (3 Stufen, 2 + 12 + 16 = 30 Blöcke) plus
     Pyramide und Moment-Map.
   - Blöcke über `BlockShadowCollector`/`BlockShadowCache`, Entities über
     `ShadowCasterSource`/`OccluderSink` (im Editor `RedactorEntityCasterSource`).
   - Gedrosselte Erst-Bakes setzen `SHADOW_PENDING`: dieses Licht fehlt im SSBO für
     1 bis 2 Frames, statt ohne Schatten durch Wände zu leuchten.
6. **`GameRendererLightMixin` nach `updateCameraState`** ruft
   `FramePipeline.uploadIfPending` -> `LightRegistry.flush(kameraPos)`: Position
   kamera-relativ (double-Subtraktion, dann float), Upload-Cap, Pakete in `LightBuffer`.
7. **`LightBuffer.upload`**: std430-SSBO an **Binding 7**:
   - Header 16 B: `lightCount`, `vlIntensity`, `vlFlags`, Magic `0x49524C50` (Profile gültig)
   - `Light[2048]` je 96 B (6 x vec4):
     `posRadius` (xyz kamera-relativ, w Radius), `colorIntensity` (rgb linear, a Intensität),
     `dirType` (xyz Richtung, w 0 Punkt/1 Spot), `cone` (cos außen/innen, Maske, bulbSize),
     `vlParams` (HG-g, Dichte, Strahl, Shadow-Tile), `cookie` (Layer, Rotation, Skala, Flags)
   - `LightProfile[2048]` je 112 B (Volumetrik-/Outline-Overrides pro Licht), danach
     Replay-Zielisten. Dieser Anhang wurde ohne Verschieben der alten Felder ergänzt.
8. **`VlGlobalsBuffer`**: std140-**UBO an Uniform-Binding 7** (eigener Namensraum), 7 x vec4.
   `vlB.w` ist ein Frame-Zähler (für Dither-Rotation), `vlD.z/w` sind reserviert.
9. **`ClusterGridBuffer`** (SSBO Binding 6) enthält Kachel-Bitmasken. Der Editor ruft
   `FramePipeline.onGbufferMatricesCaptured` nie auf, daher bleibt das Flag 0 und der
   Shader läuft die volle Lichtschleife (nur das BBS-Addon nutzt Clustering).
10. **Sampler**: `ProgramSamplersBuilderMixin` hängt an jedes Iris-Programm über
    `IrlSamplersBind` die Texturen `irl_spotShadowAtlas`, `irl_pointShadowAtlas`,
    Pyramiden, EVSM/MSM-Karten und `irl_cookieArray` (Gobos, R8, 512², Texture-Array).
    Iris kennt keinen 2D-Array-Typ; `SamplerBindingCubeArrayMixin` korrigiert das GL-Target.

## 4. Shader-Seite (GLSL, `patches/*.irlights`)

**Patcher.** `irl-core/patcher` wendet eine eigene DSL an: `+file` legt eine Datei an,
`@file` + `after|before|replace "Anker"` fügt Text an wörtlichen Ankern ein. Validierung
vor jeder Änderung, Dry-Run, Rollback, funktioniert auf gezippten Packs. Sieben Patches,
je 2600 bis 3400 Zeilen. **Jeder Patch enthält eine eigene vollständige Kopie der
Lichtbibliothek** (`shaders/lib/irlite/irlite_lights.glsl`), angepasst an die Uniforms und
den Farbraum des Packs. Eine Änderung an der Lichtformel muss heute also siebenmal
gemacht werden.

**Oberflächenlicht** (am Beispiel Complementary Reimagined, `irlite_lightSurface`,
eingebunden in `lib/lighting/mainLighting.glsl`): Schleife über `irlite_lightCount`:

```glsl
if (dist2 >= radius*radius) continue;              // Reichweiten-Test
float dr = dist / radius;                          // Frostbite-Fenster
float falloff = max(1.0 - dr*dr*dr*dr, 0.0);
float attenuation = falloff * falloff;             // (1-(d/r)^4)^2, KEIN 1/d²
attenuation *= spotCone;                           // linearer Übergang cos außen..innen
attenuation *= irlite_cookie(...);                 // Gobo
attenuation *= irlite_spotShadow/pointShadow(...); // PCSS, lightSize = bulbSize (Blöcke)
vec3 lightCol = pow(color, 1/2.2) * intensity;     // Farbe gamma-kodiert
diffuseOut += lightCol * attenuation * ndl;        // Lambert-Kosinus
specularOut += GGX(...) * lightCol * attenuation;
diffuseOut *= 5.0;                                 // pauschaler Faktor
```

Complementary addiert das Ergebnis **nach** `finalDiffuse = sqrt(finalDiffuse)` des Packs,
also in einem nichtlinearen Zwischenraum. Photon rechnet dagegen mit
`rec709_to_rec2020` in linearem Licht. Die Stelle, an der Licht einfließt, ist pro Pack
verschieden.

**Schatten.** `irlite_spotShadow`/`irlite_pointShadow`: Pyramiden-Klassifikation,
Blocker-Suche, PCSS-Halbschatten `lightSize * (d - dBlocker) / dBlocker`, adaptive PCF
bzw. EVSM/MSM für breite Halbschatten. `lightSize` ist eine Weltgröße in Blöcken, auf
`range * 0.5` begrenzt. Damit lässt sich die echte Quellengröße (P3) direkt einspeisen.

**Volumetrik.** Eigener Pass (`deferred2`, halbe Auflösung), Ray-March mit
Henyey-Greenstein-Phase, Schatten pro Schritt, 3D-Rauschen. **Das Rauschen hängt an
`frameTimeCounter`** (Iris, Echtzeit) und die Dither-Rotation am Frame-Zähler
`vlB.w`. Beides ist nicht an den Replay-Tick gebunden (relevant für W8 und F4).

**Outline.** Rim-Licht in `composite1`, für den Film-Auftrag nicht relevant.

## 5. Flashback-Anbindung (Vorbild zqicev)

- **`IrlFlashbackBridge`** (im Editor-Fork): statische Fassade nur mit JDK-Typen,
  per Reflection erreichbar: `listLights()`, `snapshot(uid) -> double[18]`,
  `apply(uid, double[18])`. Feste 18-Slot-Belegung (Position, Farbe, Intensität, Radius,
  Reichweite, Richtung, Kegel, Volumetrik, bulbSize).
- **Addon** (eigener Mod `irl-flashback`, Flashback nur `modCompileOnly files("libs/flashback.jar")`):
  - `IrlFlashbackAddonClient` registriert `IrlLightKeyframeType` in `KeyframeRegistry`
    (auch ohne IR Lights, damit gespeicherte Szenen laden).
  - `IrlLightKeyframe` hält Ziel-uid + 18 Werte; Position per Catmull-Rom/Hermite als
    Vektor, der Rest pro Komponente.
  - `KeyframeChangeIrlLight.apply` ruft die Bridge; `supportsHandler` nur für
    `MinecraftKeyframeHandler` (Vorschau und Export).
  - Mixins (alle `remap = false`): `Keyframe$TypeAdapter` (De-/Serialisierung mit
    `"type":"irl_light"`), `FlashbackGson.build` (konkreter Adapter für die
    Editor-Historie), `TimelineWindow#createNewKeyframe` (weitere Keyframes einer Spur
    übernehmen das Ziel und nehmen den aktuellen Zustand auf).
- Grenzen: nur einzelne Lichter, nur die 18 Zahlen (kein Typ, keine Schatten-/Gobo-Wahl,
  keine Gruppen), Szene wird pro Welt statt pro Replay gespeichert.

## 6. Diagnose

`client/diag/VlProfiler` misst GPU-Zeit pro Pass mit `GL_TIME_ELAPSED`-Queries
(`bake-head`, Iris-Programme über `CompositeRendererTimerMixin`), fasst sekundenweise
zusammen und zeigt eine HUD-Zeile, optional NVX-VRAM-Info. Aktiv über
`-Dirlredactor.profileVl=true` oder den Perf-Bereich des Editors.
Die `tools/`-Ordner in irl-core enthalten Einzel-Tests (PowerShell + Java ohne Gradle).
Es gibt kein JUnit-Setup und keine Gametests.

## 7. Konsequenzen für Gaffer

| Befund | Folge |
|---|---|
| Kein 1/d², Intensität ohne Einheit, Farbe gamma-kodiert, Faktor 5 | P1/P2 ändern die Kernformel in jedem Pack; `radius` wird zum reinen Culling-Radius |
| Licht fließt pro Pack an anderer Stelle und in anderem Farbraum ein | Belichtung muss pro Pack kalibriert und gemessen werden |
| GLSL-Bibliothek siebenfach kopiert | eine gemeinsame Quelle + kleine Pack-Adapter, Patches werden generiert |
| PCSS nimmt Weltgröße der Quelle | P3 speist die echte Flächengröße ein |
| Nur perspektivische und Würfel-Schatten | P4 braucht eine orthografische Schattenprojektion |
| `ShadowCasterSource`/`OccluderSink` existieren | Flags/Bounces (P5/P6) als zusätzliche Schattenwerfer einspeisbar |
| SSBO-Anhang-Muster (`LightProfilesBuffer`) existiert | Gaffer-Parameter als weiterer Anhang, ohne alte Offsets zu verschieben |
| Volumetrik-Rauschen an Echtzeit | W8/F4 brauchen eine Replay-Tick-Uhr im UBO (`vlD.z` ist frei) |
| Szene pro Welt, kein Undo, keine Gruppen | Modell wird erweitert: Szene pro Replay, Undo-Stapel, Kanäle/Gruppen/Cues |
| Bridge mit fester 18-Slot-Liste | F1 braucht benannte Parameter statt fester Indizes |
| Kein Netzwerk, Szene pro Client | M1 ersetzt `LightScene`/`LightStore` durch Server-Entities |

## 8. Netzwerk, Gizmo und Hilfslinien (geprüft für M1 und W15)

**Netzwerk.** Im gesamten Editor-Fork gibt es kein `CustomPayload`, kein
`ServerPlayNetworking` und kein `ClientPlayNetworking`. Der Main-Entrypoint
`IRLRedactorMod` ist ein Stub. Lichter existieren nur im `LightScene` des eigenen Clients
und in dessen `config/irl-redactor/lights/<worldKey>.json`. Neue Lichter heißen
standardmäßig "Источник" (`PlacedLight.name`, `LightStore`, Namensmuster im Panel).

**Gizmo und Hilfslinien.** Alle vier Befunde aus W15 bestätigt:

| | Befund | Stelle |
|---|---|---|
| a | `GuideOverlay` und ImGuizmo zeichnen auf `ImGui.getBackgroundDrawList()`, also 2D ohne Tiefe | `GuideOverlay` Z. 24/188, `LightEditorPanel.drawGizmo` Z. 1186 |
| b | `line()` zeichnet nur, wenn **beide** Endpunkte projizierbar sind; `project()` liefert false bei `clip.w <= 1e-4` | `GuideOverlay` Z. 212-218, 561-569 |
| c | `GIZMO_SIZE = 0.08f` als `setGizmoSizeClipSpace`: konstante Bildschirmgröße | `LightEditorPanel` Z. 50, 1188 |
| d | View/Projektion nachgebaut aus `mc.options.getFov()`, `rotateX(pitch)`, `rotateY(yaw+180)`, `perspective(fov, aspect, 0.05, 1000)` | `LightEditorPanel` Z. 1156-1164, `GuideOverlay` Z. 202-207 |

Zu (d) steht der Messwert noch aus: Der Test (Abweichung Gizmo-Mitte gegen echte
Projektion bei View Bobbing, Sprinten, FOV-Effekten, Flashback-FOV) läuft vor dem Fix.

## 9. Photon v1.3b (Referenzpack)

Der vorhandene Patch `photon.irlights` ist gegen Photon v1.3b geschrieben
(`@packversion v1.3b`). Er fügt die Bibliothek über `shaders/include/buffers.glsl` ein,
die Oberflächenbeleuchtung in `program/d4_deferred_shading.fsh` (deferred), die
Volumetrik in `program/c0_vl.fsh`. Photon wandelt die Lichtfarbe mit `rec709_to_rec2020`
um; das deutet auf lineare Rechnung hin. **Ob die Einspeisestelle wirklich linear ist,
wird vor der P2-Kalibrierung geprüft und hier eingetragen** (Stand: offen).

## 10. Flashback-Aufzeichnung der Gaffer-Entities (M1)

**Stand: offen.** Hier kommt das Ergebnis des ersten M1-Tests hin: Zeichnet Flashback
die synchronisierten Entity-Daten der Gaffer-Entities (inklusive eigener
`TrackedData`-Handler, Pult-Entity und `getTime()`) vollständig auf, und spielt es sie im
Replay Tick für Tick wieder ab?

Zusätzlich zu messen und hier einzutragen:

| Prüfung | Erwartung | Ergebnis |
|---|---|---|
| Effekt bei `doDaylightCycle false` | läuft weiter (Uhr ist `getTime()`, nicht `getTimeOfDay()`) | offen |
| Time-of-Day-Keyframe in Flashback | Effektverlauf unverändert | offen |
| Freeze-Keyframe in Flashback | Wirkung auf Effekte wird dokumentiert | offen |
| Tickrate-Keyframe in Flashback | Wirkung auf Effekte wird dokumentiert | offen |
