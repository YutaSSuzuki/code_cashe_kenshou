# Java Code Cache検証環境 Current State

## Status

Active

## Scope

Java Code Cache検証リポジトリの、現時点の実装およびインフラ状態を記載する。

## Overview

Spring Bootサンプルアプリケーション、ローカル検証、Ubuntu EC2での再現、CloudWatch Agentによる
JMXメトリクス・ログ監視まで完了している。

## Details

- `app/`: Java 21、Spring Boot 4.1.0、Maven Wrapperを使用するビルド可能なプロジェクト。
  `/load`、`/actuator/health`、検証profile限定Generator、Hot/Cold probe、HTTP・単体テストが存在する。
- `config/`: 設定ファイル置き場。CloudWatch Agent設定は[Monitoring](../operations/monitoring.md)に手順として記載している。
- `scripts/`: 補助スクリプト置き場。SSH port変更など検証環境用の補助資材を管理する。
- `tests/`: 将来の結合試験・デプロイ試験用の置き場。現在の自動テストは`app/src/test/`に配置している。
- `docs/`: 現在状態、API、運用、性能検証、ADR、Changelogを管理する。
- AWS: Ubuntu EC2、IAM role、CloudWatch Agent、CloudWatch Logs、custom metricsで検証済み。

確認済みの状態:

- `./mvnw clean test`は14 tests、failures 0、errors 0で成功。
- `GET /load?count=1000`はHTTP 200とJSONを返す。
- `GET /load?count=0`はHTTP 400を返す。
- `LoadService.calculate`がHotSpot Tiered Compilation Level 4へ到達することを確認済み。
- Byte Buddy Generatorで多数の異なるclass/methodを生成し、Code Cacheを意図的に圧迫できる。
- 32MB、`-XX:-UseCodeCacheFlushing`条件でCode Cache満杯、`full_count=1`、`Compilation: disabled`を再現済み。
- 満杯後にCold probeがJIT停止時の影響を受けて遅くなることを確認済み。
- 64MBへ拡張すると同一負荷で満杯を回避し、Cold probeがJIT停止時より速くなることを確認済み。
- 32MB、flushing有効条件でSweeper回収により`used`と`nmethods`が減少し、JITが継続することを確認済み。
- CloudWatch Agentで`jvm.memory.pool.used`、`max`、`committed`とJVM warning logを監視済み。

未実装の構想はCurrent Stateには含めず、Proposalへ記載する。

## Related Documents

- [System Context](system-context.md)
- [Runtime Flow](runtime-flow.md)
- [Monitoring](../operations/monitoring.md)
- [EC2 and CloudWatch Verification](../performance/ec2-cloudwatch-verification.md)
- [Roadmap](../roadmap/current-roadmap.md)
