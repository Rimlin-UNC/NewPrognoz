<?php
/**
 * Weather Professional 2.0 — админ-панель.
 * Вход: email/пароль пользователя с role=admin (создаётся install.php).
 * Возможности: сводка, источники, bias, журнал синхронизации, запуск cron-задач.
 */

declare(strict_types=1);

require __DIR__ . '/../../src/bootstrap.php';

session_name('wa_admin');
session_start();

$pdo = WA\Db::pdo();
$errors = [];
$info = '';

// ---------------------------------------------------------------- logout
if (isset($_GET['action']) && $_GET['action'] === 'logout') {
    session_destroy();
    header('Location: ' . $_SERVER['PHP_SELF']);
    exit;
}

// ------------------------------------------------------------------ login
function wa_admin_user(): ?array {
    if (empty($_SESSION['admin_user_id'])) {
        return null;
    }
    $st = WA\Db::pdo()->prepare('SELECT id, email, role FROM users WHERE id = ? AND role = ? AND deleted_at IS NULL');
    $st->execute([(int) $_SESSION['admin_user_id'], 'admin']);
    $u = $st->fetch();
    return $u ?: null;
}

$user = wa_admin_user();

if (!$user && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' && isset($_POST['login'])) {
    $email = strtolower(trim((string) ($_POST['email'] ?? '')));
    $password = (string) ($_POST['password'] ?? '');
    $st = $pdo->prepare('SELECT * FROM users WHERE email = ? AND deleted_at IS NULL');
    $st->execute([$email]);
    $row = $st->fetch();
    if ($row && $row['role'] === 'admin' && password_verify($password, (string) $row['password_hash'])) {
        session_regenerate_id(true);
        $_SESSION['admin_user_id'] = (int) $row['id'];
        WA\Logger::audit((string) $row['uuid'], 'admin_login', 'user', (string) $row['uuid']);
        header('Location: ' . $_SERVER['PHP_SELF']);
        exit;
    }
    $errors[] = 'Неверные учётные данные или пользователь не администратор.';
    WA\Logger::audit('anonymous', 'admin_login_failed', 'user', $email);
}

// ---------------------------------------------------------------- actions
if ($user && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    if (!isset($_POST['csrf']) || !hash_equals((string) ($_SESSION['csrf'] ?? ''), (string) $_POST['csrf'])) {
        $errors[] = 'CSRF-токен недействителен.';
    } else {
        $action = (string) ($_POST['action'] ?? '');
        try {
            switch ($action) {
                case 'run_aggregate':
                    $stats = WA\Services\AggregationService::run();
                    $info = 'Агрегация выполнена: ' . htmlspecialchars(json_encode($stats, JSON_UNESCAPED_UNICODE));
                    break;
                case 'run_cleanup':
                    ob_start();
                    require __DIR__ . '/../../cron/cleanup.php';
                    ob_end_clean();
                    $info = 'Очистка выполнена.';
                    break;
                case 'toggle_source':
                    $code = (string) ($_POST['code'] ?? '');
                    $pdo->prepare('UPDATE api_sources SET enabled = 1 - enabled WHERE code = ?')->execute([$code]);
                    $info = "Источник `$code` переключён.";
                    break;
                case 'toggle_forecast_source':
                    $code = (string) ($_POST['code'] ?? '');
                    $pdo->prepare('UPDATE forecast_sources SET active = 1 - active WHERE code = ?')->execute([$code]);
                    $info = "Источник прогноза `$code` переключён.";
                    break;
            }
        } catch (Throwable $e) {
            $errors[] = 'Ошибка: ' . htmlspecialchars($e->getMessage());
        }
    }
}
if (empty($_SESSION['csrf'])) {
    $_SESSION['csrf'] = bin2hex(random_bytes(16));
}
$csrf = $_SESSION['csrf'];

