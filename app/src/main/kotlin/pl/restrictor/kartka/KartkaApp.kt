package pl.restrictor.kartka

import android.app.Application
import android.content.Context
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.db.AppDatabase

class KartkaApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(context: Context) {
    val repository: DeckRepository = DeckRepository(AppDatabase.create(context))
}
