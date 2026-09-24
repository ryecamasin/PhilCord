# PhilCord notices

Official PhilCord source, issues, and releases are hosted at <https://github.com/ryecamasin/PhilCord>.

PhilCord is an independent community project derived from Vesktop and Vencord. It is not affiliated with or endorsed by Discord Inc., Cloudflare, Google, or the Vencord contributors.

- PhilCord modifications are Copyright (c) 2026 PhilCord contributors and licensed under GPL-3.0-or-later.
- Vesktop (<https://github.com/Vencord/Vesktop>) and Vencord (<https://github.com/Vendicated/Vencord>) are licensed under GPL-3.0-or-later. Their existing copyright and SPDX notices are retained in the source tree.
- Discord, Cloudflare, and Google are trademarks of their respective owners.

PhilCord Desktop configures Electron's built-in DNS-over-HTTPS resolver with public endpoints operated by Cloudflare and Google. PhilCord Android uses Android's local, app-scoped `VpnService` API only to intercept PhilCord's DNS packets and resolve them over HTTPS; it does not route ordinary traffic through or provide a remote VPN server.
