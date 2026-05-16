# Kế hoạch chuyển đổi JavaChat Desktop → Web Application

> Tài liệu này mô tả chi tiết kế hoạch migrate ứng dụng chat từ kiến trúc **JavaFX Desktop Client + TCP Socket Server** sang **Web Application** (Spring Boot REST + WebSocket + SPA frontend).

---

## 1. Phân tích kiến trúc hiện tại vs đích đến

### 1.1. Kiến trúc hiện tại (Desktop)

```
┌─────────────────┐         TCP Socket (port 8888)         ┌──────────────────┐
│  JavaFX Client  │ ◄──── Packet (JSON, line-delimited) ──► │  Socket Server   │
│  (FXML + CSS)   │                                         │  (ClientHandler) │
└─────────────────┘                                         └────────┬─────────┘
                                                                     │ Hibernate
                                                                ┌────▼─────┐
                                                                │ SQL Server│
                                                                └──────────┘
```

### 1.2. Kiến trúc đích (Web)

```
┌────────────────────┐    HTTPS (REST + WebSocket/STOMP)    ┌────────────────────┐
│   SPA Frontend     │ ◄──── JSON over HTTP / WS ─────────► │  Spring Boot Server │
│ (React/Vue + WS)   │                                      │  REST + WebSocket   │
└────────────────────┘                                      └─────────┬───────────┘
                                                                      │ Hibernate/JPA
                                                                ┌─────▼─────┐
                                                                │ SQL Server │
                                                                └────────────┘

         ┌───────────────────────────┐
         │ Object Storage (optional) │ ← lưu file/ảnh/voice
         └───────────────────────────┘
```

### 1.3. Bảng so sánh thành phần

| Thành phần hiện tại | Tương đương web | Ghi chú |
|---|---|---|
| JavaFX FXML + Controller | React/Vue components + state | Viết lại UI hoàn toàn |
| `ChatClient` (Socket) | `axios` (REST) + `STOMP.js` / native WebSocket | Tách realtime khỏi request/response |
| `ServerManager` + `ClientHandler` | Spring Boot embedded Tomcat + WebSocket handler | Thay TCP bằng HTTP/WS |
| `Packet` (JSON envelope) | REST endpoints + WS message types | Mỗi packet type → 1 endpoint hoặc 1 WS destination |
| `AuthService` (in-memory session) | Spring Security + JWT | Stateless, multi-instance ready |
| DAOs (Hibernate native) | Spring Data JPA Repositories | Giữ entity, đổi access pattern |
| Entities (`User`, `Message`,...) | **Giữ nguyên** | Chỉ thêm DTO layer |
| `VoiceRecorder` (javax.sound) | `MediaRecorder` API (browser) | API hoàn toàn khác |
| `VoicePlayer` (javax.sound) | `<audio>` HTML element | API hoàn toàn khác |
| ControlsFX `Notifications` | Toast lib (react-toastify) + Browser Notifications API | Đổi cơ chế |
| File/Image (Base64 in JSON) | `multipart/form-data` upload + URL reference | Giảm tải payload, lưu binary tách biệt |

---

## 2. Tech stack đề xuất

### 2.1. Backend
- **Spring Boot 3.x** (Java 17) — kế thừa Java code hiện có
- **Spring Web** — REST API
- **Spring WebSocket + STOMP** — realtime messaging
- **Spring Security + JWT** (`jjwt` hoặc `nimbus-jose-jwt`) — authentication
- **Spring Data JPA** — wrap Hibernate hiện có
- **MapStruct** hoặc **ModelMapper** — Entity ↔ DTO
- **Bean Validation (Hibernate Validator)** — validate request DTO
- **Springdoc OpenAPI** — auto-gen Swagger UI
- **SLF4J + Logback** — logging
- **Lombok** (optional) — giảm boilerplate

