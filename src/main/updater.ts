/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2025 Vendicated and Vesktop contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { app, BrowserWindow, ipcMain } from "electron";
import { autoUpdater, UpdateInfo } from "electron-updater";
import { join } from "path";
import { IpcEvents, UpdaterIpcEvents } from "shared/IpcEvents";
import type { UpdateCheckResult } from "shared/updater";
import { Millis } from "shared/utils/millis";

import { State } from "./settings";
import { handle } from "./utils/ipcWrappers";
import { makeLinksOpenExternally } from "./utils/makeLinksOpenExternally";
import { loadView } from "./vesktopStatic";

let updaterWindow: BrowserWindow | null = null;

// Keep desktop updates independent from Android prereleases in the same repository.
// The GitHub provider parses every release tag as semver, while the generic feed
// only follows the desktop release selected as `latest` and its latest.yml file.
autoUpdater.setFeedURL({
    provider: "generic",
    url: "https://github.com/ryecamasin/PhilCord/releases/latest/download"
});

autoUpdater.on("update-available", update => {
    if (State.store.updater?.ignoredVersion === update.version) return;
    if ((State.store.updater?.snoozeUntil ?? 0) > Date.now()) return;

    openUpdater(update);
});

autoUpdater.on("update-downloaded", () => setTimeout(() => autoUpdater.quitAndInstall(), 100));
autoUpdater.on("download-progress", p =>
    updaterWindow?.webContents.send(UpdaterIpcEvents.DOWNLOAD_PROGRESS, p.percent)
);
autoUpdater.on("error", err => updaterWindow?.webContents.send(UpdaterIpcEvents.ERROR, err.message));

autoUpdater.autoDownload = false;
autoUpdater.autoInstallOnAppQuit = false;
autoUpdater.fullChangelog = true;

const isOutdated = autoUpdater.checkForUpdates().then(
    res => Boolean(res?.isUpdateAvailable),
    error => {
        console.error("Failed to check for PhilCord updates", error);
        return false;
    }
);

handle(IpcEvents.UPDATER_IS_OUTDATED, () => isOutdated);
handle(IpcEvents.UPDATER_OPEN, async () => {
    const res = await autoUpdater.checkForUpdates();
    if (res?.isUpdateAvailable && res.updateInfo) openUpdater(res.updateInfo);

    return {
        status: res?.isUpdateAvailable ? "available" : "current",
        currentVersion: app.getVersion(),
        latestVersion: res?.updateInfo?.version
    } satisfies UpdateCheckResult;
});

function openUpdater(update: UpdateInfo) {
    if (updaterWindow && !updaterWindow.isDestroyed()) {
        updaterWindow.show();
        updaterWindow.focus();
        return;
    }

    updaterWindow = new BrowserWindow({
        title: "PhilCord Updater",
        autoHideMenuBar: true,
        webPreferences: {
            preload: join(__dirname, "updaterPreload.js")
        },
        minHeight: 400,
        minWidth: 750
    });
    makeLinksOpenExternally(updaterWindow);

    handle(UpdaterIpcEvents.GET_DATA, () => ({ update, version: app.getVersion() }));
    handle(UpdaterIpcEvents.INSTALL, async () => {
        await autoUpdater.downloadUpdate();
    });
    handle(UpdaterIpcEvents.SNOOZE_UPDATE, () => {
        State.store.updater ??= {};
        State.store.updater.snoozeUntil = Date.now() + 1 * Millis.DAY;
        updaterWindow?.close();
    });
    handle(UpdaterIpcEvents.IGNORE_UPDATE, () => {
        State.store.updater ??= {};
        State.store.updater.ignoredVersion = update.version;
        updaterWindow?.close();
    });

    updaterWindow.on("closed", () => {
        ipcMain.removeHandler(UpdaterIpcEvents.GET_DATA);
        ipcMain.removeHandler(UpdaterIpcEvents.INSTALL);
        ipcMain.removeHandler(UpdaterIpcEvents.SNOOZE_UPDATE);
        ipcMain.removeHandler(UpdaterIpcEvents.IGNORE_UPDATE);
        updaterWindow = null;
    });

    loadView(updaterWindow, "updater/index.html");
}
