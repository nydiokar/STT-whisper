# IME Settings UX Design

**Date:** 2025-10-26
**Problem:** Current ⚙️ button opens full SettingsActivity app - jarring UX
**Goal:** Seamless settings without leaving keyboard context

---

## 🚨 The Problem

### Current Behavior (BAD UX):
```
User in Notes app, typing
   ↓
Switches to Voice Keyboard
   ↓
Clicks ⚙️ Settings button
   ↓
😱 FULL APP OPENS (SettingsActivity)
   ↓
User pulled out of Notes entirely!
   ↓
Must switch back to Notes manually
```

**Issues:**
- Breaks user flow completely
- Unexpected app switch
- Loses context (Notes → Settings App → back to Notes)
- Feels like a bug, not a feature

---

## ✅ The Solution: In-Keyboard Settings

### Approach A: Inline Settings Panel (RECOMMENDED) ⭐

**Concept:** Settings expand within the keyboard itself

```
READY STATE:
┌──────────────────────────────────────┐
│         Ready to speak               │
│  🟢  ━━━━━━━━━━━━━━━━━━━━━━━━       │
│              MODE:                   │
│      [  TAP  ] [  HOLD  ]           │
│     Cancel   ⚙️                      │ ← Click this
└──────────────────────────────────────┘

SETTINGS EXPANDED (in-keyboard):
┌──────────────────────────────────────┐
│         ⚙️ Settings                  │
├──────────────────────────────────────┤
│  Default Mode:     [TAP ▼]          │ ← Dropdown
│  Language:         [English ▼]       │ ← Dropdown
│  Sensitivity:      ━●━━━━━          │ ← Slider
│  Haptic Feedback:  [✓]               │ ← Toggle
├──────────────────────────────────────┤
│  [Advanced Settings] [Close]         │
└──────────────────────────────────────┘
                ↓ If clicked
     Opens SettingsActivity for advanced options
```

**Benefits:**
- No app switching
- Quick access to common settings
- Stays in keyboard context
- Can still access advanced settings if needed

---

### Approach B: Settings Dialog (ALTERNATIVE)

**Concept:** Modal dialog overlay on current app

```
Notes app visible underneath
        ↓
    [DIMMED OVERLAY]
        ↓
┌──────────────────────────────────────┐
│         Voice Keyboard Settings      │
├──────────────────────────────────────┤
│  □ Tap Mode (default)                │
│  □ Hold Mode                         │
│                                      │
│  Language: [English ▼]               │
│                                      │
│  Visualizer Sensitivity:             │
│  ━━━●━━━━━ (Medium)                  │
│                                      │
│  [Advanced Settings]  [Close]        │
└──────────────────────────────────────┘
```

**Benefits:**
- Familiar Android pattern
- More space for options
- Clear visual separation

**Drawbacks:**
- Requires dialog permission
- Slightly more complex

---

### Approach C: Hybrid (BEST UX) 🌟

**Concept:** Quick settings in keyboard, link to app for advanced

**Quick Settings (In-Keyboard):**
```
┌──────────────────────────────────────┐
│         ⚙️ Quick Settings            │
├──────────────────────────────────────┤
│  Default Mode:     [TAP ▼]          │
│  Haptic:           [ON ▼]            │
│  Sensitivity:      ━●━━━             │
│                                      │
│  More options...   [×]               │
└──────────────────────────────────────┘
         ↑
    Tappable row
```

**Clicking "More options..." or ⚙️ long-press:**
- Shows toast: "Full settings available in Voice Input app"
- Option to open app appears in notification shade
- **OR** small "Open App" button at bottom

---

## 🎨 Recommended Implementation

### Phase 1: Inline Quick Settings (This Week)

**What to Include:**
1. **Default Mode** - Dropdown: TAP / HOLD
2. **Haptic Feedback** - Toggle: ON / OFF
3. **Visualizer Sensitivity** - Slider: Low / Medium / High

**UI:**
- Expand/collapse with animation
- Save preferences immediately (SharedPreferences)
- "×" Close button returns to normal keyboard

