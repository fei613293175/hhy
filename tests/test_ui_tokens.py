#!/usr/bin/env python3
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "check_ui_tokens", ROOT / "scripts/check_ui_tokens.py"
)
assert SPEC and SPEC.loader
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)
TOKENS = json.loads((ROOT / CHECKER.TOKEN_RELATIVE_PATH).read_text(encoding="utf-8"))


class UiTokenCheckerTest(unittest.TestCase):
    def test_repository_is_fully_aligned(self) -> None:
        self.assertEqual([], CHECKER.validate(ROOT))

    def test_r04_media_sheet_uses_frozen_button_height_token(self) -> None:
        source = (ROOT / "apps/android/feature/media/src/main/java/cc/orbexa/hhy/media/MediaUploadSheet.kt").read_text(
            encoding="utf-8"
        )
        self.assertEqual(2, source.count("height(HhySize.PrimaryButtonHeight)"))
        self.assertNotIn("48.dp", source)

    def test_missing_token_group_is_rejected(self) -> None:
        broken = copy.deepcopy(TOKENS)
        del broken["motionMs"]
        self.assertIn("TOKEN_MISSING motionMs", CHECKER.validate_token_schema(broken))

    def test_css_value_drift_is_rejected(self) -> None:
        css = CHECKER.render_css(TOKENS, include_admin=False).replace(
            "--hhy-motion-fast: 120ms;", "--hhy-motion-fast: 121ms;"
        )
        errors = CHECKER.validate_css_text(
            "packages/design-tokens/h5.css", css, TOKENS, include_admin=False
        )
        self.assertTrue(any(error.startswith("CSS_VALUE_DRIFT") for error in errors))

    def test_raw_hex_and_inline_style_are_rejected(self) -> None:
        self.assertEqual(
            [
                "RAW_HEX_FORBIDDEN apps/h5/src/Page.vue",
                "INLINE_STYLE_FORBIDDEN apps/h5/src/Page.vue",
            ],
            CHECKER.validate_page_source_text(
                "apps/h5/src/Page.vue", '<div style="color:#fff">bad</div>'
            ),
        )

    def test_noncanonical_derivative_is_rejected_and_renderer_is_idempotent(self) -> None:
        canonical = CHECKER.render_css(TOKENS, include_admin=True)
        self.assertEqual(canonical, CHECKER.render_css(TOKENS, include_admin=True))
        errors = CHECKER.validate_css_text(
            "packages/design-tokens/admin.css",
            canonical + "/* hand-edited */\n",
            TOKENS,
            include_admin=True,
        )
        self.assertIn(
            "DERIVED_NOT_IDEMPOTENT packages/design-tokens/admin.css", errors
        )


if __name__ == "__main__":
    unittest.main()
