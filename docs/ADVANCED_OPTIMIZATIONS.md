# Advanced Whisper.cpp Optimization Analysis

## Executive Summary

After analyzing community-recommended optimizations for whisper.cpp on ARM devices, we identified **3 high-impact opportunities** that could potentially achieve **1.5-2.5x speedup**:

1. ✅ **CMake build flags** - Partially implemented, missing OpenMP
2. 🔥 **CPU affinity pinning** - NOT implemented, highest potential impact (2-3x)
3. ⚠️ **speed_up parameter** - Deprecated/removed in current whisper.cpp version

---

## Current Build Configuration Analysis

### What We Have ✅

**File**: `android/whisperlib/src/main/jni/whisper/CMakeLists.txt:42-57`

```cmake
# ARM64-v8a with FP16 support
if (${target_name} STREQUAL "whisper_v8fp16_va")
    target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
    set(GGML_COMPILE_OPTIONS                      -march=armv8.2-a+fp16)
endif()

# Release optimizations
if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(${target_name} PRIVATE -O3)                    # ✅ Maximum optimization
    target_compile_options(${target_name} PRIVATE -fvisibility=hidden)     # ✅ Symbol hiding
    target_compile_options(${target_name} PRIVATE -ffunction-sections)     # ✅ Dead code elimination
    target_link_options(${target_name} PRIVATE -Wl,--gc-sections)         # ✅ Link-time optimization
    target_link_options(${target_name} PRIVATE -flto)                     # ✅ Link-Time Optimization
endif()
```

**Status**:
✅ `-O3` optimization enabled
✅ ARM64 NEON implicitly enabled via `-march=armv8.2-a`
✅ FP16 support enabled (`+fp16`)
✅ LTO (Link-Time Optimization) enabled
✅ Release mode build

### What We're Missing ⚠️

```cmake
# NOT CURRENTLY SET:
-DGGML_USE_OPENMP=ON     # ⚠️ OpenMP for better thread scheduling
```

**OpenMP Impact**:
- Provides better thread scheduling and work distribution
- Can improve multi-core utilization by 10-20%
- Especially beneficial for NEON operations

**Risk**: Low (OpenMP is stable, widely used)

---

## 1. CPU Affinity Pinning (HIGH IMPACT 🔥)

### The Problem with 8 Threads

**Current approach**: We use all 8 cores (2 performance + 6 efficiency)

**ARM big.LITTLE architecture**:
```
Performance Cores (A78): CPUs 6-7  →  2.6 GHz, advanced NEON, large cache
Efficiency Cores (A55): CPUs 0-5  →  2.0 GHz, basic NEON, small cache
```

**The Issue**:
- Encoder matrix math requires **high-performance SIMD**
- When using 8 threads, Linux scheduler distributes work to ALL cores
- Work on A55 cores runs **2-3x slower** than on A78 cores
- Thread synchronization forces A78 cores to **wait for slow A55 cores**
- Result: **Actual throughput limited by slowest core**

### The Solution: Pin to Performance Cores Only

**Use 2-3 threads pinned to CPUs 6-7** (the A78 performance cores)

**Expected Impact**:
- **2-3x faster encoder** (running only on fast cores)
- No synchronization overhead from slow cores
- Better cache utilization (less context switching)

**Why 2-3 threads, not 2?**
- 2 threads = perfect for 2 physical cores
- 3 threads = allows one core to start next task while other finishes
- More than 3 = risk of spilling to efficiency cores

### Implementation

**Step 1: Detect Performance Cores**

