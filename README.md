# Java Code Cache Verification

Java HotSpot JVMのCode CacheとTiered Compilationの挙動を、Spring Bootサンプルを使って
ローカルおよびAmazon EC2で検証するプロジェクトです。

ローカルでは`jcmd`を使ってJITコンパイル段階、Code Cache使用量、満杯時の停止・回復を確認します。
同じ資材をUbuntu EC2へcloneし、CloudWatch AgentのJMX収集とJVMログでも監視しました。

## Goals

- HTTP負荷によってJITコンパイルとCode Cache使用量が変化することを確認する。
- `jcmd`で個別メソッドのコンパイルLevelとCode Cache全体の状態を確認する。
- Code Cache満杯時のJIT停止、Sweeperによる回収、再開、HTTP応答への影響を確認する。
- ローカルで確認したアプリケーションをUbuntu EC2で再現する。
- CloudWatch AgentからJVMメトリクスとアプリケーションログを送信する。

## Current Status

- Spring Bootサンプルアプリケーション: 実装済み
- Maven自動テスト: 14 tests、failures 0、errors 0
- `/actuator/health`: ローカルでHTTP 200を確認済み
- `/load`: 正常値のHTTP 200、範囲外のHTTP 400を確認済み
- `LoadService.calculate`: Tiered Compilation Level 4を確認済み
- Code Cache GeneratorとHot/Cold probe: 実装・ローカル検証済み
- 10MB Code Cache満杯、JIT停止、Cold probe遅延: 再現済み
- Ubuntu EC2デプロイ: 完了
- CloudWatch監視: `used`、`max`、`committed`とJVM warningを確認済み
- EC2 32MB、flushing無効: 16,000 classで満杯、JIT停止、Cold遅延を再現済み
- EC2 64MB、flushing無効: 20,000 class後も満杯にならず、JIT継続を確認済み
- EC2 32MB、flushing有効: 20,000 classの間にSweeper回収とJIT継続を確認済み

EC2とCloudWatchの再実施手順は
[EC2 and CloudWatch Verification](docs/performance/ec2-cloudwatch-verification.md)を参照してください。
実測結果の詳細とグラフは、末尾の公開検証記事に記録しています。

## Technology Stack

| Category | Technology |
| --- | --- |
| Language | Java 21 |
| JVM | Ubuntu OpenJDK HotSpot |
| Framework | Spring Boot 4.1.0 |
| Web | Spring Web MVC |
| Health check | Spring Boot Actuator |
| Build | Maven Wrapper |
| Local environment | WSL2 Ubuntu 22.04 LTS |
| Verification server | Ubuntu Server 26.04 LTS EC2 x86_64、t3.micro |
| Monitoring | Amazon CloudWatch Agent、JMX、CloudWatch Logs |
| Runtime generation | Byte Buddy 1.18.2 |

## Repository Structure

```text
.
├── app/        Spring Boot application and unit/HTTP tests
├── config/     CloudWatch Agent and service configuration templates
├── scripts/    reproducible build, load, and deployment scripts
├── tests/      reserved for future integration and deployment tests
├── docs/       architecture, API, operations, performance, decisions
└── README.md
```

Spring Bootの単体・HTTPテストは`app/src/test/`に配置します。トップレベルの`tests/`は、
将来追加する結合試験およびデプロイ試験のために保持します。

## Verification Features

このプロジェクトには目的の異なる2種類のCode Cacheテストがあります。最初に作成した単一計算APIも、
通常のJITコンパイルを理解するためのテストとして残しています。

### 1. Basic Load Test

`GET /load`で、同じ計算メソッドを繰り返し実行する基本テストです。

| Code | Role |
| --- | --- |
| [`LoadController.java`](app/src/main/java/com/example/codecache/api/LoadController.java) | `/load`のquery parameter検証、処理時間計測、JSON応答 |
| [`LoadService.java`](app/src/main/java/com/example/codecache/service/LoadService.java) | `count`回の計算を行う`calculate`メソッド |
| [`LoadResponse.java`](app/src/main/java/com/example/codecache/api/LoadResponse.java) | `count`、`result`、`elapsedNanos`を返す |

このテストで確認すること:

