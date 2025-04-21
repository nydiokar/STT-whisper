from __future__ import annotations
import os
import time
import logging
import threading
import whisper
import tkinter as tk
from tkinter import messagebox
from typing import Optional, List, Dict, Any, Callable

from voice_input_service.ui.dialogs import ModelSelectionDialog, DownloadProgressDialog
from voice_input_service.core.transcription import TranscriptionEngine
from voice_input_service.core.config import Config

class ModelManager:
    """Manages Whisper model selection, verification, and downloading."""
    
    def __init__(self, ui_root: tk.Tk, config: Config):
        """Initialize the model manager.
        
        Args:
            ui_root: The root UI window for displaying dialogs
            config: The application configuration
        """
        self.logger = logging.getLogger("VoiceService.ModelManager")
        self.ui_root = ui_root
        self.config = config
        self.cache_dir = os.path.join(os.path.expanduser("~"), ".cache", "whisper")
    
    def initialize_transcription_engine(self) -> Optional[TranscriptionEngine]:
        """Initialize transcription engine with proper model handling.
        
        Returns:
            Optional[TranscriptionEngine]: Initialized transcription engine
        """
        model_name = self.config.transcription.model_name
        device = self.config.transcription.device
        language = self.config.transcription.language
        use_cpp = self.config.transcription.use_cpp
        whisper_cpp_path = self.config.transcription.whisper_cpp_path
        ggml_model_path = self.config.transcription.ggml_model_path
        
        # If using whisper.cpp, check for the model file directly
        if use_cpp:
            self.logger.info(f"Initializing whisper.cpp transcription engine")
            
            # If a specific GGML model file is specified, use that
            if ggml_model_path and os.path.exists(ggml_model_path):
                model_file_name = os.path.basename(ggml_model_path)
                self.logger.info(f"Using specified GGML model file: {model_file_name}")
                
                # Initialize engine with whisper.cpp options
                engine = TranscriptionEngine(
                    model_name=model_name,
                    device=device,
                    language=language,
                    use_cpp=use_cpp,
                    whisper_cpp_path=whisper_cpp_path
                )
                
                # Set the specific model file
                engine.set_model_file(ggml_model_path)
                
                return engine
            
            # Check for model file in models directory
            models_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "models")
            ggml_models = []
            
            self.logger.info(f"Scanning for GGML models in {models_dir}")
            for filename in os.listdir(models_dir):
                if filename.endswith(".bin") and filename.startswith("ggml-"):
                    ggml_models.append(os.path.join(models_dir, filename))
                    self.logger.debug(f"Found GGML model: {filename}")
            
            if not ggml_models:
                self.logger.warning("No GGML model files found. Please download models from https://huggingface.co/ggerganov/whisper.cpp")
                return self._handle_missing_model(model_name)
                
            # Look for model matching the requested model name
            matching_models = [m for m in ggml_models if os.path.basename(m).startswith(f"ggml-{model_name}")]
            
            if not matching_models:
                self.logger.warning(f"No GGML model file found for '{model_name}'. Using first available model instead.")
                # Prompt user to select a model
                return self._handle_ggml_model_selection(ggml_models)
                
            # Select the best matching model
            selected_model = None
            language_suffix = f".{language}" if language != "en" else ".en"
            
            # First look for language-specific models
            lang_models = [m for m in matching_models if language_suffix in os.path.basename(m)]
            if lang_models:
                # Prefer higher quality models (sort order is generally correct for quality)
                selected_model = sorted(lang_models)[-1]
            else:
                # No language match, use any matching model
                selected_model = sorted(matching_models)[-1]
                
            selected_model_name = os.path.basename(selected_model)
            self.logger.info(f"Auto-selected GGML model: {selected_model_name}")
            
            # Update the config with the selected model
            self.config.transcription.ggml_model_path = selected_model
            self.config.save()
            
            # Initialize engine with whisper.cpp options
            engine = TranscriptionEngine(
                model_name=model_name,
                device=device,
                language=language,
                use_cpp=use_cpp,
                whisper_cpp_path=whisper_cpp_path
            )
            
            # Set the specific model file
            engine.set_model_file(selected_model)
            
            return engine
        
        # Standard Python Whisper model handling
        self.logger.info(f"Initializing Python Whisper with model '{model_name}'")
        
        # Check for any file that starts with the model name
        model_files = [f for f in os.listdir(self.cache_dir) if f.startswith(f"{model_name}") and f.endswith(".pt")]
        
        if not model_files:
            self.logger.warning(f"Model '{model_name}' is not available locally. Opening model selection dialog.")
            return self._handle_missing_model(model_name)
            
        # Use the first matching model file
        actual_model_file = model_files[0]
        self.logger.info(f"Found Python Whisper model file: {actual_model_file}")
            
        # Model exists, create engine and load it
        self.logger.info(f"Initializing transcription engine with Python Whisper")
        engine = TranscriptionEngine(
            model_name=model_name,
            device=device,
            language=language,
            use_cpp=use_cpp,
            whisper_cpp_path=whisper_cpp_path
        )
        
        try:
            engine._load_model()
            self.logger.info(f"Python Whisper model loaded successfully")
            return engine
        except Exception as e:
            self.logger.error(f"Error loading Python Whisper model: {e}")
            return self._handle_missing_model(model_name)
    
    def _handle_missing_model(self, model_name: str) -> Optional[TranscriptionEngine]:
        """Handle case when a model is missing.
        
        Args:
            model_name: Name of the missing model
            
        Returns:
            Optional[TranscriptionEngine]: Initialized engine if model was selected and downloaded
        """
        # Get currently available models
        available_models = self.get_available_models()
        
        # Create result container to store the engine
        result_container = {"engine": None}
        
        # Create callbacks for the dialog
        def on_download(selected_model: str) -> None:
            result_container["engine"] = self._download_model(selected_model)
            
        def on_select(selected_model: str) -> None:
            result_container["engine"] = self._select_model(selected_model)
        
        # Show model selection dialog
        dialog = ModelSelectionDialog(
            parent=self.ui_root,
            model_name=model_name,
            available_models=available_models,
            on_download=on_download,
            on_select=on_select,
            cache_dir=self.cache_dir
        )
        
        # Wait for dialog to close
        self.ui_root.wait_window(dialog.dialog)
        
        # Return the engine (may be None if user cancelled)
        return result_container["engine"]
    
    def get_available_models(self) -> List[str]:
        """Get list of available Whisper models in the cache.
        
        Returns:
            List of available model names
        """
        available_models = []
        for model in ["tiny", "base", "small", "medium", "large"]:
            if os.path.exists(os.path.join(self.cache_dir, f"{model}.pt")):
                available_models.append(model)
        return available_models
    
    def _download_model(self, model_name: str) -> Optional[TranscriptionEngine]:
        """Download a model.
        
        Args:
            model_name: Name of the model to download
            
        Returns:
            Optional[TranscriptionEngine]: Initialized engine if download successful
        """
        # Get model size info for confirmation
        model_sizes = {
            "tiny": "~39MB",
            "base": "~142MB",
            "small": "~466MB",
            "medium": "~1.5GB",
            "large": "~3GB",
            "turbo": "~1.5GB"
        }
        
        size = model_sizes.get(model_name, "unknown size")
        
        # Ask for confirmation before downloading
        if not messagebox.askyesno(
            "Download Model",
            f"Do you want to download the {model_name} model ({size})?\n\n"
            f"This will download the model to your local cache and may take some time "
            f"depending on your internet connection."
        ):
            self.logger.info("Model download cancelled by user")
            return None
            
        self.logger.info(f"Starting download of model: {model_name}")
        
        # Show simple download dialog
        download_dialog = tk.Toplevel(self.ui_root)
        download_dialog.title("Downloading Model")
        download_dialog.geometry("300x100")
        download_dialog.transient(self.ui_root)
        download_dialog.grab_set()
        
        # Center the dialog
        download_dialog.geometry("+%d+%d" % (
            self.ui_root.winfo_x() + self.ui_root.winfo_width()//2 - 150,
            self.ui_root.winfo_y() + self.ui_root.winfo_height()//2 - 50
        ))
        
        label = tk.Label(
            download_dialog, 
            text=f"Downloading {model_name} model...\nThis may take a few minutes.",
            pady=20
        )
        label.pack()
        
        try:
            # Download model using whisper's built-in downloader
            whisper.load_model(
                model_name,
                device="cpu",  # Use CPU for download
                download_root=None,  # Use default cache
                in_memory=False  # Don't load into memory yet
            )
            
            self.logger.info(f"Model {model_name} downloaded successfully")
            
            # Close download dialog
            download_dialog.destroy()
            
            # Show completion message
            messagebox.showinfo(
                "Download Complete",
                f"The {model_name} model has been downloaded successfully!"
            )
            
            # Update config
            self.config.transcription.model_name = model_name
            self.config.save()
            
            # Create the transcriber
            engine = TranscriptionEngine(
                model_name=model_name,
                device=self.config.transcription.device,
                language=self.config.transcription.language
            )
            
            self.logger.info(f"Transcription engine initialized with model: {model_name}")
            return engine
            
        except Exception as e:
            self.logger.error(f"Error downloading model: {e}")
            download_dialog.destroy()
            messagebox.showerror(
                "Download Error",
                f"Failed to download model: {e}"
            )
            return None
    
    def _select_model(self, model_name: str) -> TranscriptionEngine:
        """Select an existing model.
        
        Args:
            model_name: Name of the model to select
            
        Returns:
            TranscriptionEngine: Initialized transcription engine
        """
        self.logger.info(f"Selecting model: {model_name}")
        
        # Update config
        self.config.transcription.model_name = model_name
        self.config.save()
        
        # Create the transcriber
        engine = TranscriptionEngine(
            model_name=model_name,
            device=self.config.transcription.device,
            language=self.config.transcription.language,
            use_cpp=self.config.transcription.use_cpp,
            whisper_cpp_path=self.config.transcription.whisper_cpp_path
        )
        
        self.logger.info(f"Transcription engine initialized with model: {model_name}")
        return engine

    def check_available_models(self) -> Dict[str, Dict[str, Any]]:
        """Check which Whisper models are available in the cache.
        
        Returns:
            Dictionary mapping model names to information about each model
        """
        self.logger.info(f"Checking for models in {self.cache_dir}")
        
        results = {}
        for model_name in ["tiny", "base", "small", "medium", "large"]:
            model_path = os.path.join(self.cache_dir, f"{model_name}.pt")
            if os.path.exists(model_path):
                size_bytes = os.path.getsize(model_path)
                size_mb = size_bytes / (1024 * 1024)
                results[model_name] = {
                    "available": True,
                    "path": model_path,
                    "size_mb": round(size_mb, 2),
                    "size_bytes": size_bytes
                }
                self.logger.info(f"Found model '{model_name}': {round(size_mb, 2)} MB")
            else:
                results[model_name] = {"available": False}
                self.logger.info(f"Model '{model_name}' not found")
        
        return results

    def _handle_ggml_model_selection(self, model_files: List[str]) -> Optional[TranscriptionEngine]:
        """Handle selection of a specific GGML model file.
        
        Args:
            model_files: List of available GGML model files
            
        Returns:
            Optional[TranscriptionEngine]: Initialized engine if model was selected
        """
        # Format model names for display
        model_options = []
        for model_path in model_files:
            filename = os.path.basename(model_path)
            size_mb = os.path.getsize(model_path) / (1024 * 1024)
            model_options.append(f"{filename} ({size_mb:.1f} MB)")
            
        # Show a dialog to select a model
        from tkinter import StringVar
        
        selection_var = StringVar(value="")
        
        def on_model_select():
            selection = selection_var.get()
            if selection:
                # Extract the filename from the selection (remove size info)
                filename = selection.split(" (")[0]
                # Find the matching model path
                for model_path in model_files:
                    if os.path.basename(model_path) == filename:
                        self.logger.info(f"Selected GGML model: {filename}")
                        
                        # Update config with selected model
                        self.config.transcription.ggml_model_path = model_path
                        self.config.save()
                        
                        # Initialize engine with the selected model
                        engine = TranscriptionEngine(
                            model_name="custom",  # Will be updated by set_model_file
                            device=self.config.transcription.device,
                            language=self.config.transcription.language,
                            use_cpp=True,
                            whisper_cpp_path=self.config.transcription.whisper_cpp_path
                        )
                        
                        # Set the specific model file
                        engine.set_model_file(model_path)
                        
                        # Store the result and close the dialog
                        result_container["engine"] = engine
                        dialog.destroy()
                        return
                        
            # No selection or model not found
            dialog.destroy()
            
        # Create a dialog for model selection
        dialog = tk.Toplevel(self.ui_root)
        dialog.title("Select GGML Model")
        dialog.geometry("500x400")
        dialog.transient(self.ui_root)
        dialog.grab_set()
        
        frame = tk.Frame(dialog, padx=10, pady=10)
        frame.pack(fill=tk.BOTH, expand=True)
        
        label = tk.Label(frame, text="Select a GGML model file to use with whisper.cpp:", pady=10)
        label.pack(anchor="w")
        
        # Create a listbox with scrollbar
        list_frame = tk.Frame(frame)
        list_frame.pack(fill=tk.BOTH, expand=True)
        
        scrollbar = tk.Scrollbar(list_frame)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        listbox = tk.Listbox(list_frame, selectmode=tk.SINGLE, yscrollcommand=scrollbar.set)
        listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        scrollbar.config(command=listbox.yview)
        
        # Add models to listbox
        for option in model_options:
            listbox.insert(tk.END, option)
            
        # Set up selection handler
        def on_select(event):
            if listbox.curselection():
                index = listbox.curselection()[0]
                selection_var.set(listbox.get(index))
                
        listbox.bind('<<ListboxSelect>>', on_select)
        
        # Buttons
        button_frame = tk.Frame(frame)
        button_frame.pack(fill=tk.X, pady=10)
        
        ok_button = tk.Button(button_frame, text="OK", command=on_model_select)
        ok_button.pack(side=tk.RIGHT, padx=5)
        
        cancel_button = tk.Button(button_frame, text="Cancel", command=dialog.destroy)
        cancel_button.pack(side=tk.RIGHT, padx=5)
        
        # Result container
        result_container = {"engine": None}
        
        # Wait for dialog to close
        self.ui_root.wait_window(dialog)
        
        return result_container["engine"]