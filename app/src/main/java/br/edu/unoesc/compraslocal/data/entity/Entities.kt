package br.edu.unoesc.compraslocal.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cnpj: String? = null,
    val remoteId: String? = null,
    val dirty: Boolean = true,
    val lastSyncAt: Long? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keywords: String,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("normalizedName"), Index("remoteId")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val categoryId: Long? = null,
    val barcode: String? = null,
    val brand: String? = null,
    val unit: String? = null,
    val imageUrl: String? = null,
    val remoteId: String? = null,
    val dirty: Boolean = true,
    val lastSyncAt: Long? = null,
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("storeId"), Index("purchasedAt"), Index("nfeKey")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long,
    val purchasedAt: Long,
    val nfeKey: String? = null,
    val totalAmount: Double,
    val synced: Boolean = false,
    val lastSyncAttemptAt: Long? = null,
    val syncError: String? = null,
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("purchaseId"), Index("productId"), Index("remoteCartItemId")],
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long?,
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val remoteCartItemId: String? = null,
    val synced: Boolean = false,
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("listId"), Index("productId")],
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val productId: Long? = null,
    val description: String,
    val quantity: Double = 1.0,
    val isChecked: Boolean = false,
    val matchedPurchaseItemId: Long? = null,
)

@Entity(tableName = "auth_state")
data class AuthStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    val expiresAt: Long? = null,
    val lastSyncAt: Long? = null,
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val direction: String,
    val status: String,
    val pushedStores: Int = 0,
    val pushedProducts: Int = 0,
    val pushedPurchases: Int = 0,
    val pulledMarkets: Int = 0,
    val pulledProducts: Int = 0,
    val pulledHistory: Int = 0,
    val errorMessage: String? = null,
)
