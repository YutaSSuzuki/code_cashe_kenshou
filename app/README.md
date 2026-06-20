# Code Cache Demo Application

Java HotSpot JVMのCode Cacheを学習・観測するためのSpring Bootサンプルを配置する。
負荷APIと自動テストは実装済みであり、現在はローカルCode Cache確認の段階である。

## Technology Stack

- Java 21 / Ubuntu OpenJDK HotSpot
- Spring Boot 4.1.0
- Spring Web
- Spring Boot Actuator
- Maven Wrapper
- Byte Buddy 1.18.2（検証profileのruntime class生成）

## Planned Source Layout

- `src/main/java/com/example/codecache/`: エントリーポイント、API、計算処理
- `src/main/java/com/example/codecache/codecache/`: Generator、Hot/Cold probe
- `src/main/resources/`: Spring Boot設定
- `src/test/java/com/example/codecache/`: 自動テスト
- `pom.xml`: Java、Spring Boot、依存ライブラリの定義

## Build and Run

雛形生成後に次のコマンドを使用する。

```bash
./mvnw clean test
./mvnw clean package
java -jar target/code-cache-demo-*.jar
```

## Environment Variables

ローカル実装段階では必須環境変数を設けない。AWS認証情報をアプリケーションへ渡さない。

## Current Verification

- `./mvnw clean test`: 14 tests、failures 0、errors 0
- `GET /actuator/health`: HTTP 200
- `GET /load?count=1000`: HTTP 200
- `GET /load?count=0`: HTTP 400
- Generatorによる10MB Code Cache満杯とJIT停止: 確認済み
- 満杯時のHot/Cold probe遅延差: 確認済み

## Documentation

- [Local Development](../docs/operations/local-development.md)
- [API Specification](../docs/api/index.md)
- [Load API Implementation Guide](../docs/api/load-api-implementation.md)
- [Code Cache Test API](../docs/api/code-cache-test-api.md)
- [Performance Test Plan](../docs/performance/test-plan.md)
