// Device Lab measurements (official plan §14.2). Debug-only screen, but the code lives in :audio
// because it drives the same Oboe streams the real engine will use.
//
// Every entry point is called from a Kotlin worker thread, never the UI thread. The audio
// callbacks below follow the engine rules: no allocation, no locks, no logging, no JNI.

#include <jni.h>
#include <oboe/Oboe.h>
#include <oboe/FullDuplexStream.h>
#include <oboe/OboeExtensions.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <thread>
#include <vector>
#include <time.h>

namespace {

int64_t nowNanos() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

// aarch64 FPCR.FZ: flush denormals to zero on the audio thread.
void flushDenormals() {
#if defined(__aarch64__)
    uint64_t fpcr;
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
    asm volatile("msr fpcr, %0" : : "r"(fpcr | (1ULL << 24)));
#endif
}

std::string describe(oboe::AudioStream *s) {
    if (s == nullptr) return "not open";
    char buf[512];
    auto latency = s->calculateLatencyMillis();
    snprintf(buf, sizeof(buf),
             "%s dev=%d rate=%d ch=%d burst=%d buffer=%d capacity=%d sharing=%s perf=%s mmap=%s api=%s%s%s latencyEst=%s",
             s->getDirection() == oboe::Direction::Input ? "in" : "out",
             s->getDeviceId(), s->getSampleRate(), s->getChannelCount(), s->getFramesPerBurst(),
             s->getBufferSizeInFrames(), s->getBufferCapacityInFrames(),
             oboe::convertToText(s->getSharingMode()), oboe::convertToText(s->getPerformanceMode()),
             oboe::OboeExtensions::isMMapUsed(s) ? "yes" : "no",
             oboe::convertToText(s->getAudioApi()),
             s->getDirection() == oboe::Direction::Input ? " preset=" : "",
             s->getDirection() == oboe::Direction::Input ? oboe::convertToText(s->getInputPreset()) : "",
             latency ? (std::to_string(static_cast<int>(latency.value() + 0.5)) + "ms").c_str() : "n/a");
    return buf;
}

class DisconnectFlag : public oboe::AudioStreamErrorCallback {
public:
    std::atomic<bool> disconnected{false};
    void onErrorAfterClose(oboe::AudioStream *, oboe::Result) override { disconnected.store(true); }
};

struct DuplexPair {
    std::shared_ptr<oboe::AudioStream> out;
    std::shared_ptr<oboe::AudioStream> in;
    std::shared_ptr<DisconnectFlag> errors = std::make_shared<DisconnectFlag>();
    std::string error;

    bool open(const std::shared_ptr<oboe::AudioStreamDataCallback> &callback, int preset) {
        oboe::AudioStreamBuilder ob;
        ob.setDirection(oboe::Direction::Output)
          ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
          ->setSharingMode(oboe::SharingMode::Exclusive)
          ->setFormat(oboe::AudioFormat::Float)
          ->setChannelCount(oboe::ChannelCount::Stereo)
          ->setDataCallback(callback)
          ->setErrorCallback(errors);
        if (ob.openStream(out) != oboe::Result::OK) { error = "output open failed"; return false; }

        oboe::AudioStreamBuilder ib;
        ib.setDirection(oboe::Direction::Input)
          ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
          ->setSharingMode(oboe::SharingMode::Exclusive)
          ->setFormat(oboe::AudioFormat::Float)
          ->setFormatConversionAllowed(true)
          ->setChannelCount(oboe::ChannelCount::Mono)
          ->setChannelConversionAllowed(true)
          ->setSampleRate(out->getSampleRate())
          ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
          ->setInputPreset(static_cast<oboe::InputPreset>(preset))
          ->setErrorCallback(errors);
        if (ib.openStream(in) != oboe::Result::OK) { error = "input open failed"; close(); return false; }
        return true;
    }

    void close() {
        if (out) { out->stop(); out->close(); }
        if (in) { in->stop(); in->close(); }
    }
};

// ---------------------------------------------------------------------------------------------
// Round-trip latency: play a known noise burst at known output frames, capture the input in the
// same callback (FullDuplexStream keeps the two frame counters locked), and let Kotlin find the
// burst in the capture by cross-correlation. Lag in frames = round trip on this route.

class LatencyRun : public oboe::FullDuplexStream {
public:
    std::vector<float> burst;
    std::vector<int64_t> starts;
    std::vector<float> capture;
    std::atomic<int> captured{0};
    std::atomic<int> shortReads{0};
    std::atomic<bool> done{false};
    int64_t outFrames = 0;
    int channels = 2;

