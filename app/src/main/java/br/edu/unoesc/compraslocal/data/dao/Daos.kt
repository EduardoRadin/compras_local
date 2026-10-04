package br.edu.unoesc.compraslocal.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import br.edu.unoesc.compraslocal.data.entity.AuthStateEntity
import br.edu.unoesc.compraslocal.data.entity.CategoryEntity
import br.edu.unoesc.compraslocal.data.entity.ProductEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseItemEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListItemEntity
import br.edu.unoesc.compraslocal.data.entity.StoreEntity
import br.edu.unoesc.compraslocal.data.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

data class PurchaseWithStore(
    val id: Long,
    val storeName: String,
    val purchasedAt: Long,
    val totalAmount: Double,
    val itemCount: Int,
    val synced: Boolean,
    val syncError: String?,
)

data class ProductPricePoint(
    val purchasedAt: Long,
    val unitPrice: Double,
    val storeName: String,
)

data class StoreComparisonRow(
    val storeName: String,
    val avgUnitPrice: Double,
    val lastPurchasedAt: Long,
)

data class FrequentProductRow(
    val productId: Long,
    val productName: String,
    val purchaseCount: Int,
    val avgDaysBetween: Double,
    val lastPurchasedAt: Long,
)

data class ProductPickerRow(
    val productId: Long,
    val productName: String,
    val purchaseCount: Int,
    val lastPurchasedAt: Long,
    val lastUnitPrice: Double,
)

data class PurchaseItemRow(
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
)

data class LastPriceRow(
    val description: String,
    val unitPrice: Double,
)

