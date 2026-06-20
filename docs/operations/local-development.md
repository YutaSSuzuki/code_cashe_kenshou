# Local Development: Spring Boot Code Cache Sample

## Status

Active

## Purpose

学習者自身がSpring Bootサンプルを段階的に実装し、EC2へ進む前にローカル環境で
ビルド、API、テスト、Code Cacheの変化を確認する。

この文書は完成コードを提供せず、作成対象、確認方法、完了条件を定義する。

## Current Progress

2026-06-20時点の進捗:

- [x] Spring Initializrの雛形を`app/`へ展開
- [x] Maven Wrapperの不足ファイルを復元
- [x] `./mvnw clean test`成功（14 tests、failures 0、errors 0）
- [x] 未変更アプリケーションの起動と`/actuator/health`を確認
- [x] `/load`、計算service、レスポンス、テストを実装
- [x] `/load?count=1000`がHTTP 200になることを確認
- [x] `/load?count=0`がHTTP 400になることを確認
- [ ] 実行可能JARを検証用JVMオプションで起動
- [ ] `jcmd`で負荷前後のCode Cacheを比較
- [ ] ローカル検証結果を記録してGitへ追加

Generatorによる満杯・JIT停止・Hot/Cold差の実装検証は完了している。上記未完了項目は、
学習者が手順を再実行して自身の結果を記録するために残す。