    oboe::DataCallbackResult onBothStreamsReady(const void *inputData, int numInputFrames,
                                                void *outputData, int numOutputFrames) override {
        const auto *in = static_cast<const float *>(inputData);
        auto *out = static_cast<float *>(outputData);

        int c = captured.load(std::memory_order_relaxed);
        int take = std::min(numInputFrames, static_cast<int>(capture.size()) - c);
        if (take > 0) memcpy(capture.data() + c, in, take * sizeof(float));
        captured.store(c + take, std::memory_order_release);
        if (numInputFrames != numOutputFrames) shortReads.fetch_add(1, std::memory_order_relaxed);

        const int64_t len = static_cast<int64_t>(burst.size());
        for (int i = 0; i < numOutputFrames; ++i) {
            const int64_t idx = outFrames + i;
            float v = 0.0f;
            for (int64_t s : starts) {
                if (idx >= s && idx < s + len) { v = burst[idx - s]; break; }
            }
            for (int ch = 0; ch < channels; ++ch) out[i * channels + ch] = v;
        }
        outFrames += numOutputFrames;

        if (c + take >= static_cast<int>(capture.size())) {
            done.store(true, std::memory_order_release);
            return oboe::DataCallbackResult::Stop;
        }
        return oboe::DataCallbackResult::Continue;
    }
};

// ---------------------------------------------------------------------------------------------
// Plain input capture for the microphone-source comparison.

class InputCapture : public oboe::AudioStreamDataCallback {
public:
    std::vector<float> capture;
    std::atomic<int> captured{0};
    std::atomic<bool> done{false};

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *, void *audioData, int32_t numFrames) override {
        const auto *in = static_cast<const float *>(audioData);
        int c = captured.load(std::memory_order_relaxed);
        int take = std::min(static_cast<int>(numFrames), static_cast<int>(capture.size()) - c);
        if (take > 0) memcpy(capture.data() + c, in, take * sizeof(float));
        captured.store(c + take, std::memory_order_release);
        if (c + take >= static_cast<int>(capture.size())) {
            done.store(true, std::memory_order_release);
            return oboe::DataCallbackResult::Stop;
        }
        return oboe::DataCallbackResult::Continue;
    }
};

// ---------------------------------------------------------------------------------------------
// Stability run: playback layer + sample-scheduled metronome + recording to disk, all at once.
// The callback pushes mic frames into an SPSC ring; a writer thread drains it to a 16-bit WAV.

class SpscRing {
public:
    explicit SpscRing(size_t capacity) : buf_(capacity) {}
    // Producer (audio thread). Returns frames actually written.
    size_t push(const float *data, size_t n) {
        const size_t w = write_.load(std::memory_order_relaxed);
        const size_t r = read_.load(std::memory_order_acquire);
        const size_t free = buf_.size() - (w - r);
        const size_t k = std::min(n, free);
        for (size_t i = 0; i < k; ++i) buf_[(w + i) % buf_.size()] = data[i];
        write_.store(w + k, std::memory_order_release);
        return k;
    }
    // Consumer (writer thread).
    size_t pop(float *data, size_t n) {
        const size_t r = read_.load(std::memory_order_relaxed);
        const size_t w = write_.load(std::memory_order_acquire);
        const size_t k = std::min(n, w - r);
        for (size_t i = 0; i < k; ++i) data[i] = buf_[(r + i) % buf_.size()];
        read_.store(r + k, std::memory_order_release);
        return k;
    }
private:
    std::vector<float> buf_;
    std::atomic<size_t> write_{0};
    std::atomic<size_t> read_{0};
};

class StabilityRun : public oboe::FullDuplexStream {
public:
    explicit StabilityRun(size_t ringFrames) : ring(ringFrames) {}

    SpscRing ring;
    int sampleRate = 48000;
    int channels = 2;
    double bpm = 120.0;
    std::atomic<bool> stopRequested{false};

    std::atomic<int64_t> callbacks{0};
    std::atomic<int64_t> framesOut{0};
    std::atomic<int64_t> framesIn{0};
    std::atomic<int64_t> shortReads{0};
    std::atomic<int64_t> ringOverflowFrames{0};
    std::atomic<int64_t> maxCallbackNanos{0};
    std::atomic<int64_t> clicks{0};

