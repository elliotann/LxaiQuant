import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getBaseUrl } from '@/api'
import { useUserStore } from '@/stores'

/**
 * K线 WebSocket 实时更新 composable
 * 使用 SockJS + STOMP 连接后端 /ws 端点（与 PC 前端一致）
 * 支持事件回调和响应式 ref 两种数据消费方式
 */
export function useKlineWebSocket() {
  let stompClient = null
  let stompSubscription = null
  const lastKline = ref(null)
  let currentSymbol = ''
  let currentInterval = ''

  /** 事件回调存储 */
  const callbacks = {
    kline_update: []
  }

  /**
   * 注册事件监听（与 PC 前端一致的事件模式）
   * @param {'kline_update'} event
   * @param {Function} handler
   */
  function on(event, handler) {
    if (!callbacks[event]) callbacks[event] = []
    callbacks[event].push(handler)
  }

  /**
   * 移除事件监听
   */
  function off(event, handler) {
    if (!callbacks[event]) return
    if (handler) {
      callbacks[event] = callbacks[event].filter(h => h !== handler)
    } else {
      callbacks[event] = []
    }
  }

  /** 触发事件 */
  function emit(event, data) {
    if (!callbacks[event]) return
    callbacks[event].forEach(handler => {
      try { handler(data) } catch (e) { /* ignore handler error */ }
    })
  }

  function getSockJsUrl() {
    const base = getBaseUrl()
    // 开发环境使用相对路径走 Vite 代理
    if (import.meta.env.DEV || !base) {
      return '/ws'
    }
    const isHttps = base.startsWith('https')
    const host = base.replace(/^https?:\/\//, '')
    return (isHttps ? 'https' : 'http') + '://' + host + '/ws'
  }

  function connect() {
    if (stompClient?.active) return

    const token = useUserStore().token

    stompClient = new Client({
      webSocketFactory: function () { return new SockJS(getSockJsUrl()) },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      connectHeaders: token ? { Authorization: 'Bearer ' + token } : {},
      onConnect: function () {
        if (currentSymbol && currentInterval) {
          subscribeTopic()
          sendSubscribe()
        }
      },
      onStompError: function () {
        if (stompClient) stompClient.deactivate()
      }
    })
    stompClient.activate()
  }

  function subscribeTopic() {
    if (!stompClient?.active) return
    // 先取消旧订阅，避免重复订阅
    if (stompSubscription) {
      try { stompSubscription.unsubscribe() } catch (e) { /* ignore */ }
      stompSubscription = null
    }
    var dest = '/topic/kline/' + currentSymbol + '_' + currentInterval
    stompSubscription = stompClient.subscribe(dest, function (message) {
      try {
        var body = JSON.parse(message.body)
        if (body.event === 'kline_update' && body.data) {
          // 通过 ref 响应式更新（兼容 Options API watch）
          lastKline.value = body.data
          // 同时通过事件回调通知（更直接的消费方式）
          emit('kline_update', body)
        }
      } catch (e) {
        // ignore parse error
      }
    })
  }

  function sendSubscribe() {
    if (!stompClient?.active) return
    stompClient.publish({
      destination: '/app/kline/subscribe',
      body: JSON.stringify({
        symbol: currentSymbol,
        interval: currentInterval
      })
    })
  }

  function subscribe(symbol, interval) {
    var s = String(symbol).trim().toUpperCase().replace(/\//g, '-')
    currentSymbol = s
    currentInterval = interval
    // 清空上次推送，避免 watch 处理旧数据
    lastKline.value = null

    if (!stompClient?.active) {
      connect()
    } else {
      subscribeTopic()
      sendSubscribe()
    }
  }

  function disconnect() {
    if (stompSubscription) {
      try { stompSubscription.unsubscribe() } catch (e) { /* ignore */ }
      stompSubscription = null
    }
    if (stompClient?.active) {
      stompClient.deactivate()
    }
    stompClient = null
    lastKline.value = null
    // 清理事件回调
    Object.keys(callbacks).forEach(k => { callbacks[k] = [] })
  }

  onUnmounted(function () {
    disconnect()
  })

  return { lastKline, subscribe, disconnect, on, off }
}
