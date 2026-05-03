package com.photoframer.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.Feature2D
import org.opencv.features2d.ORB
import org.opencv.features2d.SIFT

/**
 * 特征匹配配置
 */
enum class FeatureMatcherProfile(
    val matcherType: Int,
    val ratioThreshold: Float,
    val minGoodMatches: Int
) {
    REALTIME_ORB(
        matcherType = DescriptorMatcher.BRUTEFORCE_HAMMING,
        ratioThreshold = 0.78f,
        minGoodMatches = 10
    ),
    ROBUST_SIFT(
        matcherType = DescriptorMatcher.FLANNBASED,
        ratioThreshold = 0.70f,
        minGoodMatches = 10
    );

    fun createDetector(): Feature2D {
        return when (this) {
            REALTIME_ORB -> ORB.create(400)
            ROBUST_SIFT -> SIFT.create(500)
        }
    }

    fun requiresFloatDescriptors(): Boolean {
        return this == ROBUST_SIFT
    }
}

/**
 * 特征匹配器
 *
 * `REALTIME_ORB` 用于高频实时引导，优先控制 CPU / 发热。
 * `ROBUST_SIFT` 保留作离线/高鲁棒性配置，默认引导流程不再使用。
 */
class FeatureMatcher(
    private val profile: FeatureMatcherProfile = FeatureMatcherProfile.REALTIME_ORB
) {
    private val detector = profile.createDetector()
    private val matcher = DescriptorMatcher.create(profile.matcherType)

    private var cachedTargetKeypoints: MatOfKeyPoint? = null
    private var cachedTargetDescriptors: Mat? = null
    
    /**
     * 预处理目标图片（只需调用一次）
     */
    fun setTargetImage(targetBitmap: Bitmap) {
        val targetMat = Mat()
        val targetGray = Mat()
        val targetKeypoints = MatOfKeyPoint()
        val targetDescriptors = Mat()
        val mask = Mat()

        try {
            Utils.bitmapToMat(targetBitmap, targetMat)
            org.opencv.imgproc.Imgproc.cvtColor(targetMat, targetGray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)

            detector.detectAndCompute(targetGray, mask, targetKeypoints, targetDescriptors)
            prepareDescriptorsForMatcher(targetDescriptors)

            cachedTargetKeypoints?.release()
            cachedTargetDescriptors?.release()
            cachedTargetKeypoints = targetKeypoints
            cachedTargetDescriptors = targetDescriptors
        } catch (error: Exception) {
            targetKeypoints.release()
            targetDescriptors.release()
            throw error
        } finally {
            mask.release()
            targetGray.release()
            targetMat.release()
        }
    }
    
    /**
     * 计算当前帧与目标图片的单应性矩阵
     */
    fun computeHomography(currentBitmap: Bitmap): HomographyResult {
        return computeTransform(currentBitmap, usePerspective = false)
    }

    /**
     * 计算当前帧与目标图片的完整单应矩阵
     * 用于 View-change 任务评估透视变化
     */
    fun computePerspectiveHomography(currentBitmap: Bitmap): HomographyResult {
        return computeTransform(currentBitmap, usePerspective = true)
    }

    private fun computeTransform(
        currentBitmap: Bitmap,
        usePerspective: Boolean
    ): HomographyResult {
        if (cachedTargetDescriptors == null || cachedTargetDescriptors!!.empty()) {
            return HomographyResult(null, 0, "目标图片未初始化")
        }
        
        // 转换当前帧
        val currentMat = Mat()
        val currentGray = Mat()
        val currentKeypoints = MatOfKeyPoint()
        val currentDescriptors = Mat()
        val mask = Mat()
        var srcPointsMat: MatOfPoint2f? = null
        var dstPointsMat: MatOfPoint2f? = null
        var inliers: Mat? = null
        val knnMatches = mutableListOf<MatOfDMatch>()
        var transform: Mat? = null

        try {
            Utils.bitmapToMat(currentBitmap, currentMat)
            org.opencv.imgproc.Imgproc.cvtColor(currentMat, currentGray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)

            detector.detectAndCompute(currentGray, mask, currentKeypoints, currentDescriptors)
            if (currentDescriptors.empty()) {
                return HomographyResult(null, 0, "无法提取特征")
            }

            prepareDescriptorsForMatcher(currentDescriptors)

            // 语义约定：
            // - query = target
            // - train = current
            // 因此下面求出的变换是 "target -> current"。
            // StepValidator 的反馈方向基于这套约定实现，不能随意交换 query/train。
            matcher.knnMatch(cachedTargetDescriptors, currentDescriptors, knnMatches, 2)
            
            // Lowe's ratio test 筛选好的匹配
            val goodMatches = mutableListOf<DMatch>()
            for (match in knnMatches) {
                val matchList = match.toList()
                if (matchList.size >= 2) {
                    val m = matchList[0]
                    val n = matchList[1]
                    if (m.distance < profile.ratioThreshold * n.distance) {
                        goodMatches.add(m)
                    }
                }
            }

            if (goodMatches.size < profile.minGoodMatches) {
                return HomographyResult(null, goodMatches.size, "匹配点不足 (${goodMatches.size})")
            }

            // 提取匹配点坐标
            val targetKpList = cachedTargetKeypoints!!.toList()
            val currentKpList = currentKeypoints.toList()

            val targetPoints = mutableListOf<Point>()
            val currentPoints = mutableListOf<Point>()

            for (match in goodMatches) {
                targetPoints.add(targetKpList[match.queryIdx].pt)
                currentPoints.add(currentKpList[match.trainIdx].pt)
            }

            srcPointsMat = MatOfPoint2f(*targetPoints.toTypedArray())
            dstPointsMat = MatOfPoint2f(*currentPoints.toTypedArray())

            // 计算变换矩阵
            inliers = Mat()
            transform = if (usePerspective) {
                Calib3d.findHomography(
                    srcPointsMat,
                    dstPointsMat,
                    Calib3d.RANSAC,
                    5.0,
                    inliers
                )
            } else {
                // 计算仿射矩阵 (限制为: 平移 + 旋转 + 缩放)
                // 这种约束比单应性矩阵(8自由度)更稳定，非常适合引导用户调整相机位置
                Calib3d.estimateAffinePartial2D(
                    srcPointsMat,
                    dstPointsMat,
                    inliers,
                    Calib3d.RANSAC,
                    5.0
                )
            }

            // 计算内点数量
            val inlierCount = Core.countNonZero(inliers)

            if (transform.empty()) {
                transform.release()
                val transformName = if (usePerspective) "单应矩阵" else "仿射矩阵"
                return HomographyResult(null, goodMatches.size, "无法计算$transformName")
            }

            return HomographyResult(transform, inlierCount, "成功 ($inlierCount 内点)")
        } catch (e: Exception) {
            transform?.release()
            return HomographyResult(null, 0, "匹配失败: ${e.message}")
        } finally {
            knnMatches.forEach { it.release() }
            inliers?.release()
            srcPointsMat?.release()
            dstPointsMat?.release()
            mask.release()
            currentDescriptors.release()
            currentKeypoints.release()
            currentGray.release()
            currentMat.release()
        }
    }

    fun close() {
        cachedTargetKeypoints?.release()
        cachedTargetDescriptors?.release()
        cachedTargetKeypoints = null
        cachedTargetDescriptors = null
    }

    private fun prepareDescriptorsForMatcher(descriptors: Mat) {
        if (profile.requiresFloatDescriptors()) {
            descriptors.convertTo(descriptors, CvType.CV_32F)
        }
    }
}

/**
 * 单应性矩阵计算结果
 */
data class HomographyResult(
    val homography: Mat?,
    val matchCount: Int,
    val message: String
) {
    fun release() {
        homography?.release()
    }
}
