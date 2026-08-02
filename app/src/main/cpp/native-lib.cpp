#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "ContactlessNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/*
 * Optional native acceleration stub. Not called by the default Kotlin
 * pipeline (FingerSegmenter.kt runs the equivalent logic through OpenCV's
 * Java/Kotlin bindings). This exists as the JNI entry point to move
 * segmentation to native C++ if profiling on real mid-range hardware shows
 * it's needed -- segmentation is the dominant cost in the current pipeline
 * (see report.pdf's timing breakdown), so it's the stage most likely to
 * benefit from this path first.
 *
 * Mirrors FingerSegmenter.kt's approach: YCrCb skin-range mask AND'd with
 * an Otsu-thresholded Cr channel, largest external contour, filled mask.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_biometrics_contactless_pipeline_FingerSegmenter_nativeSegment(
        JNIEnv *env,
        jobject /* this */,
        jlong matAddrRgba,
        jlong matAddrMask) {

    cv::Mat &inputBgr = *(cv::Mat *) matAddrRgba;
    cv::Mat &outputMask = *(cv::Mat *) matAddrMask;

    cv::Mat ycrcb;
    cv::cvtColor(inputBgr, ycrcb, cv::COLOR_BGR2YCrCb);

    cv::Mat skinMask;
    cv::inRange(ycrcb, cv::Scalar(0, 133, 77), cv::Scalar(255, 173, 127), skinMask);

    std::vector<cv::Mat> channels;
    cv::split(ycrcb, channels);
    cv::Mat crChannel = channels[1];

    cv::Mat otsuMask;
    cv::threshold(crChannel, otsuMask, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);

    cv::Mat combined;
    cv::bitwise_and(skinMask, otsuMask, combined);

    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(5, 5));
    cv::morphologyEx(combined, combined, cv::MORPH_CLOSE, kernel);
    cv::morphologyEx(combined, combined, cv::MORPH_OPEN, kernel);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(combined, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    if (contours.empty()) {
        LOGI("nativeSegment: no contours found");
        return;
    }

    size_t largestIdx = 0;
    double largestArea = 0.0;
    for (size_t i = 0; i < contours.size(); i++) {
        double area = cv::contourArea(contours[i]);
        if (area > largestArea) {
            largestArea = area;
            largestIdx = i;
        }
    }

    outputMask = cv::Mat::zeros(combined.size(), combined.type());
    cv::drawContours(outputMask, contours, (int) largestIdx, cv::Scalar(255), cv::FILLED);
}
