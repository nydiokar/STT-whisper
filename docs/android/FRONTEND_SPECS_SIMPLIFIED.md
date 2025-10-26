# STT Notes App — Simplified Front-End MVP Spec

**Status:** Recommended simplified version for faster implementation
**Date:** 2025-10-26
**Related:** See `FRONTEND_SPECS.md` for full-featured version

---

## 1) Why Simplify?

The full spec in `FRONTEND_SPECS.md` is comprehensive but requires significant development time. This simplified version focuses on **core value** with **50% less development effort**.

### Changes from Full Spec:
- ❌ No sidebar navigation → ✅ Simple tab layout
- ❌ No bottom sheet recorder → ✅ Full-screen recorder activity
- ❌ No search in MVP → ✅ Add in v1.1
- ✅ Keep Edit/Delete/Share (core functionality)
- ✅ Keep dark theme and accessibility

**Estimated Time:**
- Full spec: ~1 week
- Simplified: ~2-3 days

---

## 2) Scope (Simplified MVP)

* Screens: **History** (main), **Recorder** (full-screen), **Settings** (existing)
* Actions: **New Note** → launches full-screen recorder
* Per-note: **Edit**, **Delete**, **Share** (Tag disabled/stub)
* Theme: **dark/cosmic** (match IME), WCAG AA contrast
* **No search** (defer to v1.1)
* **No sidebar** (simpler navigation)

---

## 3) Information Architecture

### Screen Flow:
```
MainActivity (History List)
    ↓ FAB click
RecorderActivity (Full Screen)
    ↓ Save
Back to History (new note at top)
```

### Navigation:
```
┌────────────────────────────┐
│  Voice Notes    [⚙️ Menu]  │  ← Simple top bar
├────────────────────────────┤
│  Note 1 preview...         │
│  Note 2 preview...         │
│  Note 3 preview...         │
│                            │
│             [+] FAB        │  ← New note button
└────────────────────────────┘
```

---

## 4) Components

### 4.1 History Screen (MainActivity)

**Layout:**
- **Top Bar:** Title "Voice Notes", Settings icon (right)
- **List:** RecyclerView with notes (chronological, newest first)
- **FAB:** Bottom-right floating "+" button

**List Item (Collapsed):**
```
┌────────────────────────────────────┐
│ First 60 chars of text...          │
│ Oct 26, 2025 • 3:42 PM             │
└────────────────────────────────────┘
```

**List Item (Expanded - click to expand):**
```
┌────────────────────────────────────┐
│ Full text content here, can be     │
│ multiple lines showing the entire  │
│ transcription...                   │
│                                    │
│ Oct 26, 2025 • 3:42 PM             │
│ 124 chars • 8 sec                  │
│                                    │
│ [Edit] [Delete] [Share] [Tag 🔒]  │
└────────────────────────────────────┘
```

### 4.2 Recorder Activity (Full Screen - Not Bottom Sheet)

**Simpler than bottom sheet:**
- No overlay complexity
- Standard activity lifecycle
- Easier back button handling
- No height constraints

**Layout:**
```
┌────────────────────────────────────┐
│  [<] New Voice Note                │  ← Top bar with back
├────────────────────────────────────┤
│                                    │
│  Transcribed text appears here     │  ← Live text area
│  as you speak...                   │     (editable)
│                                    │
│                                    │
├────────────────────────────────────┤
│                                    │
│          🎤 [60dp]                 │  ← Mic button
│      Ready to speak                │     (same as IME)
│                                    │
│  ━━▃▅▇█▇▅▃━━━▃▅▇▅▃━━              │  ← Visualizer
│                                    │
│                                    │
│  [Cancel]              [Save]      │  ← Actions
└────────────────────────────────────┘
```

**States:**
- **Idle:** Mic green, "Tap to start recording"
- **Recording:** Mic red, visualizer animating, timer
- **Processing:** Mic orange, pulsing, "Processing..."
- **Ready to save:** Text visible, Save button enabled

**Behavior:**
- Opens when FAB clicked
- Uses same recording logic as IME (reuse components!)
- Autosave draft every 2s (survive crashes)
- Back button → confirm discard if unsaved
- Save → persist to storage, return to History

### 4.3 Settings Menu (Top-Right Icon)

