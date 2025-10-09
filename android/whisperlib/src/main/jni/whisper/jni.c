#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <sys/sysinfo.h>
#include <string.h>
#include "whisper.h"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "JNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)

static inline int min(int a, int b) {
    return (a < b) ? a : b;
}

static inline int max(int a, int b) {
    return (a > b) ? a : b;
}

struct input_stream_context {
    size_t offset;
    JNIEnv * env;
    jobject thiz;
    jobject input_stream;

    jmethodID mid_available;
    jmethodID mid_read;
};

size_t inputStreamRead(void * ctx, void * output, size_t read_size) {
    struct input_stream_context* is = (struct input_stream_context*)ctx;

    jint avail_size = (*is->env)->CallIntMethod(is->env, is->input_stream, is->mid_available);
    jint size_to_copy = read_size < avail_size ? (jint)read_size : avail_size;

    jbyteArray byte_array = (*is->env)->NewByteArray(is->env, size_to_copy);

    jint n_read = (*is->env)->CallIntMethod(is->env, is->input_stream, is->mid_read, byte_array, 0, size_to_copy);

    if (size_to_copy != read_size || size_to_copy != n_read) {
        LOGI("Insufficient Read: Req=%zu, ToCopy=%d, Available=%d", read_size, size_to_copy, n_read);
    }

    jbyte* byte_array_elements = (*is->env)->GetByteArrayElements(is->env, byte_array, NULL);
    memcpy(output, byte_array_elements, size_to_copy);
    (*is->env)->ReleaseByteArrayElements(is->env, byte_array, byte_array_elements, JNI_ABORT);

    (*is->env)->DeleteLocalRef(is->env, byte_array);

    is->offset += size_to_copy;

    return size_to_copy;
}
bool inputStreamEof(void * ctx) {
    struct input_stream_context* is = (struct input_stream_context*)ctx;

    jint result = (*is->env)->CallIntMethod(is->env, is->input_stream, is->mid_available);
    return result <= 0;
}
void inputStreamClose(void * ctx) {

}

JNIEXPORT jlong JNICALL
Java_com_whispercppdemo_whisper_WhisperLib_00024Companion_initContextFromInputStream(
        JNIEnv *env, jobject thiz, jobject input_stream) {
    UNUSED(thiz);

    struct whisper_context *context = NULL;
    struct whisper_model_loader loader = {};
    struct input_stream_context inp_ctx = {};

    inp_ctx.offset = 0;
    inp_ctx.env = env;
    inp_ctx.thiz = thiz;
    inp_ctx.input_stream = input_stream;

    jclass cls = (*env)->GetObjectClass(env, input_stream);
    inp_ctx.mid_available = (*env)->GetMethodID(env, cls, "available", "()I");
    inp_ctx.mid_read = (*env)->GetMethodID(env, cls, "read", "([BII)I");

    loader.context = &inp_ctx;
    loader.read = inputStreamRead;
    loader.eof = inputStreamEof;
    loader.close = inputStreamClose;

    loader.eof(loader.context);

    struct whisper_context_params cparams = whisper_context_default_params();
    context = whisper_init_with_params(&loader, cparams);
    return (jlong) context;
}

static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return AAsset_read((AAsset *) ctx, output, read_size);
}

static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}

static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

