# Design Doc: 1-on-1 WebRTC Calls — Ktoto

---

## 1. State Machine звонка

### Состояния

```
IDLE
  ├─[call_initiate]──► OUTGOING_RINGING  (таймаут: 45s)
  └─[incoming call]──► INCOMING_RINGING  (таймаут: 45s)

OUTGOING_RINGING
  ├─[call_answer]────► NEGOTIATING       (таймаут ICE: 15s)
  ├─[call_reject]────► ENDED(rejected)
  ├─[call_busy]──────► ENDED(busy)
  ├─[timeout 45s]────► ENDED(no_answer)
  └─[call_cancel]────► ENDED(cancelled)  (сам отменил)

INCOMING_RINGING
  ├─[accept]─────────► NEGOTIATING
  ├─[decline]────────► ENDED(rejected)
  ├─[timeout 45s]────► ENDED(missed)
  └─[call_cancel]────► ENDED(cancelled)  (звонящий отменил)

NEGOTIATING
  ├─[ICE connected]──► IN_CALL
  ├─[ICE failed]─────► FAILED
  └─[timeout 15s]────► FAILED(ice_timeout)

IN_CALL
  ├─[call_end]───────► ENDED
  ├─[ICE disconnected, <8s восстановился]──► IN_CALL (прозрачно)
  ├─[ICE disconnected, >8s]────────────────► RECONNECTING (таймаут: 20s)
  └─[network gone >20s]────────────────────► ENDED(network_lost)

RECONNECTING
  ├─[ICE restart success]──► IN_CALL
  ├─[timeout 20s]──────────► ENDED(reconnect_failed)
  └─[call_end от любого]───► ENDED

ENDED / FAILED  →  IDLE (cleanup + UI)
```

### Таймауты

| Событие | Таймаут |
|---------|---------|
| Нет ответа на звонок | 45 с |
| Ожидание ICE (NEGOTIATING) | 15 с |
| ICE disconnected → reconnect attempt | 8 с grace period |
| RECONNECTING | 20 с |
| Висячий звонок на сервере (нет activity) | 120 с |
| Звонок в неактивном состоянии (краш клиента) | 30 с |

---

## 2. Протокол сигналинга (Socket.IO)

### События и payload

```typescript
// CLIENT → SERVER → CLIENT(s)

call_initiate: {
  callId: string        // uuid v4, генерит КЛИЕНТ
  toUserId: string
  callType: 'audio' | 'video'
}

call_offer: {
  callId: string
  sdp: string           // RTCSessionDescription.sdp
}

call_answer: {
  callId: string
  sdp: string
}

call_ice_candidate: {
  callId: string
  candidate: string     // JSON.stringify(RTCIceCandidate)
  sdpMid: string | null
  sdpMLineIndex: number | null
}

call_reject: {
  callId: string
  reason: 'declined' | 'busy'
}

call_cancel: {
  callId: string
}

call_end: {
  callId: string
  reason: 'hangup' | 'network_lost' | 'reconnect_failed'
}

// CLIENT → SERVER при реконнекте WS
call_state_sync: {
  activeCallId: string | null
}

// SERVER → CLIENT только
call_state_ack: {
  callId: string
  action: 'resume' | 'terminate' | 'noop'
}
```

### Валидация на сервере

```typescript
function validateCallSignal(socket, event, payload) {
  const session = callStore.get(payload.callId)

  if (!session && event !== 'call_initiate') return reject('unknown_call')
  if (session && !session.participants.includes(socket.userId))
    return reject('not_a_participant')

  const allowed = ALLOWED_EVENTS_BY_STATE[session?.state]
  if (!allowed.includes(event)) return reject('invalid_state')

  forward()
}
```

### Гонки

**A и C одновременно звонят B:**
- Сервер принимает первый `call_initiate` по порядку получения
- Второму отвечает `call_busy` мгновенно, до доставки до B

**Симметричная гонка (A звонит B, B звонит A):**
- Побеждает звонок с меньшим lexicographic callId (UUID)
- Проигравший получает `call_cancel` от сервера

