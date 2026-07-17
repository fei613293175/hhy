#!/usr/bin/env python3
"""Synchronize catalog-derived Android assets without deleting the Android project.

The executable project and Gradle wrapper are curated artifacts. This command only
refreshes the screen catalog and design-token copies, then validates required modules
and the pinned official Gradle distribution.
"""
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android"


def read_csv(relative: str) -> list[dict[str, str]]:
    with (ROOT / relative).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def main() -> int:
    required = [
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradlew",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
        "app/build.gradle.kts",
        "core/designsystem/build.gradle.kts",
        "core/network/build.gradle.kts",
        "feature/shell/build.gradle.kts",
    ]
    missing = [item for item in required if not (ANDROID / item).exists()]
    if missing:
        raise SystemExit("missing curated Android scaffold files: " + ", ".join(missing))

    wrapper = (ANDROID / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    expected = "https\\://services.gradle.org/distributions/gradle-9.4.1-bin.zip"
    if expected not in wrapper:
        raise SystemExit("Gradle wrapper distribution is not pinned to official Gradle 9.4.1")

    settings = (ANDROID / "settings.gradle.kts").read_text(encoding="utf-8")
    for module in [":app", ":core:designsystem", ":core:network", ":feature:shell"]:
        if f'include("{module}")' not in settings:
            raise SystemExit(f"missing Android module {module}")

    screens = read_csv("catalogs/android_screens.csv")
    if len(screens) != 102 or len({row["ID"] for row in screens}) != 102:
        raise SystemExit("Android screen catalog must contain 102 unique screens")
    page_specs = {row["页面ID"]: row for row in read_csv("catalogs/ui_page_specifications.csv")}
    enriched_screens = []
    for row in screens:
        spec = page_specs.get(row["ID"])
        if not spec:
            raise SystemExit(f"missing enriched Android specification for {row['ID']}")
        enriched_screens.append({
            **row,
            "模板ID": spec["模板ID"],
            "字段规格": spec["字段规格"],
            "状态规格": spec["状态规格"],
            "动作规格": spec["动作规格"],
            "DoR状态": spec["DoR状态"],
            "文档版本": "1.2.2",
        })
    write_json(
        ANDROID / "app/src/main/assets/android-screens.v1.2.2.json",
        enriched_screens,
    )

    token_candidates = sorted((ROOT / "design/tokens").glob("hhy_design_tokens_v1.2.2.json"))
    if len(token_candidates) != 1:
        raise SystemExit(f"expected one V1.2.2 token JSON, got {len(token_candidates)}")
    token_data = json.loads(token_candidates[0].read_text(encoding="utf-8"))
    metadata = token_data.setdefault("metadata", {})
    metadata["version"] = "1.2.2"
    metadata["updated_at"] = "2026-07-16"
    normalized = json.dumps(token_data, ensure_ascii=False, indent=2) + "\n"
    token_candidates[0].write_text(normalized, encoding="utf-8", newline="\n")
    target = ANDROID / "core/designsystem/src/main/assets/hhy_design_tokens_v1.2.2.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(normalized, encoding="utf-8", newline="\n")

    source = "\n".join(
        path.read_text(encoding="utf-8", errors="replace")
        for path in ANDROID.rglob("*.gradle.kts")
    )
    if re.search(r"\bprojects\.", source):
        raise SystemExit("typesafe project accessor usage is forbidden until enabled explicitly")
    print(f"synced android_screens={len(screens)} token={token_candidates[0].name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