### 2.2. Frontend
- **React 18 + Vite + TypeScript** (đề xuất chính), hoặc Vue 3 / Angular
- **TanStack Query (React Query)** — server state cho REST
- **Zustand** hoặc Redux Toolkit — client state
- **@stomp/stompjs + sockjs-client** — STOMP client
- **axios** — HTTP client (interceptor cho JWT)
- **Tailwind CSS** hoặc Mantine/Chakra UI — UI framework
- **react-router-dom** — routing
- **react-hook-form + zod** — form + validation
- **dayjs** — datetime
- **emoji-picker-react** — chọn emoji cho reaction

### 2.3. Hạ tầng & DevOps
- **SQL Server** (giữ nguyên DB hiện tại)
- **Object Storage**: MinIO (self-host) hoặc AWS S3 cho file/ảnh/voice
- **Redis** (optional, nhưng khuyến nghị):
  - Lưu danh sách user online (thay `activeClients` in-memory)
  - Pub/Sub broker để scale WebSocket multi-instance
  - Cache JWT blacklist khi logout
- **Docker + Docker Compose** — đóng gói backend + DB + Redis + MinIO
- **Nginx** (reverse proxy, TLS termination)
- **GitHub Actions** — CI/CD

---

## 3. Mapping Packet types → Endpoints

### 3.1. REST endpoints (request/response, không cần realtime)

| Packet hiện tại | HTTP Method + Path | Mô tả |
|---|---|---|
| `REGISTER_REQUEST` | `POST /api/auth/register` | Đăng ký |
| `LOGIN_REQUEST` | `POST /api/auth/login` | Trả JWT access + refresh token |
| `LOGOUT_REQUEST` | `POST /api/auth/logout` | Revoke token (Redis blacklist) |
| - | `POST /api/auth/refresh` | Refresh access token (mới) |
| `LOAD_FRIENDS_REQUEST` | `GET /api/friends` | Lấy danh sách bạn bè + pending requests |
| `ADD_FRIEND_REQUEST` | `POST /api/friends/requests` | Gửi lời mời |
| `ACCEPT_FRIEND_REQUEST` | `POST /api/friends/requests/{id}/accept` | Chấp nhận |
| - | `DELETE /api/friends/requests/{id}` | Từ chối (mới) |
| `BLOCK_USER_REQUEST` | `POST /api/friends/{username}/block` (toggle) | Chặn/bỏ chặn |
| `MUTE_USER_REQUEST` | `POST /api/friends/{username}/mute` (toggle) | Tắt/bật TB |
| `LOAD_HISTORY_REQUEST` | `GET /api/messages?with={username}&limit=50&before={id}` | Lịch sử chat private (kèm pagination) |
| `GROUP_HISTORY_REQUEST` | `GET /api/groups/{id}/messages?limit=50&before={id}` | Lịch sử chat nhóm |
| `CREATE_GROUP_REQUEST` | `POST /api/groups` | Tạo nhóm |
| `GROUP_LIST_REQUEST` | `GET /api/groups` | Danh sách nhóm của user |
| `CONVERSATION_LIST_REQUEST` | `GET /api/conversations` | Danh sách hội thoại gần đây |
| `RECALL_MESSAGE` | `DELETE /api/messages/{id}` (soft delete) | Thu hồi |
| `EDIT_MESSAGE` | `PATCH /api/messages/{id}` | Chỉnh sửa |
| `REACTION_SET_REQUEST` | `PUT /api/messages/{id}/reactions` | Set reaction |
| `REACTION_REMOVE_REQUEST` | `DELETE /api/messages/{id}/reactions` | Xóa reaction |
| - | `POST /api/uploads` (multipart) | Upload file/ảnh/voice → trả URL (mới) |
| - | `GET /api/users/me` | Profile user hiện tại (mới) |

### 3.2. WebSocket destinations (STOMP)

**Client → Server (`@MessageMapping`):**
| Destination | Tương đương cũ | Payload |
|---|---|---|
| `/app/chat.private` | `PRIVATE_MESSAGE` | `{receiver, content, type, fileUrl?}` |
| `/app/chat.group` | `GROUP_MESSAGE` | `{groupId, content, type, fileUrl?}` |
| `/app/typing` | (mới) | `{conversationKey, isTyping}` |

