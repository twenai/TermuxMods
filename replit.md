# Termux App Customizations

## Overview
Custom Termux Android terminal emulator app built with Java/Gradle. The project includes customized UI elements like sidebars, extra keys, and terminal styling.

## Project Architecture
- **Language**: Java (GraalVM CE 22.3.1, JDK 19)
- **Build System**: Gradle 7.2 with Android Gradle Plugin 4.2.2
- **Modules**: `app`, `termux-shared`, `terminal-emulator`, `terminal-view`
- **Min SDK**: 24, **Target SDK**: 28, **Compile SDK**: 30

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

## Environment Notes
- Gradle build is disabled by user request (workflow outputs status message only).
- Android SDK/NDK not available in Replit; actual APK builds require an external CI/CD pipeline or local machine.
- Code editing and Gradle tasks (lint, check, etc.) work in this environment.