# Compose API Availability Report — rank5/android

## 1. Sources consulted

| Source | Detail |
|---|---|
| Gradle resolution | `JAVA_HOME=/opt/homebrew/opt/openjdk@17/... ./gradlew -q :app:dependencies --configuration debugRuntimeClasspath` in `/Users/saruar/rank5/android` → exit 0, output saved to `/tmp/api-check/deps.txt` (782 lines). First attempt failed for an unrelated reason (output dir didn't exist yet); the re-run succeeded. |
| Gradle cache bytecode | `classes.jar` extracted from each `.aar` under `~/.gradle/caches/modules-2/files-2.1/androidx.compose.*` into `/tmp/api-check/jars/`; inspected with JDK 17 `javap`. |
| Gradle cache **sources** | The cache also contained official `-sources.jar` for every artifact at the exact resolved version; extracted to `/tmp/api-check/src/`. This is the primary evidence below (file:line refers to sources inside those jars). |
| Web (for absences only) | [HapticFeedbackType API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType) ("Added in 1.8.0" markers), [androidx.compose.ui.platform package summary](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary) (`LocalClipboard` added 1.8.0), [Compose Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3) (`LoadingIndicator` is Expressive-only). |

No project files were modified.

## 2. Exact resolved versions (BOM 2024.12.01)

Confirmed identically by the Gradle dependency report and the cache contents (cache holds exactly one version per artifact):

| Artifact | Resolved version |
|---|---|
| `androidx.compose.ui:ui` (+ ui-text, ui-graphics, ui-unit, ui-util) | **1.7.6** |
| `androidx.compose.foundation:foundation` (+ foundation-layout) | **1.7.6** |
| `androidx.compose.animation:animation` (+ animation-core) | **1.7.6** |
| `androidx.compose.material3:material3` | **1.3.1** |
| `androidx.compose.material:material-icons-core` (+ icons-extended, ripple) | **1.7.6** |
| `androidx.compose.runtime:runtime` | **1.7.6** |
| `androidx.activity:activity-compose` | **1.9.3** (pinned directly; transitive 1.7.0/1.8.x requests upgraded to it) |

## 3. Per-API verdicts

All file:line evidence is from the official sources jars of the exact versions above; spot-checked against `javap` bytecode (matched in every case).

| # | API | Verdict | Evidence |
|---|---|---|---|
| 1 | `LazyItemScope.animateItem(fadeInSpec, placementSpec, fadeOutSpec)` | **EXISTS**, stable | foundation `lazy/LazyItemScope.kt:101`; `javap`: `public default Modifier animateItem(...)` |
| 2 | `detectDragGestures(onDragStart, onDragEnd, onDragCancel, onDrag)` (immediate) | **EXISTS**, exact signature | foundation `gestures/DragGestureDetector.kt:168` — `onDragStart: (Offset)->Unit`, `onDragEnd: ()->Unit`, `onDragCancel: ()->Unit`, `onDrag: (PointerInputChange, Offset)->Unit` |
| 3 | `SnackbarHost`, `SnackbarHostState`, `Scaffold(snackbarHost=)` | **EXISTS**, stable | m3 `SnackbarHost.kt:218` (host), `:62` (state), `:99` (`suspend showSnackbar(message, actionLabel, withDismissAction, duration)`); `Scaffold.kt:84–88` has `snackbarHost: @Composable () -> Unit` |
| 4 | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` + `SegmentedButtonDefaults.itemShape` | **EXISTS**, stable (no `@ExperimentalMaterial3Api` anywhere in the file) | m3 `SegmentedButton.kt:265`, `:206` (`SingleChoiceSegmentedButtonRowScope.SegmentedButton(selected, onClick, shape, ...)`), `:529` (`itemShape(index, count, baseShape)`) |
| 5 | `LinearProgressIndicator(progress: () -> Float)`; `CircularProgressIndicator` | **EXISTS** | m3 `ProgressIndicator.kt:138` (lambda + optional `gapSize`, `drawStopIndicator`); `:573` (circular determinate lambda + `gapSize`); `:632` (indeterminate `CircularProgressIndicator()`, stable). The older lambda overloads without `gapSize` are `DeprecationLevel.HIDDEN` binary shims — source calls resolve to the gapSize overloads transparently |
| 6 | `AlertDialog(onDismissRequest, confirmButton, dismissButton, title, text)` | **EXISTS**, stable | m3 `AlertDialog.kt:95–102` (expect), `AndroidAlertDialog.android.kt:31` (actual); also optional `icon`, `shape`, colors params |
| 7 | `HorizontalDivider` | **EXISTS** | m3 `Divider.kt:50` (`modifier, thickness, color`) |
| 8 | `AnimatedContent` + `transitionSpec` + `togetherWith`/`slideInVertically`/`fadeIn`/`fadeOut`/`scaleIn` | **EXISTS**, all stable | animation `AnimatedContent.kt:129–136` (the default `transitionSpec` itself uses `fadeIn + scaleIn togetherWith fadeOut`); `togetherWith` infix `:274`; `EnterExitTransition.kt:726/284/303/392` |
| 9 | `animateIntAsState`/`animateFloatAsState`/`animateDpAsState`/`animateColorAsState`, `rememberInfiniteTransition`+`infiniteRepeatable`+`RepeatMode.Reverse`, `tween`, `spring`, `Spring.DampingRatioMediumBouncy` | **EXISTS**, all | animation-core `AnimateAsState.kt:269/63/109`; animation `SingleValueAnimation.kt:57`; `InfiniteTransition.kt:45` (+ `InfiniteTransition.animateFloat:311`); `AnimationSpec.kt:966/435–444/810/826`; `const val DampingRatioMediumBouncy = 0.5f` |
| 10 | `AnimatedVisibility` + `expandVertically`/`shrinkVertically` | **EXISTS** | animation `AnimatedVisibility.kt:125` (bool) and `:378` (`MutableTransitionState`); `EnterExitTransition.kt:582/661` |
| 11 | `LocalHapticFeedback` + `HapticFeedbackType` values | **EXISTS — but only 2 values** | ui `platform/CompositionLocals.kt:134`; `hapticfeedback/HapticFeedbackType.kt:39–49`: companion exposes **only `LongPress` and `TextHandleMove`**, and its own `values()` returns `listOf(LongPress, TextHandleMove)`. `javap` on the Companion confirms: `getLongPress`, `getTextHandleMove`, `values()` — nothing else |
| 12 | `LocalClipboardManager` + `setText(AnnotatedString)` | **EXISTS**, not deprecated in this version | ui `platform/CompositionLocals.kt:75`; `ClipboardManager.kt:31` `fun setText(annotatedString: AnnotatedString)`, `:40` `getText()` |
| 13 | `imePadding`/`navigationBarsPadding`/`systemBarsPadding`/`safeDrawingPadding`, `WindowInsets.ime` | **EXISTS** | foundation-layout `WindowInsetsPadding.android.kt:148/166/94/40`; `WindowInsets.android.kt:163` — note `WindowInsets.Companion.ime` getter is `@Composable` (read it inside composition) |
| 14 | `Modifier.semantics {}` with `customActions`, `liveRegion = LiveRegionMode.Polite`, `stateDescription`, `contentDescription` | **EXISTS** | ui `semantics/SemanticsModifier.kt:97`; `SemanticsProperties.kt:1197/932/882/869`; `CustomAccessibilityAction(label, action)` `:623` — **`action` must return `Boolean`**; `LiveRegionMode.Polite` `:828–834` |
| 15 | `zIndex`, `graphicsLayer {}`, `shadow(elevation, shape)`, `Brush.verticalGradient(colors)`, `drawBehind {}`, `Canvas`, `withFrameNanos` | **EXISTS**, all | ui `ZIndexModifier.kt:41`; `GraphicsLayerModifier.kt:483` (block overload); `Shadow.kt:63` (+ ambient/spot color overload `:102`); ui-graphics `Brush.kt:219`; ui `DrawModifier.kt:103`; foundation `Canvas.kt:42` (+ contentDescription overload `:63`); runtime `MonotonicFrameClock.kt:86` |
| 16 | `BackHandler(enabled) { }` | **EXISTS** | activity-compose 1.9.3 `BackHandler.kt:82` `public fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)`; `javap` confirms |
| 17 | `Typography()`, `MaterialTheme(colorScheme, typography, shapes)`, `Shapes()` | **EXISTS** (one caveat) | m3 `Typography.kt:82` (full 15-style constructor with defaults); `Shapes.kt:71`; `MaterialTheme.kt:51–55` — **positional order is `(colorScheme, shapes, typography, content)`**, so pass `typography`/`shapes` as named arguments |
| 18 | `FilterChip`, `ElevatedCard`, `Card(colors=...)`, `Surface(tonalElevation=...)` | **EXISTS** | m3 `Chip.kt:455`; `Card.kt:194`; `Card.kt:81–84` + `cardColors(containerColor, ...)` `:489`; `Surface.kt:94–99` |
| 19 | `androidx.compose.material3.ripple(...)` | **EXISTS**, stable in 1.3.1 | m3 `Ripple.kt:76` `ripple(bounded, radius, color): IndicationNodeFactory` + `ColorProducer` overload `:122`; `javap` confirms both |
| 20 | `TextStyle(letterSpacing, fontWeight, lineHeight)`, `FontWeight.Black/ExtraBold`, `PlatformTextStyle(includeFontPadding = false)` | **EXISTS** | ui-text `TextStyle.kt:85–99` (public ctors include all three params); `FontWeight.kt:87` (`ExtraBold = W800`), `:90` (`Black = W900`); `AndroidTextStyle.android.kt:62–67` `constructor(includeFontPadding: Boolean)` — **not deprecated** in 1.7.6 |

## 4. Allowed / Do-NOT-use summary

**Allowed (all verified present in the resolved artifacts):** `animateItem`, `detectDragGestures` (4-param immediate variant), `SnackbarHost`/`SnackbarHostState.showSnackbar`/`Scaffold(snackbarHost=)`, segmented buttons incl. `SegmentedButtonDefaults.itemShape`, lambda-progress `LinearProgressIndicator`/`CircularProgressIndicator` (+ indeterminate), `AlertDialog`, `HorizontalDivider`, `AnimatedContent`/`togetherWith`/`slideInVertically`/`fadeIn`/`fadeOut`/`scaleIn`, all `animate*AsState`, `rememberInfiniteTransition`/`infiniteRepeatable`/`RepeatMode.Reverse`/`tween`/`spring`/`Spring.DampingRatioMediumBouncy`, `AnimatedVisibility` + `expandVertically`/`shrinkVertically`, `LocalHapticFeedback` (LongPress/TextHandleMove only), `LocalClipboardManager.setText(AnnotatedString)`, all four insets-padding modifiers + `WindowInsets.ime`, full semantics set (`customActions`, `liveRegion`, `stateDescription`, `contentDescription`), `zIndex`/`graphicsLayer`/`shadow`/`Brush.verticalGradient`/`drawBehind`/`Canvas`/`withFrameNanos`, `BackHandler`, `Typography()`/`MaterialTheme(...)`/`Shapes()`, `FilterChip`/`ElevatedCard`/`Card(colors=)`/`Surface(tonalElevation=)`, `material3.ripple()`, `TextStyle(...)`/`FontWeight.Black`/`PlatformTextStyle(includeFontPadding=false)`.

**Do NOT use (missing or deprecated in these versions):**

- `HapticFeedbackType.Confirm`, `.Reject`, `.SegmentTick`, `.SegmentFrequentTick`, `.GestureEnd`, `.ToggleOn/Off`, etc. — **MISSING**; added in ui 1.8.0 (official docs). Only `LongPress` and `TextHandleMove` compile against 1.7.6.
- `LocalClipboard` (suspend `Clipboard` API) — **MISSING**; added in ui 1.8.0. Use `LocalClipboardManager` (which is not deprecated in 1.7.6).
- material3 `LoadingIndicator` / `ContainedLoadingIndicator` — **MISSING** in 1.3.1; they're M3 Expressive APIs that only ever shipped in 1.4/1.5 alpha tracks. Use `CircularProgressIndicator`.
- `Modifier.animateItemPlacement` — present but `@Deprecated("Use Modifier.animateItem() instead")` **and** `@ExperimentalFoundationApi` (`LazyItemScope.kt:120–128`). Use `animateItem`.
- `EnterTransition.with(...)` — deprecated, renamed to `togetherWith` (`AnimatedContent.kt:277–281`).
- `LinearProgressIndicator(progress: Float)` / `CircularProgressIndicator(progress: Float)` — non-lambda overloads are deprecated with a visible warning (`ProgressIndicator.kt:383`, `:728`). Use the lambda overloads.
- Gotcha, not a missing API: `MaterialTheme`'s positional parameter order is `(colorScheme, shapes, typography)` — always pass named arguments.

## 5. Confidence & gaps

**Confidence: very high.** Every verdict is grounded in the published sources jar and/or `javap` of the compiled `classes.jar` for the exact artifact versions Gradle resolves for this project (verified end-to-end by the dependency report). Absences were double-confirmed against official "Added in" version markers in Android developer docs.

Known gaps: I verified the listed signatures and the specific enum/constant values asked about, not every overload or default value of each API; `material-icons-extended` (used by the app) resolves to 1.7.6 but its icon set contents weren't audited; Kotlin 2.0.21 compatibility wasn't separately tested, though the project having built successfully on this machine with these artifacts implies it. Extraction artifacts live in `/tmp/api-check/` (jars, sources, `deps.txt`) if the plan needs further signature checks.