    oboe::DataCallbackResult onBothStreamsReady(const void *inputData, int numInputFrames,
                                                void *outputData, int numOutputFrames) override {
        const int64_t t0 = nowNanos();
        if (callbacks.load(std::memory_order_relaxed) == 0) flushDenormals();

        const auto *in = static_cast<const float *>(inputData);
        auto *out = static_cast<float *>(outputData);

        if (numInputFrames > 0) {
            const size_t pushed = ring.push(in, static_cast<size_t>(numInputFrames));
            if (pushed < static_cast<size_t>(numInputFrames))
                ringOverflowFrames.fetch_add(numInputFrames - static_cast<int64_t>(pushed), std::memory_order_relaxed);
            framesIn.fetch_add(numInputFrames, std::memory_order_relaxed);
        }
        if (numInputFrames != numOutputFrames) shortReads.fetch_add(1, std::memory_order_relaxed);

        // Stand-in playback layer: a quiet two-sine pad, so the output path carries real content.
        // Metronome: 1 kHz/1.5 kHz (accent) 20 ms decaying click at exact sample frames.
        const int64_t beatFrames = static_cast<int64_t>(std::llround(sampleRate * 60.0 / bpm));
        const int64_t clickLen = sampleRate / 50;
        int64_t f = framesOut.load(std::memory_order_relaxed);
        for (int i = 0; i < numOutputFrames; ++i, ++f) {
            const double t = static_cast<double>(f) / sampleRate;
            float v = 0.04f * static_cast<float>(std::sin(2.0 * M_PI * 220.0 * t) + std::sin(2.0 * M_PI * 277.18 * t));
            const int64_t beat = f / beatFrames;
            const int64_t inBeat = f - beat * beatFrames;
            if (inBeat == 0) clicks.fetch_add(1, std::memory_order_relaxed);
            if (inBeat < clickLen) {
                const double hz = (beat % 4 == 0) ? 1500.0 : 1000.0;
                const double env = std::exp(-static_cast<double>(inBeat) / (clickLen / 5.0));
                v += 0.3f * static_cast<float>(env * std::sin(2.0 * M_PI * hz * inBeat / sampleRate));
            }
            for (int ch = 0; ch < channels; ++ch) out[i * channels + ch] = v;
        }
        framesOut.store(f, std::memory_order_relaxed);
        callbacks.fetch_add(1, std::memory_order_relaxed);

        const int64_t dt = nowNanos() - t0;
        if (dt > maxCallbackNanos.load(std::memory_order_relaxed)) maxCallbackNanos.store(dt, std::memory_order_relaxed);

        return stopRequested.load(std::memory_order_relaxed) ? oboe::DataCallbackResult::Stop
                                                             : oboe::DataCallbackResult::Continue;
    }
};

void writeWavHeader(FILE *f, int sampleRate, uint32_t dataBytes) {
    auto u32 = [&](uint32_t v) { fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { fwrite(&v, 2, 1, f); };
    fwrite("RIFF", 1, 4, f); u32(36 + dataBytes); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); u32(16); u16(1); u16(1); u32(sampleRate); u32(sampleRate * 2); u16(2); u16(16);
    fwrite("data", 1, 4, f); u32(dataBytes);
}

struct StabilitySession {
    std::shared_ptr<StabilityRun> run;
    DuplexPair streams;
    std::thread writer;
    std::atomic<bool> writerStop{false};
    std::atomic<int64_t> bytesWritten{0};
    std::atomic<int64_t> maxWriteNanos{0};
    std::string path;
    std::string error;
};

std::unique_ptr<StabilitySession> gStability;
std::string gLastInfo;

void setLastInfo(oboe::AudioStream *out, oboe::AudioStream *in) {
    gLastInfo = describe(out) + "\n" + describe(in);
}

} // namespace

// ---------------------------------------------------------------------------------------------
// JNI: com.betterappmafia.apophenia.audio.lab.LabEngine

extern "C" JNIEXPORT jstring JNICALL
Java_com_betterappmafia_apophenia_audio_lab_LabEngine_nativeLastStreamInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(gLastInfo.c_str());
}

