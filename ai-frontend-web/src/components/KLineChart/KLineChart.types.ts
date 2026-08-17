export interface KLineData {
  time: string | number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

export interface ChartConfig {
  width?: number;
  height?: number;
  layout?: {
    background?: string;
    textColor?: string;
  };
  grid?: {
    vertLines?: { color: string };
    horzLines?: { color: string };
  };
  crosshair?: {
    mode: number;
  };
  priceScale?: {
    borderColor?: string;
  };
  timeScale?: {
    borderColor?: string;
    timeVisible?: boolean;
    secondsVisible?: boolean;
  };
  watermark?: {
    visible?: boolean;
    fontSize?: number;
    horzAlign?: string;
    vertAlign?: string;
  };
}

export interface IndicatorConfig {
  type: "sma" | "ema" | "rsi" | "macd" | "volume";
  period?: number;
  color?: string;
  lineWidth?: number;
}

export interface ChartTools {
  chart: any;
  candlestickSeries: any;
  volumeSeries: any;
  indicators: Map<string, any>;
  resize: () => void;
  update: (data: KLineData[]) => void;
  addIndicator: (config: IndicatorConfig) => string;
  removeIndicator: (id: string) => void;
}