**Краш клиента без `call_end`:**
- Socket disconnect → таймер 30с
- Если не вернулся → сервер шлёт `call_end` второму участнику
- Если вернулся → `call_state_sync` / `call_state_ack`

---

## 3. Серверное состояние (Node.js + Redis)

### Структура call session

```typescript
interface CallSession {
  callId: string
  callType: 'audio' | 'video'
  initiatorId: string
  receiverId: string
  state: CallState
  createdAt: number
  answeredAt: number | null
  endedAt: number | null
  endReason: string | null
  participants: {
    [userId: string]: {
      socketId: string
      connected: boolean
      lastSeen: number
    }
  }
  iceRestarts: number
  reconnections: number
}
```

### Несколько устройств одного пользователя

- `call_initiate` → сервер шлёт `incoming_call` на все socket-ы `toUserId`
- Первый ответивший → сервер шлёт `call_cancel` остальным устройствам

### Reconnect WebSocket

```
Клиент → call_state_sync { activeCallId: "xyz" }

Сервер:
  "xyz" в store, state == IN_CALL  → call_state_ack { action: "resume" }
  "xyz" в store, state == ENDED    → call_state_ack { action: "terminate" }
  "xyz" не в store                 → call_state_ack { action: "terminate" }
  activeCallId == null, но звонок есть → call_state_ack { action: "resume", callId }
```

### Уборка висячих звонков

```typescript
setInterval(() => {
  const now = Date.now()
  for (const [callId, session] of callStore) {
    const age = now - session.createdAt
    if (session.state === 'INCOMING_RINGING' && age > 45_000)  terminate(callId, 'no_answer')
    if (session.state === 'NEGOTIATING'      && age > 60_000)  terminate(callId, 'ice_timeout')
    if (session.state === 'IN_CALL'          && age > 4 * 3600_000) terminate(callId, 'max_duration')
    if (session.state === 'RECONNECTING'     && age > 120_000) terminate(callId, 'stale')
  }
}, 10_000)
```

---

## 4. Сетевые corner cases

### 4.1 Оба за CGNAT / жёстким NAT

STUN не пробивает симметричный NAT → нужен TURN relay (обязателен, не опционален).

```kotlin
val iceServers = listOf(
  PeerConnection.IceServer.builder("stun:31.128.39.216:3478").createIceServer(),
  PeerConnection.IceServer.builder("turn:31.128.39.216:3478")
    .setUsername("ktoto").setPassword("turn_secret").createIceServer(),
  PeerConnection.IceServer.builder("turns:31.128.39.216:443?transport=tcp")
    .setUsername("ktoto").setPassword("turn_secret").createIceServer(),
  PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
)
```

### 4.2 Блокировка UDP (операторский DPI)

coturn слушает `443/TCP+TLS` — пропускают все операторы.

```
# coturn.conf
listening-port=3478
tls-listening-port=443
cert=/etc/letsencrypt/live/yakut54.ru/fullchain.pem
pkey=/etc/letsencrypt/live/yakut54.ru/privkey.pem
fingerprint
lt-cred-mech
user=ktoto:turn_secret
realm=yakut54.ru
```

### 4.3 Переключение сети (Wi-Fi ↔ LTE)

```kotlin
override fun onIceConnectionChange(state: IceConnectionState) {
  when (state) {
    DISCONNECTED -> startGracePeriodTimer(8_000)
    FAILED       -> initiateIceRestart()
    CONNECTED, COMPLETED -> cancelGracePeriodTimer(); onCallResumed()
  }
}

fun initiateIceRestart() {
  val constraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
  }
  peerConnection.createOffer(sdpObserver, constraints)
}
```

**Когда пересоздавать PeerConnection вместо ICE restart:**
- Socket.IO тоже отвалился (нет канала сигналинга)
- После >20с RECONNECTING: полный teardown + новый PeerConnection

### 4.4 Падение сервера сигналинга

