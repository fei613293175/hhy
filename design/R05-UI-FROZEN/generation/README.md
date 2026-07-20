# R05 UI 效果图可重复生成

## 依赖

```bash
python3 -m pip install -r requirements.txt
python3 -m playwright install chromium
```

## 生成

从最终包目录执行：

```bash
python3 EDITABLE_OVERLAY/R05-UI-FROZEN/generation/generate_r05_ui.py \
  --source REFERENCE_ONLY \
  --output ../hhy-r05-ui-regenerated
```

`--output` 必须是包外的新目录。脚本会重建该输出目录，禁止指向当前最终包、源目录或其父子目录。

生成后运行：

```bash
python3 ../hhy-r05-ui-regenerated/EDITABLE_OVERLAY/R05-UI-FROZEN/generation/validate_r05_ui.py \
  --package ../hhy-r05-ui-regenerated
```
