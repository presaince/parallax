<?php
/**
 * GlassTube bug drop box.
 *
 * The app POSTs one JSON bundle per human tap to this endpoint. It is written into a directory
 * beside this one, so a report is never served as a file at a guessable URL: you read these over
 * the same FTP account the site is deployed with, and nothing else can.
 *
 * Setup: upload this file to other_apps/glasstube/upload.php. Nothing else — the report directory
 * is created on first upload, along with an .htaccess that denies HTTP access to it.
 *
 * NO SHARED SECRET, ON PURPOSE. A token baked into the APK is extractable by anyone who unpacks
 * it, so it guards nothing while adding a setup step to every install. What limits abuse instead:
 *   - per-IP rate limit (REMOTE_ADDR, never X-Forwarded-For, which is trivially spoofed)
 *   - a hard body cap and a schema check, so it cannot be used as arbitrary file upload
 *   - a filename forced to a flat sanitized .json inside one fixed directory
 *   - an age-based prune of the directory, so total disk use is bounded without ever touching
 *     a recent report
 *
 * Reports land at other_apps/glasstube_reports/<device>_<versionCode>_<timestamp>.json and are
 * never overwritten: a second report from the same phone is a second file.
 */

const REPORT_DIR = __DIR__ . '/../glasstube_reports';
const MAX_BYTES = 8 * 1024 * 1024;

// Per-IP budget: one upload every 3s, ten a minute, then a stop. Filing several reports in a row
// is normal, so the burst is generous; the cap only keeps a script from filling the directory.
const BURST_SEC = 3;
const PER_MINUTE = 10;

// Reports are kept for half a year. 0 disables pruning (manual retention).
const KEEP_DAYS = 180;

header('Content-Type: application/json');
header('Cache-Control: no-store');

function fail(string $why, int $code): void {
    http_response_code($code);
    // Never surface a PHP notice/warning: a 500 with a path in it tells a prober the layout.
    echo json_encode(['ok' => false, 'error' => $why]);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    header('Allow: POST');
    fail('POST only', 405);
}

/**
 * Per-IP sliding window in the system temp dir: writable on shared hosting, not reachable over
 * HTTP. Two gates: a minimum gap between uploads and a per-minute cap. Keyed on REMOTE_ADDR,
 * because X-Forwarded-For is attacker-controlled.
 */
function rate_ok(string $file, int $now): bool {
    $window = $now - 60;
    $stamps = [];
    if (is_readable($file)) {
        $raw = @file_get_contents($file);
        if (is_string($raw)) {
            $decoded = json_decode($raw, true);
            if (is_array($decoded)) {
                foreach ($decoded as $t) {
                    if (is_int($t) && $t > $window) $stamps[] = $t;
                }
            }
        }
    }
    if (count($stamps) >= PER_MINUTE) return false;
    if ($stamps !== [] && ($now - max($stamps)) < BURST_SEC) return false;

    $stamps[] = $now;
    // Fail CLOSED if the counter cannot be persisted: a silently-unwritable temp dir would leave
    // the endpoint effectively unthrottled, which is worse than an error.
    return @file_put_contents($file, json_encode($stamps), LOCK_EX) !== false;
}

try {
    if (!is_writable(sys_get_temp_dir())) {
        error_log('glasstube upload.php: rate-limit dir not writable');
        fail('rate limiting unavailable', 503);
    }

    $ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
    $now = time();
    $slot = rtrim(sys_get_temp_dir(), '/') . '/glasstube_reports_rate_'
        . substr(sha1((string) $ip), 0, 16) . '.json';
    if (!rate_ok($slot, $now)) {
        header('Retry-After: ' . BURST_SEC);
        fail('too many uploads from this address', 429);
    }

    $body = file_get_contents('php://input');
    if ($body === false || $body === '') {
        fail('empty body', 400);
    }
    if (strlen($body) > MAX_BYTES) {
        fail('too large', 413);
    }

    $decoded = json_decode($body, true);
    if (!is_array($decoded) || !isset($decoded['schema'])) {
        fail('not a report bundle', 400);
    }
    if (strpos((string) $decoded['schema'], 'glasstube.bug') !== 0) {
        fail('not a glasstube report', 400);
    }

    // Never trust the client with a path. Keep a flat safe charset with no separators.
    $name = preg_replace('/[^A-Za-z0-9._-]/', '', (string) ($_SERVER['HTTP_X_LOG_NAME'] ?? ''));
    if ($name === '' || !str_ends_with($name, '.json')) {
        $name = 'unknown_' . $now . '.json';
    }

    if (!is_dir(REPORT_DIR) && !@mkdir(REPORT_DIR, 0700, true) && !is_dir(REPORT_DIR)) {
        fail('storage unavailable', 500);
    }

    // The directory sits under the hosted tree, so it is denied over HTTP rather than moved: the
    // FTP account can still read it, and that is the only reader there should be. Written on
    // every upload because a fresh directory has no file yet and an operator may remove it.
    $htaccess = REPORT_DIR . '/.htaccess';
    if (!is_file($htaccess)) {
        @file_put_contents($htaccess, "Require all denied\nDeny from all\nOptions -Indexes\n");
    }

    // Bound total disk use without ever touching a recent report.
    if (KEEP_DAYS > 0) {
        $cutoff = $now - (KEEP_DAYS * 86400);
        foreach ((array) @glob(REPORT_DIR . '/*.json') as $old) {
            // filemtime() returns false on failure, and false coerces to 0 — which is < $cutoff,
            // so a bare comparison would delete exactly the files it cannot stat. Never.
            $mt = @filemtime($old);
            if (is_int($mt) && $mt < $cutoff) @unlink($old);
        }
    }

    // Write to a temp name and rename: rename is atomic within a filesystem, so a reader (or the
    // FTP client you read these with) never sees a half-written report.
    $target = REPORT_DIR . '/' . $name;
    $tmp = $target . '.part';
    if (file_put_contents($tmp, $body, LOCK_EX) === false || !rename($tmp, $target)) {
        @unlink($tmp);
        fail('write failed', 500);
    }
    @chmod($target, 0600);

    echo json_encode([
        'ok' => true,
        'name' => $name,
        'bytes' => strlen($body),
    ]);
} catch (Throwable $e) {
    error_log('glasstube upload.php: ' . $e->getMessage());
    fail('server error', 500);
}
