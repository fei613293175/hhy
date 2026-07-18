# B01-CAPTCHA 效果图输出与完成状态

状态：`COMPLETED_AND_FROZEN_FOR_IMPLEMENTATION`

主文件：`HHY_B01_CAPTCHA_8STATE_UI_REFERENCE.png`，`3072 × 3072 px`，4 列 × 2 行。

单状态文件位于 `states/`，每张 `1080 × 2280 px`。

## 八个面板

1. P01 初始登录页：安全验证码完全隐藏。
2. P02 点击原业务按钮后的挑战加载态。
3. P03 验证码加载成功、等待输入。
4. P04 答案错误、原位提示并换新图重试。
5. P05 验证码过期或主动刷新，旧答案清空。
6. P06 短信登录、注册和忘记密码在发送短信前触发挑战。
7. P07 验证成功，弹层关闭并自动继续原业务。
8. P08 网络失败、键盘弹出与小屏滚动适配。

## 视觉事实源

- B01 品牌与认证页整体视觉：`REFERENCE_ONLY/02_现有B01效果图/HHY_B01_8PAGE_UI_REFERENCE.png`
- V1.2.2 颜色、字号、间距、圆角、阴影和动画：`REFERENCE_ONLY/03_设计系统/hhy_design_tokens_v1.2.2.json`
- 当前真机页面结构与待移除的内联技术状态：`REFERENCE_ONLY/01_现有真机截图/`

## 实现事实源

- `EDITABLE_OVERLAY/docs/02-ui/R02安全验证码弹层交互与视觉规格_V1.2.2.md`
- `HHY_B01_CAPTCHA_MANIFEST.json`

效果图中的验证码字符、手机号、倒计时和状态文案示例仅用于视觉表达，不得硬编码。API、字段和安全规则仍以冻结开发文档为准。
