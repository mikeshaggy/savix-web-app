import { NextResponse } from 'next/server';

export const runtime = 'nodejs';

const BACKEND_URL = 'http://localhost:8000';

export async function GET() {
  try {
    const response = await fetch(`${BACKEND_URL}/actuator/health`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch {
    return NextResponse.json({ status: 'DOWN' }, { status: 503 });
  }
}
