# Quick Start Guide - Android Port

> **For the next person (or you) picking up this project**

## 🎯 What is this?

We're porting the Voice Input Service (a Windows voice-to-text app) to Android as a system-wide keyboard (IME). The desktop version has **superior** hallucination filtering and VAD that doesn't exist in other Android voice keyboards.

## 📂 Repository Structure

```
STT/
├── android/              # Android app (NEW - in progress)
├── desktop/              # Original Python Windows app (REFERENCE)
├── docs/
│   ├── core/            # Shared algorithm docs
│   ├── desktop/         # Desktop-specific docs
│   └── android/         # Android-specific docs
├── ANDROID_PORT_PLAN.md # FULL execution plan (READ THIS)
└── QUICKSTART.md        # This file
```

## 🚦 Current Status

Check `ANDROID_PORT_PLAN.md` → "Progress Tracking" table to see current phase.

**If Phase 0 is not started**: Begin with repository restructuring
**If Phase 1 is in progress**: Continue with TextProcessor port
**If Phase 2+**: Check the phase checklist in the main plan

## ⚡ Starting a New Session

### 1. Get Oriented (5 min)

```bash
cd STT
git status                          # See what's been done
cat ANDROID_PORT_PLAN.md | head -50 # Review the plan
```

**Find current phase:**
- Open `ANDROID_PORT_PLAN.md`
- Search for "Progress Tracking" table
- Find first ⬜ or 🟡 phase
- Go to that phase's section

### 2. Set Up Environment (10 min)

**If working on Python/Desktop:**
```bash
cd desktop
source .venv/bin/activate  # or .venv\Scripts\activate on Windows
```

**If working on Android:**
```bash
cd android
# Open in Android Studio
# File → Open → select STT/android
# Wait for Gradle sync
```

### 3. Find Your Task (5 min)

**Current phase checklist is in**: `ANDROID_PORT_PLAN.md` → Your Phase → "Implementation checklist"

**Example**: If in Phase 1 (Core Logic):
- Look for: `## Phase 1: Core Logic MVP`
- Find unchecked `[ ]` items
- Start with the first one

### 4. Code with Reference (varies)

**Porting a component?**
1. Open Python source: `desktop/voice_input_service/[path]`
2. Open target Kotlin file: `android/app/src/main/kotlin/[path]`
3. Follow component mapping in `ANDROID_PORT_PLAN.md`

**Example**:
```bash
# Reference
code desktop/voice_input_service/utils/text_processor.py

# Target
code android/app/src/main/kotlin/com/voiceinput/core/TextProcessor.kt
```

## 🗺️ Component Quick Map

| Need to port | Look at Python file | Create Kotlin file | Effort |
|--------------|---------------------|-------------------|--------|
| Text filtering | `desktop/voice_input_service/utils/text_processor.py` | `android/.../core/TextProcessor.kt` | 2-3h |
| Config | `desktop/voice_input_service/config.py` | `android/.../config/AppConfig.kt` | 2h |
| VAD/Silence | `desktop/voice_input_service/utils/silence_detection.py` | `android/.../core/SileroVAD.kt` | 4-6h |
| Audio recording | `desktop/voice_input_service/core/audio.py` | `android/.../core/AudioRecorder.kt` | 4-6h |
| Whisper engine | `desktop/voice_input_service/core/transcription.py` | `android/.../core/WhisperEngine.kt` | 6-8h |
| Audio processor | `desktop/voice_input_service/core/processing.py` | `android/.../core/AudioProcessor.kt` | 6-8h |

**Full mapping**: See `ANDROID_PORT_PLAN.md` → "Core Component Porting Guide"

## 🎯 Phase Overview (What to Expect)

**Phase 0** (2-3h): Restructure repo, create Android project
**Phase 1** (6-8h): Port TextProcessor + Config, prove it works
**Phase 2** (12-16h): Audio recording + Whisper transcription
**Phase 3** (6-8h): Add Silero VAD for silence detection
**Phase 4** (8-10h): Wire everything together
**Phase 5** (16-20h): Build the IME (keyboard)
**Phase 6** (8-10h): Settings UI and polish
**Phase 7** (8-10h): Testing and optimization

