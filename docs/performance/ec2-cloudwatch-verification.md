# EC2 and CloudWatch Verification Plan

## Status

In Progress: EC2 restore and CloudWatch collection verified

## Goal

GitHubへ登録した資材からUbuntu EC2上に検証環境を復元し、次を一連の証跡として確認する。

1. 10MB Code Cacheで満杯を再現する。
2. Code Cache満杯後のCold probeレスポンス遅延を確認する。
3. Code Cache使用量とJVM warningをCloudWatchで監視する。
4. Code Cacheを64MBへ拡張して同じ負荷を再実行する。
5. compiler停止が発生せず、Cold probe遅延が解消することを確認する。

## Comparison Principle

比較する2条件では、Code Cache上限以外を揃える。

| Condition | Small Cache | Expanded Cache |
| --- | --- | --- |
| EC2 instance | Same | Same |
| AMI / JDK / commit | Same | Same |
| Spring profile | `code-cache-test` | `code-cache-test` |
| Generator | Same class count and iterations | Same class count and iterations |
| Probe count | Same | Same |
| Code Cache | 10MB | 32MBまたは64MB |

生成classはprocess内で完全resetできないため、条件変更時はJVMを停止して再起動する。

## Phase 1: Restore on EC2

1. Ubuntu Server 24.04 LTS x86_64 EC2を用意する。
2. IAM roleへCloudWatch送信権限を付与する。
3. OpenJDK 21、Git、curl、unzip、CloudWatch Agentを導入する。
4. GitHubから対象commitをcloneする。
5. `app/`で`./mvnw clean test`を実行する。
6. `./mvnw clean package`でJARを作成する。
7. Git commit、OS、JDK、instance type、CloudWatch Agent versionを記録する。

詳細は[Deployment Operation](../operations/deployment.md)に従う。

## Phase 2: Configure CloudWatch

CloudWatch Agentで次を収集する。

| Type | Name | Purpose |
| --- | --- | --- |
| JMX | `jvm.memory.pool.used` | Code Cache現在使用量 |
| JMX | `jvm.memory.pool.max` | Code Cache上限 |
| JMX | `jvm.memory.pool.committed` | commit済み容量 |
| JMX | `jvm.memory.nonheap.used` | 非heap全体 |
| JMX | `jvm.classes.loaded` | 動的生成class増加 |
| Log | application/JVM log | Code Cache warning確認 |
| EC2 | CPUUtilization | Generator・probe負荷確認 |

Metric Mathで次を作成する。

```text
CodeCacheUsagePercent = 100 * jvm.memory.pool.used / jvm.memory.pool.max
```

小さいCode Cacheではpool名が`CodeCache`、通常のsegmented code cacheでは複数の`CodeHeap`名に
なる可能性がある。CloudWatchの`name` dimensionを確認して対象系列を選ぶ。

CloudWatch標準JMX metricには`full_count`、`stopped_count`、`restarted_count`は含まれない。
満杯の最終確認は`jcmd`、CloudWatch上の確認は使用率とJVM warning logを組み合わせる。

## Phase 3: Reproduce with 10 MB

JMX、検証profile、Code Cache warning logを有効にして起動する。

```bash
java \
  -XX:ReservedCodeCacheSize=10m \
  -XX:-UseCodeCacheFlushing \
  -Xlog:codecache*=warning \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.host=127.0.0.1 \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.rmi.port=9010 \
  -Dcom.sun.management.jmxremote.local.only=true \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Djava.rmi.server.hostname=127.0.0.1 \
  -jar target/code-cache-demo-*.jar \
  --spring.profiles.active=code-cache-test
```

JMX 9010はlocalhostからだけ接続し、Security Groupで公開しない。

### JVM Option Reference

| Option | Purpose |
| --- | --- |
| `-XX:ReservedCodeCacheSize=10m` | 満杯を短時間で強制する。通常運用値ではない |
| `-XX:ReservedCodeCacheSize=32m` | 10MBより段階的な増加を観測しやすい中間試験値 |
| `-XX:ReservedCodeCacheSize=64m` | 同一負荷で容量不足と遅延が解消するか比較する値 |
| `-XX:-UseCodeCacheFlushing` | Sweeperによる通常回復を無効化し、満杯とJIT停止を維持する検証専用option |
| `-Xlog:codecache*=warning` | Code Cache warningをapplication logへ出す |
| `-Dcom.sun.management.jmxremote` | CloudWatch AgentがJVM metricを取得するJMXを有効化する |
| `jmxremote.host=127.0.0.1` | JMX listenをlocalhostに限定する |
| `jmxremote.port=9010` | JMX registryとRMIの固定port。Security Groupでは公開しない |
| `jmxremote.authenticate=false` | localhost限定の検証環境で認証を省略する。外部公開は禁止 |
| `jmxremote.ssl=false` | localhost限定の検証環境でTLSを省略する。外部公開は禁止 |
| `--spring.profiles.active=code-cache-test` | GeneratorとHot/Cold APIを登録する検証profile |

