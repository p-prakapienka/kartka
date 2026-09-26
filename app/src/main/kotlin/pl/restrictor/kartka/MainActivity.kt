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
        val graph = (application as KartkaApp).graph
        setContent {
            KartkaTheme {
                KartkaRoot(graph.repository, graph.repeatSettings)
            }
        }
    }
}
