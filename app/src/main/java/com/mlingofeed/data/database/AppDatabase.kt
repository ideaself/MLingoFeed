package com.mlingofeed.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Bookmark::class,
        History::class,
        RssFolder::class,
        RssSubscription::class,
        RssArticle::class,
        RssArticleFts::class,
        RssTag::class,
        RssArticleTag::class,
        RssRule::class,
        WordBookEntry::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun rssDao(): RssDao
    abstract fun wordBookDao(): WordBookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds the FTS index (v9) — matches the FTS table and sync triggers Room creates for a
         * fresh install (see the generated AppDatabase_Impl).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `rss_articles_fts` USING FTS4(`title` TEXT NOT NULL, `description` TEXT NOT NULL, `content` TEXT NOT NULL, content=`rss_articles`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_rss_articles_fts_BEFORE_UPDATE BEFORE UPDATE ON `rss_articles` BEGIN DELETE FROM `rss_articles_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_rss_articles_fts_BEFORE_DELETE BEFORE DELETE ON `rss_articles` BEGIN DELETE FROM `rss_articles_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_rss_articles_fts_AFTER_UPDATE AFTER UPDATE ON `rss_articles` BEGIN INSERT INTO `rss_articles_fts`(`docid`, `title`, `description`, `content`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`description`, NEW.`content`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_rss_articles_fts_AFTER_INSERT AFTER INSERT ON `rss_articles` BEGIN INSERT INTO `rss_articles_fts`(`docid`, `title`, `description`, `content`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`description`, NEW.`content`); END")
                db.execSQL("INSERT INTO rss_articles_fts(docid, title, description, content) SELECT rowid, title, description, content FROM rss_articles")
            }
        }

        /**
         * Adds word mnemonics and article "read later" flags. Column definitions match the
         * generated schema for a fresh install (verified against sqlite pragma output).
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `word_book` ADD COLUMN `mnemonic` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `rss_articles` ADD COLUMN `isSaved` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webreader_database"
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
