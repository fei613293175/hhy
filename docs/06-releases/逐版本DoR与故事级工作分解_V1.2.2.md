# V1.2.2 逐版本开发就绪门禁与故事级工作分解

V1.2.2 将 P00—R32 拆分为 **162 个可领取故事**，并为 33 个版本定义 **396 项 DoR**。

## DoR 与退出验收的区别

- DoR 判断“开发输入是否完整”：页面、字段、状态、动作、API、配置、数据、权限、测试和故事是否可以直接施工。
- Release Exit Gate 判断“软件是否实现并验证”：编译、运行、迁移、供应商联调、性能、安全、APK和发布证据在开发过程中完成。
- Android SDK、PostgreSQL、Docker、供应商沙箱、DNS、签名和压测不再被误判为开发前文档缺口。

## 使用方法

1. 进入版本前读取 `releases/<release>/DEFINITION_OF_READY.yaml`；适用项必须全部 PASS。
2. 从 `releases/<release>/STORIES.yaml` 领取页面故事或工程治理故事。
3. 页面故事必须引用逐页施工文档；工程治理故事负责契约、数据、配置、测试、观测与交接。
4. 任何事实变化先创建 CR，再修改权威目录并重新生成派生文件。

## 版本概览

| 版本 | 主题 | 需求 | 页面/交互面 | 故事 | DoR | 依赖 |
| --- | --- | --- | --- | --- | --- | --- |
| P00 | 仓库、环境、契约与无状态接续 | 14 | 0 | 1 | PASS_DOCUMENTATION_READY | 无 |
| R01 | Design System与契约工程化 | 13 | 3 | 3 | PASS_DOCUMENTATION_READY | P00 |
| R02 | 账号、登录、注册与安全会话 | 6 | 14 | 9 | PASS_DOCUMENTATION_READY | R01 |
| R03 | 供应商配置、密钥、证书与域名中心 | 5 | 7 | 4 | PASS_DOCUMENTATION_READY | R01 |
| R04 | 媒体与多对象存储 | 2 | 1 | 2 | PASS_DOCUMENTATION_READY | R03 |
| R05 | 单一实名认证 | 2 | 7 | 8 | PASS_DOCUMENTATION_READY | R02;R04 |
| R06 | 统一内容基础、首页与CMS | 2 | 5 | 5 | PASS_DOCUMENTATION_READY | R02;R04 |
| R07 | 搜索、发布者主页与联系方式保护 | 3 | 5 | 5 | PASS_DOCUMENTATION_READY | R06 |
| R08 | 项目推广完整闭环 | 2 | 3 | 4 | PASS_DOCUMENTATION_READY | R05;R06;R07 |
| R09 | App推广完整闭环 | 2 | 3 | 4 | PASS_DOCUMENTATION_READY | R05;R06;R07 |
| R10 | 群聊推广完整闭环 | 2 | 3 | 4 | PASS_DOCUMENTATION_READY | R05;R06;R07 |
| R11 | 团队长入驻完整闭环 | 2 | 3 | 4 | PASS_DOCUMENTATION_READY | R05;R06;R07 |
| R12 | 统一发布与发布管理 | 2 | 11 | 8 | PASS_DOCUMENTATION_READY | R08;R09;R10;R11 |
| R13 | 收藏、历史、分享与行为审计 | 2 | 4 | 3 | PASS_DOCUMENTATION_READY | R07;R12 |
| R14 | 一对一聊天核心 | 2 | 6 | 4 | PASS_DOCUMENTATION_READY | R02;R04;R06 |
| R15 | 通知、公告、举报与客服 | 4 | 19 | 14 | PASS_DOCUMENTATION_READY | R14 |
| R16 | 商品、SKU、报价与统一订单 | 3 | 3 | 4 | PASS_DOCUMENTATION_READY | R02;R06 |
| R17 | 支付抽象与H5统一收银台 | 3 | 4 | 4 | PASS_DOCUMENTATION_READY | R03;R16 |
| R18 | Pro会员与补差升级 | 3 | 6 | 4 | PASS_DOCUMENTATION_READY | R17 |
| R19 | 道具与曝光层级 | 2 | 6 | 4 | PASS_DOCUMENTATION_READY | R17;R12 |
| R20 | 红包活动创建与预审核 | 3 | 6 | 7 | PASS_DOCUMENTATION_READY | R05;R12;R17 |
| R21 | 红包付款、加价与暂停 | 2 | 2 | 3 | PASS_DOCUMENTATION_READY | R20 |
| R22 | 红包20秒有效浏览与领取 | 1 | 4 | 4 | PASS_DOCUMENTATION_READY | R21 |
| R23 | 红包账本、数据与反作弊 | 2 | 3 | 4 | PASS_DOCUMENTATION_READY | R22 |
| R24 | 奖励账户与支付宝提现 | 4 | 11 | 8 | PASS_DOCUMENTATION_READY | R05;R17;R23 |
| R25 | 二级消费佣金 | 3 | 9 | 7 | PASS_DOCUMENTATION_READY | R17;R24 |
| R26 | 直推活跃阶梯奖励 | 2 | 5 | 5 | PASS_DOCUMENTATION_READY | R25 |
| R27 | 个人任务与留存 | 3 | 4 | 4 | PASS_DOCUMENTATION_READY | R24 |
| R28 | H5品牌宣传、邀请与下载 | 3 | 10 | 4 | PASS_DOCUMENTATION_READY | R06;R13;R15 |
| R29 | 官方App自助构建与版本发布中心 | 3 | 8 | 5 | PASS_DOCUMENTATION_READY | P00;R17;R28 |
| R30 | 完整运营后台、RBAC与配置闭环 | 12 | 10 | 8 | PASS_DOCUMENTATION_READY | R01;R23;R24;R28;R29 |
| R31 | 安全、性能、备份与灾备验收 | 4 | 4 | 4 | PASS_DOCUMENTATION_READY | R30 |
| R32 | 最终供应商接入、生产发布与交接 | 8 | 0 | 1 | PASS_DOCUMENTATION_READY | R31 |