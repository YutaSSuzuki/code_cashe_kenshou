# Code Cache Overflow Test

## Status

Verified Locally

## Purpose

ローカル環境でCode Cacheを意図的に満杯にし、JITコンパイルの停止、Code Cache Sweeperによる
回収・再開、HTTP応答への影響を観測する。

## Safety

- EC2や共有環境ではなく、最初はローカル環境だけで実施する。
- 現在のJavaプロセスを停止してから、検証用JVMを1つだけ起動する。
- 高CPU負荷になるため、端末が重くなったら負荷生成をCtrl+Cで停止する。
- 64MB以下のCode Cacheは検証専用値であり、本番推奨値ではない。
- JVMやOSが応答しなくなった場合に備え、PIDと停止方法を先に確認する。

## Why the Current Load May Not Fill 64 MB

`/load`は同じ`LoadService.calculate`を繰り返し呼ぶ。メソッドがJITコンパイルされた後は、
呼び出し回数を増やしても異なるコンパイル済みメソッドが無制限に増えるわけではない。

通常の`/load`だけでは64MBを満杯にできなかった。現在は検証profileのGeneratorで異なる
methodをruntime生成するため、10MB条件で再現可能に満杯へ到達できる。

## What Actually Increases Code Cache Usage

Code Cacheへ格納される単位は、概ねJITコンパイルされたmethodやOSR entryのmachine codeである。
request dataやloop iterationごとに新しいcodeが追加されるわけではない。

| Approach | Expected Effect | Judgment |
| --- | --- | --- |
| 同じmethodの呼び出し回数を増やす | Tier 4到達までは増えるが、その後は同じcodeを再利用 | 満杯用途には不十分 |
| 1つのloopで入力値を毎回変える | 同じbytecodeとcompiled codeを実行 | 満杯用途には不十分 |
| 1つのmethod内に多数の分岐を書く | methodのcodeは多少大きくなるが、compile回数は有限 | 保守性が悪く効果も限定的 |
| 通常のAPIを1つ追加する | framework codeの一部は増えるが、増加量は小さい | 効果が不確実 |
| 多数の異なるmethodをhotにする | methodごとにcompiled codeが生成される | 再現性が高い |
| `-Xcomp`を使用する | 呼び出されたmethodを初回から積極的にcompile | application変更なしで試せる |
| 動的に多数のclass/methodを生成する | 異なるmethodを必要数だけ作ってcompile可能 | Generatorとして実装済み |

同じmethodの中で「1loopごとに異なる内容」をdataや分岐で選んでも、JITは通常そのmethodを
1つまたは少数のversionへcompileするだけである。異なるmachine codeを継続的に追加したい場合は、
異なるmethodまたはclassを多数用意し、それぞれをcompile thresholdまで呼ぶ必要がある。

### Recommended Order

1. `-Xcomp`なしの通常Tiered Compilationでbaselineの応答時間とCode Cacheを取得する。
2. 多数の異なるhot methodを生成する検証専用機能を使い、通常のJIT方針のままCode Cacheを増やす。
3. 満杯前後で同じprobe APIの応答時間、`full_count`、停止・再開counterを比較する。
4. `-Xcomp`はapplication変更前の機構確認または補助試験だけに使用する。
5. `-XX:-UseCodeCacheFlushing`は停止継続を強制する最終試験だけに使用する。

単純な2つ目の業務API追加や巨大な分岐methodは、Code Cache検証とAPI機能を混在させるため採用しない。

### Implemented Generator

Byte Buddy 1.18.2を使い、`code-cache-test` profile限定で次を実装している。

```text
POST /code-cache/generator/generate?classes=N&warmupIterations=K
POST /code-cache/generator/warm?iterations=K
GET  /code-cache/generator/status
GET  /code-cache/probe/hot?count=N
GET  /code-cache/probe/cold?count=N
```

