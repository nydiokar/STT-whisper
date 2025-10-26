# STT Notes App — Front-End MVP Spec (LLM‑ready)

## 1) Objective

Build a minimal, fast UI to capture speech‑to‑text notes, store them locally, and manage them. Related to MainActivity, not IME. 

## 2) Scope (MVP)

* Screens: **History**, **Settings**.
* Global action: **New Note** (floating action) → **bottom sheet** recorder/editor.
* Search in History (inline). No filters yet.
* Per‑note actions: **Edit**, **Delete**, **Share**. **Tag** shown but disabled (stub).
* Theme: **dark/cosmic**, WCAG AA contrast.

## 3) Information Architecture

* **Sidebar (collapsed by default)**: icon rail with two entries → History, Settings. Toggle via top‑left button.
* **Top bar**: logo (left or center), sidebar toggle (left), search field appears only on History.
* **Floating New Note button**: fixed bottom‑right.

## 4) Components

### 4.1 Sidebar

* Collapsible; default: slim icon rail.
* Items: History, Settings.

### 4.2 Top Bar

* Left: sidebar toggle.
* Center/Left: Logo.
* Right: reserved for future icons (do not render in MVP).

### 4.3 History Screen

* Layout: search field at top; list below.
* List item (collapsed): title (first 80 chars), one‑liner preview (first line), createdAt (relative time).
* List item (expanded inline): full text + action row.
* Action row: **Edit**, **Delete**, **Share**; **Tag** button visible but disabled.

### 4.4 New Note Bottom Sheet

* Opens over current screen from bottom; height 60–70%.
* Elements: title (editable, default first 80 chars), live text area, controls row.
* Controls: **Mic toggle**, **Pause/Resume**, **Stop/Save**, **Discard**.
* Behavior: autosave draft every 2s; on Save → persist and close; on Discard → confirm then close.

### 4.5 Settings Screen

* Items: theme toggle (dark default), microphone permission helper, storage path/info, About/Help link.

### 4.6 Toasts/Dialogs

* Save success: "Saved to History".
* Delete confirm dialog; post‑delete undo toast (5s window).

## 5) Interactions & Flows

### 5.1 Create Note (Dictation)

1. User taps Floating New Note → bottom sheet opens.
2. User toggles Mic; transcription streams into text area.
3. User edits text inline if needed.
4. User taps Save → note persisted; sheet closes; toast shown; History list updates with new item at top.

### 5.2 Edit Note

1. In expanded History item, user taps Edit.
2. Text becomes editable; controls: Save, Cancel.
3. Save updates `text` and `updatedAt`; list reflects changes.

### 5.3 Delete Note

1. User taps Delete.
2. Confirm dialog → Delete.
3. Item removed; undo toast 5s.

### 5.4 Share Note

* System share of full text (plain text payload).

### 5.5 Search

* Inline search field filters list by substring on `text`, debounced 200ms.

### 5.6 Sidebar Toggle

* Top‑left button toggles collapsed/expanded.

## 6) Data Model

```ts
type Note = {
  id: string // uuid
  createdAt: number // epoch ms
  updatedAt: number // epoch ms
  source: 'stt' | 'manual'
  text: string
  charCount: number // compute on save
  durationSec?: number // present if source==='stt'
  // tags?: string[] // reserved for future
}
```

* Sort order: `createdAt` desc.
* Search: simple substring over `text`.

## 7) Accessibility & Input

* Keyboard: Ctrl/Cmd+N open New; Esc close sheet; Ctrl/Cmd+F focus search; Ctrl/Cmd+S save in edit.
* Focus trap inside bottom sheet; ARIA labels for all controls; 44px minimum hit targets.

## 8) Performance

* Virtualize list when items > 200.
* Debounce search 200ms.
* Autosave drafts every 2s in sheet; recover unsaved draft on reopen if crash/close.

## 9) Styling

* Dark theme; high‑contrast text; readable line length; system font stack.
* Bottom sheet: rounded top corners; shadow elevation; no full‑screen in MVP.

## 10) Non‑Goals (defer)

* Filters/sorting beyond date.
* Tags/labels UI and storage.
* Semantic search, summaries, topic grouping.
* Cloud sync, multi‑device.
* Bulk actions, pin/favorite.

## 11) Rollout Roadmap

* **v1.0 (MVP):** Everything above.
* **v1.1:** Pin and Copy actions; sort toggle Date ↑/↓; JSON import/export.
* **v1.2:** Add `labels: string[]`, `language`, `confidence`; enable Tag; filters panel; sort by `charCount`.
* **v2.0:** Summaries, semantic search, clustering, reminders.