// ------------------------------------------------------------------- data
$counts = [];
if ($user) {
    foreach ([
        'users' => 'SELECT COUNT(*) FROM users',
        'devices' => 'SELECT COUNT(*) FROM devices',
        'obs_total' => 'SELECT COUNT(*) FROM observations',
        'obs_24h' => "SELECT COUNT(*) FROM observations WHERE received_at >= '" . gmdate('Y-m-d H:i:s', time() - 86400) . "'",
        'obs_ok' => "SELECT COUNT(*) FROM observations WHERE quality = 'ok'",
        'forecasts_24h' => "SELECT COUNT(*) FROM forecasts WHERE created_at >= '" . gmdate('Y-m-d H:i:s', time() - 86400) . "'",
        'aggregates_24h' => "SELECT COUNT(*) FROM aggregates WHERE computed_at >= '" . gmdate('Y-m-d H:i:s', time() - 86400) . "'",
        'sync_24h' => "SELECT COUNT(*) FROM sync_log WHERE created_at >= '" . gmdate('Y-m-d H:i:s', time() - 86400) . "'",
    ] as $key => $sql) {
        $counts[$key] = (int) $pdo->query($sql)->fetchColumn();
    }
    $apiSources = $pdo->query('SELECT * FROM api_sources ORDER BY priority')->fetchAll();
    $fcSources = $pdo->query('SELECT * FROM forecast_sources ORDER BY base_weight DESC')->fetchAll();
    $biases = $pdo->query("SELECT source_code, payload_json, sample_size, computed_at FROM aggregates WHERE kind='bias' AND cell_lat=0 AND cell_lon=0 ORDER BY computed_at DESC")->fetchAll();
    $syncLog = $pdo->query('SELECT * FROM sync_log ORDER BY id DESC LIMIT 15')->fetchAll();
    $audit = $pdo->query('SELECT * FROM audit_log ORDER BY id DESC LIMIT 15')->fetchAll();
}

