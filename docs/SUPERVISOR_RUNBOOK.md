# Governance V5.0 Supervisor Runbook

## Scope

The Supervisor is an external, bounded wrapper around the existing Governance V5.0 Orchestrator. It reads `governance/STATE.yaml`, calls the existing controller, records runtime evidence, and stops when the controller or a safety budget requires it.

It does not select tasks, write `governance/STATE.yaml`, increment Attempts, create Candidate evidence, or call Codex CLI directly. The existing `run-loop` starts one fresh ephemeral Codex Worker per governed run.

## Audit And Preconditions

Run from the repository root:

```powershell
python tools/governance/hhy_governance.py doctor --ci
python tools/governance/hhy_governance.py active-task --format json
python tools/supervisor/hhy_supervisor.py --repo "C:\path\to\hhy" --dry-run
python tools/supervisor/hhy_supervisor.py --repo "C:\path\to\hhy" --simulate
```

The compatibility audit is stored at:

```text
governance/runtime/supervisor/SUPERVISOR_COMPATIBILITY_AUDIT.json
governance/runtime/supervisor/SUPERVISOR_COMPATIBILITY_AUDIT.md
```

The audit and Doctor must pass before any real Supervisor run.

## Modes

`--dry-run` validates the repository, configuration, Doctor result, active task, and intended bounded action. It does not call `run-loop`.

`--simulate` exercises the status mapping table without calling the controller, Codex CLI, or writing governance state.

`--trial` is the only first real-run entry point. It limits execution to one batch containing one Orchestrator run:

```powershell
python tools/supervisor/hhy_supervisor.py --repo "C:\path\to\hhy" --trial
```

For unattended operation, use the Guardian mode described below. The Guardian owns
restarts for known transient failures and automatically resumes ordinary retryable
worker failures. It does not approve candidate evidence, bypass gates, alter
Attempt counts, or invent a business decision.

## Automatic Continuation

`MAX_RUNS_REACHED`, `NEXT_TASK_READY`, `RETRY_REQUIRED`,
`RETRYABLE_INFRASTRUCTURE`, and `SUPERVISOR_BUDGET_EXHAUSTED` are resumable by
the configured Guardian. The Guardian archives the previous stop report before
starting a new bounded Supervisor session. Every restart still requires a
passing Doctor, a valid authoritative state, no active conflict, no second
Supervisor, and remaining Guardian retry budget.

## Mandatory Stops

The Supervisor stops on `CANDIDATE_REQUIRED`, `EXTERNAL_BLOCKED`, `INFRASTRUCTURE_BLOCKED`, `FAILED_BOUNDED`, `POLICY_VIOLATION`, `OWNER_ACTION_REQUIRED`, `PROGRAM_COMPLETE`, `UNKNOWN_STATUS`, `STATE_INVALID`, `DOCTOR_FAILED`, `WORKTREE_UNSAFE`, `CODEX_AUTH_REQUIRED`, `SUPERVISOR_BUDGET_EXHAUSTED`, and `NO_PROGRESS_DETECTED`.

`PROGRAM_COMPLETE` creates `governance/runtime/supervisor/PROGRAM_COMPLETE.lock`. Windows startup detects this marker and exits.

## Windows Task Scheduler

## Local Control Center

Start the loopback-only control center:

```powershell
$py = "C:\path\to\python.exe"
& $py tools\supervisor\supervisor_control_center.py `
  --repo "C:\path\to\hhy" `
  --port 8765 `
  --open
```

Open `http://127.0.0.1:8765/`. The page reads the authoritative task and Supervisor runtime evidence, and delegates actions to the existing CLI and Windows scripts. It does not write `governance/STATE.yaml`, choose a task, or change Attempt counts.

The page provides:

- current Release, Task, Attempt, Revision, heartbeat and process status;
- Windows Task Scheduler state;
- dry-run and simulate;
- one-batch/one-run trial and explicitly confirmed bounded start;
- safe stop, task installation/uninstallation and authorized recoverable-stop clearing;
- readable event, batch and stop-report conclusions instead of raw JSON;
- a lifecycle observer that presents Supervisor and governance phases as a chat-like timeline; it is an observer, not a native Codex chat;
- editable Supervisor limits for the next start, including per-task timeout, total session time, batch limits, cooldown, heartbeat and Windows recovery delay.
- Guardian online state, last diagnosis, automatic retry count and next action;
- one-time installation of the Windows login task that keeps Guardian running.

