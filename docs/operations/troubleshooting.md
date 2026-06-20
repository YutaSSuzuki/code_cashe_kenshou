# Troubleshooting

## Status

Active

| Symptom | Likely Cause | Check | Fix |
| --- | --- | --- | --- |
| アプリが起動しない | Java不一致、JAR不在、JVMオプション誤り | `systemctl status`、`journalctl -u code-cache-demo` | Javaと配置パスを修正して再起動 |
| `/load`がタイムアウトする | `count`または同時実行数が過大 | CPUUtilization、アプリログ | 負荷を停止し低い値から再開 |
| `Compiler.codecache`が失敗する | PID違いまたは実行ユーザーの権限不足 | `jcmd`、`ps -ef` | 対象JVMと同じユーザーで実行 |
| JMXへ接続できない | JVMオプションまたはポート競合 | `ss -lntp | grep 9010`、JVMログ | JMX設定を修正してJVM再起動 |
| CloudWatchにメトリクスがない | IAM、Agent設定、リージョンの不一致 | Agent status/log、IAMロール | 設定と権限を修正してAgent再起動 |
| ログだけ送信されない | ファイルパス・権限の誤り | Agentログ、`ls -l` | 読み取り権限と収集パスを修正 |
| `CodeCache is full`が出る | Code Cache上限到達 | JVMログ、`Compiler.codecache` | 負荷停止、結果保存、上限変更後に再起動 |

Code Cache満杯を意図的に発生させる場合は、通常運用の障害対応ではなく
[Code Cache Overflow Test](../performance/code-cache-overflow-test.md)に従う。

## Escalation

以下の場合は試験を中断し、条件と証跡を保存して設計を見直す。

- EC2へのSSH接続もできない高負荷状態が繰り返される。
- Code Cache以外のOOMやJVMクラッシュが発生する。
- IAM権限の追加が必要だが最小権限を判断できない。
- 想定外のCloudWatch課金または外部公開が確認された。

## Related Documents

- [Runbook](runbook.md)
- [Monitoring](monitoring.md)
