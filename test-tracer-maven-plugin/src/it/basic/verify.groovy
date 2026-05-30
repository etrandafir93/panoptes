// Phase 5/6 IT: assert the `report` aggregator produced a full HTML site.
// Two seeded spans: one test root (MyTest#myMethod, OK) and one orphan span.

File site = new File(basedir, "target/test-tracer/site")
assert site.isDirectory(): "expected site directory at ${site}"

// index.html must exist and reference the test
File index = new File(site, "index.html")
assert index.isFile(): "expected index.html"
String indexHtml = index.text
assert indexHtml.contains("panoptes test-tracer report"): "index.html missing title"
assert indexHtml.contains("MyTest#myMethod"): "index.html should list the seeded test; was:\n${indexHtml}"
assert indexHtml.contains("PASS"): "index.html should show PASS badge; was:\n${indexHtml}"

// Per-test detail page
File detail = new File(site, "aaaa0000000000000000000000000001.html")
assert detail.isFile(): "expected detail page for trace aaaa0000000000000000000000000001"
String detailHtml = detail.text
assert detailHtml.contains("MyTest#myMethod"): "detail page should show test name; was:\n${detailHtml}"
assert detailHtml.contains("waterfall"): "detail page should contain waterfall; was:\n${detailHtml}"
assert detailHtml.contains("Sequence diagram"): "detail page should contain sequence diagram section; was:\n${detailHtml}"
assert detailHtml.contains("<svg"): "detail page should contain an inline SVG sequence diagram; was:\n${detailHtml}"

// Orphan page
File orphans = new File(site, "orphans.html")
assert orphans.isFile(): "expected orphans.html for the seeded orphan span"
String orphanHtml = orphans.text
assert orphanHtml.contains("orphan-span"): "orphans.html should list orphan-span; was:\n${orphanHtml}"

return true
