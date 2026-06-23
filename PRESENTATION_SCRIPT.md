# Kịch bản thuyết trình JavaChatApp

## 1. Mở đầu

Kính chào thầy/cô và các bạn. Hôm nay em xin trình bày về hệ thống **JavaChatApp**, một ứng dụng chat được xây dựng bằng JavaFX ở phía client và Java socket ở phía server.

Hệ thống hỗ trợ các chức năng chính như đăng ký, đăng nhập, quản lý bạn bè, chat riêng, chat nhóm, gửi nhiều loại tin nhắn, thu hồi và chỉnh sửa tin nhắn, thả cảm xúc, gọi thoại, gọi video, cập nhật hồ sơ cá nhân và quản trị tài khoản dành cho admin.

Về kiến trúc tổng thể, hệ thống được chia thành 5 tầng chính:

1. **JavaFX UI**: hiển thị giao diện và nhận thao tác người dùng.
2. **Client network**: gửi và nhận packet qua TCP socket.
3. **Server network**: nhận kết nối, tạo handler cho từng client.
4. **Service layer**: xử lý logic nghiệp vụ như đăng nhập, chat, bạn bè, nhóm, cuộc gọi.
5. **DAO và database**: lưu trữ user, message, group, friendship, reaction và call log bằng Hibernate.

Luồng hoạt động tổng quát là: người dùng thao tác trên UI, controller tạo `Packet`, `ChatClient` gửi packet qua TCP đến server, `ClientHandler` nhận và chuyển tiếp sang service tương ứng, service xử lý dữ liệu, lưu database nếu cần, sau đó trả packet về client để cập nhật giao diện.

## 2. Công nghệ sử dụng

Ứng dụng sử dụng **JavaFX** để xây dựng giao diện, **FXML** để mô tả layout, và **CSS** để định nghĩa giao diện trực quan.

Phần giao tiếp mạng sử dụng:

- **TCP Socket** cho các dữ liệu cần ổn định như đăng nhập, chat, gửi file, lịch sử chat, bạn bè, nhóm, admin và signaling cuộc gọi.
- **UDP Socket** cho truyền dữ liệu realtime của cuộc gọi thoại và video.

Phần lưu trữ dữ liệu sử dụng **Hibernate ORM** để ánh xạ các class entity sang bảng trong database.

Ngoài ra, hệ thống còn có mã hóa AES cho nội dung tin nhắn trước khi lưu xuống database.

## 3. Cấu trúc giao diện

Sau khi đăng nhập thành công, người dùng được chuyển sang màn hình chính `Chat.fxml`. Đây là layout cha của toàn bộ hệ thống.

Bên trái là thanh điều hướng gồm các nút:

- Tìm kiếm bạn bè.
- Màn hình chat.
- Danh bạ.
- Nhóm chat.
- Hồ sơ cá nhân.
- Admin, chỉ hiện với tài khoản có quyền admin.
- Đăng xuất.

Ở giữa là `StackPane`, chứa nhiều màn hình con được include sẵn như `SearchView.fxml`, `ChatWorkspaceView.fxml`, `ContactsView.fxml`, `GroupsView.fxml`, `ProfileView.fxml` và `AdminView.fxml`.

Khi người dùng bấm một nút điều hướng, `ChatController` gọi hàm `switchView()` để ẩn các view khác và chỉ hiển thị view đang được chọn.

## 4. Luồng đăng ký tài khoản

Đầu tiên là chức năng đăng ký.

Người dùng nhập username, họ tên và mật khẩu ở màn hình `Register.fxml`. Khi bấm nút đăng ký, `RegisterController` tạo packet có type là `REGISTER_REQUEST`, payload chứa username, password và fullName.

Packet này được gửi qua `ChatClient.sendPacket()`. Ở server, `ClientHandler` nhận packet và chuyển sang `AuthService.handleRegister()`.

Trong service, hệ thống kiểm tra username đã tồn tại chưa. Nếu chưa tồn tại, mật khẩu sẽ được hash bằng `PasswordUtil`, sau đó tạo đối tượng `User` và lưu vào database thông qua `UserDAO.saveUser()`.

