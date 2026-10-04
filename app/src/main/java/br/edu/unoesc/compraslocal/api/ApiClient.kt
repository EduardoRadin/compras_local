package br.edu.unoesc.compraslocal.api

import android.content.Context
import br.edu.unoesc.compraslocal.data.AppDatabase
import br.edu.unoesc.compraslocal.data.entity.AuthStateEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val DEFAULT_BASE_URL = "http://192.168.68.102:3000/"
    const val PHYSICAL_DEVICE_URL = "http://192.168.1.100:3000/"

    private var baseUrl: String = DEFAULT_BASE_URL

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private lateinit var appContext: Context
    private lateinit var db: AppDatabase

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    @Volatile
    private var cachedAuth: AuthStateEntity? = null

    private val authLock = Any()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.get(appContext)
        runBlocking { loadAuth() }
    }

    fun getBaseUrl(): String = baseUrl

    fun setBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Accept", "application/json")
        val token = runBlocking {
            loadAuth()
            cachedAuth?.accessToken
        }
        if (token != null && original.header("No-Auth") == null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val refreshInterceptor = Interceptor { chain ->
        val original = chain.request()
        if (original.header("No-Refresh") != null || original.header("No-Auth") != null) {
            return@Interceptor chain.proceed(original)
        }
        val res = chain.proceed(original)
        if (res.code == 401) {
            res.close()
            val refreshed = synchronized(authLock) {
                runBlocking { doRefresh() }
            }
            if (refreshed) {
                val token = cachedAuth?.accessToken
                    ?: return@Interceptor res
                val retry = original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                return@Interceptor chain.proceed(retry)
            }
            return@Interceptor res
        }
        res
    }

    private suspend fun doRefresh(): Boolean {
        val current = loadAuth() ?: return false
        val refresh = current.refreshToken ?: return false
        return try {
            val temp = createUnauthService()
            val response = temp.refreshToken(TokenRefreshRequest(refresh))
            if (response.isSuccessful) {
                val body = response.body() ?: return false
                val updated = current.copy(
                    accessToken = body.token,
                    refreshToken = body.refreshToken,
                )
                saveAuthInternal(updated)
                true
            } else {
                clearAuthInternal()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .addInterceptor(refreshInterceptor)
            .build()
    }

    private val unauthClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val apiService: MeuMercadoApi by lazy {
        buildRetrofit(okHttpClient).create(MeuMercadoApi::class.java)
    }

    fun createUnauthService(): MeuMercadoApi {
        return buildRetrofit(unauthClient).create(MeuMercadoApi::class.java)
    }

    fun getService(): MeuMercadoApi = apiService

    suspend fun loadAuth(): AuthStateEntity? {
        if (cachedAuth == null) {
            try {
                cachedAuth = db.authDao().get()
            } catch (_: Exception) {
                cachedAuth = null
            }
        }
        _isLoggedIn.value = cachedAuth?.accessToken != null
        return cachedAuth
    }

    suspend fun saveAuth(auth: AuthStateEntity) {
        saveAuthInternal(auth)
    }

    private suspend fun saveAuthInternal(auth: AuthStateEntity) {
        db.authDao().upsert(auth)
        cachedAuth = auth
        _isLoggedIn.value = auth.accessToken != null
    }

    suspend fun clearAuth() {
        clearAuthInternal()
    }

    private suspend fun clearAuthInternal() {
        try {
            db.authDao().clear()
        } catch (_: Exception) {
        }
        cachedAuth = null
        _isLoggedIn.value = false
    }

    suspend fun login(username: String, password: String): AuthResponse {
        val res = createUnauthService().login(LoginRequest(username, password))
        return parseResponse(res)
    }

    suspend fun register(
        username: String,
        password: String,
        name: String,
        email: String,
    ): AuthResponse {
        val res = createUnauthService().register(
            RegisterRequest(
                username = username,
                password = password,
                name = name,
                email = email,
            ),
        )
        return parseResponse(res)
    }

    suspend fun logout() {
        clearAuth()
    }

    fun <T> parseResponse(response: Response<T>): T {
        if (response.isSuccessful) {
            return response.body() ?: throw ApiException("Resposta vazia", response.code())
        }
        val errorBody = response.errorBody()?.string().orEmpty()
        val error = try {
            moshi.adapter(ApiErrorResponse::class.java).fromJson(errorBody)
        } catch (_: Exception) {
            null
        }
        throw ApiException(
            message = error?.message ?: "Erro ${response.code()}",
            statusCode = response.code(),
            code = error?.code,
        )
    }
}
