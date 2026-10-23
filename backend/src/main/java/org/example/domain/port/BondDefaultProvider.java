package org.example.domain.port;

import org.example.domain.model.BondDefaultFlags;

/** Порт признаков дефолта облигации из внешнего источника. */
public interface BondDefaultProvider {

    BondDefaultFlags flags(String secId);
}
