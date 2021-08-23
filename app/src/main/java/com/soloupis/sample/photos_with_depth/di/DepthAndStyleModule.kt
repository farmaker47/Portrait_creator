package com.soloupis.sample.photos_with_depth.di

import com.soloupis.sample.photos_with_depth.fragments.segmentation.DepthAndStyleModelExecutor
import com.soloupis.sample.photos_with_depth.fragments.segmentation.DepthAndStyleViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val depthAndStyleModule = module {

    factory { DepthAndStyleModelExecutor(get(), false) }

    viewModel {
        DepthAndStyleViewModel(get())
    }
}