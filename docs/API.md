# Camera Relay Server WebSocket API

Complete API documentation for the Camera Relay Server WebSocket protocol.

## Connection

### Endpoint
```
ws://your-server:8080/camera-relay
```

For production with HTTPS:
```
wss://your-server.com/camera-relay
```

### Connection Lifecycle

1. Client opens WebSocket connection
2. Client sends CREATE_ROOM or JOIN_ROOM message
3. Server confirms with ROOM_CREATED or CONNECTED
4. Clients exchange COMMAND and RESPONSE messages
5. Connection closes (automatic cleanup)

---

## Message Format

All messages are JSON objects with the following structure:

```typescript
{
  type: string,          // Required: Message type
  pin?: string,          // Optional: 4-digit room PIN
  deviceType?: string,   // Optional: "SERVER" or "CLIENT"
  command?: string,      // Optional: Camera command
  message?: string       // Optional: Text message/response
}
```

---

## Message Types

### 1. CREATE_ROOM

**Direction**: Client → Server  
**Sender**: SERVER device (camera phone)  
**Purpose**: Create a new room with a PIN

**Request**:
```json
{
  "type": "CREATE_ROOM",
  "pin": "1234",
  "deviceType": "SERVER"
}
```

**Response** (Success):
```json
{
  "type": "ROOM_CREATED",
  "pin": "1234"
}
```

**Response** (Error):
```json
{
  "type": "ERROR",
  "message": "Room already has a server"
}
```

**Validation**:
- `pin` must be exactly 4 digits
- `deviceType` must be "SERVER"
- Room can only have one SERVER

---

### 2. JOIN_ROOM

**Direction**: Client → Server  
**Sender**: CLIENT device (controller phone)  
**Purpose**: Join an existing room

**Request**:
```json
{
  "type": "JOIN_ROOM",
  "pin": "1234",
  "deviceType": "CLIENT"
}
```

**Response** (Success):
```json
{
  "type": "CONNECTED",
  "pin": "1234"
}
```

**Additional Notification to SERVER**:
```json
{
  "type": "CLIENT_CONNECTED"
}
```

**Response** (Error - No Server):
```json
{
  "type": "ERROR",
  "message": "No server in room. Server must create room first."
}
```

**Response** (Error - Room Full):
```json
{
  "type": "ERROR",
  "message": "Room already has a client"
}
```

**Validation**:
- `pin` must be exactly 4 digits
- `deviceType` must be "CLIENT"
- Room must have a SERVER before CLIENT can join
- Room can only have one CLIENT

---

### 3. COMMAND

**Direction**: CLIENT → SERVER (via relay)  
**Sender**: CLIENT device  
**Purpose**: Send camera command from controller to camera

**Request**:
```json
{
  "type": "COMMAND",
  "command": "TAKE_PHOTO"
}
```

**Supported Commands**:
- `TAKE_PHOTO` - Capture a photo
- `START_RECORDING` - Start video recording
- `STOP_RECORDING` - Stop video recording
- `SWITCH_CAMERA` - Toggle front/back camera

**Server Receives**:
```json
{
  "type": "COMMAND",
  "command": "TAKE_PHOTO"
}
```

**Notes**:
- CLIENT must be in a room
- SERVER must be connected to receive command
- Relay server forwards message unchanged

---

### 4. RESPONSE

**Direction**: SERVER → CLIENT (via relay)  
**Sender**: SERVER device  
**Purpose**: Send response from camera to controller

**Request**:
```json
{
  "type": "RESPONSE",
  "message": "Photo captured successfully"
}
```

**Client Receives**:
```json
{
  "type": "RESPONSE",
  "message": "Photo captured successfully"
}
```

**Notes**:
- SERVER must be in a room
- CLIENT must be connected to receive response
- Can include status, errors, or confirmations

---

### 5. ROOM_CREATED

**Direction**: Server → CLIENT (SERVER device)  
**Automated**: Yes  
**Purpose**: Confirm room creation

**Message**:
```json
{
  "type": "ROOM_CREATED",
  "pin": "1234"
}
```

**Received by**: SERVER device after successful CREATE_ROOM

