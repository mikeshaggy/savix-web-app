import { NextResponse } from 'next/server';

export const runtime = 'nodejs';

export const dynamic = 'force-dynamic';

export async function GET(request) {
  const base = process.env.PROXY_BASE;
  if (!base) return NextResponse.json({ status: 'DOWN' }, { status: 503 });
  try {
    const response = await fetch(`${base}/actuator/health/readiness`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      // Finish before the SHG-16 caller's five-second probe deadline.
      signal: AbortSignal.any([AbortSignal.timeout(4000), request.signal]),
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch {
    return NextResponse.json({ status: 'DOWN' }, { status: 503 });
  }
}
