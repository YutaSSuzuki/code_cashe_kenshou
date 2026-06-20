# Java Code Cache Verification Documentation

JavaのJIT Code Cacheを意図的に使用するサンプルアプリケーションをEC2で実行し、
CloudWatchで使用量、ログ、異常を観測する検証プロジェクトのドキュメント入口です。

現在は設計・準備段階であり、アプリケーションとAWSリソースは未作成です。

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