Nếu thành công, server gửi lại `REGISTER_SUCCESS`. Nếu username đã tồn tại hoặc có lỗi database, server gửi `REGISTER_ERROR`.

Tóm tắt luồng:

```text
Register UI
-> RegisterController
-> Packet REGISTER_REQUEST
-> ChatClient TCP
-> ClientHandler
-> AuthService
-> UserDAO
-> Database
-> REGISTER_SUCCESS / REGISTER_ERROR
```

## 5. Luồng đăng nhập

Ở màn hình `Login.fxml`, người dùng nhập username và password. `LoginController` gửi packet `LOGIN_REQUEST` lên server.

Server xử lý trong `AuthService.handleLogin()`. Logic gồm các bước:

1. Tìm user trong database bằng username.
2. So sánh password người dùng nhập với password hash trong database.
3. Kiểm tra tài khoản có bị khóa bởi admin không.
4. Nếu hợp lệ, gán username hiện tại vào `ClientHandler`.
5. Cập nhật trạng thái user thành `ONLINE`.
6. Gửi `LOGIN_SUCCESS` về client.
7. Broadcast trạng thái online cho danh sách bạn bè của user.

Việc broadcast chỉ gửi cho bạn bè đã được chấp nhận, không gửi toàn hệ thống. Điều này giúp tránh thông báo không cần thiết.

Nếu đăng nhập thất bại, server gửi `LOGIN_ERROR`.

## 6. Luồng khởi tạo sau đăng nhập

Sau khi login thành công, client mở màn hình chính `Chat.fxml`.

Trong `ChatController.initialize()`, hệ thống đăng ký listener nhận packet từ server:

```text
ChatClient.setOnPacketReceived(handleServerResponse)
```

Sau đó client gửi các request ban đầu:

- `LOAD_FRIENDS_REQUEST`: tải danh sách bạn bè.
- `GROUP_LIST_REQUEST`: tải danh sách nhóm.
- `GET_USER_INFO`: tải thông tin cá nhân.
- `CONVERSATION_LIST_REQUEST`: tải danh sách hội thoại gần đây.

Từ thời điểm này, mọi packet server gửi về đều đi qua `ChatController.handleServerResponse()`, sau đó được chuyển tiếp đến controller con phù hợp.

Ví dụ:

- `CHAT_MESSAGE` chuyển cho `ChatWorkspaceController`.
- `GROUP_LIST` chuyển cho `GroupsViewController`.
- `USER_INFO` chuyển cho `ProfileViewController`.
- `ADMIN_USER_LIST` chuyển cho `AdminViewController`.

## 7. Logic quản lý bạn bè

Chức năng bạn bè được xử lý chính trong `FriendService`.

Khi client cần tải danh sách bạn bè, nó gửi `LOAD_FRIENDS_REQUEST`. Server tìm các quan hệ `Friendship` có trạng thái `ACCEPTED`, đồng thời lấy các lời mời đang chờ có trạng thái `PENDING`.

Server cũng duyệt danh sách `activeClients` trong `ServerManager` để biết user nào đang online. Kết quả trả về client bằng packet `LOAD_FRIENDS_SUCCESS`.

Với chức năng gửi lời mời kết bạn, client gửi `ADD_FRIEND_REQUEST`. Server kiểm tra target user có tồn tại không, kiểm tra hai người đã có quan hệ chưa, sau đó tạo `Friendship` với trạng thái `PENDING`.

Nếu người nhận đang online, server gửi packet `FRIEND_REQUEST` để hiển thị lời mời ngay lập tức.

Khi người nhận chấp nhận, client gửi `ACCEPT_FRIEND_REQUEST`. Server đổi trạng thái friendship từ `PENDING` sang `ACCEPTED`, sau đó reload danh sách bạn bè cho cả hai bên nếu đang online.

## 8. Logic tìm kiếm user

