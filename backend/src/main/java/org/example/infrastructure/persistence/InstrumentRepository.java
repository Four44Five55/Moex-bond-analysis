package org.example.infrastructure.persistence;

import org.example.domain.model.Instrument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Полиморфный репозиторий инструментов (для поиска по коду и общих запросов). */
public interface InstrumentRepository
        extends JpaRepository<Instrument, Long>, JpaSpecificationExecutor<Instrument> {

    Optional<Instrument> findBySecId(String secId);

    List<Instrument> findAllBySecIdIn(List<String> secIds);

    /**
     * Поиск по коду/названию/ISIN с сортировкой по релевантности:
     * точное совпадение кода → точное имя → префикс имени → прочие вхождения,
     * а внутри группы — более короткое имя выше (напр. «ВТБ ао» опережает «ВТБ Б1-245»).
     * Параметры {@code term/like/prefix} должны приходить уже в нижнем регистре.
     */
    @Query(value = """
            select i from Instrument i
            where lower(i.shortName) like :like
               or lower(i.fullName)  like :like
               or lower(i.secId)     like :like
               or lower(i.isin)      like :like
            order by
              case
                when lower(i.secId) = :term then 0
                when lower(i.shortName) = :term then 1
                when lower(i.shortName) like :prefix then 2
                else 3
              end,
              length(i.shortName),
              i.secId
            """,
            countQuery = """
            select count(i) from Instrument i
            where lower(i.shortName) like :like
               or lower(i.fullName)  like :like
               or lower(i.secId)     like :like
               or lower(i.isin)      like :like
            """)
    Page<Instrument> search(@Param("term") String term,
                            @Param("like") String like,
                            @Param("prefix") String prefix,
                            Pageable pageable);
}
