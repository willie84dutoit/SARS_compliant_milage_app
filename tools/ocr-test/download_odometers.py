"""Best-effort downloader for odometer photos from three stock-photo search pages.

Scrapes the initial HTML of each search results page for embedded image URLs and
downloads up to a cap per source. iStock and Adobe previews are watermarked and both
sites use bot protection, so those sources are best-effort; Pexels is the reliable one.

Images are saved numbered per source under: <output_root>/<source_name>/NN.jpg
A manifest CSV is written so the OCR test harness knows which file came from where.

Usage:
    python download_odometers.py [output_root]
"""

import csv
import os
import re
import sys
import time
import urllib.request
import urllib.error

MAXIMUM_IMAGES_PER_SOURCE = 50
REQUEST_TIMEOUT_SECONDS = 30
MINIMUM_IMAGE_BYTES = 2000

# Fuller browser-like headers; some stock sites 403 a bare urllib User-Agent.
REQUEST_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    ),
    # Deliberately NOT advertising image/avif or image/webp: Pexels' CDN content-negotiates
    # and will serve AVIF (which Android BitmapFactory cannot decode) if we claim to accept it.
    # Requesting JPEG keeps the downloaded files decodable on-device.
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/jpeg,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}

UNICODE_ESCAPE_PATTERN = re.compile(r"\\u([0-9a-fA-F]{4})")

# Each source: friendly name, search-results page URL, and a regex that matches the
# direct image URLs that site embeds in its HTML.
STOCK_PHOTO_SOURCES = [
    {
        "source_name": "istock",
        "search_page_url": "https://www.istockphoto.com/photos/odometer",
        "image_url_pattern": re.compile(
            r"https://media\.istockphoto\.com/id/[^\"'\s\\]+?\.(?:jpg|jpeg)(?:\\u0026[^\"'\s]*)?"
        ),
    },
    {
        "source_name": "adobe",
        "search_page_url": "https://stock.adobe.com/za/search?k=odometer",
        "image_url_pattern": re.compile(
            r"https://t[0-9]\.ftcdn\.net/jpg/[^\"'\s]+?\.(?:jpg|jpeg)"
        ),
    },
    {
        "source_name": "pexels",
        "search_page_url": "https://www.pexels.com/search/odometer/",
        "image_url_pattern": re.compile(
            r"https://images\.pexels\.com/photos/[0-9]+/[^\"'\s]+?\.(?:jpg|jpeg)[^\"'\s]*"
        ),
    },
]


def decode_unicode_escapes(escaped_text):
    """Turn JSON-style \\uXXXX escapes (e.g. \\u0026 for &) into real characters."""
    return UNICODE_ESCAPE_PATTERN.sub(
        lambda hex_match: chr(int(hex_match.group(1), 16)), escaped_text
    )


def fetch_page_html(page_url):
    """Return the decoded HTML for a page, or None on failure (failure is printed)."""
    http_request = urllib.request.Request(page_url, headers=REQUEST_HEADERS)
    try:
        with urllib.request.urlopen(http_request, timeout=REQUEST_TIMEOUT_SECONDS) as http_response:
            return http_response.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as fetch_error:
        print(f"  ! failed to fetch page {page_url}: {fetch_error}")
        return None


def extract_unique_image_urls(page_html, image_url_pattern):
    """Return de-duplicated, unescaped image URLs found in the HTML (first-seen order)."""
    seen_image_urls = set()
    ordered_image_urls = []
    for matched_image_url in image_url_pattern.findall(page_html):
        decoded_image_url = decode_unicode_escapes(matched_image_url)
        # Strip query strings when de-duplicating so the same photo at different sizes
        # is not counted twice, but keep the full URL for the actual download.
        base_image_url = decoded_image_url.split("?")[0]
        if base_image_url in seen_image_urls:
            continue
        seen_image_urls.add(base_image_url)
        ordered_image_urls.append(decoded_image_url)
    return ordered_image_urls


def download_single_image(image_url, destination_path):
    """Download one image to disk. Returns True on success; prints and returns False on failure."""
    http_request = urllib.request.Request(image_url, headers=REQUEST_HEADERS)
    try:
        with urllib.request.urlopen(http_request, timeout=REQUEST_TIMEOUT_SECONDS) as http_response:
            image_bytes = http_response.read()
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as download_error:
        print(f"    ! failed {image_url[:90]}...: {download_error}")
        return False
    if len(image_bytes) < MINIMUM_IMAGE_BYTES:
        print(f"    ! skipped tiny/placeholder ({len(image_bytes)} bytes)")
        return False
    with open(destination_path, "wb") as image_file:
        image_file.write(image_bytes)
    return True


def main():
    output_root = sys.argv[1] if len(sys.argv) > 1 else "odometer_images"
    os.makedirs(output_root, exist_ok=True)
    manifest_rows = []

    for source in STOCK_PHOTO_SOURCES:
        source_name = source["source_name"]
        print(f"\n=== {source_name} -> {source['search_page_url']} ===")
        source_output_directory = os.path.join(output_root, source_name)
        os.makedirs(source_output_directory, exist_ok=True)

        page_html = fetch_page_html(source["search_page_url"])
        if page_html is None:
            continue

        candidate_image_urls = extract_unique_image_urls(page_html, source["image_url_pattern"])
        print(f"  found {len(candidate_image_urls)} candidate image URLs in page HTML")

        downloaded_count = 0
        for candidate_image_url in candidate_image_urls:
            if downloaded_count >= MAXIMUM_IMAGES_PER_SOURCE:
                break
            image_number = downloaded_count + 1
            destination_path = os.path.join(source_output_directory, f"{image_number:02d}.jpg")
            if download_single_image(candidate_image_url, destination_path):
                downloaded_count += 1
                manifest_rows.append((source_name, image_number, destination_path, candidate_image_url))
                time.sleep(0.3)  # be gentle on the host
        print(f"  downloaded {downloaded_count} images into {source_output_directory}")

    manifest_path = os.path.join(output_root, "manifest.csv")
    with open(manifest_path, "w", newline="", encoding="utf-8") as manifest_file:
        manifest_writer = csv.writer(manifest_file)
        manifest_writer.writerow(["source_name", "image_number", "file_path", "source_url"])
        manifest_writer.writerows(manifest_rows)

    print(f"\nTotal downloaded: {len(manifest_rows)} images. Manifest: {manifest_path}")


if __name__ == "__main__":
    main()