次に実施するのは[Step 5: Build and Run with JVM Options](#step-5-build-and-run-with-jvm-options)である。

## Command Roles

| Command | Role | When to Run Again |
| --- | --- | --- |
| `./mvnw clean test` | 生成物を削除してコンパイルと全自動テストを実行 | Java、test、`pom.xml`変更後、およびGit追加前 |
| `./mvnw spring-boot:run` | 開発用にアプリを起動して手動HTTP確認 | 手動確認が必要なとき |
| `./mvnw clean package` | テスト後に実行可能JARを作成 | Code Cache検証前、デプロイ資材確認時 |

`spring-boot:run`はアプリケーション起動用であり、全テスト成功の代わりにはならない。
一方、直前の`clean test`成功後にコードや`pom.xml`を変更していなければ、同じテストを
起動直後に繰り返す必要はない。

## Scope

この段階に含めるもの:

- Spring InitializrによるMavenプロジェクトの作成
- ヘルスチェックの確認
- Code Cache負荷生成APIの実装
- 入力値の検証と自動テスト
- `jcmd`による負荷前後のCode Cache確認
- EC2で再利用するソースとMaven WrapperのGit追加

この段階に含めないもの:

- CloudWatch Agentのローカル導入
- EC2、IAM、Security Groupの作成
- systemdサービス化
- CloudWatchメトリクス、ログ、アラームの確認

## Prerequisites

ローカル環境はWSL2 Ubuntu 22.04 LTSを前提とする。

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk git curl unzip
```

Java 21が選択されていることを確認する。

```bash
java -version
javac -version
jcmd -h
git --version
curl --version
```

`java -version`が21でない場合は切り替える。

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

## Technology Baseline

| Item | Selection | Reason |
| --- | --- | --- |
| Language | Java 21 | ローカルとEC2の検証条件を揃える |
| JVM | Ubuntu OpenJDK HotSpot | Ubuntu標準パッケージで再現する |
| Framework | Spring Boot 4.1.0 | 2026-06-20時点のSpring Initializr安定版既定値 |
| Build | Maven Wrapper | Maven本体のバージョンを環境間で揃える |
| Dependencies | Spring Web、Spring Boot Actuator | 負荷APIとヘルスチェックを提供する |
| Packaging | Executable JAR | ローカルとEC2で同じ成果物を実行する |

バージョンを変更する場合は、理由と実際の値をこの文書および試験結果へ記録する。
SNAPSHOTまたはMILESTONE版は使用しない。

## Step 1: Create the Project Skeleton

Spring Initializrで次の値を選択する。

| Field | Value |
| --- | --- |
| Project | Maven |
| Language | Java |
| Spring Boot | 4.1.0 |
| Group | `com.example` |
| Artifact | `code-cache-demo` |
| Name | `code-cache-demo` |
| Package name | `com.example.codecache` |
| Packaging | Jar |
| Java | 21 |
| Dependencies | Spring Web、Spring Boot Actuator |

ブラウザのSpring Initializrを使うか、次のコマンドで同じ雛形を取得する。

```bash
curl -fG https://start.spring.io/starter.zip \
  --data-urlencode type=maven-project \
  --data-urlencode language=java \
  --data-urlencode bootVersion=4.1.0 \
  --data-urlencode javaVersion=21 \
  --data-urlencode groupId=com.example \
  --data-urlencode artifactId=code-cache-demo \
  --data-urlencode name=code-cache-demo \
  --data-urlencode packageName=com.example.codecache \
  --data-urlencode dependencies=web,actuator \
  -o /tmp/code-cache-demo.zip

unzip /tmp/code-cache-demo.zip -d app
```

既存の`app/README.md`は保持する。展開後、最低限次が存在することを確認する。

```text
app/
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src/
    ├── main/
    └── test/
```

### Completion Check

```bash
cd app
./mvnw test
```

- Maven WrapperがMavenと依存ライブラリを取得できる。
- 生成された初期テストが成功する。
- `pom.xml`のJava、Spring Boot、依存関係を自分で説明できる。

## Step 2: Start the Unmodified Application

まずコードを変更せず起動する。

```bash
cd app
./mvnw spring-boot:run
```

別ターミナルで確認する。

```bash
curl -i http://localhost:8080/actuator/health
```

### Completion Check

- HTTP 200が返る。
- レスポンスの`status`が`UP`である。
- Ctrl+Cで正常に停止できる。

## Step 3: Implement the Load API

[API specification](../api/index.md)に従い、`GET /load?count={iterations}`を実装する。
クラス構造、処理順、入力境界、テスト方法は
[Load API Implementation Guide](../api/load-api-implementation.md)を正とする。

実装時に自分で決めて文書化する項目:

- Controllerと計算処理を同じクラスに置くか分けるか。
- `count`の既定値と最大値。
- 計算結果のレスポンス形式。
- 無効値をHTTP 400にする方法。
- JITが処理自体を除去しにくく、結果をレスポンスで利用する計算方法。

単に待機する`Thread.sleep`や外部I/Oは、JITコンパイルを促すCPU処理にならないため使用しない。
動的クラス生成ライブラリは初回検証では追加せず、通常のウォームアップによる変化を先に観測する。

### Completion Check

```bash
curl -i "http://localhost:8080/load?count=1000"
curl -i "http://localhost:8080/load?count=0"
curl -i "http://localhost:8080/load?count=-1"
curl -i "http://localhost:8080/load?count=not-a-number"
```

- 正常値はHTTP 200と計算結果を返す。
- 仕様で許可しない値はHTTP 400を返す。
- 例外スタックトレースなどの内部情報をレスポンスへ露出しない。

## Step 4: Add Automated Tests

最低限、次の観点をテストする。

| Test | Expected Result |
| --- | --- |
| Application context | Springコンテキストが起動する |
| Health endpoint | HTTP 200、`UP` |
| Load API default | パラメーター省略時にHTTP 200 |
| Load API valid boundary | 最小値・最大値が仕様どおり動く |
| Load API invalid value | 負数、上限超過、非数値がHTTP 400 |
| Calculation | 同じ入力から再現可能な結果を返す |

### Completion Check

```bash
cd app
./mvnw clean test
```

- 全テストが成功する。
- ControllerのHTTP仕様と計算処理の両方が検証される。

このコマンドが成功した直後に`spring-boot:run`で手動確認し、その間にコードを変更していない
場合は、Step 5の前に同じ`clean test`を再実行しなくてよい。Step 5の`clean package`でも
通常のMaven lifecycleによりテストが実行される。

## Step 5: Build and Run with JVM Options

```bash
cd app
./mvnw clean package

java \
  -XX:ReservedCodeCacheSize=64m \
  -XX:+PrintCodeCache \
  -jar target/code-cache-demo-*.jar
```

64MBは逼迫挙動を観測しやすくするための検証値であり、本番推奨値ではない。

### Completion Check

```bash
curl -i http://localhost:8080/actuator/health
jcmd -l
```

- パッケージされたJARから起動できる。
- `jcmd -l`に対象Javaプロセスが表示される。

## Step 6: Verify Code Cache Locally

`<PID>`は`jcmd -l`で確認した対象プロセスIDに置き換える。

負荷前の値を取得する。

```bash
jcmd <PID> VM.version
jcmd <PID> VM.flags
jcmd <PID> Compiler.codecache
```

小さい負荷から段階的に増やす。

```bash
for i in $(seq 1 100); do
  curl -fsS "http://localhost:8080/load?count=100000" >/dev/null || break
done
```

負荷後の値を取得する。

```bash
jcmd <PID> Compiler.codecache
```

### Completion Check

- 負荷前後の`Compiler.codecache`出力を取得できる。
- CodeHeapごとの`size`、`used`、`max_used`、`free`を比較できる。
- HTTPエラーやJVM停止が発生していない。
- 実行条件と結果を`performance/test-result.md`へ記録できる。

使用量が期待どおり変化しなくても、直ちに実装失敗とはしない。JITの判断は実行状況に依存するため、
JDK、JVMフラグ、負荷回数、観測値を保存してから次の負荷条件を検討する。

## Step 7: Prepare Git Assets

EC2でclone後に再現できる資材だけをGitへ追加する。

追加対象:

- `app/pom.xml`
- `app/mvnw`、`app/mvnw.cmd`、`app/.mvn/`
- `app/src/main/`
- `app/src/test/`
- `app/README.md`
- 関連する`docs/`

追加しないもの:

- `app/target/`
- `.class`、生成JAR
- JVMログ、PIDファイル、一時的な測定ファイル
- IDE固有設定
- AWS認証情報、秘密鍵、PAT

```bash
git status --short
git diff --check
git diff
cd app && ./mvnw clean test
```

### Local Completion Criteria

次をすべて満たしてからEC2作業へ進む。

- Java 21でクリーンビルドと全テストが成功する。
- 実行可能JARからアプリケーションを起動できる。
- `/actuator/health`と`/load`が仕様どおり応答する。
- `jcmd Compiler.codecache`の負荷前後値を取得できる。
- clone後に必要なMaven WrapperとソースがGit管理対象になっている。
- `target/`や認証情報がGit管理対象に含まれていない。

## Troubleshooting

| Symptom | Check | Action |
| --- | --- | --- |
| `UnsupportedClassVersionError` | `java -version`と`javac -version` | 両方をJava 21へ切り替える |
| `Permission denied: ./mvnw` | `ls -l mvnw` | `chmod +x mvnw`後、実行属性をGitへ記録する |
| 8080番が使用中 | `ss -lntp | grep 8080` | 既存プロセスを止めるか一時的にポートを変更する |
| Actuatorが404 | `pom.xml`と起動ログ | Actuator依存関係とendpoint設定を確認する |
| `jcmd`に接続できない | 実行ユーザーとPID | アプリと同じLinuxユーザーで実行する |
| Maven依存取得失敗 | DNS、HTTPS、proxy | Maven Centralへの接続設定を確認する |

## Related Documents

- [API](../api/index.md)
- [Load API Implementation Guide](../api/load-api-implementation.md)
- [Local Performance Test Plan](../performance/test-plan.md)
- [Performance Test Result](../performance/test-result.md)
- [Code Cache Overflow Test](../performance/code-cache-overflow-test.md)
- [Deployment Operation](deployment.md)
- [Troubleshooting](troubleshooting.md)
