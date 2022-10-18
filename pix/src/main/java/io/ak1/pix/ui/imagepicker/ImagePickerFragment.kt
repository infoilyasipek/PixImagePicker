package io.ak1.pix.ui.imagepicker

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import io.ak1.pix.adapters.MainImageAdapter
import io.ak1.pix.databinding.FragmentImagePickerBinding
import io.ak1.pix.helpers.*
import io.ak1.pix.interfaces.OnSelectionListener
import io.ak1.pix.models.Img
import io.ak1.pix.models.Options
import io.ak1.pix.utility.ARG_PARAM_PIX
import io.ak1.pix.utility.ARG_PARAM_PIX_KEY
import kotlinx.coroutines.*

class ImagePickerFragment(private val resultCallback: ((Results) -> Unit)? = null) :
    Fragment(), View.OnTouchListener {

    private val viewModel: ImagePickerViewModel by viewModels()
    private var _binding: FragmentImagePickerBinding? = null
    private val binding get() = _binding!!
    private var scope = CoroutineScope(Dispatchers.IO)
    private lateinit var options: Options
    internal val mScrollbarHider = Runnable { _binding?.hideScrollbar() }
    private lateinit var mainImageAdapter: MainImageAdapter

    private var permReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                binding.permissionsLayout.permissionsLayout.hide()
                binding.gridLayout.gridLayout.show()
                initialise(requireActivity())
            } else {
                binding.gridLayout.gridLayout.hide()
                binding.permissionsLayout.permissionsLayout.show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        options = arguments?.getParcelable(ARG_PARAM_PIX) ?: Options()
        requireActivity().let {
            it.setupScreen()
            it.actionBar?.hide()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = run {
        _binding = FragmentImagePickerBinding.inflate(inflater, container, false)
        binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().setup()
    }

    private fun FragmentActivity.setup() {
        setUpMargins(binding)
        permissions()
        reSetup(this)
        //in case of resetting the options in an live fragment
        setFragmentResultListener(ARG_PARAM_PIX_KEY) { _, bundle ->
            val options1: Options? = bundle.getParcelable(ARG_PARAM_PIX)
            options1?.let {
                this@ImagePickerFragment.options.preSelectedUrls.apply {
                    clear()
                    addAll(it.preSelectedUrls)
                }
            }
            permReqLauncher.permissionsFilterForImagePicker(this, options) {
                retrieveMedia()
            }

        }
    }

    private fun permissions() {
        binding.permissionsLayout.permissionButton.setOnClickListener {
            permReqLauncher.permissionsFilterForImagePicker(requireActivity(), options) {
                initialise(requireActivity())
            }
        }
    }

    private fun reSetup(context: FragmentActivity) {
        permReqLauncher.permissionsFilterForImagePicker(context, options) {
            initialise(context)
        }
    }


    private fun initialise(context: FragmentActivity) {
        binding.permissionsLayout.permissionsLayout.hide()
        binding.gridLayout.gridLayout.show()
        setupAdapters(context)
        setupFastScroller(context)
        observeSelectionList()
        retrieveMedia()
        setFastScrollbar()
        setupControls()
    }

    private fun setFastScrollbar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.gridLayout.recyclerView.addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    showScrollbar(binding.gridLayout.fastscrollScrollbar, requireContext())
                    mViewHeight = binding.gridLayout.fastscrollScrollbar.measuredHeight.toFloat()
                    handler.post { _binding?.setViewPositions(getScrollProportion(_binding?.gridLayout?.recyclerView)) }
                }
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFastScroller(context: FragmentActivity) {
        toolbarHeight = context.toPx(56f)
        binding.gridLayout.apply {
            fastscrollScrollbar.hide()
            fastscrollBubble.hide()
            fastscrollHandle.setOnTouchListener(this@ImagePickerFragment)
        }
    }

    private fun setupAdapters(context: FragmentActivity) {
        val onSelectionListener: OnSelectionListener = object : OnSelectionListener {
            override fun onClick(element: Img?, view: View?, position: Int) {
                viewModel.onImageSelected(element, position) {
                    val size = viewModel.selectionListSize
                    if (options.count <= size) {
                        requireActivity().applicationContext.toast(size)
                        return@onImageSelected false
                    }
                    mainImageAdapter.select(it, position)
                    return@onImageSelected true
                }
            }

            override fun onLongClick(element: Img?, view: View?, position: Int) =
                viewModel.onImageLongSelected(element, position) {
                    val size = viewModel.selectionListSize
                    if (options.count <= size) {
                        requireActivity().applicationContext.toast(size)
                        return@onImageLongSelected false
                    }
                    mainImageAdapter.select(it, position)
                    return@onImageLongSelected true
                }
        }
        mainImageAdapter = MainImageAdapter(context, options.spanCount).apply {
            addOnSelectionListener(onSelectionListener)
            setHasStableIds(true)
        }

        binding.gridLayout.apply {
            recyclerView.setupMainRecyclerView(
                context, mainImageAdapter, scrollListener(this@ImagePickerFragment, binding)
            )
        }
    }

    private fun observeSelectionList() {
        viewModel.setOptions(options)
        viewModel.imageList.observe(requireActivity()) {
            mainImageAdapter.addImageList(it.list)
            viewModel.selectionList.value?.addAll(it.selection)
            viewModel.changeSelectionList(viewModel.selectionList.value)
        }
        viewModel.selectionList.observe(requireActivity()) {
            if (it.size == 0) {
                viewModel.changeLongSelectionStatus(false)
            } else if (viewModel.longSelectionValue.not()) {
                viewModel.changeLongSelectionStatus(true)
            }
            binding.setSelectionText(requireActivity(), it.size)
        }
        viewModel.longSelection.observe(requireActivity()) {
            binding.longSelectionStatus(it)
        }
        viewModel.callResults.observe(requireActivity()) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let { set ->
                viewModel.changeSelectionList(HashSet())
                options.preSelectedUrls.clear()
                val results = set.map { it.contentUrl }
                resultCallback?.invoke(Results(results))
            }
        }
        viewModel.onBackPressedResult.observe(requireActivity()) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let { set ->
                viewModel.changeSelectionList(HashSet())
                options.preSelectedUrls.clear()
                val results = set.map { it.contentUrl }
                resultCallback?.invoke(
                    Results(
                        results,
                        Status.BACK_PRESSED
                    )
                )
            }
        }
    }

    private fun setupControls() {
        binding.setupClickControls { status, _ ->
            when (status) {
                0 -> viewModel.returnObjects()
                1 -> onPressedBackButton()
                2 -> viewModel.changeLongSelectionStatus(true)
            }
        }
    }

    private fun onPressedBackButton() {
        viewModel.selectionList.value?.clear()
        viewModel.returnBackPressed()
    }

    private fun retrieveMedia() {
        if (options.preSelectedUrls.size > options.count) {
            val large = options.preSelectedUrls.size - 1
            val small = options.count
            for (i in large downTo small) {
                options.preSelectedUrls.removeAt(i)
            }
        }
        if (scope.isActive) {
            scope.cancel()
        }
        scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val localResourceManager = LocalResourceManager(requireContext()).apply {
                this.preSelectedUrls = options.preSelectedUrls
            }
            mainImageAdapter.clearList()
            viewModel.retrieveImages(localResourceManager)
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                binding.apply {
                    if (event.x < gridLayout.fastscrollHandle.x - ViewCompat.getPaddingStart(
                            gridLayout.fastscrollHandle
                        )
                    ) {
                        return false
                    }
                    gridLayout.fastscrollHandle.isSelected = true
                    handler.removeCallbacks(mScrollbarHider)
                    cancelAnimation(mScrollbarAnimator, mBubbleAnimator)
                    if (gridLayout.fastscrollScrollbar.isVisible.not()&& (gridLayout.recyclerView.computeVerticalScrollRange() - mViewHeight > 0)) {
                        mScrollbarAnimator = showScrollbar(gridLayout.fastscrollScrollbar, requireActivity())
                    }
                    showBubble()
                    val y = event.rawY
                    setViewPositions(y - toolbarHeight)
                    setRecyclerViewPosition(y, mainImageAdapter)
                    v?.parent?.requestDisallowInterceptTouchEvent(true)
                }

                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.rawY
                binding.setViewPositions(y - toolbarHeight)
                binding.setRecyclerViewPosition(y, mainImageAdapter)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v?.parent?.requestDisallowInterceptTouchEvent(false)
                binding.gridLayout.fastscrollHandle.isSelected = false
                handler.postDelayed(mScrollbarHider, sScrollbarHideDelay.toLong())
                binding.hideBubble()
                return true
            }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.imageList.removeObservers(requireActivity())
        viewModel.selectionList.removeObservers(requireActivity())
        viewModel.longSelection.removeObservers(requireActivity())
        viewModel.callResults.removeObservers(requireActivity())
        _binding = null
    }
}