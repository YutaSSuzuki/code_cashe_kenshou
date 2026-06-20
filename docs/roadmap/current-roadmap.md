# Roadmap

## Status

Active

## Current Focus

GitHub資材からUbuntu EC2へ環境を復元し、Code Cache満杯・遅延・CloudWatch監視・容量拡張後の
遅延解消を同一条件で比較する。

## In Progress

| Item | Purpose | Completion Check |
| --- | --- | --- |
| EC2・CloudWatch検証準備 | ローカル資材と比較条件をEC2へ移行 | GitHubからcloneし14 tests成功 |

## Not Started

| Priority | Item | Purpose | Completion Check |
| --- | --- | --- | --- |
| High | EC2環境復元 | 同一commitをUbuntu EC2で実行 | build、test、health check成功 |
| High | CloudWatch Agent設定 | CodeHeapとログを収集 | CloudWatchにpool metricとwarning logが表示 |
| High | 10MB満杯・遅延再現 | Generatorでcompiler停止を再現 | full_count、Cold遅延、CloudWatch証跡を保存 |
| High | 64MB拡張比較 | 容量拡張による遅延解消を確認 | compiler enabled、Cold時間短縮を確認 |
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