- InterpreterからC1/C2へのTiered Compilation
- `LoadService.calculate`がLevel 4へ到達すること
- 通常のSpring Boot起動とHTTP負荷によるCode Cache使用量の変化
- 同じメソッドはコンパイル後に再利用され、呼び出し回数に比例してCode Cacheが増えないこと

同じ`calculate`を何度呼んでも、生成されるcompiled codeは同じメソッドの少数versionです。
そのため、このAPIはCode Cacheを満杯にするためではなく、通常JIT動作の基準として使用します。

### 2. Code Cache Overflow Test

Byte Buddyで多数の異なるクラス・メソッドをruntime生成し、Code Cacheを意図的に圧迫するテストです。

| Code | Role |
| --- | --- |
| [`CodeCacheTestController.java`](app/src/main/java/com/example/codecache/codecache/CodeCacheTestController.java) | Generator、warm、status、Hot/Cold APIを提供 |
| [`CodeCacheGeneratorService.java`](app/src/main/java/com/example/codecache/codecache/CodeCacheGeneratorService.java) | 異なる`WorkUnit`クラスを生成し、各メソッドをwarm-up |
| [`GeneratedWorkUnit.java`](app/src/main/java/com/example/codecache/codecache/GeneratedWorkUnit.java) | 動的生成クラスが実装する共通interface |
| [`CodeCacheProbeService.java`](app/src/main/java/com/example/codecache/codecache/CodeCacheProbeService.java) | 同じ計算内容を持つ別メソッド`hot`と`cold` |
| [`GeneratorResult.java`](app/src/main/java/com/example/codecache/codecache/GeneratorResult.java) | 生成数、呼び出し数、処理時間を返す |
| [`GeneratorStatus.java`](app/src/main/java/com/example/codecache/codecache/GeneratorStatus.java) | process内の累計生成数を返す |
| [`ProbeResponse.java`](app/src/main/java/com/example/codecache/codecache/ProbeResponse.java) | probe種別、計算結果、処理時間を返す |

Generatorは1クラスにつき1つの異なる`execute`メソッドを生成します。各メソッドをJITコンパイル
させることで、同じメソッドのループでは増えなかったcompiled code数を段階的に増やします。

Hot/Cold probeの役割:

- Hot: Code Cache満杯前に繰り返し実行し、Level 4へコンパイルしておく。
- Cold: 負荷投入後まで呼ばず、未コンパイル状態からの実行時間を測る。満杯時はInterpreter実行の影響を見る。
- 両方は同じ計算式と反復回数を使用するため、`elapsedNanos`を比較できる。

検証APIは`code-cache-test` profileでだけ有効です。通常起動では`/code-cache/**`はHTTP 404に
なり、基本の`/load`と`/actuator/health`だけを使用できます。

### Test Selection

| Goal | Test |
| --- | --- |
| JITの段階とLevel 4を確認 | Basic `/load` |
| 通常負荷でCode Cacheが増えることを確認 | Basic `/load` |
| 同一メソッドでは使用量が頭打ちになることを確認 | Basic `/load` |
| Code Cacheを意図的に満杯にする | Generator |
| compiler停止を確認 | Generator + `jcmd` |
| 満杯前後のレスポンス遅延を確認 | Hot/Cold probe |
| 容量拡張による遅延改善を確認 | Generator + Hot/Coldを32MB/64MBで比較 |
| Sweeperによる回収を確認 | 32MB、flushing有効でbatchごとの`used`と`nmethods`を比較 |

## EC2 Verification Results

| Condition | Generated classes | Code Cache | Compilation | Hot | Cold | Result |
| --- | ---: | --- | --- | ---: | ---: | --- |
| 32MB、flushing無効 | 16,000 | peak 32,767KB、`full_count=1` | disabled | 2.12ms | 24.46ms | 満杯とCold約11.5倍遅延を再現 |
| 64MB、flushing無効 | 20,000 | max 64MB、used約36MB | enabled | 1.98ms | 4.69ms | 満杯を回避、ColdはJIT停止時より約5.2倍高速 |
| 32MB、flushing有効 | 20,000 | peak 17,931KB、最終13,063KB | enabled | 12.68ms | 6.06ms | 回収により32MB内でJIT継続 |

