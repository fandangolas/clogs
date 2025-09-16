# Doom Emacs Setup for Clojure Development

## What's Installed

### Software:
- **Doom Emacs** - Modern Emacs configuration framework
- **clojure-lsp 2025.08.25** - Language server for Clojure
- **Java 21 LTS** - Runtime for Clojure

### Doom Modules Enabled:
- `(clojure +lsp)` - Clojure support with LSP integration
- `lsp` - Language Server Protocol support
- `tree-sitter` - Advanced syntax highlighting
- `data` - Support for JSON, YAML, etc.
- `evil` - Vim keybindings (default in Doom)

### Key Features:
- **CIDER** - Interactive Clojure development
- **clojure-lsp** - Code completion, navigation, refactoring
- **clj-refactor** - Advanced refactoring tools
- **Flycheck** - Real-time syntax checking
- **Evil mode** - Vim-style editing (can be disabled if needed)

---

## Getting Started

### 1. Open Emacs
```bash
emacs src/clogs/core.clj
```

### 2. First Time Setup
- Doom will automatically install packages when you first open a Clojure file
- LSP will start automatically and index your project
- You may see messages about downloading dependencies - this is normal

### 3. Start a REPL
- Press `SPC m '` (Space, then m, then single quote)
- Or use `:cider-jack-in` in Evil command mode
- Choose `deps.edn` when prompted

---

## Essential Keybindings

### Navigation (Evil mode):
- `SPC f f` - Find file
- `SPC p f` - Find file in project
- `SPC b b` - Switch buffer
- `SPC w w` - Switch window

### Clojure Development:
- `SPC m '` - Start/connect to REPL
- `SPC m e e` - Evaluate expression at point
- `SPC m e f` - Evaluate function
- `SPC m e b` - Evaluate buffer
- `SPC m e r` - Evaluate region
- `SPC m d d` - Show documentation
- `SPC m g g` - Go to definition
- `SPC m t n` - Run tests in namespace

### LSP Features:
- `SPC c a` - Code actions
- `SPC c r` - Rename symbol
- `SPC c f` - Format buffer
- `SPC c d` - Show diagnostics

### General:
- `SPC h d h` - Doom help
- `SPC q q` - Quit Emacs
- `:q` - Quit current buffer (Vim-style)

---

## Configuration Files

### Main Configuration:
- `~/.config/doom/init.el` - Enabled modules
- `~/.config/doom/config.el` - Custom settings
- `~/.config/doom/packages.el` - Additional packages

### Updating:
```bash
~/.config/emacs/bin/doom sync    # Sync configuration changes
~/.config/emacs/bin/doom upgrade # Update Doom itself
```

---

## Clojure Project Structure

Your project is set up with:
- `deps.edn` - Dependencies and aliases
- `src/clogs/core.clj` - Sample code with Malli validation
- REPL-friendly development setup

### Sample Workflow:
1. Open `src/clogs/core.clj`
2. Start REPL with `SPC m '`
3. Evaluate code with `SPC m e f`
4. Test validation: `(validate-log sample-log)`
5. Use LSP features for navigation and refactoring

---

## Troubleshooting

### If LSP doesn't start:
- Make sure `clojure-lsp` is in your PATH: `which clojure-lsp`
- Check LSP server status: `SPC c w r` (restart LSP)

### If CIDER won't connect:
- Ensure you have a valid `deps.edn` file
- Try running `clojure` in terminal first to verify setup

### If packages are missing:
```bash
~/.config/emacs/bin/doom sync
```

---

## Next Steps

1. Explore the sample code in `src/clogs/core.clj`
2. Try the REPL-driven development workflow
3. Use LSP features for code navigation
4. Customize Doom configuration as needed

Enjoy your modern Clojure development environment! 🚀