Configuration changes are schema-validated and write only
`tools/supervisor/supervisor_config.yaml`. They do not write `governance/STATE.yaml`,
select a Task, or change Attempt counts. The current Supervisor process keeps its
already-loaded settings; saved values apply to the next start.

Before a real `--trial` or bounded start, the authoritative worktree must be
clean. The Supervisor extension itself must therefore be reviewed and committed
before the first real run; otherwise the controller stops with `WORKTREE_UNSAFE`
without starting a Codex Worker.

The server binds to `127.0.0.1` by default and should not be exposed through a reverse proxy or LAN binding.

## Windows Task Scheduler

Install the unattended Guardian once:

```powershell
.\tools\supervisor\windows\install_autonomous_mode.ps1 `
  -RepoPath "C:\path\to\hhy"
```

After installation, Windows starts Guardian at user logon. Guardian starts
Supervisor automatically when the project is active, monitors heartbeats,
archives known recoverable stop reports, and restarts bounded sessions. The
control center's **安装全天候自动模式** button performs the same operation.

Guardian state and readable recovery events are stored under:

```text
governance/runtime/supervisor/GUARDIAN_STATE.json
governance/runtime/supervisor/GUARDIAN_EVENTS.jsonl
governance/runtime/supervisor/guardian-history/
```

Guardian never auto-clears `UNKNOWN_STATUS` unless its diagnostic evidence maps
the error to a known transient category such as Windows access denial,
governance lock contention, or a controller timeout. Candidate, external,
policy, authentication, state-integrity and bounded-failure conditions remain
visible as attention-required states.

Install for the current user:

```powershell
.\tools\supervisor\windows\install_supervisor_task.ps1 -RepoPath "C:\path\to\hhy"
```

The task uses current-user limited privileges, ignores concurrent instances, and starts after the configured login delay. It does not require administrator privileges by default.

Inspect:

```powershell
.\tools\supervisor\windows\status_supervisor.ps1 -RepoPath "C:\path\to\hhy"
```

Request a safe stop:

```powershell
.\tools\supervisor\windows\stop_supervisor.ps1 -RepoPath "C:\path\to\hhy"
```

The stop marker is consumed between batches. The current batch is allowed to finish.

Uninstall the scheduled task while preserving logs:

```powershell
.\tools\supervisor\windows\uninstall_supervisor_task.ps1
```

## Runtime Evidence

Runtime files are under `governance/runtime/supervisor/` and are never authoritative:

```text
supervisor.lock
supervisor.pid
heartbeat.json
runtime_state.json
batches.jsonl
events.jsonl
SUPERVISOR_STOP_REPORT.json
SUPERVISOR_STOP_REPORT.md
PROGRAM_COMPLETE.lock
```

The operating-system lock is authoritative for single-instance detection. PID, process start time, repository path, and instance UUID are diagnostic metadata.

An interrupted `RUNNING` runtime state is recorded as `INTERRUPTED` in the event and batch logs. The next run re-reads Governance state and never assumes that the incomplete batch succeeded.

## Recovery

Read-only status:

```powershell
python tools/supervisor/supervisor_status.py --repo "C:\path\to\hhy"
```

A stop report is a durable stop condition. Do not delete it manually. After the project owner has supplied a machine-readable authorization document with `status: APPROVED` and `authorized_by: PROJECT_OWNER`, clear only the recoverable stop marker:

```powershell
python tools/supervisor/supervisor_status.py `
  --repo "C:\path\to\hhy" `
  --clear-recoverable `
  --authorization "C:\path\to\owner-authorization.json"
```

Then re-run `--dry-run` before a new `--trial`.

## First Formal Enablement

Before formal bounded automation, the owner must confirm:

1. The repository path and current-user Task Scheduler identity.
2. The `--dry-run`, `--simulate`, and one-batch/one-run trial results.
3. The external and infrastructure prerequisites required by the active Task.
4. Permission to run the default Supervisor budget.

The Supervisor does not change R14 product code. Any product change remains inside the existing Orchestrator Worker worktree and its Governance V5.0 gates.
