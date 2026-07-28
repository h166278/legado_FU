package io.legado.app.ui.book.character

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.databinding.ActivityBookCharacterTtsBinding
import io.legado.app.databinding.ItemBookCharacterTtsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.BookTtsBindingPolicy
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsSheetLaunchDebouncer
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoiceSelectionSheet
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.gone
import io.legado.app.utils.getPrefString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

open class BookCharacterTtsActivity : BaseActivity<ActivityBookCharacterTtsBinding>(),
    ItemTouchCallback.Callback,
    BookCharacterEditDialog.Callback {

    override val binding by viewBinding(ActivityBookCharacterTtsBinding::inflate)
    private val adapter by lazy { Adapter() }
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()
    private lateinit var itemTouchCallback: ItemTouchCallback
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var bookUrl: String? = null
    private var page: Page = Page.TEMPORARY
    private var snapshot = Snapshot()
    private var pendingCharacterId: Long? = null
    private var quickDelete = false
    private var reassigning = false

    protected enum class Page {
        FORMAL,
        TEMPORARY,
        DEFAULTS
    }

    protected open fun initialPage(): Page = Page.TEMPORARY

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        bookUrl = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_URL)
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        page = initialPage()
        appDb.bookCharacterDao.getOrCreateProfile(bookName, bookAuthor, bookUrl)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        itemTouchCallback = ItemTouchCallback(this).apply {
            isCanDrag = page == Page.FORMAL
            isCanSwipe = page != Page.DEFAULTS
        }
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        binding.tvTabFormal.setOnClickListener { selectPage(Page.FORMAL) }
        binding.tvTabTemporary.setOnClickListener { selectPage(Page.TEMPORARY) }
        binding.tvTabDefaults.setOnClickListener { selectPage(Page.DEFAULTS) }
        observeData()
        linkPromotedRolesOnce()
        updateTabs()
        renderRouteWarning()
    }

    override fun onResume() {
        super.onResume()
        renderRouteWarning()
    }

    private fun linkPromotedRolesOnce() {
        lifecycleScope.launch(IO) {
            BookTtsCastingCoordinator.linkPromotedRoles(
                workKey,
                appDb.bookCharacterDao.getCharacters(workKey)
            )
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<Boolean>(EventBus.TTS_ROUTE_WARNING) {
            renderRouteWarning()
        }
    }

    private fun renderRouteWarning() {
        val warning = BaseReadAloudService.ttsRouteWarning
            ?.takeIf {
                it.bookUrl == bookUrl && it.engineId == AppConfig.multiRoleTtsEngineId
            }
        binding.layoutRouteWarning.isVisible = warning != null
        if (warning != null) {
            binding.tvRouteWarning.setText(R.string.character_tts_route_fallback)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_character, menu)
        menu.findItem(R.id.menu_tts)?.isVisible = false
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val deletedRoles = ignoredTemporaryRoles()
        menu.findItem(R.id.menu_tts)?.isVisible = false
        menu.findItem(R.id.menu_add)?.isVisible = page == Page.FORMAL
        menu.findItem(R.id.menu_quick_delete)?.apply {
            isVisible = page != Page.DEFAULTS
            isChecked = quickDelete
        }
        menu.findItem(R.id.menu_refresh)?.apply {
            isVisible = page == Page.TEMPORARY && activeTemporaryRoles().isNotEmpty()
            isEnabled = !reassigning
        }
        menu.findItem(R.id.menu_restore_temporary)?.apply {
            isVisible = page == Page.TEMPORARY && deletedRoles.isNotEmpty()
            title = getString(R.string.character_restore_deleted_count, deletedRoles.size)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> showCharacterDialog()
            R.id.menu_quick_delete -> {
                quickDelete = !quickDelete
                item.isChecked = quickDelete
                toastOnUi(
                    if (quickDelete) {
                        R.string.character_quick_delete_enabled
                    } else {
                        R.string.character_quick_delete_disabled
                    }
                )
            }

            R.id.menu_restore_temporary -> showDeletedRoleManager()
            R.id.menu_refresh -> confirmReassign()
            else -> return super.onCompatOptionsItemSelected(item)
        }
        return true
    }

    private fun observeData() {
        lifecycleScope.launch {
            combine(
                appDb.bookCharacterDao.flowCharacters(workKey),
                appDb.bookCharacterDao.flowTtsCastRoles(workKey),
                appDb.bookCharacterDao.flowTtsBindings(workKey)
            ) { characters, castRoles, bindings ->
                Snapshot(characters, castRoles, bindings)
            }.catch {
                toastOnUi(it.localizedMessage)
            }.flowOn(IO).collect { value ->
                snapshot = value.copy(voiceCatalog = snapshot.voiceCatalog)
                renderPage()
            }
        }
        lifecycleScope.launch(IO) {
            val voiceCatalog = VoiceCatalogSnapshot.load()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                snapshot = snapshot.copy(voiceCatalog = voiceCatalog)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun selectPage(newPage: Page) {
        if (page == newPage && adapter.getItems().isNotEmpty()) return
        page = newPage
        if (::itemTouchCallback.isInitialized) {
            itemTouchCallback.isCanDrag = page == Page.FORMAL
            itemTouchCallback.isCanSwipe = page != Page.DEFAULTS
        }
        invalidateOptionsMenu()
        updateTabs()
        renderPage()
        binding.recyclerView.scrollToPosition(0)
    }

    private fun updateTabs() {
        val formalCount = snapshot.characters.count { it.enabled }
        val temporaryCount = snapshot.castRoles.count {
            !it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole()
        }
        binding.tvTabFormal.text = getString(
            R.string.character_page_tab_count,
            getString(R.string.character_page_formal),
            formalCount
        )
        binding.tvTabTemporary.text = getString(
            R.string.character_page_tab_count,
            getString(R.string.character_page_temporary),
            temporaryCount
        )
        binding.tvTabDefaults.text = getString(
            R.string.character_page_tab_count,
            getString(R.string.character_page_defaults),
            DEFAULT_VOICE_COUNT
        )
        binding.tvTabFormal.isSelected = page == Page.FORMAL
        binding.tvTabTemporary.isSelected = page == Page.TEMPORARY
        binding.tvTabDefaults.isSelected = page == Page.DEFAULTS
    }

    private fun renderPage() {
        val rows = buildRows(snapshot.characters, snapshot.castRoles, snapshot.bindings)
        adapter.setItems(rows)
        updateTabs()
        invalidateOptionsMenu()
        val emptyText = when (page) {
            Page.FORMAL -> R.string.book_character_empty
            Page.TEMPORARY -> R.string.character_temporary_empty
            Page.DEFAULTS -> null
        }
        if (rows.isEmpty() && emptyText != null) {
            binding.tvEmpty.setText(emptyText)
            binding.tvEmpty.visible()
        } else {
            binding.tvEmpty.gone()
        }
        val characterId = pendingCharacterId ?: return
        val position = rows.indexOfFirst {
            it is Row.Character && it.character.id == characterId
        }
        if (position >= 0) {
            pendingCharacterId = null
            binding.recyclerView.post {
                binding.recyclerView.smoothScrollToPosition(position)
            }
        }
    }

    private fun buildRows(
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>,
        bindings: List<BookCharacterTtsBinding>
    ): List<Row> {
        val currentEngineId = AppConfig.multiRoleTtsEngineId.orEmpty()
        val bindingMap = bindings.associateBy { Triple(it.targetType, it.targetId, it.engineId) }
        val narratorBinding = bindings
            .filter { it.targetType == BookCharacterTtsBinding.TargetType.NARRATOR }
            .maxByOrNull { it.updatedAt }
        return when (page) {
            Page.FORMAL -> characters.filter { it.enabled }.map { character ->
                Row.Character(
                    character = character,
                    binding = bindingMap[
                        Triple(
                            BookCharacterTtsBinding.TargetType.CHARACTER,
                            character.id,
                            currentEngineId
                        )
                    ]
                )
            }

            Page.TEMPORARY -> castRoles
                .filter { !it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole() }
                .sortedWith(temporaryRoleComparator())
                .map { role ->
                    Row.CastRole(
                        role = role,
                        binding = bindingMap[
                            Triple(
                                BookCharacterTtsBinding.TargetType.CAST_ROLE,
                                role.id,
                                currentEngineId
                            )
                        ]
                    )
                }

            Page.DEFAULTS -> listOf(
                Row.Narrator(narratorBinding),
                Row.DialogueFallback(
                    gender = BookCharacter.Gender.MALE,
                    binding = bindingMap[
                        Triple(
                            BookCharacterTtsBinding.TargetType.DIALOGUE_MALE,
                            0L,
                            currentEngineId
                        )
                    ]
                ),
                Row.DialogueFallback(
                    gender = BookCharacter.Gender.FEMALE,
                    binding = bindingMap[
                        Triple(
                            BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE,
                            0L,
                            currentEngineId
                        )
                    ]
                )
            )
        }
    }

    private fun temporaryRoleComparator(): Comparator<BookTtsCastRole> {
        val currentChapterIndex = ReadBook.book
            ?.takeIf { BookCharacterProfile.workKey(it.name, it.author) == workKey }
            ?.let { ReadBook.durChapterIndex }
        return compareByDescending<BookTtsCastRole> {
            currentChapterIndex != null && it.lastChapterIndex == currentChapterIndex
        }.thenByDescending { it.lastChapterIndex }
            .thenByDescending { it.occurrenceCount }
            .thenBy { it.id }
    }

    private fun showCharacterDialog(characterId: Long = 0L, castRoleId: Long = 0L) {
        showDialogFragment(BookCharacterEditDialog(workKey, characterId, castRoleId))
    }

    private fun showPromoteDialog(role: BookTtsCastRole) {
        showCharacterDialog(castRoleId = role.id)
    }

    override fun onCharacterSaved(characterId: Long, castRoleId: Long) {
        setResult(RESULT_OK)
        pendingCharacterId = characterId
        if (castRoleId > 0L || page != Page.FORMAL) selectPage(Page.FORMAL)
    }

    private fun showVoiceSheet(row: Row) {
        val selectedBinding = row.binding()
        TtsVoiceSelectionSheet(
            context = this,
            lifecycleScope = lifecycleScope,
            title = row.title(),
            searchHint = getString(R.string.default_tts_voice_search),
            emptyText = getString(R.string.character_tts_no_voice_options),
            engines = { selectableEngines(row) },
            isSelected = { option -> isSelected(selectedBinding, option) },
            onSelect = { option -> saveBinding(row, option) },
            beforePreview = {
                if (BaseReadAloudService.isPlay()) ReadAloud.pause(this)
            },
            titleAction = when {
                selectedBinding == null -> null
                row is Row.Narrator || row is Row.DialogueFallback -> {
                    getString(R.string.clear) to { clearBinding(row) }
                }
                else -> {
                    getString(R.string.character_tts_use_dialogue_fallback) to {
                        saveInheritBinding(row)
                    }
                }
            }
        ).show()
    }

    private fun selectableEngines(row: Row) = when (row) {
        is Row.Narrator -> TtsEngineStore.engines().filter { it.enabled }
        else -> listOfNotNull(
            TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
                ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
        )
    }

    private fun saveBinding(row: Row, option: TtsVoiceOption) {
        lifecycleScope.launch(IO) {
            val now = System.currentTimeMillis()
            val old = row.binding()
            val oldEngineId = old?.engineId
            val stored = (old ?: row.newBinding()).apply {
                engineId = option.engine.id
                voiceId = option.voice.id.takeUnless { option.systemDefault }
                bindingMode = BookCharacterTtsBinding.BindingMode.MANUAL
                if (createdAt <= 0L) createdAt = now
                updatedAt = now
            }
            if (row is Row.Narrator && !oldEngineId.isNullOrBlank() && oldEngineId != option.engine.id) {
                appDb.bookCharacterDao.deleteTtsBinding(
                    workKey,
                    BookCharacterTtsBinding.TargetType.NARRATOR,
                    0L,
                    oldEngineId
                )
            }
            appDb.bookCharacterDao.upsertTtsBinding(stored)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_saved))
                refreshRunningReadAloud()
            }
        }
    }

    private fun clearBinding(row: Row) {
        val target = row.target()
        val engineId = row.binding()?.engineId ?: AppConfig.multiRoleTtsEngineId.orEmpty()
        lifecycleScope.launch(IO) {
            appDb.bookCharacterDao.deleteTtsBinding(workKey, target.first, target.second, engineId)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_cleared))
                refreshRunningReadAloud()
            }
        }
    }

    private fun saveInheritBinding(row: Row) {
        val engineId = AppConfig.multiRoleTtsEngineId.orEmpty()
        if (engineId.isBlank()) return
        lifecycleScope.launch(IO) {
            val now = System.currentTimeMillis()
            val stored = (row.binding() ?: row.newBinding()).apply {
                this.engineId = engineId
                voiceId = null
                bindingMode = BookCharacterTtsBinding.BindingMode.INHERIT
                if (createdAt <= 0L) createdAt = now
                updatedAt = now
            }
            appDb.bookCharacterDao.upsertTtsBinding(stored)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_saved))
                refreshRunningReadAloud()
            }
        }
    }

    private fun refreshRunningReadAloud() {
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.refreshTtsRoute(this)
        }
    }

    private fun ignoredTemporaryRoles(): List<BookTtsCastRole> {
        return snapshot.castRoles
            .filter { it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole() }
            .sortedWith(
                compareByDescending<BookTtsCastRole> { it.occurrenceCount }
                    .thenBy { it.id }
            )
    }

    private fun activeTemporaryRoles(): List<BookTtsCastRole> {
        return snapshot.castRoles.filter {
            !it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole()
        }
    }

    private fun confirmReassign() {
        if (reassigning || activeTemporaryRoles().isEmpty()) return
        if (TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId) == null) {
            toastOnUi(R.string.multi_role_tts_engine_unset)
            return
        }
        alert(titleResource = R.string.character_reassign) {
            setMessage(getString(R.string.character_reassign_message))
            yesButton { reassignTemporaryRoles() }
            noButton()
        }
    }

    private fun reassignTemporaryRoles() {
        if (reassigning) return
        reassigning = true
        invalidateOptionsMenu()
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.prepareTtsCasting(this)
        }
        lifecycleScope.launch(IO) {
            val result = runCatching {
                BookTtsCastingCoordinator.reassignTemporaryRoles(workKey)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                reassigning = false
                invalidateOptionsMenu()
                result.onSuccess { count ->
                    toastOnUi(getString(R.string.character_reassign_done, count))
                }.onFailure { error ->
                    toastOnUi(error.localizedMessage ?: getString(R.string.character_reassign_failed))
                }
                refreshRunningReadAloud()
            }
        }
    }

    private fun showDeletedRoleManager() {
        val roles = ignoredTemporaryRoles()
        if (roles.isEmpty()) return
        DeletedRoleManageDialog(
            context = this,
            roles = roles,
            onRestore = ::restoreTemporaryRoles,
            onPermanentlyDelete = ::permanentlyDeleteTemporaryRoles
        ).show()
    }

    private fun restoreTemporaryRoles(roles: List<BookTtsCastRole>) {
        if (roles.isEmpty()) return
        lifecycleScope.launch(IO) {
            appDb.runInTransaction {
                roles.forEach { role ->
                    appDb.bookCharacterDao.restoreTtsCastRole(role.id)
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                setResult(RESULT_OK)
                toastOnUi(getString(R.string.character_restore_done, roles.size))
                refreshRunningReadAloud()
            }
        }
    }

    private fun permanentlyDeleteTemporaryRoles(roles: List<BookTtsCastRole>) {
        if (roles.isEmpty()) return
        lifecycleScope.launch(IO) {
            var deletedCount = 0
            appDb.runInTransaction {
                roles.forEach { role ->
                    if (appDb.bookCharacterDao.permanentlyDeleteIgnoredTtsCastRole(role.id)) {
                        deletedCount++
                    }
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                setResult(RESULT_OK)
                toastOnUi(getString(R.string.character_deleted_permanent_done, deletedCount))
            }
        }
    }

    private fun confirmDelete(row: Row, adapterPosition: Int) {
        val isFormal = row is Row.Character
        val name = row.title()
        alert(
            titleResource = if (isFormal) {
                R.string.character_delete_formal_title
            } else {
                R.string.character_delete_temporary_title
            }
        ) {
            setMessage(
                getString(
                    if (isFormal) {
                        R.string.character_delete_formal_message
                    } else {
                        R.string.character_delete_temporary_message
                    },
                    name
                )
            )
            yesButton { deleteRow(row) }
            noButton { adapter.notifyItemChanged(adapterPosition) }
        }
    }

    private fun deleteRow(row: Row) {
        lifecycleScope.launch(IO) {
            val doneMessage = when (row) {
                is Row.Character -> {
                    appDb.bookCharacterDao.deleteCharacterWithTts(row.character)
                    R.string.character_delete_formal_done
                }

                is Row.CastRole -> {
                    appDb.bookCharacterDao.ignoreTtsCastRole(row.role)
                    R.string.character_delete_temporary_done
                }

                else -> return@launch
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                setResult(RESULT_OK)
                toastOnUi(doneMessage)
                refreshRunningReadAloud()
            }
        }
    }

    private fun Row.voiceSummary(): String {
        val speaker = when (this) {
            is Row.Narrator -> bindingVoiceName(binding())
                ?: defaultNarratorVoiceName()
                ?: globalVoiceName()

            is Row.DialogueFallback -> bindingVoiceName(binding())
                ?: defaultDialogueVoiceName(gender)
                ?: getString(R.string.character_tts_voice_unset)

            is Row.Character -> roleVoiceName(binding(), character.gender)
            is Row.CastRole -> roleVoiceName(binding(), role.gender)
        }
        return getString(R.string.character_tts_speaker_summary, speaker)
    }

    private fun roleVoiceName(binding: BookCharacterTtsBinding?, gender: String): String {
        if (binding?.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) {
            return fallbackVoiceName(gender)
        }
        if (!snapshot.voiceCatalog.loaded) {
            return bindingVoiceName(binding) ?: fallbackVoiceName(gender)
        }
        val engine = binding?.let { snapshot.voiceCatalog.engines[it.engineId] }
            ?.takeIf { it.enabled }
        val usableVoiceIds = engine
            ?.let { snapshot.voiceCatalog.enabledVoiceIds[it.id] }
            .orEmpty()
        return when (BookTtsBindingPolicy.autoState(binding, usableVoiceIds)) {
            BookTtsBindingPolicy.AutoState.PENDING -> pendingVoiceName(gender)
            BookTtsBindingPolicy.AutoState.PROVISIONAL -> getString(
                R.string.character_tts_provisional_voice,
                bindingVoiceName(binding) ?: pendingVoiceName(gender)
            )

            BookTtsBindingPolicy.AutoState.STABLE,
            BookTtsBindingPolicy.AutoState.PROTECTED ->
                bindingVoiceName(binding) ?: fallbackVoiceName(gender)
        }
    }

    private fun bindingVoiceName(binding: BookCharacterTtsBinding?): String? {
        binding ?: return null
        if (binding.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) return null
        val voiceId = binding.voiceId
        return if (voiceId.isNullOrBlank()) {
            getString(R.string.character_tts_system_default_voice)
        } else {
            snapshot.voiceCatalog.voiceNames[binding.engineId to voiceId] ?: voiceId
        }
    }

    private fun fallbackVoiceName(gender: String): String {
        return getString(
            when (gender) {
                BookCharacter.Gender.MALE -> R.string.character_tts_fallback_male
                BookCharacter.Gender.FEMALE -> R.string.character_tts_fallback_female
                else -> R.string.character_tts_fallback_generic
            }
        )
    }

    private fun pendingVoiceName(gender: String): String {
        return getString(
            when (gender) {
                BookCharacter.Gender.MALE -> R.string.character_tts_pending_male
                BookCharacter.Gender.FEMALE -> R.string.character_tts_pending_female
                else -> R.string.character_tts_pending_generic
            }
        )
    }

    private fun globalVoiceName(): String {
        val engine = snapshot.voiceCatalog.activeEngineId
            ?.let(snapshot.voiceCatalog.engines::get)
        val voice = engine?.activeVoiceId?.let { voiceId ->
            snapshot.voiceCatalog.voiceNames[engine.id to voiceId]
        }
        return when {
            engine == null || !engine.enabled -> getString(R.string.character_tts_voice_unset)
            !voice.isNullOrBlank() -> voice
            else -> getString(R.string.character_tts_system_default_voice)
        }
    }

    private fun defaultNarratorVoiceName(): String? {
        return configuredVoiceName(
            engineId = AppConfig.defaultNarratorTtsEngineId,
            voiceId = AppConfig.defaultNarratorTtsVoiceId,
            allowSystemDefault = true
        )
    }

    private fun defaultDialogueVoiceName(gender: String): String? {
        val voiceId = if (gender == BookCharacter.Gender.MALE) {
            AppConfig.defaultDialogueMaleTtsVoiceId
        } else {
            AppConfig.defaultDialogueFemaleTtsVoiceId
        }
        return configuredVoiceName(
            engineId = AppConfig.multiRoleTtsEngineId,
            voiceId = voiceId,
            allowSystemDefault = false
        )
    }

    private fun configuredVoiceName(
        engineId: String?,
        voiceId: String?,
        allowSystemDefault: Boolean
    ): String? {
        val engine = engineId
            ?.let(snapshot.voiceCatalog.engines::get)
            ?.takeIf { it.enabled }
            ?: return null
        return when {
            !voiceId.isNullOrBlank() &&
                voiceId in snapshot.voiceCatalog.enabledVoiceIds[engine.id].orEmpty() ->
                snapshot.voiceCatalog.voiceNames[engine.id to voiceId]

            allowSystemDefault && engine.type == TtsEngineType.SYSTEM -> {
                getString(R.string.character_tts_system_default_voice)
            }

            else -> null
        }
    }

    private fun isSelected(
        binding: BookCharacterTtsBinding?,
        option: TtsVoiceOption
    ): Boolean {
        binding ?: return false
        return binding.engineId == option.engine.id && if (option.systemDefault) {
            binding.voiceId.isNullOrBlank()
        } else {
            binding.voiceId == option.voice.id
        }
    }

    private fun Row.title(): String = when (this) {
        is Row.Narrator -> getString(R.string.character_tts_narrator)
        is Row.DialogueFallback -> getString(
            if (gender == BookCharacter.Gender.MALE) {
                R.string.character_tts_dialogue_male
            } else {
                R.string.character_tts_dialogue_female
            }
        )

        is Row.Character -> character.name
        is Row.CastRole -> role.name
    }

    private fun Row.gender(): String? = when (this) {
        is Row.Narrator -> null
        is Row.DialogueFallback -> gender
        is Row.Character -> character.gender
        is Row.CastRole -> role.gender
    }

    private fun Row.binding(): BookCharacterTtsBinding? {
        val stored = when (this) {
            is Row.Narrator -> binding
            is Row.DialogueFallback -> binding
            is Row.Character -> binding
            is Row.CastRole -> binding
        }
        return stored?.takeIf {
            this is Row.Narrator || it.engineId == AppConfig.multiRoleTtsEngineId
        }
    }

    private fun Row.newBinding(): BookCharacterTtsBinding = when (this) {
        is Row.Narrator -> BookCharacterTtsBinding.narrator(workKey)
        is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
            BookCharacterTtsBinding.dialogueMale(workKey)
        } else {
            BookCharacterTtsBinding.dialogueFemale(workKey)
        }

        is Row.Character -> BookCharacterTtsBinding.character(workKey, character.id)
        is Row.CastRole -> BookCharacterTtsBinding.castRole(workKey, role.id)
    }

    private fun Row.target(): Pair<String, Long> = when (this) {
        is Row.Narrator -> BookCharacterTtsBinding.TargetType.NARRATOR to 0L
        is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
            BookCharacterTtsBinding.TargetType.DIALOGUE_MALE to 0L
        } else {
            BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE to 0L
        }

        is Row.Character -> BookCharacterTtsBinding.TargetType.CHARACTER to character.id
        is Row.CastRole -> BookCharacterTtsBinding.TargetType.CAST_ROLE to role.id
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        if (page != Page.FORMAL) return false
        val from = adapter.getItems().getOrNull(srcPosition)
        val to = adapter.getItems().getOrNull(targetPosition)
        if (from !is Row.Character || to !is Row.Character) return false
        adapter.swapItem(srcPosition, targetPosition)
        return true
    }

    override fun getSwipeFlags(adapterPosition: Int, defaultFlags: Int): Int {
        return when (adapter.getItems().getOrNull(adapterPosition)) {
            is Row.Character, is Row.CastRole -> ItemTouchHelper.RIGHT
            else -> 0
        }
    }

    override fun onSwiped(adapterPosition: Int, direction: Int) {
        val row = adapter.getItems().getOrNull(adapterPosition)
        if (
            direction != ItemTouchHelper.RIGHT ||
            row !is Row.Character && row !is Row.CastRole
        ) {
            adapter.notifyItemChanged(adapterPosition)
            return
        }
        if (quickDelete) {
            deleteRow(row)
        } else {
            confirmDelete(row, adapterPosition)
        }
    }

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (page != Page.FORMAL) return
        val now = System.currentTimeMillis()
        val sorted = adapter.getItems().mapIndexedNotNull { index, row ->
            (row as? Row.Character)?.character?.apply {
                sortOrder = index
                updatedAt = now
            }
        }
        lifecycleScope.launch(IO) {
            appDb.bookCharacterDao.updateCharacters(*sorted.toTypedArray())
            appDb.bookCharacterDao.updateCharacterCount(workKey, now)
            setResult(RESULT_OK)
        }
    }

    private inner class Adapter :
        RecyclerAdapter<Row, ItemBookCharacterTtsBinding>(this@BookCharacterTtsActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemBookCharacterTtsBinding {
            return ItemBookCharacterTtsBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookCharacterTtsBinding,
            item: Row,
            payloads: MutableList<Any>
        ) = binding.run {
            tvAvatar.text = when (item) {
                is Row.Narrator -> getString(R.string.character_tts_narrator_avatar)
                is Row.DialogueFallback -> getString(
                    if (item.gender == BookCharacter.Gender.MALE) {
                        R.string.character_tts_dialogue_male_avatar
                    } else {
                        R.string.character_tts_dialogue_female_avatar
                    }
                )

                is Row.Character -> item.character.name.firstOrNull()?.toString().orEmpty()
                is Row.CastRole -> item.role.name.firstOrNull()?.toString().orEmpty()
            }
            tvAvatar.setBackgroundResource(item.gender().avatarBackground())
            tvName.text = item.title()
            bindGender(tvGender, item.gender())
            tvVoice.text = item.voiceSummary()
            tvStyle.isVisible = false
            val roleLabel = when (item) {
                is Row.Narrator -> null
                is Row.DialogueFallback -> getString(R.string.character_tts_dialogue_fallback)
                is Row.Character -> BookCharacterLabels.roleLabel(
                    this@BookCharacterTtsActivity,
                    item.character.roleTag
                )

                is Row.CastRole -> getString(R.string.character_temporary_role)
            }
            tvRole.text = roleLabel
            tvRole.isVisible = !roleLabel.isNullOrBlank()
            tvAction.isVisible = item !is Row.CastRole
            tvPromote.isVisible = item is Row.CastRole
            tvAction.text = null
            tvAction.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_chevron_right_20,
                0,
                0,
                0
            )
            TextViewCompat.setCompoundDrawableTintList(
                tvAction,
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@BookCharacterTtsActivity,
                        R.color.ng_on_surface_variant
                    )
                )
            )
            tvAction.background = null
            tvAction.foreground = null
            tvAction.minWidth = 0
            tvAction.setPadding(0, 0, 0, 0)
            tvAction.isClickable = false
            tvAction.isFocusable = false
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBookCharacterTtsBinding) {
            binding.root.setOnClickListener {
                val item = getItemByLayoutPosition(holder) ?: return@setOnClickListener
                if (item is Row.Character) {
                    showCharacterDialog(characterId = item.character.id)
                } else if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
                    showVoiceSheet(item)
                }
            }
            binding.tvVoice.setOnClickListener {
                if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
                    getItemByLayoutPosition(holder)?.let(::showVoiceSheet)
                }
            }
            binding.tvPromote.setOnClickListener {
                (getItemByLayoutPosition(holder) as? Row.CastRole)?.let {
                    showPromoteDialog(it.role)
                }
            }
        }

        private fun getItemByLayoutPosition(holder: ItemViewHolder): Row? {
            val position = holder.bindingAdapterPosition
            return getItem(position)
        }
    }

    private fun bindGender(view: android.widget.TextView, gender: String?) {
        view.isVisible = gender != null
        view.text = when (gender) {
            BookCharacter.Gender.MALE -> "♂"
            BookCharacter.Gender.FEMALE -> "♀"
            null -> ""
            else -> "?"
        }
        view.setTextColor(
            ContextCompat.getColor(
                this,
                when (gender) {
                    BookCharacter.Gender.MALE -> R.color.ng_tts_gender_male
                    BookCharacter.Gender.FEMALE -> R.color.ng_tts_gender_female
                    else -> R.color.ng_on_surface_variant
                }
            )
        )
    }

    private fun String?.avatarBackground(): Int = when (this) {
        BookCharacter.Gender.MALE -> R.drawable.bg_character_avatar_male
        BookCharacter.Gender.FEMALE -> R.drawable.bg_character_avatar_female
        else -> R.drawable.bg_character_avatar_unknown
    }

    private data class Snapshot(
        val characters: List<BookCharacter> = emptyList(),
        val castRoles: List<BookTtsCastRole> = emptyList(),
        val bindings: List<BookCharacterTtsBinding> = emptyList(),
        val voiceCatalog: VoiceCatalogSnapshot = VoiceCatalogSnapshot()
    )

    private data class VoiceCatalogSnapshot(
        val loaded: Boolean = false,
        val engines: Map<String, TtsEngineSetting> = emptyMap(),
        val enabledVoiceIds: Map<String, Set<String>> = emptyMap(),
        val voiceNames: Map<Pair<String, String>, String> = emptyMap(),
        val activeEngineId: String? = null
    ) {
        companion object {
            fun load(): VoiceCatalogSnapshot {
                val engines = TtsEngineStore.engines()
                val activeEngineId = appCtx.getPrefString(PreferKey.ttsEngineV2ActiveId)
                    ?.takeIf { savedId ->
                        engines.any { engine -> engine.id == savedId && engine.enabled }
                    }
                    ?: engines.firstOrNull { it.enabled }?.id
                return VoiceCatalogSnapshot(
                    loaded = true,
                    engines = engines.associateBy { it.id },
                    enabledVoiceIds = engines.associate { engine ->
                        engine.id to engine.enabledVoices().mapTo(mutableSetOf()) { it.id }
                    },
                    voiceNames = engines.flatMap { engine ->
                        engine.effectiveVoices().map { voice ->
                            (engine.id to voice.id) to voice.name
                        }
                    }.toMap(),
                    activeEngineId = activeEngineId
                )
            }
        }
    }

    private sealed interface Row {
        data class Narrator(val binding: BookCharacterTtsBinding?) : Row
        data class DialogueFallback(
            val gender: String,
            val binding: BookCharacterTtsBinding?
        ) : Row

        data class Character(
            val character: BookCharacter,
            val binding: BookCharacterTtsBinding?
        ) : Row

        data class CastRole(
            val role: BookTtsCastRole,
            val binding: BookCharacterTtsBinding?
        ) : Row
    }

    companion object {
        private const val DEFAULT_VOICE_COUNT = 3
    }
}
