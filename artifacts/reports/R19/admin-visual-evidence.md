# R19 管理端运行截图证据

视觉合同来源：`design/R19-UI-FROZEN/specs/ADM-PROP-001.md`、`ADM-PROP-002.md`、`ADM-PROP-003.md`。

采集环境：R19 本地只读 fixture server，真实构建后的 Admin bundle，视口 `1440x900`。截图均通过页面渲染层采集，非静态占位；DOM 中包含 fixture 数据、状态统计、筛选和权限动作。

| Page ID | 运行路径 | 截图 | SHA-256 |
|---|---|---|---|
| ADM-PROP-001 | `/commerce/props/products?page=1&pageSize=20&sort=createdAt:desc` | `visual/admin/ADM-PROP-001-products.png` | `91192D5937C658904FF7A4E3FED2B7738FC30944C4891DD98B2633BA89CF2C5F` |
| ADM-PROP-002 | `/commerce/props/slots?page=1&pageSize=20&sort=createdAt:desc` | `visual/admin/ADM-PROP-002-slots.png` | `07B7A625B6857B8A382812682070C9F7D6E2CC77D1D98426B5112104D026C281` |
| ADM-PROP-003 | `/commerce/props/executions?page=1&pageSize=20&sort=createdAt:desc` | `visual/admin/ADM-PROP-003-executions.png` | `6429BEF47BA6B8BAC517A38CBE4B7394B6587EC567EBF584CEA737983D80847D` |

逐页对照结果：三页均满足冻结规格的标题/说明、统计、URL 筛选、服务端状态、表格密度、权限动作和错误/只读边界；未发现重叠或空白渲染。Android 三页仍需远程 Pixel 7/API 35 截图后完成对应对照。
