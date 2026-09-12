<?php
declare(strict_types=1);

namespace WA;

/**
 * Минималистичный роутер: точные пути + именованные параметры {param}.
 */
final class Router {
    /** @var array<int, array{method:string, pattern:string, handler:callable}> */
    private array $routes = [];

    public function add(string $method, string $pattern, callable $handler): void {
        $this->routes[] = ['method' => $method, 'pattern' => $pattern, 'handler' => $handler];
    }

    public function dispatch(string $method, string $path): void {
        $allowed = [];
        foreach ($this->routes as $route) {
            $regex = '#^' . preg_replace('#\{(\w+)\}#', '(?P<$1>[^/]+)', $route['pattern']) . '$#';
            if (preg_match($regex, $path, $m)) {
                if ($route['method'] !== $method) {
                    $allowed[] = $route['method'];
                    continue;
                }
                $params = array_filter($m, 'is_string', ARRAY_FILTER_USE_KEY);
                ($route['handler'])($params);
                return;
            }
        }
        if ($allowed !== []) {
            header('Allow: ' . implode(', ', array_unique($allowed)));
            wa_error('method_not_allowed', "Метод $method не поддерживается для $path", 405);
        }
        wa_error('not_found', "Неизвестный маршрут: $path", 404);
    }
}
