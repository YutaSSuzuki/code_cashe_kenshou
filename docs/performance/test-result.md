# Performance Test Result

## Status

Local Complete / EC2 Not Started

## Test Plan

- [Performance Test Plan](test-plan.md)

## Execution Date

2026-06-20

## Environment

- Environment: Local WSL2 Ubuntu 22.04 LTS
- Application: Spring Boot 4.1.0
- JVM: OpenJDK 21.0.11 HotSpot
- JVM option: `-XX:ReservedCodeCacheSize=10m`、通常Tiered Compilation、flushing既定値
- Profile: `code-cache-test`
- Test command: `./mvnw clean test`（14 tests、failures 0、errors 0）

## Results

| Scenario | Result | Metrics | Judgment |
| --- | --- | --- | --- |
| Automated test | 14 tests、failures 0、errors 0 | Maven Surefire report | Pass |
| API success | `GET /load?count=1000` | HTTP 200、JSON応答 | Pass |
| API invalid input | `GET /load?count=0` | HTTP 400 | Pass |
| Generator baseline | used 6,827KB、max 8,175KB、free 3,412KB | full_count 0、enabled | Pass |
| Generate 500 | used 9,707KB、free 532KB、generated nmethods 500 | 500万invocations、614ms | Pass |
| Generate additional 250 | used 9,341KB、max 9,880KB、free 898KB | full_count 1、stopped 1、disabled | Full reproduced |
| Hot probe after full | 92,234ns | compiled before full | Control |
| Cold probe after full | 1,265,000ns | first call after compiler stop | 13.7x slower |
| Tiered compilation | `LoadService.calculate(I)J`がLevel 4 | `jcmd Compiler.codelist` | Pass |
| 10MB Code Cache | used 7,906KB、max 8,850KB、free 2,333KB、nmethods 1,976 | full_count 0、Compilation enabled | Not full |

## Findings

- 負荷APIの正常系と入力範囲外が設計どおり応答した。
- 負の`result`は`long`のwraparoundによるもので、本検証では許容する。
- `LoadService.calculate`はC2のLevel 4へ到達済み。追加requestがCode Cacheを同じ割合で増やすとは限らない。
- 10MB条件では現在使用率約77.2%、peak約86.4%だが満杯ではない。64MB条件より`nmethods`が少なく、回収・compile policyの影響が見られる。
- Generatorにより異なるmethodを増やすと、通常Tiered CompilationのままCode Cacheを満杯にできた。
- 満杯後もHot probeはcompiled codeを利用でき、Cold probeはinterpreter実行により約13.7倍遅かった。
- 満杯後の`used`がpeakより減っていても、連続領域不足によりcompilerはdisabledのままになり得る。

## Related Documents

- [Bottleneck Analysis](bottleneck-analysis.md)
- [Code Cache Test API](../api/code-cache-test-api.md)
- [Code Cache Overflow Test](code-cache-overflow-test.md)
- [EC2 and CloudWatch Verification](ec2-cloudwatch-verification.md)
