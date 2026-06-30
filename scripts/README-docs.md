# scripts/docs.ts

AegisOps 文档治理工具：扫描 `docs/` 目录下所有 markdown，校验 YAML frontmatter
一致性，生成 `docs/INDEX.md`。

**单一职责**：文档元数据治理。不读源码、不调用 Docker、不修改任何非 `docs/`
路径下的文件。

## 用法

```bash
# 显式调用（需要仓库根目录）
npx tsx scripts/docs.ts <subcommand>

# 或经 package.json scripts 间接调用
pnpm ci:docs
```

## 子命令

| 子命令                                                                 | 作用                                                                                                        | 退出码      |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ----------- |
| `check`                                                                | 扫描所有 `docs/**/*.md`，校验 frontmatter 必填字段、type / status 枚举、related 是否存在；不通过即非 0 退出 | 0 绿 / 1 红 |
| `index`                                                                | 重新生成 `docs/INDEX.md`，按 type + title 排序                                                              | 0 绿 / 1 红 |
| `new <rel-path> --type=... --phase=... [--title=...] [--status=draft]` | 按模板生成新文档 frontmatter 骨架                                                                           | 0 绿 / 1 红 |
| `init`                                                                 | 在仓库内首次创建 `docs/INDEX.md` 模板                                                                       | 0 绿 / 1 红 |
| `help`                                                                 | 打印 usage                                                                                                  | 0           |

`check` 是 CI 守门（见 `.github/workflows/ci.yml` 与 `scripts/ci/docs.sh`），其他
子命令主要供本地手动使用。

## 校验规则（frontmatter）

| 字段      | 必填 | 枚举                                                                                                                      |
| --------- | ---- | ------------------------------------------------------------------------------------------------------------------------- |
| `title`   | ✓    | 自由文本，≤ 80 字                                                                                                         |
| `type`    | ✓    | `architecture` `adr` `phase` `design` `review` `fix` `api` `database` `integration` `ai` `operation` `runbook` `research` |
| `status`  | ✓    | `draft` `review` `accepted` `deprecated` `superseded`                                                                     |
| `phase`   | ✓    | 自由文本（推荐 SKILL §14 Phase 0~6）                                                                                      |
| `owner`   | ✓    | 自由文本                                                                                                                  |
| `created` | ✓    | `YYYY-MM-DD`                                                                                                              |
| `updated` | ✓    | `YYYY-MM-DD`                                                                                                              |
| `related` | ✗    | 路径数组，指向仓库内已存在文件；缺文件则 warn                                                                             |

## 边界（不做什么）

- **不读源码**：不 import 任何 `apps/` `modules/` `web/` 路径
- **不写非 docs 文件**：唯一副作用是 `docs/INDEX.md`
- **不调用 Docker / Maven / pnpm**
- **不依赖 start.ts**：两个脚本独立维护，互不 import

## 故障排查

| 现象                                 | 原因                                 | 修复                           |
| ------------------------------------ | ------------------------------------ | ------------------------------ |
| `check` 报 `unknown type`            | frontmatter `type` 不在枚举内        | 改为枚举值或更新 `VALID_TYPES` |
| `check` 报 `related path not found`  | `related` 数组里的文件不存在         | 修正路径或删除该条             |
| `index` 输出的排序异常               | 文档 frontmatter 缺 `type` / `title` | 先 `check` 修复                |
| `npx tsx scripts/docs.ts` 找不到 tsx | 未安装 dev dep                       | `pnpm install`                 |
