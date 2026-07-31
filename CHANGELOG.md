# Changelog

All notable changes to this project will be documented in this file.

## [2.2.0] - 2026-08-01

### Added
- **Firestore Seeder**: Automated initial data population (States, Cities, Institutions) for fresh installations.
- **Dynamic Multi-Institution Architecture**: Registration now supports selecting State -> City -> Institution from a global verified list.
- **Institution Request Workflow**: Built-in flow for lecturers to suggest new institutions.
- **Batch-Level Verification**: Mandatory batch field for subjects ensures students only mark attendance for their correct academic year.
- **Manual Attendance Backup**: USN-based search and manual marking for lecturers to support students with device issues.
- **Attendance Analytics Dashboard**: High-performance lecturer screen showing student-wise percentages with 75% threshold highlighting.

### Changed
- **Database Migration**: Fully migrated from legacy nested Realtime Database to a flat, scalable Firestore schema.
- **BLE-Only Workflow**: Optimized attendance marking to use 100% Bluetooth verification, eliminating GPS delays.
- **Unified Navigation**: Synchronized Navigation Drawers for both roles with role-specific menu items.
- **Branding**: Official transition of all system strings and assets to **SecureAttend**.

### Fixed
- **Routing & Recovery**: Fixed Splash Activity to correctly recover roles and institutions on app reinstall.
- **Registration Stability**: Added progress indicators and double-click prevention for registration buttons.
- **Data Aggregation**: Fixed student attendance history to accurately reflect subject-wise statistics.
- **Name Sync**: Lecturer dashboard now displays real student names and USNs instead of generic IDs.

### Removed
- **Geofencing Logic**: Removed all GPS-based coordinate checks and boundary setup screens to improve speed and privacy.
- **Legacy RTDB**: Purged all Realtime Database dependencies and code.
- **Unused UI**: Removed "Achievements" for lecturers and geofence-specific layout files.

### Security & Privacy
- **Android 12+ Optimization**: Implemented `neverForLocation` flag for BLE scanning to respect student privacy.
- **Locked Identity**: Core fields like USN, Email, and Institution are now read-only post-registration.
