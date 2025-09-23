#!/usr/bin/env python3
"""
Enhanced VAD (Voice Activity Detection) Demo

This example script demonstrates the enhanced VAD capabilities 
in the Voice Input Service. It will use the selected VAD backend
to detect speech, buffer it, and transcribe it continuously.

Usage:
    python examples/vad_demo.py --vad-mode webrtc --model base
"""

import argparse
import sys
import os
import time
import logging
import tkinter as tk
from tkinter import ttk, scrolledtext

# Add parent directory to path for imports
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from voice_input_service import config
from voice_input_service.core.audio import AudioRecorder
from voice_input_service.core.transcription import TranscriptionEngine
from voice_input_service.core.processing import TranscriptionWorker

class VADDemoApp:
    """Simple demo app to showcase enhanced VAD capabilities."""
    
    def __init__(self, vad_mode="basic", model_name="base", use_cpp=True):
        """Initialize the VAD demo application."""
        self.root = tk.Tk()
        self.root.title("Enhanced VAD Demo")
        self.root.geometry("800x600")
        
        # Load configuration
        self.cfg = config.Config()
        
        # Initialize components
        self._setup_logging()
        self._setup_ui()
        
        # Override config with arguments
        self.cfg.audio.vad_mode = vad_mode
        self.cfg.transcription.model_name = model_name
        self.cfg.transcription.use_cpp = use_cpp
        
        # Initialize transcription engine
        self.transcriber = TranscriptionEngine(
            model_name=self.cfg.transcription.model_name,
            language=self.cfg.transcription.language,
            use_cpp=self.cfg.transcription.use_cpp,
            whisper_cpp_path=self.cfg.transcription.whisper_cpp_path
        )
        
        # Initialize audio recorder
        self.recorder = AudioRecorder(
            sample_rate=self.cfg.audio.sample_rate,
            channels=self.cfg.audio.channels,
            chunk_size=self.cfg.audio.chunk_size,
            on_data_callback=self._on_audio_data
        )
        
        # Initialize VAD worker
        self.worker = TranscriptionWorker(
            process_func=self._process_audio,
            on_result=self._on_transcription_result,
            min_audio_length=self.cfg.audio.min_audio_length,
            vad_mode=self.cfg.audio.vad_mode,
            vad_aggressiveness=self.cfg.audio.vad_aggressiveness,
            vad_threshold=self.cfg.audio.vad_threshold,
            silence_duration=self.cfg.audio.silence_duration,
            sample_rate=self.cfg.audio.sample_rate
        )
        
        # Set up clean exit
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        
        # Flag for recording state
        self.recording = False
        
        # Log status
        self.logger.info(f"Initialized VAD Demo with mode: {vad_mode}")
        self.logger.info(f"Using model: {model_name} (use_cpp: {use_cpp})")
        
    def _setup_logging(self):
        """Set up logging for the application."""
        self.logger = logging.getLogger("VADDemo")
        self.logger.setLevel(logging.INFO)
        
        # Create console handler
        ch = logging.StreamHandler()
        ch.setLevel(logging.INFO)
        
        # Create formatter
        formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
        ch.setFormatter(formatter)
        
        # Add handler to logger
        self.logger.addHandler(ch)
    
    def _setup_ui(self):
        """Set up the user interface."""
        # Create main frame
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Create control frame
        control_frame = ttk.Frame(main_frame)
        control_frame.pack(fill=tk.X, pady=5)
        
        # Create start/stop button
        self.toggle_button = ttk.Button(
            control_frame, 
            text="Start Recording", 
            command=self._toggle_recording
        )
        self.toggle_button.pack(side=tk.LEFT, padx=5)
        
        # Create clear button
        self.clear_button = ttk.Button(
            control_frame,
            text="Clear Transcript",
            command=self._clear_transcript
        )
        self.clear_button.pack(side=tk.LEFT, padx=5)
        
        # Create status label
        self.status_label = ttk.Label(
            control_frame,
            text="Status: Idle"
        )
        self.status_label.pack(side=tk.RIGHT, padx=5)
        
        # Create VAD info frame
        vad_frame = ttk.LabelFrame(main_frame, text="VAD Settings")
        vad_frame.pack(fill=tk.X, pady=5)
        
        # VAD mode label
        ttk.Label(vad_frame, text=f"VAD Mode: {self.cfg.audio.vad_mode}").pack(anchor=tk.W, padx=5, pady=2)
        
        # Create transcript area
        transcript_frame = ttk.LabelFrame(main_frame, text="Transcription")
        transcript_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.transcript_text = scrolledtext.ScrolledText(
            transcript_frame,
            wrap=tk.WORD,
            width=80,
            height=20
        )
        self.transcript_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Create log area
        log_frame = ttk.LabelFrame(main_frame, text="Log")
        log_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.log_text = scrolledtext.ScrolledText(
            log_frame, 
            wrap=tk.WORD,
            width=80,
            height=10,
            background='black',
            foreground='white'
        )
        self.log_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Add log handler for the text widget
        class TextHandler(logging.Handler):
            def __init__(self, text_widget):
                logging.Handler.__init__(self)
                self.text_widget = text_widget
                
            def emit(self, record):
                msg = self.format(record) + '\n'
                
                def append():
                    self.text_widget.configure(state='normal')
                    self.text_widget.insert(tk.END, msg)
                    self.text_widget.see(tk.END)
                    self.text_widget.configure(state='disabled')
                
                self.text_widget.after(0, append)
        
        text_handler = TextHandler(self.log_text)
        text_handler.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
        self.logger.addHandler(text_handler)
    
    def _toggle_recording(self):
        """Toggle recording state."""
        if not self.recording:
            self._start_recording()
        else:
            self._stop_recording()
    
    def _start_recording(self):
        """Start recording and transcription."""
        if self.recording:
            return
            
        self.logger.info("Starting recording...")
        
        # Start worker if not already running
        if not self.worker.running:
            self.worker.start()
        
        # Start recording
        if self.recorder.start():
            self.recording = True
            self.toggle_button.config(text="Stop Recording")
            self.status_label.config(text="Status: Recording")
            self.logger.info("Recording started. Speak into your microphone.")
        else:
            self.logger.error("Failed to start recording!")
    
    def _stop_recording(self):
        """Stop recording and transcription."""
        if not self.recording:
            return
            
        self.logger.info("Stopping recording...")
        self.recorder.stop()
        self.recording = False
        self.toggle_button.config(text="Start Recording")
        self.status_label.config(text="Status: Idle")
        self.logger.info("Recording stopped.")
    
    def _on_audio_data(self, data):
        """Handle incoming audio data."""
        if self.recording:
            # Feed data to the worker
            self.worker.add_audio(data)
    
    def _process_audio(self, audio_data):
        """Process audio for transcription."""
        try:
            # Transcribe the audio
            result = self.transcriber.transcribe(audio_data)
            if result and "text" in result:
                return result["text"].strip()
        except Exception as e:
            self.logger.error(f"Transcription error: {e}")
        return None
    
    def _on_transcription_result(self, text):
        """Handle transcription results."""
        if not text:
            return
            
        # Update transcript area
        self.transcript_text.insert(tk.END, text + "\n\n")
        self.transcript_text.see(tk.END)
        self.logger.info(f"Transcription: {text}")
    
    def _clear_transcript(self):
        """Clear the transcript area."""
        self.transcript_text.delete(1.0, tk.END)
        self.logger.info("Transcript cleared.")
    
    def _on_close(self):
        """Handle window close event."""
        self.logger.info("Shutting down...")
        if self.recording:
            self._stop_recording()
        if self.worker.running:
            self.worker.stop()
        self.root.destroy()
    
    def run(self):
        """Run the application."""
        self.root.mainloop()


def main():
    """Main entry point."""
    # Parse command line arguments
    parser = argparse.ArgumentParser(description="VAD Demo Application")
    parser.add_argument("--vad-mode", type=str, choices=["basic", "webrtc", "silero"], 
                        default="basic", help="VAD mode to use")
    parser.add_argument("--model", type=str, default="base", 
                        help="Whisper model to use (tiny, base, small, medium, large)")
    parser.add_argument("--no-cpp", action="store_true", 
                        help="Disable whisper.cpp (use Python Whisper)")
    
    args = parser.parse_args()
    
    # Create and run the application
    app = VADDemoApp(
        vad_mode=args.vad_mode,
        model_name=args.model,
        use_cpp=not args.no_cpp
    )
    app.run()


if __name__ == "__main__":
    main() 