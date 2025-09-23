## Current System Flow (Simplified)

This diagram shows how audio flows and gets processed in the two different modes **as the code is currently written**.

**Key Points:**

1.  **Two Modes:** `Session` (records all, then transcribes) and `Continuous` (transcribes chunks in real-time for UI).
2.  **Centralized Files:** All output (WAV + JSON) goes into `~/.voice_input_service/data/transcripts/YYYY-MM-DD/`.
3.  **Output Format & Process:**
    *   **Continuous Mode (UI):** Transcribes audio chunks incrementally to update the UI text box in real-time.
    *   **Saving (Both Modes):** When stopping, the *full* session audio is transcribed **once** to generate the final, consistent output files: `session_HH-MM-SS.json` (with session info + accurate segments relative to the WAV) and `session_HH-MM-SS.wav` (full audio).
4.  **Incremental UI:** Continuous mode **does** update the text box incrementally as you speak.

```ascii
+-------+      +---------------+      +-----------------------+      +-----------------+      +-------+
| Mic   |----->| AudioRecorder |----->| VoiceInputService     |----->| Transcription   |----->| Files |
+-------+      +---------------+      | (Manages Modes & UI)  |      | Engine          |<-----|       |
                                      +-----------+-----------+      +-----------------+      +-------+
                                                  |                                             ^ (JSON+WAV)
                                                  |                                             |
      /-------------------------------------------+------------------------------------------\
     /                                            |                                            \
+----|------------+                          +----|----------------+                          +---------+
| Session Mode    |                          | Continuous Mode     |                          | UI Text |
| (Record->Stop)  |                          | (Real-time Chunks)  |                          +----+----+
+-----------------+                          +----------+----------+                               ^
     |                                                  |                                           | Incremental
     |                                                  V                                           | Update
     | Audio                                +--------------------+      +-----------------+         |
     | (Full Buffer)                        | TranscriptionWorker|----->| Transcription   |---------+
     |                                      | (VAD, Buffering)   |      | Engine (Chunks) |
     V                                      +--------------------+      +-----------------+
+--------------------------------------------------------------------------------------------------+
| STOP RECORDING (Common Logic)                                                                    |
| 1. Get Full Audio from Recorder                                                                  |
| 2. Transcribe FULL audio using Engine (Result = Full Text + Segments, Saves session.wav)         |
| 3. Create session.json (Session Info + Segments List)                                            |
| 4. Save session.json and session.wav via TranscriptManager                                       |
| 5. Update UI with final full text *only if different* from incrementally displayed text         |
+--------------------------------------------------------------------------------------------------+

```
