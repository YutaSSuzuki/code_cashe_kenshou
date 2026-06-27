# Java Code Cache Verification Documentation

JavaのJIT Code Cacheを意図的に使用するサンプルアプリケーションをEC2で実行し、
CloudWatchで使用量、ログ、異常を観測する検証プロジェクトのドキュメント入口です。

現在の検証は完了済みです。実装、ローカル確認、Ubuntu EC2での再現、CloudWatch Agentによる
Code Cacheメトリクス監視まで確認済みです。

## How to Read

目的別に次の順で確認します。

| Want to know | Read |
| --- | --- |
| いま何ができているか | [Current State](architecture/current-state.md) |
| ローカルで起動・実装確認したい | [Local Development](operations/local-development.md) |
| APIとJavaコードの対応を知りたい | [API Specification](api/index.md)、[Runtime Flow](architecture/runtime-flow.md) |
| Code Cacheを満杯にする手順を知りたい | [Code Cache Overflow Test](performance/code-cache-overflow-test.md) |
| EC2で再現したい | [Deployment Operation](operations/deployment.md)、[EC2 and CloudWatch Verification](performance/ec2-cloudwatch-verification.md) |
| CloudWatch Agentの設定を知りたい | [Monitoring](operations/monitoring.md) |
| 検証結果を見たい | [Test Result](performance/test-result.md) |
| なぜこの設計にしたか | [ADR](adr/index.md) |

## Documentation Areas

`docs/`配下の各ディレクトリは、次の役割で分けます。

| Area | Role | Example |
| --- | --- | --- |
| `architecture/` | 現在の構成、システム境界、実行時の流れを書く。実施手順ではなく「どういう作りか」を残す | EC2、CloudWatch Agent、JMX、Spring ControllerからServiceへの流れ |
| `api/` | HTTP API、入力値、レスポンス、対応するJavaクラスを書く | `/load`、`/code-cache/generator/generate` |
| `operations/` | 人が環境を作る、起動する、監視する、障害時に見る手順を書く | ローカル起動、EC2構築、CloudWatch Agent設定、Runbook |
| `performance/` | 性能・負荷・Code Cache検証の計画、条件、結果、分析を書く | 32MB/64MB比較、Hot/Cold遅延、`jcmd`結果 |
| `adr/` | 採用した設計判断と理由を書く。後から変わっても履歴として残す | Byte Buddyで動的class生成する判断 |
| `proposals/` | これから行う案を書く。完了後はCurrent State、ADR、Changelogへ反映する | 検証環境案 |
| `roadmap/` | 今やること、未着手、完了を短く管理する | EC2検証完了、次の改善候補 |
| `changelog/` | 完了した変更を日付ごとに記録する | 実装、docs更新、検証完了 |
| `database/` | DBを使う場合のschema、ER、migrationを書く。現在のCode Cache検証では実質未使用 | 将来DBを追加した場合 |

## Update Rule

コード、検証条件、実行結果、AWS設定を変更したら、同じ作業内で対応するdocsも更新します。
「資料を最新化して」と別途依頼しなくても、実装・検証の完了条件にdocs更新を含めます。

最低限、次を確認します。

| Change | Required docs |
| --- | --- |
| 現在の実装状態が変わった | `README.md`、`docs/architecture/current-state.md` |
| 起動方法、EC2構築、CloudWatch設定が変わった | `docs/operations/` |
| APIやJavaコードの呼び出し関係が変わった | `docs/api/`、`docs/architecture/runtime-flow.md` |
| 検証条件や結果が変わった | `docs/performance/` |
| 設計判断を変えた | `docs/adr/` |
| 作業状態が変わった | `docs/roadmap/current-roadmap.md`、`docs/changelog/CHANGELOG.md` |

## Start Here

- [Documentation Policy](documentation-policy.md)
- Architecture
  - [Current State](architecture/current-state.md)
  - [System Context](architecture/system-context.md)
  - [Runtime Flow](architecture/runtime-flow.md)
  - [Deployment Architecture](architecture/deployment.md)
- API
  - [Specification](api/index.md)
  - [Load API Implementation Guide](api/load-api-implementation.md)
  - [Code Cache Test API](api/code-cache-test-api.md)
- Database
  - [Schema](database/schema.md)
  - [ER Diagram](database/er-diagram.md)
  - [Migration](database/migration.md)
- Operations
  - [Local Development](operations/local-development.md)
  - [Runbook](operations/runbook.md)
  - [Deployment](operations/deployment.md)
  - [Backup and Restore](operations/backup.md)
  - [Monitoring](operations/monitoring.md)
  - [Troubleshooting](operations/troubleshooting.md)
- Performance
  - [Current Performance](performance/current-performance.md)
  - [Test Plan](performance/test-plan.md)
  - [Code Cache Overflow Test](performance/code-cache-overflow-test.md)
  - [EC2 and CloudWatch Verification](performance/ec2-cloudwatch-verification.md)
  - [Test Result](performance/test-result.md)
  - [Bottleneck Analysis](performance/bottleneck-analysis.md)
- [Roadmap](roadmap/current-roadmap.md)
- [Changelog](changelog/CHANGELOG.md)

## History and Future Work

- [ADR](adr/index.md)
- [Proposal](proposals/index.md)

Current State文書には現在の状態だけを書き、過去の判断はADR、未実装の構想はProposal、実施済み変更はChangelogへ分ける。

## Verification Goal

- ローカル環境でCode Cacheの増加を`jcmd`により確認できる。
- EC2上で同じサンプルを再現できる。
- CloudWatchでCodeHeapメモリプールとアプリケーションログを確認できる。
- Code Cache逼迫時の兆候と対処方法をRunbookに残す。
