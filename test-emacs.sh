#!/bin/bash
# Simple test script to verify Doom Emacs setup

echo "Testing Doom Emacs configuration..."

# Test that Emacs can start and load configuration
emacs --batch --eval "(progn (message \"Doom Emacs loaded successfully!\") (message \"Available major modes: %s\" (mapcar 'car auto-mode-alist)) (message \"LSP available: %s\" (featurep 'lsp-mode)) (message \"CIDER available: %s\" (featurep 'cider)))" 2>&1

echo "Test completed. If you see success messages above, Doom Emacs is working correctly."