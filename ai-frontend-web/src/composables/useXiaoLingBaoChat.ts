import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, type Ref } from "vue"
import DOMPurify from "dompurify"
import MarkdownIt from "markdown-it"
import { ElMessage, ElMessageBox } from "element-plus"
import { useAuthStore } from "@/stores/auth"

export type XiaolingbaoRole = "user" | "assistant"

export interface XiaolingbaoMessage {
  id: number
  role: XiaolingbaoRole
  content: string
  adviceId?: string
  tradeplan?: any
  tradeplanValid?: boolean
  tradeplanErrors?: string[]
  riskAccepted?: boolean
  creatingSignal?: boolean
}

export type XiaolingbaoBizPillKey = "commander" | "live_advice" | "trade_plans" | "recap"

const XIAOLINGBAO_CHAT_HISTORY_STORAGE_KEY = "xiaolingbao_chat_history_market_v1"
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: true,
})
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token.attrGet("href") || ""
  if (/^javascript:/i.test(href)) {
    token.attrSet("href", "#")
  }
  token.attrSet("target", "_blank")
  token.attrSet("rel", "noopener noreferrer")
  return self.renderToken(tokens, idx, options)
}

function stripTradeplanForDisplay(content: string) {
  const raw = typeof content === "string" ? content : String(content ?? "")
  let stripped = raw.replace(
    /(^|\n)```{3,}tradeplan[^\n]*\n[\s\S]*?\n```{3,}\s*(?=\n|$)/g,
    "\n",
  )
  stripped = stripped.replace(/<!--tradeplan[\s\S]*?-->/g, "")

  // 去掉标记前缀，只保留标记之后的内容
  const markers = ["[ADVICE_START]", "[RECAP_START]"]
  for (const marker of markers) {
    const idx = stripped.indexOf(marker)
    if (idx !== -1) {
      stripped = stripped.slice(idx + marker.length)
      break
    }
  }

  return stripped.trim()
}

function normalizeAdviceButtonMarkers(content: string, msg: XiaolingbaoMessage) {
  const raw = typeof content === "string" ? content : String(content ?? "")
  const lines = raw.split(/\r?\n/)
  const out: string[] = []
  let hasMarker = false
  for (const line of lines) {
    const trimmed = line.trim()
    if (!line.includes("【按钮位置】")) {
      out.push(line)
      continue
    }
    hasMarker = true
    const m = line.match(/action\s*:\s*([a-z_]+)/i)
    if (m && m[1]) {
      out.push(`[[ADVICE_ACTION:${m[1].toLowerCase()}]]`)
      continue
    }
    out.push("")
  }
  const isLikelyLiveAdvice =
    msg?.role === "assistant" &&
    /趋势跟踪建议/.test(out.join("\n")) &&
    (/15分钟入场策略/.test(out.join("\n")) || out.join("\n").includes("主策略"))
  if (!hasMarker && (msg?.tradeplan || isLikelyLiveAdvice)) {
    const isTitle = (t: string, keyword: string) => {
      const s = (t || "").trim()
      if (!s) return false
      const re = new RegExp(`^(?:#{1,6}\\s*)?(?:\\d+\\.?\\s*)?${keyword}`)
      return re.test(s)
    }
    const injected: string[] = []
    let insertedMain = false
    let insertedAlt = false
    let insertedHold = false
    let insertedAll = false
    let inChecklist = false
    for (let i = 0; i < out.length; i++) {
      const line = out[i]
      const t = String(line || "").trim()
      if (!insertedMain && isTitle(t, "主策略")) {
        injected.push(line)
        injected.push("[[ADVICE_ACTION:limit_signal]]")
        insertedMain = true
        continue
      }
      if (!insertedAlt && isTitle(t, "备选策略")) {
        injected.push(line)
        injected.push("[[ADVICE_ACTION:cond_signal]]")
        insertedAlt = true
        continue
      }
      if (!insertedHold && isTitle(t, "持仓")) {
        injected.push(line)
        injected.push("[[ADVICE_ACTION:hedge_signal]]")
        injected.push("[[ADVICE_ACTION:close_signal]]")
        insertedHold = true
        continue
      }
      if (isTitle(t, "执行清单")) {
        inChecklist = true
        injected.push(line)
        continue
      }
      if (inChecklist && isTitle(t, "风险提醒") && !insertedAll) {
        injected.push("[[ADVICE_ACTION:all_signals]]")
        insertedAll = true
        inChecklist = false
        injected.push(line)
        continue
      }
      injected.push(line)
    }
    if (!insertedAll) {
      injected.push("[[ADVICE_ACTION:all_signals]]")
    }
    return injected.join("\n").trim()
  }
  return out.join("\n").trim()
}

