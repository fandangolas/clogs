;; Debug script to check Doom Emacs key bindings
(message "=== Doom Debug Info ===")
(message "Leader key: %s" (if (boundp 'doom-leader-key) doom-leader-key "UNBOUND"))
(message "Which-key active: %s" (if (bound-and-true-p which-key-mode) "YES" "NO"))
(message "General override mode: %s" (if (bound-and-true-p general-override-mode) "YES" "NO"))
(message "Evil mode: %s" (if (bound-and-true-p evil-mode) "YES" "NO"))

;; Check key bindings
(message "SPC binding: %s"
         (condition-case err
             (key-binding (kbd "SPC"))
           (error (format "ERROR: %s" err))))

;; Check which-key manually
(condition-case err
    (progn
      (which-key-mode 1)
      (message "Which-key manually enabled"))
  (error (message "Which-key error: %s" err)))

(message "=== End Debug ===")