**Server → Client (subscribe):**
| Destination | Tương đương cũ | Mô tả |
|---|---|---|
| `/user/queue/messages` | `CHAT_MESSAGE`, `CHAT_ACK` | Tin nhắn private đến / ack tin đã gửi |
| `/user/queue/notifications` | `FRIEND_REQUEST`, status update | Push notification cá nhân |
| `/topic/group.{groupId}` | `GROUP_MESSAGE` | Broadcast nhóm |
| `/topic/presence` | `STATUS_UPDATE` | Online/offline broadcast |
| `/user/queue/message-events` | `MESSAGE_RECALLED`, `MESSAGE_EDITED`, `REACTION_UPDATED` | Cập nhật tin nhắn |

> **Quy ước**: dùng `convertAndSendToUser(username, "/queue/...", payload)` của Spring để tự động prefix `/user/{username}`.

---

## 4. Roadmap triển khai (8 phase)

### Phase 0 — Chuẩn bị (1-2 ngày)
- [ ] Tạo nhánh `feature/web-migration` riêng
- [ ] Tạo cấu trúc multi-module Maven:
  - `javachat-common` (entities + DTOs dùng chung — extract từ project hiện tại)
  - `javachat-server-web` (Spring Boot mới)
  - `javachat-server-legacy` (giữ nguyên TCP server, để rollback nếu cần)
  - `javachat-web-client` (frontend React, có thể tách repo)
- [ ] Setup CI: Maven build, lint frontend, run unit tests
- [ ] Document API contract trước khi code (OpenAPI YAML draft)

### Phase 1 — Backend foundation (3-4 ngày)
- [ ] Tạo Spring Boot project, copy package `org.example.common.model.*` sang module common
- [ ] Cấu hình `application.yml`: datasource (SQL Server), JPA, JWT secret, CORS
- [ ] **Bỏ** `hibernate.cfg.xml`, dùng Spring Boot autoconfig
- [ ] Convert DAO → Spring Data JPA repositories:
  - `UserRepository extends JpaRepository<User, Long>`
  - `MessageRepository`, `FriendshipRepository`, `GroupChatRepository`, `GroupMemberRepository`, `MessageReactionRepository`
- [ ] Viết DTOs (request/response) và mapper:
  - `UserDto`, `MessageDto`, `FriendshipDto`, `GroupDto`, `ConversationDto`, `ReactionSummaryDto`
  - `LoginRequest`, `RegisterRequest`, `SendMessageRequest`,...
- [ ] Implement `GlobalExceptionHandler` (`@RestControllerAdvice`) trả lỗi format chuẩn `{code, message, fieldErrors?}`
- [ ] Setup Springdoc OpenAPI để auto-generate Swagger UI tại `/swagger-ui.html`

### Phase 2 — Authentication & Security (2-3 ngày)
- [ ] Implement `JwtService` (sign/verify với HS256, claim chứa username + roles)
- [ ] `JwtAuthenticationFilter` extract token từ `Authorization: Bearer ...`
- [ ] `SecurityConfig`:
  - `permitAll`: `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/uploads/**` (nếu public)
  - `authenticated`: tất cả `/api/**` còn lại + `/ws`
  - Stateless session, CSRF disabled (vì dùng JWT), CORS bật
- [ ] `AuthController`: `/register`, `/login`, `/refresh`, `/logout`
- [ ] Refresh token strategy: long-lived refresh token lưu DB (table `refresh_tokens`), rotate khi refresh
- [ ] Logout = đưa JWT vào Redis blacklist (hoặc cập nhật `tokenVersion` trong User)
- [ ] Giữ lại `PasswordUtil` (BCrypt) — không cần đổi

### Phase 3 — Core REST APIs (3-4 ngày)
- [ ] `UserController`: `/api/users/me`, `/api/users/search?q=...`
- [ ] `FriendController`: list, add, accept, reject, block toggle, mute toggle
  - Migrate logic từ `FriendService` sang `FriendApplicationService` (đổi tên để tránh nhầm)
  - **Bỏ** logic blockedBy/mutedBy dạng CSV — thiết kế lại bảng riêng `UserBlock`, `UserMute` (cleanup tech debt)
