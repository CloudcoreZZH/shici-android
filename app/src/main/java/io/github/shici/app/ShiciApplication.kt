package io.github.shici.app

import android.app.Application
import io.github.shici.app.data.DictionaryStore
import io.github.shici.app.data.LearningDatabase
import io.github.shici.app.data.LearningRepository

class ShiciApplication : Application() {
    val dictionary by lazy { DictionaryStore(this) }
    val learning by lazy { LearningRepository(LearningDatabase(this)) }
}
