# Performance: Java Code Cache

## Status

Active

## Purpose

直近の検証済み性能状態を示す。

## Conditions

- Local WSL2 Ubuntu 22.04 LTS
- OpenJDK 21.0.11 HotSpot
- Spring Boot 4.1.0
- `-XX:ReservedCodeCacheSize=10m`
- 通常Tiered Compilation、Code Cache flushing有効

## Test Data

Byte Buddy Generatorで500 class、追加250 classを生成し、各methodを1万回呼び出した。

## Scenario

[Performance Test Plan](test-plan.md)に定義する。

## Metrics

- `Compiler.codecache`
- Generator実行時間
- Hot/Cold probeの`elapsedNanos`

## Result

- 500 class生成後: used 9,707KB、free 532KB、full_count 0
- 追加250 class後: full_count 1、Compilation disabled
- Hot probe: 92,234ns
- Cold probe: 1,265,000ns（Hot比約13.7倍）

## Bottleneck Analysis

[Bottleneck Analysis](bottleneck-analysis.md)に記載する。

## Improvements

Code Cache上限を通常運用値から不用意に縮小しない。満杯時は未compile pathの遅延を監視し、
原因method数、動的class生成、JVM optionを調査する。

## Related Documents

- [Performance Test Plan](test-plan.md)
- [Performance Test Result](test-result.md)
