<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="signal-tooltip"
      :style="{
        left: left + 'px',
        top: top + 'px',
      }"
    >
      <div class="signal-tooltip-title">
        <span
          class="signal-tooltip-color"
          :style="{ backgroundColor: color }"
        ></span>
        <span class="signal-tooltip-title-text">{{ title }}</span>
        <span class="signal-tooltip-hint">Ctrl+C 复制</span>
      </div>
      <div class="signal-tooltip-rows">
        <template v-for="row in rows" :key="row.key">
          <div
            v-if="row.kind === 'section'"
            class="signal-tooltip-row signal-tooltip-row--section"
          >
            <span class="signal-tooltip-section-text">{{ row.label }}</span>
          </div>
          <div v-else class="signal-tooltip-cell">
            <span class="signal-tooltip-cell-label">{{ row.label }}</span>
            <span class="signal-tooltip-cell-value">{{ row.value }}</span>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type { SignalTooltipRow } from "./SignalTooltip.types";

defineProps<{
  visible: boolean;
  left: number;
  top: number;
  title: string;
  color: string;
  rows: SignalTooltipRow[];
}>();
</script>

<style scoped>
.signal-tooltip {
  position: fixed;
  z-index: 3000;
  min-width: 340px;
  max-width: 560px;
  padding: var(--mk-space-8) var(--mk-space-12);
  border: 1px solid var(--mk-border);
  border-radius: var(--mk-radius-lg);
  background: var(--mk-bg-modal);
  box-shadow: var(--mk-shadow-dropdown);
  backdrop-filter: blur(6px);
  pointer-events: none;
  color: var(--mk-text-primary);
}
.signal-tooltip-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: var(--mk-font-sm);
  margin-bottom: 6px;
}
.signal-tooltip-hint {
  margin-left: auto;
  font-weight: 400;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-sm);
  white-space: nowrap;
}
.signal-tooltip-color {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex: 0 0 auto;
}
.signal-tooltip-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.signal-tooltip-rows {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  column-gap: 8px;
  row-gap: 6px;
}
.signal-tooltip-row.signal-tooltip-row--section {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  padding-top: 2px;
  margin-top: 6px;
  font-size: var(--mk-font-sm);
  line-height: 18px;
}
.signal-tooltip-rows .signal-tooltip-row.signal-tooltip-row--section:first-child {
  margin-top: 0;
  padding-top: 0;
}
.signal-tooltip-section-text {
  font-weight: 600;
  color: var(--mk-text-tertiary);
  white-space: nowrap;
}
.signal-tooltip-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-size: var(--mk-font-xs);
  line-height: 1.3;
  min-width: 0;
  overflow: hidden;
}
.signal-tooltip-cell-label {
  color: var(--mk-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.signal-tooltip-cell-value {
  color: var(--mk-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
  min-width: 0;
}
</style>
