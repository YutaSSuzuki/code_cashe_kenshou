# API: Application Endpoints

## Status

Active (Local)

JITコンパイルを促す計算処理を繰り返し実行する、検証専用HTTP API。
一般公開や業務利用は想定しない。

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/load?count={iterations}` | 指定回数の計算処理を実行して負荷を生成する |
| GET | `/actuator/health` | アプリケーションの生存確認を行う |

Code Cache満杯試験で使用する検証専用APIは`code-cache-test` profileでのみ有効になる。
仕様は[Code Cache Test API](code-cache-test-api.md)を参照する。

## Request

- `count`: 計算回数。省略時は`100000`を使用する。
- 許可範囲は1以上`10000000`以下とする。

## Response

- `/load`: `count`、計算結果、処理時間をJSONで返す。
- `/actuator/health`: Spring Boot Actuatorのヘルス情報を返す。

`/load`のレスポンス例:

```json
{
  "count": 1000,
  "result": 123456789,
  "elapsedNanos": 12345
}
```

`result`と`elapsedNanos`の値は例であり、固定値ではない。

## Errors

| Status | Condition |
| --- | --- |
| 400 | `count`が数値でない、1未満、または上限超過 |
| 500 | 負荷処理中に予期しないエラーが発生 |

## Authorization

アプリケーション認証は実装しない。Security Groupで接続元IPを制限する。

## Related Documents

- [Runtime Flow](../architecture/runtime-flow.md)
- [Load API Implementation Guide](load-api-implementation.md)
- [Code Cache Test API](code-cache-test-api.md)
- [Performance Test Plan](../performance/test-plan.md)
