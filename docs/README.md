# Voice Input STT - Documentation

**Last Updated:** 2025-10-26

## 📖 Start Here

- **[PROJECT_STATUS.md](PROJECT_STATUS.md)** - **READ THIS FIRST** - Current state, what works, what's next

## 📱 Android Implementation

- **[FRONTEND_SPECS_SIMPLIFIED.md](android/FRONTEND_SPECS_SIMPLIFIED.md)** - Simplified MainActivity plan (next phase)
- **[TESTING_REPORT_2025_10_26.md](android/TESTING_REPORT_2025_10_26.md)** - Real-world testing findings & solutions

## 📂 Folder Structure

```
docs/
├── PROJECT_STATUS.md          ← Main status doc (start here!)
├── README.md                  ← This file
├── android/
│   ├── FRONTEND_SPECS_SIMPLIFIED.md  ← Next: MainActivity
│   ├── TESTING_REPORT_2025_10_26.md  ← Test findings
│   └── archive/               ← Completed implementation docs
├── archive/                   ← Historical/completed docs
│   └── 2025-10-26/           ← Old status docs, completed phases
├── core/                      ← Core architecture docs
└── desktop/                   ← Desktop implementation (legacy)
```

## 🎯 Current Status (Quick Summary)

**Version:** 1.2 (Full App MVP)

**What Works:**
- ✅ Voice keyboard (IME) in any app
- ✅ Dual modes (TAP/HOLD)
- ✅ Smart formatting (emails, URLs)
- ✅ Custom vocabulary (personalized corrections)
- ✅ Space + Backspace buttons
- ✅ Settings (inline + app)
- ✅ **MainActivity with note history**
- ✅ **RecorderActivity for standalone recording**
- ✅ **Auto-save from IME**

**Next Steps:**
- Enhance MainActivity (search, edit, share)
- OR test different Whisper models
- OR add voice commands

See [PROJECT_STATUS.md](PROJECT_STATUS.md) for complete details.

## 📚 Historical Docs

Old docs are in `archive/` - kept for reference but no longer actively maintained.