**Code Structure:**
```kotlin
class VoiceKeyboardView(context: Context) : LinearLayout(context) {

    private val settingsPanel: LinearLayout
    private var settingsExpanded = false

    fun toggleSettings() {
        if (settingsExpanded) {
            collapseSettings()
        } else {
            expandSettings()
        }
    }

    private fun expandSettings() {
        // Animate slide-down
        settingsPanel.visibility = View.VISIBLE
        // Hide main UI elements
        microphoneButton.visibility = View.GONE
        // ...
    }

    private fun collapseSettings() {
        // Animate slide-up
        settingsPanel.visibility = View.GONE
        // Restore main UI
        microphoneButton.visibility = View.VISIBLE
    }
}
```

---

### Phase 2: Advanced Settings (Later)

**When Needed:**
- Language selection (if multiple models)
- Model selection (tiny/base/small)
- Recording duration limits
- Audio quality settings
- Theme customization

**Access:**
- "Advanced..." button in quick settings
- Opens SettingsActivity but with clear UX:
  - Toast: "Opening settings app..."
  - User expects the transition
  - Easy back button to return

---

## 📋 Settings to Implement

### Must Have (Quick Settings):
1. ✅ **Default Mode** - TAP or HOLD (persist)
2. ✅ **Haptic Feedback** - ON/OFF toggle
3. ✅ **Sensitivity** - Low/Medium/High slider

### Nice to Have (Quick Settings):
4. 🟡 **Auto-Insert** - ON/OFF (insert on success vs preview)
5. 🟡 **Timer Display** - Show/Hide recording time

### Advanced (Full App):
6. 🔵 **Language** - If multiple models available
7. 🔵 **Model Size** - tiny/base/small (if available)
8. 🔵 **Max Duration** - 30s/60s/unlimited
9. 🔵 **Visualizer Colors** - Custom theme
10. 🔵 **Debug Mode** - Logs, performance stats

---

## 🔧 Implementation Plan

### Step 1: Preferences System (2 hours)

**Create:**
```kotlin
// File: PreferencesManager.kt
class PreferencesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(
        "voice_ime_prefs",
        Context.MODE_PRIVATE
    )

    var defaultMode: InputMode
        get() = InputMode.valueOf(
            prefs.getString("default_mode", "TAP") ?: "TAP"
        )
        set(value) = prefs.edit().putString("default_mode", value.name).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()

    var visualizerSensitivity: Float
        get() = prefs.getFloat("visualizer_sensitivity", 0.5f)
        set(value) = prefs.edit().putFloat("visualizer_sensitivity", value).apply()
}

enum class InputMode { TAP, HOLD }
```

### Step 2: Quick Settings UI (4 hours)

**Layout:** `res/layout/voice_keyboard_settings_panel.xml`
```xml
<LinearLayout
    android:id="@+id/settingsPanel"
    android:visibility="gone"
    android:background="@drawable/cosmos_gradient"
    android:padding="16dp">

    <TextView text="⚙️ Settings" />

    <Spinner android:id="@+id/modeSpinner" />
    <Switch android:id="@+id/hapticSwitch" />
    <SeekBar android:id="@+id/sensitivitySlider" />

    <Button text="Close" />
</LinearLayout>
```

**Logic:**
- Toggle visibility on ⚙️ click
- Save changes immediately
- Apply settings to current session
- Smooth animations

### Step 3: Apply Settings (2 hours)

**In VoiceInputIME:**
```kotlin
override fun onCreate() {
    super.onCreate()
    val prefs = PreferencesManager(this)

    // Apply saved preferences
    isTapMode = (prefs.defaultMode == InputMode.TAP)
    // ...
}
```

**In VoiceKeyboardView:**
```kotlin
fun applySensitivity(level: Float) {
    audioVisualizer.setSensitivity(level)
}
```

---

## 🎯 User Flow (Final Design)

