# Load API Implementation Guide

## Status

Active

## Goal

Spring MVCの基本構造を学びながら`GET /load`を実装し、HTTPリクエストによって
HotSpot JVMのJITコンパイル対象となるCPU計算を繰り返し実行できるようにする。

完成コードはこの文書に置かない。各節の責務と完了条件に従って、自分でJavaコードを書く。

## Current Starting Point

Spring Initializrで生成した未変更アプリケーションは起動済みであり、次を確認できている。

- `GET /actuator/health`: HTTP 200
- `GET /load?count=1000`: HTTP 404

`/load`の404は未実装時点の期待結果である。

## API Contract

| Item | Value |
| --- | --- |
| Method | GET |
| Path | `/load` |
| Query parameter | `count` |
| Default | `100000` |
| Minimum | `1` |
| Maximum | `10000000` |
| Success | HTTP 200、JSON |
| Invalid input | HTTP 400 |

成功レスポンスは次の3項目を持つ。

| Field | Java type | Meaning |
| --- | --- | --- |
| `count` | `int` | 実際に実行した反復回数 |
| `result` | `long` | 計算結果。計算が除去されないようレスポンスまで利用する |
| `elapsedNanos` | `long` | `System.nanoTime()`で測った計算処理時間 |

## Intended Package Structure

```text
app/src/main/java/com/example/codecache/
├── CodeCacheDemoApplication.java
├── api/
│   ├── LoadController.java
│   └── LoadResponse.java
└── service/
    └── LoadService.java
```

役割を次のように分ける。

| Class | Responsibility | Must Not Do |
| --- | --- | --- |
| `LoadController` | HTTP mapping、パラメーター受付、範囲検証、時間計測、レスポンス生成 | 計算ループを直接持たない |
| `LoadService` | `count`回のCPU計算を実行して`long`を返す | HTTPやSpring MVCの型へ依存しない |
| `LoadResponse` | 成功レスポンスの3項目を表現する | 計算や入力検証を行わない |

`CodeCacheDemoApplication`の配下パッケージに置くことで、Spring Bootのcomponent scan対象になる。

## Step 1: Create the Response Type

`api/LoadResponse.java`を作成する。

学習ポイント:

- 変更しないレスポンスデータにはJavaの`record`を利用できる。
- record component名がJSONのfield名になる。
- 型はAPI契約の`int`, `long`, `long`に合わせる。

作成する構造を擬似コードで示す。

```text
public record LoadResponse(
    count,
    result,
    elapsedNanos
) {}
```

擬似コードにJavaの型とpackage宣言を補い、コンパイル可能なクラスにする。

### Completion Check

```bash
cd app
./mvnw test
```

## Step 2: Implement the Calculation Service

`service/LoadService.java`を作成し、Spring管理対象のserviceにする。

必要な構造:

```text
class LoadService
  method calculate(count: int): long
    accumulatorを非ゼロで初期化
    iを使ってcount回ループ
      accumulatorを前回値とiから更新
    accumulatorを返す
```

計算式は自分で決める。次を満たすこと。

- 各反復が前回の`accumulator`に依存する。
- ループ変数`i`も計算に使う。
- 最終値をメソッドの戻り値にする。
- ファイル、ネットワーク、sleepを使用しない。
- リクエストごとの可変な`count`を使い、結果を事前計算できる定数処理にしない。

例えば「乗算、加算、ビット演算を組み合わせてaccumulatorを更新する」という方針が使える。
`long`のオーバーフローは本検証では許容する。Javaの整数演算として決定的にwraparoundし、
Code Cache検証では数値の業務的な正しさを目的にしないためである。

### Why a Separate Service

JITで観測したい計算処理をHTTP処理から分けると、次が明確になる。

- 単体テストで計算だけを繰り返せる。
- `LoadService.calculate`という観測対象メソッドを特定できる。
- Controllerの責務がHTTP変換に限定される。

### Completion Check

`LoadService`の単体テストを作り、次を確認する。

- 同じ`count`から毎回同じ結果を返す。
- 異なる`count`で計算が実際に進む。
- `calculate(1)`など小さい入力で即座に完了する。

## Step 3: Implement the Controller

`api/LoadController.java`を作成する。

使用するSpring MVC要素:

| Element | Role |
| --- | --- |
| `@RestController` | 戻り値をJSONレスポンスとして扱う |
| constructor injection | `LoadService`をControllerへ渡す |
| `@GetMapping("/load")` | GET `/load`へmethodを対応付ける |
| `@RequestParam` | query parameter `count`を`int`として受け取る |
| `ResponseStatusException` | 範囲外をHTTP 400として返す |

