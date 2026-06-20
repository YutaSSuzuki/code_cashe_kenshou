# Operation: Java Code Cache Verification

## Status

Active

## Scope

ローカルおよびEC2上で負荷を生成し、JVM Code CacheとCloudWatchへの送信状態を確認する。

## Prerequisites

- Ubuntu上のOpenJDK 21およびMaven Wrapperでアプリケーションをビルドできること。
- EC2にCloudWatch Agent用IAMロールが付与されていること。
- JMXがlocalhost:9010で有効になっていること。
- Security Groupの8080番が検証元IPだけに許可されていること。

## Procedure

1. `systemctl status code-cache-demo`でアプリケーションを確認する。
2. `curl http://localhost:8080/actuator/health`で正常応答を確認する。
3. `jcmd`でPIDを特定し、`jcmd <PID> Compiler.codecache`を保存する。
4. `/load`へ一定回数リクエストを送り負荷を生成する。
5. 同じ`jcmd`コマンドを実行して前後差分を記録する。
6. CloudWatchでCodeHeap系列とログを確認する。
7. 条件、実測値、判定をPerformance Test Resultへ記録する。

## Verification

- 負荷リクエストがHTTP 200を返す。
- `jcmd <PID> Compiler.codecache`でCode Cache使用量を取得できる。
- CloudWatchのメトリクスにCodeHeap名の系列が現れる。
- CloudWatch Logsにアプリケーションログが現れる。

## Troubleshooting

| Symptom | Likely Cause | Check | Fix |
| --- | --- | --- | --- |
| JMXメトリクスがない | JMX未起動またはAgent設定誤り | `ss -lntp`、Agentログ | JVMオプションとendpointを修正して再起動 |
| CodeHeap系列がない | 測定項目またはディメンションの見落とし | Agent設定、CloudWatchの全ディメンション | `jvm.memory.pool.*`を有効化 |
| HTTP 503/タイムアウト | 負荷値が過大 | CPU、アプリログ、`count` | 負荷を下げ、上限値を設定 |
| `CodeCache is full` | Code Cache上限到達 | JVMログ、`Compiler.codecache` | 負荷停止後に上限を見直して再起動 |

## Related Documents

- [Monitoring](monitoring.md)
- [Troubleshooting](troubleshooting.md)
- [Performance Test Plan](../performance/test-plan.md)