- Socket.IO: `reconnection: true, reconnectionDelay: 1000, reconnectionAttempts: 10`
- После реконнекта → `call_state_sync`
- P2P трафик через TURN продолжает идти независимо от Socket.IO

### 4.5 Высокий RTT / потери пакетов

```kotlin
peerConnection.getStats { report ->
  report.statsMap.values
    .filter { it.type == "candidate-pair" && it.members["state"] == "succeeded" }
    .forEach { stat ->
      val rtt = stat.members["currentRoundTripTime"] as? Double ?: return@forEach
      val lost = stat.members["packetsLost"] as? Long ?: 0L
      updateQualityIndicator(rtt, lost)
    }
}
// Запускать каждые 3-5 секунд в IN_CALL
```

| Метрика | Порог | Действие |
|---------|-------|---------|
| RTT | > 400ms стабильно | Предупредить юзера |
| Packet loss | > 30% за 10с | Предупредить юзера |
| ICE FAILED | — | ICE restart немедленно |
| ICE DISCONNECTED | > 8с | ICE restart |

---

## 5. Android-специфика

### Архитектура

```
CallActivity / CallScreen
    │
    ▼
CallViewModel (StateFlow<CallState>)
    │
    ├──► CallService (Foreground Service) ← единственная "истина"
    │       ├── WebRtcManager (PeerConnection, tracks)
    │       ├── AudioManager wrapper
    │       └── CallNotification (incoming / active)
    │
    └──► SocketManager (call_* события)
```

### Foreground Service

```kotlin
class CallService : Service() {
  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    startForeground(CALL_NOTIF_ID, buildCallNotification())
    return START_STICKY
  }
}
```

### AudioManager и Bluetooth

```kotlin
audioManager.requestAudioFocus(focusChangeListener,
  AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

// BroadcastReceiver для смены устройств
BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED → refreshAudioRoute()
AudioManager.ACTION_HEADSET_PLUG                 → refreshAudioRoute()
AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED      → handleScoState()
```

### Mute / камера

```kotlin
audioTrack.setEnabled(false)   // mute, renegotiation НЕ нужна
videoTrack.setEnabled(false)   // freeze кадр, renegotiation НЕ нужна
// removeTrack() + renegotiation только при полном отключении видео-режима
```

---

## 6. Безопасность

```typescript
// Rate limiting: макс 10 звонков/минуту на userId
function checkRateLimit(userId: string): boolean {
  const now = Date.now()
  const times = (callRateLimit.get(userId) || []).filter(t => now - t < 60_000)
  if (times.length >= 10) return false
  callRateLimit.set(userId, [...times, now])
  return true
}
```

```typescript
// Логирование
interface CallLog {
  callId: string
  initiatorId: string
  receiverId: string
  callType: string
  endReason: string
  durationMs: number
  iceTransportType: 'direct' | 'srflx' | 'relay' | 'unknown'
  iceRestarts: number
  reconnections: number
  createdAt: string
  endedAt: string
}
// Таблица: call_logs в PostgreSQL
```

---

## Поэтапный план реализации

### Фаза 6.1 — Инфраструктура и сигналинг

- [ ] Поднять coturn на VPS (STUN/TURN/TURNS:443)
- [ ] Backend: call-сессии в Redis (TTL, структура)
- [ ] Backend: все Socket.IO события + валидация
- [ ] Backend: таймауты и уборка висячих звонков
- [ ] Тест сигналинга через два браузера

### Фаза 6.2 — Android аудиозвонки

- [ ] Зависимость `org.webrtc:google-webrtc`
- [ ] `CallService` + `WebRtcManager`
- [ ] ICE state machine (restart / teardown)
- [ ] `CallViewModel` + `CallActivity` (mute, завершить, таймер)
- [ ] Incoming через PushService (ntfy) → уведомление Accept/Decline
- [ ] AudioManager: фокус, BT гарнитура, маршрут
- [ ] Тест: два реальных Android устройства (Wi-Fi + LTE)