@Dao
interface StoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(store: StoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stores: List<StoreEntity>)

    @Update
    suspend fun update(store: StoreEntity)

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): StoreEntity?

    @Query("SELECT * FROM stores WHERE cnpj = :cnpj LIMIT 1")
    suspend fun findByCnpj(cnpj: String): StoreEntity?

    @Query("SELECT * FROM stores WHERE name LIKE :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): StoreEntity?

    @Query("SELECT * FROM stores WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): StoreEntity?

    @Query("SELECT * FROM stores WHERE dirty = 1")
    suspend fun findDirty(): List<StoreEntity>

    @Query("SELECT * FROM stores ORDER BY name")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores ORDER BY name")
    suspend fun getAll(): List<StoreEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name LIKE :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?
}

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products WHERE normalizedName = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE dirty = 1")
    suspend fun findDirty(): List<ProductEntity>

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>
}

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Update
    suspend fun update(purchase: PurchaseEntity)

    @Query(
        """
        SELECT p.id, s.name AS storeName, p.purchasedAt, p.totalAmount,
               (SELECT COUNT(*) FROM purchase_items pi WHERE pi.purchaseId = p.id) AS itemCount,
               p.synced AS synced, p.syncError AS syncError
        FROM purchases p
        INNER JOIN stores s ON s.id = p.storeId
        ORDER BY p.purchasedAt DESC
        """,
    )
    fun observePurchases(): Flow<List<PurchaseWithStore>>

    @Query("SELECT * FROM purchases WHERE nfeKey = :key LIMIT 1")
    suspend fun findByNfeKey(key: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun findById(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE synced = 0 ORDER BY purchasedAt ASC")
    suspend fun findNotSynced(): List<PurchaseEntity>

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM purchases WHERE storeId IN (SELECT id FROM stores WHERE name = 'Mercado Demo')")
    suspend fun deleteDemoPurchases(): Int
}

@Dao
interface PurchaseItemDao {
    @Insert
    suspend fun insertAll(items: List<PurchaseItemEntity>)

    @Update
    suspend fun update(item: PurchaseItemEntity)

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id ASC")
    suspend fun findByPurchaseId(purchaseId: Long): List<PurchaseItemEntity>

    @Query(
        """
        SELECT p.purchasedAt, i.unitPrice, s.name AS storeName
        FROM purchase_items i
        INNER JOIN purchases p ON p.id = i.purchaseId
        INNER JOIN stores s ON s.id = p.storeId
        WHERE i.productId = :productId
        ORDER BY p.purchasedAt ASC
        """,
    )
    suspend fun priceHistory(productId: Long): List<ProductPricePoint>

    @Query(
        """
        SELECT s.name AS storeName, AVG(i.unitPrice) AS avgUnitPrice, MAX(p.purchasedAt) AS lastPurchasedAt
        FROM purchase_items i
        INNER JOIN purchases p ON p.id = i.purchaseId
        INNER JOIN stores s ON s.id = p.storeId
        WHERE i.productId = :productId
        GROUP BY s.id, s.name
        ORDER BY avgUnitPrice ASC
        """,
    )
    suspend fun compareStores(productId: Long): List<StoreComparisonRow>

    @Query(
        """
        SELECT pr.id AS productId, pr.name AS productName,
               COUNT(DISTINCT p.id) AS purchaseCount,
               0.0 AS avgDaysBetween,
               MAX(p.purchasedAt) AS lastPurchasedAt
        FROM products pr
        INNER JOIN purchase_items i ON i.productId = pr.id
        INNER JOIN purchases p ON p.id = i.purchaseId
        GROUP BY pr.id
        HAVING purchaseCount >= 2
        ORDER BY purchaseCount DESC, lastPurchasedAt ASC
        LIMIT 20
        """,
    )
    suspend fun frequentProducts(): List<FrequentProductRow>

    @Query(
        """
        SELECT i.description AS description, i.quantity, i.unitPrice, i.totalPrice
        FROM purchase_items i
        WHERE i.purchaseId = :purchaseId
        ORDER BY i.id ASC
        """,
    )
    suspend fun itemsForPurchase(purchaseId: Long): List<PurchaseItemRow>

    @Query(
        """
        SELECT pr.id AS productId, pr.name AS productName,
               COUNT(DISTINCT p.id) AS purchaseCount,
               MAX(p.purchasedAt) AS lastPurchasedAt,
               (SELECT i2.unitPrice FROM purchase_items i2
                INNER JOIN purchases p2 ON p2.id = i2.purchaseId
                WHERE i2.productId = pr.id
                ORDER BY p2.purchasedAt DESC LIMIT 1) AS lastUnitPrice
        FROM products pr
        INNER JOIN purchase_items i ON i.productId = pr.id
        INNER JOIN purchases p ON p.id = i.purchaseId
        GROUP BY pr.id
        ORDER BY lastPurchasedAt DESC
        LIMIT 80
        """,
    )
    suspend fun productsForAnalytics(): List<ProductPickerRow>

    @Query(
        """
        SELECT i.description AS description, i.unitPrice AS unitPrice
        FROM purchase_items i
        INNER JOIN purchases p ON p.id = i.purchaseId
        ORDER BY p.purchasedAt DESC
        LIMIT 500
        """,
    )
    suspend fun recentItemPrices(): List<LastPriceRow>
}

@Dao
interface ShoppingListDao {
    @Insert
    suspend fun insertList(list: ShoppingListEntity): Long

    @Update
    suspend fun updateList(list: ShoppingListEntity)

    @Query("SELECT * FROM shopping_lists WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActiveLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getList(id: Long): ShoppingListEntity?

    @Insert
    suspend fun insertItem(item: ShoppingListItemEntity): Long

    @Update
    suspend fun updateItem(item: ShoppingListItemEntity)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM shopping_list_items WHERE listId = :listId AND isChecked = 1")
    suspend fun deleteCheckedItems(listId: Long)

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId ORDER BY isChecked ASC, id ASC")
    fun observeItems(listId: Long): Flow<List<ShoppingListItemEntity>>
}

@Dao
interface ExportDao {
    @Query(
        """
        SELECT p.purchasedAt AS purchasedAt,
               s.name AS storeName,
               pr.name AS productName,
               c.name AS categoryName,
               i.quantity AS quantity,
               i.unitPrice AS unitPrice,
               i.totalPrice AS totalPrice,
               p.nfeKey AS nfeKey
        FROM purchase_items i
        INNER JOIN purchases p ON p.id = i.purchaseId
        INNER JOIN stores s ON s.id = p.storeId
        LEFT JOIN products pr ON pr.id = i.productId
        LEFT JOIN categories c ON c.id = pr.categoryId
        ORDER BY p.purchasedAt DESC
        """,
    )
    suspend fun exportRows(): List<ExportRow>
}

data class ExportRow(
    val purchasedAt: Long,
    val storeName: String,
    val productName: String?,
    val categoryName: String?,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val nfeKey: String?,
)

@Dao
interface AuthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(auth: AuthStateEntity): Long

    @Query("SELECT * FROM auth_state LIMIT 1")
    suspend fun get(): AuthStateEntity?

    @Query("DELETE FROM auth_state")
    suspend fun clear()
}

@Dao
interface SyncDao {
    @Insert
    suspend fun insertLog(log: SyncLogEntity): Long

    @Update
    suspend fun updateLog(log: SyncLogEntity)

    @Query("SELECT * FROM sync_logs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 20): List<SyncLogEntity>
}
