## 0.1.0 (2026-02-15)

### 新增
- **Bill Williams indicator suite**: Added `FractalHighIndicator`, `FractalLowIndicator`, `AlligatorIndicator` (jaw/teeth/lips defaults), `GatorOscillatorIndicator` (upper/lower histogram branches), and `MarketFacilitationIndexIndicator` with comprehensive regression coverage for confirmation delays, overlapping windows, flat-price/zero-volume edge cases, constructor validation, unstable-bar boundaries, and lower-histogram signed-zero handling.

### 修改
- **Risk/stop-rule refinements**: Tightened volatility stop-gain coefficient validation, removed redundant risk recomputation in `RMultipleCriterion`, added the missing `AverageTrueRangeTrailingStopLossRule` lookback overload, and expanded shared stop-rule fixtures/tests and Javadocs.

### 修复bug
- **Stop-rule behavior and efficiency**: Trailing stop-gain variants now arm only after the configured favorable move, volatility stop-loss variants trigger on exact threshold touches, gain-side helpers are used consistently, and trailing volatility/fixed-amount rules now reuse stabilized max-lookback indicators to reduce hot-path allocations.