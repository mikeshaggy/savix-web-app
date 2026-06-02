'use client';
import React from 'react';

/**
 * Lightweight, dependency-free emoji field.
 *
 * A selected-emoji preview chip + a plain free-text input (custom emojis fully
 * supported). Feels like a regular premium input — no suggestion grid, no OS
 * picker invocation. Matches the Savix dark-input vocabulary.
 *
 * Props:
 *   label          – field label (string)
 *   optionalLabel  – small "(optional)" suffix shown next to the label
 *   value          – current emoji string
 *   onChange       – (value) => void
 *   placeholder    – input placeholder
 *   fallback       – preview glyph when empty (default 🏷️)
 *   hint           – helper text shown below when there is no error
 *   error          – error text shown below (overrides hint)
 *   disabled       – disables the input
 *   maxLength      – input maxLength (default 16)
 *   inputClassName – class string for the text input (e.g. INPUT_SM / INPUT_MD)
 */
export default function EmojiPickerField({
  label,
  optionalLabel,
  value = '',
  onChange,
  placeholder,
  fallback = '🏷️',
  hint,
  error,
  disabled = false,
  maxLength = 16,
  inputClassName = '',
}) {
  const current = value?.trim();

  return (
    <div>
      {label && (
        <div className="text-[12px] font-bold tracking-[0.12em] uppercase text-white/30 mb-2 flex items-center gap-1.5">
          {label}
          {optionalLabel && (
            <span className="text-white/25 font-normal tracking-normal normal-case text-[13px]">
              ({optionalLabel})
            </span>
          )}
        </div>
      )}

      <div className="flex items-center gap-2.5">
        <div className="w-11 h-11 shrink-0 bg-[#131325] border border-white/[0.055] rounded-xl flex items-center justify-center text-[22px] select-none">
          {current || fallback}
        </div>
        <input
          type="text"
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          maxLength={maxLength}
          disabled={disabled}
          placeholder={placeholder}
          className={`${inputClassName} flex-1 ${error ? 'border-red-500' : 'border-white/[0.055]'}`}
        />
      </div>

      {error ? (
        <p className="text-red-400 text-xs mt-1.5">{error}</p>
      ) : hint ? (
        <p className="text-[13px] text-white/25 mt-[7px] leading-relaxed">{hint}</p>
      ) : null}
    </div>
  );
}
