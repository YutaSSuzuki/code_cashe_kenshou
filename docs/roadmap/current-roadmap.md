# Roadmap

## Status

Active

## Current Focus

Code Cache検証は完了済み。現在の焦点は、資料の保守性改善とdocsの現在状態の整合性維持。

## In Progress

| Item | Purpose | Completion Check |
| --- | --- | --- |
| docs保守ルール見直し | 実装・検証状態と資料の乖離を防ぐ | README、Current State、docs index、policyが同じ状態を示す |

## Not Started

| Priority | Item | Purpose | Completion Check |
| --- | --- | --- | --- |
| Medium | CloudWatch Alarm作成 | Code Cache逼迫を通知 | テスト通知またはアラーム状態を確認 |

## Blocked / On Hold

| Item | Reason | Resume Condition |
| --- | --- | --- |

## Done

| Date | Item | Related Change |
| --- | --- | --- |
| 2026-06-20 | 検証用ドキュメント初期化 | Architecture、Operations、Performance文書を更新 |
| 2026-06-20 | GeneratorとHot/Cold probe実装 | 10MB Code Cache満杯、JIT停止、Cold遅延を再現 |
| 2026-06-20 | ローカル検証完了 | Code Cache満杯とレスポンス遅延を確認 |
| 2026-06-20 | EC2・CloudWatch検証完了 | Ubuntu EC2で32MB/64MB比較、CloudWatch監視、Sweeper回収を確認 |
