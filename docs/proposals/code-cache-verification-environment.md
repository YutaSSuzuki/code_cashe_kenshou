# Proposal: Java Code Cache Verification Environment

## Status

Approved

## Background

Java HotSpot JVMのCode Cache使用量、JITコンパイルによる変化、上限接近時の挙動を、
ローカル診断だけでなくCloudWatch上でも継続的に確認したい。

## Problem

当初はアプリケーション、負荷生成方法、AWS環境、監視設定がなく、同じ条件で検証を再現できなかった。
ローカルアプリとGeneratorは実装済みで、AWS環境と監視設定が残っている。

## Goal

負荷生成可能なJavaアプリをEC2で実行し、CodeHeapメモリプールとログをCloudWatchへ送信して、
条件・手順・結果をGitで追跡できるようにする。

## Scope

含むもの:

- Ubuntu OpenJDK 21とSpring Bootによる検証専用API
- Ubuntu Server 24.04 LTS EC2（x86_64）への配置
- localhost限定JMX
- CloudWatch AgentによるJVMメトリクスとログの収集
- Code Cache負荷試験と結果記録

含まないもの:

- 本番サービスとしての可用性設計
- データベース、ユーザー認証、公開API
- EKS、ECS、複数台構成

## Proposed Change

1. `app/`にSpring Bootアプリケーションと自動テストを実装する。（完了）
2. `/load`、`/actuator/health`、検証profile限定GeneratorとHot/Cold probeを提供する。（完了）
3. `config/`にCloudWatch Agentとsystemdの設定テンプレートを置く。
4. `scripts/`にビルド、デプロイ、負荷生成の再現可能なスクリプトを置く。
5. EC2には最小権限のIAMロールを付与し、JMXを外部公開しない。
6. 性能試験後に実測値と結論を文書化する。

JDKベンダーをAmazon Correttoに固定しない。初回検証ではローカルとEC2の両方で
Ubuntuの`openjdk-21-jdk`を使用し、各環境の完全なバージョン文字列を結果に残す。

## Affected Documents

- `docs/architecture/`
- `docs/api/index.md`
- `docs/operations/`
- `docs/performance/`
- `docs/roadmap/current-roadmap.md`
- `docs/changelog/CHANGELOG.md`

## Risks

- JMXの外部公開によるセキュリティリスク
- 高負荷によるEC2の応答不能
- CloudWatchカスタムメトリクスおよびログの課金
- 小さいCode Cache設定を通常運用値と誤認するリスク

JMXはlocalhostに限定し、接続元IP、試験時間、ログ保持期間を制限する。

## Rollback Plan

EC2と関連するCloudWatchアラーム・ロググループを削除する。
アプリケーション変更はGitでrevertできる。検証結果とADRは履歴として保持する。
