<template>
  <div v-show="visible" class="xlb-dialog-overlay" @click.self="$emit('close')">
    <div class="xlb-dialog">
      <div class="xlb-header">
        <span class="xlb-title"><i class="xlb-logo"></i>小灵宝</span>
        <div class="xlb-actions">
          <button class="xlb-clear" title="清除聊天记录" @click="clearHistory">🗑</button>
          <button class="xlb-close" @click="$emit('close')">×</button>
        </div>
      </div>
      <div class="xlb-body">
        <div class="xlb-history" ref="historyRef" @click="onHistoryClick">
          <div v-for="msg in messages" :key="msg.id" class="xlb-msg" :class="msg.role">
            <div class="xlb-role">
              <span class="xlb-avatar">{{ msg.role === 'assistant' ? '灵' : '我' }}</span>
              <span class="xlb-name">{{ msg.role === 'assistant' ? '小灵宝' : '你' }}</span>
            </div>
            <div class="xlb-content markdown-body" v-html="renderMarkdown(msg)"></div>
          </div>
        </div>
        <div class="xlb-chat-input-shell">
          <div class="xlb-biz-pill-bar">
            <span
              v-for="pill in bizPills"
              :key="pill.key"
              class="xlb-biz-pill"
              :class="{ active: activeBizPill === pill.key }"
              @click="onBizPillClick(pill.key)"
            >{{ pill.label }}</span>
          </div>
          <div class="xlb-chat-input-top">
            <input
              class="xlb-chat-input"
              v-model="input"
              :placeholder="inputPlaceholder"
              @keydown.enter="sendMessage"
              :disabled="sending"
            />
            <button
              class="xlb-send-btn"
              :class="{ disabled: sending || !input.trim() }"
              @click="sendMessage"
              :disabled="sending || !input.trim()"
            >
              <span class="xlb-send-btn-icon">➤</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, nextTick } from "vue"
import { useXiaoLingBaoChat } from "@/composables/useXiaoLingBaoChat"

const props = defineProps<{
  visible: boolean
  symbol: string
  botId: string
  interval: string
}>()

const emit = defineEmits<{
  (e: "close"): void
}>()

const symbolRef = computed(() => props.symbol)
const botIdRef = computed(() => props.botId)
const intervalRef = computed(() => props.interval)

const {
  messages,
  input,
  sending,
  historyRef,
  bizPills,
  activeBizPill,
  inputPlaceholder,
  sendMessage,
  clearHistory,
  onHistoryClick,
  onBizPillClick,
  scrollToBottom,
  renderMarkdown,
} = useXiaoLingBaoChat({
  symbol: symbolRef,
  botId: botIdRef,
  interval: intervalRef,
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      nextTick(() => {
        scrollToBottom(true)
        setTimeout(() => scrollToBottom(true), 80)
      })
    }
  },
)
</script>