**Total estimate**: 66-83 hours (~2-3 weeks full-time)

## 🔧 Key Commands

### Desktop (Python)
```bash
cd desktop
python -m pytest                    # Run tests
python -m voice_input_service       # Run app
```

### Android
```bash
cd android
./gradlew build                     # Build app
./gradlew test                      # Run unit tests
./gradlew installDebug             # Install on device
```

### Git
```bash
git status                          # Check progress
git add -p                          # Stage changes interactively
git commit -m "feat(android): ..."  # Commit with conventional commits
git push                            # Push to remote
```

## 📝 Updating Progress

**After completing a task:**

1. Open `ANDROID_PORT_PLAN.md`
2. Find the checklist item
3. Change `[ ]` to `[x]`
4. Update "Progress Tracking" table if phase complete
5. Commit:
   ```bash
   git add ANDROID_PORT_PLAN.md
   git commit -m "docs: update progress - completed [task name]"
   ```

**After completing a phase:**

1. Update phase status: ⬜ → ✅
2. Add completion date
3. Add any notes/learnings
4. Commit progress update

## 🆘 Troubleshooting

**"I don't know where to start"**
→ Go to Phase 0 in `ANDROID_PORT_PLAN.md`

**"The Python code is confusing"**
→ Check `docs/core/` for algorithm explanations
→ Look at unit tests in `desktop/tests/`

**"Android build failing"**
→ Sync Gradle: File → Sync Project with Gradle Files
→ Check `android/build.gradle.kts` dependencies

**"Don't understand the porting approach"**
→ Read `ANDROID_PORT_PLAN.md` → "Core Component Porting Guide"
→ See the tier system (Tier 1 = easy, Tier 3 = hard)

**"Stuck on a component"**
→ Check component checklist in main plan
→ Look for Android equivalent in plan
→ Search for examples in existing Android voice keyboard projects

## 💡 Pro Tips

1. **Don't rush** - The Python code is your specification. Understand it first.
2. **Test as you go** - Write unit tests for each ported component
3. **Commit frequently** - Small commits with clear messages
4. **Use real device** - Audio features behave differently on emulator
5. **Read the logs** - Android Studio logcat is your friend
6. **IME is tricky** - Requires device restart to activate after install

## 🎯 First-Time Contributor Path

**Never touched this project before? Start here:**

1. **Read**: `README.md` (root) - Understand what the app does
2. **Read**: `desktop/README.md` - See the desktop version
3. **Read**: `ANDROID_PORT_PLAN.md` → "Component Mapping" - See what needs porting
4. **Read**: `docs/core/text-processing.md` - Understand the core algorithm (if exists)
5. **Start**: Phase 1, Task 1 - Port TextProcessor.kt
6. **Test**: Write unit tests to prove it works
7. **Celebrate**: You just contributed the most important component!

## 📚 Essential Reading Order

1. This file (QUICKSTART.md) - You are here ✓
2. `ANDROID_PORT_PLAN.md` - The master plan
3. `docs/core/` - Algorithm documentation (once created)
4. `desktop/` Python code - Reference implementation
5. Android docs - InputMethodService, AudioRecord, etc.

## 🚀 Next Actions Template

**Copy this when starting a session:**

```markdown
## Session [Date]

**Phase**: [Current phase number and name]
**Goal**: [What you want to achieve this session]

**Tasks**:
- [ ] [Specific task 1]
- [ ] [Specific task 2]
- [ ] [Specific task 3]

**Blockers**: [Any issues preventing progress]

**Progress**: [What you completed]

**Next session**: [Where to start next time]
```

---

**Remember**: The desktop version is GOLD. We're bringing that quality to Android. Take your time, do it right.

**Questions?** Check the main plan. Still stuck? Document your blocker in the plan and move to another task.

**Good luck! 🚀**