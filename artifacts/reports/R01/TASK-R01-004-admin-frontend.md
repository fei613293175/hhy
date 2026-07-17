# TASK-R01-004 · 管理员认证与个人安全前端证据

- Session：`SES-20260717T111928Z-C383F7A2`
- Stories：`STORY-R01-001`、`STORY-R01-002`
- 页面：`ADM-AUTH-001`、`ADM-AUTH-002`、`ADM-SECURITY-001`
- 冻结 operationId：8/8 已通过 `AdminContract.operations` 生成类型绑定

## 页面状态证据

| 页面 | 状态 | 实现/证据 |
| --- | --- | --- |
| ADM-AUTH-001 | EDITING / SUBMITTING / SUCCESS / MFA_REQUIRED | 表单本地校验、提交锁、成功按安全同源 redirect 导航、MFA 分支进入 `/auth/mfa` |
| ADM-AUTH-001 | ERROR / RATE_LIMITED / LOCKED | 结构化错误与 requestId；Retry-After 倒计时期间禁止提交；真实后端 422 `管理员账号已受限` 映射终态锁定 |
| ADM-AUTH-001 | OFFLINE / NO_PERMISSION | 离线禁用写入；403 清除密码并隐藏认证表单 |
| ADM-AUTH-002 | EDITING / SUBMITTING / MFA_REQUIRED / SUCCESS | 内存 MFA ticket 守卫、验证码校验、提交锁与会话升级导航 |
| ADM-AUTH-002 | ERROR / RATE_LIMITED / LOCKED | 字段级错误、requestId、限流倒计时与锁定状态禁止重复提交 |
| ADM-AUTH-002 | OFFLINE / NO_PERMISSION | 离线禁用验证；403 清除验证码并只保留安全返回登录 |
| ADM-SECURITY-001 | LOADING / CONTENT | 首屏骨架；安全事实、可选字段占位、未绑定 MFA 空业务态和显式刷新 |
| ADM-SECURITY-001 | NO_PERMISSION / ERROR / OFFLINE | 403 全页无权限且隐藏事实；错误重试；离线清除高敏事实并禁用所有 POST |
| ADM-SECURITY-001 | 局部失败 / 409 / 422 | 写失败保留已加载事实；409 提示并重取最新事实；422 字段错误就地展示且非敏感原因保留 |

`PENDING_APPROVAL` 对当前 R01 冻结 8 接口明确为 N/A：请求和响应模型均无审批单、审批状态或待审批资源，后端 R01 服务全部同步返回安全事实/命令结果。前端不伪造审批状态；后续只有在批准的契约新增审批信号后接入。`GET /me/security` 的 404 在冻结页面动作规格中同样明确为不适用，自身安全资源使用 401/403/错误态收口。

## 安全与交互证据

- 生产源码不使用 `localStorage`、`sessionStorage` 或 Cookie 保存管理员凭证。
- 同一请求体网络重试复用幂等键；请求体变化或成功完成后生成新键。
- GET 安全概览仅对网络瞬断指数退避重试 2 次，403 等业务错误不自动重试。
- 登录失败、MFA 失败、权限撤销、关闭弹窗、改密成功、退出和刷新均验证敏感输入/内存会话清理。
- 全部目录型后台路由需要内存认证会话；匿名访问保存同源返回路径后进入登录。
- CRITICAL 弹窗具备 accessible name、打开移焦、Tab/Shift+Tab 焦点圈定、Escape 关闭和关闭还焦。
- 本地真实浏览器完成桌面和 390px 检查：普通登录、安全中心、MFA 绑定/确认、退出、MFA 登录、刷新失效，无横向溢出和控制台错误。

## 自动化

- `pnpm --filter @hhy/admin-web test`
- `pnpm --filter @hhy/admin-web build`
- `node --check scripts/run_r01_admin_web_mock.mjs`
- `git diff --check`

最终命令结果和精确测试数量记录在本 Session 最终 checkpoint 与关闭记录中。
