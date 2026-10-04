package br.edu.unoesc.compraslocal.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val timestamp: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val username: String,
    val name: String,
    val email: String,
    val timezone: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val password: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "token") val token: String,
    @Json(name = "refreshToken") val refreshToken: String,
    val user: UserDto,
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    val user: UserDto,
)

@JsonClass(generateAdapter = true)
data class TokenRefreshRequest(
    val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class TokenRefreshResponse(
    @Json(name = "token") val token: String,
    @Json(name = "refreshToken") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class TokenValidResponse(
    val user: UserDto,
    val expiresAt: String,
)

@JsonClass(generateAdapter = true)
data class LatestPriceDto(
    val id: String,
    val amount: Double,
    val marketId: String,
    val market: String,
    val observedAt: String,
)

@JsonClass(generateAdapter = true)
data class ProductDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val unit: String? = null,
    val imageUrl: String? = null,
    val latestPrice: LatestPriceDto? = null,
)

@JsonClass(generateAdapter = true)
data class CreateProductRequest(
    val name: String,
    val category: String,
    val unit: String,
    val brand: String? = null,
    val imageUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class IdResponse(
    val id: String,
)

@JsonClass(generateAdapter = true)
data class MarketDto(
    val id: String,
    val name: String,
    val addressLine: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateMarketRequest(
    val name: String,
)

@JsonClass(generateAdapter = true)
data class CartItemDto(
    val id: String,
    val productId: String,
    val name: String,
    val imageUrl: String? = null,
    val unit: String? = null,
    val quantity: Double,
    val status: String,
    val purchasedAt: String? = null,
    val unitPrice: Double? = null,
    val totalAmount: Double? = null,
    val marketName: String? = null,
    val suggestedMarketId: String? = null,
    val suggestedUnitPrice: Double? = null,
)

@JsonClass(generateAdapter = true)
data class AddCartItemRequest(
    val productId: String,
    val priceRecordId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PatchCartItemRequest(
    val purchased: Boolean,
    val marketId: String? = null,
    val quantity: Double? = null,
    val unitPrice: Double? = null,
)

@JsonClass(generateAdapter = true)
data class PatchCartItemResponse(
    val id: String,
    val status: String,
    val purchasedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class HistoryPriceDto(
    val amount: Double,
    val market: String,
    val observedAt: String,
)

@JsonClass(generateAdapter = true)
data class HistoryEntryDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val unit: String? = null,
    val imageUrl: String? = null,
    val price: HistoryPriceDto,
)

@JsonClass(generateAdapter = true)
data class DashboardStatsDto(
    val marketsCompared: Int,
    val pricesRegistered: Int,
    val savings: Double,
    val trackedProducts: Int,
)

@JsonClass(generateAdapter = true)
data class DashboardSummaryRow(
    val productId: String,
    val name: String,
    val imageUrl: String? = null,
    val totalQuantity: Double,
    val totalAmount: Double,
)

@JsonClass(generateAdapter = true)
data class ApiErrorResponse(
    val statusCode: Int,
    val code: String? = null,
    val message: String,
)

class ApiException(
    override val message: String,
    val statusCode: Int = 0,
    val code: String? = null,
    override val cause: Throwable? = null,
) : Throwable(message)
