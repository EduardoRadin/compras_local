package br.edu.unoesc.compraslocal.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MeuMercadoApi {

    @GET("health")
    suspend fun health(): Response<HealthResponse>

    @POST("register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>

    @POST("login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @GET("me")
    suspend fun me(): Response<MeResponse>

    @POST("token/refresh")
    suspend fun refreshToken(@Body req: TokenRefreshRequest): Response<TokenRefreshResponse>

    @GET("token")
    suspend fun validateToken(): Response<TokenValidResponse>

    @GET("products")
    suspend fun listProducts(): Response<List<ProductDto>>

    @POST("products")
    suspend fun createProduct(@Body req: CreateProductRequest): Response<IdResponse>

    @GET("markets")
    suspend fun listMarkets(): Response<List<MarketDto>>

    @POST("markets")
    suspend fun createMarket(@Body req: CreateMarketRequest): Response<MarketDto>

    @GET("cart/items")
    suspend fun listCartItems(): Response<List<CartItemDto>>

    @POST("cart/items")
    suspend fun addCartItem(@Body req: AddCartItemRequest): Response<IdResponse>

    @PATCH("cart/items/{id}")
    suspend fun patchCartItem(
        @Path("id") id: String,
        @Body req: PatchCartItemRequest,
    ): Response<PatchCartItemResponse>

    @DELETE("cart/items/{id}")
    suspend fun deleteCartItem(@Path("id") id: String): Response<IdResponse>

    @GET("history")
    suspend fun getHistory(): Response<List<HistoryEntryDto>>

    @GET("dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStatsDto>

    @GET("dashboard/summary")
    suspend fun getDashboardSummary(): Response<List<DashboardSummaryRow>>
}