Màn hình tìm kiếm dùng `SearchView.fxml` và `SearchViewController`.

Client gửi `SEARCH_ALL_USERS_REQUEST`. Server trả về danh sách user, loại trừ chính người đang đăng nhập.

Mỗi kết quả gồm:

- username.
- trạng thái online hoặc offline.
- quan hệ với user hiện tại: chưa kết bạn, đã là bạn, đã gửi lời mời, hoặc đang nhận lời mời.

Ở client, `SearchViewController` hiển thị từng dòng bằng custom cell gồm tên user, trạng thái và nút hành động. Tùy quan hệ, nút có thể là thêm bạn, chấp nhận hoặc bị vô hiệu hóa.

## 9. Logic chặn và tắt thông báo

Chức năng chặn và tắt thông báo được lưu trong bảng `Friendship`.

Trường `blockedBy` lưu danh sách username đã chặn người còn lại. Khi gửi tin nhắn riêng, `ChatService.handlePrivateMessage()` gọi `friendshipDAO.isBlocked(sender, receiver)`. Nếu người nhận đã chặn người gửi, server không lưu tin nhắn và trả về `CHAT_ERROR`.

Trường `mutedBy` lưu danh sách username đã tắt thông báo. Nếu người nhận đã mute người gửi, server vẫn gửi tin nhắn nhưng thêm cờ `isMuted = true`. Client nhận được tin nhưng không hiện notification.

## 10. Logic chat riêng

Khi người dùng mở một hội thoại riêng, `ChatWorkspaceController.startPrivateChat()` được gọi.

Controller cập nhật tiêu đề, avatar, hiện các nút gọi, chặn, tắt thông báo, sau đó gọi `loadHistory(username)`. Client gửi packet `LOAD_HISTORY_REQUEST`.

Server xử lý trong `ChatService.handleLoadHistory()`. Hệ thống lấy lịch sử tin nhắn giữa hai user bằng `MessageDAO.getPrivateHistory()`, lấy thêm reaction theo batch, đồng thời lấy lịch sử cuộc gọi giữa hai user. Sau đó server gộp tin nhắn và call log theo thời gian rồi gửi về client bằng `CHAT_HISTORY`.

Khi người dùng gửi tin nhắn text, controller lấy nội dung từ `txtMessage`, tạo JSON gồm `content`, `type = TEXT` và `receiver`, rồi gửi packet `PRIVATE_MESSAGE`.

Server xử lý:

1. Parse receiver, content, type và filename nếu có.
2. Tìm sender từ `currentUsername`.
3. Tìm receiver trong database.
4. Kiểm tra block.
5. Tạo entity `Message`.
6. Lưu vào database.
7. Gửi `CHAT_ACK` về người gửi.
8. Gửi `CHAT_MESSAGE` cho người nhận nếu đang online.

Client không thêm tin vào UI ngay khi bấm gửi. Nó chờ `CHAT_ACK` vì ACK chứa `messageId` thật trong database. Message ID này cần cho các thao tác sau như sửa, thu hồi và reaction.

## 11. Logic chat nhóm

Nhóm chat được quản lý bởi `GroupDAO`, `GroupMemberDAO` và `ChatService`.

Khi tạo nhóm, client gửi `CREATE_GROUP_REQUEST` gồm tên nhóm và danh sách thành viên. Server kiểm tra tên nhóm không rỗng, thêm người tạo vào danh sách thành viên và yêu cầu tổng số thành viên tối thiểu là 3.

Sau đó server tạo `GroupChat`, lưu các `GroupMember`, gửi `GROUP_SUCCESS` cho người tạo và gửi `GROUP_LIST_UPDATED` cho các thành viên.

Khi gửi tin nhắn nhóm, client gửi `GROUP_MESSAGE` gồm `groupId`, `content`, `type` và `filename` nếu có.

Server kiểm tra người gửi có thuộc nhóm không. Nếu hợp lệ, server lưu `Message` với `groupChat`, gửi `GROUP_MESSAGE_ACK` về người gửi và gửi `GROUP_MESSAGE` cho tất cả thành viên còn lại trong nhóm.