**Simple menu popup:**
```
┌─────────────────────┐
│ ⚙️ App Settings     │
│ 📖 Help & Guide     │
│ ℹ️  About           │
└─────────────────────┘
```

Opens existing `SettingsActivity`.

### 4.4 Dialogs

**Delete Confirmation:**
```
┌─────────────────────────────┐
│ Delete this note?           │
│                             │
│ This cannot be undone.      │
│                             │
│   [Cancel]     [Delete]     │
└─────────────────────────────┘
```

**Discard Changes:**
```
┌─────────────────────────────┐
│ Discard unsaved note?       │
│                             │
│   [Keep Editing]  [Discard] │
└─────────────────────────────┘
```

---

## 5) Interactions & Flows

### 5.1 Create Note (Simplified)

1. User taps FAB → `RecorderActivity` launches
2. User taps mic → recording starts (same as IME)
3. Text streams into text area (live transcription)
4. User taps Save → note persists, activity closes
5. Returns to History → new note at top

**Differences from Full Spec:**
- ✅ Full screen (not bottom sheet) - simpler
- ✅ Standard activity (easier lifecycle)
- ✅ No height constraints

### 5.2 Edit Note

1. Tap note in list → expands inline
2. Tap "Edit" → text becomes editable
3. Edit text directly (keyboard appears)
4. Auto-saves on focus loss

**Or:**
1. Long-press note → opens in `RecorderActivity`
2. Edit text + re-record if needed
3. Save → updates note

### 5.3 Delete Note

1. Tap "Delete" in expanded item
2. Confirm dialog → "Delete"
3. Item removed from list
4. Optional: Undo toast (5s) - defer to v1.1

### 5.4 Share Note

Standard Android share sheet with plain text.

---

## 6) Data Model

**Same as full spec:**

```kotlin
data class Note(
    val id: String,              // UUID
    val createdAt: Long,         // epoch ms
    val updatedAt: Long,         // epoch ms
    val source: String,          // "stt" or "manual"
    val text: String,
    val charCount: Int,          // computed
    val durationSec: Int? = null // if source == "stt"
)
```

**Storage:**
- Use `SharedPreferences` (JSON array) for MVP
- Or Room database if you prefer type-safety
- Sort: `createdAt DESC`

---

## 7) Removed from Full Spec (Defer to Later)

### Not in Simplified MVP:

❌ **Search** - Defer to v1.1
- Reason: Additional UI complexity
- Can add later as simple filter

❌ **Sidebar Navigation** - Not needed
- Reason: Only 2 screens (History + Settings)
- Simple top-right menu is enough

❌ **Bottom Sheet Recorder** - Use full activity
- Reason: Simpler implementation
- Full screen is more intuitive

❌ **Undo after Delete** - Show confirmation only
- Reason: Additional state management
- Can add in v1.1

❌ **Keyboard Shortcuts** - Mobile-first
- Reason: Android users don't expect Ctrl+N
- Can add if desktop version planned

❌ **Virtualization** - Not needed yet
- Reason: Only triggers at 200+ notes
- Add when performance becomes issue

---

## 8) Styling (Same as Full Spec)

- **Dark theme** - Match IME cosmos gradient
- **High contrast** - WCAG AA compliant
- **System font** - Roboto/default
- **44dp minimum touch targets**
- **Rounded corners** - 8dp (cards), 4dp (buttons)

**Colors** (from IME):
- Background: `#0f0f1e` (dark cosmos)
- Cards: `#1a1a2e` (slightly lighter)
- Text: `#e0e0e0` (light gray)
- Accent: `#4CAF50` (green - ready state)
- Error: `#f44336` (red)

---

## 9) Implementation Phases

### Phase 1: Data Layer (Day 1 - 4 hours)

- [ ] Create `Note` data class
- [ ] Create `NotesRepository` (SharedPreferences or Room)
- [ ] Implement CRUD operations (Create, Read, Update, Delete)
- [ ] Write unit tests

### Phase 2: History Screen (Day 1-2 - 6 hours)

- [ ] Create `MainActivity` with RecyclerView
- [ ] Create `NoteAdapter` with ViewHolder
- [ ] Implement expand/collapse on click
- [ ] Add Edit/Delete/Share buttons (expanded state)
- [ ] Add FAB for new note

