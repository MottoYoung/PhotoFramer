package com.photoframer.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.SIFT
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 特征匹配器（优化版）
 * 使用 SIFT 特征点进行图像匹配，计算单应性矩阵
 * SIFT 比 ORB 更稳定，适合实时场景
 */
class FeatureMatcher {
    
    // 使用 SIFT 特征提取器（比 ORB 更准确）
    private val sift = SIFT.create(500)
    
    // 使用 FLANN 匹配器（适合 SIFT）
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED)
    
    // 缓存目标图片的特征
    private var cachedTargetKeypoints: MatOfKeyPoint? = null
    private var cachedTargetDescriptors: Mat? = null
    private var cachedTargetSize: Size? = null
    
    /**
     * 预处理目标图片（只需调用一次）
     */
    fun setTargetImage(targetBitmap: Bitmap) {
        val targetMat = Mat()
        Utils.bitmapToMat(targetBitmap, targetMat)
        
        val targetGray = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(targetMat, targetGray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
        
        cachedTargetKeypoints = MatOfKeyPoint()
        cachedTargetDescriptors = Mat()
        cachedTargetSize = Size(targetMat.cols().toDouble(), targetMat.rows().toDouble())
        
        sift.detectAndCompute(targetGray, Mat(), cachedTargetKeypoints, cachedTargetDescriptors)
        
        // 转换描述子为 float 类型（FLANN 需要）
        cachedTargetDescriptors?.convertTo(cachedTargetDescriptors, CvType.CV_32F)
    }
    
    /**
     * 计算当前帧与目标图片的单应性矩阵
     */
    fun computeHomography(currentBitmap: Bitmap): HomographyResult {
        if (cachedTargetDescriptors == null || cachedTargetDescriptors!!.empty()) {
            return HomographyResult(null, 0, "目标图片未初始化")
        }
        
        // 转换当前帧
        val currentMat = Mat()
        Utils.bitmapToMat(currentBitmap, currentMat)
        
        val currentGray = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(currentMat, currentGray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
        
        // 提取当前帧特征
        val currentKeypoints = MatOfKeyPoint()
        val currentDescriptors = Mat()
        sift.detectAndCompute(currentGray, Mat(), currentKeypoints, currentDescriptors)
        
        if (currentDescriptors.empty()) {
            return HomographyResult(null, 0, "无法提取特征")
        }
        
        // 转换描述子类型
        currentDescriptors.convertTo(currentDescriptors, CvType.CV_32F)
        
        // KNN 匹配
        val knnMatches = mutableListOf<MatOfDMatch>()
        try {
            matcher.knnMatch(cachedTargetDescriptors, currentDescriptors, knnMatches, 2)
        } catch (e: Exception) {
            return HomographyResult(null, 0, "匹配失败: ${e.message}")
        }
        
        // Lowe's ratio test 筛选好的匹配
        val goodMatches = mutableListOf<DMatch>()
        for (match in knnMatches) {
            val matchList = match.toList()
            if (matchList.size >= 2) {
                val m = matchList[0]
                val n = matchList[1]
                if (m.distance < 0.7f * n.distance) {
                    goodMatches.add(m)
                }
            }
        }
        
        if (goodMatches.size < 10) {
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
        
        val srcPointsMat = MatOfPoint2f(*targetPoints.toTypedArray())
        val dstPointsMat = MatOfPoint2f(*currentPoints.toTypedArray())
        
        // 计算单应性矩阵
        // 计算仿射矩阵 (限制为: 平移 + 旋转 + 缩放)
        // 这种约束比单应性矩阵(8自由度)更稳定，非常适合引导用户调整相机位置
        val inliers = Mat()
        val affine2D = Calib3d.estimateAffinePartial2D(
            srcPointsMat,
            dstPointsMat,
            inliers,
            Calib3d.RANSAC,
            5.0
        )
        
        // 计算内点数量
        val inlierCount = Core.countNonZero(inliers)
        
        if (affine2D.empty()) {
            return HomographyResult(null, goodMatches.size, "无法计算仿射矩阵")
        }
        
        return HomographyResult(affine2D, inlierCount, "成功 ($inlierCount 内点)")
    }
}

/**
 * 单应性矩阵计算结果
 */
data class HomographyResult(
    val homography: Mat?,
    val matchCount: Int,
    val message: String
)