Controller処理順:

1. `count`を受け取る。省略時は文字列`"100000"`を既定値として指定する。
2. `count < 1`または`count > 10000000`ならHTTP 400にする。
3. 計算直前の`System.nanoTime()`を取得する。
4. `LoadService.calculate(count)`を1回呼ぶ。
5. 計算直後の`System.nanoTime()`を取得して差を求める。
6. `LoadResponse`を生成して返す。

`System.currentTimeMillis()`は壁時計であり短い処理時間の測定には粗いため、経過時間には
`System.nanoTime()`を使う。ただしこの値は簡易観測用であり、厳密なJavaベンチマークではない。

### Input Behavior

| Request | Expected |
| --- | --- |
| `/load` | 既定値100000でHTTP 200 |
| `/load?count=1` | HTTP 200 |
| `/load?count=1000` | HTTP 200 |
| `/load?count=10000000` | HTTP 200 |
| `/load?count=0` | HTTP 400 |
| `/load?count=-1` | HTTP 400 |
| `/load?count=10000001` | HTTP 400 |
| `/load?count=abc` | Spring MVCの型変換エラーによりHTTP 400 |

## Step 4: Verify Manually

アプリケーションを再起動する。実行中のプロセスにJavaソース変更は自動反映されない。

```bash
cd app
./mvnw spring-boot:run
```

別ターミナルで正常系を確認する。

```bash
curl -i http://localhost:8080/actuator/health
curl -i "http://localhost:8080/load?count=1000"
curl -i "http://localhost:8080/load"
```

異常系を確認する。

```bash
curl -i "http://localhost:8080/load?count=0"
curl -i "http://localhost:8080/load?count=-1"
curl -i "http://localhost:8080/load?count=10000001"
curl -i "http://localhost:8080/load?count=abc"
```

### Completion Check

- `/load?count=1000`が404ではなくHTTP 200になる。
- `Content-Type`がJSONである。
- JSONに`count`, `result`, `elapsedNanos`がある。
- 無効値はHTTP 400になる。
- アプリケーションログに予期しないstack traceがない。

## Step 5: Add HTTP Tests

Spring Bootのtest依存関係は生成済み`pom.xml`の次のstarterを使用する。

- `spring-boot-starter-actuator-test`
- `spring-boot-starter-webmvc-test`

HTTPテストではSpring contextとMockMvcを使用し、少なくとも次を自動化する。

| Test Case | Assertion |
| --- | --- |
| health | status 200、JSONの`status`が`UP` |
| explicit count | status 200、JSONの`count`が指定値 |
| omitted count | status 200、JSONの`count`が100000 |
| zero | status 400 |
| negative | status 400 |
| over maximum | status 400 |
| non-numeric | status 400 |

`result`の値そのものは`LoadService`の単体テストで確認し、Controllerテストではレスポンスに
数値として存在することを確認する。`elapsedNanos`の具体値や上限をテストすると、マシン性能に
依存して不安定になるため、数値として存在することだけを確認する。

### Completion Check

```bash
cd app
./mvnw clean test
```

## Step 6: Prepare for Code Cache Observation

全テスト成功後にJARを作成して検証用JVMオプションで起動する。

```bash
cd app
./mvnw clean package

java \
  -XX:ReservedCodeCacheSize=64m \
  -XX:+PrintCodeCache \
  -jar target/code-cache-demo-*.jar
```

別ターミナルでPIDと負荷前の状態を確認する。

```bash
jcmd -l
jcmd <PID> Compiler.codecache
```

負荷を生成する。

```bash
for i in $(seq 1 100); do
  curl -fsS "http://localhost:8080/load?count=100000" >/dev/null || break
done

jcmd <PID> Compiler.codecache
```

Code Cacheの結果解釈と記録方法は[Local Development](../operations/local-development.md)および
[Performance Test Plan](../performance/test-plan.md)に従う。

## Review Checklist

- Controller、Service、Responseの責務が分かれている。
- フィールドインジェクションではなくconstructor injectionを使用している。
- `count`の既定値と境界値がAPI仕様と一致している。
- 計算結果がHTTPレスポンスまで利用されている。
- `Thread.sleep`や外部I/Oを負荷として使用していない。
- 正常系と異常系の自動テストがある。
- `./mvnw clean test`が成功する。
- `/load`を繰り返してもアプリケーションが停止しない。

## Related Documents

- [API Specification](index.md)
- [Local Development](../operations/local-development.md)
- [Runtime Flow](../architecture/runtime-flow.md)
- [Performance Test Plan](../performance/test-plan.md)
