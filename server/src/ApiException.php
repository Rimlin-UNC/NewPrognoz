<?php
declare(strict_types=1);

namespace WA;

/** Ошибка API с HTTP-кодом (не завершает процесс — пригодна для catch в cron). */
final class ApiException extends \RuntimeException {
    public function __construct(
        public readonly int $status,
        public readonly string $errorCode,
        string $message
    ) {
        parent::__construct($message);
    }
}
