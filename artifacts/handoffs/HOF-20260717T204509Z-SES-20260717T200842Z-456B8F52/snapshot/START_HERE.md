# START HERE · V1.2.3

## 1. 本包是什么

V1.2.2 仍是产品、页面和运营施工基线；V1.2.3 在其上增加持续开发无状态接续强制门禁。项目可以不依赖任何旧对话，由完整仓库恢复任务、WIP、测试、CR、检查点和下一步。

## 2. 新 AI / 新开发者第一条命令

```bash
python3 scripts/continuity.py resume
```

严格执行输出的唯一下一命令。不要先修改文件，也不要让用户重新解释需求。

## 3. 首次仓库初始化

```bash
python3 scripts/continuity.py bootstrap   --actor <ACTOR_ID> --init-git --initial-commit   --task TASK-P00-001 --branch task/TASK-P00-001
python3 scripts/continuity.py start   --actor <ACTOR_ID> --task TASK-P00-001   --story <STORY_ID> --goal '<精确目标>'
```

## 4. 开发中

```bash
python3 scripts/continuity.py checkpoint   --summary '<完成内容>' --next-step '<精确下一步>'   --test 'name|PASS|evidence|note'
```

冻结事实变化使用 `cr-create`、`cr-amend` 和不同 Actor 的 `cr-approve`。切换 AI 前使用 `handoff`；异常中断使用 `recover`；干净项目归档使用 `export-clean`。

## 5. 门禁

```bash
python3 scripts/check_v123_continuity.py --strict
python3 scripts/continuity_gate.py --mode doctor --strict
```

代码编译、数据库、供应商、DNS、签名、压测和上线合规仍在对应开发/发布门禁完成；它们不是文档或接续协议缺口。
