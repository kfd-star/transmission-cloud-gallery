export default class PictureEditWebSocket {
  private pictureId: number
  private socket: WebSocket | null
  private eventHandlers: any
  private pendingMessages: object[]

  constructor(pictureId: number) {
    this.pictureId = pictureId // 当前编辑的图片 ID
    this.socket = null // WebSocket 实例
    this.eventHandlers = {} // 自定义事件处理器
    this.pendingMessages = [] // WebSocket 建立前暂存待发送消息，避免点击过快导致消息丢失
  }

  /**
   * 初始化 WebSocket 连接
   */
  connect() {
    const DEV_BASE_URL = 'ws://localhost:8123'
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const PROD_BASE_URL = `${protocol}//${window.location.host}`
    const baseUrl = import.meta.env.PROD ? PROD_BASE_URL : DEV_BASE_URL
    const url = `${baseUrl}/api/ws/picture/edit?pictureId=${this.pictureId}`
    this.socket = new WebSocket(url)

    // 设置携带 cookie
    this.socket.binaryType = 'blob'

    // 监听连接成功事件
    this.socket.onopen = () => {
      console.log('WebSocket 连接已建立')
      this.triggerEvent('open')
      this.flushPendingMessages()
    }

    // 监听消息事件
    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data)
      console.log('收到消息:', message)

      // 根据消息类型触发对应事件
      const type = message.type
      this.triggerEvent(type, message)
    }

    // 监听连接关闭事件
    this.socket.onclose = (event) => {
      console.log('WebSocket 连接已关闭:', event)
      this.triggerEvent('close', event)
    }

    // 监听错误事件
    this.socket.onerror = (error) => {
      console.error('WebSocket 发生错误:', error)
      this.triggerEvent('error', error)
    }
  }

  /**
   * 关闭 WebSocket 连接
   */
  disconnect() {
    this.pendingMessages = []
    if (this.socket) {
      this.socket.close()
      console.log('WebSocket 连接已手动关闭')
    }
  }

  /**
   * 发送消息到后端
   * @param {Object} message 消息对象
   */
  sendMessage(message: object) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message))
      console.log('消息已发送:', message)
      return
    }
    if (this.socket && this.socket.readyState === WebSocket.CONNECTING) {
      this.pendingMessages.push(message)
      console.warn('WebSocket 正在连接，消息已暂存:', message)
      return
    }
    console.error('WebSocket 未连接，无法发送消息:', message)
  }

  /**
   * 连接建立后发送暂存消息
   */
  private flushPendingMessages() {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return
    }
    const messages = [...this.pendingMessages]
    this.pendingMessages = []
    messages.forEach((message) => {
      this.socket?.send(JSON.stringify(message))
      console.log('暂存消息已发送:', message)
    })
  }

  /**
   * 添加自定义事件监听
   * @param {string} type 消息类型
   * @param {Function} handler 消息处理函数
   */
  on(type: string, handler: (data?: any) => void) {
    if (!this.eventHandlers[type]) {
      this.eventHandlers[type] = []
    }
    this.eventHandlers[type].push(handler)
  }

  /**
   * 触发事件
   * @param {string} type 消息类型
   * @param {Object} data 消息数据
   */
  triggerEvent(type: string, data?: any) {
    const handlers = this.eventHandlers[type]
    if (handlers) {
      handlers.forEach((handler: any) => handler(data))
    }
  }
}
