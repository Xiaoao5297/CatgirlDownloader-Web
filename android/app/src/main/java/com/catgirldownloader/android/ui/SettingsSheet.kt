package com.catgirldownloader.android.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import com.catgirldownloader.android.R
import com.catgirldownloader.android.data.NsfwModes
import com.catgirldownloader.android.data.Tag
import com.catgirldownloader.android.databinding.SheetSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet with all the settings: language, source, NSFW filter,
 * tags/category/API key (source dependent) and auto-reload.
 */
class SettingsSheet(
    private val activity: AppCompatActivity,
    private val viewModel: MainViewModel,
) {

    private var dialog: BottomSheetDialog? = null
    private var binding: SheetSettingsBinding? = null
    private var tagSuggestions: List<Tag> = emptyList()
    private var searchJob: Job? = null

    fun show() {
        val root = LayoutInflater.from(activity).inflate(R.layout.sheet_settings, null)
        binding = SheetSettingsBinding.bind(root)
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(root)
        dialog.show()
        this.dialog = dialog
        bind()
    }

    private fun bind() {
        val b = binding ?: return
        val state = viewModel.state.value

        // Language
        val langAdapter = ArrayAdapter(
            activity,
            android.R.layout.simple_list_item_1,
            listOf(
                activity.getString(R.string.language_auto),
                "中文",
                "English",
            ),
        )
        b.langInput.setAdapter(langAdapter)
        val langIndex = when (viewModel.prefsLang()) {
            "zh" -> 1
            "en" -> 2
            else -> 0
        }
        b.langInput.setText(langAdapter.getItem(langIndex), false)
        b.langInput.setOnItemClickListener { _, _, position, _ ->
            val lang = when (position) {
                1 -> "zh"
                2 -> "en"
                else -> "auto"
            }
            viewModel.setLang(lang)
            AppCompatDelegate.setApplicationLocales(
                if (lang == "auto") LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(if (lang == "zh") "zh-CN" else "en")
            )
        }

        // Source
        val sourceAdapter = ArrayAdapter(
            activity,
            android.R.layout.simple_list_item_1,
            state.sources.map { it.name },
        )
        b.sourceInput.setAdapter(sourceAdapter)
        val sourceIndex = state.sources.indexOfFirst { it.key == state.sourceKey }
        b.sourceInput.setText(sourceAdapter.getItem(sourceIndex.coerceAtLeast(0)), false)
        b.sourceInput.setOnItemClickListener { _, _, position, _ ->
            val key = state.sources[position].key
            viewModel.setSource(key)
            refreshForSource()
        }

        // NSFW
        val nsfwAdapter = ArrayAdapter(
            activity,
            android.R.layout.simple_list_item_1,
            listOf(
                activity.getString(R.string.nsfw_block),
                activity.getString(R.string.nsfw_only),
                activity.getString(R.string.nsfw_all),
            ),
        )
        b.nsfwInput.setAdapter(nsfwAdapter)
        val nsfwIndex = when (state.nsfwMode) {
            NsfwModes.ONLY -> 1
            NsfwModes.ALL -> 2
            else -> 0
        }
        b.nsfwInput.setText(nsfwAdapter.getItem(nsfwIndex), false)
        b.nsfwInput.setOnItemClickListener { _, _, position, _ ->
            val mode = when (position) {
                1 -> NsfwModes.ONLY
                2 -> NsfwModes.ALL
                else -> NsfwModes.BLOCK
            }
            viewModel.setNsfwMode(mode)
        }

        // Auto reload
        b.reloadSwitch.isChecked = state.autoReload
        b.reloadSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoReload(checked)
        }
        b.intervalInput.setText(state.reloadInterval.toString())
        b.intervalInput.setOnEditorActionListener { v, _, _ ->
            val secs = v.text.toString().toIntOrNull()
            if (secs != null) viewModel.setReloadInterval(secs)
            false
        }
        b.intervalInput.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val secs = (v as EditText).text.toString().toIntOrNull()
                if (secs != null) viewModel.setReloadInterval(secs)
            }
        }

        // Preload count
        val preload = viewModel.preloadCount()
        b.preloadSlider.value = preload.toFloat()
        b.preloadValue.text = preload.toString()
        b.preloadSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val n = value.toInt()
                b.preloadValue.text = n.toString()
                viewModel.setPreloadCount(n)
            }
        }

        // Tag picker
        b.tagInput.setAdapter(ArrayAdapter(activity, android.R.layout.simple_list_item_1, emptyList<String>()))
        b.tagInput.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            val slug = tagSuggestions.firstOrNull { it.name == name }?.slug ?: name
            addTag(slug)
        }
        b.tagInput.setOnEditorActionListener { v, _, _ ->
            val text = v.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) addTag(text)
            true
        }
        b.tagInput.doAfterTextChanged {
            val meta = viewModel.state.value.sources.firstOrNull { it.key == viewModel.state.value.sourceKey }
            if (meta?.tagDynamic == true) scheduleSearch()
        }
        b.categoryInput.setText(viewModel.categoryValue())
        b.categoryInput.setOnEditorActionListener { v, _, _ ->
            viewModel.setCategory(v.text?.toString().orEmpty())
            true
        }
        b.categoryInput.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) viewModel.setCategory((v as EditText).text?.toString().orEmpty())
        }

        // Fluxpoint key
        b.keyInput.setText(viewModel.fluxpointKey())
        b.keyInput.setOnEditorActionListener { v, _, _ ->
            viewModel.setFluxpointKey(v.text?.toString().orEmpty())
            true
        }
        b.keyInput.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) viewModel.setFluxpointKey((v as EditText).text?.toString().orEmpty())
        }

        refreshForSource()
    }

    /** Show/hide source-dependent fields and repopulate chips. */
    private fun refreshForSource() {
        val b = binding ?: return
        val state = viewModel.state.value
        val meta = state.sources.firstOrNull { it.key == state.sourceKey } ?: return

        b.categoryLayout.isVisible = meta.hasTags && !meta.tagPicker
        b.tagPickerLayout.isVisible = meta.tagPicker
        b.keyLayout.isVisible = meta.needsKey

        if (meta.tagPicker) {
            b.tagPickerLabel.text = meta.tagsLabel ?: activity.getString(R.string.tags)
            b.chipGroup.isSingleSelection = meta.tagSingle
            if (b.chipGroup.isSingleSelection) b.chipGroup.isSelectionRequired = false
            renderChips()
            if (meta.tagDynamic) {
                b.tagInput.setAdapter(ArrayAdapter(activity, android.R.layout.simple_list_item_1, emptyList<String>()))
                scheduleSearch()
            } else {
                loadStaticTags()
            }
        }
    }

    private fun loadStaticTags() {
        val b = binding ?: return
        val sourceKey = viewModel.state.value.sourceKey
        CoroutineScope(Dispatchers.Main).launch {
            tagSuggestions = viewModel.searchTags(sourceKey, null).orEmpty()
            if (binding !== b) return@launch
            b.tagInput.setAdapter(
                ArrayAdapter(activity, android.R.layout.simple_list_item_1, tagSuggestions.map { it.name }),
            )
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            delay(250)
            val query = binding?.tagInput?.text?.toString()?.trim().orEmpty()
            val sourceKey = viewModel.state.value.sourceKey
            val tags = viewModel.searchTags(sourceKey, query).orEmpty()
            tagSuggestions = tags
            binding?.tagInput?.setAdapter(
                ArrayAdapter(activity, android.R.layout.simple_list_item_1, tags.map { it.name }),
            )
        }
    }

    private fun renderChips() {
        val b = binding ?: return
        b.chipGroup.removeAllViews()
        val tags = viewModel.state.value.pickerTags
        tags.forEach { tag ->
            val chip = Chip(activity).apply {
                text = tag
                isCloseIconVisible = true
                isCheckable = false
                setOnCloseIconClickListener {
                    viewModel.setPickerTags(viewModel.state.value.pickerTags - tag)
                    renderChips()
                }
            }
            b.chipGroup.addView(chip)
        }
    }

    private fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        val meta = viewModel.state.value.sources.firstOrNull { it.key == viewModel.state.value.sourceKey }
        val current = viewModel.state.value.pickerTags.toMutableList()
        if (meta?.tagSingle == true) {
            current.clear()
            current.add(tag)
        } else {
            if (tag in current || current.size >= 20) return
            current.add(tag)
        }
        viewModel.setPickerTags(current)
        binding?.tagInput?.setText("")
        renderChips()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
    }
}

private fun com.google.android.material.textfield.MaterialAutoCompleteTextView.doAfterTextChanged(
    block: () -> Unit,
) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) {
            block()
        }
    })
}
