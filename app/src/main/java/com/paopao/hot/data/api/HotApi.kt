package com.paopao.hot.data.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * DailyHotApi 兼容接口。
 * 参考实现: https://github.com/imsyy/DailyHotApi
 * 默认公共实例: https://hot-api.liiiu.cn
 */
interface HotApi {

    @GET("{route}")
    suspend fun getHot(@Path("route") route: String): HotResponse
}

data class HotResponse(
    val code: Int? = null,
    val name: String? = null,
    val title: String? = null,
    val type: String? = null,
    val total: Int? = null,
    val updateTime: String? = null,
    val data: List<HotItemDto> = emptyList()
)

data class HotItemDto(
    val id: Any? = null,
    val title: String? = null,
    val desc: String? = null,
    val author: String? = null,
    val hot: Double? = null,
    val cover: String? = null,
    /** 话题发布时间（毫秒），部分平台不提供 */
    val timestamp: Long? = null,
    val url: String? = null,
    val mobileUrl: String? = null
)