- 1 classにつき1つの異なる`GeneratedWorkUnit.execute`を生成する。
- 各methodを`K`回呼び、compile thresholdへ到達させる。
- 結果をvolatile sinkへ集約し、呼び出しの除去を防ぐ。
- 生成classへの参照をprocess終了まで保持する。
- 1 request 1,000 class、累計2万class、2,000万呼び出しを上限とする。
- 通常profileではendpoint自体を登録しない。

この方法はCode CacheだけでなくMetaspaceも増加させる。Code Cache不足とMetaspace不足を混同しない
よう、`jcmd <PID> VM.native_memory summary`などを併用する。Native Memory Trackingを使用する場合は
JVM起動時に`-XX:NativeMemoryTracking=summary`が必要である。

詳細なparameterとresponseは[Code Cache Test API](../api/code-cache-test-api.md)を参照する。

## Limitation of `-Xcomp`

`-Xcomp`でもCode Cache満杯、JIT停止、`full_count`などの機構は確認できる。ただし次の理由で
応答遅延の本試験には使用しない。

- 通常のTiered Compilationを経ず、methodを初回から積極的にcompileする。
- Spring Bootの起動と初回request自体が大幅に遅くなる。
- 満杯前のbaselineが通常起動と同じ条件にならない。
- 既にcompile済みのmethodは満杯後も実行できるため、すべてのrequestが一様に遅くなるわけではない。
- 満杯後に初めて呼ばれるmethod、deoptimizationされたmethod、未compile methodが主にinterpreter実行の影響を受ける。

したがって試験目的を分ける。

| Test Purpose | JVM Mode | Valid Observation |
| --- | --- | --- |
| Code Cacheを満杯にできるか | `-Xcomp`可 | full_count、compiler停止・再開、JVM warning |
| 通常運用に近い遅延変化 | `-Xcomp`なし | baseline比のHTTP latency、throughput、JIT状態 |
| JIT停止を継続させる | flushing無効 | interpreter継続時の挙動。人工的な障害条件 |

### Latency Probe Design

Code Cacheを増やすgeneratorと、遅延を測るprobeを同じ処理にしない。

- Generator: 多数の異なるclass/methodを生成してwarm-upする。
- Hot probe: 満杯前にLevel 4までwarm-upした固定methodを呼ぶ。満杯後もcompiled codeが利用できる対照群。
- Cold probe: Hot probeと同等の計算だが、満杯後まで呼ばない別methodを用意する。interpreter実行の影響を見る。
- Control: `/actuator/health`でapplication生存を確認する。

既存の`/load`はすでにLevel 4へ到達しているため、満杯後も速いままの可能性がある。これは失敗ではなく、
既存compiled codeが引き続き実行できることを示す。遅延を明確に比較するには、同じ計算量のhot/cold
methodを分けるか、満杯なし・満杯ありの別processで同じcold pathを比較する。

probeは満杯前・満杯接近中・満杯後・回復後に測定する。`elapsedNanos`だけでなく、client側の
response time、HTTP status、timeout、throughputも記録する。

## Expected Behavior

Code Cacheが満杯になった場合、JVMが必ず終了するわけではない。

1. 新しいJITコンパイル結果を格納できなくなる。
2. JITコンパイルが停止し、未コンパイル部分はインタープリタで実行される。
3. Code Cache Sweeperが不要なcompiled codeを回収する。
4. 領域を確保できればJITコンパイルが再開する。
5. 回収できなければコンパイル停止が継続し、応答が遅くなる可能性がある。

観測対象:

```text
full_count
Compilation: enabled | disabled
stopped_count
restarted_count
used / max_used / free
nmethods
HTTP status and elapsedNanos
JVM warning log
```

## How to Read `Compiler.codecache`

対象PIDを`jcmd -l`で確認してから実行する。

```bash
jcmd <PID> Compiler.codecache
```

出力例:

```text
CodeCache: size=65536Kb used=16610Kb max_used=17737Kb free=48925Kb
 bounds [0x0000000000000000, 0x0000000000000000, 0x0000000000000000]
 total_blobs=6245, nmethods=5635, adapters=515, full_count=0
Compilation: enabled, stopped_count=0, restarted_count=0
```

