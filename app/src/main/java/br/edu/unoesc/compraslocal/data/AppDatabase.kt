package br.edu.unoesc.compraslocal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import br.edu.unoesc.compraslocal.data.dao.AuthDao
import br.edu.unoesc.compraslocal.data.dao.CategoryDao
import br.edu.unoesc.compraslocal.data.dao.ExportDao
import br.edu.unoesc.compraslocal.data.dao.ProductDao
import br.edu.unoesc.compraslocal.data.dao.PurchaseDao
import br.edu.unoesc.compraslocal.data.dao.PurchaseItemDao
import br.edu.unoesc.compraslocal.data.dao.ShoppingListDao
import br.edu.unoesc.compraslocal.data.dao.StoreDao
import br.edu.unoesc.compraslocal.data.dao.SyncDao
import br.edu.unoesc.compraslocal.data.entity.AuthStateEntity
import br.edu.unoesc.compraslocal.data.entity.CategoryEntity
import br.edu.unoesc.compraslocal.data.entity.ProductEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseItemEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListItemEntity
import br.edu.unoesc.compraslocal.data.entity.StoreEntity
import br.edu.unoesc.compraslocal.data.entity.SyncLogEntity

@Database(
    entities = [
        StoreEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        AuthStateEntity::class,
        SyncLogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseItemDao(): PurchaseItemDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun exportDao(): ExportDao
    abstract fun authDao(): AuthDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "compras_local.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