<style scoped>
.xlb-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: transparent;
}
.xlb-dialog {
  position: fixed;
  bottom: 60px;
  right: 50px;
  width: 360px;
  height: 560px;
  background: var(--mk-bg-secondary);
  border: 1px solid var(--mk-border);
  border-radius: var(--mk-radius-xl);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 1001;
}
.xlb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--mk-space-8) var(--mk-space-12);
  background: var(--mk-bg-primary);
  border-bottom: 1px solid var(--mk-border);
}
.xlb-title {
  font-size: var(--mk-font-base);
  font-weight: 600;
  color: var(--mk-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.xlb-logo {
  display: inline-block;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: url("@/assets/xiaolingbao-logo.svg") center / cover no-repeat;
  flex-shrink: 0;
}
.xlb-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.xlb-clear {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: var(--mk-font-base);
  color: var(--mk-text-secondary);
  cursor: pointer;
  border-radius: var(--mk-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.xlb-clear:hover {
  background: var(--mk-bg-tertiary);
  color: var(--mk-text-tertiary);
}
.xlb-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: var(--mk-font-xl);
  color: var(--mk-text-secondary);
  cursor: pointer;
  border-radius: var(--mk-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.xlb-close:hover {
  background: var(--mk-bg-tertiary);
  color: var(--mk-text-tertiary);
}
.xlb-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--mk-space-12);
}
.xlb-history {
  flex: 1;
  overflow-y: auto;
  padding: 8px 8px 0 8px;
  border: 1px solid var(--mk-border);
  border-radius: var(--mk-radius-lg);
  background: var(--mk-bg-primary);
  margin-bottom: var(--mk-space-12);
}
.xlb-msg {
  display: flex;
  padding: 8px 0;
  flex-direction: column;
  align-items: flex-start;
}
.xlb-role {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mk-color-brand);
  font-size: var(--mk-font-sm);
  margin-bottom: 6px;
}
.xlb-avatar {
  width: 18px;
  height: 18px;
  border-radius: 9px;
  background: rgba(64, 158, 255, 0.12);
  border: 1px solid rgba(64, 158, 255, 0.25);
  color: var(--mk-color-brand);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--mk-font-sm);
  line-height: 18px;
}
.xlb-name {
  color: var(--mk-color-brand);
}
.xlb-content {
  font-size: var(--mk-font-xs);
  line-height: 1.5;
  color: var(--mk-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  padding: 8px 10px;
  border-radius: var(--mk-radius-xl);
  background: var(--mk-bg-primary);
  border: 1px solid var(--mk-border-input);
  max-width: 96%;
}
.xlb-content.markdown-body {
  white-space: pre-wrap;
}
:deep(.xlb-content.markdown-body h1),
:deep(.xlb-content.markdown-body h2),
:deep(.xlb-content.markdown-body h3),
:deep(.xlb-content.markdown-body h4) {
  font-size: var(--mk-font-xs);
  margin: 6px 0 4px 0;
}
:deep(.xlb-content.markdown-body p) {
  margin: 0 0 4px 0;
}
:deep(.xlb-content.markdown-body p:last-child) {
  margin-bottom: 0;
}
:deep(.xlb-content.markdown-body li > p) {
  margin: 0;
}
:deep(.xlb-content.markdown-body ul),
:deep(.xlb-content.markdown-body ol) {
  margin: 4px 0 4px 16px;
  padding: 0;
}
:deep(.xlb-content.markdown-body li) {
  margin: 1px 0;
}
:deep(.xlb-content.markdown-body code) {
  background: rgba(64, 158, 255, 0.12);
  border: 1px solid rgba(64, 158, 255, 0.2);
  padding: 0 6px;
  border-radius: var(--mk-radius-md);
  font-size: var(--mk-font-sm);
}
:deep(.xlb-content.markdown-body pre) {
  background: var(--mk-bg-secondary);
  border: 1px solid var(--mk-border);
  border-radius: var(--mk-radius-xl);
  padding: 12px;
  overflow: auto;
  margin: 10px 0;
}
:deep(.xlb-content.markdown-body pre code) {
  background: transparent;
  border: 0;
  padding: 0;
  font-size: var(--mk-font-sm);
}
:deep(.xlb-content.markdown-body blockquote) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--mk-color-brand);
  background: rgba(64, 158, 255, 0.08);
  border-radius: var(--mk-radius-lg);
}
:deep(.xlb-content.markdown-body a) {
  color: var(--mk-color-brand);
  text-decoration: none;
}
:deep(.xlb-content.markdown-body a:hover) {
  text-decoration: underline;
}
:deep(.xlb-content.markdown-body h1),
:deep(.xlb-content.markdown-body h2),
:deep(.xlb-content.markdown-body h3) {
  margin: 10px 0 8px 0;
  font-weight: 700;
}
.xlb-msg.user {
  align-items: flex-end;
}
.xlb-msg.user .xlb-role {
  justify-content: flex-end;
  color: var(--mk-color-up);
}
.xlb-msg.user .xlb-avatar {
  background: color-mix(in srgb, var(--mk-color-up) 12%, transparent);
  border-color: color-mix(in srgb, var(--mk-color-up) 25%, transparent);
  color: var(--mk-color-up);
}
.xlb-msg.assistant .xlb-content {
  background: var(--mk-bg-primary);
  color: var(--mk-text-primary);
}
:deep(.xiaolingbao-inline-btn) {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: var(--mk-radius-lg);
  border: 1px solid var(--mk-color-brand);
  background: var(--mk-color-brand);
  color: var(--mk-text-inverse);
  font-size: var(--mk-font-sm);
  cursor: pointer;
  user-select: none;
  margin-right: 10px;
  margin-top: 6px;
  appearance: none;
  outline: none;
}
:deep(.xiaolingbao-inline-btn.disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}
:deep(.xiaolingbao-inline-btn.unsupported) {
  border-color: rgba(144, 147, 153, 0.35);
  background: rgba(144, 147, 153, 0.18);
  color: var(--mk-text-tertiary);
}
:deep(.xiaolingbao-risk-pill) {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: var(--mk-font-sm);
  border: 1px solid color-mix(in srgb, var(--mk-color-brand) 25%, transparent);
  background: color-mix(in srgb, var(--mk-color-brand) 8%, transparent);
  color: var(--mk-color-brand);
  cursor: pointer;
  user-select: none;
  appearance: none;
  outline: none;
}
:deep(.xiaolingbao-risk-pill.on) {
  background: color-mix(in srgb, var(--mk-color-up) 12%, transparent);
  border-color: color-mix(in srgb, var(--mk-color-up) 25%, transparent);
  color: var(--mk-color-up);
}
.xlb-chat-input-shell {
  display: flex;
  flex-direction: column;
  gap: var(--mk-space-8);
}
.xlb-chat-input-top {
  display: flex;
  align-items: center;
  gap: var(--mk-space-8);
}
.xlb-chat-input {
  flex: 1;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--mk-border-input);
  border-radius: 20px;
  outline: none;
  font-size: var(--mk-font-md);
  color: var(--mk-text-primary);
  background: var(--mk-bg-primary);
}
.xlb-chat-input::placeholder {
  color: var(--mk-text-tertiary);
}
.xlb-chat-input:-webkit-autofill {
  -webkit-text-fill-color: var(--mk-text-primary);
  -webkit-box-shadow: 0 0 0 1000px var(--mk-bg-primary) inset;
  transition: background-color 5000s ease-in-out 0s;
}
.xlb-chat-input:disabled {
  background: var(--mk-bg-secondary);
  color: var(--mk-text-tertiary);
}
.xlb-send-btn {
  width: 36px;
  height: 36px;
  border-radius: 18px;
  background: rgba(64, 158, 255, 0.12);
  border: 1px solid rgba(64, 158, 255, 0.25);
  color: var(--mk-color-brand);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
}
.xlb-send-btn:hover {
  background: rgba(64, 158, 255, 0.18);
}
.xlb-send-btn.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.xlb-send-btn-icon {
  font-size: var(--mk-font-xl);
}
.xlb-biz-pill-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.xlb-biz-pill {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--mk-border-input);
  background: var(--mk-bg-primary);
  color: var(--mk-text-tertiary);
  font-size: var(--mk-font-sm);
  cursor: pointer;
  user-select: none;
}
.xlb-biz-pill:hover {
  border-color: var(--mk-text-tertiary);
}
.xlb-biz-pill.active {
  border-color: rgba(64, 158, 255, 0.5);
  background: rgba(64, 158, 255, 0.12);
  color: var(--mk-color-brand);
}
</style>