### Фаза 6.3 — Android видеозвонки

- [ ] Video track (Camera2 через `Camera2Capturer`)
- [ ] UI: `SurfaceViewRenderer` локал + удалённый
- [ ] Переключение камера/аудио, on/off видео
- [ ] PiP режим при уходе в фон (Android 8+)

### Фаза 6.4 — Edge cases и polish

- [ ] ICE restart при смене сети (`ConnectivityManager.NetworkCallback`)
- [ ] Reconnect при падении сигналинг-сервера (`call_state_sync`)
- [ ] Несколько устройств одного пользователя
- [ ] Симметричная гонка звонков
- [ ] Rate limiting + логирование в `call_logs`
- [ ] UI: индикатор качества, статус "переподключение..."

---

# Design Doc: Групповые чаты

---

## 1. Модель данных

### Схема БД

```sql
-- Уже есть: conversations (type='group'), conversation_participants
-- Добавить:

ALTER TABLE conversation_participants
  ADD COLUMN role TEXT NOT NULL DEFAULT 'member'
    CHECK (role IN ('owner','admin','member')),
  ADD COLUMN joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN muted_until TIMESTAMPTZ;          -- NULL = не замьючено

-- Реакции (отдельная таблица — array не атомарен при concurrent updates)
CREATE TABLE message_reactions (
  message_id  UUID REFERENCES messages(id) ON DELETE CASCADE,
  user_id     UUID REFERENCES users(id)    ON DELETE CASCADE,
  emoji       TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (message_id, user_id, emoji)
);

-- Прочитано в группе (per-user per-message дорого → per-user per-conversation)
-- last_read_at уже есть в conversation_participants ✓

-- Индексы
CREATE INDEX ON messages (conversation_id, created_at DESC);  -- пагинация
CREATE INDEX ON messages (user_id);                           -- unread
CREATE INDEX ON message_reactions (message_id);
```

### Unread count в группе

```sql
-- Точка отсчёта = MAX(joined_at, last_read_at) — не считаем до вступления
SELECT COUNT(*) FROM messages m
JOIN conversation_participants cp
  ON cp.conversation_id = m.conversation_id AND cp.user_id = $userId
WHERE m.conversation_id = $convId
  AND m.deleted_at IS NULL
  AND m.user_id != $userId
  AND m.created_at > GREATEST(cp.last_read_at, cp.joined_at)
```

---

## 2. Роли и права

| Действие | member | admin | owner |
|----------|--------|-------|-------|
| Читать/писать | ✓ | ✓ | ✓ |
| Удалить своё | ✓ | ✓ | ✓ |
| Удалить чужое | ✗ | ✓ | ✓ |
| Добавить участника | ✗ | ✓ | ✓ |
| Удалить участника | ✗ | ✓ | ✓ |
| Назначить admin | ✗ | ✗ | ✓ |
| Изменить название/аватар | ✗ | ✓ | ✓ |
| Удалить группу | ✗ | ✗ | ✓ |
| Покинуть группу | ✓ | ✓ | только если есть другой owner |

---

## 3. Backend — новые эндпоинты

```
POST   /api/conversations/:id/members          — добавить участников
DELETE /api/conversations/:id/members/:userId  — удалить участника / покинуть
PATCH  /api/conversations/:id/members/:userId  — изменить роль
PATCH  /api/conversations/:id                  — изменить название/аватар группы
POST   /api/messages/:id/reactions             — добавить реакцию
DELETE /api/messages/:id/reactions             — убрать реакцию
```

### Socket.IO события для групп

```typescript
// Добавлен участник
group_member_added: { conversationId, userId, username, avatarUrl, addedBy }

// Участник удалён / покинул
group_member_removed: { conversationId, userId, removedBy }

// Изменение роли
group_member_role_changed: { conversationId, userId, newRole }

// Группа обновлена (название, аватар)
group_updated: { conversationId, name, avatarUrl }

// Реакция
message_reaction_added:   { messageId, conversationId, userId, emoji }
message_reaction_removed: { messageId, conversationId, userId, emoji }
```

