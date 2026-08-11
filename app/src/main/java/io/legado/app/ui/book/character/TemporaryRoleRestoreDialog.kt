package io.legado.app.ui.book.character

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.databinding.DialogTemporaryRoleRestoreBinding
import io.legado.app.databinding.ItemTemporaryRoleRestoreBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.dpToPx

class DeletedRoleManageDialog(
    private val context: Context,
    roles: List<BookTtsCastRole>,
    private val onRestore: (List<BookTtsCastRole>) -> Unit,
    private val onPermanentlyDelete: (List<BookTtsCastRole>) -> Unit
) {

    private val selectedIds = linkedSetOf<Long>()
    private val binding = DialogTemporaryRoleRestoreBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context)
    private val dialog = AlertDialog.Builder(context)
        .setView(binding.root)
        .create()

    init {
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        adapter.setItems(roles)
        binding.tvSelectAll.setOnClickListener { toggleAll() }
        binding.tvCancel.setOnClickListener { dialog.dismiss() }
        binding.tvConfirm.setOnClickListener {
            val selectedRoles = selectedRoles()
            if (selectedRoles.isEmpty()) return@setOnClickListener
            dialog.dismiss()
            onRestore(selectedRoles)
        }
        binding.tvDelete.setOnClickListener {
            val selectedRoles = selectedRoles()
            if (selectedRoles.isEmpty()) return@setOnClickListener
            context.alert(titleResource = R.string.character_deleted_permanent_title) {
                setMessage(
                    context.getString(
                        R.string.character_deleted_permanent_message,
                        selectedRoles.size
                    )
                )
                yesButton {
                    dialog.dismiss()
                    onPermanentlyDelete(selectedRoles)
                }
                noButton()
            }
        }
        updateSelectionState()
    }

    fun show() {
        prepareDialogSize()
        dialog.setOnShowListener {
            dialog.window?.run {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    context.resources.displayMetrics.widthPixels - 32.dpToPx(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        dialog.show()
    }

    private fun prepareDialogSize() {
        val width = context.resources.displayMetrics.widthPixels - 32.dpToPx()
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.86f).toInt()
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (binding.root.measuredHeight <= maxHeight) return
        binding.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            maxHeight
        )
        binding.recyclerView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            topMargin = 12.dpToPx()
        }
    }

    private fun toggleAll() {
        val roles = adapter.getItems()
        if (roles.isNotEmpty() && selectedIds.size == roles.size) {
            selectedIds.clear()
        } else {
            selectedIds.clear()
            selectedIds.addAll(roles.map { it.id })
        }
        adapter.notifyDataSetChanged()
        updateSelectionState()
    }

    private fun selectedRoles(): List<BookTtsCastRole> {
        return adapter.getItems().filter { it.id in selectedIds }
    }

    private fun updateSelectionState() {
        val total = adapter.getItems().size
        val selected = selectedIds.size
        binding.tvSummary.text = context.getString(
            R.string.character_restore_selection_summary,
            selected,
            total
        )
        binding.tvSelectAll.setText(
            if (total > 0 && selected == total) R.string.unselect_all else R.string.select_all
        )
        binding.tvConfirm.isEnabled = selected > 0
        binding.tvConfirm.alpha = if (selected > 0) 1f else 0.45f
        binding.tvDelete.isEnabled = selected > 0
        binding.tvDelete.alpha = if (selected > 0) 1f else 0.45f
    }

    private inner class Adapter(context: Context) :
        RecyclerAdapter<BookTtsCastRole, ItemTemporaryRoleRestoreBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemTemporaryRoleRestoreBinding {
            return ItemTemporaryRoleRestoreBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTemporaryRoleRestoreBinding,
            item: BookTtsCastRole,
            payloads: MutableList<Any>
        ) = binding.run {
            cbRole.isChecked = item.id in selectedIds
            tvName.text = item.name
            tvSummary.text = context.getString(
                R.string.character_restore_role_summary,
                BookCharacterLabels.genderLabel(context, item.gender),
                item.occurrenceCount
            )
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemTemporaryRoleRestoreBinding
        ) {
            binding.root.setOnClickListener {
                val position = holder.bindingAdapterPosition
                val role = getItem(position) ?: return@setOnClickListener
                if (!selectedIds.add(role.id)) selectedIds.remove(role.id)
                notifyItemChanged(position)
                updateSelectionState()
            }
        }
    }
}
