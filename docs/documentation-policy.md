# Docs as Code Operation Policy

## Premise

This project treats the future maintainer as a different developer, even when it is a solo project.

Documentation must preserve current state, decision context, unfinished work, and completed changes.

## Basic Policy

- Use Markdown for all project documentation.
- Manage documentation in Git with the code.
- Prefer Mermaid for diagrams.
- Keep Current State documents up to date.
- Do not keep historical architecture in Current State documents.
- Preserve decision history in ADRs.
- Preserve future ideas in Proposals.
- Preserve completed changes in Changelog.
- Treat documentation updates as part of implementation and verification completion.
- Do not wait for a separate request such as "update the docs" when code, runtime options, AWS settings, or verification results change.

## Directory Structure

```text
docs/
├── index.md
├── documentation-policy.md
├── architecture/     Current State, context, runtime, deployment
├── api/              endpoints and feature flows
├── database/         schema, ER diagram, migration
├── operations/       runbook, deployment, backup, monitoring
├── performance/      current state, plans, results, analysis
├── adr/              permanent decision records
├── proposals/        changes under consideration
├── roadmap/          current development status
└── changelog/        completed changes
```

## Directory Roles

| Directory | Role | Do not put here |
| --- | --- | --- |
| `architecture/` | 現在の構成、境界、依存関係、実行時の流れ | 手順の詳細、過去の案 |
| `api/` | HTTP API、入力、出力、Controller/Service対応 | EC2構築手順、性能結果 |
| `operations/` | 人が実行する構築・起動・監視・障害対応手順 | 性能評価の結論、設計判断の履歴 |
| `performance/` | 性能検証の条件、手順、結果、分析 | 一般的な起動手順だけの説明 |
| `adr/` | 決定済みの設計判断と理由 | 日々の進捗、未確定の案 |
| `proposals/` | 未実装・検討中の案 | 実装済みの現在状態 |
| `roadmap/` | 現在の作業状態、未着手、完了 | 詳細な手順や長い検証ログ |
| `changelog/` | 完了した変更の履歴 | 未完了の予定 |
| `database/` | DBを使う場合のschema、ER、migration | DBを使わない機能の説明 |

## Update Rules

| Change | Update |
| --- | --- |
| System architecture | `architecture/`、必要に応じて`README.md` |
| API | `api/`、`architecture/runtime-flow.md` |
| Database | `database/` |
| Operation procedure | `operations/` |
| Performance condition or result | `performance/` |
| Runtime option or AWS setting | `operations/`、`performance/` |
| Design decision | `adr/` |
| Future plan | `proposals/` |
| Development status | `roadmap/` |
| Completed change | `changelog/` |

## Workflow

1. Check roadmap, proposals, architecture, database, and API docs before work.
2. Create or update a Proposal before large feature, design, database, or infrastructure changes.
3. Update code, scripts, configuration, and the corresponding docs in the same work unit.
4. Update Current State documents during implementation when the actual state changes.
5. After implementation or verification, update ADR, roadmap, changelog, and performance results when needed.
6. Move completed Proposals to `proposals/done/` or delete them when no longer useful.

## Completion Checklist

作業完了前に次を確認する。

- `README.md`のCurrent StatusとdocsのCurrent Stateが矛盾していない。
- `docs/index.md`から新規・更新した資料へ辿れる。
- 実行コマンドを変えた場合、`operations/`または`performance/`に反映している。
- APIやJavaクラスを変えた場合、`api/`と`runtime-flow.md`に反映している。
- 検証結果を得た場合、条件、コマンド、出力、判断を`performance/`に残している。
- 完了した変更を`changelog/CHANGELOG.md`へ記録している。

## Initial Setup Rule

The repository already contains the standard application and documentation structure.

- Do not create alternative top-level source or docs directories without a recorded reason.
- Update existing Current State files before adding another document.
- Split a feature into a new document only when the existing document becomes difficult to navigate.
- Add new ADR and Proposal files when a new decision or proposal must have its own lifecycle.
