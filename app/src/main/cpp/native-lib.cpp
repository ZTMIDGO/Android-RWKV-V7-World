#include <jni.h>
#include <string>
#include <cstdint>
#include <map>
#include "rwkv.h"
#include "sampler.h"
#include <android/log.h>
#define TAG "jni"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__)

struct Api{
    public: struct rwkv_context *ctx;
    public: float *state_out;
    public: float *logits_out;
    public: size_t logits_len;
};

static sampler sampler;
static int count = 0;
static std::map<int, Api> apiMap;

const char *toChar(JNIEnv* env, jobject obj, jstring javaString) {
    const char *nativeString = env->GetStringUTFChars(javaString, 0);
    env->ReleaseStringUTFChars(javaString, nativeString);
    return nativeString;
}

const jlong tojLong(int value){
    jlong jLong = static_cast<jlong>(value);
    return jLong;
}

const uint32_t *toUint32Array(JNIEnv *env, jobject obj, jintArray jintArrayInput) {
    // 获取数组长度
    jsize length = env->GetArrayLength(jintArrayInput);

    // 分配本地数组以存储 jint 数据
    jint *jintArray = env->GetIntArrayElements(jintArrayInput, nullptr);

    // 创建 uint32_t 数组
    uint32_t *uintArray = new uint32_t[length];

    // 将 jint 转换为 uint32_t
    for (jsize i = 0; i < length; i++) {
        uintArray[i] = static_cast<uint32_t>(jintArray[i]);
    }

    // 释放 jintArray
    env->ReleaseIntArrayElements(jintArrayInput, jintArray, JNI_ABORT);

    return uintArray;
}

const jfloatArray toJfloatArray(JNIEnv *env, jobject obj, float *floatArray, int length) {
    // 创建一个新的 jfloatArray
    jfloatArray jFloatArray = env->NewFloatArray(length);

    // 检查是否成功创建 jfloatArray
    if (jFloatArray == nullptr) {
        return nullptr; // 返回 null 表示失败
    }

    // 将 float 数组数据复制到 jfloatArray
    env->SetFloatArrayRegion(jFloatArray, 0, length, floatArray);

    // 返回 jfloatArray
    return jFloatArray;
}

const float * toFloatArray(JNIEnv *env, jobject obj, jfloatArray jFloatArrayInput){
    // 获取数组长度
    jsize length = env->GetArrayLength(jFloatArrayInput);

    // 提取数组数据到本地缓冲区
    jfloat* tempArray = env->GetFloatArrayElements(jFloatArrayInput, nullptr);

    // 创建一个本地的 float 数组
    float* floatArray = new float[length];

    // 将数据从临时缓冲区复制到本地数组
    for (jsize i = 0; i < length; i++) {
        floatArray[i] = tempArray[i];
    }

    // 释放由 GetFloatArrayElements 分配的资源
    env->ReleaseFloatArrayElements(jFloatArrayInput, tempArray, JNI_ABORT);

    return floatArray;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_demo_rwkv_ai_RWKVModel_nativeInit(
        JNIEnv* env,
        jobject obj,
        jstring filePath
        ) {
    struct rwkv_context * ctx = rwkv_init_from_file(toChar(env, obj, filePath), 4, 0);
    size_t state_len = rwkv_get_state_len(ctx);
    size_t logits_len = rwkv_get_logits_len(ctx);
    float * state = static_cast<float *>(calloc(state_len, sizeof(float)));
    float * logits = static_cast<float *>(calloc(logits_len, sizeof(float)));
    memset(state, 0, sizeof(state));
    memset(logits, 0, sizeof(logits));
    int id = ++ count;
    apiMap.insert(std::pair<int, Api>(id, {ctx, state, logits, logits_len}));
    LOGI("logits_len is %d", logits_len);
    LOGI("state_len is %d", state_len);
    LOGI("ids is %d", id);
    return tojLong(id);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_demo_rwkv_ai_RWKVModel_nativeForward(
        JNIEnv* env,
        jobject obj,
        jlong reference,
        jintArray tonkens
) {
    Api api = apiMap[reference];
    jsize prompt_length = env->GetArrayLength(tonkens);
    uint32_t * prompt_tokens = const_cast<uint32_t *>(toUint32Array(env, obj, tonkens));
    rwkv_eval_sequence_in_chunks(api.ctx, prompt_tokens, prompt_length, 16, api.state_out, api.state_out, api.logits_out);
    delete[] prompt_tokens;
    return toJfloatArray(env, obj, api.logits_out, api.logits_len);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_demo_rwkv_ai_RWKVModel_sampleLogits(
        JNIEnv* env,
        jobject obj,
        jfloatArray logits,
        jint size,
        jfloat temperature,
        jint top_k,
        jfloat top_p
        ) {
    const float * logitsArray = toFloatArray(env, obj, logits);
    const int result = sampler.sample(logitsArray, size, temperature, top_k, top_p);
    delete[] logitsArray;
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_demo_rwkv_ai_RWKVModel_nativeClean(
        JNIEnv* env,
        jobject obj,
        jlong reference
) {

    Api api = apiMap[reference];
    size_t state_len = rwkv_get_state_len(api.ctx);
    size_t logits_len = rwkv_get_logits_len(api.ctx);
    float * state = static_cast<float *>(calloc(state_len, sizeof(float)));
    float * logits = static_cast<float *>(calloc(logits_len, sizeof(float)));
    memset(state, 0, sizeof(state));
    memset(logits, 0, sizeof(logits));
    api.state_out = state;
    api.logits_out = logits;
    api.logits_len = logits_len;
}

extern "C" JNIEXPORT void JNICALL
Java_com_demo_rwkv_ai_RWKVModel_nativeClose(
        JNIEnv* env,
        jobject obj,
        jlong reference
        ) {

    Api api = apiMap[reference];
    apiMap.erase(reference);
    rwkv_free(api.ctx);
    free(api.logits_out);
    free(api.state_out);
}
