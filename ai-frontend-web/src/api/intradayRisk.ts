import api from "./base";

export interface IntradayRiskConfig {
  enabled: boolean;
  warningRatio: number;
  stopRatio: number;
  profitTargetRatio: number;
  defaultStopLossPercent: number;
  slippagePercent: number;
  riskPerExposure: number;
}

export const getIntradayRiskConfig = (memberId?: number) => {
  return api.get("/risk/intraday/config", { params: { memberId } });
};

export const saveIntradayRiskConfig = (config: IntradayRiskConfig, memberId?: number) => {
  return api.put("/risk/intraday/config", config, { params: { memberId } });
};

export default {
  getIntradayRiskConfig,
  saveIntradayRiskConfig,
};