`bounds`のaddressは説明用にゼロへ置き換えている。実際の出力ではプロセスごとに異なるaddressになる。

### Capacity

| Field | Meaning | What to Check |
| --- | --- | --- |
| `size` | `ReservedCodeCacheSize`で予約した最大容量 | 起動optionが反映されているか |
| `used` | 現在使用中の容量 | 負荷前後でどれだけ増減したか |
| `max_used` | JVM起動後の最大使用量 | 一時的なpeakが現在値より高くないか |
| `free` | 現在の空き容量 | ゼロ付近へ接近しているか |

使用率は次で計算する。

```text
current usage percent = used / size * 100
peak usage percent    = max_used / size * 100
```

例では現在使用率が約25.3%、peak使用率が約27.1%である。

```text
16610 / 65536 * 100 = 25.34%
17737 / 65536 * 100 = 27.06%
```

`max_used`が`used`より大きいのは異常ではない。一度使用されたcompiled codeがSweeperにより
回収されると`used`は減るが、過去のpeakである`max_used`は維持される。

### Stored Code

| Field | Meaning | What to Check |
| --- | --- | --- |
| `total_blobs` | Code Cache内の全code blob数 | 負荷により全体が増えたか |
| `nmethods` | JITコンパイルされたJava methodのcode数 | JITコンパイルが進んだか |
| `adapters` | Java、interpreter、native間を接続するadapter code数 | 通常は起動後の変化が小さい |

`total_blobs`は`nmethods + adapters`だけではなく、runtime stubなども含む。
`nmethods`はJava sourceのmethod数そのものではなく、Code Cacheに存在するcompiled codeの数として読む。

### Full and Compilation State

| Field | Meaning | Healthy Baseline |
| --- | --- | --- |
| `full_count` | Code Cacheが満杯になった累積回数 | `0` |
| `Compilation` | 現在JITコンパイル可能か | `enabled` |
| `stopped_count` | JITコンパイルが停止した累積回数 | `0` |
| `restarted_count` | 停止後にJITが再開した累積回数 | `0` |

これらは観測時点の値と累積値が混在する。

- `Compilation: disabled`: 観測時点でJIT停止中。
- `enabled`かつ`stopped_count > 0`: 過去に停止したが、現在は利用可能。
- `restarted_count > 0`: Sweeperなどによる領域確保後、JITが再開した証拠。
- `full_count > 0`: 観測時に回復済みでも、過去に満杯へ到達した証拠。

### Segmented Output

通常の大きいCode Cacheでは、JDK設定により次の複数領域が表示される場合がある。

```text
CodeHeap 'non-profiled nmethods'
CodeHeap 'profiled nmethods'
CodeHeap 'non-nmethods'
```

今回のように`ReservedCodeCacheSize`を小さくするとsegmented code cacheが無効になり、
単一の`CodeCache:`として表示される場合がある。どちらも異常ではなく、表示された各領域の
`used`、`max_used`、`free`と、最終的なcompilation状態を確認する。

### Before and After Comparison

負荷前と負荷後の出力を別fileへ保存する。

```bash
jcmd "$PID" Compiler.codecache | tee /tmp/codecache-before.txt

# Run load here

jcmd "$PID" Compiler.codecache | tee /tmp/codecache-after.txt
diff -u /tmp/codecache-before.txt /tmp/codecache-after.txt || true
```

主に次の差分を読む。

1. `used`と`max_used`の増加量
2. `free`の減少量
3. `nmethods`の増加量
4. `full_count`の増加
5. `stopped_count`と`restarted_count`の増加
6. `Compilation`がenabledかdisabledか

同じmethodだけを繰り返す負荷では、JITコンパイル完了後に`nmethods`と`used`が横ばいになる。
これは負荷失敗ではなく、同じcompiled codeが再利用されているためである。

## Tiered Compilation Levels

HotSpot JVMは実行profileを収集しながら、methodを段階的にコンパイルする。

