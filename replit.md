# Termux App Customizations

## Right Sidebar
- **Prompt**: Updated with Kali Linux style (┌──(Termux㉿localhost)-[~]└─$ ) and command-not-found handler.
- **Logo**: Configured Neofetch with custom ASCII art and lolcat colors.
- **Reset**: Restores default settings.
- **Commands**: Updated with Material Symbols-style icons (via `ic_root` and `ic_system`).

## Extra Keys
- Disabled background change on click/touch to maintain a consistent glass look.

## Sidebar Design
- Left and Right Sidebars both use a smooth, transparent glass background (`sidebar_left_glass_bg` and `sidebar_glass_bg`).
- Buttons in sidebars use rounded glass style (`btn_glass_rounded`).

## Build Fixes
- Removed duplicate `setupRightSidebar` method.
- Fixed `setTerminalBackground` symbol error by simplifying Drawable loading.
- Handled missing icons by using resource identifiers safely.
