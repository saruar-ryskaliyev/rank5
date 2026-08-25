# Rank5 UI system

The Android UI uses one system font and a deliberately small token set. New UI must reuse these tokens instead of introducing local values.

- Spacing: `Spacing.xs`, `sm`, `md`, and `lg` (4, 8, 16, 24 dp).
- Type: six underlying styles in `Type.kt` (hero, screen title, heading, title, body, label). Material typography slots alias these styles.
- Color: violet and rose are the only chromatic families. Black/white neutral variations provide backgrounds, surfaces, text, and outlines. Semantic roles come from `MaterialTheme.colorScheme`.
- Shape: `Corners.compact` and `Corners.roomy` (12 and 20 dp). Components consume them through `MaterialTheme.shapes`.
- Icons: Material rounded icons. Brand and provider artwork are the only image exceptions.
- Layout: every destination uses `GameScaffold`, which supplies safe insets, 16 dp horizontal alignment, and shared maximum content widths. Interactive targets are at least `Sizes.touchTarget` (48 dp); normal text never drops below 13 sp.

## Motion

Motion explains state changes and preserves continuity; it is not decoration. Following the supplied Google I/O “Motional Intelligence” guidance, transitions must be:

1. Reentrant: a new state may arrive during any transition.
2. Continuous: a retarget starts from the current rendered value.
3. Smooth: retargeting should preserve velocity where practical.

Compose state animations satisfy the first two requirements. `Modifier.animateLayoutChanges()` is the project equivalent of View-system `animateLayoutChanges`; it uses a retargetable, non-bouncy spring from `Motion.kt`. Destination changes use one centrally timed `AnimatedContent` transition. Avoid imperative, fire-and-forget animators and skip animation when state has not changed. Compose also honors the system animator-duration scale.