- [ ] `MessageController`: get history private, edit, recall, reactions
- [ ] `GroupController`: create, list, get history, members
- [ ] `ConversationController`: `/api/conversations` (gộp từ `ChatService.handleLoadConversations`)
- [ ] `UploadController`: `POST /api/uploads` (multipart) → trả `{url, filename, size, mimeType}`
  - Lưu vào MinIO/S3 (hoặc filesystem local cho dev)
  - Validate kích thước (image ≤ 10MB, file ≤ 20MB, voice ≤ 2MB)
  - Trả URL public hoặc presigned URL có TTL
- [ ] Viết integration tests với `@SpringBootTest` + Testcontainers (SQL Server)

### Phase 4 — WebSocket realtime (3-4 ngày)
- [ ] Cấu hình `WebSocketConfig` với STOMP, endpoint `/ws`, broker prefix `/topic`, `/queue`, `/user`
- [ ] `WebSocketAuthInterceptor` (ChannelInterceptor) extract JWT từ STOMP CONNECT header → set principal
- [ ] `ChatWebSocketController`:
  - `@MessageMapping("/chat.private")` → handle private message → save DB → broadcast
  - `@MessageMapping("/chat.group")` → handle group → save DB → broadcast tới members online
- [ ] `PresenceService`:
  - Track online users qua Redis SET (key: `presence:online`, value: username, TTL refresh)
  - Lắng nghe `SessionConnectedEvent` / `SessionDisconnectEvent`
  - Broadcast `STATUS_UPDATE` lên `/topic/presence`
- [ ] `NotificationService`:
  - Khi A gửi friend request cho B online → push `/user/{B}/queue/notifications`
  - Khi user offline thì lưu DB, lúc connect lại sẽ load qua REST
- [ ] Push reaction/edit/recall events lên cả receiver và sender
- [ ] Test với 2 client browser tab khác nhau

### Phase 5 — Frontend foundation (3-4 ngày)
- [ ] Scaffold project: `npm create vite@latest javachat-web -- --template react-ts`
- [ ] Cài deps: react-router, axios, @stomp/stompjs, sockjs-client, @tanstack/react-query, zustand, tailwindcss, react-hook-form, zod, dayjs, react-toastify
- [ ] Cấu trúc folder:
  ```
  src/
    api/           ← axios client + endpoints theo resource
    hooks/         ← useAuth, useChat, useWebSocket, useConversations
    stores/        ← zustand stores (auth, chat, presence)
    components/
      common/      ← Button, Input, Modal, Avatar
      chat/        ← MessageBubble, MessageList, Composer, ConversationItem
      friends/     ← FriendList, FriendRequestItem
      groups/      ← GroupList, CreateGroupDialog
    pages/         ← LoginPage, RegisterPage, ChatPage
    routes/        ← ProtectedRoute, router config
    types/         ← TS interfaces sync với DTO backend
    utils/         ← formatTime, fileUtils
  ```
- [ ] Setup axios interceptor: tự gắn `Authorization` header, auto refresh khi 401
- [ ] Login/Register page (UI đơn giản, validate bằng zod)
- [ ] Auth store (zustand) lưu token vào `localStorage` (hoặc cookie httpOnly nếu có BFF)
- [ ] `ProtectedRoute` redirect về `/login` nếu chưa auth

### Phase 6 — Frontend chat features (5-6 ngày)
- [ ] **Chat layout**: 3 cột (sidebar nav | conversation list | chat pane), responsive
- [ ] **Conversation list**: query `GET /api/conversations`, render với React Query
- [ ] **Message list**: virtualization với `react-window` cho performance, infinite scroll lên trên load thêm history
- [ ] **Message bubble** components:
  - Text, Image (lazy load), File (download button), Voice (`<audio controls>`)
  - Reaction summary, context menu (right-click hoặc 3-dots) cho edit/recall/react
