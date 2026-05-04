# Recent Conversations Feature - Testing Guide

## How to Build and Run

1. **Build the project:**
   ```bash
   mvn clean package
   ```

2. **Run the application:**
   ```bash
   mvn javafx:run
   ```

## How to Test the Feature

### Test Case 1: View Recent Conversations on First Login
1. Login to the application
2. Click the chat icon (💬) in the left sidebar
3. **Expected:** You should see a list view with title "Tin nhắn gần đây" (Recent Messages)
4. **Note:** The list will be empty on first login

### Test Case 2: Add Conversations to Recent List
1. Go to Contacts (👥) tab
2. Select a friend and start a conversation
3. Send a message
4. Go back to Chat (💬) tab
5. **Expected:** The friend should now appear in your recent conversations list

### Test Case 3: Open Recent Conversations
1. From the Chat (💬) tab, click on a person in the recent conversations list
2. **Expected:** The chat window opens with that person's message history
3. **Note:** If you click "back" or switch tabs, you should return to the recent conversations list

### Test Case 4: Receive Messages from New Contacts
1. Have two users logged in (or test with different accounts)
2. User B sends a message to User A
3. User A goes to Chat (💬) tab  
4. **Expected:** User B automatically appears in the recent conversations list

### Test Case 5: Group Conversations
1. Go to Groups tab and create a group or join a group
2. Send a message to the group
3. Go back to Chat (💬) tab
4. **Expected:** The group appears in the recent conversations list as "GroupName (memberCount)"

### Test Case 6: Recent Conversations Order
1. Open recent conversations
2. Message multiple different people/groups
3. **Expected:** Most recently messaged person/group appears at the top of the list
4. **Note:** When you message someone already in the list, they move to the top

### Test Case 7: Limit to 20 Conversations
1. Message more than 20 different people
2. **Expected:** Only the 20 most recent conversations are shown
3. The oldest conversations are removed from the list

## UI Elements

### Before the Changes
```
Chat Tab (empty)
└─ "Chọn cuộc trò chuyện để bắt đầu"
└─ (User had to go to Contacts or Groups tabs)
```

### After the Changes  
```
Chat Tab
├─ Header: "Tin nhắn gần đây"
├─ Recent Conversations View (visible by default)
│  └─ ListView with recent people/groups
└─ (When you select a conversation)
   └─ Chat Messages View
      ├─ Message list
      └─ Message composer
```

## Key Observations

### What Should Work
- ✅ Recent conversations appear automatically when messaging
- ✅ Clicking a recent conversation opens that chat
- ✅ Recent conversations list reorders on each message
- ✅ Both private chats and group chats appear together
- ✅ Can still access full Contacts and Groups tabs

### What Shouldn't Happen
- ❌ Chat icon (💬) should NOT show contacts or groups by default anymore
- ❌ You should NOT need to go to separate tabs to find recent contacts
- ❌ Closed chats should NOT remove conversations from recent list

## Troubleshooting

### Issue: Recent conversations list is empty
- **Solution:** Send at least one message to someone, then open the Chat tab

### Issue: Recent conversations don't appear when receiving messages
- **Solution:** Check that notifications are enabled. The sender must not be in your blocked list

### Issue: Group doesn't show in recent conversations after messaging
- **Solution:** Make sure you're actually part of the group. The group must be loaded in your groups list

### Issue: Encoding shows Vietnamese text as question marks
- **Solution:** This is usually a display issue in the IDE only. The application should render Vietnamese correctly

