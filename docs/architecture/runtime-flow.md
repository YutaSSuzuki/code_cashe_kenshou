# Runtime Flow

## Status

Active (Local), Draft (CloudWatch)

## Overview

Generatorで多数の異なるmethodをJITコンパイルし、Code Cache満杯前後のHot/Cold probeを比較する。

## Main Flow

```mermaid
sequenceDiagram
    actor Engineer as 検証実施者
    participant App
    participant JVM
    participant Agent as CloudWatch Agent
    participant CW as CloudWatch

    Engineer->>App: Hot probeをwarm-up
    App->>JVM: Hot methodをLevel 4へcompile
    Engineer->>App: Generatorで異なるclass/methodを生成
    App->>JVM: 各methodを繰り返し実行
    JVM->>JVM: Code Cacheを消費、満杯でcompiler停止
    Engineer->>App: Hot probeとCold probeを実行
    App-->>Engineer: elapsedNanosを含むHTTP 200
    Engineer->>JVM: jcmdでfull/stop/restartを確認
    Agent->>JVM: localhost JMXでメモリプール取得
    Agent->>CW: CodeHeapメトリクスとログを送信
    Engineer->>CW: 時系列グラフとログを確認
```

## Related Documents

- [System Context](system-context.md)
- [API](../api/index.md)
- [Code Cache Test API](../api/code-cache-test-api.md)
