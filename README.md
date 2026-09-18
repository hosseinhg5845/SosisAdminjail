# SosisAdminJail

> A powerful, feature-rich jail system for Paper servers with anti-cheat, database support, Discord webhooks, and a beautiful GUI.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20%2B-green)
![Paper](https://img.shields.io/badge/Platform-Paper-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Version](https://img.shields.io/badge/Version-2.3.9-blue)

---

## Features

-  **Jail System** — Jail online & offline players with reason and required block count
-  **Mine System** — Custom mine regions with auto-respawn
-  **Anti-Cheat** — Fly, Speed, NoClip, Jesus, AutoMine detection
-  **Multi-Database** — H2 (default), MySQL, or MongoDB
-  **Stats GUI** — View jail history, blocks broken, total jails
-  **Discord Webhook** — Jail/Unjail/Progress notifications
-  **Website Webhook** — Send jail data to your own website
-  **PlaceholderAPI** — Full placeholder support
-  **Configurable** — Titles, sounds, boss bar, action bar, messages
-  **Discount Blocks** — Allow jailed players to pay to reduce blocks
-  **Reload Command** — Hot-reload without restart

---

##  Installation

1. Download the latest `SosisAdminJail-x.x.x.jar` from [Releases](../../releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.
4. Configure `config.yml`, `discord.yml`, `mine.yml`, `storage.yml`.
5. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholders.

---

##  Commands

### Player / Staff
| Command | Description | Permission |
|---------|-------------|------------|
| `/adminjail <player> <blocks> [reason] [jail]` | Jail a player | `adminjail.use` |
| `/unjail <player>` | Release a player | `adminjail.use` |
| `/jailpay` | Pay to reduce blocks | `adminjail.use` |
| `/adminjailstats <player>` | View jail stats GUI | `adminjail.use` |

### Admin
| Command | Description |
|---------|-------------|
| `/adminjailadmin setjail <name>` | Set jail location |
| `/adminjailadmin deljail <name>` | Delete jail |
| `/adminjailadmin setspawn <name>` | Set spawn for unjail |
| `/adminjailadmin delspawn <name>` | Delete spawn |
| `/adminjailadmin tp <jail/spawn> <name>` | Teleport to location |
| `/adminjailadmin visit <player>` | Visit jailed player |
| `/adminjailadmin transfer <player> <jail>` | Transfer player to another jail |
| `/adminjailadmin setmine <name>` | Create mine region |
| `/adminjailadmin delmine <name>` | Delete mine |
| `/adminjailadmin listmine` | List all mines |
| `/adminjailadmin cancelmine` | Cancel mine selection |
| `/adminjailadmin reload` | Reload config |
| `/adminjailadmin debug` | Show debug info |

---

##  Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `adminjail.use` | Use jail commands | op |
| `adminjail.admin` | Use admin commands | op |

---

##  Placeholders (PlaceholderAPI)

| Placeholder | Description |
|-------------|-------------|
| `%sosisadminjail_jailed%` | `true` / `false` |
| `%sosisadminjail_status%` | Colored status |
| `%sosisadminjail_status_raw%` | Raw status |
| `%sosisadminjail_jail_name%` | Current jail name |
| `%sosisadminjail_reason%` | Jail reason |
| `%sosisadminjail_needed_blocks%` | Total blocks |
| `%sosisadminjail_broken_blocks%` | Broken blocks |
| `%sosisadminjail_remaining_blocks%` | Remaining blocks |
| `%sosisadminjail_progress_percent%` | Progress % |
| `%sosisadminjail_progress_bar_5%` | 5-char bar |
| `%sosisadminjail_progress_bar_10%` | 10-char bar |
| `%sosisadminjail_progress_bar_20%` | 20-char bar |

---

##  Storage Backends

Edit `storage.yml`:

```yaml
storage-type: "H2"  # H2, MYSQL, or MONGODB

mysql:
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: ""

mongodb:
  uri: "mongodb://localhost:27017"
  database: "adminjail"
  collection: "jailed_players"