---

### 6. CONNECTED

**Direction**: Server → CLIENT (CLIENT device)  
**Automated**: Yes  
**Purpose**: Confirm successful room join

**Message**:
```json
{
  "type": "CONNECTED",
  "pin": "1234"
}
```

**Received by**: CLIENT device after successful JOIN_ROOM

---

### 7. CLIENT_CONNECTED

**Direction**: Server → SERVER device  
**Automated**: Yes  
**Purpose**: Notify SERVER that CLIENT joined

**Message**:
```json
{
  "type": "CLIENT_CONNECTED"
}
```

**Received by**: 
- SERVER device when CLIENT joins
- SERVER device if CLIENT was already present when SERVER joined

---

### 8. CLIENT_DISCONNECTED

**Direction**: Server → SERVER device  
**Automated**: Yes  
**Purpose**: Notify SERVER that CLIENT left

**Message**:
```json
{
  "type": "CLIENT_DISCONNECTED"
}
```

**Received by**: SERVER device when CLIENT disconnects

---

### 9. SERVER_DISCONNECTED

**Direction**: Server → CLIENT device  
**Automated**: Yes  
**Purpose**: Notify CLIENT that SERVER left

**Message**:
```json
{
  "type": "SERVER_DISCONNECTED"
}
```

**Received by**: CLIENT device when SERVER disconnects

---

### 10. ERROR

**Direction**: Server → Client  
**Automated**: Yes  
**Purpose**: Report errors

**Message**:
```json
{
  "type": "ERROR",
  "message": "Error description"
}
```

**Common Errors**:
- `"PIN and deviceType are required"`
- `"PIN must be 4 digits"`
- `"Room already has a server"`
- `"Room already has a client"`
- `"No server in room. Server must create room first."`
- `"Invalid JSON format"`
- `"Unknown message type: TYPE_NAME"`

---

## Complete Flow Examples

### Example 1: Standard Connection Flow

**Step 1 - SERVER creates room**:
```
CLIENT (Camera Phone):
→ {"type": "CREATE_ROOM", "pin": "5678", "deviceType": "SERVER"}
← {"type": "ROOM_CREATED", "pin": "5678"}
```

**Step 2 - CLIENT joins room**:
```
CLIENT (Controller Phone):
→ {"type": "JOIN_ROOM", "pin": "5678", "deviceType": "CLIENT"}
← {"type": "CONNECTED", "pin": "5678"}

SERVER (Camera Phone):
← {"type": "CLIENT_CONNECTED"}
```

**Step 3 - CLIENT sends command**:
```
CLIENT (Controller):
→ {"type": "COMMAND", "command": "TAKE_PHOTO"}

SERVER (Camera):
← {"type": "COMMAND", "command": "TAKE_PHOTO"}
```

**Step 4 - SERVER sends response**:
```
SERVER (Camera):
→ {"type": "RESPONSE", "message": "Photo saved"}

CLIENT (Controller):
← {"type": "RESPONSE", "message": "Photo saved"}
```

---

### Example 2: CLIENT Joins Before SERVER (Error)

```
CLIENT (Controller Phone):
→ {"type": "JOIN_ROOM", "pin": "9999", "deviceType": "CLIENT"}
← {"type": "ERROR", "message": "No server in room. Server must create room first."}
```

**Solution**: SERVER must create room first

---

### Example 3: Multiple CLIENTs (Error)

```
# First CLIENT
CLIENT 1:
→ {"type": "JOIN_ROOM", "pin": "1111", "deviceType": "CLIENT"}
← {"type": "CONNECTED", "pin": "1111"}

# Second CLIENT (fails)
CLIENT 2:
→ {"type": "JOIN_ROOM", "pin": "1111", "deviceType": "CLIENT"}
← {"type": "ERROR", "message": "Room already has a client"}
```

**Note**: Rooms support only 1 SERVER + 1 CLIENT

---

### Example 4: Disconnection Handling

**SERVER disconnects**:
```
SERVER (Camera):
[Connection closes]

CLIENT (Controller):
← {"type": "SERVER_DISCONNECTED"}
```

