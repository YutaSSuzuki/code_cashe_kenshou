# ADR-0001 Runtime Code Generation for Code Cache Pressure

## Status

Accepted

## Context

同じ`LoadService.calculate`を繰り返しても、Level 4到達後は同じcompiled codeが再利用され、
Code Cache使用量が増え続けなかった。通常のAPIを追加するだけではmethod数が少なく、満杯を
再現しにくい。`-Xcomp`は満杯機構の確認には使えるが、通常Tiered Compilationの遅延比較を歪める。

## Decision

Byte Buddy 1.18.2でruntimeに多数の異なるclass/methodを生成する。

- `code-cache-test` profileでのみ有効にする。
- 1 classにつき1つの`GeneratedWorkUnit.execute`を生成する。
- methodを指定回数呼び、結果をvolatile sinkへ集約する。
- 生成数と呼び出し数に上限を設ける。
- Generatorと遅延測定用Hot/Cold probeを分離する。
- 通常Tiered Compilationを本試験条件とし、`-Xcomp`は補助試験だけに使う。

## Consequences

- Code Cacheを段階的かつ再現可能に圧迫できる。
- 満杯前にcompile済みのHot methodと、満杯後のCold methodを比較できる。
- Byte Buddyがruntime依存関係に追加される。
- 生成classを保持するためMetaspaceも増える。
- 同一process内で完全resetできないため、再試験時はapplicationを再起動する。
- 検証profileを外部公開環境で有効にしない運用が必要になる。

## Related Documents

- [Code Cache Test API](../api/code-cache-test-api.md)
- [Code Cache Overflow Test](../performance/code-cache-overflow-test.md)
- [Performance Test Result](../performance/test-result.md)
- [Verification Environment Proposal](../proposals/code-cache-verification-environment.md)
