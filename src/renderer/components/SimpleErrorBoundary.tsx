/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2025 Vendicated and Vesktop contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { Card, ErrorBoundary, HeadingTertiary, Paragraph, TextButton } from "@vencord/types/components";
import type { PropsWithChildren } from "react";

function openIssueTracker() {
    window.open("https://github.com/ryecamasin/PhilCord/issues", "_blank");
}

function Fallback() {
    return (
        <Card variant="danger">
            <HeadingTertiary>Something went wrong.</HeadingTertiary>
            <Paragraph>
                Please make sure PhilCord is fully up to date. You can report this problem on our{" "}
                <TextButton variant="link" onClick={openIssueTracker}>
                    issue tracker
                </TextButton>
            </Paragraph>
        </Card>
    );
}

export function SimpleErrorBoundary({ children }: PropsWithChildren<{}>) {
    return <ErrorBoundary fallback={Fallback}>{children}</ErrorBoundary>;
}