function getTradeplanEntryType(msg: XiaolingbaoMessage) {
  return String(msg?.tradeplan?.advice?.entry?.type || "").toUpperCase()
}

function getTradeplanHasAlternative(msg: XiaolingbaoMessage) {
  return !!msg?.tradeplan?.alternativeAdvice
}

function getTradeplanHasPositions(msg: XiaolingbaoMessage) {
  const positions = msg?.tradeplan?.facts?.riskStatus?.positions
  return Array.isArray(positions) && positions.length > 0
}

function isActionSupported(action: string) {
  return (
    action === "limit_signal" ||
    action === "cond_signal" ||
    action === "hedge_signal" ||
    action === "close_signal" ||
    action === "all_signals"
  )
}

function getActionLabel(msg: XiaolingbaoMessage, action: string) {
  const entryType = getTradeplanEntryType(msg)
  if (action === "limit_signal") {
    if (entryType === "MARKET") return "生成市价单信号"
    return "生成限价单信号"
  }
  if (action === "cond_signal") return "生成条件单信号"
  if (action === "hedge_signal") return "生成对冲信号"
  if (action === "close_signal") return "生成平仓信号"
  if (action === "all_signals") return "一键生成全部信号"
  return "生成信号"
}

function buildAdviceId() {
  const rand = Math.random().toString(16).slice(2, 8)
  return `adv_${Date.now()}_${rand}`
}

function extractTradeplanFromContent(content: string) {
  if (!content) return null
  const start = content.indexOf("<!--tradeplan")
  if (start === -1) return null
  const end = content.indexOf("-->", start)
  if (end === -1) return null
  const jsonText = content.slice(start + "<!--tradeplan".length, end).trim()
  if (!jsonText) return null
  try {
    return JSON.parse(jsonText)
  } catch {
    return null
  }
}

function buildSignalSummary(msg: XiaolingbaoMessage, action: string) {
  const advice =
    action === "cond_signal"
      ? msg?.tradeplan?.alternativeAdvice || {}
      : msg?.tradeplan?.advice || {}
  const positions = msg?.tradeplan?.facts?.riskStatus?.positions
  const firstPosSide =
    Array.isArray(positions) && positions.length
      ? String((positions[0] as any)?.side || "")
      : ""
  const entry = advice?.entry || {}
  const entryType = String(entry?.type || "").toUpperCase()
  const signalStrength = (advice as any)?.signalStrength
  const takeProfit = Array.isArray(advice?.takeProfit) ? advice.takeProfit : []
  const tpText = takeProfit.map((t: any) => `${t?.level ?? ""}(${t?.ratio ?? ""})`).filter(Boolean).join(" / ")
  const lines = [
    `动作：${action}`,
    action === "hedge_signal"
      ? `持仓：${firstPosSide}，对冲：${firstPosSide === "LONG" ? "SHORT" : firstPosSide === "SHORT" ? "LONG" : "-"}`
      : action === "close_signal"
        ? `持仓：${firstPosSide}`
        : `方向：${String(advice?.direction || "")}`,
    `入场类型：${entryType}`,
    entryType === "LIMIT" ? `入场价：${String(entry?.price ?? "")}` : "",
    entryType === "CONDITION" ? `条件：${String(entry?.condition ?? "")}` : "",
    `止损：${String(advice?.stopLoss ?? "")}`,
    tpText ? `止盈：${tpText}` : "",
    signalStrength != null ? `仓位权重：${String(signalStrength)}` : "",
  ].filter(Boolean)
  return lines.join("\n")
}

