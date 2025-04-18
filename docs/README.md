# Voice Input Service Documentation

Welcome to the documentation for the Voice Input Service, a system-wide speech-to-text application that provides real-time transcription using OpenAI's Whisper models.

## Documentation Sections

### [Architecture](architecture/README.md)
- System overview
- Component documentation
- Data flow diagrams

### [User Guide](user_guide/README.md)
- Installation instructions
- Usage guide
- Common workflows
- Troubleshooting

### [Development Guide](development/README.md)
- Setting up the development environment
- Project structure
- Testing framework
- Contributing guidelines

### [API Documentation](api/README.md)
- Component interfaces
- Public methods and classes
- Configuration options

## Quick Start

To start the Voice Input Service:

```powershell
# Navigate to the project directory
cd C:\path\to\STT

# Activate the virtual environment
.\.venv\Scripts\activate

# Run the application
python -m voice_input_service
```

The application will first check for microphone access, then initialize the UI and begin listening for keyboard shortcuts to start/stop recording. 