package pl.restrictor.kartka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.restrictor.kartka.ui.KartkaRoot
import pl.restrictor.kartka.ui.theme.KartkaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as KartkaApp).graph.repository
        setContent {
            KartkaTheme {
                KartkaRoot(repository)
            }
        }
    }
}
