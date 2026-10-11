/** 只列出完整 API 路径，避免 URL 子串（含查询参数）误命中白名单。 */
const tokenFreePaths = new Set([
  '/system/auth/login',
  '/system/auth/refresh-token',
  '/system/tenant/get-id-by-name',
])

interface AuthHeaderOptions {
  url: string
  header?: Record<string, any>
  token?: string
  tenantEnabled: boolean
  tenantId?: string | number | null
  visitTenantId?: string | number | null
  apiBaseUrls?: readonly string[]
}

/** 不依赖浏览器 URL API，兼容小程序运行时。 */
function getRequestPath(url: string): string {
  return url.split(/[?#]/, 1)[0].replace(/^(?:https?:)?\/\/[^/]*/i, '') || '/'
}

export function isTokenFreeRequest(url: string, apiBaseUrls: readonly string[] = []): boolean {
  const path = getRequestPath(url)
  if (tokenFreePaths.has(path)) {
    return true
  }

  // 仅移除已配置的 API 基路径；不能用 endsWith 匹配任意前缀。
  return apiBaseUrls.some((baseUrl) => {
    const basePath = getRequestPath(baseUrl).replace(/\/+$/, '')
    return basePath !== ''
      && path.startsWith(`${basePath}/`)
      && tokenFreePaths.has(path.slice(basePath.length))
  })
}

function removeHeader(header: Record<string, any>, name: string) {
  for (const key of Object.keys(header)) {
    if (key.toLowerCase() === name) {
      delete header[key]
    }
  }
}

/** 统一计算认证和租户请求头；不修改调用方传入的 header。 */
export function buildRequestHeaders(options: AuthHeaderOptions): Record<string, any> {
  const header = { ...options.header }
  const skipToken = header.isToken === false || isTokenFreeRequest(options.url, options.apiBaseUrls)
  const attachToken = !skipToken && !!options.token

  if (skipToken || attachToken) {
    // HTTP 头大小写不敏感，避免旧 Authorization 或不同大小写的重复头泄漏。
    removeHeader(header, 'authorization')
  }
  if (attachToken) {
    header.Authorization = `Bearer ${options.token}`
  }

  // 访问租户只能随当前有效的会话 token 发送，不能沿用旧请求头。
  removeHeader(header, 'visit-tenant-id')
  if (options.tenantEnabled) {
    if (options.tenantId) {
      removeHeader(header, 'tenant-id')
      header['tenant-id'] = options.tenantId
    }
    if (attachToken && options.visitTenantId) {
      header['visit-tenant-id'] = options.visitTenantId
    }
  }

  // 保留 isToken 控制字段，以便 http.ts 重放请求时仍能遵守显式禁用。
  return header
}
