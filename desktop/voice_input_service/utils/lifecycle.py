"""Component lifecycle management utilities."""
from abc import ABC, abstractmethod
from typing import Optional, Any

class Closeable(ABC):
    """Interface for objects that need explicit cleanup."""
    
    @abstractmethod
    def close(self) -> None:
        """Clean up resources."""
        pass
        
    def __enter__(self):
        """Context manager entry."""
        return self
        
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.close()


class Component(Closeable):
    """Base interface for components with standardized lifecycle."""
    
    @abstractmethod
    def start(self) -> bool:
        """Start the component.
        
        Returns:
            True if started successfully, False otherwise
        """
        pass
        
    @abstractmethod
    def stop(self) -> None:
        """Stop the component."""
        pass
        
    def close(self) -> None:
        """Clean up resources."""
        self.stop()
        
    def restart(self) -> bool:
        """Restart the component.
        
        Returns:
            True if restarted successfully, False otherwise
        """
        self.stop()
        return self.start() 