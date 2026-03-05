<div align="center">

# 🎯 FachyBounties

**Sistema de recompensas avanzado para servidores PvP de Minecraft** *1.13.x - 1.21.x*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.13--1.21.x-brightgreen?style=for-the-badge&logo=minecraft)](https://www.spigotmc.org)
[![Java](https://img.shields.io/badge/Java-11+-orange?style=for-the-badge&logo=java)](https://www.java.com)
[![Spigot](https://img.shields.io/badge/Spigot%20%7C%20Paper%20%7C%20Purpur-compatible-yellow?style=for-the-badge)](https://www.spigotmc.org)
[![License](https://img.shields.io/badge/License-Custom-red?style=for-the-badge)](LICENSE)

*Pon precio a la cabeza de tus enemigos. El que los mate, cobra.*

</div>

---

## ✨ Características

- 🏆 **Sistema de recompensas competitivo** — Solo la oferta más alta gana. Para superarla hay que poner más dinero, y el anterior ofertante recibe su dinero de vuelta automáticamente.
- ⚔️ **Combat Tracker propio** — Si un jugador muere de caída, lava o cualquier causa huyendo de alguien, el último atacante cobra la recompensa. Ventana de combate configurable.
- 🛡️ **Anti-farm** — Evita que dos jugadores se maten entre sí repetidamente para farmear recompensas. Cooldown configurable por horas.
- ⏳ **Expiración automática** — Los bounties caducan después de X días. El dinero se devuelve al ofertante automáticamente.
- 📊 **Top y historial** — `/bounty top` muestra las recompensas más altas en el chat. `/bounty history` guarda un registro de todos los kills con bounty.
- 🔔 **Notificaciones** — El objetivo recibe un aviso cuando alguien pone una recompensa sobre su cabeza.
- 💸 **Sistema de impuestos** — Comisión configurable por transacción. Los rangos VIP pueden tener bypass de impuestos.
- 🎨 **Totalmente configurable** — Mensajes, efectos, sonidos, materiales del menú, colores, formatos de fecha... todo en `config.yml`.
- 🔌 **PlaceholderAPI** — Placeholders para scoreboards, tablist y cualquier plugin compatible.
- 🖥️ **Menú visual** — GUI con cabezas de jugadores ordenadas por recompensa, con paginación.

---

## 📋 Requisitos

| Requisito | Versión |
|-----------|---------|
| Java | 11 o superior |
| Servidor | Spigot / Paper / Purpur 1.13 - 1.21.x |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Cualquier versión reciente |
| Plugin de economía | EssentialsX, CMI, etc. |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Opcional |

---

## 🚀 Instalación

1. Descarga el `.jar` desde [Releases](../../releases/latest)
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Asegúrate de tener **Vault** y un plugin de economía instalados
4. Reinicia el servidor
5. Edita `plugins/FachyBounties/config.yml` a tu gusto
6. Usa `/bounty reload` para aplicar cambios sin reiniciar

---

## 🎮 Comandos

| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/bounty help` | Muestra el menú de ayuda | — |
| `/bounty set <jugador> <cantidad>` | Pone una recompensa | `fachybounties.set` |
| `/bounty list` | Abre el menú visual de recompensas | `fachybounties.list` |
| `/bounty top` | Muestra el top de recompensas en el chat | `fachybounties.top` |
| `/bounty search <jugador>` | Busca la recompensa de un jugador | `fachybounties.search` |
| `/bounty remove <jugador>` | Retira tu propia recompensa | `fachybounties.remove` |
| `/bounty history [jugador]` | Historial de kills con bounty | `fachybounties.history` |
| `/bounty reload` | Recarga configuración y datos | `fachybounties.admin` |

---

## 🔐 Permisos

| Permiso | Descripción | Default |
|---------|-------------|---------|
| `fachybounties.set` | Poner recompensas | op |
| `fachybounties.list` | Ver menú visual | true |
| `fachybounties.top` | Ver top en chat | true |
| `fachybounties.search` | Buscar recompensas | true |
| `fachybounties.remove` | Retirar propia recompensa | op |
| `fachybounties.history` | Ver historial | true |
| `fachybounties.admin` | Reload y borrar cualquier bounty | op |
| `fachybounties.bypass.tax` | Sin comisión al poner bounty | false |
| `fachybounties.bypass.cooldown` | Sin cooldown entre bounties | false |
| `fachybounties.minbounty.500` | Mínimo $500 (rango VIP) | false |
| `fachybounties.minbounty.1000` | Mínimo $1000 (rango Admin) | false |

---

## 📊 PlaceholderAPI

| Placeholder | Descripción |
|-------------|-------------|
| `%fachybounties_amount%` | Recompensa activa sobre el jugador |
| `%fachybounties_top_name_1%` | Nombre del #1 con más recompensa |
| `%fachybounties_top_amount_1%` | Cantidad del #1 |
| `%fachybounties_top_name_2%` | Nombre del #2 |
| `%fachybounties_top_amount_2%` | Cantidad del #2 |

> Cambia el número final para obtener otras posiciones del top.

---

## ⚙️ Configuración destacada

```yaml
settings:
  tax-rate: 0.10            # Comisión del 10% por transacción
  min-bounty: 100           # Mínimo para poner una recompensa
  max-bounty: 0             # Máximo (0 = sin límite)
  cooldown: 10              # Segundos entre /bounty set
  combat-tag-seconds: 10    # Ventana de combate para muertes indirectas
  bounty-expiry-days: 7     # Días hasta que un bounty expira (0 = nunca)
  anti-farm-hours: 4        # Horas de cooldown entre kills del mismo jugador

# Mínimos distintos por rango (via permisos)
rank-min-bounty:
  500: "VIP"
  1000: "Admin"
```

---

## 🏗️ Arquitectura técnica

- **Caché de totales y nombres** para evitar operaciones costosas repetidas
- **Guardado asíncrono** con snapshot real para no bloquear el hilo principal
- **Combat Tracker en memoria** con limpieza automática al desconectarse
- **Operaciones de disco asíncronas** (`getOfflinePlayer`) para eliminar lag spikes
- **Anti race condition** en cooldowns registrados antes de operaciones async
- **Compatibilidad 1.13-1.21.x** sin usar APIs específicas de versión

---

## 📁 Estructura del proyecto

```
src/main/
├── java/me/fachybounties/
│   ├── BountyPlugin.java      # Core, datos, caché, expiración, anti-farm
│   ├── BountyCommand.java     # Comandos y tab completer
│   ├── BountyListener.java    # Eventos, combat tracker, GUI
│   └── BountyExpansion.java   # Integración PlaceholderAPI
└── resources/
    ├── plugin.yml
    └── config.yml
```

---

## 📜 Licencia

© 2026 Fachy — All Rights Reserved. Ver [LICENSE](LICENSE) para más detalles.

---

<div align="center">
Hecho con ☕ por <strong>Fachy</strong>
</div>
