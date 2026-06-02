'use client';
import React from 'react';
import { Check } from 'lucide-react';
import { isValidHexColor, normalizeHexColor, resolveAccentColor } from '@/utils/helpers';

/**
 * Lightweight, dependency-free color picker field.
 *
 * Preset swatches (primary interaction) + a native <input type="color"> for
 * arbitrary picking + an optional hex text input. Invalid/empty hex never break
 * styles — the native picker and swatch ring fall back to violet for display
 * while the stored value stays exactly as typed. Stores a hex string in the
 * same backend field as before.
 *
 * Props:
 *   label          – field label
 *   optionalLabel  – small "(optional)" suffix
 *   value          – current hex string (may be '' / invalid)
 *   onChange       – (value) => void  (stores normalized #rrggbb on pick)
 *   presets        – array of { value, name } swatches
 *   placeholder    – hex input placeholder (default #7c3aed)
 *   hint           – helper text below
 *   error          – error text below (overrides hint)
 *   disabled       – disables all controls
 */
export default function ColorPickerField({
  label,
  optionalLabel,
  value = '',
  onChange,
  presets = [],
  placeholder = '#7c3aed',
  hint,
  error,
  disabled = false,
}) {
  const normalized = normalizeHexColor(value);          // null when empty/invalid
  const displayColor = resolveAccentColor(value);       // always a usable hex

  const pick = (hex) => {
    if (disabled) return;
    onChange?.(hex);
  };

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

      {/* Preset swatches */}
      {presets.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {presets.map(({ value: hex, name }) => {
            const active = normalized === normalizeHexColor(hex);
            return (
              <button
                key={hex}
                type="button"
                disabled={disabled}
                onClick={() => pick(hex)}
                title={name}
                aria-label={name}
                aria-pressed={active}
                style={{ backgroundColor: hex }}
                className={`w-8 h-8 rounded-full flex items-center justify-center transition-all disabled:opacity-40 disabled:cursor-not-allowed ${
                  active
                    ? 'ring-2 ring-white/70 ring-offset-2 ring-offset-[#0e0e1c]'
                    : 'ring-1 ring-white/10 hover:ring-white/40'
                }`}
              >
                {active && <Check className="w-3.5 h-3.5 text-white drop-shadow" strokeWidth={3} />}
              </button>
            );
          })}
        </div>
      )}

      {/* Native picker + hex text input + live preview */}
      <div className="flex items-center gap-2.5 mt-2.5">
        <label
          className="relative w-9 h-9 shrink-0 rounded-[10px] border border-white/[0.12] overflow-hidden cursor-pointer hover:border-white/25 transition-all"
          style={{ backgroundColor: displayColor }}
          title={normalized || placeholder}
        >
          <input
            type="color"
            value={displayColor}
            disabled={disabled}
            onChange={(e) => pick(e.target.value)}
            className="absolute inset-0 opacity-0 cursor-pointer disabled:cursor-not-allowed"
            aria-label={label}
          />
        </label>

        <input
          type="text"
          value={value}
          disabled={disabled}
          onChange={(e) => onChange?.(e.target.value)}
          maxLength={7}
          placeholder={placeholder}
          spellCheck={false}
          className={`bg-[#131325] border rounded-[10px] px-3 py-2.5 text-[14px] font-mono text-white placeholder:text-white/25 outline-none transition-all flex-1 min-w-0 focus:border-purple-500/50 focus:shadow-[0_0_0_3px_rgba(124,58,237,0.1)] focus:bg-[#1a1a2e] ${
            value && !isValidHexColor(value) ? 'border-amber-500/50' : 'border-white/[0.055]'
          }`}
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
