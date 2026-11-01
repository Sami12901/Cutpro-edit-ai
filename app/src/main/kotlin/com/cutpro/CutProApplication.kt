package com.cutpro

import android.app.Application
import com.cutpro.core.data.database.CutproDatabase

class CutProApplication : Application() {
    val database: CutproDatabase by lazy {
        CutproDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
