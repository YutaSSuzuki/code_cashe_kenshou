# Changelog

## 2026-06-20

### Added

- Java Code Cache検証の目的、予定アーキテクチャ、API仕様を文書化。
- CloudWatch監視項目、Runbook、性能試験計画を追加。
- 検証環境構築Proposalと初期Roadmapを追加。
- 学習者向けのSpring Bootローカル実装・確認手順を追加。
- `/load` APIのクラス設計、実装順、境界値、テスト観点を記載した学習ガイドを追加。
- ローカルAPI実装とsmoke testの進捗をCurrent StateとTest Resultへ記録。
- Code Cache満杯時の停止、回収、再開、HTTP影響を確認する試験手順を追加。
- `Compiler.codecache`の各項目、使用率、差分、segmented表示、満杯・回復判定の読み方を追記。
- Tiered CompilationのLevel 0～4、backedge、個別methodのLevel確認方法を追記。
- Code Cacheを増やす単位、同一loopでは増え続けない理由、`-Xcomp`と動的class生成の選択基準を追記。
- `-Xcomp`試験の既定commandから詳細logと`tee`を外し、最小optionと追加調査optionを分離。
- `-Xcomp`起動完了を判別できるようlog抑制を外し、portとhealthの確認方法を追記。
- `-Xcomp`を機構確認に限定し、通常Tiered Compilationとgenerator/probe分離を遅延試験の条件に変更。
- Byte Buddyによる検証profile限定Generator、Hot/Cold probe、入力上限、自動テストを追加。
- Generator API仕様とローカル再現手順を追加。
- EC2復元、CloudWatch監視、10MB満杯、64MB拡張後の遅延解消を比較する次工程計画を追加。

### Changed

- データベースと永続データのバックアップを対象外として明記。
- EC2の検証OSをAmazon Linux 2023からUbuntu Server 24.04 LTSへ変更。
- JDKをCorretto固定とせず、Ubuntu OpenJDK 21をローカルとEC2の基準に設定。
- ローカル・EC2の必要資材、導入コマンド、AWSリソース要件をDeployment Operationへ追記。
- Spring Bootサンプル実装をRoadmapの進行中へ変更し、環境構築ProposalをApprovedへ変更。
- ローカル手順へMavenコマンドの役割、再実行条件、現在の再開地点を追記。
- ローカルで10MB Code Cache満杯、JIT停止、Cold probe約13.7倍遅延を確認し、性能文書を更新。
- ローカル検証を完了し、RoadmapのCurrent FocusをEC2・CloudWatch検証へ変更。

### Fixed

- 該当なし。

### Removed

- 汎用プロジェクト雛形のプレースホルダー記述。

### Related Documents

- [Current State](../architecture/current-state.md)
- [Performance Test Plan](../performance/test-plan.md)
- [Roadmap](../roadmap/current-roadmap.md)
- [Local Development](../operations/local-development.md)
