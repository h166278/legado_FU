package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookChapter

@Dao
interface BookChapterDao {

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and title like '%'||:key||'%' order by `index`")
    fun search(bookUrl: String, key: String): List<BookChapter>

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and `index` >= :start and `index` <= :end and title like '%'||:key||'%' order by `index`")
    fun search(bookUrl: String, key: String, start: Int, end: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl order by `index`")
    fun getChapterList(bookUrl: String): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl and `index` >= :start and `index` <= :end order by `index`")
    fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl order by `index` limit :limit offset :offset")
    fun getChapterPage(bookUrl: String, offset: Int, limit: Int): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl order by `index` desc limit :limit offset :offset")
    fun getChapterPageDescending(bookUrl: String, offset: Int, limit: Int): List<BookChapter>

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and title like '%'||:key||'%' order by `index` limit :limit offset :offset")
    fun searchPage(bookUrl: String, key: String, offset: Int, limit: Int): List<BookChapter>

    @Query("SELECT * FROM chapters where bookUrl = :bookUrl and title like '%'||:key||'%' order by `index` desc limit :limit offset :offset")
    fun searchPageDescending(
        bookUrl: String,
        key: String,
        offset: Int,
        limit: Int,
    ): List<BookChapter>

    @Query("select * from chapters where bookUrl = :bookUrl and `index` = :index")
    fun getChapter(bookUrl: String, index: Int): BookChapter?

    @Query("select * from chapters where bookUrl = :bookUrl and `title` = :title")
    fun getChapter(bookUrl: String, title: String): BookChapter?

    @Query("select count(url) from chapters where bookUrl = :bookUrl")
    fun getChapterCount(bookUrl: String): Int

    @Query("select count(url) from chapters where bookUrl = :bookUrl and title like '%'||:key||'%'")
    fun getChapterCount(bookUrl: String, key: String): Int

    @Query("select count(url) from chapters where bookUrl = :bookUrl and `index` < :chapterIndex")
    fun getChapterPosition(bookUrl: String, chapterIndex: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookChapter: BookChapter)

    @Update
    fun update(vararg bookChapter: BookChapter)

    @Query("delete from chapters where bookUrl = :bookUrl")
    fun delByBook(bookUrl: String)

    @Query("update chapters set wordCount = :wordCount where bookUrl = :bookUrl and url = :url")
    fun upWordCount(bookUrl: String, url: String, wordCount: String)

}
