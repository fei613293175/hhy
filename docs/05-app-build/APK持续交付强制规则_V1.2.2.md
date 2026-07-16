# APK 持续交付强制规则 V1.2

1. `catalogs/release_plan.csv` 标记 `Android测试APK=YES` 的版本，未产生可下载APK不得关闭。
2. APK必须绑定唯一 Git Commit、versionName、versionCode、构建任务、签名指纹、文件大小、SHA256和测试报告。
3. 测试包使用独立包名和测试签名，可与正式版共存；图标必须有TEST标识。
4. APK不提交Git，上传至 `apk_release` 私有/受控下载Scope，生成短期或测试渠道下载链接。
5. 每版验收后把 Artifact Manifest 写入 `artifacts/apk/<release>/APK_MANIFEST.yaml`。
6. App前端有改动但申请APK豁免必须通过CR和用户确认；默认不允许。
7. 生产APK只能来自全绿CI、受保护Tag和生产签名Profile。
