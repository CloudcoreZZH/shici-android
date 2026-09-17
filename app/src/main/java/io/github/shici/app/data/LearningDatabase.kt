package io.github.shici.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LearningDatabase(context: Context) : SQLiteOpenHelper(context, "learning.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL CHECK(length(name)>0))")
        db.execSQL("INSERT INTO books(id,name) VALUES(1,'考研生词本')")
        db.execSQL("""CREATE TABLE additions(
            id TEXT PRIMARY KEY,book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
            word TEXT NOT NULL,added_at INTEGER NOT NULL,completed_at INTEGER)""")
        db.execSQL("CREATE INDEX additions_book_word ON additions(book_id,word)")
        db.execSQL("CREATE INDEX additions_pending ON additions(book_id,completed_at,added_at)")
        db.execSQL("""CREATE TABLE memory(
            book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,word TEXT NOT NULL,
            stability REAL NOT NULL CHECK(stability>=0.001),difficulty REAL NOT NULL CHECK(difficulty>=1 AND difficulty<=10),
            reviewed_at INTEGER NOT NULL,due_at INTEGER NOT NULL,phase TEXT NOT NULL,step INTEGER NOT NULL,
            repetitions INTEGER NOT NULL,lapses INTEGER NOT NULL,PRIMARY KEY(book_id,word))""")
        db.execSQL("""CREATE TABLE reviews(
            action_id TEXT PRIMARY KEY,book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
            word TEXT NOT NULL,rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 4),
            reviewed_at INTEGER NOT NULL,mode TEXT NOT NULL,task_id TEXT)""")
        db.execSQL("CREATE INDEX reviews_date ON reviews(book_id,reviewed_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Deliberately fail rather than erase learning history on an unsupported schema.
        error("不支持的数据版本 $oldVersion → $newVersion，请保留数据并联系开发者。")
    }
}