`-Xcomp`はすべてのmethodを強制compileし、実運用に近いHot/Cold遅延比較を壊すため使用しない。

### Start in Background

容量だけを`10m`、`32m`、`64m`から選び、次の形で起動する。

```bash
cd ~/code_cashe_kenshou/app

nohup java \
  -XX:ReservedCodeCacheSize=32m \
  -XX:-UseCodeCacheFlushing \
  -Xlog:codecache*=warning \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.host=127.0.0.1 \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.rmi.port=9010 \
  -Dcom.sun.management.jmxremote.local.only=true \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Djava.rmi.server.hostname=127.0.0.1 \
  -jar target/code-cache-demo-*.jar \
  --spring.profiles.active=code-cache-test \
  > /home/ubuntu/code-cache-demo.log 2>&1 &

echo $! > /home/ubuntu/code-cache-demo.pid
```

```bash
PID=$(cat /home/ubuntu/code-cache-demo.pid)
curl -fsS http://localhost:8080/actuator/health
jcmd "$PID" Compiler.codecache
ss -lnt | grep 9010
```

9010の期待listen先は`127.0.0.1`である。

### Reset Between Conditions

生成classとCode CacheはJVM process内の状態なので、容量変更時は必ずprocessを停止する。

```bash
PID=$(cat /home/ubuntu/code-cache-demo.pid)
kill "$PID"

while kill -0 "$PID" 2>/dev/null; do
  sleep 1
done

mv /home/ubuntu/code-cache-demo.log \
  "/home/ubuntu/code-cache-demo-$(date +%Y%m%d-%H%M%S).log"
```

新しい容量で起動し、PIDと`Compiler.codecache` baselineを取り直す。CloudWatchの60秒samplingで
段階的な増加を残す場合はGenerator batch間を65秒空ける。

### Procedure

1. PIDとbaselineを保存する。
2. Hot probeだけを100回warm-upする。
3. Generatorを500 class、各10,000回で実行する。
4. `full_count=0`なら250または1,000 classずつ追加する。
5. 満杯を確認した時点の累計class数を64MB比較条件として記録する。
6. Hot probeを測定する。
7. Cold probeを初めて測定する。
8. CloudWatch metric、warning log、`jcmd`を同じ時刻で保存する。

### Full Confirmation

`jcmd <PID> Compiler.codecache`で次を確認する。

```text
full_count > 0
Compilation: disabled
stopped_count > 0
```

CloudWatchでは次を確認する。

- Code Cache使用率が上昇している。
- `jvm.classes.loaded`がGenerator実行に伴って増えている。
- JVM warning logがCloudWatch Logsへ送信されている。
- CPUUtilizationとHTTP応答が同じ時刻で確認できる。

### Latency Confirmation

- Hot/Coldで同じ`count`と同じ計算結果を使う。
- JSONの`elapsedNanos`とclient側の`time_total`を保存する。
- `Cold / Hot`を計算する。
- Coldがcompiled code一覧に存在しないことを確認する。

## Phase 4: Expand to 64 MB

1. 10MB JVMを停止する。
2. `ReservedCodeCacheSize`だけを64MBへ変更して再起動する。
3. 新しいPID、baseline、CloudWatch上限値を確認する。
4. Hot probeを同じ回数warm-upする。
5. 10MBで満杯になったときと同じclass数・iterationsをGeneratorへ指定する。
6. Hot/Coldを同じ`count`で測定する。
7. `jcmd`とCloudWatchを保存する。

起動optionの差分:

```text
-XX:ReservedCodeCacheSize=10m
                       ↓
-XX:ReservedCodeCacheSize=64m
```

## Success Criteria

| Verification | Success Criteria |
| --- | --- |
| Environment restore | clone後に14 testsが成功しJARを起動できる |
| 10MB full | `full_count > 0`かつ`stopped_count > 0` |
| CloudWatch | pool used/max、class数、warning logを確認できる |
| Delay | 10MB満杯時のColdがHotより明確に遅い |
| 64MB capacity | 同じGenerator条件で`full_count=0`、Compilation enabled |
| Delay recovery | 64MB条件のColdが10MB満杯時より短い |
| Application | 全probeがHTTP 200、計算結果が一致 |

遅延は単発値だけで断定せず、条件と時刻を揃えて複数回またはJVM再起動を含む再試験で確認する。

## Evidence to Preserve

- Git commit SHA
- EC2 instance type、AMI ID、CPU architecture
- `java -version`、`VM.flags`
- CloudWatch Agent設定とversion
- `Compiler.codecache`のbaseline、満杯時、64MB時
- Generator responseと累計class数
- Hot/Cold response、`elapsedNanos`、client time
- CloudWatch graphの期間とmetric dimension
- JVM warning log
- 10MBと64MBの判定

## Related Documents

- [Deployment Operation](../operations/deployment.md)
- [Monitoring](../operations/monitoring.md)
- [Code Cache Test API](../api/code-cache-test-api.md)
- [Code Cache Overflow Test](code-cache-overflow-test.md)
- [Performance Test Result](test-result.md)
