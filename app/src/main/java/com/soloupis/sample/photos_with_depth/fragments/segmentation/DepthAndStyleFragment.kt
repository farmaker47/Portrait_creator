package com.soloupis.sample.photos_with_depth.fragments.segmentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.soloupis.sample.photos_with_depth.MainActivity
import com.soloupis.sample.photos_with_depth.R
import com.soloupis.sample.photos_with_depth.databinding.FragmentDepthAndStyleBinding
import com.soloupis.sample.photos_with_depth.fragments.StyleFragment
import com.soloupis.sample.photos_with_depth.utils.ImageUtils
import kotlinx.android.synthetic.main.fragment_depth_and_style.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.viewmodel.ext.android.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass.
 * Use the [DepthAndStyleFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 * This is where we show both the captured input image and the output image
 */
class DepthAndStyleFragment : Fragment(),
    SearchFragmentNavigationAdapter.SearchClickItemListener,
    StyleFragment.OnListFragmentInteractionListener {

    private val args: DepthAndStyleFragmentArgs by navArgs()
    private lateinit var filePath: String
    private var finalBitmap: Bitmap? = null
    private var finalBitmapWithStyle: Bitmap? = null

    // Koin inject ViewModel
    private val viewModel: DepthAndStyleViewModel by viewModel()

    // DataBinding
    private lateinit var binding: FragmentDepthAndStyleBinding
    private lateinit var photoFile: File

    // RecyclerView
    private lateinit var mSearchFragmentNavigationAdapter: SearchFragmentNavigationAdapter

    //
    private lateinit var depthAndStyleModelExecutor: DepthAndStyleModelExecutor

    private lateinit var scaledBitmap: Bitmap
    private lateinit var loadedBitmap: Bitmap
    private lateinit var outputBitmapGray: Bitmap
    private lateinit var outputBitmapBlack: Bitmap

    private var inferenceTime: Long = 0L
    private val stylesFragment: StyleFragment = StyleFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true) // enable toolbar

        retainInstance = true
        filePath = args.rootDir
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentDepthAndStyleBinding.inflate(inflater)
        binding.lifecycleOwner = this
        binding.viewModelXml = viewModel

        // RecyclerView setup
        mSearchFragmentNavigationAdapter =
            SearchFragmentNavigationAdapter(
                requireActivity(),
                viewModel.currentList,
                this
            )
        binding.recyclerViewStyles.apply {
            setHasFixedSize(true)
            layoutManager =
                LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mSearchFragmentNavigationAdapter

        }

        // Initialize class with Koin
        depthAndStyleModelExecutor = get()

        getKoin().setProperty(getString(R.string.koinStyle), viewModel.stylename)

        // Observe view model
        observeViewModel()

        // Click on Style picker if visible
        binding.chooseStyleTextView.setOnClickListener {
            stylesFragment.show(requireActivity().supportFragmentManager, "StylesFragment")
        }

        // Listeners for toggle buttons
        binding.imageToggleLeft.setOnClickListener {

            // Make input ImageView visible
            binding.imageviewInput.visibility = View.VISIBLE
            // Make output Image gone
            binding.imageviewOutput.visibility = View.GONE
        }
        binding.imageToggleRight.setOnClickListener {

            // Make input ImageView gone
            binding.imageviewInput.visibility = View.GONE
            // Make output Image visible
            binding.imageviewOutput.visibility = View.VISIBLE
        }

        return binding.root
    }


    private fun observeViewModel() {

        viewModel.styledBitmap.observe(
            requireActivity(),
            Observer { resultImage ->
                if (resultImage != null) {
                    Glide.with(requireActivity())
                        .load(resultImage)
                        .fitCenter()
                        .into(binding.imageviewStyled)

                    // Set this to use with save function
                    finalBitmapWithStyle = resultImage

                }
            }
        )

        // Observe depth procedure
        viewModel.inferenceDone.observe(
            requireActivity(),
            Observer { loadingDone ->
                when (loadingDone) {
                    true -> binding.progressbarStyle.visibility = View.GONE
                }
            }
        )

        viewModel.totalTimeInference.observe(
            requireActivity(),
            Observer { time ->
                //binding.inferenceInfoStyle.text = "Total process time: ${time}ms"
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (filePath.startsWith("/storage")) {
            photoFile = File(filePath)
            // Make input ImageView visible
            binding.imageviewInput.visibility = View.VISIBLE

            loadedBitmap = BitmapFactory.decodeFile(filePath)
            imageview_input.setImageBitmap(loadedBitmap)

            lifecycleScope.launch(Dispatchers.Default) {
                val (bitmapGray, bitmapBlack, inferenceTime) = viewModel.performDepthAndStyleProcedure(
                    loadedBitmap,
                    requireActivity()
                )
                outputBitmapGray = bitmapGray
                outputBitmapBlack = bitmapBlack
                withContext(Dispatchers.Main) {

                    // Make input ImageView gone
                    binding.imageviewInput.visibility = View.GONE

                    updateUI(outputBitmapGray, inferenceTime)
                    finalBitmap = outputBitmapGray

                    // Make output Image visible
                    binding.imageviewOutput.visibility = View.VISIBLE

                }
            }
        } else {
            // When selecting image from gallery
            loadedBitmap =
                BitmapFactory.decodeStream(
                    requireActivity().contentResolver.openInputStream(
                        filePath.toUri()
                    )
                )

            Glide.with(imageview_input.context)
                .load(loadedBitmap)
                .fitCenter()
                .into(imageview_input)

            // Make input ImageView visible
            binding.imageviewInput.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.Default) {
                val (bitmapGray, bitmapBlack, inferenceTime) = viewModel.performDepthAndStyleProcedure(
                    loadedBitmap,
                    requireActivity()
                )
                outputBitmapGray = bitmapGray
                outputBitmapBlack = bitmapBlack
                withContext(Dispatchers.Main) {

                    // Make input ImageView gone
                    binding.imageviewInput.visibility = View.GONE

                    updateUI(outputBitmapGray, inferenceTime)
                    finalBitmap = outputBitmapGray

                    // Make output Image visible
                    binding.imageviewOutput.visibility = View.VISIBLE

                }
            }

        }

    }

    private fun updateUI(outputBitmap: Bitmap?, inferenceTime: Long) {
        progressbar.visibility = View.GONE
        imageview_input.visibility = View.INVISIBLE
        Glide.with(requireActivity())
            .load(outputBitmap)
            .fitCenter()
            .into(imageview_output)

        inference_info.text = "Total process time: " + inferenceTime.toString() + "ms"
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> saveImageToSDCard(finalBitmapWithStyle)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveImageToSDCard(bitmap: Bitmap?): String {
        val file = File(
            MainActivity.getOutputDirectory(requireContext()),
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + "_photo_with_depth.jpg"
        )

        ImageUtils.saveBitmap(bitmap, file)
        Toast.makeText(context, "saved to " + file.absolutePath.toString(), Toast.LENGTH_SHORT)
            .show()

        return file.absolutePath
    }

    companion object {
        private const val TAG = "PhotosAndDepthFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val MODEL_WIDTH = 384
        const val MODEL_HEIGHT = 384
    }

    override fun onListItemClick(itemIndex: Int, sharedImage: ImageView?, type: String) {
        // Upon click show progress bar
        binding.progressbarStyle.visibility = View.VISIBLE
        // make placeholder gone
        imageview_placeholder.visibility = View.GONE

        // Created scaled version of bitmap for model input.
        scaledBitmap = Bitmap.createScaledBitmap(
            loadedBitmap,
            MODEL_WIDTH,
            MODEL_HEIGHT, true
        )

        showStyledImage(type)
        getKoin().setProperty(getString(R.string.koinStyle), type)
        viewModel.setStyleName(type)
    }

    override fun onListFragmentInteraction(item: String) {
    }

    fun methodToStartStyleTransfer(item: String) {
        stylesFragment.dismiss()

        scaledBitmap = Bitmap.createScaledBitmap(
            loadedBitmap,
            MODEL_WIDTH,
            MODEL_HEIGHT, true
        )

        showStyledImage(item)
        getKoin().setProperty(getString(R.string.koinStyle), item)
        viewModel.setStyleName(item)
    }

    private fun showStyledImage(style: String) {
        lifecycleScope.launch(Dispatchers.Default) {

            viewModel.cropBitmapWithMask(
                scaledBitmap, outputBitmapBlack, style
            )
        }
    }
}