function h(?string $s): string {
    return htmlspecialchars((string) $s, ENT_QUOTES, 'UTF-8');
}
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Weather Pro 2.0 — Админ</title>
<style>
  :root { color-scheme: dark; }
  body { font-family: system-ui, -apple-system, sans-serif; margin: 0; background: #0b1a33; color: #eef; }
  .wrap { max-width: 1100px; margin: 0 auto; padding: 20px; }
  h1 { font-size: 22px; } h2 { font-size: 16px; color: #9fb8e8; margin-top: 28px; }
  .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; }
  .card { background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.12); border-radius: 14px; padding: 12px 14px; }
  .card b { font-size: 26px; display: block; }
  .card span { font-size: 12px; color: #9fb8e8; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid rgba(255,255,255,.08); }
  .btn { background: #2d6cdf; border: 0; color: #fff; border-radius: 8px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
  .btn.sec { background: rgba(255,255,255,.12); }
  input[type=email], input[type=password] { background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.2); color: #fff; border-radius: 8px; padding: 8px 10px; width: 240px; box-sizing: border-box; }
  .msg { padding: 10px 14px; border-radius: 10px; margin: 10px 0; }
  .ok { background: rgba(60,200,120,.15); border: 1px solid rgba(60,200,120,.4); }
  .err { background: rgba(230,80,80,.15); border: 1px solid rgba(230,80,80,.4); }
  .login { max-width: 340px; margin: 80px auto; }
  .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  code { background: rgba(255,255,255,.1); padding: 1px 5px; border-radius: 5px; }
</style>
</head>
<body>
<div class="wrap">
<?php if (!$user): ?>
  <div class="login">
    <h1>Weather Pro 2.0 — Админ</h1>
    <?php foreach ($errors as $e): ?><div class="msg err"><?= $e ?></div><?php endforeach; ?>
    <form method="post">
      <input type="hidden" name="login" value="1">
      <p><input type="email" name="email" placeholder="email" required autofocus></p>
      <p><input type="password" name="password" placeholder="пароль" required></p>
      <button class="btn">Войти</button>
    </form>
  </div>
<?php else: ?>
  <div class="row" style="justify-content:space-between">
    <h1 style="margin:0">Weather Pro 2.0 — Панель управления</h1>
    <div class="row">
      <form method="post" style="display:inline">
        <input type="hidden" name="csrf" value="<?= h($csrf) ?>">
        <input type="hidden" name="action" value="run_aggregate">
        <button class="btn">Запустить агрегацию</button>
      </form>
      <form method="post" style="display:inline">
        <input type="hidden" name="csrf" value="<?= h($csrf) ?>">
        <input type="hidden" name="action" value="run_cleanup">
        <button class="btn sec">Очистка</button>
      </form>
      <a class="btn sec" style="text-decoration:none" href="?action=logout">Выйти</a>
    </div>
  </div>

  <?php foreach ($errors as $e): ?><div class="msg err"><?= $e ?></div><?php endforeach; ?>
  <?php if ($info): ?><div class="msg ok"><?= $info ?></div><?php endif; ?>

  <h2>Сводка</h2>
  <div class="cards">
    <div class="card"><b><?= $counts['users'] ?></b><span>Пользователи</span></div>
    <div class="card"><b><?= $counts['devices'] ?></b><span>Устройства</span></div>
    <div class="card"><b><?= $counts['obs_total'] ?></b><span>Наблюдения (всего)</span></div>
    <div class="card"><b><?= $counts['obs_24h'] ?></b><span>Наблюдения (24 ч)</span></div>
    <div class="card"><b><?= $counts['obs_ok'] ?></b><span>Валидные наблюдения</span></div>
    <div class="card"><b><?= $counts['forecasts_24h'] ?></b><span>Точки прогноза (24 ч)</span></div>
    <div class="card"><b><?= $counts['aggregates_24h'] ?></b><span>Агрегаты (24 ч)</span></div>
    <div class="card"><b><?= $counts['sync_24h'] ?></b><span>Синхронизации (24 ч)</span></div>
  </div>

  <h2>Bias-коррекция источников</h2>
  <table>
    <tr><th>Источник</th><th>Смещение T</th><th>Смещение ветра</th><th>MAE T</th><th>Пар</th><th>Обновлено (UTC)</th></tr>
    <?php if (!$biases): ?><tr><td colspan="6">Пока нет данных верификации</td></tr><?php endif; ?>
    <?php foreach ($biases as $b): $p = json_decode((string) $b['payload_json'], true); ?>
      <tr>
        <td><code><?= h($b['source_code']) ?></code></td>
        <td><?= number_format((float) ($p['temp_c'] ?? 0), 2) ?> °C</td>
        <td><?= number_format((float) ($p['wind_ms'] ?? 0), 2) ?> м/с</td>
        <td><?= number_format((float) ($p['mae_temp'] ?? 0), 2) ?></td>
        <td><?= (int) ($p['n'] ?? 0) ?></td>
        <td><?= h($b['computed_at']) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>

  <h2>Источники прогнозов</h2>
  <table>
    <tr><th>Код</th><th>Название</th><th>Вес</th><th>Активен</th><th>Статус</th><th>Действие</th></tr>
    <?php foreach ($fcSources as $s): ?>
      <tr>
        <td><code><?= h($s['code']) ?></code></td>
        <td><?= h($s['name']) ?></td>
        <td><?= h((string) $s['base_weight']) ?></td>
        <td><?= $s['active'] ? 'да' : 'нет' ?></td>
        <td><?= h($s['last_status']) ?></td>
        <td>
          <form method="post" style="display:inline">
            <input type="hidden" name="csrf" value="<?= h($csrf) ?>">
            <input type="hidden" name="action" value="toggle_forecast_source">
            <input type="hidden" name="code" value="<?= h($s['code']) ?>">
            <button class="btn sec"><?= $s['active'] ? 'Выключить' : 'Включить' ?></button>
          </form>
        </td>
      </tr>
    <?php endforeach; ?>
  </table>

  <h2>Внешние API</h2>
  <table>
    <tr><th>Код</th><th>API</th><th>URL</th><th>Включён</th><th>Статус</th><th>Действие</th></tr>
    <?php foreach ($apiSources as $s): ?>
      <tr>
        <td><code><?= h($s['code']) ?></code></td>
        <td><?= h($s['name']) ?></td>
        <td><?= h($s['base_url']) ?></td>
        <td><?= $s['enabled'] ? 'да' : 'нет' ?></td>
        <td><?= h($s['last_status']) ?></td>
        <td>
          <form method="post" style="display:inline">
            <input type="hidden" name="csrf" value="<?= h($csrf) ?>">
            <input type="hidden" name="action" value="toggle_source">
            <input type="hidden" name="code" value="<?= h($s['code']) ?>">
            <button class="btn sec"><?= $s['enabled'] ? 'Выключить' : 'Включить' ?></button>
          </form>
        </td>
      </tr>
    <?php endforeach; ?>
  </table>

  <h2>Последние синхронизации</h2>
  <table>
    <tr><th>Время (UTC)</th><th>Устройство</th><th>Направление</th><th>Операций</th><th>Успешно</th><th>HTTP</th></tr>
    <?php foreach ($syncLog as $r): ?>
      <tr>
        <td><?= h($r['created_at']) ?></td>
        <td><code><?= h(substr((string) $r['device_uuid'], 0, 8)) ?>…</code></td>
        <td><?= h($r['direction']) ?></td>
        <td><?= (int) $r['ops_in'] ?></td>
        <td><?= (int) $r['ops_ok'] ?></td>
        <td><?= (int) $r['http_status'] ?></td>
      </tr>
    <?php endforeach; ?>
  </table>

  <h2>Журнал аудита</h2>
  <table>
    <tr><th>Время (UTC)</th><th>Актор</th><th>Действие</th><th>Сущность</th></tr>
    <?php foreach ($audit as $r): ?>
      <tr>
        <td><?= h($r['created_at']) ?></td>
        <td><code><?= h(substr((string) $r['actor'], 0, 8)) ?>…</code></td>
        <td><?= h($r['action']) ?></td>
        <td><?= h($r['entity']) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
<?php endif; ?>
</div>
</body>
</html>