---

## 4. Corner Cases

### 4.1 Owner покидает группу

**Техническая суть:** если owner уходит без передачи прав — группа становится ownerless.

**Решение:**
```typescript
// При запросе owner на выход:
const admins = await getAdmins(convId)  // кроме owner
if (admins.length > 0) {
  // auto-promote первого admin в owner
  await promoteToOwner(admins[0].userId, convId)
} else {
  const members = await getMembers(convId)
  if (members.length > 0) await promoteToOwner(members[0].userId, convId)
  else await deleteConversation(convId)  // группа пустая — удаляем
}
```

### 4.2 Kick участника из активного Socket.IO room

**Проблема:** удалённый участник ещё подключён, продолжает получать сообщения.

**Решение:**
```typescript
// DELETE /members handler:
await removeFromDb(convId, targetUserId)

// Найти все сокеты targetUserId и выгнать из комнаты
const sockets = await app.io.in(`user:${targetUserId}`).fetchSockets()
for (const s of sockets) {
  s.leave(`conv:${convId}`)
}
// Уведомить самого удалённого
app.io.to(`user:${targetUserId}`).emit('group_member_removed', {
  conversationId: convId, userId: targetUserId, removedBy: currentUserId
})
// Уведомить остальных участников
app.io.to(`conv:${convId}`).emit('group_member_removed', { ... })
```

**Android:** при получении `group_member_removed` где `userId == currentUserId` → навигация назад из чата.

### 4.3 Pagination race (scroll up + новые WS сообщения)

**Проблема:** загружаем страницу старых сообщений пока новые прилетают → дубликаты или пропуски по cursor.

**Решение — cursor-based пагинация по UUID:**
```typescript
// GET /messages?before=<messageId>&limit=50
// before — ID сообщения (не timestamp!) как курсор
// Сортировка: created_at DESC
// Android: дедупликация по message.id перед вставкой в список
```

```kotlin
// Android: merge при подгрузке старых
val existingIds = _messages.value.map { it.id }.toSet()
val newOld = olderMessages.filter { it.id !in existingIds }
_messages.value = (newOld + _messages.value).sortedBy { it.createdAt }
```

### 4.4 Presigned URL expiry в длинных сессиях

**Проблема:** MinIO presigned URL (TTL 1h) истекает пока юзер листает историю — картинки/файлы ломаются.

**Решение:**
```typescript
// Эндпоинт для обновления URL
GET /api/messages/:id/media-url
// Возвращает свежий presigned URL, можно вызывать при 403/404 на медиа
```

```kotlin
// Android: Coil interceptor
class RefreshUrlInterceptor : Interceptor {
  override fun intercept(chain: Chain): Response {
    val response = chain.proceed(chain.request())
    if (response.code == 403 || response.code == 404) {
      // Запросить свежий URL, повторить загрузку
      val freshUrl = api.getMediaUrl(messageId)
      return chain.proceed(chain.request().newBuilder().url(freshUrl).build())
    }
    return response
  }
}
```

### 4.5 Last message в списке диалогов при soft-delete

**Проблема:** последнее сообщение в группе удалено (soft-delete) → список диалогов показывает `null` или удалённый текст.

**Решение:**
```sql
-- В запросе conversations list — уже есть WHERE deleted_at IS NULL ✓
-- Но нужно обновить conversations.updated_at при удалении:
UPDATE conversations SET updated_at = NOW() WHERE id = $convId
-- И на Android: при получении message_deleted — если это последнее сообщение,
-- обновить preview в ConversationsScreen через WS
```

### 4.6 Read status в группах vs 1:1

**Проблема:** в 1:1 — `readByOthers: boolean`. В группе на 50+ человек per-user tracking дорогой.

**Решение — двойной подход:**
- Сообщения показывают только количество: `seen_count: Int` (обновляется при `last_read_at` update)
- Детальный список "кто прочитал" — по запросу (`GET /messages/:id/read-by`), не real-time

