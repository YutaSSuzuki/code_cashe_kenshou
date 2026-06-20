# Performance Test Plan

## Status

Active

Code Cache使用量がHTTP負荷により変化すること、およびその変化をローカルの`jcmd`と
CloudWatch JMXメトリクスの両方で観測できることを確認する。

## Scope

- Java 21 HotSpot JVMのCode Cache
- サンプルアプリケーションの負荷API
- EC2上のCloudWatch AgentによるJMX・ログ収集
- アプリケーションの基本的な応答可否

業務性能、スループット目標、他JVM実装の比較は対象外とする。

## Conditions

- Local OS: WSL2 Ubuntu 22.04 LTS
- EC2 OS: Ubuntu Server 24.04 LTS x86_64
- JVM: Ubuntu OpenJDK 21 HotSpot。`java -version`の完全な出力を試験結果へ記録する。
- 通常観測: `-XX:ReservedCodeCacheSize=64m`
- 満杯試験: `-XX:ReservedCodeCacheSize=10m`、`code-cache-test` profile
- 通常Tiered Compilationを使用し、`-Xcomp`は本試験に使用しない。
- EC2: インスタンスタイプ、AMI ID、CPUアーキテクチャを実施時に記録する。
- CloudWatch Agent: バージョンを実施時に記録する。

## Test Data

- 通常負荷: `GET /load?count={iterations}`
- Hot/Cold: `/code-cache/probe/hot`, `/code-cache/probe/cold`
- Generator: 500 class + 250 class、各method 10,000回を初期条件とする。

## Scenarios

| Scenario | Load | Duration | Success Criteria |
| --- | --- | --- | --- |
| Baseline | 起動後、負荷なし | 5分 | `jcmd`とCloudWatchで初期値を取得できる |
| Warm-up | `/load`へ100リクエスト | 完了まで | 全リクエスト成功、Code Cache使用量が初期値以上 |
| Sustained load | `/load`へ一定レートで連続送信 | 15分 | JVM停止なし、CloudWatchに連続データがある |
| Generator pressure | 10MB、500 class + 250 class | 完了まで | full_countまたはcompiler停止を確認 |
| Hot/Cold comparison | Hotを事前warm、Coldを満杯後に初回実行 | 完了まで | 同一計算結果とelapsedNanos差を記録 |
| Recovery | 満杯後に待機またはwarmを継続 | 最大10分 | stopped/restarted、enabled/disabledを記録 |

## Metrics

- `jcmd <PID> Compiler.codecache`の各CodeHeap used/max
- `jvm.memory.pool.used`, `committed`, `max`のCodeHeap系列
- `jvm.memory.nonheap.used`
- `jvm.classes.loaded`
- EC2 CPUUtilization
- HTTP成功数、失敗数、応答時間
- Hot/Coldの`elapsedNanos`比
- Generatorの生成class数、呼び出し数、実行時間
- `CodeCache is full`などのJVMログ

## Execution Procedure

1. ビルド成果物、JVMオプション、環境情報を記録する。
2. アプリケーションとCloudWatch Agentを起動する。
3. Baselineを取得し、Hot probeだけをwarm-upする。
4. Generatorを段階実行し、各段階の`jcmd`出力を保存する。
5. 満杯確認後にHot/Coldを測定する。
6. CloudWatchの同時間帯のメトリクスとログを確認する。
7. 試験結果と再現条件を`test-result.md`へ記録する。
8. 異常またはボトルネックがあれば`bottleneck-analysis.md`を更新する。

## Related Documents

- [Current Performance](current-performance.md)
- [Code Cache Overflow Test](code-cache-overflow-test.md)
- [Code Cache Test API](../api/code-cache-test-api.md)
- [EC2 and CloudWatch Verification](ec2-cloudwatch-verification.md)
- [Test Result](test-result.md)
