# OakEndGate

Locks End portals until a configured date and time, with optional hologram countdown display.

## Features

- Block End portal activation (Eye of Ender placement) until a scheduled unlock date
- Block End portal travel for already-active portals
- Countdown hologram display at portal locations via FancyHolograms
- Auto-detect portal locations when players interact with portal frames
- Manual unlock/lock commands for admin override
- Server-wide announcement when the End opens
- Fully configurable messages with MiniMessage support
- Bypass permission for admin testing

## Requirements

- Paper 1.21.10+
- Java 21
- [FancyHolograms](https://modrinth.com/plugin/fancyholograms) (optional, for hologram display)

## Installation

1. Drop the JAR into your `plugins/` folder
2. Restart the server
3. Edit `plugins/OakEndGate/config.yml` to set your unlock date and time
4. Optionally install FancyHolograms for countdown holograms at portal locations

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/oakendgate` | View lock status and countdown | `oakendgate.status` |
| `/oakendgate reload` | Reload configuration | `oakendgate.reload` |
| `/oakendgate unlock` | Manually unlock the End early | `oakendgate.unlock` |
| `/oakendgate lock` | Re-lock the End after manual unlock | `oakendgate.lock` |
| `/oakendgate portals` | List registered portal locations | `oakendgate.portals` |
| `/oakendgate portals add` | Register the nearest portal | `oakendgate.portals` |
| `/oakendgate portals remove <index>` | Remove a portal by index | `oakendgate.portals` |

Aliases: `/endgate`, `/oeg`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `oakendgate.*` | All OakEndGate permissions | op |
| `oakendgate.status` | View lock status and countdown | everyone |
| `oakendgate.reload` | Reload configuration | op |
| `oakendgate.unlock` | Manually unlock the End early | op |
| `oakendgate.lock` | Re-lock after manual unlock | op |
| `oakendgate.portals` | Manage registered portal locations | op |
| `oakendgate.bypass` | Bypass End portal lock | op |

## Configuration

Set the unlock date, time, and timezone in `config.yml`:

```yaml
unlock:
  date: "2026-06-01"
  time: "18:00:00"
  timezone: "UTC"
```

Hologram appearance is fully configurable, including the countdown lines, billboard mode, vertical offset, and update interval. See `config.yml` for all options.

Portal locations are detected automatically when players interact with End portal frames, or can be managed manually via commands.
