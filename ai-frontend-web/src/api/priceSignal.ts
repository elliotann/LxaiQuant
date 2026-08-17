import request, { del } from "./base";

/**
 * 分页查询技术信号
 */
export interface TechnicalSignalQueryParams {
  symbol?: string;
  indicator?: string;
  technicalDirection?: string;
  startTime?: string;
  endTime?: string;
  pageNum?: number;
  pageSize?: number;
}

/**
 * 技术信号数据
 */
export interface TechnicalSignal {
  id?: number;
  symbol?: string;
  indicator?: string;
  technicalDirection?: string;
  signalStrength?: number;
  confidence?: number;
  entryType?: string;
  limitPrice?: number;
  klineTime?: string;
  createTime?: string;
  [key: string]: any;
}

/**
 * 分页响应
 */
export interface PageResponse<T> {
  success: boolean;
  data: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
  message?: string;
}

/**
 * 历史信号生成请求参数
 */
export interface GenerateHistorySignalsRequest {
  symbol: string;
  interval: string;
  strategyType: string;
  startTime: number;
  robotId?: string;
}

/**
 * 历史信号生成响应
 */
export interface GenerateHistorySignalsResponse {
  success: boolean;
  message: string;
  signalCount?: number;
  symbol?: string;
  strategyType?: string;
}

/**
 * 分页查询技术信号
 */
export function getTechnicalSignals(
  params: TechnicalSignalQueryParams,
): Promise<PageResponse<TechnicalSignal>> {
  return request.get<PageResponse<TechnicalSignal>>("/price-signal/list", {
    params,
  });
}

/**
 * 生成历史信号
 */
export function generateHistorySignals(
  data: GenerateHistorySignalsRequest,
): Promise<GenerateHistorySignalsResponse> {
  return request.post<GenerateHistorySignalsResponse>(
    "/price-signal/generate-history",
    data,
  );
}

/**
 * 清除信号响应
 */
export interface ClearSignalsResponse {
  success: boolean;
  message: string;
  deletedCount?: number;
  indicator?: string;
}

/**
 * 根据指标类型清除所有技术信号
 */
export function clearSignalsByIndicator(
  indicator: string,
): Promise<ClearSignalsResponse> {
  return del<ClearSignalsResponse>("/price-signal/clear-by-indicator", {
    params: { indicator },
  });
}

/**
 * 根据机器人ID清除所有技术信号
 */
export function clearSignalsByRobot(
  robot: string,
): Promise<ClearSignalsResponse> {
  return del<ClearSignalsResponse>("/price-signal/clear-by-indicator", {
    params: { indicator: robot }, // 参数名仍为 indicator，但值为机器人ID
  });
}

export type SignalServiceParamType = "number" | "text" | "boolean" | "select";

export interface SignalServiceParamOption {
  label: string;
  value: string | number | boolean;
}

export interface SignalServiceParamDefinition {
  key: string;
  label: string;
  type: SignalServiceParamType;
  defaultValue: string | number | boolean;
  min?: number;
  max?: number;
  step?: number;
  options?: SignalServiceParamOption[];
  group?: string;
  description?: string;
}

export interface SignalServiceDefinition {
  key: string;
  label: string;
  parameters: SignalServiceParamDefinition[];
}

export interface SignalServiceConfig {
  id?: number;
  name: string;
  serviceKey: string;
  enabled: boolean;
  params: Record<string, any>;
  weightRules?: WeightRuleConfig;
  updatedAt?: string;
  updatedAtTs?: number;
}

export interface RuleCondition {
  indicator: string;
  params?: Record<string, string>;
  operator: string;
  value: string;
  direction?: string;
}

export interface WeightRule {
  name: string;
  type: "SCORING" | "VETO";
  score?: number;
  vetoWeight?: number;
  conditions: RuleCondition[];
  conditionOperator: "AND" | "OR";
  enabled?: boolean;
  order?: number;
}

export interface WeightScoringConfig {
  vetoContributeScore?: boolean;
  mappingMode?: string;
  linearSlope?: number;
  linearMinWeight?: number;
  linearMaxWeight?: number;
}

export interface WeightRuleConfig {
  enabled: boolean;
  rules: WeightRule[];
  scoringConfig?: WeightScoringConfig;
}

export interface SignalServiceResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
}

export interface TestCandlestick {
  id: number;
  open: number;
  high: number;
  low: number;
  close: number;
}

export interface ConditionTrace {
  indicator: string;
  operator: string;
  direction: string;
  expectedValue: string;
  actualValue: string | number | null;
  valueType?: string;
  matched: boolean;
}

export interface RuleEvaluationTrace {
  ruleName: string;
  ruleType: string;
  matched: boolean;
  contribution: number;
  reason: string;
  conditionOperator: string;
  conditionResults: ConditionTrace[];
}

