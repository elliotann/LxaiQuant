import { get, post, del } from "./base";

export const trainModel = async (symbol: string, modelType?: string) => {
  return await post(`/ml/train/${symbol}`, null, { params: { modelType: modelType || "DIRECTION" } });
};

export const trainModelAsync = async (symbol: string) => {
  return await post(`/ml/train/${symbol}/async`);
};

export const getTrainingProgress = async (jobId: string) => {
  return await get(`/ml/training/progress/${jobId}`);
};

export const predictDirection = async (symbol: string) => {
  return await get(`/ml/predict/${symbol}`);
};

export const predictVolatility = async (symbol: string) => {
  return await post(`/ml/predict/volatility`, null, { params: { symbol } });
};

export const getMarketState = async (symbol: string) => {
  return await get(`/ml/market/state`, { params: { symbol } });
};

export const listModels = async (symbol: string, modelType?: string) => {
  return await get(`/ml/models/${symbol}`, { params: { modelType: modelType || "DIRECTION" } });
};

export const getActiveModel = async (symbol: string, modelType?: string) => {
  return await get(`/ml/models/active/${symbol}`, { params: { modelType: modelType || "DIRECTION" } });
};

export const activateModel = async (modelId: string) => {
  return await post(`/ml/models/${modelId}/activate`);
};

export const deleteModel = async (modelId: string) => {
  return await del(`/ml/models/${modelId}`);
};

export const getCurrentFeatures = async (symbol: string) => {
  return await get(`/ml/features/${symbol}`);
};

export const getFeatureTimeSeries = async (symbol: string, limit?: number) => {
  return await get(`/ml/features/timeseries`, { params: { symbol, limit: limit || 100 } });
};

export const getFeatureImportance = async (modelId: string) => {
  return await get(`/ml/models/${modelId}/feature-importance`);
};

export const getConfusionMatrix = async (modelId: string) => {
  return await get(`/ml/models/${modelId}/confusion-matrix`);
};

export const getAccuracyTrend = async (modelId: string) => {
  return await get(`/ml/models/${modelId}/accuracy-trend`);
};

// ---- 特征自动搜索 ----

export interface AutoSearchRequest {
  symbol: string;
  maxCombinations?: number;
  minFeatures?: number;
  maxFeatures?: number;
  featurePool: Record<string, any[]>;
  modelParams?: {
    numTrees: number;
    maxDepth: number;
    minSamples: number;
  };
  weights?: {
    f1: number;
    precision: number;
    signalCount: number;
  };
  dataSplit?: {
    totalBars: number;
    testRatio: number;
  };
  thresholdScan?: {
    start: number;
    end: number;
    step: number;
  };
}

export const startAutoSearch = async (req: AutoSearchRequest) => {
  return await post(`/ml/auto-search`, req);
};

export const getAutoSearchProgress = async (searchId: string) => {
  return await get(`/ml/auto-search/${searchId}`);
};

export const stopAutoSearch = async (searchId: string) => {
  return await post(`/ml/auto-search/stop/${searchId}`);
};

export const applyFeatureCombination = async (req: { features: string[]; symbol?: string }) => {
  return await post(`/ml/auto-search/apply`, req);
};

export const getFeaturePoolDefs = async () => {
  return await get(`/ml/feature-pool`);
};
