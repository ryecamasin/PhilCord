# PhilCord

[Download PhilCord](https://github.com/ryecamasin/PhilCord/releases/latest) | [Report a problem](https://github.com/ryecamasin/PhilCord/issues) | [Source code](https://github.com/ryecamasin/PhilCord)

PhilCord is a community Discord client that forces Discord hostname lookups through encrypted DNS-over-HTTPS (DoH), avoiding reliance on the ISP's DNS resolver without installing a VPN or changing Windows network settings.

PhilCord is independent software. It is not affiliated with or endorsed by Discord Inc., Cloudflare, Google, or the Vencord contributors. Using a modified Discord client may violate Discord's terms of service.

## Current prototype

- Client modifications are included and presented under the PhilCord name.
- Strict DoH is enabled automatically before Discord opens.
- Cloudflare (`https://cloudflare-dns.com/dns-query`) is the primary resolver and Google (`https://dns.google/dns-query`) is the fallback.
- Plaintext DNS fallback is disabled inside PhilCord.
- No administrator access, VPN profile, background tunnel service, or separate application is required.
- DoH only bypasses DNS-level interference. It cannot bypass IP, SNI, or protocol-level blocking.

## Build

Requirements: Git, Node.js 22+, and pnpm 11+.

```powershell
pnpm install --frozen-lockfile
pnpm package
```

`pnpm package` builds PhilCord and creates packages under `dist/`.

To run the development client:

```powershell
pnpm start
```

## Licensing

PhilCord is licensed under GPL-3.0-or-later. It is derived from the GPL-licensed [Vesktop](https://github.com/Vencord/Vesktop) and [Vencord](https://github.com/Vendicated/Vencord) projects, whose copyright and attribution notices are retained as required. See [PHILCORD_NOTICE.md](PHILCORD_NOTICE.md) for details.