32MB・flushing有効条件では、例えばbatch 13から14で`used`が17,412KBから12,970KB、
`nmethods`が13,885から10,155へ同時に減少しました。`max_used`はpeak 17,931KBを維持したため、
現在値とコンパイル済みコード数の減少をSweeperによる回収の証拠としました。
32MB・flushing有効試験は単発のHot/Cold大小ではなく、回収とJIT継続を判定対象にしています。

## Prerequisites

ローカル環境には次が必要です。

- OpenJDK 21 JDK
- Git
- curl
- unzip

Ubuntuでは次のコマンドで導入します。

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk git curl unzip
```

確認します。

```bash
java -version
javac -version
jcmd -h
git --version
curl --version
```

詳細は[Local Development](docs/operations/local-development.md)を参照してください。

## Clone

```bash
git clone https://github.com/YutaSSuzuki/code_cashe_kenshou.git
cd code_cashe_kenshou
```

## Test

```bash
cd app
./mvnw clean test
```

`clean test`はJava、テスト、`pom.xml`を変更した後、およびGitへ追加する前に実行します。

## Run the Basic Load Test Locally

開発用起動:

```bash
cd app
./mvnw spring-boot:run
```

別ターミナルで確認します。

```bash
curl -i http://localhost:8080/actuator/health
curl -i "http://localhost:8080/load?count=1000"
curl -i "http://localhost:8080/load?count=0"
```

期待結果:

- `/actuator/health`: HTTP 200、`UP`
- `/load?count=1000`: HTTP 200、計算結果JSON
- `/load?count=0`: HTTP 400

API仕様は[API Specification](docs/api/index.md)を参照してください。

## Inspect the Basic Test with `jcmd`

実行可能JARを作成します。`clean package`でも通常のMaven lifecycleによりテストが実行されます。

```bash
cd app
./mvnw clean package

java \
  -XX:ReservedCodeCacheSize=64m \
  -jar target/code-cache-demo-*.jar
```

別ターミナルで対象PIDとCode Cacheを確認します。

```bash
PID=$(jcmd -l | awk '/code-cache-demo-.*\.jar/{print $1; exit}')
echo "$PID"

jcmd "$PID" VM.version
jcmd "$PID" VM.flags
jcmd "$PID" Compiler.codecache
jcmd "$PID" Compiler.codelist | grep 'LoadService.calculate'
```

負荷例:

```bash
for i in $(seq 1 100); do
  curl -fsS "http://localhost:8080/load?count=100000" >/dev/null || break
done
```

負荷後に再度確認します。

```bash
jcmd "$PID" Compiler.codecache
```

## Code Cache Overflow Test

Code Cache満杯を確認する試験では、通常起動を停止してから検証用JVMを1つだけ起動します。
EC2の本検証では32MBとflushing無効を使い、通常のTiered Compilationは維持します。

```bash
cd app

java \
  -XX:ReservedCodeCacheSize=32m \
  -XX:-UseCodeCacheFlushing \
  -jar target/code-cache-demo-*.jar \
  --spring.profiles.active=code-cache-test
```

`-XX:-UseCodeCacheFlushing`は満杯状態を維持してJIT停止後のCold遅延を測る検証専用optionです。
通常運用の推奨設定ではありません。`-Xcomp`はHot/Cold比較の条件を変えるため使用しません。

別ターミナルで対象PIDを取得します。

```bash
PID=$(jcmd -l | awk '/code-cache-demo-.*\.jar/{print $1; exit}')
jcmd "$PID" Compiler.codecache
```

Cold probeはまだ呼ばず、Hot probeだけをwarm-upしてからGeneratorを段階実行します。

```bash
for i in $(seq 1 100); do
  curl -fsS 'http://localhost:8080/code-cache/probe/hot?count=100000' >/dev/null
done

curl -fsS -X POST \
  'http://localhost:8080/code-cache/generator/generate?classes=1000&warmupIterations=10000'
