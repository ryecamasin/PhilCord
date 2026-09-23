/*
 * Vesktop, a desktop app aiming to give you a snappier Discord Experience
 * Copyright (c) 2026 Vendicated and Vesktop contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import "./branding.css";

const TEXT_ATTRIBUTES = ["aria-label", "alt", "placeholder", "title"] as const;

function replaceBrand(value: string) {
    return value.replace(/\bvencord\b/gi, "PhilCord");
}

function containsUpstreamBrand(value: string | null | undefined) {
    return value?.toLowerCase().includes("vencord") ?? false;
}

function removeSupportCards(root: ParentNode) {
    const cards = root.querySelectorAll?.(".vc-special-card") ?? [];

    for (const card of cards) {
        if (card.querySelector(".vc-donate-button") || card.textContent?.includes("Support the Project")) {
            card.remove();
        }
    }
}

function brandElement(element: Element) {
    for (const attribute of TEXT_ATTRIBUTES) {
        const value = element.getAttribute(attribute);
        if (containsUpstreamBrand(value)) element.setAttribute(attribute, replaceBrand(value!));
    }
}

function brandTree(root: Node) {
    if (root.nodeType === Node.TEXT_NODE) {
        const value = root.nodeValue;
        if (containsUpstreamBrand(value)) root.nodeValue = replaceBrand(value!);
        return;
    }

    if (root instanceof Element) brandElement(root);

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
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

    if (root instanceof Element || root instanceof Document || root instanceof DocumentFragment) {
        removeSupportCards(root);
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

            mutation.addedNodes.forEach(brandTree);
        }

        removeSupportCards(document);
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