| Level | Execution / Compiler | Purpose |
| --- | --- | --- |
| 0 | Interpreter | bytecodeを解釈実行し、実行profileを収集する |
| 1 | C1、profileなし | 短時間で単純なcompiled codeを生成する |
| 2 | C1、限定profile | 一部の実行profileを収集する |
| 3 | C1、完全profile | C2最適化に必要なprofileを収集する |
| 4 | C2 | profileを利用して強く最適化したcodeを生成する |

LevelはHTTP request数だけでは決まらない。主に次が影響する。

- methodの呼び出し回数
- loopのbackedge回数
- methodやloopの実行時間
- JVMが収集した型・分岐profile
- compile queueの状態
- `CompileThreshold`などのJVM option

`/load?count=100000`を100回実行すると、単純計算では約1,000万回loopする。method呼び出しは
100回でもbackedge counterが大きく進むため、Level 4へ到達する可能性が高い。長いloopの途中で
On-Stack Replacementが行われ、method全体の次回呼び出しを待たずにcompiled codeへ切り替わる
場合もある。

`Compiler.codecache`はCode Cache全体の容量を表示するcommandであり、個別methodのLevelは表示しない。
個別methodは次で確認する。

```bash
jcmd "$PID" Compiler.codelist | grep 'LoadService.calculate'
jcmd "$PID" Compiler.queue
```

出力例:

```text
4330 4 0 com.example.codecache.service.LoadService.calculate(I)J [...]
```

この例の2列目`4`がcompilation Levelであり、`LoadService.calculate`はC2 compilation済みである。
compile ID、memory address、PIDは起動ごとに変わるため、固定値として監視しない。

compileの遷移を起動時から観測したい場合は、別試験として`-XX:+PrintCompilation`を追加する。

```bash
java \
  -XX:ReservedCodeCacheSize=64m \
  -XX:+PrintCompilation \
  -jar target/code-cache-demo-*.jar \
  2>&1 | tee /tmp/compilation.log
```

Spring Boot全体のcompile logが大量に出るため、対象methodを抽出する。

```bash
grep 'LoadService::calculate' /tmp/compilation.log
```

Level 4へ到達した後に同じmethodを呼び続けても、通常は同じcompiled codeが再利用される。
そのため、100回よりさらに回数を増やしてもCode Cache使用量が同じ割合で増え続けるとは限らない。

## Overflow Judgment

次の優先順位で満杯状態を判定する。

| Priority | Evidence | Judgment |
| --- | --- | --- |
| 1 | `full_count > 0` | Code Cache満杯へ到達済み |
| 2 | `stopped_count > 0` | JIT停止が発生済み |
| 3 | `Compilation: disabled` | 現在JIT停止中 |
| 4 | `restarted_count > 0` | 停止後にJITが回復済み |
| 5 | JVM warning log | 発生時刻と原因を確認可能 |
| 6 | `free`がほぼゼロ | 接近を示すが、単独では満杯確定にしない |

`free`は観測直前・直後にSweeperで変化するため、少ないことだけで満杯と断定しない。
累積counterとJVM logを合わせて判断する。

## Execution Flow

```text
通常Tiered Compilation、10MB、検証profileで起動
        |
        v
Hot probeを100回warm-up
        |
        v
Generatorで500 class生成・warm-up
        |
        v
Compiler.codecacheを確認
        |
        +-- 満杯でない --> 250 class追加
        |
        +-- full_count > 0 --> Hot/Coldを初回比較
        v
結果・JVM optionを記録
        |
        v
必要な場合だけ-Xcompまたはflushing無効の補助試験
        |
        v
processを停止して生成classを破棄
```

各再起動後はPIDが変わるため、必ず`jcmd -l`から取得し直す。

## Preparation

通常起動中のアプリケーションをCtrl+Cで停止し、実行可能JARを作成する。

```bash
cd app
./mvnw clean package
```

`clean package`は通常のMaven lifecycleでテストも実行する。直前に別途`clean test`を実行する
必要はない。

## Test A: Generator with Normal Tiered Compilation

