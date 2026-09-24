/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2026 Vendicated and Vesktop contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { Button, Card, HeadingSecondary, Paragraph } from "@vencord/types/components";
import { useState } from "@vencord/types/webpack/common";

type CheckState = "idle" | "checking" | "current" | "available" | "error";

export default function PhilCordUpdater() {
    const [state, setState] = useState<CheckState>("idle");
    const [message, setMessage] = useState("");

    async function checkForUpdates() {
        setState("checking");
        setMessage("Checking GitHub for a newer PhilCord release…");

        try {
            const result = await VesktopNative.app.openUpdater();
            if (result.status === "available") {
                setState("available");
                setMessage(`PhilCord ${result.latestVersion} is available. The updater window is now open.`);
            } else {
                setState("current");
                setMessage(`PhilCord ${result.currentVersion} is up to date.`);
            }
        } catch (error) {
            setState("error");
            setMessage(`Update check failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    return (
        <Card style={{ padding: "20px" }}>
            <HeadingSecondary>PhilCord updates</HeadingSecondary>
            <Paragraph style={{ margin: "12px 0 18px" }}>
                Check the official PhilCord GitHub releases and install desktop updates without leaving the app.
            </Paragraph>
            <Button onClick={checkForUpdates} disabled={state === "checking"}>
                {state === "checking" ? "Checking…" : "Check for updates"}
            </Button>
            {message && (
                <Paragraph style={{ marginTop: "16px" }}>
                    {state === "error" ? "⚠ " : state === "current" ? "✓ " : ""}
                    {message}
                </Paragraph>
            )}
        </Card>
    );
}