async function createSignalFromAdvice(msg: XiaolingbaoMessage, action: string) {
  const adviceId = msg.adviceId || buildAdviceId()
  msg.adviceId = adviceId
  if (!isActionSupported(action)) {
    ElMessage.warning(`当前系统未实现该动作：${action}`)
    return
  }
  if (msg.tradeplanValid === false) {
    const err = Array.isArray(msg.tradeplanErrors) ? msg.tradeplanErrors.join("；") : "tradeplan 校验未通过"
    ElMessage.warning(err)
    return
  }
  const summary = msg.tradeplan ? buildSignalSummary(msg, action) : `动作：${action}\nadviceId：${adviceId}\n说明：将以后端缓存的建议为准生成信号`
  try {
    await ElMessageBox.confirm(summary, "确认生成技术信号", {
      confirmButtonText: "确认生成",
      cancelButtonText: "取消",
      type: "warning",
    })
  } catch {
    return
  }
  msg.creatingSignal = true
  try {
    const createToken = useAuthStore().token
    const res = await fetch("/api/signal/create-from-advice", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(createToken ? { Authorization: `Bearer ${createToken}` } : {}),
      },
      body: JSON.stringify({ adviceId, action, options: { tradeplan: msg.tradeplan ?? null } }),
    })
    const json = (await res.json()) as any
    if (!json?.success) {
      throw new Error(json?.message || "生成信号失败")
    }
    const data = json?.data || {}
    const idText =
      typeof data?.signalId === "number"
        ? String(data.signalId)
        : Array.isArray(data?.signalIds) && data.signalIds.length
          ? data.signalIds.join(",")
          : "-"
    ElMessage.success(`信号已生成（ID: ${idText}），量化系统将自动处理`)
  } catch (e: any) {
    ElMessage.error(e?.message || "生成信号失败")
  } finally {
    msg.creatingSignal = false
  }
}

function buildAdviceButtonHtml(msg: XiaolingbaoMessage, action: string) {
  const supported = isActionSupported(action)
  const disabled = msg?.tradeplanValid === false || !!msg?.creatingSignal || !supported
  const label = getActionLabel(msg, action)
  const cls = `xiaolingbao-inline-btn${disabled ? " disabled" : ""}${supported ? "" : " unsupported"}`
  const disabledAttr = disabled ? " disabled" : ""
  return `<button type="button" class="${cls}" data-xlb-kind="action" data-xlb-action="${action}" data-xlb-msgid="${msg.id}"${disabledAttr}>${label}</button>`
}

function shouldShowAction(msg: XiaolingbaoMessage, action: string) {
  const content = typeof msg?.content === "string" ? msg.content : String(msg?.content ?? "")
  const isLikelyLiveAdvice =
    msg?.role === "assistant" &&
    /趋势跟踪建议/.test(content) &&
    (/15分钟入场策略/.test(content) || /主策略/.test(content))
  if (action === "limit_signal") {
    if (!msg?.tradeplan) return isLikelyLiveAdvice
    const entryType = getTradeplanEntryType(msg)
    if (entryType === "LIMIT" || entryType === "MARKET") return true
    return isLikelyLiveAdvice
  }
  if (action === "cond_signal") {
    if (!msg?.tradeplan) return isLikelyLiveAdvice && /备选策略/.test(content)
    if (getTradeplanHasAlternative(msg)) return true
    return isLikelyLiveAdvice && /备选策略/.test(content)
  }
  if (action === "hedge_signal" || action === "close_signal") {
    if (!msg?.tradeplan) return false
    return getTradeplanHasPositions(msg)
  }
  if (action === "all_signals") {
    if (!msg?.tradeplan) return isLikelyLiveAdvice
    return true
  }
  return false
}

function injectAdviceButtons(sanitizedHtml: string, msg: XiaolingbaoMessage) {
  let html = sanitizedHtml
  html = html.replace(/\[\[ADVICE_ACTION:([a-z_]+)\]\]/g, (_full, action: string) => {
    const a = String(action || "").toLowerCase()
    if (!shouldShowAction(msg, a)) return ""
    return buildAdviceButtonHtml(msg, a)
  })
  return html
}

