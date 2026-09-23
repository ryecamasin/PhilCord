/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2026 Vendicated and Vesktop contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { app } from "electron";

// Keep more than one independent resolver so PhilCord still starts if one
// provider is temporarily unavailable. "secure" intentionally prevents the
// ISP's plaintext resolver from being used as a silent fallback.
export const SECURE_DNS_SERVERS = ["https://cloudflare-dns.com/dns-query", "https://dns.google/dns-query"];

export function configureSecureDns() {
    app.configureHostResolver({
        enableBuiltInResolver: true,
        secureDnsMode: "secure",
        secureDnsServers: SECURE_DNS_SERVERS
    });

    console.log("Strict DNS-over-HTTPS enabled for PhilCord");
}