export interface RuleEvaluationResult {
  vetoed: boolean;
  finalWeight: number;
  totalScore: number;
  reason: string;
  traces: RuleEvaluationTrace[];
  indicatorSnapshot: Record<string, any>;
}

export interface IndicatorParamDef {
  key: string;
  label: string;
  type: string;
  defaultValue?: any;
  min?: number;
  max?: number;
  step?: number;
  options?: string[];
}

export interface IndicatorValueRange {
  min?: number;
  max?: number;
}

export interface IndicatorMetadata {
  id: string;
  name: string;
  category: string;
  description?: string;
  valueType: string;
  valueRange?: IndicatorValueRange;
  operators: string[];
  params?: IndicatorParamDef[];
  enumValues?: string[];
}

export interface WeightRuleVersion {
  id: number;
  configId: number;
  version: number;
  configJson: string;
  status: string;
  remark: string;
  createdBy: string;
  createTime: string;
}

export interface RuleEngineTestRequest {
  direction: string;
  symbol?: string;
  currentPrice?: number;
  marketTrend?: string;
  kLines?: TestCandlestick[];
  weightRules: WeightRuleConfig;
  context?: Record<string, any>;
}

export function getSignalServiceDefinitions(): Promise<
  SignalServiceResponse<SignalServiceDefinition[]>
> {
  return request.get<SignalServiceResponse<SignalServiceDefinition[]>>(
    "/signal-service/definitions",
  );
}

export function getSignalServiceConfigs(): Promise<
  SignalServiceResponse<SignalServiceConfig[]>
> {
  return request.get<SignalServiceResponse<SignalServiceConfig[]>>(
    "/signal-service/configs",
  );
}

export function createSignalServiceConfig(
  data: SignalServiceConfig,
): Promise<SignalServiceResponse<SignalServiceConfig>> {
  return request.post<SignalServiceResponse<SignalServiceConfig>>(
    "/signal-service/configs",
    data,
  );
}

export function updateSignalServiceConfig(
  id: number,
  data: SignalServiceConfig,
): Promise<SignalServiceResponse<SignalServiceConfig>> {
  return request.put<SignalServiceResponse<SignalServiceConfig>>(
    `/signal-service/configs/${id}`,
    data,
  );
}

export function deleteSignalServiceConfig(
  id: number,
): Promise<SignalServiceResponse<boolean>> {
  return del<SignalServiceResponse<boolean>>(`/signal-service/configs/${id}`);
}

export function testRuleEngine(
  data: RuleEngineTestRequest,
): Promise<SignalServiceResponse<RuleEvaluationResult>> {
  return request.post<SignalServiceResponse<RuleEvaluationResult>>(
    "/rule-engine/test",
    data,
  );
}

export function getIndicatorMetadata(): Promise<
  SignalServiceResponse<IndicatorMetadata[]>
> {
  return request.get<SignalServiceResponse<IndicatorMetadata[]>>(
    "/rule-engine/indicators",
  );
}

export function getWeightRuleVersions(
  configId: number,
): Promise<SignalServiceResponse<WeightRuleVersion[]>> {
  return request.get<SignalServiceResponse<WeightRuleVersion[]>>(
    `/rule-engine/versions/${configId}`,
  );
}

export function restoreWeightRuleVersion(
  configId: number,
  version: number,
): Promise<SignalServiceResponse<WeightRuleConfig>> {
  return request.post<SignalServiceResponse<WeightRuleConfig>>(
    `/rule-engine/versions/${configId}/restore/${version}`,
  );
}

export function updateWeightRules(
  id: number,
  data: WeightRuleConfig,
): Promise<SignalServiceResponse<boolean>> {
  return request.put<SignalServiceResponse<boolean>>(
    `/signal-service/configs/${id}/weight-rules`,
    data,
  );
}

export interface CreateTechnicalSignalRequest {
  symbol: string;
  timeframe: string;
  klineTime: string;
  klineTimestamp: number;
  indicator: string;
  strategyName: string;
  technicalDirection:
    | "STRONG_BULLISH"
    | "BULLISH"
    | "NEUTRAL"
    | "BEARISH"
    | "STRONG_BEARISH";
  signalStrength: number;
  currentPrice: number;
  confidence?: number;
  indicatorValues?: Record<string, any>;
  signalHash: string;
  extraParams?: string;
}

export interface CreateTechnicalSignalResponse {
  success: boolean;
  data?: number;
  message?: string;
}

export function createTechnicalSignal(
  data: CreateTechnicalSignalRequest,
): Promise<CreateTechnicalSignalResponse> {
  return request.post<CreateTechnicalSignalResponse>("/signal/technical", data);
}
