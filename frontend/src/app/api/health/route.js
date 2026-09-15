import { NextResponse } from 'next/server';

export const runtime = 'nodejs';

export const dynamic = 'force-dynamic';

export async function GET() {
  const base = process.env.PROXY_BASE;
  if (!base) return NextResponse.json({ status: 'DOWN' }, { status: 503 });
  try {
    const response = await fetch(`${base}/actuator/health`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch {
    return NextResponse.json({ status: 'DOWN' }, { status: 503 });
  }
}
