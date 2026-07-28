# APK 持续交付强制规则 V1.2

1. `catalogs/release_plan.csv` 标记 `Android测试APK=YES` 的版本，未产生可下载APK不得关闭。
2. APK必须绑定唯一 Git Commit、versionName、versionCode、构建任务、签名指纹、文件大小、SHA256和测试报告。
3. 测试包使用独立包名和测试签名，可与正式版共存；图标必须有TEST标识。
4. APK不提交Git，上传至 `apk_release` 私有/受控下载Scope，生成短期或测试渠道下载链接。
5. 每版验收后把 Artifact Manifest 写入 `artifacts/apk/<release>/APK_MANIFEST.yaml`。
6. App前端有改动但申请APK豁免必须通过CR和用户确认；默认不允许。
7. 生产APK只能来自全绿CI、受保护Tag和生产签名Profile。
8. 每个标记 `Android测试APK=YES` 的版本，由Codex完成构建、签名、SHA256、下载校验，并将测试APK与该版本完整测试说明成对保留到项目所有者电脑桌面；R14起说明文件固定命名为`hhy-<release小写>-<Commit前7位>-test-guide.md`，Manifest、Evidence、Release Manifest、仓库说明源和桌面说明必须在路径、文件名、大小、SHA256与PASS状态上逐项一致，任一缺失或漂移必须非零失败；`Android测试APK=NO` 的版本不适用。
9. 项目所有者按自己的时间在真机执行安装和启动验收。自动构建、模拟器或服务器容器结果不能替代真机结论；未反馈时保持 `owner_physical_test=PENDING`，只阻断正式验收和生产激活，不阻断机器收尾、下一版本开发或后续桌面 `TEST_APK` 交付。

## 自动交付状态机

Android 测试 APK 使用 `scripts/deliver_android_test_apk.py` 执行三段式交付，机器交付和项目所有者真机验收不得合并或互相替代：

1. `prepare`：校验 Release、完整 Git Commit、单调 `versionCode`、固定测试签名证据和外部构建证据；随后生成规范文件名、复制仓库忽略副本和桌面副本、分片上传、远端 SHA-256/大小校验、原子发布并从 HTTPS 完整下载复核。成功后 `delivery_status=PASS`，但 `test_status` 与 `owner_physical_test` 必须保持 `PENDING`。
2. `verify`：重新校验仓库忽略副本、桌面副本、服务器文件、HTTPS 完整下载、MIME、Range 响应、文件大小和 SHA-256，不改变真机验收状态。
3. `accept`：仅在收到项目所有者明确的真机结论后执行；只有机器交付各段均为 `PASS` 且桌面 APK 未变化时，才允许把 `owner_physical_test` 和 `test_status` 从 `PENDING` 单向转换为 `PASS`。既有 `PASS` 结论不可用不同文本覆盖。

任何阶段失败均不得写成已交付或已验收。新创建的远端文件在 HTTPS 复核失败时按精确路径和 SHA-256 回撤；已存在且 SHA-256 相同的远端文件不得因本次网络失败被删除。

## 外部构建与固定签名契约

交付脚本不接收 Keystore、密码、口令、私钥文件或任意 Shell。构建系统必须在脚本外完成以下固定命令，并以 JSON 提供不含秘密的证据：

```text
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
apksigner verify --verbose --print-certs <apk>
```

构建系统通过受保护环境或 SecretRef 注入以下值；值不得写入日志或 Build Evidence：

- 固定 Staging 签名材料及其访问凭据；
- `HHY_API_BASE_URL=https://api.orbexa.cc`；
- 当前 Release、完整 Git Commit 和该 Release 对应的单调 `versionCode`。

R13 起固定测试签名 Profile 为 `hhy-staging-test-v2`，SecretRef 为 `secretref://obx-test/hhy/android/test-signing/v2`，批准证书 SHA-256 为 `e32a9d7ff8a209d2903db6383b461b259f0018a698d1f3646e6ed7f1112671be`。该引用只能在 `obx-test` 的 root-only 环境解析，秘密目录必须为 `0700`，keystore、口令文件和引用文件必须为 `0600`；任何接手者不得生成临时替代签名。R06 至 R12 的旧 Profile `hhy-staging-test-v1` 仅保留历史指纹 `f17b040789a845244ff9e2a9d8aedc1e7412adea0539d99c5cc036baf5dbb873`，因私钥不可恢复已退役。首次安装 R13 前需卸载旧测试 APK 一次，R13 及后续包必须持续复用 v2 并通过递增 `versionCode` 覆盖安装。

Build Evidence 最小格式如下。证书 SHA-256 指纹和签名 Profile ID 是可审计元数据，不是签名秘密：

```json
{
  "release": "R02",
  "commit": "<40位Git Commit>",
  "version_name": "1.2.2-debug",
  "version_code": 10202,
  "build_status": "PASS",
  "checks": ["testDebugUnitTest", "lintDebug", "assembleDebug", "apksigner"],
  "stable_signing": true,
  "signing_profile_id": "<固定Staging签名Profile ID>",
  "signing_fingerprint": "<64位证书SHA-256>",
  "api_base_url": "https://api.orbexa.cc",
  "built_at": "2026-07-18T00:00:00Z"
}
```

正式 `prepare` 缺少该证据、使用临时/default debug 签名、证据指纹与批准指纹不同、`versionCode` 低于 `10200 + R序号`、与客户端冻结 `ReleasePolicy` 不一致或不单调、API 使用 `.invalid` 域名、证据中的 Release/Commit 不一致时必须失败。P00 最低 `10200`，R01 最低 `10201`，R02 最低 `10202`；已因测试包替换递增的版本号不得回退。

## 操作命令

先执行无副作用预检；`--dry-run` 不复制文件、不连接服务器、不写 Manifest：

```powershell
python scripts/deliver_android_test_apk.py prepare `
  --release R02 `
  --commit <40位Git-Commit> `
  --version-name 1.2.2-debug `
  --version-code 10202 `
  --apk <已构建APK路径> `
  --build-evidence <Build-Evidence-JSON路径> `
  --test-guide <仓库内该版本测试说明.md或.txt> `
  --expected-signing-fingerprint <批准的64位证书SHA-256> `
  --ssh-alias <已配置SSH别名> `
  --remote-root /www/wwwroot/download.orbexa.cc `
  --dry-run
```

预检通过后去掉 `--dry-run` 执行正式交付。脚本只使用已配置的 SSH Alias/Agent，并强制 `BatchMode=yes`；禁止在命令行添加密码、IdentityFile 或口令。APK 规范命名为：

```text
hhy-<release小写>-<Commit前7位>-debug.apk
```

正式交付后可以独立复核：

```powershell
python scripts/deliver_android_test_apk.py verify `
  --release R02 `
  --ssh-alias <已配置SSH别名> `
  --remote-root /www/wwwroot/download.orbexa.cc
```

收到项目所有者明确真机反馈后，才可记录验收：

```powershell
python scripts/deliver_android_test_apk.py accept `
  --release R02 `
  --confirmation "项目所有者明确确认真机安装、启动与本版本核心路径通过" `
  --evidence-reference "<真机截图或用户反馈的受控引用>"
```

机器证据原子写入 `artifacts/validation/<release小写>-apk-delivery/delivery-evidence.json`；Artifact Manifest 原子写入 `artifacts/apk/<Release>/APK_MANIFEST.yaml`。APK 二进制继续受 `.gitignore` 排除，桌面副本必须保留给项目所有者真机测试。
