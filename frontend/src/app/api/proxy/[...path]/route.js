import { NextResponse } from "next/server";

export const runtime = "nodejs";

const PROXY_SECRET = process.env.PROXY_SECRET;

async function forward(req, { params }) {

  if (!PROXY_SECRET) {
    return NextResponse.json(
      { error: "Server configuration error: PROXY_SECRET not set" },
      { status: 500 }
    );
  }

  const { path: pathSegments } = await params;
  const pathParts = pathSegments || [];
  const base = process.env.PROXY_BASE;

  if (!base) {
    return NextResponse.json(
      { error: "Missing PROXY_BASE env var" },
      { status: 500 }
    );
  }

  const path = pathParts.join("/");
  const fullPath = path.startsWith("api/") ? path : `api/${path}`;
  const targetUrl = new URL(`${base.replace(/\/$/, "")}/${fullPath}`);

  req.nextUrl.searchParams.forEach((v, k) => targetUrl.searchParams.append(k, v));

  const hasBody = !["GET", "HEAD"].includes(req.method);
  const body = hasBody ? await req.arrayBuffer() : undefined;

  const headers = new Headers(req.headers);
  headers.delete("host");
  headers.delete("content-length");
  
  headers.delete("origin");
  headers.delete("referer");


  headers.set("X-Savix-Proxy-Secret", PROXY_SECRET);

  if (!headers.get("accept")) headers.set("accept", "application/json");

  const res = await fetch(targetUrl, {
    method: req.method,
    headers,
    body,
    redirect: "manual",
  });

  const out = new NextResponse(res.body, { status: res.status });
  
  res.headers.forEach((v, k) => {
    if (k.toLowerCase() !== 'set-cookie') {
      out.headers.set(k, v);
    }
  });
  
  const setCookieHeaders = res.headers.getSetCookie?.() || [];
  setCookieHeaders.forEach(cookie => {
    out.headers.append('Set-Cookie', cookie);
  });
  
  return out;
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;
