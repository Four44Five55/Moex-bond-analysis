package org.example.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;

/**
 * Политика работы с деньгами: масштабы, округление и безопасное деление.
 * <p>
 * Тип поля везде остаётся {@link BigDecimal} — это сознательный отказ от собственного
 * value-объекта и от библиотек вроде JavaMoney (JSR-354) или Joda-Money. Обе библиотеки решают
 * в первую очередь задачу мультивалютности, которой здесь нет (всё в ₽, курсов взять негде),
 * а платить за них пришлось бы конвертерами JPA на каждое поле и нестандартной сериализацией
 * в JSON: фронт получал бы объект вместо числа. С голым {@code BigDecimal} и JPA, и Jackson
 * работают без единой настройки.
 *
 * <h2>Что деньгами <b>не</b> является</h2>
 * Не всякое дробное число здесь — деньги, и переводить всё подряд в {@code BigDecimal} вредно.
 * В {@code double} осознанно остаются:
 * <ul>
 *   <li><b>итеративная аналитика</b> — XIRR (метод Ньютона), YTM, дюрация, выпуклость:
 *       это решения уравнений через {@code Math.pow} с дробной степенью, точной десятичной
 *       арифметики для них не существует, а результат — иррациональная ставка;</li>
 *   <li><b>котировки и рыночные метрики</b> — свечи OHLCV, {@code lastPrice}, изменение за день,
 *       капитализация: величины сами по себе приблизительные и меняются ежесекундно.</li>
 * </ul>
 * Деньги переходят в {@code double} только на границе с аналитикой — через {@link #toDouble}.
 * Обратно, когда котировка входит в денежный расчёт (стоимость позиции = количество × цена),
 * — через {@link #of(Double)}.
 *
 * <h2>Масштабы</h2>
 * Их три, потому что одного не хватает: округлив стоимость одной бумаги до копейки, на лоте
 * в 10 000 штук получаем расхождение в десятки рублей.
 * <ul>
 *   <li>{@link #SCALE} — итоговые суммы к показу и хранению;</li>
 *   <li>{@link #UNIT_SCALE} — величины «на одну бумагу»: цена, НКД, себестоимость лота;</li>
 *   <li>{@link #CALC_SCALE} — промежуточные деления, чтобы ошибка не накапливалась в цепочке.</li>
 * </ul>
 *
 * <h2>Грабли</h2>
 * Сравнивать суммы только через {@link BigDecimal#compareTo}: {@code equals} учитывает масштаб,
 * и {@code 100.00} не равно {@code 100.000000}. Из Postgres значения приходят с масштабом
 * колонки, а посчитанные в коде — с каким получилось, так что {@code equals} на них
 * непредсказуем. Для частого случая «ноль ли это» есть {@link #isZero}, для остальных —
 * {@code compareTo}; в тестах по той же причине {@code isEqualByComparingTo}.
 */
public final class Money {

    /** Итоговые суммы — до копейки. */
    public static final int SCALE = 2;

    /** Величины на одну бумагу: цена в % номинала, НКД, себестоимость лота. */
    public static final int UNIT_SCALE = 6;

    /** Промежуточные деления: запас, чтобы ошибка не накапливалась в длинной цепочке. */
    public static final int CALC_SCALE = 12;

    /**
     * Арифметическое округление — то, которое применяется в российском учёте и при удержании
     * НДФЛ. Банковское (HALF_EVEN) статистически точнее на больших массивах, но расходится
     * с тем, что пользователь получит от брокера, а сверять он будет именно с брокером.
     */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    /**
     * Из {@code double}. Именно {@link BigDecimal#valueOf}, а не {@code new BigDecimal(double)}:
     * конструктор берёт точное двоичное значение и превращает 0.1 в
     * {@code 0.1000000000000000055511151231257827021181583404541015625}, тогда как
     * {@code valueOf} идёт через {@code Double.toString} и даёт ожидаемые {@code 0.1}.
     */
    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }

    /** Из котировки, которой может не быть: {@code null} — это ноль денег. */
    public static BigDecimal of(Double value) {
        return value == null ? ZERO : BigDecimal.valueOf(value);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value);
    }

    /** Из строки — предпочтительный способ задать константу: {@code Money.of("13.00")}. */
    public static BigDecimal of(String value) {
        return new BigDecimal(value);
    }

    /** Ничего не значащий {@code null} из БД или DTO — это ноль, а не NPE в середине суммы. */
    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** Округление итоговой суммы до копейки. */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    /** Округление величины «на одну бумагу». */
    public static BigDecimal roundUnit(BigDecimal value) {
        return value.setScale(UNIT_SCALE, ROUNDING);
    }

    /**
     * Деление с запасом по точности. {@code BigDecimal.divide} без указания масштаба бросает
     * {@code ArithmeticException} на бесконечной дроби (1/3), поэтому масштаб обязателен.
     *
     * @throws ArithmeticException если делитель — ноль; молча возвращать ноль нельзя,
     *                             это спрятало бы ошибку в данных
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (isZero(divisor)) {
            throw new ArithmeticException("Деление денежной величины на ноль");
        }
        return dividend.divide(divisor, CALC_SCALE, ROUNDING);
    }

    /**
     * Процент от суммы: {@code amount × ratePct / 100}. Отдельный метод, потому что делить
     * на 100 приходится в каждом расчёте налога и купона, и масштаб должен быть везде один.
     */
    public static BigDecimal pct(BigDecimal amount, BigDecimal ratePct) {
        return amount.multiply(ratePct).divide(HUNDRED, CALC_SCALE, ROUNDING);
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal total = ZERO;
        for (BigDecimal v : values) {
            total = total.add(orZero(v));
        }
        return total;
    }

    /** Сумма денежного поля по коллекции — замена {@code mapToDouble(...).sum()}. */
    public static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> field) {
        BigDecimal total = ZERO;
        for (T item : items) {
            total = total.add(orZero(field.apply(item)));
        }
        return total;
    }

    /**
     * Доля от базы <b>в процентах</b>. Результат — {@code double}, потому что это уже не деньги,
     * а производная величина для показа: её всё равно округляют до десятых.
     * <p>
     * Живёт здесь, а не в вызывающих: формула нужна и аллокации, и карточке позиции, и строке
     * счёта, а записанная трижды она разъезжается — в аллокации однажды получилась доля 0.5
     * вместо 50%, потому что умножение на сто и деление на сто сократились.
     *
     * @return 0 при небоположительной базе — деления на ноль здесь быть не может
     */
    public static double percentOf(BigDecimal part, BigDecimal base) {
        if (!isPositive(base)) {
            return 0.0;
        }
        return toDouble(divide(part, base).multiply(HUNDRED));
    }

    public static boolean isZero(BigDecimal value) {
        return value == null || value.signum() == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Отрицательную сумму — в ноль (налоговая база от убытка не считается). */
    public static BigDecimal atLeastZero(BigDecimal value) {
        return isNegative(value) ? ZERO : value;
    }

    /**
     * Граница с аналитикой: XIRR, доходности и прочая итеративная математика работают
     * в {@code double}. Вызов этого метода — явная отметка, что точность здесь кончается.
     */
    public static double toDouble(BigDecimal value) {
        return orZero(value).doubleValue();
    }
}
