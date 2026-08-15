#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MNN-JNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MNN-JNI", __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_mnnvoiceassistant_engine_ASREngine_nativeCreateRecognizer(
        JNIEnv *env, jobject thiz, jstring model_path, jstring config_path) {
    const char *model = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Creating ASR recognizer: %s", model);
    env->ReleaseStringUTFChars(model_path, model);
    return 1;
}

JNIEXPORT jstring JNICALL
Java_com_example_mnnvoiceassistant_engine_ASREngine_nativeRecognize(
        JNIEnv *env, jobject thiz, jlong handle, jfloatArray samples) {
    return env->NewStringUTF("Native ASR placeholder");
}

JNIEXPORT void JNICALL
Java_com_example_mnnvoiceassistant_engine_ASREngine_nativeReleaseRecognizer(
        JNIEnv *env, jobject thiz, jlong handle) {
    LOGI("Releasing ASR");
}

JNIEXPORT jlong JNICALL
Java_com_example_mnnvoiceassistant_engine_TTSEngine_nativeCreateTTS(
        JNIEnv *env, jobject thiz, jstring model_path, jstring config_path) {
    const char *model = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Creating TTS: %s", model);
    env->ReleaseStringUTFChars(model_path, model);
    return 1;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_mnnvoiceassistant_engine_TTSEngine_nativeSynthesize(
        JNIEnv *env, jobject thiz, jlong handle, jstring text, jint speaker_id, jfloat speed) {
    jfloatArray result = env->NewFloatArray(0);
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_mnnvoiceassistant_engine_TTSEngine_nativeReleaseTTS(
        JNIEnv *env, jobject thiz, jlong handle) {
    LOGI("Releasing TTS");
}

}