- [ ] **Composer**: textarea + emoji picker + attach file/image + record voice (`MediaRecorder` API)
- [ ] **Voice recording**: dùng `navigator.mediaDevices.getUserMedia()` + `MediaRecorder` xuất WebM/Opus, convert hoặc gửi thẳng lên server
- [ ] **WebSocket hook** (`useWebSocket`):
  - Connect khi authenticated, gửi JWT trong CONNECT headers
  - Subscribe các destination cần thiết
  - Reconnect logic với exponential backoff
  - Cleanup khi unmount/logout
- [ ] **Realtime updates**: khi nhận packet → update React Query cache (`queryClient.setQueryData`) thay vì refetch
- [ ] **Friend management page**: tab pending requests, accepted friends, block/mute toggle
- [ ] **Group management**: dialog create group với multi-select friends
- [ ] **Notifications**:
  - Toast trong app (react-toastify)
  - Browser Notification API khi tab không focus
  - Tôn trọng cờ `isMuted` từ server

### Phase 7 — Hardening & DevOps (3-4 ngày)
- [ ] Rate limiting (Bucket4j hoặc Spring Cloud Gateway) cho auth/upload endpoints
- [ ] Input validation toàn bộ request DTO (`@Valid`, `@NotBlank`,...)
- [ ] Sanitize HTML trong message content (anti XSS) — escape ở frontend khi render
- [ ] HTTPS bắt buộc (Nginx reverse proxy + Let's Encrypt)
- [ ] Configure CORS chính xác (chỉ allow origin frontend đã biết)
- [ ] Audit log: ai làm gì, khi nào (login, message edit/recall)
- [ ] Backup strategy cho SQL Server
- [ ] Dockerfile cho backend, frontend (multi-stage build)
- [ ] `docker-compose.yml`: `backend`, `frontend`, `mssql`, `redis`, `minio`, `nginx`
- [ ] CI: build, test, image push lên registry; CD: deploy lên VPS / cloud
- [ ] Health check endpoints (`/actuator/health`)
- [ ] Monitoring cơ bản: Prometheus metrics + Grafana, hoặc dùng cloud APM

### Phase 8 — Migration & Cutover (1-2 ngày)
- [ ] Schema DB: chạy Flyway/Liquibase migration để cleanup (tách bảng `UserBlock`, `UserMute` từ Friendship CSV cũ — nếu chọn refactor)
- [ ] Hỗ trợ song song: TCP server cũ + Web server mới chạy chung 1 thời gian (nếu có user cần)
- [ ] User data migration: không cần (dùng chung DB)
- [ ] Truyền thông cho user: hướng dẫn URL mới, deprecate desktop client theo lộ trình
- [ ] Sau khi ổn định: gỡ TCP server, archive desktop client repo

---

## 5. Cấu trúc thư mục đề xuất (sau migration)

```
JavaChat/
├── backend/                              ← Spring Boot Maven multi-module
│   ├── pom.xml
│   ├── common/                           ← entities + DTOs
│   │   └── src/main/java/org/example/common/
│   │       ├── model/                    ← giữ nguyên User, Message,...
│   │       └── dto/
│   └── server-web/                       ← Spring Boot app
│       └── src/main/java/org/example/server/
│           ├── ServerWebApplication.java ← @SpringBootApplication
│           ├── config/                   ← SecurityConfig, WebSocketConfig, CorsConfig
│           ├── security/                 ← JwtService, JwtAuthFilter, JwtAuthInterceptor
│           ├── controller/               ← REST controllers
│           ├── websocket/                ← @MessageMapping controllers
│           ├── service/                  ← business logic (refactor từ Service cũ)
│           ├── repository/               ← Spring Data JPA
│           ├── storage/                  ← MinIO/S3 client
│           ├── exception/                ← GlobalExceptionHandler
│           └── presence/                 ← PresenceService (Redis)
│       └── src/main/resources/
│           ├── application.yml
│           └── db/migration/             ← Flyway scripts
│
├── frontend/                             ← React + Vite + TS
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   └── src/
│       ├── api/
│       ├── hooks/
│       ├── stores/
│       ├── components/
│       ├── pages/
│       ├── routes/
│       ├── types/
│       └── utils/
│
├── infra/
│   ├── docker-compose.yml
│   ├── nginx/
│   │   └── nginx.conf
│   └── env/
│       └── .env.example
│
├── legacy/                               ← code desktop cũ (giữ tham khảo)
│   ├── src-fxml/
│   └── src-java-client/
│
├── docs/
│   ├── transform_plan.md                 ← file này
│   ├── api-contract.yaml                 ← OpenAPI spec
│   └── architecture.md
│
└── README.md
```

---

## 6. Vấn đề cần xử lý đặc biệt

### 6.1. File/ảnh/voice — không gửi Base64 qua WebSocket nữa
- **Lý do**: payload Base64 lớn (>1MB) làm nghẽn WS, tăng RAM, không cache được CDN
- **Giải pháp**:
  1. Frontend `POST /api/uploads` (multipart) → nhận `{url, filename, size, mimeType}`
  2. Gửi message qua WS chỉ chứa **URL** (`type: IMAGE, content: <url>`)
  3. Backend lưu URL trong cột `content`, file binary lưu MinIO/S3
- **Migration data cũ**: viết script chuyển Base64 trong cột `content` (tin nhắn cũ) sang object storage và update URL

### 6.2. Refactor `Friendship.blockedBy` / `mutedBy` (CSV string)
- **Vấn đề hiện tại**: dùng `String` chứa `"userA,userB"`, dễ dính bug khi username chứa dấu phẩy hoặc bị contains nhầm (`"foo".contains("foobar")`)
- **Giải pháp**: tách thành 2 bảng nhỏ (recommended)
  ```sql
  CREATE TABLE UserBlocks (id, blocker_id, blocked_id, created_at);
  CREATE TABLE UserMutes  (id, muter_id, muted_id, created_at);
  ```
- **Migration**: viết Flyway script parse CSV cũ → insert vào bảng mới

### 6.3. Online presence khi multi-instance
- **Vấn đề**: `ServerManager.activeClients` (in-memory) không scale ngang
- **Giải pháp**:
  - Dùng Redis SET `presence:online` lưu username online
  - Mỗi instance Spring Boot subscribe Redis Pub/Sub channel `presence-events`
  - Hoặc dùng external broker như RabbitMQ với STOMP relay

### 6.4. WebSocket authentication
- **Cách**: gửi JWT trong CONNECT frame header
  ```js
  stompClient.connectHeaders = { Authorization: `Bearer ${token}` }
  ```
- **Server**: `ChannelInterceptor` validate token trên CONNECT, set `Principal` cho session

### 6.5. Voice format
- **Browser**: `MediaRecorder` mặc định xuất WebM/Opus (không phải WAV)
- **Lựa chọn**:
  - **Khuyến nghị**: chấp nhận WebM/Opus ở backend, lưu nguyên dạng (browser khác chơi tốt)
  - Hoặc convert sang MP3/WAV ở backend bằng ffmpeg (tốn CPU, không khuyến khích)

### 6.6. Tin nhắn group — broadcast hiệu quả
- Hiện tại: loop `serverManager.sendToClient(member.getUsername(), packet)` cho từng member
- Web: dùng STOMP topic `/topic/group.{groupId}` — Spring tự fan-out cho subscribers
- Cần track ai đang subscribe topic nào để biết "ai đang xem chat group này" (read receipts trong tương lai)

### 6.7. CORS & cookie
- Dev: `allowedOrigins: http://localhost:5173`, `allowCredentials: true`
- Prod: chỉ allow domain frontend
- Nếu dùng cookie httpOnly cho refresh token thì phải `SameSite=Strict` + HTTPS

---

## 7. Đánh giá nhân lực & timeline

| Phase | Thời gian (1 dev fullstack) | Thời gian (team 2: BE+FE) |
|---|---|---|
| Phase 0 — Chuẩn bị | 1-2 ngày | 1 ngày |
| Phase 1 — Backend foundation | 3-4 ngày | 2-3 ngày |
| Phase 2 — Auth & Security | 2-3 ngày | 2 ngày |
| Phase 3 — Core REST APIs | 3-4 ngày | 2-3 ngày |
| Phase 4 — WebSocket realtime | 3-4 ngày | 3 ngày |
| Phase 5 — Frontend foundation | 3-4 ngày | 2-3 ngày (FE song song từ Phase 2) |
| Phase 6 — Frontend chat features | 5-6 ngày | 4-5 ngày |
| Phase 7 — Hardening & DevOps | 3-4 ngày | 2-3 ngày |
| Phase 8 — Migration & Cutover | 1-2 ngày | 1 ngày |
| **Tổng** | **~24-33 ngày** (~5-6 tuần) | **~3-4 tuần** |

---

## 8. Rủi ro & cách giảm thiểu

| Rủi ro | Mức độ | Cách giảm thiểu |
|---|---|---|
| WebSocket reconnect mất tin nhắn | Cao | Mỗi tin có server ID, client query missing messages khi reconnect (REST endpoint `GET /api/messages?after={lastId}`) |
| Payload Base64 lớn ở DB cũ | Trung | Migration script lazy chuyển sang object storage |
| Bug `blockedBy.contains()` legacy | Trung | Refactor sang bảng riêng (Phase 1-3) |
| JWT lộ → mất tài khoản | Cao | Short TTL access (15 phút) + refresh rotation, HTTPS bắt buộc |
| User chưa online → mất push notification | Trung | Lưu DB, load lại khi connect |
| Browser không hỗ trợ MediaRecorder cũ | Thấp | Feature detect, hiển thị thông báo nâng cấp browser |
| Performance load lịch sử > 1000 tin | Trung | Pagination + virtualization (react-window) |
| Migration đứt giữa chừng | Cao | Test trên DB staging trước, có rollback Flyway |

---

## 9. Quick win — phần có thể tái sử dụng nguyên vẹn

- **Toàn bộ entity** trong `org.example.common.model.*` (User, Message, Friendship, GroupChat, GroupMember, MessageReaction)
- **`PasswordUtil`** (BCrypt) — copy nguyên vào backend mới
- **Cấu trúc DB SQL Server** — không cần đổi schema (trừ refactor blockedBy/mutedBy nếu chọn)
- **Logic nghiệp vụ** trong `AuthService`, `ChatService`, `FriendService`, `MessageService` — port sang Spring Service được, chỉ đổi cách gửi response (Packet → REST DTO / WS message)

---

## 10. Tiêu chí "Done"

Phase được coi là hoàn tất khi:
- [ ] Tất cả endpoint trong phase đó pass integration test
- [ ] Swagger UI hiển thị đầy đủ endpoint
- [ ] Frontend tương ứng đã connect và demo được flow
- [ ] Code review approved
- [ ] Không còn TODO/FIXME chưa giải quyết trong phạm vi phase
- [ ] Documentation cập nhật (README + architecture.md)

Toàn bộ migration coi là done khi:
- [ ] Tất cả tính năng cũ chạy được trên web (login, chat private/group, file/ảnh/voice, friend, block, mute, recall, edit, reaction, presence)
- [ ] Performance: 100 user concurrent, p95 message latency < 200ms
- [ ] Security audit cơ bản pass (no obvious XSS/SQLi, JWT đúng chuẩn)
- [ ] Deployed lên môi trường staging và chạy ổn 1 tuần
- [ ] Hướng dẫn deploy + vận hành đầy đủ trong `docs/`

---

## 11. Bước tiếp theo ngay sau khi approve plan này

1. Tạo nhánh `feature/web-migration`
2. Setup Spring Boot skeleton + chạy được "Hello World" với SQL Server connection
3. Viết OpenAPI YAML draft cho 5 endpoint đầu tiên (auth + friends)
4. Setup frontend Vite skeleton + login UI mock (chưa connect API)
5. Demo end-to-end login flow → cột mốc xác thực kiến trúc đúng hướng
