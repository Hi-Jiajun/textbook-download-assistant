package com.jiaocai.download.data

/**
 * 平台返回的错误状态码，翻译成用户能看懂的话。
 *
 * 之前这里直接把「请求失败 HTTP 401: https://...」抛给界面，用户看到一长串地址
 * 完全不知道该做什么。401/403 是实际最常遇到的两种：前者是凭据过期（重试无用，
 * 必须重新登录），后者是权限问题。
 */

/** 凭据失效（HTTP 401）：重试无用，必须重新登录。 */
class AuthExpiredException(message: String) : Exception(message)

/** 访问被拒（HTTP 403）：可能是登录态失效，也可能是账号对该资源无权限。 */
class AccessDeniedException(message: String) : Exception(message)

internal fun httpFailure(code: Int, url: String): Exception = when {
    code == 401 -> AuthExpiredException("登录状态已失效，请重新登录后再试。")
    code == 403 -> AccessDeniedException("平台拒绝了这次请求（HTTP 403）：可能是登录状态失效，或该资源需要权限。请重新登录后再试。")
    code == 404 -> IllegalStateException("资源不存在（HTTP 404），可能已下架或链接有误。")
    code in 500..599 -> IllegalStateException("平台服务暂时不可用（HTTP $code），请稍后重试。")
    else -> IllegalStateException("请求失败 HTTP $code：$url")
}
