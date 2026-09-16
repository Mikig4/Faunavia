package it.faunavia.app

import android.app.Application
import it.faunavia.domain.AppClock
import it.faunavia.local.LocalRepositories

class FaunaviaApplication : Application() {
    val repositories by lazy {
        LocalRepositories.open(this, object : AppClock {
            override fun nowEpochMillis(): Long = System.currentTimeMillis()
        })
    }
}
