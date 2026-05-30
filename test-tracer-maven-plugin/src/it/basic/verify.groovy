// Phase 4 IT: assert the `report` aggregator produced an HTML index and that it picked up the
// seeded NDJSON file (1 file, 1 span line).

File site = new File(basedir, "target/test-tracer/site")
assert site.isDirectory(): "expected site directory at ${site}"

File index = new File(site, "index.html")
assert index.isFile(): "expected index.html at ${index}"

String html = index.text
assert html.contains("test-tracer report"): "index.html missing title; was:\n${html}"
assert html.contains("spans.ndjson"): "index.html should reference the seeded NDJSON; was:\n${html}"
assert html.contains("NDJSON files: <strong>1</strong>"): "expected exactly 1 NDJSON file detected; was:\n${html}"

return true
