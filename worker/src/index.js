import { AwsClient } from 'aws4fetch';

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === '/health') {
      return jsonResponse({ ok: true, service: 'fondi-photo-worker' });
    }

    if (url.pathname === '/upload' && request.method === 'POST') {
      const authError = await authenticate(request, env);
      if (authError) return authError;
      return handleUpload(request, env);
    }

    return jsonResponse({ ok: false, error: 'not_found' }, 404);
  }
};

async function authenticate(request, env) {
  const header = request.headers.get('authorization') || '';
  const prefix = 'Bearer ';
  if (!header.startsWith(prefix)) {
    return jsonResponse({ ok: false, error: 'unauthorized' }, 401);
  }
  const provided = header.slice(prefix.length).trim();
  const expected = env.APP_TOKEN;
  if (typeof expected !== 'string' || expected.length === 0) {
    // Misconfiguration: secret not set. Fail closed.
    return jsonResponse({ ok: false, error: 'server_misconfigured' }, 500);
  }
  const ok = await timingSafeEqual(provided, expected);
  if (!ok) {
    return jsonResponse({ ok: false, error: 'unauthorized' }, 401);
  }
  return null;
}

async function timingSafeEqual(a, b) {
  const enc = new TextEncoder();
  const aBytes = enc.encode(a);
  const bBytes = enc.encode(b);
  // timingSafeEqual requires equal length; pad to avoid length-based early exit.
  const len = Math.max(aBytes.length, bBytes.length);
  const aPadded = new Uint8Array(len);
  const bPadded = new Uint8Array(len);
  aPadded.set(aBytes);
  bPadded.set(bBytes);
  const equal = crypto.subtle.timingSafeEqual(aPadded, bPadded);
  return equal && aBytes.length === bBytes.length;
}

async function handleUpload(request, env) {
  const contentLength = request.headers.get('content-length');
  if (contentLength && parseInt(contentLength) > MAX_UPLOAD_BYTES) {
    return jsonResponse({ ok: false, error: 'too_large' }, 413);
  }

  const bodyBuffer = await request.arrayBuffer();
  if (bodyBuffer.byteLength > MAX_UPLOAD_BYTES) {
    return jsonResponse({ ok: false, error: 'too_large' }, 413);
  }

  const clientHash = request.headers.get('x-content-sha256');
  if (!clientHash || !/^[a-f0-9]{64}$/i.test(clientHash)) {
    return jsonResponse({ ok: false, error: 'bad_hash' }, 400);
  }

  const serverHash = await sha256Hex(bodyBuffer);
  if (serverHash !== clientHash.toLowerCase()) {
    return jsonResponse({ ok: false, error: 'checksum_mismatch' }, 400);
  }

  const ext = detectExtension(bodyBuffer);
  if (!ext) {
    return jsonResponse({ ok: false, error: 'unsupported_type' }, 400);
  }

  const id = env.UPLOAD_COORDINATOR.idFromName(serverHash);
  const stub = env.UPLOAD_COORDINATOR.get(id);

  const result = await stub.fetch('https://do/coordinate', {
    method: 'POST',
    body: bodyBuffer,
    headers: {
      'x-hash': serverHash,
      'x-ext': ext,
      'x-content-type': ext === 'jpg' ? 'image/jpeg' : 'image/png'
    }
  });

  const data = await result.json();
  return jsonResponse(data, result.status);
}

export class UploadCoordinator {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname !== '/coordinate') {
      return new Response('not found', { status: 404 });
    }

    const hash = request.headers.get('x-hash');
    const ext = request.headers.get('x-ext');
    const contentType = request.headers.get('x-content-type');
    const body = await request.arrayBuffer();

    const key = `photos/${hash}.${ext}`;

    const existing = await this.ctx.storage.get('uploaded');
    if (existing === true) {
      return jsonResponse({ ok: true, duplicate: true, id: key }, 200);
    }

    const uploadResult = await uploadToB2(this.env, key, body, contentType);
    if (!uploadResult.ok) {
      return jsonResponse({ ok: false, error: 'storage_error', detail: uploadResult.error }, 502);
    }

    await this.ctx.storage.put('uploaded', true);

    return jsonResponse({ ok: true, duplicate: false, id: key }, 201);
  }
}

async function uploadToB2(env, key, body, contentType) {
  const aws = new AwsClient({
    accessKeyId: env.B2_APPLICATION_KEY_ID,
    secretAccessKey: env.B2_APPLICATION_KEY,
    service: 's3',
    region: env.B2_REGION
  });

  const endpoint = `https://${env.B2_BUCKET}.${env.B2_ENDPOINT}/${key}`;

  try {
    const res = await aws.fetch(endpoint, {
      method: 'PUT',
      body: body,
      headers: {
        'Content-Type': contentType
        // Deliberately NOT sending x-amz-checksum-* — B2 S3-compatible API rejects it
      }
    });

    if (!res.ok) {
      const text = await res.text();
      return { ok: false, error: `b2_${res.status}: ${text.slice(0, 200)}` };
    }

    return { ok: true };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

function detectExtension(buffer) {
  const bytes = new Uint8Array(buffer.slice(0, 4));
  if (bytes[0] === 0xFF && bytes[1] === 0xD8 && bytes[2] === 0xFF) return 'jpg';
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4E && bytes[3] === 0x47) return 'png';
  return null;
}

async function sha256Hex(buffer) {
  const hash = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(hash))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

function jsonResponse(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}