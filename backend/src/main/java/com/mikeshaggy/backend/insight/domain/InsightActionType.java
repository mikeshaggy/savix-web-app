package com.mikeshaggy.backend.insight.domain;

/**
 * The one action an insight may carry (decision I4). {@code actionRef} is interpreted per type:
 * <ul>
 *   <li>{@code LINK} — an application route, e.g. {@code /wallets/transfers}.</li>
 *   <li>{@code EXCLUDE_TRANSACTION} — the transaction id to exclude from the pace.</li>
 *   <li>{@code CREATE_FIXED_PAYMENT} — optional; the prefill lives in the payload.</li>
 * </ul>
 */
public enum InsightActionType {
    LINK,
    EXCLUDE_TRANSACTION,
    CREATE_FIXED_PAYMENT
}
