# Bottleneck Analysis

## Status

Active

## Observed Problem

10MB Code Cache満杯後、JIT compilerが停止し、初回実行のCold probeがHot probeより遅延した。

## Evidence

- `full_count=1`
- `Compilation: disabled (not enough contiguous free space left)`
- `stopped_count=1`, `restarted_count=0`
- Hot probe 92,234ns
- Cold probe 1,265,000ns

## Root Cause

多数の異なるgenerated methodがCode Cacheへ格納され、連続した空き領域を確保できなくなった。
満杯前にcompile済みのHot methodは実行可能だが、満杯後に初めて呼ばれたCold methodは
JIT compilationできず、interpreter実行になった。

## Improvement Options

| Option | Effect | Cost | Risk |
| --- | --- | --- | --- |
| Code Cache上限を適正化 | compiler停止を防ぐ | JVM再起動 | 過大予約によるnative memory増加 |
| 動的class/method数を抑制 | Code CacheとMetaspace増加を抑える | application変更 | 必要機能を制限する可能性 |
| 使用量とcompiler停止を監視 | 早期検知 | 監視cost | JMX標準値だけでは停止counterを直接取得できない |

## Decision

今回の10MBは障害再現用であり、通常設定の推奨値とはしない。EC2では通常上限から開始し、
CloudWatchのCodeHeap使用量、JVM warning、応答時間を合わせて監視する。

重要な設計判断になる場合はADRへ記録する。

## Related Documents

- [Test Result](test-result.md)
- [ADR Template](../adr/template.md)