`-Xcomp`を指定せず、`UseCodeCacheFlushing`の既定値`true`を維持する。

```bash
java \
  -XX:ReservedCodeCacheSize=10m \
  -jar target/code-cache-demo-*.jar \
  --spring.profiles.active=code-cache-test
```

別ターミナルでPIDを取得する。

```bash
PID=$(jcmd -l | awk '/code-cache-demo-.*\.jar/{print $1; exit}')
echo "$PID"
jcmd "$PID" VM.flags
jcmd "$PID" Compiler.codecache
```

Cold probeはまだ呼ばず、Hot probeだけをwarm-upする。

```bash
for i in $(seq 1 100); do
  curl -fsS 'http://localhost:8080/code-cache/probe/hot?count=100000' >/dev/null
done

jcmd "$PID" Compiler.codelist | grep 'CodeCacheProbeService.hot'
jcmd "$PID" Compiler.codecache | tee /tmp/codecache-generator-before.txt
```

最初の500 classを生成し、各methodを1万回呼ぶ。

```bash
curl -fsS -X POST \
  'http://localhost:8080/code-cache/generator/generate?classes=500&warmupIterations=10000'

jcmd "$PID" Compiler.codecache
```

満杯でなければ250 class追加する。JITのtimingとSweeperの回収量は起動ごとに変わるため、
500 + 250 classは固定の満杯条件ではない。`full_count=0`なら新しいclassをさらに追加する。

```bash
curl -fsS -X POST \
  'http://localhost:8080/code-cache/generator/generate?classes=250&warmupIterations=10000'

jcmd "$PID" Compiler.codecache | tee /tmp/codecache-generator-full.txt
```

750 classでも満杯にならない場合は、既存methodのwarm回数を増やすのではなく、異なるmethodを
1,000 class追加する。

```bash
curl -fsS -X POST \
  'http://localhost:8080/code-cache/generator/generate?classes=1000&warmupIterations=10000'

jcmd "$PID" Compiler.codecache
```

同じmethodがすでにcompile済みなら、warm回数だけを増やしてもCode Cacheは大きく増えない。
`generator/status`の`totalGenerated`と、次のcompiled method数を合わせて確認する。

```bash
curl -fsS http://localhost:8080/code-cache/generator/status
jcmd "$PID" Compiler.codelist | grep -c 'com.example.codecache.generated.WorkUnit'
```

`full_count > 0`または`Compilation: disabled`を確認してから、Hot/Coldを各1回呼ぶ。

```bash
curl -fsS 'http://localhost:8080/code-cache/probe/hot?count=100000'
echo
curl -fsS 'http://localhost:8080/code-cache/probe/cold?count=100000'
echo
```

### Success Evidence

次のいずれかが観測できれば、通常Tiered Compilation条件で満杯に到達している。

- `full_count`が1以上になる。
- `stopped_count`が1以上になる。
- `restarted_count`が1以上になる。
- JVMログにCode Cache満杯またはcompiler停止を示す記録がある。

観測タイミングによってはSweeperがすでに回収し、`Compilation: enabled`へ戻っていることがある。
その場合も`full_count`、`stopped_count`、`restarted_count`とログを証跡にする。

## Test B: Forced Compilation and Persistent Stop

この試験はCode Cache満杯機構を短時間で確認する補助試験であり、通常運用に近いlatency比較には
使用しない。Test Aまたはgenerator試験で回収・再開を確認した後に限り、
`UseCodeCacheFlushing`を無効化して停止継続時の挙動を確認する。
この条件は通常運用を再現するものではなく、障害状態を強制する実験である。

まずflushingを有効のまま、10MBと`-Xcomp`で起動する。通常の検証では詳細logを有効にせず、
`jcmd`で状態を確認する。

```bash
java \
  -Xcomp \
  -XX:ReservedCodeCacheSize=10m \
  -jar target/code-cache-demo-*.jar
```

この条件で`full_count`、停止、回復を観測できれば、application変更は不要である。
回収後にすぐ`enabled`へ戻るため停止継続も確認したい場合だけ、次のflushing無効条件へ進む。

