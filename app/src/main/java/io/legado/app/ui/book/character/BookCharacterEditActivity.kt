package io.legado.app.ui.book.character

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.databinding.ActivityBookCharacterEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class BookCharacterEditActivity : BaseActivity<ActivityBookCharacterEditBinding>() {

    override val binding by viewBinding(ActivityBookCharacterEditBinding::inflate)
    private lateinit var workKey: String
    private var characterId: Long = 0L
    private var character: BookCharacter? = null
    private var castRoleId: Long = 0L
    private var castRole: BookTtsCastRole? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY).orEmpty()
        characterId = intent.getLongExtra(BookCharacterActivity.EXTRA_CHARACTER_ID, 0L)
        castRoleId = intent.getLongExtra(BookCharacterActivity.EXTRA_CAST_ROLE_ID, 0L)
        character = appDb.bookCharacterDao.getCharacter(characterId)
        castRole = appDb.bookCharacterDao.getTtsCastRole(castRoleId)
        binding.titleBar.title = getString(
            if (characterId > 0L) R.string.edit_character else R.string.add_character
        )
        binding.form.initCharacterForm(this)
        binding.form.bindCharacterForm(character, castRole)
        binding.tvSave.setOnClickListener { saveCharacter() }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        if (characterId > 0L) {
            menuInflater.inflate(R.menu.book_character_edit, menu)
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_delete) {
            deleteCharacter()
            return true
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun saveCharacter() {
        val value = binding.form.readCharacterForm()
        if (value.name.isEmpty()) {
            toastOnUi(R.string.character_name_empty)
            return
        }
        val savedCharacterId = BookCharacterEditor.save(workKey, character, castRole, value)
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BookCharacterActivity.EXTRA_CHARACTER_ID, savedCharacterId)
                .putExtra(BookCharacterActivity.EXTRA_CAST_ROLE_ID, castRoleId)
        )
        finish()
    }

    private fun deleteCharacter() {
        val item = character ?: return
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton {
                appDb.bookCharacterDao.deleteCharacterWithTts(item)
                setResult(RESULT_OK)
                finish()
            }
            noButton()
        }
    }

}
