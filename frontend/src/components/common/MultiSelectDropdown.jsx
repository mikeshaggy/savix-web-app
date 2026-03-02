import React, { useState, useRef, useEffect } from 'react';
import { ChevronDown, Check } from 'lucide-react';

export default function MultiSelectDropdown({
    options = [],
    selected = [],
    onChange,
    allLabel = 'All',
    nSelectedLabel,
    className = '',
}) {
    const [open, setOpen] = useState(false);
    const ref = useRef(null);

    useEffect(() => {
        const handler = (e) => {
            if (ref.current && !ref.current.contains(e.target)) {
                setOpen(false);
            }
        };
        document.addEventListener('mousedown', handler);
        return () => document.removeEventListener('mousedown', handler);
    }, []);

    const isActive = selected.length > 0;

    const toggle = (value) => {
        const next = selected.includes(value)
            ? selected.filter((v) => v !== value)
            : [...selected, value];
        onChange(next);
    };

    const clearAll = () => {
        onChange([]);
    };

    let displayLabel;
    if (selected.length === 0) {
        displayLabel = allLabel;
    } else if (selected.length === 1) {
        const opt = options.find((o) => o.value === selected[0]);
        displayLabel = opt ? (opt.emoji ? `${opt.emoji} ${opt.label}` : opt.label) : selected[0];
    } else {
        displayLabel = nSelectedLabel ? nSelectedLabel(selected.length) : `${selected.length} selected`;
    }

    const chipBase =
        'appearance-none outline-none rounded-[10px] px-[13px] py-[7px] text-[13px] font-medium cursor-pointer transition-all whitespace-nowrap';
    const chipDefault =
        'bg-[#0e0e1c] border border-white/[0.055] text-white/50 hover:border-white/[0.12] hover:text-white';
    const chipActive =
        'bg-[rgba(124,58,237,0.14)] border border-[rgba(124,58,237,0.35)] text-purple-300';

    return (
        <div className={`relative ${className}`} ref={ref}>
            {/* Trigger button */}
            <button
                type="button"
                onClick={() => setOpen((prev) => !prev)}
                className={`${chipBase} ${isActive ? chipActive : chipDefault} flex items-center gap-1.5`}
            >
                <span className="truncate max-w-[160px]">{displayLabel}</span>
                <ChevronDown
                    className={`w-3 h-3 opacity-40 transition-transform ${open ? 'rotate-180' : ''}`}
                />
            </button>

            {/* Dropdown panel */}
            {open && (
                <div
                    className="absolute top-full left-0 mt-1.5 min-w-[200px] max-h-[280px] overflow-y-auto bg-[#131325] border border-white/[0.07] rounded-xl shadow-[0_16px_48px_rgba(0,0,0,0.5)] z-50"
                    style={{ animation: 'fadeUp 0.12s cubic-bezier(0.4,0,0.2,1) both' }}
                >
                    <div className="p-1">
                        {/* "All" row – clears selection */}
                        <button
                            type="button"
                            onClick={() => {
                                clearAll();
                                setOpen(false);
                            }}
                            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-[13px] transition-all cursor-pointer ${
                                selected.length === 0
                                    ? 'text-purple-300 bg-purple-500/10'
                                    : 'text-white/50 hover:bg-white/[0.04] hover:text-white'
                            }`}
                        >
                            <span className="w-4 h-4 flex items-center justify-center">
                                {selected.length === 0 && <Check className="w-3.5 h-3.5" />}
                            </span>
                            {allLabel}
                        </button>

                        {/* Separator */}
                        <div className="h-px bg-white/[0.06] mx-2 my-1" />

                        {/* Options */}
                        {options.map((opt) => {
                            const isSelected = selected.includes(opt.value);
                            return (
                                <button
                                    key={opt.value}
                                    type="button"
                                    onClick={() => toggle(opt.value)}
                                    className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-[13px] transition-all cursor-pointer ${
                                        isSelected
                                            ? 'text-purple-300 bg-purple-500/10'
                                            : 'text-white/50 hover:bg-white/[0.04] hover:text-white'
                                    }`}
                                >
                                    {/* Checkbox indicator */}
                                    <span
                                        className={`w-4 h-4 rounded-[4px] border flex items-center justify-center flex-shrink-0 transition-all ${
                                            isSelected
                                                ? 'bg-purple-500/30 border-purple-400/50'
                                                : 'border-white/15 bg-transparent'
                                        }`}
                                    >
                                        {isSelected && <Check className="w-3 h-3 text-purple-300" />}
                                    </span>
                                    {opt.emoji && <span className="text-[14px]">{opt.emoji}</span>}
                                    <span className="truncate">{opt.label}</span>
                                </button>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}