### Quick Settings Flow:
```
User typing in WhatsApp
   ↓
Switches to Voice Keyboard (sees familiar UI)
   ↓
Clicks ⚙️ button
   ↓
Settings panel slides down (still in keyboard)
   ↓
Changes "Default Mode" to HOLD
   ↓
Clicks "×" or taps outside
   ↓
Settings slide up, saved automatically
   ↓
Back to voice keyboard (WhatsApp still in view)
```

**No app switching! Seamless experience!** ✅

---

### Advanced Settings Flow (When Needed):
```
User wants to change language
   ↓
Opens quick settings
   ↓
Sees "Language: English" (grayed out)
   ↓
Small text: "More options in app"
   ↓
Clicks "Advanced..."
   ↓
Toast: "Opening Voice Input app..."
   ↓
SettingsActivity opens
   ↓
User knows they left keyboard (expected)
   ↓
Makes changes, presses back
   ↓
Returns to previous app
```

**Clear expectation, user not surprised!** ✅

---

## 🚫 What NOT to Do

### ❌ Bad Patterns:
1. **Opening full app without warning** - Current behavior
2. **No visual feedback** - Silent transitions
3. **Too many options in keyboard** - Cluttered UI
4. **Modal dialogs without context** - Confusing
5. **No way to access advanced settings** - Limited functionality

### ✅ Good Patterns:
1. **Inline quick settings** - Stay in context
2. **Clear visual transitions** - Animations
3. **Essential options only** - Focus
4. **Clear path to advanced** - When needed
5. **Immediate saves** - No "Apply" button

---

## 📱 Button Redesign

### Current Layout:
```
[Cancel]   🔄 Tap   ⚙️
```

### Proposed Layout Option 1:
```
[Cancel]   ⚙️   ℹ️
            ↑    ↑
       Settings Help/App
```

### Proposed Layout Option 2:
```
       ⚙️      ℹ️
    Settings   App
[Cancel] shown only when recording
```

### Proposed Layout Option 3 (RECOMMENDED):
```
MODE:
[TAP] [HOLD]

     ⚙️    (corner, smaller, subtle)
```
- Settings subtle but accessible
- Main focus on mode buttons
- ℹ️ help button? Or remove entirely?

---

## 🎨 Visual Mockup (Expanded Settings)

```
┌──────────────────────────────────────┐
│         ⚙️ Settings                  │
│                                      │
│  Default Input Mode                  │
│  ┌────────────────────────────────┐  │
│  │  TAP           HOLD        ▼  │  │ Dropdown
│  └────────────────────────────────┘  │
│                                      │
│  Haptic Feedback                     │
│  ━━━━━━━━━━━━━━━━━━━ [✓] ON        │ Toggle
│                                      │
│  Visualizer Sensitivity              │
│  Low  ━━━●━━━━━  High               │ Slider
│                                      │
│  [ Advanced Settings ]  [ × Close ]  │
└──────────────────────────────────────┘
```

---

## ✅ Implementation Checklist

### Week 1: Inline Settings
- [ ] Create `PreferencesManager.kt`
- [ ] Design settings panel layout XML
- [ ] Add expand/collapse animations
- [ ] Implement mode dropdown/toggle
- [ ] Implement haptic toggle
- [ ] Implement sensitivity slider
- [ ] Save preferences on change
- [ ] Load preferences on IME start
- [ ] Test settings persistence

### Week 2: Polish & Advanced
- [ ] Redesign button layout (subtle ⚙️)
- [ ] Add "Advanced Settings" button
- [ ] Create proper SettingsActivity UI
- [ ] Add toast/notification for app opening
- [ ] Test full flow in multiple apps
- [ ] Handle edge cases (permissions, errors)

---

## 🎯 Success Criteria

Settings implementation is complete when:
- ✅ User can change mode without leaving keyboard
- ✅ Changes persist across sessions
- ✅ No app switching for common settings
- ✅ Clear path to advanced settings
- ✅ Smooth animations and transitions
- ✅ Works reliably in all tested apps

---

**Recommended Next Step:** Implement Approach C (Hybrid) - Quick settings inline, advanced in app

**Estimated Time:** 1-2 days for full implementation
