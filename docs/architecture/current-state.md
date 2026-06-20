# Java Code Cache検証環境 Current State

## Status

Active

## Scope

Java Code Cache検証リポジトリの、現時点の実装およびインフラ状態を記載する。

## Overview

Spring Bootサンプルアプリケーションとローカルテストが実装されている。
デプロイスクリプト、CloudWatch設定、AWSリソースはまだ作成されていない。

## Details

- `app/`: Java 21、Spring Boot 4.1.0、Maven Wrapperを使用するビルド可能なプロジェクト。
  `/load`、`/actuator/health`、検証profile限定Generator、Hot/Cold probe、HTTP・単体テストが存在する。
- `config/`: READMEのみ。CloudWatch Agent設定は未作成。
- `scripts/`: READMEのみ。負荷生成・デプロイスクリプトは未作成。
- `tests/`: READMEのみ。自動テストは未作成。
- `docs/`: 検証目的、予定構成、運用手順、性能試験計画を管理する。
- AWS: EC2、IAMロール、CloudWatch Logs、カスタムメトリクス、アラームは未作成。

ローカル確認済みの状態:

- `./mvnw clean test`は14 tests、failures 0、errors 0で成功。
- `GET /load?count=1000`はHTTP 200とJSONを返す。
- `GET /load?count=0`はHTTP 400を返す。
- Byte Buddyで500 classを生成し、Code Cacheを6,827KBから9,707KBへ増加できた。
- 追加250 classで`full_count=1`、`Compilation: disabled`を再現した。
- 満杯後のHot probe 92,234ns、Cold probe 1,265,000nsを観測した。
- 学習者による再実行でもCode Cache満杯とHot/Cold遅延を確認済み。
- 次工程はGitHub資材からUbuntu EC2へ復元し、10MB/64MB比較とCloudWatch監視を行う。

予定している構成はCurrent Stateには含めず、ProposalおよびDraft文書に記載する。

## Related Documents

- [System Context](system-context.md)
- [Proposed Verification Environment](../proposals/code-cache-verification-environment.md)
- [Roadmap](../roadmap/current-roadmap.md)