```

各batchの後に確認します。

```bash
jcmd "$PID" Compiler.codecache
```

1,000 classずつ追加し、次の状態になれば満杯です。

```text
full_count > 0
Compilation: disabled
```

`stopped_count`はJDKや停止経路によって0のままの場合があるため、`full_count`、`Compilation`、
JVM warningを組み合わせて判定します。同じ生成済みメソッドのwarm回数ではなく、新しいクラスを
追加することが重要です。

満杯後にHot/Coldを同じ`count`で測定します。

```bash
curl -fsS \
  'http://localhost:8080/code-cache/probe/hot?count=1000000'
echo

curl -fsS \
  'http://localhost:8080/code-cache/probe/cold?count=1000000'
echo
```

両レスポンスの`result`が同じで、Coldの`elapsedNanos`がHotより大きいことを確認します。
また、`Compiler.codelist`にHotだけが存在し、Coldが未コンパイルであることを確認します。

```bash
jcmd "$PID" Compiler.codelist \
  | grep 'CodeCacheProbeService'
```

追加比較ではJVMを再起動して状態をリセットし、次の2条件で同じ20,000 classを生成します。

```text
64MB、-XX:-UseCodeCacheFlushingあり: 容量拡張だけの効果を確認
32MB、-XX:-UseCodeCacheFlushingなし: 既定の回収動作を確認
```

通常設定へ検証profile、小さいCode Cache、flushing無効を残さないでください。詳細な試験順、
結果の読み方、停止方法は
[Code Cache Overflow Test](docs/performance/code-cache-overflow-test.md)を参照してください。

## EC2 and CloudWatch

EC2では次の流れを実施済みです。

1. ソース、Maven Wrapper、設定、docsをGitへ追加する。
2. Ubuntu Server 26.04 LTS EC2でリポジトリをcloneする。
3. EC2上でテスト、ビルド、起動確認を行う。
4. JMXをlocalhostだけで有効にする。
5. CloudWatch Agentから`jvm.memory.pool.used`、`max`、`committed`とログを60秒間隔で送信する。
6. CloudWatch上の値と`jcmd`の値を比較する。

JMX 9010は`127.0.0.1`だけでlistenし、Security Groupでは公開しません。CloudWatchの
`name=CodeCache`系列で容量を確認し、満杯の最終判定には次を使用します。

```bash
jcmd "$PID" Compiler.codecache
```

CloudWatchの標準JMXメトリクスでは`full_count`やcompiler停止状態を直接取得できないため、
`used`、`max`、`committed`、JVM warning、`jcmd`を組み合わせます。

必要資材とAWS要件は[Deployment Operation](docs/operations/deployment.md)、監視項目は
[Monitoring](docs/operations/monitoring.md)を参照してください。

## Documentation

ドキュメントの入口は[Project Documentation](docs/index.md)です。

- [Documentation Policy](docs/documentation-policy.md)
- [Current State](docs/architecture/current-state.md)
- [Runtime Flow](docs/architecture/runtime-flow.md)
- [Local Development](docs/operations/local-development.md)
- [Load API Implementation Guide](docs/api/load-api-implementation.md)
- [Code Cache Test API](docs/api/code-cache-test-api.md)
- [Performance Test Plan](docs/performance/test-plan.md)
- [Code Cache Overflow Test](docs/performance/code-cache-overflow-test.md)
- [Performance Test Result](docs/performance/test-result.md)
- [EC2 and CloudWatch Verification](docs/performance/ec2-cloudwatch-verification.md)
- [Roadmap](docs/roadmap/current-roadmap.md)
- [Changelog](docs/changelog/CHANGELOG.md)

公開した検証記事:

- [JVM Code Cacheの検証](https://yutassuzuki.github.io/page/Java/code_cache.html)
- [JVM Code Cacheの仕組み](https://yutassuzuki.github.io/page/Java/code_cache_overview.html)

Current Stateには現在の状態だけを記載し、設計判断はADR、未実装の構想はProposal、
完了した変更はChangelogへ分離します。

## Git Scope

Gitへ追加するもの:

- Java sourceとtest
- `pom.xml`
- Maven Wrapper（`mvnw`、`mvnw.cmd`、`.mvn/`）
- 再現可能な設定とscript
- docs

Gitへ追加しないもの:

- `target/`と生成JAR
- JVM log、PID file、一時測定file
- IDE固有設定
- AWS認証情報、秘密鍵、PAT
