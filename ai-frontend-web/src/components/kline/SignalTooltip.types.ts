export type SignalTooltipRow =
  | { key: string; kind: "section"; label: string }
  | { key: string; kind: "item"; label: string; value: string };
