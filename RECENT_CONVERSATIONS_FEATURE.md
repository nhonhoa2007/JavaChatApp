# Recent Conversations Feature

## Overview
This feature adds a "Recent Conversations" view to the chat interface that displays a list of people and groups you've recently chatted with. When you click the chat icon (💬), you'll see recent conversations at the top level instead of having to navigate to Contacts or Groups.

## Changes Made

### 1. FXML Changes (src/main/resources/fxml/Chat.fxml)

#### Header Text Update
- Changed the toolbar title from "Chọn cuộc trò chuyện để bắt đầu" to "Tin nhắn gần đây"

#### New UI Structure
The `viewChat` VBox now contains two sub-views:

1. **viewRecentConversations** (VBox)
   - Contains a ListView (`listRecentConversations`) showing recent conversations
   - Visible by default when entering the chat view
   - Displays people and groups you've recently messaged

2. **viewChatMessages** (VBox) 
   - Contains the message list and composer (input area)
   - Hidden by default
   - Becomes visible when you select a conversation
   - When you click back, returns to recent conversations

### 2. ChatController Changes (src/main/java/org/example/client/controller/ChatController.java)

#### New FXML Fields
```java
@FXML
private ListView<String> listRecentConversations;

@FXML
private VBox viewRecentConversations;

@FXML
private VBox viewChatMessages;
```

#### New Data Structures
```java
// Recent conversations tracking
private final ObservableList<String> recentConversations = FXCollections.observableArrayList();
private final Map<String, String> conversationKeyMap = new HashMap<>(); // Display name -> conversation key
```

#### New Methods

**switchToChatMessages()** 
- Hides the recent conversations list
- Shows the message view and composer

**switchToRecentConversations()**
- Shows the recent conversations list  
- Hides the message view and composer

**handleRecentConversationSelected(String displayName, String conversationKey)**
- Called when user clicks on a recent conversation
- Automatically selects the corresponding friend or group
- Triggers the normal chat flow

**addToRecentConversations(String displayName, String conversationKey)**
- Adds or moves a conversation to the top of the recent list
- Keeps display name and conversation key in sync
- Limits list to 20 recent conversations

#### Updated Methods

**initialize()**
- Sets up listener for recent conversations ListView
- Calls `switchToChatMessages()` when opening a chat
- Calls `switchToRecentConversations()` when closing a chat
- Calls `addToRecentConversations()` when selecting a friend/group

**handleIncomingMessage()**
- Now calls `addToRecentConversations()` to add sender to recent list
- Updates when receiving messages from someone new

**handleIncomingGroupMessage()**
- Now calls `addToRecentConversations()` for group messages
- Includes null check for listGroups

**handleChatAck()** 
- Now calls `addToRecentConversations()` when sending private messages

**handleGroupMessageAck()**
- Now calls `addToRecentConversations()` when sending group messages
- Includes null check for listGroups

## User Experience

### Before
1. Click chat icon (💬)
2. See empty chat area with message "Chọn cuộc trò chuyện để bắt đầu"
3. Must go to Contacts (👥) to find friends
4. Must go to Groups to find groups
5. Select person/group and start chatting

### After
1. Click chat icon (💬)
2. See list of recent conversations (people and groups)
3. Can directly click on recent conversation to open it
4. Still have access to full Contacts and Groups tabs if needed
5. Contacts and Groups automatically added to recent list when you message them

## Key Features

- **Automatic Tracking**: Conversations are automatically added to the recent list when you:
  - Receive a message from someone
  - Send a message to someone
  - Open a group chat
  
- **Memory Efficient**: Only stores up to 20 recent conversations

- **Clean Navigation**: Easy to see who you've been talking to and resume conversations

- **Dual Display**: Recent conversations can show both private chats (usernames) and group chats (group names with member count)

- **Context Preservation**: When clicking a recent conversation, it automatically selects them in the appropriate list (Friends/Groups) and loads their chat history

## File Summary

- **Chat.fxml**: 71 lines (updated UI structure)
- **ChatController.java**: 1463 lines (added/updated 13 methods + data structures)

Total changes: ~100 lines of new code + structure reorganization

