package br.edu.unoesc.compraslocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import br.edu.unoesc.compraslocal.ui.screens.ComprasAppShell
import br.edu.unoesc.compraslocal.ui.theme.ComprasLocalTheme
import br.edu.unoesc.compraslocal.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComprasLocalTheme {
                val vm: MainViewModel = viewModel()
                ComprasAppShell(vm)
            }
        }
    }
}
