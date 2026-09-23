/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2023 Vendicated and Vencord contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { app } from "electron";
import { copyFileSync, mkdirSync, readdirSync } from "fs";
import { join } from "path";

import { autoStart } from "./autoStart";
import { DATA_DIR } from "./constants";
import { Settings, State } from "./settings";

function importVencordSettings() {
    const from = join(app.getPath("userData"), "..", "Vencord", "settings");
    const to = join(DATA_DIR, "settings");

    try {
        const files = readdirSync(from);
        mkdirSync(to, { recursive: true });

        for (const file of files) {
            copyFileSync(join(from, file), join(to, file));
        }
    } catch (e) {
        if (e instanceof Error && "code" in e && e.code === "ENOENT") {
            console.log("No Vencord settings found to import.");
        } else {
            console.error("Failed to import Vencord settings:", e);
        }
    }
}

export function completeFirstLaunch() {
    importVencordSettings();

    Settings.store.discordBranch = "stable";
    Settings.store.minimizeToTray = true;
    Settings.store.arRPC = true;
    State.store.firstLaunch = false;

    autoStart.enable();
    console.log("Applied PhilCord first-launch defaults: stable, startup, Rich Presence, import, and tray.");
}
