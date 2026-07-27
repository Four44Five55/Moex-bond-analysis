import { useEffect, useRef, useState } from "react";

/** Двузначный год трактуем как 20xx: сделок XX века в портфеле не бывает. */
const CENTURY = 2000;

const pad = (n: number) => String(n).padStart(2, "0");

/** ISO (`2026-07-24`) → то, что видит пользователь (`24.07.26`). */
function toShort(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return m ? `${m[3]}.${m[2]}.${m[1].slice(2)}` : "";
}

/**
 * Ввод пользователя → ISO, либо null.
 * <p>
 * Принимаем всё, чем человек на самом деле набирает дату: `240726`, `24.07.26`, `24/07/26`,
 * `24.07` (текущий год) и полный `24.07.2026`. Разделители не обязательны — на цифровой
 * клавиатуре их набирать дороже всего.
 */
export function parseShortDate(input: string, today = new Date()): string | null {
  // Вставленную из другого места ISO-дату тоже принимаем: разбирать её как ддммгггг бессмысленно
  const isoPaste = /^(\d{4})-(\d{2})-(\d{2})$/.exec(input.trim());
  if (isoPaste) {
    return isValid(+isoPaste[1], +isoPaste[2], +isoPaste[3]) ? input.trim() : null;
  }

  const digits = input.replace(/\D/g, "");
  let day: number;
  let month: number;
  let year: number;

  if (digits.length === 4) {          // ддмм
    day = +digits.slice(0, 2);
    month = +digits.slice(2, 4);
    year = today.getFullYear();
  } else if (digits.length === 6) {   // ддммгг
    day = +digits.slice(0, 2);
    month = +digits.slice(2, 4);
    year = CENTURY + +digits.slice(4, 6);
  } else if (digits.length === 8) {   // ддммгггг
    day = +digits.slice(0, 2);
    month = +digits.slice(2, 4);
    year = +digits.slice(4, 8);
  } else {
    return null;
  }

  return isValid(year, month, day) ? `${year}-${pad(month)}-${pad(day)}` : null;
}

/** 31.02 без этой проверки молча превратилось бы в 3 марта. */
function isValid(year: number, month: number, day: number): boolean {
  const parsed = new Date(`${year}-${pad(month)}-${pad(day)}T00:00:00`);
  return !Number.isNaN(parsed.getTime())
      && parsed.getDate() === day
      && parsed.getMonth() + 1 === month;
}

/**
 * Расстановка точек по мере ввода: `2` → `2`, `24` → `24.`, `2407` → `24.07.`, `240726` → `24.07.26`.
 * <p>
 * Это заменяет перескок между полями, который сам делал нативный `input[type=date]`: точка
 * появляется ровно тогда, когда день или месяц набран целиком, и следующая цифра попадает
 * уже в следующую часть даты.
 *
 * @param typingForward цифр стало больше — значит, набирают, а не стирают; при удалении точку
 *                      дописывать нельзя, иначе стереть её будет невозможно
 */
function maskDate(digits: string, typingForward: boolean): string {
  const tail = typingForward ? "." : "";
  if (digits.length <= 1) {
    return digits;
  }
  if (digits.length === 2) {
    return `${digits}${tail}`;
  }
  if (digits.length <= 3) {
    return `${digits.slice(0, 2)}.${digits.slice(2)}`;
  }
  if (digits.length === 4) {
    return `${digits.slice(0, 2)}.${digits.slice(2)}${tail}`;
  }
  return `${digits.slice(0, 2)}.${digits.slice(2, 4)}.${digits.slice(4)}`;
}

/**
 * Поле даты в коротком формате `дд.мм.гг` с календарём по кнопке.
 * <p>
 * Нативный `input[type=date]` короткий год не принимает вообще: браузер всегда требует
 * четыре цифры. Поэтому вводится дата текстом, а сам нативный инпут остаётся скрытым —
 * ради календаря и только.
 */
export function DateField({
  value,
  onChange,
  required,
}: {
  /** ISO `гггг-мм-дд` — формат обмена с API, наружу отдаётся тоже он. */
  value: string;
  onChange: (iso: string) => void;
  required?: boolean;
}) {
  const [text, setText] = useState(() => toShort(value));
  const picker = useRef<HTMLInputElement>(null);

  // Значение может смениться извне — например, формой после сохранения сделки
  useEffect(() => setText(toShort(value)), [value]);

  const handleText = (raw: string) => {
    // Вставленную ISO-дату не маскируем: из «2026-07-24» получилось бы «20.26.0724»
    if (/^\d{4}-\d{2}-\d{2}$/.test(raw.trim())) {
      const pasted = parseShortDate(raw);
      if (pasted) {
        setText(toShort(pasted));
        onChange(pasted);
        return;
      }
    }

    const digits = raw.replace(/\D/g, "").slice(0, 8);
    const typingForward = digits.length > text.replace(/\D/g, "").length;
    const masked = maskDate(digits, typingForward);
    setText(masked);

    const iso = parseShortDate(masked);
    if (iso) {
      onChange(iso);
    }
  };

  // Незаконченный ввод не должен остаться на экране: возвращаем последнюю принятую дату
  const handleBlur = () => setText(toShort(value));

  const openPicker = () => {
    const input = picker.current;
    if (input?.showPicker) {
      input.showPicker();
    } else {
      input?.click();
    }
  };

  const valid = text === "" || parseShortDate(text) != null;

  return (
    <span className="date-field">
      <input
        type="text"
        inputMode="numeric"
        placeholder="дд.мм.гг"
        maxLength={10}
        value={text}
        onChange={(e) => handleText(e.target.value)}
        onBlur={handleBlur}
        aria-invalid={!valid}
        required={required}
      />
      <button type="button" className="date-pick" onClick={openPicker} title="Выбрать в календаре">
        📅
      </button>
      <input
        ref={picker}
        type="date"
        className="date-hidden"
        tabIndex={-1}
        aria-hidden="true"
        value={value}
        onChange={(e) => e.target.value && onChange(e.target.value)}
      />
    </span>
  );
}
