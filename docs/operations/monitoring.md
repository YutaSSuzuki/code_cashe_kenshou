# Monitoring

## Status

Verified on EC2

CloudWatch Agentからlocalhost JMXのメトリクスとJVMログを収集する。

## Prerequisites

- EC2のIAM roleへAWS管理ポリシー`CloudWatchAgentServerPolicy`を付与する。
- Security Groupのoutbound HTTPS 443を許可する。
- JMX 9010はSecurity Groupのinboundへ追加しない。
- CloudWatch Agentを[Deployment Operation](deployment.md)に従ってインストールする。

## Agent Configuration

次を`/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json`へ保存する。
満杯状態は`-XX:-UseCodeCacheFlushing`で維持し、`jcmd`とログでも確認できるため、収集間隔は
費用を抑える60秒とする。

```json
{
  "agent": {
    "metrics_collection_interval": 60,
    "run_as_user": "root"
  },
  "metrics": {
    "namespace": "CodeCacheDemo",
    "append_dimensions": {
      "InstanceId": "${aws:InstanceId}"
    },
    "metrics_collected": {
      "jmx": [
        {
          "endpoint": "localhost:9010",
          "metrics_collection_interval": 60,
          "jvm": {
            "measurement": [
              "jvm.memory.pool.used",
              "jvm.memory.pool.max",
              "jvm.memory.pool.committed",
              "jvm.memory.nonheap.used",
              "jvm.classes.loaded"
            ]
          }
        }
      ]
    }
  },
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/home/ubuntu/code-cache-demo.log",
            "log_group_name": "/code-cache-demo/application",
            "log_stream_name": "{instance_id}"
          }
        ]
      }
    }
  }
}
```

設定を検証して起動する。

```bash
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config \
  -m ec2 \
  -s \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json

sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a status
sudo grep -n 'collection_interval' \
  /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.yaml
sudo tail -50 \
  /opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log
```

期待値は`collection_interval: 1m0s`とAgentの`running`である。

## Monitoring Targets

| Target | Metric / Log | Alert Condition |
| --- | --- | --- |
| Code Cache | `jvm.memory.pool.used`（CodeHeap系列） | 最大値に対する使用率80%以上が5分継続 |
| Code Cache上限 | `jvm.memory.pool.max`（CodeHeap系列） | アラーム計算用の基準値 |
| 非ヒープ全体 | `jvm.memory.nonheap.used` | 平常値からの継続的な増加 |
| クラスロード | `jvm.classes.loaded` | 想定外の継続増加 |
| EC2 | CPUUtilization | 80%以上が5分継続 |
| アプリケーション | `/home/ubuntu/code-cache-demo.log` | `CodeCache is full`またはコンパイル停止を示すログ |

## CloudWatch Graph Configuration

CloudWatchの`すべてのメトリクス`からcustom namespace `CodeCacheDemo`を選ぶ。Code Cacheは
同一`InstanceId`かつ`name=CodeCache`の次の3系列を同じグラフへ追加する。

```text
jvm.memory.pool.used
jvm.memory.pool.max
jvm.memory.pool.committed
```

`used`のmetric IDが`m1`、`max`が`m2`の場合、`数式を追加`から次を登録する。

```text
100 * m1 / m2
```

labelを`CodeCacheUsagePercent`、unitを`Percent`、periodを`1 minute`にする。
`jvm.classes.loaded`には`name` dimensionがないため、`InstanceId`だけの分類から別グラフへ追加する。

JVMを10MBから32MBなどへ再起動すると、同じ時系列に古い`max`も残る。時間範囲を再起動後へ絞り、
`name=CodeCache`、statistic `Maximum`、period `1 minute`を確認する。新しい値が2分以上届かない場合だけ
Agentを再起動してJMXへ再接続する。

```bash
sudo systemctl restart amazon-cloudwatch-agent
```

## Health Check

`GET /actuator/health`がHTTP 200を返すことを確認する。

## Logs

- Log group: `/code-cache-demo/application`
- Log stream: EC2 Instance ID
- 保持期間: 検証用途として7日を初期値とする。
- JVM起動オプション`-Xlog:codecache*=warning`のwarningを収集する。

## Alerts

CodeHeapは複数のメモリプールに分かれるため、`name`ディメンションごとに監視する。
アラームの確定値は初回試験結果を基に見直す。

## EC2 Verification Sequence

1. 10MBまたは32MB条件でCode Cache使用率とJVM warningをCloudWatchへ送る。
2. `jcmd`で`full_count`とcompiler停止を確認する。
3. Hot/Coldレスポンス時間を同じ時刻で記録する。
4. JVMを64MB条件で再起動する。
5. 同じGenerator負荷をかけ、使用率低下とcompiler enabledを確認する。
6. Coldレスポンス時間が10MB満杯時より短縮したことを確認する。

CloudWatch Agentの標準JMX metricだけでは`full_count`やcompiler停止counterを直接取得できない。
Code Cache満杯はpool使用率、JVM warning log、`jcmd`を組み合わせて判定する。

60秒収集では1秒未満の一時的な満杯を直接samplingできない。今回の強制試験は満杯状態を維持するため
監視できるが、通常のflushing有効条件で短時間に回復する事象はJVM warningログと`jcmd`を証跡にする。

## Related Documents

- [Runbook](runbook.md)
- [Troubleshooting](troubleshooting.md)
- [EC2 and CloudWatch Verification](../performance/ec2-cloudwatch-verification.md)