## 12. Logic các loại tin nhắn

Hệ thống hỗ trợ nhiều loại tin nhắn:

- `TEXT`: tin nhắn văn bản.
- `IMAGE`: ảnh được encode Base64.
- `FILE`: file được encode Base64 và có filename.
- `VOICE`: tin nhắn thoại dạng WAV encode Base64.
- `VIDEO`: video encode Base64 và có filename.
- `CALL_LOG`: lịch sử cuộc gọi hiển thị trong chat.

Tất cả loại tin nhắn này đều đi qua cùng luồng TCP `PRIVATE_MESSAGE` hoặc `GROUP_MESSAGE`. Điểm khác nhau nằm ở trường `type`, `content` và `filename`.

Ở UI, `ChatWorkspaceController.createMessageNodeByType()` kiểm tra type và tạo node hiển thị phù hợp:

- Text tạo bubble chữ.
- Image decode Base64 rồi hiển thị ảnh.
- Voice tạo nút phát âm thanh.
- File tạo hộp file và nút lưu.
- Video tạo hộp video và nút phát.
- Call log tạo dòng lịch sử cuộc gọi.

## 13. Logic chỉnh sửa tin nhắn

Khi người dùng click chuột phải vào tin nhắn của mình và chọn sửa, client gửi `EDIT_MESSAGE` gồm `messageId` và `newContent`.

Server xử lý trong `MessageService.handleEditMessage()`.

Điều kiện sửa:

- Message phải tồn tại.
- Message chưa bị thu hồi.
- Người sửa phải chính là sender của message.

Nếu hợp lệ, server cập nhật content mới, set `isEdited = true`, cập nhật thời gian `updatedAt`, lưu vào database và gửi `MESSAGE_EDITED` cho người nhận hoặc các thành viên nhóm.

Client nhận `MESSAGE_EDITED`, tìm message trong `messageIdToIndexMap`, sau đó render lại node tin nhắn với nội dung mới.

## 14. Logic thu hồi tin nhắn

Khi người dùng chọn thu hồi, client gửi `RECALL_MESSAGE` với `messageId`.

Server kiểm tra message tồn tại và người yêu cầu là sender. Nếu hợp lệ, server set `isRecalled = true`, update database và gửi `MESSAGE_RECALLED` cho các client liên quan.

Ở client, message được thay bằng nội dung như “Tin nhắn đã bị thu hồi”.

## 15. Logic reaction

Reaction được xử lý bởi `MessageService` và `MessageReactionDAO`.

Khi user thả cảm xúc, client gửi `REACTION_SET_REQUEST` gồm `messageId` và emoji.

Server kiểm tra user có quyền tương tác với message không:

- Với chat riêng, user phải là sender hoặc receiver.
- Với chat nhóm, user phải là thành viên nhóm.

Nếu hợp lệ, server tạo hoặc cập nhật `MessageReaction`. Sau đó server tính lại thống kê reaction theo emoji, ví dụ mỗi emoji có bao nhiêu lượt, rồi gửi `REACTION_UPDATED` cho các client liên quan.

Client cập nhật lại reaction chip dưới message.

## 16. Logic mã hóa tin nhắn

Nội dung tin nhắn và tên file được mã hóa trước khi lưu database.

Trong entity `Message`, các trường `content` và `fileName` dùng `MessageEncryptionConverter`. Converter này gọi `AESUtil.encrypt()` khi lưu và `AESUtil.decrypt()` khi đọc.

Thuật toán đang dùng là `AES/CBC/PKCS5Padding`. Mỗi lần mã hóa, hệ thống tạo IV ngẫu nhiên 16 byte, mã hóa nội dung, ghép IV với ciphertext, encode Base64 và thêm prefix `enc:`.

Khi đọc dữ liệu, nếu chuỗi bắt đầu bằng `enc:`, hệ thống sẽ giải mã. Nếu không có prefix, dữ liệu được trả nguyên để tương thích với dữ liệu cũ chưa mã hóa.