// Returns the capture (mono, at meta[0] Hz) or null on failure. meta: [sampleRate, captured,
// shortReads, disconnected].
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_betterappmafia_apophenia_audio_lab_LabEngine_nativeRunLatency(
        JNIEnv *env, jobject, jfloatArray burstArr, jfloat startSeconds, jfloat spacingSeconds,
        jint count, jint preset, jintArray metaArr) {
    auto run = std::make_shared<LatencyRun>();
    const jsize burstLen = env->GetArrayLength(burstArr);
    run->burst.resize(burstLen);
    env->GetFloatArrayRegion(burstArr, 0, burstLen, run->burst.data());

    DuplexPair streams;
    gLastInfo = "";
    if (!streams.open(run, preset)) { gLastInfo = streams.error; return nullptr; }
    const int rate = streams.out->getSampleRate();
    run->channels = streams.out->getChannelCount();
    for (int k = 0; k < count; ++k)
        run->starts.push_back(static_cast<int64_t>((startSeconds + k * spacingSeconds) * rate));
    run->capture.assign(static_cast<size_t>((startSeconds + count * spacingSeconds) * rate), 0.0f);
    run->setSharedOutputStream(streams.out);
    run->setSharedInputStream(streams.in);

    if (run->start() != oboe::Result::OK) { streams.close(); gLastInfo = "start failed"; return nullptr; }
    setLastInfo(streams.out.get(), streams.in.get());

    const double expectedSeconds = static_cast<double>(run->capture.size()) / rate + 3.0;
    const int64_t deadline = nowNanos() + static_cast<int64_t>(expectedSeconds * 1e9);
    while (!run->done.load(std::memory_order_acquire) && !streams.errors->disconnected.load() && nowNanos() < deadline)
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    // Latency estimate from timestamps, read while the streams are still running.
    setLastInfo(streams.out.get(), streams.in.get());
    run->stop();
    streams.close();

    const int captured = run->captured.load(std::memory_order_acquire);
    jint meta[4] = {rate, captured, run->shortReads.load(), streams.errors->disconnected.load() ? 1 : 0};
    env->SetIntArrayRegion(metaArr, 0, 4, meta);
    jfloatArray result = env->NewFloatArray(captured);
    env->SetFloatArrayRegion(result, 0, captured, run->capture.data());
    return result;
}

