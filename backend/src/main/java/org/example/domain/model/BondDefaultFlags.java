package org.example.domain.model;

/**
 * Признаки дефолта выпуска по данным биржи.
 * <p>
 * Оба признака означают «допущен когда-либо», а не «длится сейчас»: MOEX не снимает их
 * ни после погашения просрочки, ни после реструктуризации. Поэтому сами по себе они —
 * сигнал, а не дата: когда именно прекратились выплаты, решает инвестор
 * (см. {@code BondDefaultMark}).
 *
 * @param hasDefault          допущен дефолт
 * @param hasTechnicalDefault допущен технический дефолт — задержка, которую эмитент мог закрыть
 */
public record BondDefaultFlags(boolean hasDefault, boolean hasTechnicalDefault) {

    public static final BondDefaultFlags NONE = new BondDefaultFlags(false, false);

    /** Признаки, известные по справочнику: синхронизация ставит их по режиму торгов. */
    public static BondDefaultFlags of(Bond bond) {
        return new BondDefaultFlags(bond.isHasDefault(), bond.isHasTechnicalDefault());
    }

    /** Признак ставится, если его назвал хоть один источник. */
    public BondDefaultFlags or(BondDefaultFlags other) {
        return new BondDefaultFlags(hasDefault || other.hasDefault,
                hasTechnicalDefault || other.hasTechnicalDefault);
    }

    public boolean any() {
        return hasDefault || hasTechnicalDefault;
    }
}