Điểm cần nhấn mạnh là đây là mã hóa khi lưu database, không phải mã hóa end-to-end giữa hai client.

## 17. Logic profile

Màn hình profile cho phép người dùng xem và cập nhật họ tên, avatar.

Khi mở app, client gửi `GET_USER_INFO`. Server trả về username, fullName, avatar và role.

Khi người dùng lưu thay đổi, client gửi `UPDATE_PROFILE_REQUEST`. Server cập nhật `User` trong database rồi trả `UPDATE_PROFILE_RESPONSE`.

Avatar có thể là emoji mẫu hoặc ảnh upload dạng Base64.

## 18. Logic admin

Tài khoản admin có thêm màn hình `AdminView.fxml`.

Mọi thao tác admin đều được kiểm tra quyền trong `AdminService`: user hiện tại phải có role `ADMIN`.

Admin có thể:

- Xem toàn bộ tài khoản.
- Tạo tài khoản mới.
- Cập nhật họ tên và vai trò.
- Đặt lại mật khẩu.
- Khóa hoặc mở khóa tài khoản.

Khi khóa một tài khoản đang online, server gửi `FORCE_LOGOUT` cho user đó. Client nhận packet này sẽ buộc đăng xuất.

Hệ thống cũng bảo vệ tài khoản admin chính bằng cách không cho khóa hoặc hạ quyền tài khoản `admin`.

## 19. Logic TCP trong hệ thống

TCP được dùng cho dữ liệu cần đảm bảo chính xác và đúng thứ tự.

Client mở socket tới server bằng `new Socket(host, port)`. Server mở `ServerSocket(8888)` và chờ kết nối bằng `accept()`.

Hai bên dùng `BufferedReader` để đọc dữ liệu và `PrintWriter` để ghi dữ liệu:

```text
PrintWriter.println(packetJson)
BufferedReader.readLine()
```

Mỗi packet được serialize thành một dòng JSON. Do đó giao thức TCP của hệ thống là giao thức dạng line-based.

Client có một `listenerThread` chạy nền để đọc packet từ server. Nếu không dùng thread riêng, lệnh `readLine()` sẽ block và làm đứng giao diện.

Server dùng `ExecutorService` để tạo thread xử lý cho nhiều client cùng lúc. Mỗi client có một `ClientHandler` riêng.

## 20. Logic UDP trong cuộc gọi

UDP được dùng cho audio và video realtime vì độ trễ thấp quan trọng hơn độ tin cậy tuyệt đối.

Server mở hai UDP relay:

- Port `8889` cho audio.
- Port `8890` cho video.

Client không gửi audio/video trực tiếp cho nhau, mà gửi về server relay. Mỗi UDP packet có 8 byte đầu là `callId`. Server dùng `callId` để biết packet thuộc cuộc gọi nào, sau đó forward payload cho bên còn lại.

Server không giải mã audio hay video, chỉ chuyển tiếp byte.

## 21. Logic cuộc gọi thoại và video

Cuộc gọi có hai phần:

1. **Signaling qua TCP**.
2. **Media qua UDP**.

Khi user A gọi user B, client gửi `CALL_INVITE` qua TCP. Server kiểm tra:

- Không gọi chính mình.
- Người nhận online.
- Hai bên không đang bận.
- Người nhận không chặn người gọi.

Nếu hợp lệ, server tạo `CallSession` trạng thái `RINGING` và gửi invite tới người nhận.

Nếu người nhận chấp nhận, client gửi `CALL_ACCEPT`. Server chuyển tiếp cho caller. Caller tạo UDP socket, gửi `CALL_ACCEPT_ACK`. Khi ACK hoàn tất, cả hai client chuyển sang trạng thái `ACTIVE` và bắt đầu mở thread audio/video.

Audio dùng `AudioCaptureThread` để đọc micro và gửi UDP, `AudioPlaybackThread` để nhận UDP và phát loa.

