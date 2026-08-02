#!/usr/bin/env python3
"""Probe one internal HTTPS endpoint and verify its SPIFFE URI SAN."""

from __future__ import annotations

import argparse
import http.client
import socket
import ssl
from collections.abc import Mapping, Sequence


def build_ssl_context(cert_file: str, key_file: str, ca_file: str) -> ssl.SSLContext:
    context = ssl.create_default_context()
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.check_hostname = True
    context.verify_mode = ssl.CERT_REQUIRED
    context.load_cert_chain(cert_file, key_file)
    context.load_verify_locations(cafile=ca_file)
    return context


def require_spiffe_identity(
    peer_certificate: Mapping[str, object], expected_identity: str
) -> None:
    raw_names = peer_certificate.get("subjectAltName", ())
    names = raw_names if isinstance(raw_names, Sequence) else ()
    identities = {
        value
        for kind, value in names
        if kind == "URI" and isinstance(value, str)
    }
    if expected_identity not in identities:
        raise ValueError(f"peer certificate does not contain expected SPIFFE identity")


def probe(
    host: str,
    port: int,
    server_name: str,
    path: str,
    cert_file: str,
    key_file: str,
    ca_file: str,
    expected_identity: str,
    timeout_seconds: float,
) -> int:
    context = build_ssl_context(cert_file, key_file, ca_file)
    with socket.create_connection((host, port), timeout=timeout_seconds) as raw_socket:
        with context.wrap_socket(raw_socket, server_hostname=server_name) as tls_socket:
            require_spiffe_identity(tls_socket.getpeercert(), expected_identity)
            request = (
                f"GET {path} HTTP/1.1\r\n"
                f"Host: {server_name}\r\n"
                "Connection: close\r\n\r\n"
            )
            tls_socket.sendall(request.encode("ascii"))
            response = http.client.HTTPResponse(tls_socket)
            response.begin()
            response.read()
    if response.status < 200 or response.status >= 400:
        raise RuntimeError(f"internal TLS probe returned HTTP {response.status}")
    return response.status


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--server-name", required=True)
    parser.add_argument("--path", default="/health")
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--ca", required=True)
    parser.add_argument("--expected-spiffe", required=True)
    parser.add_argument("--timeout", type=float, default=5.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    status = probe(
        host=args.host,
        port=args.port,
        server_name=args.server_name,
        path=args.path,
        cert_file=args.cert,
        key_file=args.key,
        ca_file=args.ca,
        expected_identity=args.expected_spiffe,
        timeout_seconds=args.timeout,
    )
    print(f"internal TLS handshake verified (HTTP {status})")


if __name__ == "__main__":
    main()
