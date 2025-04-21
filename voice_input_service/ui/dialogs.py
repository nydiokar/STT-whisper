from __future__ import annotations
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import os
import logging
import threading
import time
from typing import Optional, Dict, Any, List, Callable

from voice_input_service.config import Config

class ModelSelectionDialog:
    """Dialog for model selection when a model is missing."""
    
    def __init__(self, parent: tk.Tk, model_name: str, available_models: List[str], 
                  on_download: Callable[[str], None],
                  on_select: Callable[[str], None],
                  cache_dir: Optional[str] = None):
        """Initialize the model selection dialog.
        
        Args:
            parent: Parent window
            model_name: Name of the missing model
            available_models: List of available models
            on_download: Callback for downloading model
            on_select: Callback for selecting existing model
            cache_dir: Optional cache directory
        """
        self.logger = logging.getLogger("VoiceService.UI")
        self.parent = parent
        self.model_name = model_name
        self.available_models = available_models
        self.on_download = on_download
        self.on_select = on_select
        self.cache_dir = cache_dir
        
        # Model sizes and info
        self.model_info = {
            "tiny": {"size": "~39MB", "accuracy": "lowest", "speed": "fastest"},
            "base": {"size": "~142MB", "accuracy": "low", "speed": "very fast"}, 
            "small": {"size": "~466MB", "accuracy": "medium", "speed": "fast"},
            "medium": {"size": "~1.5GB", "accuracy": "good", "speed": "moderate"},
            "large": {"size": "~3GB", "accuracy": "best", "speed": "slow"},
            "turbo": {"size": "~1.5GB", "accuracy": "best", "speed": "fastest"}
        }
        
        # Create dialog
        self.dialog = tk.Toplevel(parent)
        self.dialog.title("Model Selection")
        self.dialog.geometry("500x400")
        self.dialog.resizable(False, False)
        
        # Make dialog modal
        self.dialog.transient(parent)
        self.dialog.grab_set()
        self.dialog.focus_set()
        
        # Initialize selection variable
        self.selection = tk.StringVar(value="download")
        self.selected_model = tk.StringVar(value=model_name)
        
        # Center the dialog
        self.dialog.update_idletasks()
        width = self.dialog.winfo_width()
        height = self.dialog.winfo_height()
        x = (parent.winfo_rootx() + parent.winfo_width() // 2) - (width // 2)
        y = (parent.winfo_rooty() + parent.winfo_height() // 2) - (height // 2)
        self.dialog.geometry(f'+{x}+{y}')
        
        # Prevent clicking on parent window
        self.dialog.grab_set()
        
        # Setup UI
        self._setup_ui()
        
        # Make dialog modal and handle window close button
        self.dialog.protocol("WM_DELETE_WINDOW", self._on_cancel)
        self.dialog.bind("<Escape>", lambda e: self._on_cancel())
        
        # Force focus and wait for window
        self.dialog.focus_force()
        self.dialog.wait_visibility()
        self.dialog.lift()
        
    def _setup_ui(self) -> None:
        """Set up the dialog UI."""
        # Main frame with padding
        main_frame = ttk.Frame(self.dialog, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Header
        header = ttk.Label(
            main_frame, 
            text=f"Model '{self.model_name}' Not Available",
            font=("Segoe UI", 12, "bold")
        )
        header.pack(pady=(0, 10))
        
        # Message
        message = ttk.Label(
            main_frame,
            text=(f"The requested model '{self.model_name}' is not available locally. "
                  f"Please choose one of the options below:"),
            wraplength=480,
            justify="left"
        )
        message.pack(fill=tk.X, pady=(0, 10))
        
        # Options frame
        options_frame = ttk.LabelFrame(main_frame, text="Options", padding="10")
        options_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 10))
        
        # Option 1: Download requested model
        download_frame = ttk.Frame(options_frame)
        download_frame.pack(fill=tk.X, pady=5)
        
        download_radio = ttk.Radiobutton(
            download_frame, 
            text=f"Download {self.model_name} model", 
            value="download",
            variable=self.selection,
            command=lambda: self.selected_model.set(self.model_name)
        )
        download_radio.pack(side=tk.LEFT)
        
        download_info = ttk.Label(
            download_frame,
            text=f"({self.model_info.get(self.model_name, {}).get('size', 'unknown')} - "
                 f"{self.model_info.get(self.model_name, {}).get('accuracy', 'unknown')} accuracy, " 
                 f"{self.model_info.get(self.model_name, {}).get('speed', 'unknown')} speed)",
            foreground="gray"
        )
        download_info.pack(side=tk.LEFT, padx=5)
        
        # Option 2: Use existing model
        if self.available_models:
            existing_label = ttk.Label(
                options_frame,
                text="Use an existing model:",
                justify="left"
            )
            existing_label.pack(fill=tk.X, pady=(10, 5), anchor="w")
            
            for model in sorted(self.available_models):
                model_frame = ttk.Frame(options_frame)
                model_frame.pack(fill=tk.X, pady=2)
                
                model_radio = ttk.Radiobutton(
                    model_frame, 
                    text=f"{model}", 
                    value=f"select_{model}",
                    variable=self.selection,
                    command=lambda m=model: self.selected_model.set(m)
                )
                model_radio.pack(side=tk.LEFT)
                
                model_info = ttk.Label(
                    model_frame,
                    text=f"({self.model_info.get(model, {}).get('size', 'unknown')} - "
                         f"{self.model_info.get(model, {}).get('accuracy', 'unknown')} accuracy, " 
                         f"{self.model_info.get(model, {}).get('speed', 'unknown')} speed)",
                    foreground="gray"
                )
                model_info.pack(side=tk.LEFT, padx=5)
        
        # Option 3: Exit
        exit_radio = ttk.Radiobutton(
            options_frame, 
            text="Exit and modify configuration manually", 
            value="exit",
            variable=self.selection
        )
        exit_radio.pack(fill=tk.X, pady=(10, 5), anchor="w")
        
        # Buttons frame
        button_frame = ttk.Frame(main_frame)
        button_frame.pack(fill=tk.X, pady=(10, 0))
        
        ok_button = ttk.Button(button_frame, text="OK", command=self._on_ok, default="active")
        ok_button.pack(side=tk.RIGHT, padx=(5, 0))
        self.dialog.bind("<Return>", lambda e: self._on_ok())
        
        cancel_button = ttk.Button(button_frame, text="Cancel", command=self._on_cancel)
        cancel_button.pack(side=tk.RIGHT)
        
        # Set initial focus to the OK button
        ok_button.focus_set()
        
    def _on_ok(self) -> None:
        """Handle OK button click."""
        choice = self.selection.get()
        selected_model = self.selected_model.get()
        
        if choice == "download":
            self.logger.info(f"User chose to download model: {selected_model}")
            self.dialog.destroy()
            self.on_download(selected_model)
        elif choice == "exit":
            self.logger.info("User chose to exit and modify configuration manually")
            self.dialog.destroy()
            messagebox.showinfo(
                "Exit",
                "Please modify the configuration file and restart the application."
            )
            self.parent.destroy()
        elif choice.startswith("select_"):
            model = selected_model
            self.logger.info(f"User chose to use existing model: {model}")
            self.dialog.destroy()
            self.on_select(model)
        else:
            self.logger.warning(f"Invalid selection: {choice}")
    
    def _on_cancel(self) -> None:
        """Handle Cancel button click."""
        self.logger.info("User cancelled model selection")
        self.dialog.destroy()
        messagebox.showinfo(
            "Exit",
            "Cannot proceed without a valid model. The application will exit."
        )
        self.parent.destroy()

class DownloadProgressDialog:
    """Dialog for showing model download progress."""
    
    def __init__(self, parent: tk.Tk, model_name: str):
        """Initialize the download progress dialog.
        
        Args:
            parent: Parent window
            model_name: Name of the model being downloaded
        """
        self.logger = logging.getLogger("VoiceService.UI")
        self.parent = parent
        self.model_name = model_name
        self.on_cancel = None  # Callback for when dialog is closed
        
        # Model sizes for progress estimation
        self.model_sizes = {
            "tiny": 39 * 1024 * 1024,       # 39 MB
            "base": 142 * 1024 * 1024,      # 142 MB
            "small": 466 * 1024 * 1024,     # 466 MB
            "medium": 1.5 * 1024 * 1024 * 1024,  # 1.5 GB
            "large": 3 * 1024 * 1024 * 1024,     # 3 GB
            "turbo": 1.5 * 1024 * 1024 * 1024,  # 1.5 GB
        }
        
        # Create dialog
        self.dialog = tk.Toplevel(parent)
        self.dialog.title("Downloading Model")
        self.dialog.geometry("400x150")
        self.dialog.resizable(False, False)
        self.dialog.transient(parent)
        self.dialog.grab_set()
        
        # Center the dialog
        self.dialog.update_idletasks()
        width = self.dialog.winfo_width()
        height = self.dialog.winfo_height()
        x = (parent.winfo_rootx() + parent.winfo_width() // 2) - (width // 2)
        y = (parent.winfo_rooty() + parent.winfo_height() // 2) - (height // 2)
        self.dialog.geometry(f'+{x}+{y}')
        
        # Queue for thread-safe updates
        self._update_queue = []
        self._is_closing = False
        
        self._setup_ui()
        
        # Make dialog modal and handle window close button
        self.dialog.protocol("WM_DELETE_WINDOW", self._handle_close)
        self.dialog.bind("<Escape>", lambda e: self._handle_close())
        
        # Start update checker
        self._check_updates()
        
    def _setup_ui(self) -> None:
        """Set up the dialog UI."""
        # Main frame with padding
        main_frame = ttk.Frame(self.dialog, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Header
        header = ttk.Label(
            main_frame, 
            text=f"Downloading {self.model_name} Model",
            font=("Segoe UI", 12, "bold")
        )
        header.pack(pady=(0, 10))
        
        # Status message
        self.status_var = tk.StringVar(value="Initializing download...")
        status = ttk.Label(main_frame, textvariable=self.status_var)
        status.pack(fill=tk.X, pady=(0, 10))
        
        # Progress bar
        self.progress_var = tk.DoubleVar(value=0)
        progress = ttk.Progressbar(
            main_frame, 
            orient="horizontal", 
            length=380, 
            mode="determinate",
            variable=self.progress_var
        )
        progress.pack(fill=tk.X, pady=(0, 10))
        
        # Estimated size and time label
        self.size_var = tk.StringVar(value="")
        size_label = ttk.Label(main_frame, textvariable=self.size_var)
        size_label.pack(fill=tk.X)
        
        # Cancel button
        cancel_button = ttk.Button(main_frame, text="Cancel", command=self._handle_close)
        cancel_button.pack(pady=(10, 0))
        
        # Calculate estimated size
        estimated_size = self.model_sizes.get(self.model_name, 0)
        if estimated_size > 0:
            if estimated_size >= 1024 * 1024 * 1024:
                size_str = f"Estimated size: {estimated_size / (1024 * 1024 * 1024):.1f} GB"
            else:
                size_str = f"Estimated size: {estimated_size / (1024 * 1024):.1f} MB"
            self.size_var.set(size_str)
    
    def _check_updates(self) -> None:
        """Check for queued updates and apply them in the main thread."""
        if not self._is_closing and self._update_queue:
            progress, message = self._update_queue.pop(0)
            try:
                self.progress_var.set(progress)
                if message:
                    self.status_var.set(message)
                self.dialog.update()
            except Exception as e:
                self.logger.error(f"Error updating progress dialog: {e}")
        
        # Schedule next check if dialog still exists
        if not self._is_closing and self.dialog.winfo_exists():
            self.dialog.after(50, self._check_updates)
    
    def _handle_close(self) -> None:
        """Handle dialog close request."""
        if self.on_cancel:
            self.on_cancel()
        else:
            self.close()
    
    def update_progress(self, progress: float, message: str = None) -> None:
        """Update the progress bar.
        
        Args:
            progress: Progress value (0-100)
            message: Optional status message
        """
        if not self._is_closing:
            self._update_queue.append((progress, message))
    
    def close(self) -> None:
        """Close the dialog."""
        self._is_closing = True
        try:
            if self.dialog.winfo_exists():
                self.dialog.destroy()
        except Exception as e:
            self.logger.error(f"Error closing progress dialog: {e}")

class SettingsDialog:
    """Dialog for application settings."""
    
    def __init__(self, parent: tk.Tk, config: 'Config', on_save: callable = None):
        """Initialize the settings dialog.
        
        Args:
            parent: Parent window
            config: Application configuration
            on_save: Callback to call when settings are saved
        """
        self.logger = logging.getLogger("VoiceService.SettingsDialog")
        self.parent = parent
        self.config = config
        self.on_save = on_save
        
        # Create a new toplevel window
        self.dialog = tk.Toplevel(parent)
        self.dialog.title("Settings")
        self.dialog.geometry("550x500")
        self.dialog.transient(parent)
        self.dialog.grab_set()
        
        # Set up variables
        self.use_cpp_var = tk.BooleanVar(value=config.transcription.use_cpp)
        self.whisper_cpp_path_var = tk.StringVar(value=config.transcription.whisper_cpp_path)
        self.ggml_model_var = tk.StringVar(value=config.transcription.ggml_model_path or "")
        
        # Collect available GGML models
        self.ggml_models = self._find_ggml_models()
        
        # Set up UI
        self._setup_ui()
        
    def _find_ggml_models(self) -> List[str]:
        """Find available GGML model files.
        
        Returns:
            List of full paths to GGML model files
        """
        models = []
        
        # Check models directory
        try:
            # Use absolute project path to models directory
            models_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "models")
            self.logger.debug(f"Looking for GGML models in: {models_dir}")
            
            if os.path.exists(models_dir):
                for filename in os.listdir(models_dir):
                    if filename.endswith(".bin") and filename.startswith("ggml-"):
                        full_path = os.path.join(models_dir, filename)
                        file_size_mb = os.path.getsize(full_path) / (1024 * 1024)
                        self.logger.debug(f"Found GGML model: {filename} ({file_size_mb:.1f} MB)")
                        models.append(os.path.join(models_dir, filename))
        except Exception as e:
            self.logger.error(f"Error finding GGML models: {e}")
            
        return models
        
    def _setup_ui(self):
        """Set up the dialog UI."""
        notebook = ttk.Notebook(self.dialog)
        notebook.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # Transcription tab
        transcription_frame = ttk.Frame(notebook, padding="10")
        notebook.add(transcription_frame, text="Transcription")
        
        # Engine selection
        engine_frame = ttk.LabelFrame(transcription_frame, text="Speech Recognition Engine", padding="10")
        engine_frame.pack(fill=tk.X, pady=5)
        
        # Whisper.cpp option
        use_cpp_check = ttk.Checkbutton(
            engine_frame, 
            text="Use whisper.cpp (faster, lower resource usage)",
            variable=self.use_cpp_var,
            command=self._toggle_cpp_options
        )
        use_cpp_check.pack(anchor="w", pady=5)
        
        # Whisper.cpp options frame
        self.cpp_options_frame = ttk.Frame(engine_frame)
        self.cpp_options_frame.pack(fill=tk.X, pady=5)
        
        # GGML Model selection
        model_frame = ttk.Frame(self.cpp_options_frame)
        model_frame.pack(fill=tk.X, pady=5)
        
        model_label = ttk.Label(model_frame, text="GGML Model:")
        model_label.pack(side=tk.LEFT, padx=5)
        
        # Create model dropdown
        model_options = []
        for model_path in self.ggml_models:
            filename = os.path.basename(model_path)
            size_mb = os.path.getsize(model_path) / (1024 * 1024)
            option_text = f"{filename} ({size_mb:.1f} MB)"
            model_options.append((option_text, model_path))
        
        # Add a special "Auto-select best model" option
        model_options.insert(0, ("Auto-select best model", ""))
        
        # Create variable to track selected model display text
        self.model_display_var = tk.StringVar()
        
        # Set initial display value
        if self.ggml_model_var.get():
            for display_text, path in model_options:
                if path == self.ggml_model_var.get():
                    self.model_display_var.set(display_text)
                    break
        else:
            self.model_display_var.set(model_options[0][0])
        
        # Create the dropdown
        model_dropdown = ttk.Combobox(
            model_frame,
            textvariable=self.model_display_var,
            values=[display_text for display_text, _ in model_options],
            width=40,
            state="readonly"
        )
        model_dropdown.pack(side=tk.LEFT, padx=5, fill=tk.X, expand=True)
        
        # Handle selection changes
        def on_model_select(event):
            selection = self.model_display_var.get()
            for display_text, path in model_options:
                if display_text == selection:
                    self.ggml_model_var.set(path)
                    break
                    
        model_dropdown.bind("<<ComboboxSelected>>", on_model_select)
        
        # Whisper.cpp path
        path_frame = ttk.Frame(self.cpp_options_frame)
        path_frame.pack(fill=tk.X, pady=5)
        
        path_label = ttk.Label(path_frame, text="whisper.cpp executable path:")
        path_label.pack(side=tk.LEFT, padx=5)
        
        path_entry = ttk.Entry(path_frame, textvariable=self.whisper_cpp_path_var, width=30)
        path_entry.pack(side=tk.LEFT, padx=5, expand=True, fill=tk.X)
        
        browse_button = ttk.Button(path_frame, text="Browse...", command=self._browse_executable)
        browse_button.pack(side=tk.RIGHT, padx=5)
        
        # Disclaimer
        disclaimer = ttk.Label(
            engine_frame, 
            text="Note: Using whisper.cpp requires downloading the GGML model files (.bin) "
                 "and the whisper.cpp executable. See https://github.com/ggml-org/whisper.cpp",
            wraplength=400,
            justify="left",
            foreground="gray"
        )
        disclaimer.pack(fill=tk.X, pady=10)
        
        # Model information
        info_frame = ttk.LabelFrame(transcription_frame, text="GGML Model Information", padding="10")
        info_frame.pack(fill=tk.X, pady=10)
        
        info_text = tk.Text(info_frame, height=6, wrap=tk.WORD)
        info_text.pack(fill=tk.X, expand=True)
        
        info_text.insert(tk.END, 
            "Available GGML model variants:\n\n"
            "- Base models: ggml-{size}.bin (e.g., ggml-base.bin)\n"
            "- English-only models: ggml-{size}.en.bin (e.g., ggml-base.en.bin)\n" 
            "- Quantized models: ggml-{size}-q5_1.bin (e.g., ggml-base-q5_1.bin)\n\n"
            "Quantized models (q5_1, q8_0) are smaller and faster with minimal quality loss."
        )
        info_text.config(state=tk.DISABLED)
        
        # Buttons
        button_frame = ttk.Frame(self.dialog)
        button_frame.pack(fill=tk.X, padx=10, pady=10)
        
        save_button = ttk.Button(button_frame, text="Save", command=self._on_save)
        save_button.pack(side=tk.RIGHT, padx=5)
        
        cancel_button = ttk.Button(button_frame, text="Cancel", command=self._on_cancel)
        cancel_button.pack(side=tk.RIGHT, padx=5)
        
        # Initial state
        self._toggle_cpp_options()
        
    def _toggle_cpp_options(self):
        """Toggle visibility of whisper.cpp options based on checkbox."""
        if self.use_cpp_var.get():
            self.cpp_options_frame.pack(fill=tk.X, pady=5)
        else:
            self.cpp_options_frame.pack_forget()
        
    def _browse_executable(self):
        """Browse for whisper.cpp executable."""
        file_path = filedialog.askopenfilename(
            title="Select whisper.cpp executable",
            filetypes=[
                ("Executable files", "*.exe"),
                ("All files", "*.*")
            ]
        )
        if file_path:
            self.whisper_cpp_path_var.set(file_path)
            
    def _on_save(self):
        """Save settings and close dialog."""
        # Update config
        self.config.transcription.use_cpp = self.use_cpp_var.get()
        self.config.transcription.whisper_cpp_path = self.whisper_cpp_path_var.get()
        self.config.transcription.ggml_model_path = self.ggml_model_var.get()
        
        # Save to file
        self.config.save()
        
        # Call callback if provided
        if self.on_save:
            self.on_save()
            
        self.logger.info("Settings saved")
        self.dialog.destroy()
        
    def _on_cancel(self):
        """Cancel and close dialog."""
        self.dialog.destroy() 