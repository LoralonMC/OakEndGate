# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.1] - 2026-11-02

### Fixed
- A player riding a boat, minecart or mount through a lit portal entered a locked End. The vehicle's passengers are teleported with cause END_PORTAL and no PlayerPortalEvent of their own, and the teleport backstop skipped that cause. The backstop now gates it, and a vehicle carrying a player who may not enter is stopped at the portal, so it doesn't cross without them (Plugin Review 2026-11-01, July carry-over; verified in Paper's Entity.teleportCrossDimension).

## [1.1.0] - 2026-10-20

### Changed

- Targets Paper 26.3 (`api-version: '26.3'`, built against paper-api 26.3). This build no longer loads on 26.1.2.

## [1.0.0] - 2026-04-09

### Added

- Initial release
- Block End portal activation until a configured date and time
- Block End portal travel while locked (handles pre-activated portals)
- FancyHolograms integration with countdown display at portal locations
- Auto-detect portal locations when players interact with portal frames
- Manual portal location management via commands
- Manual unlock/lock commands for admin override
- Configurable hologram text, billboard mode, update interval, and Y offset
- Server-wide announcement when the End unlocks
- Full MiniMessage support for all messages and hologram text
- Bypass permission for admin testing
