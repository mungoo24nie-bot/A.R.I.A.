#include <jni.h>
#include <llama.h>
#include <ggml-backend.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

static std::mutex engine_mutex;
static llama_model * current_model = nullptr;
static std::string current_model_path;
static bool backend_initialized = false;

static jbyteArray to_java_bytes(JNIEnv * env, const std::string & text) {
    auto * result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_pl_aria_app_MainActivity_nativeLoadModel(JNIEnv * env, jclass, jstring path) {
    if (!path) return JNI_FALSE;
    const char * chars = env->GetStringUTFChars(path, nullptr);
    if (!chars) return JNI_FALSE;
    std::string new_path(chars);
    env->ReleaseStringUTFChars(path, chars);

    std::lock_guard<std::mutex> guard(engine_mutex);
    if (current_model && current_model_path == new_path) return JNI_TRUE;
    if (current_model) {
        llama_model_free(current_model);
        current_model = nullptr;
        current_model_path.clear();
    }
    if (!backend_initialized) {
        ggml_backend_load_all();
        backend_initialized = true;
    }
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.use_mmap = true;
    current_model = llama_model_load_from_file(new_path.c_str(), params);
    if (!current_model) return JNI_FALSE;
    current_model_path = new_path;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_pl_aria_app_MainActivity_nativeUnloadModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    if (current_model) llama_model_free(current_model);
    current_model = nullptr;
    current_model_path.clear();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_pl_aria_app_MainActivity_nativeGenerate(JNIEnv * env, jclass, jstring prompt_java, jint max_tokens) {
    if (!prompt_java) return nullptr;
    const char * chars = env->GetStringUTFChars(prompt_java, nullptr);
    if (!chars) return nullptr;
    std::string prompt(chars);
    env->ReleaseStringUTFChars(prompt_java, chars);

    std::lock_guard<std::mutex> guard(engine_mutex);
    if (!current_model) return nullptr;
    const llama_vocab * vocab = llama_model_get_vocab(current_model);
    const int token_count = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (token_count <= 0 || token_count > 1600) return nullptr;
    std::vector<llama_token> tokens(token_count);
    if (llama_tokenize(vocab, prompt.c_str(), prompt.size(), tokens.data(), tokens.size(), true, true) != token_count) return nullptr;

    llama_context_params params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 512;
    params.n_ubatch = 256;
    params.n_threads = 4;
    params.n_threads_batch = 4;
    llama_context * ctx = llama_init_from_model(current_model, params);
    if (!ctx) return nullptr;
    llama_sampler_chain_params sample_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sample_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(12345));

    bool success = true;
    for (int start = 0; start < token_count; start += 512) {
        const int length = std::min(512, token_count - start);
        llama_batch batch = llama_batch_get_one(tokens.data() + start, length);
        if (llama_decode(ctx, batch) != 0) { success = false; break; }
    }
    std::string result;
    if (success) {
        const int limit = std::max(1, std::min(256, (int)max_tokens));
        for (int i = 0; i < limit; i++) {
            llama_token token = llama_sampler_sample(sampler, ctx, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            char buffer[8192];
            const int written = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, true);
            if (written < 0) { success = false; break; }
            result.append(buffer, written);
            if (i + 1 < limit) {
                llama_batch batch = llama_batch_get_one(&token, 1);
                if (llama_decode(ctx, batch) != 0) { success = false; break; }
            }
        }
    }
    llama_sampler_free(sampler);
    llama_free(ctx);
    if (!success) return nullptr;
    // Qwen3 może zwrócić znaczniki trybu myślenia. Nie pokazujemy ich użytkownikowi.
    const auto end_think = result.rfind("</think>");
    if (end_think != std::string::npos) result = result.substr(end_think + 8);
    return to_java_bytes(env, result);
}