function renderMarkdown(msg: XiaolingbaoMessage) {
  const raw = normalizeAdviceButtonMarkers(stripTradeplanForDisplay(msg.content), msg)
  const html = md.render(raw)
  const compactHtml = html.replace(/>\s+</g, "><")
  const sanitized = DOMPurify.sanitize(compactHtml, {
    ALLOWED_TAGS: [
      "p", "br", "strong", "em", "ul", "ol", "li", "pre", "code",
      "blockquote", "a", "h1", "h2", "h3", "h4", "h5", "h6", "hr",
    ],
    ALLOWED_ATTR: ["href", "target", "rel", "class"],
  })
  return injectAdviceButtons(sanitized, msg)
}

export function useXiaoLingBaoChat(options: {
  symbol: Ref<string>
  botId: Ref<string>
  interval: Ref<string>
}) {
  const messages = ref<XiaolingbaoMessage[]>([
    { id: Date.now(), role: "assistant", content: "你好，我是小灵宝。你可以直接问我行情、策略、止盈止损怎么设置等问题。" },
  ])
  const input = ref("")
  const sending = ref(false)
  const historyRef = ref<HTMLDivElement | null>(null)
  const activeBizPill = ref<XiaolingbaoBizPillKey | null>(null)

  const bizPills = [
    { key: "commander" as const, label: "AI指挥官" },
    { key: "live_advice" as const, label: "实时建议" },
    { key: "trade_plans" as const, label: "交易计划" },
    { key: "recap" as const, label: "复盘" },
  ]

  const inputPlaceholder = computed(() => {
    if (activeBizPill.value === "commander") return "输入标的或指令（例如 BTC / BTC-USDT-SWAP / 观望条件）"
    if (activeBizPill.value === "live_advice") return "请输入需要分析的标的"
    if (activeBizPill.value === "recap") return "发送复盘指令（例如 今日复盘 / 昨日复盘 / 本周复盘）"
    return "向小灵宝提问"
  })

  function scrollToBottom(force = false) {
    const el = historyRef.value
    if (!el) return
    if (!force) {
      const distance = el.scrollHeight - el.scrollTop - el.clientHeight
      if (distance > 120) return
    }
    el.scrollTop = el.scrollHeight
  }

  function onBizPillClick(key: XiaolingbaoBizPillKey) {
    if (key === "recap" && activeBizPill.value !== "recap") {
      activeBizPill.value = "recap"
      input.value = options.symbol.value + " 复盘"
      nextTick(() => sendMessage())
      return
    }
    activeBizPill.value = activeBizPill.value === key ? null : key
    if (key === "live_advice" && !input.value.trim()) {
      input.value = options.symbol.value
    }
  }

  function onHistoryClick(e: MouseEvent) {
    const target = e.target
    if (!(target instanceof Element)) return
    const el = target.closest("[data-xlb-kind]") as HTMLElement | null
    if (!el) return
    const kind = el.getAttribute("data-xlb-kind") || ""
    const msgId = Number(el.getAttribute("data-xlb-msgid") || "")
    if (!Number.isFinite(msgId)) return
    const msg = messages.value.find((m) => m.id === msgId)
    if (!msg) return
    if (kind === "risk") {
      msg.riskAccepted = !msg.riskAccepted
      persistChatHistoryNow()
      return
    }
    if (kind === "action") {
      if (el.hasAttribute("disabled") || el.classList.contains("disabled")) return
      const action = String(el.getAttribute("data-xlb-action") || "").trim()
      if (!action) return
      createSignalFromAdvice(msg, action)
    }
  }

  function clearHistory() {
    if (!window.confirm("确定清除所有聊天记录？")) return
    messages.value = [
      { id: Date.now(), role: "assistant", content: "你好，我是小灵宝。你可以直接问我行情、策略、止盈止损怎么设置等问题。" },
    ]
    flushChatHistoryNow()
  }

  function buildChatHistorySnapshot() {
    const items = (messages.value as any[])
      .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
      .map((m) => ({
        id: Number.isFinite(Number(m.id)) ? Number(m.id) : Date.now(),
        role: String(m.role),
        content: String(m.content || ""),
        adviceId: typeof m.adviceId === "string" ? m.adviceId : undefined,
        tradeplan: m.tradeplan ?? undefined,
        tradeplanValid: m.tradeplanValid ?? undefined,
        tradeplanErrors: Array.isArray(m.tradeplanErrors) ? m.tradeplanErrors : undefined,
        riskAccepted: typeof m.riskAccepted === "boolean" ? m.riskAccepted : undefined,
      }))
      .filter((m) => {
        const c = m.content.trim()
        if (!c) return false
        if (m.role === "assistant" && c === "小灵宝思考中…") return false
        return true
      })
    const hasGreeting = items.some((m) => m.role === "assistant" && String(m.content || "").includes("你好，我是小灵宝"))
    const normalized = hasGreeting
      ? items
      : [{ id: Date.now(), role: "assistant", content: "你好，我是小灵宝。你可以直接问我行情、策略、止盈止损怎么设置等问题。" }, ...items]
    return normalized.length > 120 ? [normalized[0], ...normalized.slice(-119)] : normalized
  }

  function persistChatHistoryNow() {
    try {
      localStorage.setItem(XIAOLINGBAO_CHAT_HISTORY_STORAGE_KEY, JSON.stringify(buildChatHistorySnapshot()))
    } catch {
    }
  }

  let persistTimer: number | null = null

  function flushChatHistoryNow() {
    if (persistTimer) {
      window.clearTimeout(persistTimer)
      persistTimer = null
    }
    persistChatHistoryNow()
  }

  function schedulePersistChatHistory() {
    if (persistTimer) window.clearTimeout(persistTimer)
    persistTimer = window.setTimeout(() => {
      persistTimer = null
      persistChatHistoryNow()
    }, 800)
  }

  function loadChatHistory() {
    try {
      const raw = localStorage.getItem(XIAOLINGBAO_CHAT_HISTORY_STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw)
      if (!Array.isArray(parsed) || parsed.length === 0) return
      const normalized = (parsed as any[])
        .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
        .map((m) => ({
          id: Number.isFinite(Number(m.id)) ? Number(m.id) : Date.now(),
          role: String(m.role) as XiaolingbaoRole,
          content: String(m.content || ""),
          adviceId: typeof m.adviceId === "string" ? m.adviceId : undefined,
          tradeplan: m.tradeplan ?? undefined,
          tradeplanValid: m.tradeplanValid ?? undefined,
          tradeplanErrors: Array.isArray(m.tradeplanErrors) ? m.tradeplanErrors : [],
          riskAccepted: typeof m.riskAccepted === "boolean" ? m.riskAccepted : false,
        }))
        .filter((m) => {
          const c = m.content.trim()
          if (!c) return false
          if (m.role === "assistant" && c === "小灵宝思考中…") return false
          return true
        })
      if (normalized.length > 0) {
        messages.value = normalized as XiaolingbaoMessage[]
      }
    } catch {
    }
  }

  async function sendMessage() {
    if (sending.value) return
    const text = input.value.trim()
    if (!text) return
    const biz = activeBizPill.value
    const label = (bizPills.find((p) => p.key === biz)?.label as string | undefined) || ""
    const requestUserText =
      biz === "live_advice" && !/^实时建议\s*[:：]/.test(text) ? `实时建议：${text}` : text
    const userMsg: XiaolingbaoMessage = { id: Date.now(), role: "user", content: requestUserText }
    const assistantMsg: XiaolingbaoMessage = { id: Date.now() + 1, role: "assistant", content: "小灵宝思考中…" }
    messages.value = [...messages.value, userMsg, assistantMsg]
    input.value = ""
    scrollToBottom(true)
    persistChatHistoryNow()
    sending.value = true
    const getAssistantMsg = () => {
      const msgs = messages.value
      return msgs[msgs.length - 1]
    }
    try {
      const historyForRequest = (messages.value as any[])
        .filter((m) => (m?.role === "user" || m?.role === "assistant") && typeof m?.content === "string")
        .map((m) => ({ role: String(m.role), content: String(m.content || "") }))
        .filter((m) => m.content.trim() && m.content !== "小灵宝思考中…")
        .slice(-12)
      let requestUrl = "/api/llm/generate"
      let requestBody: any = null
      if (biz === "live_advice") {
        requestUrl = "/api/agent/chat/stream"
        requestBody = {
          stream: true,
          skill: "live-advice",
          message: `${options.symbol.value} ${requestUserText}`.trim(),
          sessionId: "xiaolingbao-market",
        }
      } else if (biz === "recap") {
        const now = new Date()
        const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
        const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
        requestUrl = "/api/agent/chat/stream"
        requestBody = {
          stream: true,
          skill: "recap",
          message: `复盘 ${options.symbol.value}，机器人ID: ${options.botId.value || ""}，开始日期: ${fmt(yesterday)}，结束日期: ${fmt(now)}。${requestUserText}`,
          sessionId: "xiaolingbao-recap",
        }
      } else {
        const systemPrompt =
          `你是小灵宝（智能量化助手），回答以中文为主，偏向可执行建议，尽量简短清晰。\n` +
          `当前页面：市场行情。\n` +
          `交易对：${options.symbol.value}。\n` +
          `周期：${options.interval.value}。\n` +
          (label ? `当前模式：${label}\n` : "")
        requestBody = {
          stream: true,
          messages: [
            { role: "system", content: systemPrompt },
            ...historyForRequest,
            { role: "user", content: requestUserText },
          ],
        }
      }
      const chatToken = useAuthStore().token
      const res = await fetch(requestUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(chatToken ? { Authorization: `Bearer ${chatToken}` } : {}),
        },
        body: JSON.stringify(requestBody),
      })
      if (!res.ok) {
        const errText = await res.text().catch(() => "")
        throw new Error(errText || `HTTP ${res.status}`)
      }
      if (!requestBody?.stream) {
        const json = (await res.json()) as any
        if (json?.error) {
          throw new Error(json.error)
        }
        const answer = String(json.response ?? "").trim()
        assistantMsg.content = answer || "我没有拿到有效回复，请稍后再试。"
        if (biz === "live_advice") {
          assistantMsg.tradeplan = json?.tradeplan ?? null
          assistantMsg.tradeplanValid = json?.tradeplanValid
          assistantMsg.tradeplanErrors = Array.isArray(json?.tradeplanErrors) ? json.tradeplanErrors : []
          assistantMsg.adviceId = typeof json?.adviceId === "string" && json.adviceId.trim()
            ? json.adviceId.trim()
            : buildAdviceId()
          assistantMsg.riskAccepted = false
        }
        return
      }
      if (!res.body) {
        throw new Error("响应体为空")
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      const contentType = res.headers.get("content-type") || ""
      const isSse = contentType.includes("text/event-stream")
      let buffer = ""
      const markerConfig: Record<string, { marker: string; buffer: string }> = {
        live_advice: { marker: "[ADVICE_START]", buffer: "" },
        recap: { marker: "[RECAP_START]", buffer: "" },
      }
      const processPayload = (jsonText: string) => {
        const text = (jsonText || "").trim()
        if (!text) return false
        if (text === "[DONE]") return true
        if (!text.startsWith("{")) return false
        let chunk: any
        try {
          chunk = JSON.parse(text)
        } catch (_) {
          return false
        }
        if (chunk?.error) {
          throw new Error(chunk.error)
        }
        const msg = getAssistantMsg()
        const delta = chunk?.response ?? ""
        if (delta) {
          const cfg = markerConfig[biz]
          if (cfg) {
            // 有标记配置的 biz：缓冲直到出现标记才显示
            cfg.buffer += String(delta)
            const markerIdx = cfg.buffer.indexOf(cfg.marker)
            if (markerIdx !== -1) {
              if (msg.content === "小灵宝思考中…") msg.content = ""
              msg.content = cfg.buffer.slice(markerIdx + cfg.marker.length)
              scrollToBottom(true)
            } else {
              // 还没到关键内容，保持"小灵宝思考中…"
            }
          } else {
            // 无标记配置：直接追加
            if (msg.content === "小灵宝思考中…") msg.content = ""
            msg.content += String(delta)
            scrollToBottom(true)
          }
        }
        if (biz === "live_advice") {
          if (!msg.tradeplan) {
            msg.tradeplan = extractTradeplanFromContent(msg.content)
          }
          if (chunk?.tradeplan !== undefined) {
            msg.tradeplan = chunk.tradeplan
          }
          if (chunk?.tradeplanValid !== undefined) {
            msg.tradeplanValid = chunk.tradeplanValid
          }
          if (chunk?.tradeplanErrors !== undefined) {
            msg.tradeplanErrors = Array.isArray(chunk.tradeplanErrors) ? chunk.tradeplanErrors : []
          }
          if (typeof chunk?.adviceId === "string" && chunk.adviceId.trim()) {
            msg.adviceId = chunk.adviceId.trim()
          }
          if (msg.tradeplan && msg.riskAccepted == null) {
            msg.riskAccepted = false
          }
        }
        return Boolean(chunk?.done)
      }
      const processSseEvent = (eventText: string) => {
        const lines = eventText.split(/\r?\n/)
        const dataParts: string[] = []
        for (const raw of lines) {
          const line = raw.trimEnd()
          if (!line) continue
          if (line.startsWith(":")) continue
          if (line.startsWith("data:")) {
            dataParts.push(line.slice("data:".length).trimStart())
          }
        }
        if (dataParts.length === 0) return false
        return processPayload(dataParts.join("\n"))
      }
      let doneByServer = false
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        if (isSse) {
          for (;;) {
            const idxLF = buffer.indexOf("\n\n")
            const idxCRLF = buffer.indexOf("\r\n\r\n")
            let idx = -1
            let sepLen = 0
            if (idxCRLF !== -1 && (idxLF === -1 || idxCRLF < idxLF)) {
              idx = idxCRLF
              sepLen = 4
            } else if (idxLF !== -1) {
              idx = idxLF
              sepLen = 2
            }
            if (idx === -1) break
            const eventText = buffer.slice(0, idx)
            buffer = buffer.slice(idx + sepLen)
            if (processSseEvent(eventText)) {
              doneByServer = true
              break
            }
          }
        } else {
          const lines = buffer.split(/\r?\n/)
          buffer = lines.pop() ?? ""
          for (const raw of lines) {
            const line = raw.trim()
            if (!line) continue
            if (line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:")) continue
            const jsonText = line.startsWith("data:") ? line.slice("data:".length).trim() : line
            if (processPayload(jsonText)) {
              doneByServer = true
              break
            }
          }
        }
        if (doneByServer) break
      }
      buffer += decoder.decode()
      if (isSse) {
        const rest = buffer.trim()
        if (rest) {
          try { processSseEvent(rest) } catch (_) { }
        }
      } else {
        const tailLines = buffer.split(/\r?\n/).filter((l) => l.trim())
        for (const raw of tailLines) {
          const line = raw.trim()
          if (!line) continue
          if (line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:")) continue
          const jsonText = line.startsWith("data:") ? line.slice("data:".length).trim() : line
          if (processPayload(jsonText)) break
        }
      }
    } catch (e: any) {
      const errMsg = getAssistantMsg()
      errMsg.content = e?.message ? `请求失败：${e.message}` : "请求失败"
    } finally {
      // 带标记的 biz：流结束仍未出现标记时，显示缓冲的全部内容
      const cfg = markerConfig[biz]
      if (cfg && cfg.buffer) {
        const msg2 = getAssistantMsg()
        if (msg2.content === "小灵宝思考中…" || !msg2.content) {
          msg2.content = cfg.buffer
        }
      }
      sending.value = false
      flushChatHistoryNow()
      nextTick(() => scrollToBottom())
    }
  }

  watch(messages, schedulePersistChatHistory, { deep: true })

  const onVisibilityChange = () => {
    if (document.visibilityState === "hidden") {
      flushChatHistoryNow()
    }
  }
  const onBeforeUnload = () => {
    flushChatHistoryNow()
  }

  onMounted(() => {
    loadChatHistory()
    window.addEventListener("beforeunload", onBeforeUnload)
    document.addEventListener("visibilitychange", onVisibilityChange)
  })

  onBeforeUnmount(() => {
    window.removeEventListener("beforeunload", onBeforeUnload)
    document.removeEventListener("visibilitychange", onVisibilityChange)
    flushChatHistoryNow()
  })

  return {
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
  }
}
