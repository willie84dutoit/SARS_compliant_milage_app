package com.mileagetracker.app.domain.export

/**
 * T-032 Half A / Pass 2: the static, human-readable `VERIFY.md` recipe written alongside every
 * CSV export's `.integrity.json` sidecar. Content is fixed (does not depend on any particular
 * export's trips), so this is a single constant rather than a per-export builder — the sidecar
 * JSON's own `csvFilename` field is what ties a specific verification run to a specific export.
 *
 * Encoding pins documented here MUST match [IntegritySidecarGenerator] and
 * [com.mileagetracker.app.data.signing.TripSigner.buildCanonicalPayload] exactly — this is
 * read by a human (or a SARS auditor's script) outside the app, so drift here is silent and
 * undetectable until someone tries to verify a real export and fails.
 */
object VerificationRecipeDocument {

    /** Full Markdown content of `VERIFY.md`, UTF-8. */
    val content: String = buildString {
        append("# Verifying a Mileage Tracker integrity sidecar\n\n")
        append(
            "This recipe verifies the `.integrity.json` sidecar that accompanies a CSV export " +
                "from the Automated Mileage Tracker app. It proves two things about each listed " +
                "signed trip:\n\n",
        )
        append("1. The trip's recorded data was not altered after it was signed on-device.\n")
        append("2. The signed trips form an unbroken hash chain in completion order.\n\n")
        append(
            "It does **not** prove that no trips were deleted from the end of the chain — see " +
                "Limitations below.\n\n",
        )

        append("## Encoding pins (must match exactly)\n\n")
        append("- Signature algorithm: `SHA256withECDSA`\n")
        append("- Signature encoding: `DER`\n")
        append("- Curve: `P-256` (`secp256r1`)\n")
        append(
            "- Signature Base64: standard Base64 alphabet, **no padding** — re-pad with `=` " +
                "characters to a multiple of 4 before decoding with a strict Base64 decoder.\n",
        )
        append(
            "- Canonical payload: UTF-8 bytes of the `canonicalPayload` string field, signed " +
                "and verified as-is (do not re-serialize it).\n",
        )
        append(
            "- Chain tail: `computedTail(n) = lowercase_hex(SHA-256(utf8(signatureBase64(n))))`; " +
                "`prevTail(n) = computedTail(n-1)`; the genesis (first) trip's `prevTail` is `null`.\n\n",
        )

        append("## Option A: Python (`cryptography` library)\n\n")
        append("```python\n")
        append("import base64, hashlib, json\n")
        append("from cryptography.hazmat.primitives import hashes, serialization\n")
        append("from cryptography.hazmat.primitives.asymmetric import ec\n")
        append("from cryptography.exceptions import InvalidSignature\n\n")
        append("with open(\"mileage_trips_YYYYMMDD_HHMMSS.integrity.json\") as sidecar_file:\n")
        append("    sidecar = json.load(sidecar_file)\n\n")
        append("public_key = serialization.load_pem_public_key(\n")
        append("    sidecar[\"publicKeyPem\"].encode(\"utf-8\"),\n")
        append(")\n\n")
        append("def repad_base64(value: str) -> str:\n")
        append("    return value + \"=\" * (-len(value) % 4)\n\n")
        append("previous_computed_tail = None\n")
        append("for trip_entry in sidecar[\"signedTrips\"]:\n")
        append("    signature_bytes = base64.b64decode(repad_base64(trip_entry[\"signatureBase64\"]))\n")
        append("    payload_bytes = trip_entry[\"canonicalPayload\"].encode(\"utf-8\")\n")
        append("    try:\n")
        append("        public_key.verify(signature_bytes, payload_bytes, ec.ECDSA(hashes.SHA256()))\n")
        append("        signature_ok = True\n")
        append("    except InvalidSignature:\n")
        append("        signature_ok = False\n\n")
        append(
            "    computed_tail = hashlib.sha256(trip_entry[\"signatureBase64\"].encode(\"utf-8\"))" +
                ".hexdigest()\n",
        )
        append("    chain_ok = (\n")
        append("        trip_entry[\"prevTail\"] == previous_computed_tail\n")
        append("        and computed_tail == trip_entry[\"computedTail\"]\n")
        append("    )\n")
        append(
            "    print(trip_entry[\"tripId\"], \"signature_ok=\", signature_ok, \"chain_ok=\", chain_ok)\n",
        )
        append("    previous_computed_tail = computed_tail\n")
        append("```\n\n")

        append("## Option B: openssl CLI (per-trip spot check)\n\n")
        append("```bash\n")
        append("# Extract the public key\n")
        append("jq -r '.publicKeyPem' mileage_trips_YYYYMMDD_HHMMSS.integrity.json > pubkey.pem\n\n")
        append("# Extract one trip's payload and signature (index 0 = first signed trip)\n")
        append(
            "jq -r '.signedTrips[0].canonicalPayload' mileage_trips_YYYYMMDD_HHMMSS.integrity.json " +
                "| tr -d '\\n' > payload.bin\n",
        )
        append(
            "jq -r '.signedTrips[0].signatureBase64' mileage_trips_YYYYMMDD_HHMMSS.integrity.json " +
                "> sig_base64_nopad.txt\n\n",
        )
        append("# Re-pad the no-padding Base64 signature to a multiple of 4 with '='\n")
        append("python3 -c \"\n")
        append("import sys\n")
        append("value = open('sig_base64_nopad.txt').read().strip()\n")
        append("padded = value + '=' * (-len(value) % 4)\n")
        append("sys.stdout.write(padded)\n")
        append("\" > sig_base64_padded.txt\n")
        append("base64 -d sig_base64_padded.txt > sig.der\n\n")
        append("# Verify (DER signature, SHA-256, against the extracted payload bytes)\n")
        append("openssl dgst -sha256 -verify pubkey.pem -signature sig.der payload.bin\n")
        append("```\n\n")

        append("## Limitations\n\n")
        append(
            "- **Key lifecycle:** signing keys are hardware-backed and non-exportable; they are " +
                "destroyed if the app is uninstalled or its data is cleared. Trips signed before " +
                "such an event cannot be verified against the public key in a sidecar generated " +
                "after that event. A future server-side key registry (Phase 2) removes this " +
                "limitation.\n",
        )
        append(
            "- **End-of-chain truncation:** this file proves that each listed signed trip was " +
                "not individually altered after signing, and that the listed trips form an " +
                "unbroken hash chain. It does NOT prove that no trips were deleted from the end " +
                "of the chain on the device. Detecting end-of-chain truncation requires the " +
                "server-side append-only record (Phase 2).\n",
        )
        append(
            "- **Unsigned trips:** any trip listed under `unsignedTrips` was exported but could " +
                "not be signed at completion time (Keystore failure) — it is not part of the " +
                "integrity chain and is not cryptographically verifiable.\n",
        )
    }
}