```kotlin
// File: android/app/src/main/kotlin/com/voiceinput/core/CpuInfo.kt
class CpuInfo {
    companion object {
        fun getPerformanceCoreIds(): List<Int> {
            val cpuInfo = File("/proc/cpuinfo").readText()
            val cores = mutableListOf<Int>()

            // Read CPU frequencies to identify performance cores
            for (cpu in 0 until Runtime.getRuntime().availableProcessors()) {
                val freqPath = "/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq"
                val maxFreq = try {
                    File(freqPath).readText().trim().toLong()
                } catch (e: Exception) {
                    0L
                }

                // Performance cores typically have max freq > 2.4 GHz (2400000 kHz)
                if (maxFreq > 2400000) {
                    cores.add(cpu)
                }
            }

            return cores.ifEmpty {
                // Fallback: assume last 2 CPUs are performance cores
                listOf(6, 7)
            }
        }

        fun getOptimalThreadCount(): Int {
            val perfCores = getPerformanceCoreIds()
            // Use 2-3 threads for performance cores
            return minOf(3, perfCores.size)
        }
    }
}
```

**Step 2: Native CPU Affinity Function**

```c
// File: android/whisperlib/src/main/jni/whisper/jni.c

#include <sched.h>  // For CPU affinity

// Add new JNI function to set thread affinity
JNIEXPORT jboolean JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_setThreadAffinity(
        JNIEnv *env, jobject thiz, jintArray cpu_ids) {
    UNUSED(thiz);

    jint* cpus = (*env)->GetIntArrayElements(env, cpu_ids, NULL);
    jsize cpu_count = (*env)->GetArrayLength(env, cpu_ids);

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);

    for (int i = 0; i < cpu_count; i++) {
        CPU_SET(cpus[i], &cpuset);
        LOGI("Setting CPU affinity to core %d", cpus[i]);
    }

    (*env)->ReleaseIntArrayElements(env, cpu_ids, cpus, JNI_ABORT);

    // Set affinity for current process
    int result = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);

    if (result != 0) {
        LOGW("Failed to set CPU affinity: %d", result);
        return JNI_FALSE;
    }

    LOGI("CPU affinity set successfully");
    return JNI_TRUE;
}
```

**Step 3: Update LibWhisper.kt**

```kotlin
// File: android/whisperlib/src/main/java/com/whispercpp/whisper/LibWhisper.kt

class WhisperContext private constructor(private var ptr: Long) {

    suspend fun transcribeData(data: FloatArray, printTimestamp: Boolean = true): String =
        withContext(Dispatchers.Default) {
            require(ptr != 0L)

            // ⚡ NEW: Pin to performance cores only
            val perfCores = CpuInfo.getPerformanceCoreIds()
            val success = WhisperLib.setThreadAffinity(perfCores.toIntArray())

            if (success) {
                Log.d(LOG_TAG, "⚡ Pinned to performance cores: ${perfCores.joinToString()}")
            }

            // Use optimal thread count (2-3 for performance cores)
            val numThreads = CpuInfo.getOptimalThreadCount()
            Log.d(LOG_TAG, "⚡ Using $numThreads threads on ${perfCores.size} performance cores")

            val transcriptionText = WhisperLib.fullTranscribe(ptr, numThreads, data)
            // ... rest of implementation
        }
}

private class WhisperLib {
    companion object {
        // ... existing methods

        // NEW: Native function for CPU affinity
        external fun setThreadAffinity(cpuIds: IntArray): Boolean
    }
}
```

**Expected Results**:
```
Before (8 threads, all cores):
  Encode time: 31,700 ms

After (2-3 threads, performance cores only):
  Encode time: 10,000-15,000 ms  (2-3x faster)

Total RTF: 3.06x → 1.0-1.5x  ✅ Real-time capable!
```

---

## 2. OpenMP Thread Scheduling

### What is OpenMP?

OpenMP provides:
- Better thread work distribution
- Dynamic load balancing
- Optimized synchronization primitives
- Better cache coherency

### Implementation

**Update CMakeLists.txt**:

