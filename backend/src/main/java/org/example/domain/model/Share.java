package org.example.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Акция MOEX. Метрики: цена, изменение за день, капитализация, лот, сектор.
 */
@Entity
@Table(name = "share")
@DiscriminatorValue("SHARE")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class Share extends Instrument {

    @Column(name = "lot_size")
    private Integer lotSize;

    private Double capitalization;

    private String sector;

    @Column(name = "list_level")
    private Integer listLevel;

    @Column(name = "issue_size")
    private Double issueSize;

    @Override
    public InstrumentType getType() {
        return InstrumentType.SHARE;
    }
}
