# Backup and Restore

## Status

Not Applicable

## Backup Targets

| Target | Method | Schedule | Retention |
| --- | --- | --- | --- |
| ソース・設定・文書 | Gitリポジトリ | 変更時にcommit | Git履歴に従う |
| 検証結果 | `docs/performance/test-result.md`へ記録 | 試験実施時 | Git履歴に従う |

## Backup Procedure

永続データは存在しない。再現に必要なソース、設定、試験結果をcommitする。

## Restore Procedure

リポジトリをcloneし、Deployment Operationに従って環境を再作成する。

## Restore Verification

ビルド、ヘルスチェック、CloudWatch送信を再確認する。

## Related Documents

- [Runbook](runbook.md)