// Records `seconds` of mono input with the given preset. meta: [sampleRate, captured].
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_betterappmafia_apophenia_audio_lab_LabEngine_nativeRecordInput(
        JNIEnv *env, jobject, jint preset, jfloat seconds, jintArray metaArr) {
    auto cap = std::make_shared<InputCapture>();
    auto errors = std::make_shared<DisconnectFlag>();
    std::shared_ptr<oboe::AudioStream> in;
    oboe::AudioStreamBuilder ib;
    ib.setDirection(oboe::Direction::Input)
      ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
      ->setSharingMode(oboe::SharingMode::Exclusive)
      ->setFormat(oboe::AudioFormat::Float)
      ->setFormatConversionAllowed(true)
      ->setChannelCount(oboe::ChannelCount::Mono)
      ->setChannelConversionAllowed(true)
      ->setSampleRate(48000)
      ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
      ->setInputPreset(static_cast<oboe::InputPreset>(preset))
      ->setDataCallback(cap)
      ->setErrorCallback(errors);
    gLastInfo = "";
    if (ib.openStream(in) != oboe::Result::OK) { gLastInfo = "input open failed"; return nullptr; }
    const int rate = in->getSampleRate();
    cap->capture.assign(static_cast<size_t>(seconds * rate), 0.0f);
    if (in->requestStart() != oboe::Result::OK) { in->close(); gLastInfo = "start failed"; return nullptr; }
    gLastInfo = describe(in.get());

    const int64_t deadline = nowNanos() + static_cast<int64_t>((seconds + 3.0) * 1e9);
    while (!cap->done.load(std::memory_order_acquire) && !errors->disconnected.load() && nowNanos() < deadline)
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    in->stop();
    in->close();

    const int captured = cap->captured.load(std::memory_order_acquire);
    jint meta[2] = {rate, captured};
    env->SetIntArrayRegion(metaArr, 0, 2, meta);
    jfloatArray result = env->NewFloatArray(captured);
    env->SetFloatArrayRegion(result, 0, captured, cap->capture.data());
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_betterappmafia_apophenia_audio_lab_LabEngine_nativeStartStability(
        JNIEnv *env, jobject, jstring wavPath, jint preset, jdouble bpm) {
    if (gStability) return JNI_FALSE;
    auto session = std::make_unique<StabilitySession>();
    const char *p = env->GetStringUTFChars(wavPath, nullptr);
    session->path = p;
    env->ReleaseStringUTFChars(wavPath, p);

    session->run = std::make_shared<StabilityRun>(48000 * 4);  // 4 s of mic headroom for the writer
    session->run->bpm = bpm;
    if (!session->streams.open(session->run, preset)) { gLastInfo = session->streams.error; return JNI_FALSE; }
    session->run->sampleRate = session->streams.out->getSampleRate();
    session->run->channels = session->streams.out->getChannelCount();
    session->run->setSharedOutputStream(session->streams.out);
    session->run->setSharedInputStream(session->streams.in);

    FILE *f = fopen(session->path.c_str(), "wb");
    if (f == nullptr) { session->streams.close(); gLastInfo = "cannot create " + session->path; return JNI_FALSE; }
    const int rate = session->run->sampleRate;
    writeWavHeader(f, rate, 0);

    StabilitySession *s = session.get();
    s->writer = std::thread([s, f, rate]() {
        std::vector<float> chunk(4096);
        std::vector<int16_t> pcm(4096);
        uint32_t dataBytes = 0;
        for (;;) {
            const bool stopping = s->writerStop.load(std::memory_order_acquire);
            const size_t n = s->run->ring.pop(chunk.data(), chunk.size());
            if (n > 0) {
                for (size_t i = 0; i < n; ++i)
                    pcm[i] = static_cast<int16_t>(std::lrint(std::clamp(chunk[i], -1.0f, 1.0f) * 32767.0f));
                const int64_t t0 = nowNanos();
                fwrite(pcm.data(), 2, n, f);
                const int64_t dt = nowNanos() - t0;
                if (dt > s->maxWriteNanos.load()) s->maxWriteNanos.store(dt);
                dataBytes += static_cast<uint32_t>(n * 2);
                s->bytesWritten.store(dataBytes);
            } else if (stopping) {
                break;
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(20));
            }
        }
        fseek(f, 0, SEEK_SET);
        writeWavHeader(f, rate, dataBytes);
        fclose(f);
    });

    if (s->run->start() != oboe::Result::OK) {
        s->writerStop.store(true);
        s->writer.join();
        s->streams.close();
        gLastInfo = "start failed";
        return JNI_FALSE;
    }
    setLastInfo(s->streams.out.get(), s->streams.in.get());
    gStability = std::move(session);
    return JNI_TRUE;
}

// stats: [callbacks, framesOut, framesIn, shortReads, ringOverflowFrames, maxCallbackMicros,
//         outXRuns, inXRuns, bytesWritten, maxWriteMicros, disconnected, clicks, sampleRate]
extern "C" JNIEXPORT jboolean JNICALL
Java_com_betterappmafia_apophenia_audio_lab_LabEngine_nativePollStability(JNIEnv *env, jobject, jlongArray statsArr) {
    if (!gStability) return JNI_FALSE;
    StabilitySession *s = gStability.get();
    auto outX = s->streams.out->getXRunCount();
    auto inX = s->streams.in->getXRunCount();
    jlong stats[13] = {
            s->run->callbacks.load(), s->run->framesOut.load(), s->run->framesIn.load(),
            s->run->shortReads.load(), s->run->ringOverflowFrames.load(),
            s->run->maxCallbackNanos.load() / 1000,
            outX ? outX.value() : -1, inX ? inX.value() : -1,
            s->bytesWritten.load(), s->maxWriteNanos.load() / 1000,
            s->streams.errors->disconnected.load() ? 1 : 0,
            s->run->clicks.load(), s->run->sampleRate};
    env->SetLongArrayRegion(statsArr, 0, 13, stats);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_betterappmafia_apophenia_audio_lab_LabEngine_nativeStopStability(JNIEnv *, jobject) {
    if (!gStability) return;
    StabilitySession *s = gStability.get();
    s->run->stopRequested.store(true);
    std::this_thread::sleep_for(std::chrono::milliseconds(50));
    s->run->stop();
    s->streams.close();
    s->writerStop.store(true, std::memory_order_release);
    s->writer.join();
    gStability.reset();
}