Video dùng `VideoCaptureThread` để đọc webcam, nén JPEG và gửi UDP, `VideoPlaybackThread` để nhận JPEG và hiển thị lên UI.

Khi cuộc gọi kết thúc, client gửi `CALL_END`, server cleanup call session, unregister UDP relay và lưu `CallLog` vào database.

## 22. Logic xử lý thread

Hệ thống dùng nhiều thread để tránh block UI và xử lý realtime:

- JavaFX Application Thread: cập nhật giao diện.
- `ChatClient.listenerThread`: nhận packet TCP từ server.
- `ClientHandler` threads: xử lý từng client trên server.
- UDP relay thread: nhận và forward UDP packet.
- Audio capture/playback threads: ghi âm và phát âm thanh realtime.
- Video capture/playback threads: gửi và nhận video.
- Heartbeat thread: theo dõi cuộc gọi có bị mất kết nối không.
- Voice recorder thread: ghi âm voice message.

Khi packet từ server về client, việc cập nhật UI luôn được bọc trong `Platform.runLater()` để đảm bảo chạy trên JavaFX Application Thread.

## 23. Logic synchronized

Do có nhiều thread chạy song song, project dùng `synchronized` và `volatile` để tránh lỗi cạnh tranh dữ liệu.

Trong `ChatClient`, danh sách listeners được bảo vệ bằng `synchronized`. Khi nhận packet, client copy danh sách listener thành snapshot rồi mới gọi callback. Cách này tránh lỗi vừa duyệt list vừa sửa list.

Trong `CallManager`, nhiều hàm như `startCall()`, `acceptCall()`, `endCall()` được synchronized để đảm bảo trạng thái cuộc gọi `currentSession` không bị thay đổi đồng thời bởi nhiều thread.

Trong `VoiceRecorder`, code dùng lock riêng để tránh start và stop recorder chạy chồng lên nhau.

Các biến đơn giản như `running`, `muted`, `recording` dùng `volatile` để thread này thay đổi thì thread khác nhìn thấy ngay.

## 24. Logic database và DAO

Các DAO chịu trách nhiệm giao tiếp với database thông qua Hibernate.

Các DAO chính gồm:

- `UserDAO`: tìm, lưu và cập nhật user.
- `FriendshipDAO`: quản lý bạn bè, block và mute.
- `MessageDAO`: lưu và tải tin nhắn.
- `GroupDAO`: quản lý nhóm.
- `GroupMemberDAO`: quản lý thành viên nhóm.
- `MessageReactionDAO`: quản lý cảm xúc tin nhắn.
- `CallLogDAO`: lưu lịch sử cuộc gọi.

Mẫu xử lý chung của DAO là:

```text
Mở Hibernate Session
-> bắt đầu Transaction nếu ghi dữ liệu
-> query, persist hoặc merge
-> commit nếu thành công
-> rollback nếu lỗi
-> đóng Session
```

## 25. Tổng kết luồng hoạt động chính

Tóm lại, toàn bộ hệ thống hoạt động theo mô hình client-server.

Với chat:

```text
Người dùng thao tác UI
-> Controller tạo Packet
-> ChatClient gửi TCP
-> Server ClientHandler nhận
-> Service xử lý logic
-> DAO lưu hoặc đọc database
-> Server gửi Packet phản hồi
-> Client nhận Packet
-> Controller cập nhật JavaFX UI
```

Với cuộc gọi:

```text
TCP dùng để mời, chấp nhận, từ chối, kết thúc cuộc gọi
UDP dùng để truyền audio/video realtime
Server relay UDP dựa trên callId
Client dùng nhiều thread để capture và playback media
```

Điểm nổi bật của project là hệ thống không chỉ chat text cơ bản mà còn có nhiều tính năng như chat nhóm, gửi media, reaction, recall, edit, block, mute, profile, admin, mã hóa tin nhắn và gọi audio/video realtime.

Em xin kết thúc phần trình bày tại đây. Cảm ơn thầy/cô và các bạn đã lắng nghe.
