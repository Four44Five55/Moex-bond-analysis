package org.example.domain.portfolio;

/** Вид будущей выплаты. */
public enum IncomeKind {
    COUPON,
    AMORTIZATION,
    DIVIDEND,
    /** Проценты по банковскому вкладу. */
    INTEREST,
    /** Возмещение по бумаге в дефолте — только среди полученного, в прогнозе его не бывает. */
    RECOVERY
}
