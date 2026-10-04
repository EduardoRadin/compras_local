package br.edu.unoesc.compraslocal

import android.app.Application
import br.edu.unoesc.compraslocal.api.ApiClient
import br.edu.unoesc.compraslocal.data.AppDatabase
import br.edu.unoesc.compraslocal.data.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ComprasApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ApiClient.initialize(this)
        scope.launch {
            DatabaseSeeder.seedIfNeeded(AppDatabase.get(this@ComprasApp))
        }
    }
}
