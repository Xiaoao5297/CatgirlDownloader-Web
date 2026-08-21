package com.catgirldownloader.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.Coil
import coil.request.ImageRequest
import coil.target.Target
import com.catgirldownloader.android.databinding.ActivityMainBinding
import com.catgirldownloader.android.ui.ArtInfoDialog
import com.catgirldownloader.android.ui.FavoritesDialog
import com.catgirldownloader.android.ui.MainViewModel
import com.catgirldownloader.android.ui.SettingsSheet
import com.catgirldownloader.android.ui.SourcesDisplayName
import com.catgirldownloader.android.ui.UiState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var settingsSheet: SettingsSheet

    private val imageLoader by lazy { Coil.imageLoader(this) }
    private var lastLoadedUrl: String? = null
    private var prevError = false
    private var imageLoading = false
    private var wasLoading = false
    private var pendingDownload = false

    private val writePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingDownload) {
                viewModel.downloadCurrent(this) { ok ->
                    toast(if (ok) R.string.added_to_gallery else R.string.save_failed)
                }
            }
            pendingDownload = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { settingsSheet.show() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorites -> {
                    showFavorites(); true
                }
                R.id.action_about -> {
                    showAbout(); true
                }
                else -> false
            }
        }

        settingsSheet = SettingsSheet(this, viewModel)
        setupImageArea()
        setupControls()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::onState) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progress.collect { p ->
                    binding.reloadProgress.progress = (p * 100).toInt()
                }
            }
        }

        viewModel.fetchImage()
    }

    private fun setupImageArea() {
        binding.imageView.zoomListener = { scale ->
            binding.zoomBadge.text = "${(scale * 100).toInt()}%"
            binding.zoomBadge.isVisible = scale > 1.01f
        }
    }

    private fun setupControls() {
        binding.refreshBtn.setOnClickListener { viewModel.fetchImage() }
        binding.prevBtn.setOnClickListener { viewModel.goPrev() }
        binding.nextBtn.setOnClickListener { viewModel.goNext() }
        binding.saveBtn.setOnClickListener { requestDownload() }
        binding.favBtn.setOnClickListener { viewModel.toggleFavorite() }
        binding.favsBtn.setOnClickListener { showFavorites() }
        binding.artInfoBtn.setOnClickListener {
            viewModel.state.value.current?.let { ArtInfoDialog.show(this, it) }
        }
    }

    private fun onState(state: UiState) {
        SourcesDisplayName.setSources(state.sources)
        val cur = state.current

        binding.refreshBtn.isEnabled = !state.isLoading
        binding.reloadProgress.isVisible = state.autoReload && !state.isLoading

        if (cur == null) {
            binding.placeholder.isVisible = !state.isLoading
            binding.infoBar.isVisible = false
            binding.saveBtn.isEnabled = false
            binding.favBtn.isEnabled = false
            binding.zoomBadge.isVisible = false
        } else {
            binding.placeholder.isVisible = false
            binding.infoBar.isVisible = cur.artist != null
            binding.artistName.text = cur.artist ?: ""
            binding.sourceName.text = SourcesDisplayName.displayName(this, cur.sourceKey)
            binding.saveBtn.isEnabled = true
            binding.favBtn.isEnabled = true

            if (cur.imageUrl != lastLoadedUrl) {
                lastLoadedUrl = cur.imageUrl
                loadImage(cur.imageUrl, wasLoading)
            }
        }

        binding.prevBtn.isEnabled = state.historyIndex > 0 && !state.isLoading
        binding.nextBtn.isEnabled =
            state.historyIndex < state.history.size - 1 && !state.isLoading

        val isFaved = cur != null &&
            (cur.favId != null || state.favorites.any { it.imageUrl == cur.imageUrl })
        binding.favBtn.text = getString(if (isFaved) R.string.favorited else R.string.favorite)
        binding.favBtn.setIconResource(
            if (isFaved) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
        )

        binding.reloadProgress.isVisible = state.autoReload && !state.isLoading

        if (state.error && !prevError) {
            Snackbar.make(binding.root, R.string.error_load, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { viewModel.fetchImage() }
                .show()
        }
        prevError = state.error
        wasLoading = state.isLoading
        refreshLoadingBar()
    }

    /**
     * The bottom loading bar covers the whole load: fetching image metadata
     * ([UiState.isLoading]) plus the actual image download by Coil.
     * It only disappears once the image is really displayed.
     */
    private fun refreshLoadingBar() {
        binding.loadingBar.isVisible = viewModel.state.value.isLoading || imageLoading
    }

    private fun loadImage(url: String, isFetch: Boolean) {
        val imageView = binding.imageView
        imageLoading = true
        refreshLoadingBar()

        val request = ImageRequest.Builder(this)
            .data(url)
            .target(object : Target {
                override fun onStart(placeholder: Drawable?) = Unit

                override fun onError(error: Drawable?) {
                    imageLoading = false
                    refreshLoadingBar()
                    imageView.animate().cancel()
                    imageView.setImageDrawable(error)
                    imageView.visibility = View.VISIBLE
                    if (isFetch) viewModel.onImageDisplayed()
                }

                override fun onSuccess(result: Drawable) {
                    imageLoading = false
                    refreshLoadingBar()
                    imageView.setImageDrawable(result)
                    imageView.visibility = View.VISIBLE
                    // Same effect as the web app: fade in + slight scale up.
                    imageView.animate().cancel()
                    imageView.alpha = 0f
                    imageView.scaleX = 0.97f
                    imageView.scaleY = 0.97f
                    imageView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                    if (isFetch) viewModel.onImageDisplayed()
                }
            })
            .build()
        imageLoader.enqueue(request)
    }

    private fun requestDownload() {
        if (Build.VERSION.SDK_INT < 29) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingDownload = true
                writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        viewModel.downloadCurrent(this) { ok ->
            toast(if (ok) R.string.added_to_gallery else R.string.save_failed)
        }
    }

    private fun showFavorites() {
        FavoritesDialog(this, viewModel).show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(getString(R.string.about_desc) + "\n\n" + getString(R.string.about_credit))
            .setPositiveButton(R.string.about_link) { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/nyarchlinux/catgirldownloader"),
                        ),
                    )
                } catch (e: Exception) {
                    // no browser available
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }
}