```cmake
# File: android/whisperlib/src/main/jni/whisper/CMakeLists.txt

# Add after line 39:
target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
target_compile_definitions(${target_name} PUBLIC GGML_USE_OPENMP)  # ⚡ NEW

# Add OpenMP flags
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    target_link_libraries(${target_name} OpenMP::OpenMP_CXX)
    message(STATUS "OpenMP enabled for ${target_name}")
else()
    # Fallback: manually set flags
    target_compile_options(${target_name} PRIVATE -fopenmp)
    target_link_options(${target_name} PRIVATE -fopenmp)
    message(STATUS "OpenMP manually enabled for ${target_name}")
endif()
```

**Expected Impact**:
- 10-20% faster encoder (better work distribution)
- Better scaling with CPU affinity pinning
- Minimal risk (OpenMP is standard)

---

## 3. Speed-Up Parameter Analysis ⚠️

### Investigation

The recommended `speed_up = true` parameter **does not exist** in current whisper.cpp:

```bash
$ grep -r "speed_up" android/whisperlib/whisper.cpp/
# No results
```

**Status**: This parameter was likely in older whisper.cpp versions or removed

**Alternative**: The functionality may be replaced by `audio_ctx` reduction:
```c
// Reducing audio_ctx processes fewer mel-spectrogram frames
params.audio_ctx = 768;  // Instead of 1500 (default)
```

**Caveat**: We already tried aggressive audio_ctx reduction and it caused hallucinations.

**Recommendation**: ❌ Skip this optimization (deprecated or removed)

---

## 4. Additional Whisper Parameters Review

### Current Settings vs Recommendations

| Parameter | Current | Recommended | Status |
|-----------|---------|-------------|--------|
| `no_timestamps` | `false` | `false` | ✅ Same |
| `greedy.best_of` | `1` | `1` | ✅ Same |
| `translate` | `false` | `false` | ✅ Same |
| `no_context` | `true` | `true` | ✅ Same |
| `single_segment` | `false` | `true` (short audio) | ⚠️ Can optimize |

### Single Segment Optimization

**For short audio clips (<10 seconds)**, use `single_segment = true`:

```c
// In jni.c, add audio duration check:
const float audio_duration = audio_data_length / 16000.0f;  // 16kHz sample rate

if (audio_duration < 10.0f) {
    params.single_segment = true;  // ⚡ Skip segmentation for short clips
    LOGI("Short audio detected (%.2fs), using single_segment mode", audio_duration);
}
```

**Expected Impact**: 5-10% faster for short clips (skips segmentation overhead)

---

## 5. Warm-Up Run

### Why It Matters

**First run includes**:
- JIT compilation (Android ART)
- Cache warming
- CPU frequency scaling (ramp-up to max frequency)

**Recommendation**: Run a dummy transcription before measuring:

```kotlin
suspend fun warmUpWhisper() {
    // Generate 1 second of silent audio
    val warmupAudio = FloatArray(16000) { 0.0f }

    Log.i(TAG, "Warming up Whisper engine...")
    whisperEngine.transcribe(warmupAudio)

    // Now CPU is at max frequency and caches are warm
}

// Before benchmark:
warmUpWhisper()
val result = benchmark.benchmarkWithJFKAudio("tiny")
```

**Expected Impact**: More consistent measurements, potential 10-15% faster after warm-up

---

## Recommended Implementation Plan

### Phase 1: CPU Affinity (Highest Impact 🔥)

**Expected**: 2-3x speedup
**Risk**: Low
**Effort**: 2-3 hours

1. Add `CpuInfo.kt` helper class
2. Add native `setThreadAffinity()` JNI function
3. Update `LibWhisper.kt` to use 2-3 threads on performance cores
4. Test and measure

### Phase 2: OpenMP (Medium Impact)

**Expected**: 10-20% speedup
**Risk**: Very low
**Effort**: 30 minutes

1. Update `CMakeLists.txt` with OpenMP flags
2. Rebuild
3. Test and measure

### Phase 3: Dynamic Optimizations (Low Impact)

