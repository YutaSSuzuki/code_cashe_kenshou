# Monitoring

## Status

Draft

CloudWatch AgentからJMXメトリクスとアプリケーションログを収集する予定。

## Monitoring Targets

| Target | Metric / Log | Alert Condition |
| --- | --- | --- |
| Code Cache | `jvm.memory.pool.used`（CodeHeap系列） | 最大値に対する使用率80%以上が5分継続 |
| Code Cache上限 | `jvm.memory.pool.max`（CodeHeap系列） | アラーム計算用の基準値 |
| 非ヒープ全体 | `jvm.memory.nonheap.used` | 平常値からの継続的な増加 |
| クラスロード | `jvm.classes.loaded` | 想定外の継続増加 |
| EC2 | CPUUtilization | 80%以上が5分継続 |
| アプリケーション | `/var/log/code-cache-demo.log` | `CodeCache is full`またはコンパイル停止を示すログ |

## Health Check

`GET /actuator/health`がHTTP 200を返すことを確認する。

## Logs

- Log group: `/code-cache-demo/application`
- Log stream: EC2 Instance ID
- 保持期間: 検証用途として7日を初期値とする。
- JVM起動オプション`-XX:+PrintCodeCache`の出力を収集する。

## Alerts

CodeHeapは複数のメモリプールに分かれるため、`name`ディメンションごとに監視する。
アラームの確定値は初回試験結果を基に見直す。

## EC2 Verification Sequence

1. 10MB条件でCode Cache使用率とJVM warningをCloudWatchへ送る。
2. `jcmd`で`full_count`とcompiler停止を確認する。
3. Hot/Coldレスポンス時間を同じ時刻で記録する。
4. JVMを64MB条件で再起動する。
5. 同じGenerator負荷をかけ、使用率低下とcompiler enabledを確認する。
6. Coldレスポンス時間が10MB満杯時より短縮したことを確認する。

CloudWatch Agentの標準JMX metricだけでは`full_count`やcompiler停止counterを直接取得できない。
Code Cache満杯はpool使用率、JVM warning log、`jcmd`を組み合わせて判定する。

## Related Documents

- [Runbook](runbook.md)
- [Troubleshooting](troubleshooting.md)
- [EC2 and CloudWatch Verification](../performance/ec2-cloudwatch-verification.md)
