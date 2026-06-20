# Code Cache Test API

## Status

Active (Local Test Only)

## Activation

検証専用APIは通常profileでは登録されず、すべてHTTP 404になる。
ローカル検証時だけ次のprofileを有効にする。

```bash
--spring.profiles.active=code-cache-test
```

EC2や外部公開環境で不用意に有効化しない。

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/code-cache/generator/generate` | 異なるclass/methodを生成し、指定回数呼び出す |
| POST | `/code-cache/generator/warm` | 生成済みmethodを再度呼び、hotな状態を維持する |
| GET | `/code-cache/generator/status` | 生成class数と計算sinkを確認する |
| GET | `/code-cache/probe/hot` | 満杯前にwarm-upする計算methodの時間を測る |
| GET | `/code-cache/probe/cold` | 満杯後に初めて呼ぶ同等計算methodの時間を測る |

## Generate

```http
POST /code-cache/generator/generate?classes=500&warmupIterations=10000
```

| Parameter | Default | Range | Meaning |
| --- | ---: | ---: | --- |
| `classes` | 250 | 1～1,000/request | 生成するclass数。1 classにつき1 method |
| `warmupIterations` | 10,000 | 1～20,000 | 各生成methodを呼ぶ回数 |

制約:

- 1 requestの総呼び出し数は2,000万以下。
- 1 processで生成できるclassは累計2万以下。
- 生成classはapplication class loaderへinjectし、process終了までunloadしない。
- 状態を初期化する場合はapplicationを再起動する。

レスポンス例:

```json
{
  "operation": "generate",
  "generated": 500,
  "totalGenerated": 500,
  "iterations": 10000,
  "invocations": 5000000,
  "elapsedMillis": 614,
  "sink": 4064
}
```

`sink`は呼び出し結果が不要処理として除去されにくくするための値であり、業務的意味はない。
満杯になるclass数はJIT compilationとSweeperのtimingで変わる。`full_count=0`の場合は、
上限内で新しいclassを追加生成する。同じ生成済みmethodの呼び出し回数だけを増やしても、
compile完了後のCode Cache使用量は大きく増えない。

## Warm Existing Methods

```http
POST /code-cache/generator/warm?iterations=100
```

生成済み全methodを再実行する。生成前はHTTP 400になる。
総呼び出し数の上限はGenerateと同じ2,000万である。

## Status

```http
GET /code-cache/generator/status
```

```json
{
  "totalGenerated": 500,
  "sink": 4064
}
```

## Hot and Cold Probes

```http
GET /code-cache/probe/hot?count=100000
GET /code-cache/probe/cold?count=100000
```

| Parameter | Default | Range |
| --- | ---: | ---: |
| `count` | 100,000 | 1～10,000,000 |

両probeは同じ初期値・計算式・反復回数を使い、異なるJava methodとして実装する。

- Hot: Generator実行前に繰り返してLevel 4へ上げる。
- Cold: Code Cache満杯後まで呼ばない。

```json
{
  "probe": "hot",
  "count": 100000,
  "result": -2002236132324804515,
  "elapsedNanos": 92234
}
```

`result`はHot/Coldで一致する。比較対象は主に`elapsedNanos`である。

## Implementation

- Byte Buddy 1.18.2で一意なclassをruntime生成する。
- 各classは`GeneratedWorkUnit.execute(int)`を実装する。
- methodごとに異なる定数を返し、呼び出し結果を`volatile` sinkへ集約する。
- interface call siteを多数の実装でmegamorphicにし、methodごとのJIT compilationを促す。
- classへの参照を保持し、検証中のclass unloadingを防ぐ。

GeneratorはMetaspaceも増加させる。Code Cacheと区別して観測すること。

## Errors

| Status | Condition |
| --- | --- |
| 400 | parameterが範囲外 |
| 400 | 1 requestの呼び出し数が2,000万超過 |
| 400 | 累計生成classが2万超過 |
| 400 | class未生成でwarmを実行 |
| 404 | `code-cache-test` profileが無効 |

## Related Documents

- [Code Cache Overflow Test](../performance/code-cache-overflow-test.md)
- [Current State](../architecture/current-state.md)
- [Troubleshooting](../operations/troubleshooting.md)
