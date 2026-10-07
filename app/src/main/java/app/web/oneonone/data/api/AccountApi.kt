package app.web.oneonone.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

@Serializable data class Me(val userId: String, val connectionCode: String)
@Serializable data class CodeResult(val connectionCode: String)
@Serializable data class ConnectionRequest(val connectionCode: String)
@Serializable data class TokenBody(val token: String)
@Serializable data class PushTokenBody(val token: String, val platform: String)
@Serializable data class BlockedUser(val blockedUserId: String, val createdAt: String)
@Serializable data class BlocksResult(val blocks: List<BlockedUser>)
@Serializable data class CurrentResult(val connection: CurrentConnection?)
@Serializable data class NicknameBody(val nickname: String)
@Serializable data class LeaveResult(val status: String, val myLeaveStep: Int, val daysRemaining: Int?, val bothLeaving: Boolean, val terminated: Boolean)
@Serializable data class LeaveResponse(val leave: LeaveResult)
@Serializable data class WallpaperBody(val wallpaper: String)
@Serializable data class ReportBody(val category: String, val reason: String)
@Serializable data class CurrentConnection(
    val id: String,
    val status: String,
    val myUserId: String,
    val isRequester: Boolean,
    val otherNickname: String?,
    val otherConnectionCode: String,
    val myLeaveStep: Int,
    val otherLeaveStep: Int,
    val daysRemaining: Int?,
    val bothLeaving: Boolean,
    val canAdvanceLeave: Boolean,
    val otherLastReadAt: String?,
    val otherLastDeliveredAt: String?,
    val wallpaper: String,
    val messageStyle: String,
)

interface AccountApi {
    @GET("api/me") suspend fun me(): Me
    @POST("api/me/connection-code/regenerate") suspend fun regenerate(): CodeResult
    @DELETE("api/me") suspend fun deleteAccount()
    @GET("api/me/blocks") suspend fun blocks(): BlocksResult
    @DELETE("api/me/blocks/{id}") suspend fun unblock(@Path("id") id: String)
    @GET("api/connections/current") suspend fun current(): CurrentResult
    @POST("api/connections/request") suspend fun request(@Body body: ConnectionRequest): JsonObject
    @POST("api/connections/{id}/accept") suspend fun accept(@Path("id") id: String): JsonObject
    @POST("api/connections/{id}/decline") suspend fun decline(@Path("id") id: String): JsonObject
    @POST("api/connections/{id}/cancel") suspend fun cancel(@Path("id") id: String): JsonObject
    @POST("api/push/token/unregister") suspend fun unregister(@Body body: TokenBody)
    @POST("api/push/token") suspend fun register(@Body body: PushTokenBody)
    @PATCH("api/connections/{id}/nickname") suspend fun nickname(@Path("id") id: String, @Body body: NicknameBody)
    @POST("api/connections/{id}/leave") suspend fun leave(@Path("id") id: String): LeaveResponse
    @POST("api/connections/{id}/leave/cancel") suspend fun cancelLeave(@Path("id") id: String): LeaveResponse
    @POST("api/connections/{id}/leave/confirm-end") suspend fun confirmEnd(@Path("id") id: String): LeaveResponse
    @PATCH("api/connections/{id}/wallpaper") suspend fun wallpaper(@Path("id") id: String, @Body body: WallpaperBody)
    @POST("api/connections/{id}/block") suspend fun block(@Path("id") id: String)
    @POST("api/connections/{id}/report") suspend fun reportConnection(@Path("id") id: String, @Body body: ReportBody)
    @POST("api/messages/{id}/report") suspend fun reportMessage(@Path("id") id: String, @Body body: ReportBody)
}