各optionの必要性:

| Option | Default Test | Reason |
| --- | --- | --- |
| `-Xcomp` | 必須 | 呼び出したmethodを積極的にcompileして圧迫する |
| `-XX:ReservedCodeCacheSize=10m` | 必須 | 満杯へ到達しやすい上限にする |
| `-jar ...` | 必須 | 対象applicationを起動する |
| `-XX:+PrintCodeCache` | 不要 | `jcmd Compiler.codecache`で代替する |
| `-Xlog:codecache*=debug` | 通常不要 | 詳細調査時だけ有効にする |
| `tee` | 通常不要 | log保存が必要な試験だけ使用する |

別terminalで確認する。

```bash
PID=$(jcmd -l | awk '/code-cache-demo-.*\.jar/{print $1; exit}')
jcmd "$PID" Compiler.codecache
```

起動に時間がかかる主因は`-Xcomp`であり、これはCode Cacheを圧迫するための本体なので削除しない。
debug出力を削除しても起動時間が長い場合は、その時間自体が`-Xcomp`によるcompile処理である。

`-Xcomp`ではSpring Boot起動と初回HTTP requestの両方に時間がかかる。起動logの
`Started CodeCacheDemoApplication`、TCP 8080のlisten、health responseを分けて確認する。

```bash
ss -lntp | grep ':8080'
curl --max-time 30 -i http://localhost:8080/actuator/health
```

`--logging.level.root=WARN`を指定すると起動完了のINFO logも非表示になり、未起動と誤認しやすいため、
基本手順では使用しない。

満杯の発生時刻やSweeperの詳細が必要になった場合だけ、次を追加して再実行する。

```bash
-Xlog:codecache*=debug
```

```bash
java \
  -Xcomp \
  -XX:ReservedCodeCacheSize=16m \
  -XX:-UseCodeCacheFlushing \
  -XX:+PrintCodeCache \
  -Xlog:codecache*=debug \
  -jar target/code-cache-demo-*.jar \
  2>&1 | tee /tmp/code-cache-forced.log
```

- `-Xcomp`: 初回実行時から積極的にコンパイルし、Code Cache消費を促す。
- `-XX:-UseCodeCacheFlushing`: Sweeperによる通常の回復を無効化する。

起動しない場合も有効な結果である。起動ログ、終了code、最終Code Cache状態を記録する。
起動できた場合はTest Aと同じ負荷および`jcmd`確認を行い、HTTP 200継続可否と応答時間を記録する。

## Stop Procedure

まず負荷生成をCtrl+Cで停止し、アプリケーションを起動したターミナルでCtrl+Cを押す。
別ターミナルから停止する場合:

```bash
kill "$PID"
```

終了しない場合だけ、PIDが対象JARであることを再確認してから強制終了する。

```bash
jcmd -l
kill -KILL "$PID"
```

## Result Recording

各条件について[Performance Test Result](test-result.md)へ次を記録する。

| Category | Values |
| --- | --- |
| Environment | JDK完全version、OS、CPU、Git commit |
| JVM options | cache size、flushing、`-Xcomp`有無 |
| Before / after | used、max_used、free、nmethods |
| Full state | full_count、enabled/disabled、stopped/restarted |
| Application | 起動可否、HTTP status、elapsedNanos |
| Logs | 警告messageと発生時刻 |
| Recovery | 自動回復、再起動が必要、起動不能 |

## Judgment

- Test Aでは満杯後もHTTP応答を確認し、回収・再開の有無を判定する。
- Test BではJIT停止が継続した場合の起動・応答への影響を判定する。
- JVMが終了しなくても、JIT停止や顕著な遅延があればCode Cache不足の影響ありとする。
- 試験後は64MB条件へ戻し、強制用オプションを通常起動設定へ残さない。

## Related Documents

- [Performance Test Plan](test-plan.md)
- [Performance Test Result](test-result.md)
- [Local Development](../operations/local-development.md)
- [Troubleshooting](../operations/troubleshooting.md)
