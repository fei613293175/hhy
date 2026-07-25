#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
STATE_MACHINE = ROOT / "database/state_machines.yaml"
MIGRATION = ROOT / "database/migrations/V039__r12_publish_management_invariants.sql"


def fail(messages: list[str]) -> None:
    print("CONTENT_STATE_MACHINE_PROJECTION_FAIL")
    print("\n".join(messages))
    raise SystemExit(1)


document = yaml.safe_load(STATE_MACHINE.read_text(encoding="utf-8")) or {}
machines = [machine for machine in document.get("machines", []) if machine.get("code") == "CONTENT_STATUS"]
if len(machines) != 1:
    fail([f"expected exactly one CONTENT_STATUS machine, found {len(machines)}"])

machine = machines[0]
terminal = set(machine.get("terminal", []))
states = {machine.get("initial"), *terminal}
for transition in machine.get("transitions", []):
    source = transition.get("from")
    target = transition.get("to")
    if source != "*":
        states.add(source)
    states.add(target)
states.discard(None)

expected: set[tuple[str, str]] = set()
for transition in machine.get("transitions", []):
    source = transition["from"]
    target = transition["to"]
    if source == "*":
        expected.update((state, target) for state in states if state not in terminal and state != target)
    else:
        expected.add((source, target))

sql = MIGRATION.read_text(encoding="utf-8")
projection = re.search(
    r"-- BEGIN CONTENT_STATUS_SQL_PROJECTION(?P<body>.*?)-- END CONTENT_STATUS_SQL_PROJECTION",
    sql,
    re.DOTALL,
)
if projection is None:
    fail(["V039 is missing the delimited CONTENT_STATUS SQL projection"])

actual_pairs = re.findall(r"\('([A-Z_]+)'\s*,\s*'([A-Z_]+)'\)", projection.group("body"))
actual = set(actual_pairs)
errors: list[str] = []
if len(actual_pairs) != len(actual):
    errors.append("V039 projection contains duplicate edges")
missing = sorted(expected - actual)
extra = sorted(actual - expected)
if missing:
    errors.append(f"missing SQL edges: {missing}")
if extra:
    errors.append(f"extra SQL edges: {extra}")
for terminal_state in terminal:
    escaped = [edge for edge in actual if edge[0] == terminal_state]
    if escaped:
        errors.append(f"terminal state {terminal_state} has outgoing edges: {sorted(escaped)}")
if errors:
    fail(errors)

print(
    "CONTENT_STATE_MACHINE_PROJECTION_OK "
    f"machine=CONTENT_STATUS states={len(states)} edges={len(actual)} terminals={','.join(sorted(terminal))}"
)