### Phase 3: Recorder Activity (Day 2 - 6 hours)

- [ ] Create `RecorderActivity` layout
- [ ] Reuse `VoiceKeyboardView` or extract shared components
- [ ] Implement recording → transcription flow
- [ ] Add autosave (draft recovery)
- [ ] Handle back button (confirm discard)

### Phase 4: Polish (Day 3 - 4 hours)

- [ ] Add delete confirmation dialog
- [ ] Implement share functionality
- [ ] Add empty state ("No notes yet")
- [ ] Test on device
- [ ] Fix bugs

**Total:** ~20 hours (~2.5 days)

---

## 10) Comparison: Full vs Simplified

| Feature | Full Spec | Simplified | Time Saved |
|---------|-----------|------------|------------|
| Navigation | Sidebar | Top menu | 4 hours |
| Recorder | Bottom sheet | Full activity | 6 hours |
| Search | Inline filter | Deferred | 4 hours |
| Undo delete | Toast + restore | Confirm only | 2 hours |
| Keyboard shortcuts | Full support | None | 2 hours |
| Virtualization | At 200+ items | Deferred | 2 hours |
| **Total** | ~40 hours | ~20 hours | **50% savings** |

---

## 11) Future Enhancements (Post-MVP)

Once simplified MVP works:

### v1.1 (Add Convenience)
- Search/filter notes
- Undo after delete (toast)
- Sort options (date, length)
- Copy note text

### v1.2 (Add Features)
- Tags/labels support
- Multi-select + bulk actions
- Pin/favorite notes
- Export to file (JSON/CSV)

### v1.3 (Add Intelligence)
- Note summaries (LLM)
- Topic clustering
- Semantic search
- Reminders/notifications

---

## 12) Why This is Better for MVP

### Advantages of Simplified Approach:

✅ **Faster to build** - 2-3 days vs 1 week
✅ **Easier to test** - Fewer edge cases
✅ **Less code to maintain** - Simpler architecture
✅ **Clearer UX** - Full-screen is more intuitive than bottom sheet
✅ **Lower risk** - Standard patterns, proven components

### What You Still Get:

✅ Voice note recording (core value!)
✅ History with edit/delete/share
✅ Dark theme matching IME
✅ Persistent storage
✅ Production-quality code

### What You Defer (Can Add Later):

⏸️ Search (nice-to-have)
⏸️ Advanced navigation (not needed for 2 screens)
⏸️ Fancy bottom sheet (full screen is simpler)

---

## 13) Technical Notes

### Reuse from IME:

You can **extract shared components** to avoid duplication:

```kotlin
// Shared between IME and RecorderActivity
class VoiceRecorder(context: Context) {
    private val audioRecorder: AudioRecorder
    private val whisperEngine: WhisperEngine

    suspend fun startRecording() { ... }
    suspend fun stopRecording(): String { ... }
    fun cancelRecording() { ... }
}
```

This way:
- IME uses `VoiceRecorder` for text insertion
- `RecorderActivity` uses `VoiceRecorder` for note creation
- Same code, same behavior, less bugs!

---

## 14) Success Criteria

Simplified MVP is complete when:

- ✅ Can create voice notes via recording
- ✅ Can view all notes in chronological list
- ✅ Can edit existing notes (text only)
- ✅ Can delete notes (with confirmation)
- ✅ Can share notes (plain text)
- ✅ Notes persist across app restarts
- ✅ UI matches IME theme (cosmos dark)
- ✅ No crashes in normal usage

---

## 15) Recommendation

**Build the simplified version first**, then iterate:

1. **Day 1-3:** Build simplified MVP (~20 hours)
2. **Day 4:** Test with real usage
3. **Day 5+:** Add features from full spec if needed

This approach:
- Gets you a working app faster
- Validates if the feature is useful
- Allows user feedback before over-investing
- Keeps scope manageable

**If the simplified version proves valuable**, you can always add:
- Search (4 hours)
- Bottom sheet (6 hours)
- Sidebar (4 hours)
- etc.

But you'll have learned what users actually need first!

---

**Conclusion:** The simplified spec delivers 80% of the value with 50% of the effort. Perfect for an MVP! 🚀
