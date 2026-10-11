/* eslint-disable brace-style */ // 原因：unibest 官方维护的代码，尽量不要大概，避免难以合并
import type { CustomRequestOptions } from '@/http/types'
import { useTokenStore, useUserStore } from '@/store'
import { getEnvBaseUrl } from '@/utils'
import { ApiEncrypt } from '@/utils/encrypt'
import { buildRequestHeaders } from './authHeaders'
import { stringifyQuery } from './tools/queryString'

// 请求基准地址
const baseUrl = getEnvBaseUrl()
const tenantEnable = import.meta.env.VITE_APP_TENANT_ENABLE

// 拦截器配置
const httpInterceptor = {
  // 拦截前触发
  invoke(options: CustomRequestOptions) {
    // 接口请求支持通过 query 参数配置 queryString
    if (options.query) {
      const queryStr = stringifyQuery(options.query)
      if (options.url.includes('?')) {
        options.url += `&${queryStr}`
      }
      else {
        options.url += `?${queryStr}`
      }
    }
    // 非 http 开头需拼接地址
    if (!options.url.startsWith('http')) {
      // #ifdef H5
      if (JSON.parse(import.meta.env.VITE_APP_PROXY_ENABLE)) {
        // 自动拼接代理前缀
        options.url = import.meta.env.VITE_APP_PROXY_PREFIX + options.url
      }
      else {
        options.url = baseUrl + options.url
      }
      // #endif
      // 非H5正常拼接
      // #ifndef H5
      options.url = baseUrl + options.url
      // #endif
      // TIPS: 如果需要对接多个后端服务，也可以在这里处理，拼接成所需要的地址
    }
    // 1. 请求超时
    options.timeout = 60000 // 60s
    // 2. 统一处理 token 和租户头，白名单及显式禁用不携带会话凭据。
    const tokenStore = useTokenStore()
    const token = tokenStore.updateNowTime().validToken
    const tenantEnabled = tenantEnable === 'true'
    const userStore = tenantEnabled ? useUserStore() : undefined
    options.header = buildRequestHeaders({
      url: options.url,
      header: options.header,
      token,
      tenantEnabled,
      tenantId: userStore?.tenantId,
      visitTenantId: userStore?.visitTenantId,
      apiBaseUrls: [baseUrl, import.meta.env.VITE_APP_PROXY_PREFIX].filter(Boolean),
    })

    // 3. add by panda：是否 API 加密
    if (options.isEncrypt) {
      try {
        // 加密请求数据
        if (options.data) {
          options.data = ApiEncrypt.encryptRequest(options.data)
          // 设置加密标识头
          options.header[ApiEncrypt.getEncryptHeader()] = 'true'
        }
      } catch (error) {
        console.error('请求数据加密失败:', error)
        throw error
      }
    }

    return options
  },
}

export const requestInterceptor = {
  install() {
    // 拦截 request 请求
    uni.addInterceptor('request', httpInterceptor)
    // 拦截 uploadFile 文件上传
    uni.addInterceptor('uploadFile', httpInterceptor)
  },
}
