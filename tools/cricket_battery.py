#!/usr/bin/env python3
"""
Bateria de testes em tempo de execução do Cricket (página 0 do ylauncher).

Exercita o control-plane da Ruby exatamente como o cliente do app faz:
REST (RubyApi), SSE (RubyEventStream) e WebSocket /ws (RubyChatSocket).
Só usa a stdlib — nenhuma dependência.

SEGURANÇA (por design):
  * Sem token: só conectividade + contrato de auth (não-mutante).
  * Com token (--token / env CRICKET_TOKEN): leituras + SSE + abrir o /ws.
    O /ws é só ABERTO e escutado — nunca manda `falar`/`aprovar`.
  * --danger: adiciona um round-trip criar+excluir conversa (auto-limpa).
  * NUNCA roda sozinho: decidir (decisão real), disparar (dispara trabalho),
    falar/aprovar no /ws (gasta cota da assinatura). Aparecem como PULADO.

Uso:
  python3 tools/cricket_battery.py                 # camadas sem token
  CRICKET_TOKEN=xxxx python3 tools/cricket_battery.py
  python3 tools/cricket_battery.py --token xxx --danger
  python3 tools/cricket_battery.py --base http://192.168.0.30:8080 --sse-secs 8
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import http.client
import json
import os
import socket
import sys
import time
from urllib.parse import urlparse

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"  # RFC 6455

# ---- saída bonitinha ---------------------------------------------------------
class C:
    G = "\033[32m"; R = "\033[31m"; Y = "\033[33m"; DIM = "\033[2m"; B = "\033[1m"; X = "\033[0m"

def _c(s, col):
    return f"{col}{s}{C.X}" if sys.stdout.isatty() else s

results = []  # (nome, status, detalhe, ms)

def record(nome, status, detalhe="", ms=None):
    results.append((nome, status, detalhe, ms))
    tag = {"PASS": _c("PASS", C.G), "FAIL": _c("FAIL", C.R),
           "WARN": _c("WARN", C.Y), "SKIP": _c("SKIP", C.DIM)}[status]
    t = f" {_c(f'{ms:>5.0f}ms', C.DIM)}" if ms is not None else " " * 8
    print(f"  [{tag}]{t}  {_c(nome, C.B)}" + (f"  {_c('· ' + detalhe, C.DIM)}" if detalhe else ""))

# ---- REST (espelha RubyApi.raw/authed) --------------------------------------
def http_req(base, method, path, token=None, body=None, timeout=6.0):
    """Devolve (code, texto, ms). code=-1 = inalcançável (igual RubyResult.Unreachable)."""
    u = urlparse(base)
    t0 = time.time()
    try:
        conn = http.client.HTTPConnection(u.hostname, u.port or 80, timeout=timeout)
        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        conn.request(method, path, body=data, headers=headers)
        r = conn.getresponse()
        txt = r.read().decode("utf-8", "replace")
        conn.close()
        return r.status, txt, (time.time() - t0) * 1000
    except Exception as e:
        return -1, f"{type(e).__name__}: {e}", (time.time() - t0) * 1000

def is_unix_seconds(ts):
    """True se `ts` parece unix-segundos plausível (não millis, não zero, perto de agora)."""
    if not isinstance(ts, (int, float)) or ts <= 0:
        return False
    now = time.time()
    # segundos: mesma ordem de grandeza que now (~1.7e9); millis seriam ~1.7e12
    return 1_000_000_000 < ts < now + 86_400 and ts < 1e12

# ---- camada 0/1: sem token ---------------------------------------------------
def layer_connect(base):
    print(_c("\n▸ conectividade & contrato de auth (sem token)", C.B))
    code, txt, ms = http_req(base, "GET", "/v1/frota")
    if code == -1:
        record("dell_alcancavel", "FAIL", f"inalcançável: {txt}", ms)
        return False
    record("dell_alcancavel", "PASS", f"respondeu HTTP {code}", ms)

    # /v1/frota é protegida → sem token deve dar 401
    if code == 401:
        record("auth_exigida", "PASS", "frota sem token → 401", ms)
    else:
        record("auth_exigida", "WARN", f"esperava 401 sem token, veio {code}", ms)

    # parear com código falso → 401 (código errado ou já usado)
    code, txt, ms = http_req(base, "POST", "/v1/parear",
                             body={"codigo": "codigo-invalido-bateria", "nome": "bateria-teste"})
    if code == 401:
        record("parear_rejeita_codigo_falso", "PASS", "→ 401", ms)
    elif code == -1:
        record("parear_rejeita_codigo_falso", "FAIL", txt, ms)
    else:
        record("parear_rejeita_codigo_falso", "WARN", f"esperava 401, veio {code}", ms)
    return True

# ---- camada 2: leituras autenticadas ----------------------------------------
def expect_json(base, path, token, want_array, required_keys=None, sample_ts_keys=None):
    code, txt, ms = http_req(base, "GET", path, token=token)
    if code == -1:
        return record(path, "FAIL", txt, ms) or None
    if code == 401:
        return record(path, "FAIL", "401 — token inválido/expirado", ms) or None
    if code != 200:
        return record(path, "FAIL", f"HTTP {code}", ms) or None
    try:
        j = json.loads(txt)
    except Exception as e:
        return record(path, "FAIL", f"JSON inválido: {e}", ms) or None
    if want_array and not isinstance(j, list):
        return record(path, "FAIL", f"esperava array, veio {type(j).__name__}", ms) or None
    if not want_array and not isinstance(j, dict):
        return record(path, "FAIL", f"esperava objeto, veio {type(j).__name__}", ms) or None

    sample = (j[0] if (want_array and j) else (j if not want_array else None))
    missing = []
    if required_keys and isinstance(sample, dict):
        missing = [k for k in required_keys if k not in sample]
    n = len(j) if want_array else 1
    if missing:
        record(path, "WARN", f"{n} item(s), faltam chaves {missing}", ms)
    else:
        record(path, "PASS", f"{n} item(s), shape ok", ms)

    # checagem de unidade de timestamp
    if sample_ts_keys and isinstance(sample, dict):
        for k in sample_ts_keys:
            if k in sample and sample[k]:
                ok = is_unix_seconds(sample[k])
                record(f"{path} · {k} em segundos", "PASS" if ok else "WARN",
                       f"{sample[k]}" + ("" if ok else " (parece millis/zero)"))
    return j

def layer_reads(base, token):
    print(_c("\n▸ leituras do snapshot (com token)", C.B))
    expect_json(base, "/v1/frota", token, want_array=False,
                required_keys=["agora", "maquinas", "sessoes"], sample_ts_keys=["agora"])
    expect_json(base, "/v1/pedidos", token, want_array=True,
                required_keys=["id", "titulo", "estado", "criado_em"], sample_ts_keys=["criado_em"])
    expect_json(base, "/v1/despachos", token, want_array=True,
                required_keys=["id", "fluxo", "estado", "criada_em", "mudou_em"],
                sample_ts_keys=["criada_em", "mudou_em"])
    # fluxos = array de strings
    code, txt, ms = http_req(base, "GET", "/v1/fluxos", token=token)
    if code == 200:
        try:
            arr = json.loads(txt)
            ok = isinstance(arr, list) and all(isinstance(x, str) for x in arr)
            record("/v1/fluxos", "PASS" if ok else "WARN",
                   f"{len(arr)} fluxo(s)" if ok else "não é array de strings", ms)
        except Exception as e:
            record("/v1/fluxos", "FAIL", f"JSON: {e}", ms)
    else:
        record("/v1/fluxos", "FAIL" if code != -1 else "FAIL", f"HTTP {code}", ms)
    expect_json(base, "/v1/conversas", token, want_array=True,
                required_keys=["id", "criada_em"], sample_ts_keys=["criada_em"])

# ---- camada 3: SSE /v1/eventos ----------------------------------------------
def layer_sse(base, token, secs):
    print(_c(f"\n▸ SSE /v1/eventos (escuta {secs}s, read-only)", C.B))
    u = urlparse(base)
    t0 = time.time()
    try:
        s = socket.create_connection((u.hostname, u.port or 80), timeout=6.0)
    except Exception as e:
        return record("sse_conecta", "FAIL", f"{type(e).__name__}: {e}")
    req = (f"GET /v1/eventos HTTP/1.1\r\nHost: {u.hostname}:{u.port}\r\n"
           f"Accept: text/event-stream\r\nAuthorization: Bearer {token}\r\nConnection: keep-alive\r\n\r\n")
    s.sendall(req.encode())
    s.settimeout(1.0)
    buf = b""
    status_line = None
    header_done = False
    frames, tipos = 0, {}
    bad = 0
    deadline = time.time() + secs
    try:
        while time.time() < deadline:
            try:
                chunk = s.recv(4096)
            except socket.timeout:
                continue
            if not chunk:
                break
            buf += chunk
            if not header_done:
                if b"\r\n\r\n" in buf:
                    head, buf = buf.split(b"\r\n\r\n", 1)
                    status_line = head.split(b"\r\n", 1)[0].decode("ascii", "replace")
                    header_done = True
                else:
                    continue
            # eventos SSE separados por linha em branco; interessa `data:`
            while b"\n\n" in buf:
                block, buf = buf.split(b"\n\n", 1)
                for line in block.split(b"\n"):
                    line = line.strip()
                    if line.startswith(b"data:"):
                        payload = line[5:].strip()
                        frames += 1
                        try:
                            tp = json.loads(payload).get("tipo", "?")
                        except Exception:
                            tp = None
                        if tp is None:
                            bad += 1
                        else:
                            tipos[tp] = tipos.get(tp, 0) + 1
    finally:
        s.close()
    ms = (time.time() - t0) * 1000
    if status_line and "200" not in status_line:
        return record("sse_conecta", "FAIL", f"handshake: {status_line}", ms)
    record("sse_conecta", "PASS", status_line or "conectado", ms)
    resumo = ", ".join(f"{k}×{v}" for k, v in sorted(tipos.items())) or "nenhum"
    if frames == 0:
        record("sse_recebe_eventos", "WARN", f"nada em {secs}s (pode estar quieto; sem pulso?)")
    else:
        record("sse_recebe_eventos", "PASS", f"{frames} frame(s): {resumo}")
    if bad:
        record("sse_frames_bem_formados", "WARN", f"{bad} frame(s) sem `tipo`/JSON inválido")
    else:
        record("sse_frames_bem_formados", "PASS", "todo frame é JSON com `tipo`")

# ---- camada 4: WebSocket /ws (abre e escuta, NÃO manda nada) -----------------
def layer_ws(base, token, listen_secs=3.0):
    print(_c(f"\n▸ WebSocket /ws (handshake + escuta {listen_secs:.0f}s; NÃO manda falar)", C.B))
    u = urlparse(base)
    key_b64 = base64.b64encode(os.urandom(16)).decode()
    expected_accept = base64.b64encode(
        hashlib.sha1((key_b64 + WS_GUID).encode()).digest()).decode()
    t0 = time.time()
    try:
        s = socket.create_connection((u.hostname, u.port or 80), timeout=6.0)
    except Exception as e:
        return record("ws_conecta", "FAIL", f"{type(e).__name__}: {e}")
    req = (f"GET /ws HTTP/1.1\r\nHost: {u.hostname}:{u.port}\r\n"
           f"Upgrade: websocket\r\nConnection: Upgrade\r\n"
           f"Sec-WebSocket-Key: {key_b64}\r\nSec-WebSocket-Version: 13\r\n"
           f"Authorization: Bearer {token}\r\n\r\n")
    s.sendall(req.encode())
    s.settimeout(6.0)
    # lê a resposta do handshake até a linha em branco
    buf = b""
    try:
        while b"\r\n\r\n" not in buf:
            chunk = s.recv(1024)
            if not chunk:
                break
            buf += chunk
    except socket.timeout:
        pass
    head, _, rest = buf.partition(b"\r\n\r\n")
    ms = (time.time() - t0) * 1000
    head_txt = head.decode("ascii", "replace")
    status = head_txt.split("\r\n", 1)[0] if head_txt else ""
    if "101" not in status:
        s.close()
        return record("ws_handshake_101", "FAIL", status or "sem resposta", ms)
    record("ws_handshake_101", "PASS", status, ms)
    # verifica Sec-WebSocket-Accept
    acc = None
    for line in head_txt.split("\r\n")[1:]:
        if line.lower().startswith("sec-websocket-accept:"):
            acc = line.split(":", 1)[1].strip()
    if acc == expected_accept:
        record("ws_accept_confere", "PASS", "Sec-WebSocket-Accept correto")
    else:
        record("ws_accept_confere", "WARN", f"accept={acc!r} esperado={expected_accept!r}")

    # escuta frames de servidor (não-mascarados) por um tempo — saudação/connected
    s.settimeout(1.0)
    got_text = 0
    preview = ""
    deadline = time.time() + listen_secs
    stream = rest
    while time.time() < deadline:
        try:
            chunk = s.recv(4096)
            if not chunk:
                break
            stream += chunk
        except socket.timeout:
            pass
        # decodifica frames simples (servidor→cliente não mascara)
        while len(stream) >= 2:
            b0, b1 = stream[0], stream[1]
            opcode = b0 & 0x0F
            ln = b1 & 0x7F
            off = 2
            if ln == 126:
                if len(stream) < 4: break
                ln = int.from_bytes(stream[2:4], "big"); off = 4
            elif ln == 127:
                if len(stream) < 10: break
                ln = int.from_bytes(stream[2:10], "big"); off = 10
            if len(stream) < off + ln:
                break
            payload = stream[off:off + ln]
            stream = stream[off + ln:]
            if opcode == 0x1:  # text
                got_text += 1
                if not preview:
                    preview = payload.decode("utf-8", "replace")[:80]
    # fecha educadamente (frame close mascarado)
    try:
        mask = os.urandom(4)
        s.sendall(bytes([0x88, 0x80]) + mask)
    except Exception:
        pass
    s.close()
    if got_text:
        record("ws_recebe_saudacao", "PASS", f"{got_text} frame(s) texto · {preview!r}")
    else:
        record("ws_recebe_saudacao", "WARN", "abriu mas não mandou nada em silêncio (ok se só responde a falar)")

# ---- camada 5: --danger (round-trip conversa, auto-limpa) --------------------
def layer_danger(base, token):
    print(_c("\n▸ --danger: round-trip criar+excluir conversa (auto-limpa)", C.B))
    titulo = f"bateria-{int(time.time())}"
    code, txt, ms = http_req(base, "POST", "/v1/conversas", token=token, body={"titulo": titulo})
    if code not in (200, 201):
        return record("conversa_criar", "FAIL", f"HTTP {code}: {txt[:80]}", ms)
    try:
        cid = json.loads(txt).get("id")
    except Exception as e:
        return record("conversa_criar", "FAIL", f"JSON: {e}", ms)
    if not cid:
        return record("conversa_criar", "FAIL", "sem `id` na resposta", ms)
    record("conversa_criar", "PASS", f"id={cid}", ms)

    code, txt, ms = http_req(base, "DELETE", f"/v1/conversas/{cid}", token=token)
    if code in (200, 204, 404):
        record("conversa_excluir", "PASS", f"HTTP {code}", ms)
    else:
        record("conversa_excluir", "FAIL", f"HTTP {code}: {txt[:80]}", ms)

    # confirma que sumiu
    code, txt, _ = http_req(base, "GET", "/v1/conversas", token=token)
    if code == 200:
        try:
            ainda = any(c.get("id") == cid for c in json.loads(txt))
            record("conversa_sumiu", "PASS" if not ainda else "FAIL",
                   "não está mais na lista" if not ainda else "ainda aparece!")
        except Exception as e:
            record("conversa_sumiu", "WARN", f"JSON: {e}")

def skipped_destructive():
    print(_c("\n▸ nunca automático (decisão real / gasta cota da assinatura)", C.B))
    record("decidir_pedido", "SKIP", "decisão real — retoma o fluxo, gasta cota")
    record("disparar_fluxo", "SKIP", "dispara trabalho real na frota")
    record("ws_falar / ws_aprovar", "SKIP", "gasta cota; servidor é da sessão irmã")

# ---- main -------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description="Bateria de runtime do Cricket")
    ap.add_argument("--base", default=os.environ.get("CRICKET_BASE", "http://192.168.0.30:8080"))
    ap.add_argument("--token", default=os.environ.get("CRICKET_TOKEN"))
    ap.add_argument("--sse-secs", type=float, default=8.0)
    ap.add_argument("--danger", action="store_true", help="round-trip criar+excluir conversa")
    args = ap.parse_args()

    print(_c(f"\nBateria do Cricket → {args.base}", C.B) +
          _c(f"   token: {'sim' if args.token else 'não'}", C.DIM))

    alcancou = layer_connect(args.base)
    if alcancou and args.token:
        layer_reads(args.base, args.token)
        layer_sse(args.base, args.token, args.sse_secs)
        layer_ws(args.base, args.token)
        if args.danger:
            layer_danger(args.base, args.token)
    elif alcancou:
        print(_c("\n  (sem token: pulando leituras/SSE/WS. Passe --token ou CRICKET_TOKEN.)", C.DIM))
    skipped_destructive()

    # resumo
    n = {"PASS": 0, "FAIL": 0, "WARN": 0, "SKIP": 0}
    for _, st, _, _ in results:
        n[st] += 1
    print(_c("\n── resumo ──", C.B))
    print(f"  {_c(str(n['PASS']) + ' PASS', C.G)}   "
          f"{_c(str(n['FAIL']) + ' FAIL', C.R)}   "
          f"{_c(str(n['WARN']) + ' WARN', C.Y)}   "
          f"{_c(str(n['SKIP']) + ' SKIP', C.DIM)}\n")
    sys.exit(1 if n["FAIL"] else 0)

if __name__ == "__main__":
    main()
