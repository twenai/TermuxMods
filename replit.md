# Termux App - replit.md

## Overview

Termux is an Android terminal emulator application and Linux environment. It provides a full Linux command line experience on Android devices, including shell access (Bash, Zsh), package management, and over 1000 installable packages. The app consists of the core terminal emulator UI, terminal emulation engine, and a shared library (`termux-shared`) used across Termux and its plugin ecosystem.

The project is a native Android application built with Java/Gradle. It is not a web application — there is no frontend/backend split, no database, and no web API. The repository contains the Android app source code, a shared library module, and supports several optional plugin apps (Termux:API, Termux:Boot, Termux:Float, Termux:Styling, Termux:Tasker, Termux:Widget).

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Project Structure

- **Multi-module Android Gradle project**: The repository is organized as an Android project with at least two modules:
  - **`app`** (implied) — The main Termux application containing the terminal UI, terminal emulation, and shell management.
  - **`termux-shared`** — A shared library module containing reusable utilities, constants, settings management, file utilities, logging, notification helpers, shell utilities, crash handling, and more. This library is also published via JitPack for use by plugin apps.

### Core Architectural Decisions

1. **Native Android (Java)**
   - **Problem**: Need a performant terminal emulator on Android with low-level system access.
   - **Solution**: Built as a native Android app in Java using the Android SDK.
   - **Rationale**: Terminal emulation requires direct access to Android's process management, file system, and input handling — areas where native development excels over cross-platform frameworks.

2. **Terminal Emulation Heritage**
   - **Problem**: Need reliable terminal emulation.
   - **Solution**: Based on code from [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) (Apache 2.0 licensed), adapted and extended for Termux's needs.

3. **Shared Library Module (`termux-shared`)**
   - **Problem**: Multiple Termux plugin apps need access to common constants, utilities, and settings.
   - **Solution**: A separate `termux-shared` library module published to JitPack, containing shared code for file operations, shell management, logging, preferences, notifications, crash handling, markdown rendering, and Termux-specific constants.
   - **Key packages**: `activities`, `crash`, `data`, `file`, `interact`, `logger`, `markdown`, `models`, `notification`, `settings`, `shell`, `termux`, `view`.

4. **Plugin Architecture**
   - **Problem**: Users need extensibility (API access, boot scripts, floating windows, styling, task automation, widgets).
   - **Solution**: Plugin apps communicate with the core Termux app, likely via Android Intents and content providers. Each plugin is a separate app/repository that depends on `termux-shared`.

5. **Settings Management**
   - **Problem**: Need configurable terminal behavior.
   - **Solution**: Dual approach using Android SharedPreferences (for app preferences) and properties files (for terminal-specific settings like key bindings). Managed via `SharedPreferenceUtils`, `SharedProperties`, and `SharedPropertiesParser` in the shared library.

6. **Shell/Process Management**
   - **Problem**: Need to run and manage Linux processes within the Android app.
   - **Solution**: Custom shell management via `TermuxTask`, `ShellUtils`, `ShellEnvironmentClient`, and `ResultSender` classes in the shared library.

### Build System

- **Gradle** with Android Gradle Plugin
- **CI/CD**: GitHub Actions for build and unit test workflows
- **Library Distribution**: JitPack for publishing `termux-shared` as a dependency

### Licensing

- Main app: GPLv3 only
- Terminal emulator base code: Apache License 2.0
- Several utility classes in `termux-shared`: MIT License

## External Dependencies

### Build & Distribution
- **Gradle**: Android build system
- **JitPack**: Maven repository for publishing `termux-shared` library releases for plugin apps to consume
- **GitHub Actions**: CI/CD for automated builds and unit tests

### Android Platform
- **Android SDK**: Core platform dependency
- **Android SharedPreferences**: Local settings storage
- **Android Notification System**: Used for service notifications and alerts

### Community & Communication
- **Gitter / Discord**: Community support channels (not code dependencies, but part of the project ecosystem)

### No Web/Cloud Dependencies
This is a self-contained Android application. There are no web servers, databases, external APIs, or cloud services integrated into the core app. Package management (installing Linux packages inside Termux) connects to Termux package repositories, but that functionality lives in the [termux-packages](https://github.com/termux/termux-packages) repository.