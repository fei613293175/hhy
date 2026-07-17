import { spawnSync } from "node:child_process";

const [, , script, ...scriptArguments] = process.argv;
if (!script) {
  console.error("usage: node scripts/run-python.mjs <script> [...args]");
  process.exit(2);
}

const candidates = [];
if (process.env.HHY_PYTHON) {
  candidates.push([process.env.HHY_PYTHON, []]);
}
if (process.platform === "win32") {
  candidates.push(["py", ["-3"]]);
}
candidates.push(["python3", []], ["python", []]);

for (const [executable, prefix] of candidates) {
  const result = spawnSync(executable, [...prefix, script, ...scriptArguments], {
    cwd: process.cwd(),
    env: process.env,
    stdio: "inherit",
    windowsHide: true,
  });
  if (result.error?.code === "ENOENT") {
    continue;
  }
  if (result.error) {
    console.error(`${executable}: ${result.error.message}`);
    process.exit(1);
  }
  process.exit(result.status ?? 1);
}

console.error("Python 3 was not found. Set HHY_PYTHON to an explicit interpreter path.");
process.exit(127);