static struct whisper_context *whisper_init_from_asset(
        JNIEnv *env,
        jobject assetManager,
        const char *asset_path
) {
    LOGI("Loading model from asset '%s'\n", asset_path);
    AAssetManager *asset_manager = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(asset_manager, asset_path, AASSET_MODE_STREAMING);
    if (!asset) {
        LOGW("Failed to open '%s'\n", asset_path);
        return NULL;
    }

    whisper_model_loader loader = {
            .context = asset,
            .read = &asset_read,
            .eof = &asset_is_eof,
            .close = &asset_close
    };

    return whisper_init_with_params(&loader, whisper_context_default_params());
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring asset_path_str) {
    UNUSED(thiz);
    struct whisper_context *context = NULL;
    const char *asset_path_chars = (*env)->GetStringUTFChars(env, asset_path_str, NULL);
    context = whisper_init_from_asset(env, assetManager, asset_path_chars);
    (*env)->ReleaseStringUTFChars(env, asset_path_str, asset_path_chars);
    return (jlong) context;
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    struct whisper_context *context = NULL;
    const char *model_path_chars = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    context = whisper_init_from_file_with_params(model_path_chars, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path_chars);
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads, jfloatArray audio_data) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);

    // ⚡ MAXIMUM PERFORMANCE CONFIGURATION ⚡
    // Using GREEDY sampling (fastest strategy)
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // === DISABLE ALL OUTPUT (huge performance gain) ===
    params.print_realtime = false;     // ⚡ CRITICAL - printing kills performance
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    // === CORE SETTINGS ===
    params.translate = false;
    params.language = "en";
    params.n_threads = num_threads;    // ⚡ Now using ALL cores
    params.offset_ms = 0;
    params.duration_ms = 0;            // Process entire audio

    // === PERFORMANCE OPTIMIZATIONS ===
    params.no_context = true;          // ⚡ Skip context analysis (faster, slightly less accurate)
    params.no_timestamps = false;      // Generate timestamps (needed for segments)
    params.single_segment = false;     // Allow multiple segments
    params.token_timestamps = false;   // ⚡ No token-level timestamps (faster)
    params.max_len = 0;               // No segment length limit
    params.max_tokens = 0;            // No token limit
    params.split_on_word = true;       // Default
    params.audio_ctx = 0;             // ⚡ Use model default (1500 for tiny, optimal for quality/speed balance)
    params.n_max_text_ctx = 0;        // ⚡ Use model default (16384, prevents hallucination loops)
    params.debug_mode = false;         // ⚡ Disable debug (faster)
    params.tdrz_enable = false;        // No diarization
    params.detect_language = false;    // ⚡ Language is known (faster)

    // === TEMPERATURE FALLBACK (disable for speed) ===
    params.temperature = 0.0f;         // ⚡ Deterministic, single-pass
    params.temperature_inc = 0.0f;     // ⚡ No fallback attempts (faster)
    params.entropy_thold = 2.4f;       // Default
    params.logprob_thold = -1.0f;      // Default
    params.no_speech_thold = 0.6f;     // Default

    // === SUPPRESS SETTINGS ===
    params.suppress_blank = true;      // Default
    params.suppress_nst = false;       // Default

    // === GREEDY SAMPLING STRATEGY ===
    params.greedy.best_of = 1;         // ⚡ Single pass only (fastest)

    whisper_reset_timings(context);

    LOGI("About to run whisper_full with %d samples, %d threads", audio_data_length, num_threads);
    LOGI("Whisper params: realtime=%d, timestamps=%d, context=%d", params.print_realtime, params.print_timestamps, params.no_context);

    int result = whisper_full(context, params, audio_data_arr, audio_data_length);

    LOGI("whisper_full returned: %d", result);
    if (result != 0) {
        LOGI("Failed to run the model, error code: %d", result);
        (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
        return (*env)->NewStringUTF(env, "");  // Return empty string on error
    } else {
        LOGI("Model ran successfully, printing timings...");

        // Extract and log detailed timings (whisper_print_timings goes to stdout, not logcat)
        struct whisper_timings * timings = whisper_get_timings(context);

        LOGI("========================================");
        LOGI("⏱️ WHISPER.CPP DETAILED TIMINGS:");
        LOGI("========================================");
        LOGI("  Sample time:  %8.2f ms (token sampling)", timings->sample_ms);
        LOGI("  Encode time:  %8.2f ms (encoder forward pass)", timings->encode_ms);
        LOGI("  Decode time:  %8.2f ms (decoder forward pass)", timings->decode_ms);
        LOGI("  Batch time:   %8.2f ms (batch decoding)", timings->batchd_ms);
        LOGI("  Prompt time:  %8.2f ms (prompt processing)", timings->prompt_ms);
        LOGI("========================================");
        LOGI("  TOTAL:        %8.2f ms", timings->sample_ms + timings->encode_ms + timings->decode_ms + timings->batchd_ms + timings->prompt_ms);
        LOGI("========================================");

        LOGI("Timings printed, transcription complete");
        
        // Extract transcription text
        const int n_segments = whisper_full_n_segments(context);
        char *full_text = malloc(1);
        full_text[0] = '\0';
        
        for (int i = 0; i < n_segments; i++) {
            const char *segment_text = whisper_full_get_segment_text(context, i);
            if (segment_text != NULL) {
                // Concatenate segment text
                size_t old_len = strlen(full_text);
                size_t segment_len = strlen(segment_text);
                full_text = realloc(full_text, old_len + segment_len + 1);
                strcat(full_text, segment_text);
            }
        }
        
        jstring result_string = (*env)->NewStringUTF(env, full_text);
        free(full_text);
        (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
        return result_string;
    }
}

JNIEXPORT jint JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_segment_text(context, index);
    jstring string = (*env)->NewStringUTF(env, text);
    return string;
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_segment_t0(context, index);
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_segment_t1(context, index);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz
) {
    UNUSED(thiz);
    const char *sysinfo = whisper_print_system_info();
    jstring string = (*env)->NewStringUTF(env, sysinfo);
    return string;
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchMemcpy(JNIEnv *env, jobject thiz,
                                                                      jint n_threads) {
    UNUSED(thiz);
    const char *bench_ggml_memcpy = whisper_bench_memcpy_str(n_threads);
    jstring string = (*env)->NewStringUTF(env, bench_ggml_memcpy);
    return string;
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchGgmlMulMat(JNIEnv *env, jobject thiz,
                                                                          jint n_threads) {
    UNUSED(thiz);
    const char *bench_ggml_mul_mat = whisper_bench_ggml_mul_mat_str(n_threads);
    jstring string = (*env)->NewStringUTF(env, bench_ggml_mul_mat);
    return string;
}