```sql
-- seen_count вычисляется как
SELECT COUNT(*) FROM conversation_participants
WHERE conversation_id = $convId
  AND user_id != $senderId
  AND last_read_at >= $messageCreatedAt
```

### 4.7 Concurrent реакции

**Проблема:** несколько пользователей жмут одну реакцию одновременно → race condition на UPDATE.

**Решение:** отдельная таблица `message_reactions` с PK `(message_id, user_id, emoji)` — INSERT ... ON CONFLICT DO NOTHING, удаление — DELETE. Атомарно по природе PK.

### 4.8 Rate limiting в группах

**Проблема:** в группе на 100 человек одно сообщение = 100 Socket.IO emit-ов. Спамер бьёт весь сервер.

**Решение:**
```typescript
// Redis rate limit per (userId, conversationId)
const key = `rl:msg:${userId}:${convId}`
const count = await redis.incr(key)
if (count === 1) await redis.expire(key, 60)
if (count > 20) return reply.status(429).send({ error: 'rate_limit' })
// 20 сообщений/минуту на пользователя в одном чате
```

### 4.9 Множество устройств — unread sync

**Проблема:** юзер читает в приложении на планшете → телефон не знает, сбрасывает ли unread.

**Решение:** `messages_read` WS событие уже шлётся на `user:${userId}` (все устройства). Android обновляет `last_read_at` локально при получении события где `readerId == currentUserId`.

---

## 5. Android — изменения

### ConversationsScreen
- Группы показывают `Вы: текст` / `username: текст` в preview последнего сообщения
- Аватар группы: первая буква названия (уже есть) или кастомное изображение через Coil

### ChatScreen — новые элементы
- **Header:** тап на название → экран участников группы
- **Системные сообщения:** `[username] добавлен в группу`, `[username] покинул группу` — отдельный bubble-тип `system`
- **Реакции:** строка под баблом с emoji + count, long-press добавить
- **Seen count:** под своими сообщениями в группе `👁 3` вместо delivery icon

### ChatViewModel — новые подписки
```kotlin
// При получении group_member_removed где userId == currentUserId
socketManager.groupMemberRemoved
  .filter { it.conversationId == conversationId && it.userId == currentUserId }
  .collect { onBack() }  // выкидываем из чата

// Системные сообщения
socketManager.groupMemberAdded
  .filter { it.conversationId == conversationId }
  .collect { event ->
    val sysMsg = Message(type = "system", content = "${event.username} добавлен в группу", ...)
    _messages.value = _messages.value + sysMsg
  }
```

---

## Поэтапный план — Фаза 7: Групповые чаты

### Фаза 7.1 — Backend + DB

- [ ] Миграция: роли в `conversation_participants`, таблица `message_reactions`
- [ ] Эндпоинты: add/remove/role members, update group info
- [ ] Socket.IO события: `group_member_added/removed`, `group_updated`
- [ ] Owner auto-promotion при выходе
- [ ] Kick из Socket.IO room при удалении
- [ ] Rate limiting per (userId, convId)
- [ ] Cursor-based пагинация по messageId

### Фаза 7.2 — Android базовый UI

- [ ] Экран участников группы
- [ ] Системные сообщения (bubble type = "system")
- [ ] Обработка `group_member_removed` (выброс из чата)
- [ ] Seen count вместо delivery icon в группах
- [ ] Pagination: scroll up → loadOlder() с дедупликацией

### Фаза 7.3 — Реакции

- [ ] Backend: POST/DELETE реакций + WS события
- [ ] Android: emoji picker, bubble с реакциями, real-time update

### Фаза 7.4 — Edge cases

- [ ] Presigned URL refresh (Coil interceptor + `/media-url` эндпоинт)
- [ ] Multi-device unread sync через `messages_read`
- [ ] Last message update в ConversationsScreen при message_deleted
- [ ] Mute/unmute уведомлений для конкретной группы
