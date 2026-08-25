#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
ui_root="$repo_root/android/app/src/main/java/io/rank5/app/ui"
failed=0

check_absent() {
  local description="$1"
  local pattern="$2"
  local matches
  matches="$(rg -n "$pattern" "$ui_root" -g '*.kt' | rg -v '/theme/' || true)"
  if [[ -n "$matches" ]]; then
    printf 'UI contract violation: %s\n%s\n' "$description" "$matches"
    failed=1
  fi
}

check_absent 'raw dp/sp values outside theme tokens' '[0-9]+(\.[0-9]+)?\.(dp|sp)'
check_absent 'local font overrides outside the six typography styles' 'font(Size|Weight)|letterSpacing'
check_absent 'non-rounded Material icon family' 'icons\.(filled|outlined|sharp|twotone)|Icons\.(Filled|Outlined|Sharp|TwoTone)'

spacing_count="$(sed -n '/object Spacing {/,/^}/p' "$ui_root/theme/Dimens.kt" | rg -c '^    val ')"
type_count="$(rg -c '^private val (Hero|ScreenTitle|Heading|Title|Body|Label) = TextStyle' "$ui_root/theme/Type.kt")"

if [[ "$spacing_count" -ne 4 ]]; then
  printf 'UI contract violation: expected 4 spacing tokens, found %s\n' "$spacing_count"
  failed=1
fi
if [[ "$type_count" -ne 6 ]]; then
  printf 'UI contract violation: expected 6 typography styles, found %s\n' "$type_count"
  failed=1
fi

exit "$failed"