**Expected**: 5-10% speedup
**Risk**: Low
**Effort**: 1 hour

1. Add `single_segment = true` for short audio
2. Add warm-up run before benchmarks
3. Test and measure

---

## Expected Final Performance

### Current Performance
```
Encode time:  31,700 ms
Total time:   33,700 ms
RTF:          3.06x
```

### After CPU Affinity + OpenMP
```
Encode time:  ~9,000-12,000 ms  (2.5-3.5x faster)
Total time:   ~10,000-13,000 ms
RTF:          0.9-1.2x  ✅ REAL-TIME CAPABLE
```

### Best Case (All Optimizations)
```
Encode time:  ~8,000-10,000 ms
Total time:   ~9,000-11,000 ms
RTF:          0.8-1.0x  🚀 FASTER THAN REAL-TIME
```

---

## Risks and Considerations

### CPU Affinity Risks

**Issue**: Modern Android (10+) restricts `sched_setaffinity()`

**Workaround**:
```c
// If sched_setaffinity() fails, fall back to thread count reduction
if (result != 0) {
    LOGW("CPU affinity failed (restricted by OS), using reduced thread count instead");
    // Still use 2-3 threads, just not pinned
}
```

**Alternative**: Use Android's `Process.setThreadPriority()` to boost priority:
```kotlin
Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
```

### OpenMP Risks

**Issue**: Some Android NDK versions have buggy OpenMP

**Mitigation**:
- Test on multiple devices
- Keep fallback without OpenMP
- Monitor for crashes

---

## Testing Protocol

### Benchmark Script

```kotlin
suspend fun comprehensiveBenchmark() {
    // Test 1: Baseline (current)
    val baseline = benchmark.benchmarkWithJFKAudio("tiny")

    // Test 2: With CPU affinity
    CpuInfo.pinToPerformanceCores()
    val withAffinity = benchmark.benchmarkWithJFKAudio("tiny")

    // Test 3: With OpenMP (requires rebuild)
    val withOpenMP = benchmark.benchmarkWithJFKAudio("tiny")

    // Test 4: Combined
    val combined = benchmark.benchmarkWithJFKAudio("tiny")

    Log.i(TAG, """
        COMPREHENSIVE BENCHMARK RESULTS:
        ================================
        Baseline:         ${baseline.realTimeFactor}x RTF
        + CPU Affinity:   ${withAffinity.realTimeFactor}x RTF (${baseline.realTimeFactor / withAffinity.realTimeFactor}x speedup)
        + OpenMP:         ${withOpenMP.realTimeFactor}x RTF
        + Combined:       ${combined.realTimeFactor}x RTF (${baseline.realTimeFactor / combined.realTimeFactor}x total speedup)
    """.trimIndent())
}
```

---

## Conclusion

### High-Impact Optimizations Worth Implementing

1. ✅ **CPU Affinity Pinning** (2-3x speedup expected)
   - Use 2-3 threads on performance cores only
   - Avoid slow efficiency cores dragging down performance

2. ✅ **OpenMP Threading** (1.1-1.2x speedup expected)
   - Better thread scheduling
   - Low risk, easy to implement

3. ✅ **Single Segment for Short Audio** (1.05-1.1x speedup)
   - Skip segmentation overhead
   - Only for <10 second clips

### Not Worth Implementing

❌ **speed_up parameter**: Doesn't exist in current whisper.cpp
❌ **Vulkan backend**: Too unstable, requires GPU drivers
❌ **Aggressive audio_ctx reduction**: Causes hallucinations

### Bottom Line

**CPU affinity is the game-changer.** By using only 2-3 performance cores instead of 8 mixed cores, we can potentially achieve **2-3x speedup**, bringing RTF from 3.06x down to **~1.0x (real-time)**.

Combined with OpenMP and minor optimizations, we could reach **0.8-1.0x RTF**, making whisper.cpp viable for **real-time transcription** on mid-range devices.
