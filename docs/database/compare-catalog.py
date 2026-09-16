#!/usr/bin/env python3
"""Compare a read-only PostgreSQL catalog capture with accepted V1 metadata."""
import json
from pathlib import Path
import sys

# PostgreSQL may deparse the same varchar-array -> text-array coercion either
# around the whole array or each element. Only these exact known expressions
# are equivalent; changing status values or any other constraint still fails.
FUNDS_STATUS_FORMS = (
    "CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])))",
    "CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('COMPLETED'::character varying)::text, ('ARCHIVED'::character varying)::text])))",
)


def normalize(rows):
    result = set()
    for original in rows:
        row = dict(original)
        if (row.get('section') == 'constraints' and row.get('table') == 'funds'
                and row.get('name') == 'funds_status_check'
                and row.get('definition') in FUNDS_STATUS_FORMS):
            row['definition'] = FUNDS_STATUS_FORMS[0]
        result.add(json.dumps(row, sort_keys=True))
    return result


def main():
    if len(sys.argv) != 2:
        print('Usage: python3 compare-catalog.py captured-catalog.json', file=sys.stderr)
        return 2
    expected = json.loads(Path(__file__).with_name('accepted-v1-catalog.json').read_text())
    actual = json.loads(Path(sys.argv[1]).read_text())
    missing = normalize(expected) - normalize(actual)
    extra = normalize(actual) - normalize(expected)
    for title, rows in [('Missing or changed accepted metadata', missing), ('Unexpected metadata', extra)]:
        if rows:
            print(title + ':')
            for row in sorted(rows):
                print(row)
    if missing or extra:
        return 1
    print('PASS: all 86 columns, 46 constraints and 42 indexes match accepted V1 metadata.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