**CLIENT disconnects**:
```
CLIENT (Controller):
[Connection closes]

SERVER (Camera):
← {"type": "CLIENT_DISCONNECTED"}
```

**Room cleanup**: If both disconnect, room is automatically deleted

---

## REST Endpoints

### Health Check

**Endpoint**: `GET /health`  
**Response**: `200 OK`  
**Body**: `OK`

**Example**:
```bash
curl http://your-server:8080/health
```

---

### Status Page

**Endpoint**: `GET /`  
**Response**: `200 OK`  
**Content-Type**: `text/plain`

**Example**:
```bash
curl http://your-server:8080/
```

**Response**:
```
Camera Relay Server
===================
Status: Running
Active Rooms: 3
Active Connections: 5

WebSocket Endpoint: /camera-relay
Health Check: /health
```

---

## Testing with wscat

### Install wscat
```bash
npm install -g wscat
```

### Test Connection
```bash
wscat -c ws://localhost:8080/camera-relay
```

### Create Room (as SERVER)
```
Connected (press CTRL+C to quit)
> {"type": "CREATE_ROOM", "pin": "1234", "deviceType": "SERVER"}
< {"type": "ROOM_CREATED", "pin": "1234"}
```

### Join Room (in another terminal as CLIENT)
```bash
wscat -c ws://localhost:8080/camera-relay
```
```
Connected (press CTRL+C to quit)
> {"type": "JOIN_ROOM", "pin": "1234", "deviceType": "CLIENT"}
< {"type": "CONNECTED", "pin": "1234"}
```

### Send Command
```
> {"type": "COMMAND", "command": "TAKE_PHOTO"}
```

(SERVER terminal will receive the command)

---

## Error Handling

### Client-Side Recommendations

1. **Validate Messages Before Sending**
   - Ensure PIN is 4 digits
   - Ensure deviceType is "SERVER" or "CLIENT"
   - Include all required fields

2. **Handle Errors Gracefully**
   - Parse ERROR messages
   - Display user-friendly error messages
   - Allow retry on recoverable errors

3. **Handle Disconnections**
   - Listen for SERVER_DISCONNECTED / CLIENT_DISCONNECTED
   - Implement reconnection logic
   - Show connection status to user

4. **Implement Timeouts**
   - Timeout if no ROOM_CREATED/CONNECTED within 5 seconds
   - Timeout if no RESPONSE within 30 seconds
   - Implement WebSocket ping/pong monitoring

### Example Error Handler (JavaScript)
```javascript
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  
  if (msg.type === 'ERROR') {
    console.error('Server error:', msg.message);
    showUserError(msg.message);
  } else if (msg.type === 'ROOM_CREATED') {
    console.log('Room created:', msg.pin);
    showRoomCode(msg.pin);
  }
  // ... handle other message types
};
```

---

## Security Considerations

1. **PIN Strength**: 4-digit PINs provide 10,000 combinations
   - Sufficient for temporary pairing
   - Not suitable for long-term security
   - Consider adding expiration/rate limiting for production

2. **Message Validation**: Server validates all messages
   - Checks required fields
   - Validates PIN format
   - Ensures device types are correct

3. **Isolation**: Rooms are isolated
   - Messages only sent between paired devices
   - No cross-room communication
   - Automatic cleanup on disconnect

4. **Transport Security**: Use WSS (WebSocket Secure) in production
   - Encrypt all traffic
   - Prevent MITM attacks
   - Required for HTTPS sites

---

## Rate Limiting (Not Implemented)

For production deployment, consider adding:
- Max rooms per IP
- Max connection attempts per minute
- Command rate limiting
- PIN attempt limiting

---

## Future Enhancements

Potential improvements:
- Authentication tokens
- Room expiration
- Multiple clients per room
- Broadcast to multiple servers
- Message encryption
- File transfer support
- Screen sharing

---

## Support

For issues or questions:
- Check server logs
- Verify WebSocket connection
- Test with wscat
- Review error messages

Server logs include:
- Connection events
- Room creation/join
- Message routing
- Disconnections
- Errors with stack traces
