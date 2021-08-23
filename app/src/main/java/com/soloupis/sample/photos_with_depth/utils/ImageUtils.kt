/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soloupis.sample.photos_with_depth.utils

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs


/**
 * Collection of image reading and manipulation utilities in the form of static functions.
 */
abstract class ImageUtils {
    companion object {

        /**
         * Helper function used to convert an EXIF orientation enum into a transformation matrix
         * that can be applied to a bitmap.
         * @param orientation - One of the constants from [ExifInterface]
         */
        private fun decodeExifOrientation(orientation: Int): Matrix {
            val matrix = Matrix()

            // Apply transformation corresponding to declared EXIF orientation
            when (orientation) {
                ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> Unit
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postScale(-1F, 1F)
                    matrix.postRotate(270F)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postScale(-1F, 1F)
                    matrix.postRotate(90F)
                }

                // Error out if the EXIF orientation is invalid
                else -> throw IllegalArgumentException("Invalid orientation: $orientation")
            }

            // Return the resulting matrix
            return matrix
        }

        /**
         * sets the Exif orientation of an image.
         * this method is used to fix the exit of pictures taken by the camera
         *
         * @param filePath - The image file to change
         * @param value - the orientation of the file
         */
        fun setExifOrientation(
            filePath: String,
            value: String
        ) {
            val exif = ExifInterface(filePath)
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION, value
            )
            exif.saveAttributes()
        }

        /** Transforms rotation and mirroring information into one of the [ExifInterface] constants */
        fun computeExifOrientation(rotationDegrees: Int, mirrored: Boolean) = when {
            rotationDegrees == 0 && !mirrored -> ExifInterface.ORIENTATION_NORMAL
            rotationDegrees == 0 && mirrored -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
            rotationDegrees == 180 && mirrored -> ExifInterface.ORIENTATION_FLIP_VERTICAL
            rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
            rotationDegrees == 90 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_90
            rotationDegrees == 90 && mirrored -> ExifInterface.ORIENTATION_TRANSPOSE
            rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_ROTATE_270
            rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
            else -> ExifInterface.ORIENTATION_UNDEFINED
        }

        /**
         * Decode a bitmap from a file and apply the transformations described in its EXIF data
         *
         * @param file - The image file to be read using [BitmapFactory.decodeFile]
         */
        fun decodeBitmap(file: File): Bitmap {
            // First, decode EXIF data and retrieve transformation matrix
            val exif = ExifInterface(file.absolutePath)
            val transformation =
                decodeExifOrientation(
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90
                    )
                )

            // Read bitmap using factory methods, and transform it using EXIF data
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            return Bitmap.createBitmap(
                BitmapFactory.decodeFile(file.absolutePath),
                0, 0, bitmap.width, bitmap.height, transformation, true
            )
        }

        fun scaleBitmapAndKeepRatio(
            targetBmp: Bitmap,
            reqHeightInPixels: Int,
            reqWidthInPixels: Int
        ): Bitmap {
            if (targetBmp.height == reqHeightInPixels && targetBmp.width == reqWidthInPixels) {
                return targetBmp
            }
            val matrix = Matrix()
            matrix.setRectToRect(
                RectF(
                    0f, 0f,
                    targetBmp.width.toFloat(),
                    targetBmp.width.toFloat()
                ),
                RectF(
                    0f, 0f,
                    reqWidthInPixels.toFloat(),
                    reqHeightInPixels.toFloat()
                ),
                Matrix.ScaleToFit.FILL
            )
            return Bitmap.createBitmap(
                targetBmp, 0, 0,
                targetBmp.width,
                targetBmp.width, matrix, true
            )
        }

        fun cropBitmap(bitmap: Bitmap): Bitmap {
            val bitmapRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val modelInputRatio = 1.0f
            val maxDifference = 1.0E-5
            var cropHeight = modelInputRatio - bitmapRatio
            return if (abs(cropHeight) < maxDifference) {
                bitmap
            } else {
                val var10000: Bitmap
                val croppedBitmap: Bitmap
                if (modelInputRatio < bitmapRatio) {
                    cropHeight = bitmap.height.toFloat() - bitmap.width.toFloat() / modelInputRatio
                    var10000 = Bitmap.createBitmap(
                        bitmap,
                        0,
                        (cropHeight / 2.toFloat()).toInt(),
                        bitmap.width,
                        (bitmap.height.toFloat() - cropHeight).toInt()
                    )

                    croppedBitmap = var10000
                } else {
                    cropHeight = bitmap.width.toFloat() - bitmap.height.toFloat() * modelInputRatio
                    var10000 = Bitmap.createBitmap(
                        bitmap,
                        (cropHeight / 2.toFloat()).toInt(), 0,
                        (bitmap.width.toFloat() - cropHeight).toInt(), bitmap.height
                    )

                    croppedBitmap = var10000
                }
                croppedBitmap
            }
        }

        fun bitmapToByteBuffer(
            bitmapIn: Bitmap,
            width: Int,
            height: Int,
            mean: Float = 0.0f,
            std: Float = 255.0f
        ): ByteBuffer {
            //var bitmap = cropBitmap(bitmapIn)
            //bitmap = scaleBitmapAndKeepRatio(bitmapIn, width, height)
            val bitmap = Bitmap.createScaledBitmap(bitmapIn, width, height, true)
            val inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4)
            inputImage.order(ByteOrder.nativeOrder())
            inputImage.rewind()

            val intValues = IntArray(width * height)
            bitmap.getPixels(intValues, 0, width, 0, 0, width, height)
            var pixel = 0
            for (i in 0 until 3) {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        //val value = intValues[pixel++]
                        val value = intValues[x * width + y]
                        when (i) {
                            0 -> {
                                inputImage.putFloat(((Color.red(value)) - mean) / std)
                            }
                            1 -> {
                                inputImage.putFloat(((Color.green(value)) - mean) / std)
                            }
                            else -> {
                                inputImage.putFloat(((Color.blue(value)) - mean) / std)
                            }
                        }

                    }
                }
            }

            return inputImage
        }

        fun floatToByteArray(value: Float): ByteArray {
            val intBits = java.lang.Float.floatToIntBits(value)
            return byteArrayOf(
                (intBits shr 24).toByte(),
                (intBits shr 16).toByte(),
                (intBits shr 8).toByte(),
                intBits.toByte()
            )
        }

        fun bitmapToFloatArray(bitmap: Bitmap):
                Array<Array<Array<FloatArray>>> {
            val width: Int = bitmap.width
            val height: Int = bitmap.height
            val intValues = IntArray(width * height)
            bitmap.getPixels(intValues, 0, width, 0, 0, width, height)

            val fourDimensionalArray = Array(1) {
                Array(3) {
                    Array(width) {
                        FloatArray(height)
                    }
                }
            }

            // https://github.com/xuebinqin/U-2-Net/blob/f2b8e4ac1c4fbe90daba8707bca051a0ec830bf6/data_loader.py#L204
            for (i in 0 until width - 1) {
                for (j in 0 until height - 1) {
                    val pixelValue: Int = intValues[i * width + j]
                    fourDimensionalArray[0][0][i][j] =
                        Color.red(pixelValue)
                            .toFloat() //(pixelValue shr 16 and 0xff).toFloat() / 255.0f
                    fourDimensionalArray[0][1][i][j] =
                        Color.green(pixelValue)
                            .toFloat() //(pixelValue shr 8 and 0xff).toFloat() / 255.0f
                    fourDimensionalArray[0][2][i][j] =
                        Color.blue(pixelValue).toFloat() //(pixelValue and 0xff).toFloat() / 255.0f
                }

            }

            // Convert multidimensional array to 1D
            val oneDFloatArray = ArrayList<Float>()

            for (m in fourDimensionalArray[0].indices) {
                for (x in fourDimensionalArray[0][0].indices) {
                    for (y in fourDimensionalArray[0][0][0].indices) {
                        oneDFloatArray.add(fourDimensionalArray[0][m][x][y])
                    }
                }
            }

            val maxValue: Float = oneDFloatArray.maxOrNull() ?: 0f
            val minValue: Float = oneDFloatArray.minOrNull() ?: 0f

            // Final array
            val finalFourDimensionalArray = Array(1) {
                Array(3) {
                    Array(width) {
                        FloatArray(height)
                    }
                }
            }
            for (i in 0 until width - 1) {
                for (j in 0 until height - 1) {
                    val pixelValue: Int = intValues[i * width + j]
                    finalFourDimensionalArray[0][0][i][j] =
                        ((Color.red(pixelValue).toFloat() / maxValue) - 0.485f) / 0.229f
                    finalFourDimensionalArray[0][1][i][j] =
                        ((Color.green(pixelValue).toFloat() / maxValue) - 0.456f) / 0.224f
                    finalFourDimensionalArray[0][2][i][j] =
                        ((Color.blue(pixelValue).toFloat() / maxValue) - 0.406f) / 0.225f
                }

            }

            return finalFourDimensionalArray
        }

        fun convertArrayToBitmapPytorch(
            imageArray: Array<Array<Array<FloatArray>>>,
            imageWidth: Int,
            imageHeight: Int
        ): Pair<Bitmap, Bitmap> {
            val conf = Bitmap.Config.ARGB_8888 // see other conf types
            val grayToneImage = Bitmap.createBitmap(imageWidth, imageHeight, conf)
            val blackWhiteImage = Bitmap.createBitmap(imageWidth, imageHeight, conf)

            for (x in imageArray[0][0].indices) {
                for (y in imageArray[0][0][0].indices) {
                    val color = Color.rgb(
                        //
                        (((imageArray[0][0][x][y]) * 255f).toInt()),
                        (((imageArray[0][0][x][y]) * 255f).toInt()),
                        (((imageArray[0][0][x][y]) * 255f).toInt())
                    )

                    // this y, x is in the correct order!!!
                    grayToneImage.setPixel(y, x, color)
                }
            }
            return Pair(grayToneImage, blackWhiteImage)
        }

        fun bitmapToFloatArrayOld(bitmap: Bitmap):
                Array<Array<Array<FloatArray>>> {
            val width: Int = bitmap.width
            val height: Int = bitmap.height
            val intValues = IntArray(width * height)
            bitmap.getPixels(intValues, 0, width, 0, 0, width, height)

            /*for (k in 0..9) {
                val pixelValue: Int = intValues[k]
                Log.i("PIXEL_NUMBER", k.toString())
                val R = Color.red(pixelValue) / 255.0f
                Log.i("PIXEL_VALUE_R", R.toString())
                val G = Color.green(pixelValue) / 255.0f
                Log.i("PIXEL_VALUE_G", G.toString())
                val B = Color.blue(pixelValue) / 255.0f
                Log.i("PIXEL_VALUE_B", B.toString())
            }*/

            val floatArray = Array(1) {
                Array(3) {
                    Array(width) {
                        FloatArray(height)
                    }
                }
            }

            for (i in 0 until width - 1) {
                for (j in 0 until height - 1) {
                    val pixelValue: Int = intValues[i * width + j]
                    floatArray[0][0][i][j] =
                        Color.red(pixelValue) / 255.0f //(pixelValue shr 16 and 0xff).toFloat() / 255.0f
                    floatArray[0][1][i][j] =
                        Color.green(pixelValue) / 255.0f //(pixelValue shr 8 and 0xff).toFloat() / 255.0f
                    floatArray[0][2][i][j] =
                        Color.blue(pixelValue) / 255.0f //(pixelValue and 0xff).toFloat() / 255.0f
                }

            }

            return floatArray
        }

        fun floatArrayToByteBuffer(bitmap: Bitmap): ByteBuffer {
            val width: Int = bitmap.width
            val height: Int = bitmap.height
            val intValues = IntArray(width * height)
            bitmap.getPixels(intValues, 0, width, 0, 0, width, height)

            val inputImage = ByteBuffer.allocateDirect(1 * 3 * width * height * 4)
            inputImage.order(ByteOrder.nativeOrder())
            inputImage.rewind()

            for (i in 0 until width - 1) {
                for (j in 0 until height - 1) {
                    val pixelValue: Int = intValues[i * width + j]
                    inputImage.putFloat(Color.red(pixelValue) / 255.0f)     //(pixelValue shr 16 and 0xff).toFloat() / 255.0f
                    inputImage.putFloat(Color.green(pixelValue) / 255.0f)   //(pixelValue shr 8 and 0xff).toFloat() / 255.0f
                    inputImage.putFloat(Color.blue(pixelValue) / 255.0f)    //(pixelValue and 0xff).toFloat() / 255.0f
                }

            }

            return inputImage
        }


        fun createEmptyBitmap(imageWidth: Int, imageHeigth: Int, color: Int = 0): Bitmap {
            val ret = Bitmap.createBitmap(imageWidth, imageHeigth, Bitmap.Config.RGB_565)
            if (color != 0) {
                ret.eraseColor(color)
            }
            return ret
        }

        fun loadBitmapFromResources(context: Context, path: String): Bitmap {
            val inputStream = context.assets.open(path)
            return BitmapFactory.decodeStream(inputStream)
        }

        fun convertArrayToBitmap(
            imageArray: Array<Array<Array<FloatArray>>>,
            imageWidth: Int,
            imageHeight: Int
        ): Pair<Bitmap, Bitmap> {

            // Convert multidimensional array to 1D
            val oneDFloatArray = ArrayList<Float>()

            for (m in imageArray[0].indices) {
                for (x in imageArray[0][0].indices) {
                    for (y in imageArray[0][0][0].indices) {
                        oneDFloatArray.add(1 - imageArray[0][m][x][y])
                    }
                }
            }

            val maxValue: Float = oneDFloatArray.maxOrNull() ?: 0f
            val minValue: Float = oneDFloatArray.minOrNull() ?: 0f

            val conf = Bitmap.Config.ARGB_8888 // see other conf types
            val grayToneImage = Bitmap.createBitmap(imageWidth, imageHeight, conf)
            val blackWhiteImage = Bitmap.createBitmap(imageWidth, imageHeight, conf)

            // Use manipulation like Colab post processing......  // 255 * (depth - depth_min) / (depth_max - depth_min)
            for (x in imageArray[0][0].indices) {
                for (y in imageArray[0][0][0].indices) {

                    // Create black and transparent bitmap based on pixel value above a certain number eg. 150
                    // make all pixels black in case value of grayscale image is above 150
                    blackWhiteImage.setPixel(
                        y,
                        x,
                        if ((255 * (imageArray[0][0][x][y] - minValue) / (maxValue - minValue)).toInt() > 150) Color.BLACK else Color.TRANSPARENT
                    )

                    // Create grayscale image to show on screen after inference
                    val color = Color.rgb(
                        (255 * ((1 - imageArray[0][0][x][y]) - minValue) / (maxValue - minValue)).toInt(), //((imageArray[0][0][x][y] * 255).toInt()),
                        (255 * ((1 - imageArray[0][0][x][y]) - minValue) / (maxValue - minValue)).toInt(),//((imageArray[0][0][x][y] * 255).toInt()),
                        (255 * ((1 - imageArray[0][0][x][y]) - minValue) / (maxValue - minValue)).toInt()//(imageArray[0][0][x][y] * 255).toInt()
                    )

                    // this y, x is in the correct order!!!
                    grayToneImage.setPixel(y, x, color)
                }
            }
            return Pair(grayToneImage, blackWhiteImage)
        }

        fun saveBitmap(bitmap: Bitmap?, file: File): String {

            try {
                val stream: OutputStream = FileOutputStream(file)
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                stream.flush()
                stream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return file.absolutePath

        }
    }
}
