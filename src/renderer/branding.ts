/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2026 Vendicated and Vesktop contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import "./branding.css";

const TEXT_ATTRIBUTES = ["aria-label", "alt", "placeholder", "title"] as const;
const BRANDING_IDLE_TIMEOUT = 250;
const LARGE_DYNAMIC_CONTENT_SELECTOR = ".vc-plugins-grid";

const pendingRoots = new Set<Node>();
let brandingScheduled = false;

function replaceBrand(value: string) {
    return value.replace(/\bvencord\b/gi, "PhilCord");
}

function containsUpstreamBrand(value: string | null | undefined) {
    return value?.toLowerCase().includes("vencord") ?? false;
}

function brandElement(element: Element) {
    for (const attribute of TEXT_ATTRIBUTES) {
        const value = element.getAttribute(attribute);
        if (containsUpstreamBrand(value)) element.setAttribute(attribute, replaceBrand(value!));
    }
}

function brandTree(root: Node) {
    if (root instanceof Element && root.closest(LARGE_DYNAMIC_CONTENT_SELECTOR)) return;

    if (root.nodeType === Node.TEXT_NODE) {
        const value = root.nodeValue;
        if (containsUpstreamBrand(value)) root.nodeValue = replaceBrand(value!);
        return;
    }

    if (root instanceof Element) brandElement(root);

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, node => {
        if (node instanceof Element && node.matches(LARGE_DYNAMIC_CONTENT_SELECTOR)) {
            return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_ACCEPT;
    });
    let current: Node | null;

    while ((current = walker.nextNode())) {
        if (current instanceof Element) {
            brandElement(current);
            continue;
        }

        const parent = current.parentElement;
        if (!parent || parent.matches("script, style, textarea")) continue;

        const value = current.nodeValue;
        if (containsUpstreamBrand(value)) current.nodeValue = replaceBrand(value!);
    }
}

/**
 * Discord mounts the Plugins page in hundreds of small DOM operations. Walking
 * every added node directly from the MutationObserver makes us traverse the
 * same nested plugin cards many times and can block the renderer for seconds.
 * Coalesce nested roots and do the cosmetic replacement after the render has
 * yielded back to Chromium instead.
 */
function flushPendingBranding() {
    brandingScheduled = false;

    const roots = Array.from(pendingRoots);
    const rootSet = new Set(roots);
    pendingRoots.clear();

    for (const root of roots) {
        if (!root.isConnected) continue;

        let ancestor = root.parentNode;
        let hasPendingAncestor = false;
        while (ancestor) {
            if (rootSet.has(ancestor)) {
                hasPendingAncestor = true;
                break;
            }
            ancestor = ancestor.parentNode;
        }
        if (hasPendingAncestor) continue;

        brandTree(root);
    }
}

function scheduleBranding(root: Node) {
    pendingRoots.add(root);
    if (brandingScheduled) return;

    brandingScheduled = true;
    if (typeof window.requestIdleCallback === "function") {
        window.requestIdleCallback(flushPendingBranding, { timeout: BRANDING_IDLE_TIMEOUT });
    } else {
        setTimeout(flushPendingBranding, 0);
    }
}

function initializeBranding() {
    brandTree(document.body);

    new MutationObserver(mutations => {
        for (const mutation of mutations) {
            if (mutation.type === "characterData") {
                const value = mutation.target.nodeValue;
                if (containsUpstreamBrand(value)) mutation.target.nodeValue = replaceBrand(value!);
                continue;
            }

            if (mutation.type === "attributes" && mutation.target instanceof Element) {
                brandElement(mutation.target);
                continue;
            }

            mutation.addedNodes.forEach(scheduleBranding);
        }
    }).observe(document.body, {
        attributes: true,
        attributeFilter: [...TEXT_ATTRIBUTES],
        characterData: true,
        childList: true,
        subtree: true
    });
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeBranding, { once: true });
} else {
    initializeBranding();
}
