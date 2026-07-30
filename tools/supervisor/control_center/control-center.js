(() => {
  const $ = (id) => document.getElementById(id);
  let currentConfig = null;
  let activeLogName = "events";

  const escapeHtml = (value) => String(value ?? "-")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

  const toast = (message, error = false) => {
    const node = $("toast");
    node.textContent = message;
    node.className = `toast show${error ? " error" : ""}`;
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => { node.className = "toast"; }, 4200);
  };

  const statusClass = (value) => {
    if (!value) return "neutral";
    if (["RUNNING", "PASS", "READY", "ACTIVE", "TASK_DONE", "检查通过", "正在运行"].includes(value)) return "good";
    if (["STOPPED", "INTERRUPTED", "DRY_RUN", "MAX_RUNS_REACHED", "NEXT_TASK_READY"].includes(value)) return "warn";
    if (["FAILED_BOUNDED", "PROGRAM_COMPLETE", "UNKNOWN_STATUS", "DOCTOR_FAILED", "WORKTREE_UNSAFE", "NO_PROGRESS_DETECTED"].includes(value)) return "stop";
    return "neutral";
  };

  const setBadge = (id, value) => {
    const node = $(id);
    node.textContent = value || "-";
    node.className = `status-badge ${statusClass(value)}`;
  };

  const formatValue = (value) => {
    if (value === null || value === undefined || value === "") return "-";
    if (typeof value === "boolean") return value ? "是" : "否";
    return String(value);
  };

  const formatTime = (value) => {
    if (!value || value === "-") return "-";
    return String(value).replace("T", " ").replace("Z", "");
  };

  const humanStopReason = (report) => {
    if (!report || !report.stop_status) return "-";
    if (report.stop_status === "WORKTREE_UNSAFE") {
      return "仓库有未提交改动，治理系统为保护项目安全拒绝启动。先提交或整理控制中心文件，再重新 Dry-run。";
    }
    if (report.stop_status === "DOCTOR_FAILED") return "环境检查未通过，暂时不能启动真实开发。";
    if (report.stop_status === "OWNER_ACTION_REQUIRED") return "需要项目所有者处理，当前不会自动恢复。";
    if (report.stop_status === "UNKNOWN_STATUS") return "治理系统返回了未知结果，已按安全规则停止。";
    return report.resolution || report.stop_status;
  };

  const renderStatus = (payload) => {
    $("repo-path").textContent = payload.repo || "仓库路径未知";
    $("connection-state").textContent = "已连接";
    const runtime = payload.runtime || {};
    const runtimeState = runtime.state || {};
    const project = payload.governance?.project || {};
    const task = payload.governance?.task || {};
    const supervisorStatus = runtimeState.status || (runtime.pid_alive ? "RUNNING" : "STOPPED");
    setBadge("task-badge", task.status || project.status);
    setBadge("stop-badge", runtime.stop_report?.stop_status || "无报告");
    $("supervisor-status").textContent = supervisorStatus === "RUNNING" ? "运行中" : "已停止";
    $("supervisor-detail").textContent = runtime.pid_alive ? `PID ${runtime.pid?.pid || "-"}` : "当前无活动进程";
    $("release").textContent = project.active_release || "-";
    $("release-status").textContent = project.status || "-";
    $("task").textContent = project.active_task || "无活动任务";
    $("task-status").textContent = task.status || "-";
    $("revision").textContent = project.revision || runtimeState.state_revision || "-";
    $("attempt").textContent = `Attempt ${task.attempts_used ?? "-"}/3`;
    const scheduler = payload.task_scheduler || {};
    $("scheduled-task").textContent = scheduler.installed ? (scheduler.state || "已安装") : "未安装";
    $("scheduled-detail").textContent = scheduler.installed ? `LastResult ${scheduler.last_result ?? "-"}` : (scheduler.status || "-");
    $("task-title").textContent = task.title || "当前任务";
    $("task-id").textContent = project.active_task || "-";
    $("task-release").textContent = task.release || project.active_release || "-";
    $("task-objective").textContent = task.objective || "-";
    $("heartbeat").textContent = formatTime(runtime.heartbeat?.at);
    $("authority-commit").textContent = project.authoritative_commit || "-";
    const report = runtime.stop_report || {};
    $("stopped-at").textContent = formatTime(report.stopped_at);
    $("stop-status").textContent = report.stop_status || "-";
    $("auto-continue").textContent = formatValue(report.allow_auto_continue);
    $("resolution").textContent = humanStopReason(report);
    const config = payload.configuration || {};
    $("config-status").textContent = config.status === "PASS" ? "正常" : "异常";
    $("config-detail").textContent = config.status === "PASS" ? "下次启动生效" : (config.error || "无法读取");
    if (payload.state_error) toast(`状态读取失败：${payload.state_error}`, true);
  };

  const renderConfig = (payload) => {
    currentConfig = payload;
    const host = $("config-form");
    if (!payload || payload.status !== "PASS") {
      host.innerHTML = `<div class="timeline-empty">${escapeHtml(payload?.error || "配置读取失败")}</div>`;
      return;
    }
    const groups = {};
    (payload.editable || []).forEach((field) => {
      groups[field.group] ??= { label: field.group_label, fields: [] };
      groups[field.group].fields.push(field);
    });
    host.innerHTML = Object.entries(groups).map(([group, value]) => {
      const fields = value.fields.map((field) => {
        const configValue = payload.values?.[group]?.[field.key];
        const id = `config-${group}-${field.key}`;
        if (field.type === "boolean") {
          return `<div class="config-field">
            <div><label for="${id}">${escapeHtml(field.label)}</label><small>开启后由 Windows 任务计划负责启动</small></div>
            <div class="config-input config-check"><input id="${id}" type="checkbox" data-config-field data-group="${group}" data-key="${field.key}" data-type="${field.type}" ${configValue ? "checked" : ""}></div>
          </div>`;
        }
        const step = field.type === "number" ? "0.25" : "1";
        return `<div class="config-field">
          <div><label for="${id}">${escapeHtml(field.label)}</label><small>范围 ${field.min} - ${field.max} ${escapeHtml(field.unit || "")}</small></div>
          <div class="config-input"><input id="${id}" type="number" min="${field.min}" max="${field.max}" step="${step}" value="${escapeHtml(configValue)}" data-config-field data-group="${group}" data-key="${field.key}" data-type="${field.type}"><span>${escapeHtml(field.unit || "")}</span></div>
        </div>`;
      }).join("");
      return `<section class="config-group"><h3>${escapeHtml(value.label)}</h3>${fields}</section>`;
    }).join("");
  };

  const collectConfig = () => {
    const values = {};
    document.querySelectorAll("[data-config-field]").forEach((input) => {
      const group = input.dataset.group;
      const key = input.dataset.key;
      values[group] ??= {};
      if (input.dataset.type === "boolean") values[group][key] = input.checked;
      else values[group][key] = input.dataset.type === "integer" ? Number.parseInt(input.value, 10) : Number.parseFloat(input.value);
    });
    return values;
  };

  const loadConfig = async () => {
    try {
      const response = await fetch("/api/status", { cache: "no-store" });
      const payload = await response.json();
      renderConfig(payload.configuration);
    } catch (error) {
      renderConfig({ status: "FAIL", error: error.message });
    }
  };

  const renderLog = (payload) => {
    const summary = payload.summary || {};
    $("log-summary").innerHTML = `
      <div class="summary-box"><strong>${escapeHtml(summary.count ?? 0)}</strong><span>条记录</span></div>
      <div class="summary-box"><strong>${escapeHtml(summary.attention_count ?? 0)}</strong><span>需要关注</span></div>
      <div class="summary-box"><strong>${escapeHtml(summary.headline || "暂无运行记录")}</strong><span>最新结论</span></div>`;
    const items = payload.items || [];
    if (!items.length) {
      $("log-view").innerHTML = `<div class="timeline-empty">当前没有${activeLogName === "stop-report" ? "停止报告" : "运行记录"}。</div>`;
      return;
    }
    $("log-view").innerHTML = items.slice().reverse().map((item) => {
      const actions = (item.actions || []).map((action) => `<div>处理建议：${escapeHtml(action)}</div>`).join("");
      return `<article class="timeline-item ${escapeHtml(item.level || "info")}">
        <span class="timeline-mark"></span>
        <div><div class="timeline-title">${escapeHtml(item.title)}</div><div class="timeline-detail">${escapeHtml(item.detail)}</div>${actions ? `<div class="timeline-actions">${actions}</div>` : ""}</div>
        <time class="timeline-time">${escapeHtml(formatTime(item.at))}</time>
      </article>`;
    }).join("");
  };

  const renderChat = (payload) => {
    $("chat-notice").textContent = payload.notice || "这里显示 Supervisor、治理控制器和安全守卫的过程消息。";
    $("chat-mode").textContent = payload.mode === "lifecycle-observer" ? "观摩模式" : "实时状态";
    const messages = payload.messages || [];
    if (!messages.length) {
      $("chat-view").innerHTML = `<div class="timeline-empty">暂时没有开发过程消息。</div>`;
      return;
    }
    $("chat-view").innerHTML = messages.slice().reverse().map((message) => `
      <article class="chat-message ${escapeHtml(message.level || "info")}">
        <div class="chat-avatar">${escapeHtml(message.role || "控制中心")}</div>
        <div class="chat-content">
          <div class="chat-title">${escapeHtml(message.title)}</div>
          <div class="chat-detail">${escapeHtml(message.detail)}</div>
          <div class="chat-time">${escapeHtml(formatTime(message.at))}</div>
        </div>
      </article>
    `).join("");
  };

  const loadChat = async () => {
    try {
      const response = await fetch("/api/chat", { cache: "no-store" });
      const payload = await response.json();
      if (!response.ok || payload.status === "FAIL") throw new Error(payload.error || `HTTP ${response.status}`);
      renderChat(payload);
    } catch (error) {
      $("chat-view").innerHTML = `<div class="timeline-empty">${escapeHtml(error.message)}</div>`;
    }
  };

  const loadLog = async () => {
    activeLogName = $("log-select").value;
    try {
      const response = await fetch(`/api/logs?name=${encodeURIComponent(activeLogName)}&tail=200`, { cache: "no-store" });
      const payload = await response.json();
      if (!response.ok || payload.status === "FAIL") throw new Error(payload.error || `HTTP ${response.status}`);
      renderLog(payload);
    } catch (error) {
      $("log-view").innerHTML = `<div class="timeline-empty">${escapeHtml(error.message)}</div>`;
      toast(`运行日志读取失败：${error.message}`, true);
    }
  };

  const renderActionResult = (payload) => {
    const friendly = payload.friendly || {
      title: payload.status === "FAIL" ? "操作未完成" : "操作已完成",
      detail: payload.error || payload.status || "请刷新状态确认结果。",
      level: payload.status === "FAIL" ? "stop" : "info",
    };
    renderLog({
      summary: { count: 1, attention_count: friendly.level === "stop" ? 1 : 0, headline: friendly.title },
      items: [{ ...friendly, at: new Date().toISOString(), actions: [] }],
    });
  };

  const action = async (name) => {
    let confirmed = false;
    if (["trial", "start", "install-task", "uninstall-task", "clear-recoverable"].includes(name)) {
      const labels = {
        trial: "确认启动一次、最多 1 批 1 次的有限试运行？",
        start: "确认启动正式 Supervisor？这会调用现有 run-loop。",
        "install-task": "确认安装 Windows 登录自动启动任务？",
        "uninstall-task": "确认卸载 Windows 登录自动启动任务？",
        "clear-recoverable": "确认使用项目所有者授权文件清除可恢复停止？",
      };
      confirmed = window.confirm(labels[name]);
      if (!confirmed) return;
    }
    const body = { action: name, confirmed };
    if (name === "clear-recoverable") body.authorization = $("authorization").value.trim();
    try {
      const response = await fetch("/api/action", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      const payload = await response.json();
      const failed = payload.status === "FAIL";
      toast(`${name}: ${payload.status || "UNKNOWN"}`, failed);
      renderActionResult(payload);
      await refresh();
      if (name === "dry-run" || name === "simulate") await loadLog();
    } catch (error) {
      toast(`操作失败：${error.message}`, true);
    }
  };

  const saveConfig = async () => {
    if (!currentConfig || currentConfig.status !== "PASS") {
      toast("配置尚未读取完成", true);
      return;
    }
    if (!window.confirm("确认保存这些运行配置？它们将在下一次启动 Supervisor 时生效。")) return;
    try {
      const response = await fetch("/api/config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ values: collectConfig() }),
      });
      const payload = await response.json();
      if (!response.ok || payload.status === "FAIL") throw new Error(payload.error || "配置保存失败");
      renderConfig(payload);
      toast("配置已保存，下次启动 Supervisor 生效");
      await refresh();
    } catch (error) {
      toast(`配置保存失败：${error.message}`, true);
    }
  };

  const refresh = async () => {
    try {
      const response = await fetch("/api/status", { cache: "no-store" });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      renderStatus(payload);
      if (!currentConfig) renderConfig(payload.configuration);
    } catch (error) {
      $("connection-state").textContent = "连接失败";
      toast(`控制中心连接失败：${error.message}`, true);
    }
  };

  document.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", () => {
      const name = button.dataset.action;
      if (name === "refresh") refresh();
      else if (name === "load-log") loadLog();
      else if (name === "reload-config") loadConfig();
      else if (name === "save-config") saveConfig();
      else action(name);
    });
  });
  $("log-select").addEventListener("change", loadLog);

  refresh();
  loadChat();
  loadLog();
  window.setInterval(refresh, 5000);
  window.setInterval(loadChat, 2000);
  window.setInterval(loadLog, 10000);
})();
