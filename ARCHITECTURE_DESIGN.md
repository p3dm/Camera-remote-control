# Android Camera Remote Control - Complete Architecture Design

## Executive Summary
This document describes a comprehensive architecture for an Android application that operates in dual modes: **Server** (Camera Device) and **Client** (Remote Controller). The app uses TCP socket communication for real-time camera control.

---

## 1. USE CASE OVERVIEW

### Primary Actors
- **User (Server Role)**: Device owner who wants to share camera access
- **User (Client Role)**: Remote user who wants to control another device's camera

### Use Cases

#### UC-1: Select Application Mode
**Actor**: User  
**Precondition**: App is launched  
**Flow**:
1. User sees Role Selection Screen
2. User chooses "Become Server" or "Become Client"
3. System navigates to appropriate screen

#### UC-2: Server - Share Camera Access
**Actor**: User (Server)  
**Precondition**: User selected "Become Server"  
**Flow**:
1. System automatically starts ServerSocket on port 2000
2. System retrieves device's local IP address
3. System displays IP in read-only field
4. User shares IP with potential clients
5. System listens for incoming connections
6. When client connects:
   - System validates connection request
   - System sends "ACCEPT" response
   - System transitions to Camera Screen
   - System starts camera preview
7. System listens for commands while camera is active
8. For each command received:
   - System validates command
   - System executes camera operation
   - System sends response/acknowledgment

#### UC-3: Client - Control Remote Camera
**Actor**: User (Client)  
**Precondition**: User selected "Become Client"  
**Flow**:
1. System displays Connection Screen
2. User enters Server IP address
3. User presses "Connect"
4. System attempts TCP connection to Server IP:2000
5. System waits for "ACCEPT" response (timeout: 10s)
6. On success:
   - System displays "Connected Successfully"
   - System enables control buttons
   - System transitions to Remote Control Screen
7. User sends commands:
   - Take Photo
   - Start/Stop Video Recording
   - Switch Camera
8. For each command:
   - System sends command over socket
   - System waits for response
   - System displays status/result

---

## 2. APPLICATION FLOW DIAGRAMS

### 2.1 Server Side Flow

```
[App Launch]
     ↓
[Role Selection Screen]
     ↓
[User: "Become Server"]
     ↓
[Initialize ServerSocket on port 2000]
     ↓
[Fetch Local IP Address] → [Display IP in read-only TextField]
     ↓
[Show "Waiting for connection..." status]
     ↓
[Listen Loop: serverSocket.accept()]
     ↓
[Client Connection Detected]
     ↓
[Validate Connection]
     ↓
[Send "ACCEPT" response to client]
     ↓
[Transition to Camera Screen]
     ↓
[Initialize CameraX]
     ↓
[Start Camera Preview]
     ↓
┌─────────────────────────────────────┐
│ Active State:                       │
│ - Camera preview running            │
│ - Socket listening for commands     │
│                                     │
│ Command Loop:                       │
│   ↓                                 │
│ [Receive Command]                   │
│   ↓                                 │
│ [Parse & Validate]                  │
│   ↓                                 │
│ [Execute Camera Operation]          │
│   ↓                                 │
│ [Send Response/ACK]                 │
│   ↓                                 │
│ [Back to Listen]                    │
└─────────────────────────────────────┘
     ↓
[Client Disconnects OR User Stops Server]
     ↓
[Release Camera Resources]
     ↓
[Close ServerSocket]
     ↓
[Return to Role Selection Screen]
```

### 2.2 Client Side Flow

```
[App Launch]
     ↓
[Role Selection Screen]
     ↓
[User: "Become Client"]
     ↓
[Connection Screen]
     ↓
[Show IP Input Field + "Connect" Button]
     ↓
[User enters Server IP]
     ↓
[User presses "Connect"]
     ↓
[Initialize Socket(serverIP, 2000)]
     ↓
[Show "Connecting..." status]
     ↓
[Wait for "ACCEPT" response] (timeout: 10s)
     ↓
     ├─ [Timeout/Error] → [Show Error Message]
     │                    [Enable retry]
     │
     └─ [ACCEPT received]
          ↓
        [Update UI: Connected]
          ↓
        [Change "Connect" → "Disconnect"]
          ↓
        [Disable IP Input]
          ↓
        [Enable Control Buttons]
          ↓
        [Transition to Remote Control Screen]
          ↓
┌─────────────────────────────────────┐
│ Connected State:                    │
│                                     │
│ User Actions:                       │
│   - Press "Take Photo"              │
│   - Press "Record/Stop Video"       │
│   - Press "Switch Camera"           │
│                                     │
│ For Each Action:                    │
│   ↓                                 │
│ [Send Command via Socket]           │
│   ↓                                 │
│ [Show "Processing..." status]       │
│   ↓                                 │
│ [Wait for Response]                 │
│   ↓                                 │
│ [Display Result/Status]             │
│   ↓                                 │
│ [Ready for next command]            │
└─────────────────────────────────────┘
     ↓
[User presses "Disconnect" OR Connection Lost]
     ↓
[Close Socket]
     ↓
[Reset UI State]
     ↓
[Enable IP Input]
     ↓
[Change "Disconnect" → "Connect"]
     ↓
[Disable Control Buttons]
```

---

## 3. STATE MACHINES

### 3.1 Server State Machine

```
States:
- IDLE: Role not selected
- LISTENING: ServerSocket active, waiting for connection
- CONNECTED: Client connected, camera active
- PROCESSING_COMMAND: Executing camera operation
- ERROR: Connection/operation error
- TERMINATED: Server stopped

Transitions:

IDLE → LISTENING
  Trigger: User selects "Become Server"
  Action: Start ServerSocket, fetch IP, display

LISTENING → CONNECTED
  Trigger: Client connection accepted
  Action: Send ACCEPT, start camera, begin command loop

CONNECTED → PROCESSING_COMMAND
  Trigger: Command received from client
  Action: Parse command, validate

PROCESSING_COMMAND → CONNECTED
  Trigger: Command execution complete
  Action: Send response/ACK

CONNECTED → LISTENING
  Trigger: Client disconnects gracefully
  Action: Release camera, wait for new connection

CONNECTED → ERROR
  Trigger: Socket error, invalid command
  Action: Log error, attempt recovery

ERROR → LISTENING
  Trigger: Error resolved
  Action: Resume listening

LISTENING → TERMINATED
  Trigger: User stops server
  Action: Close ServerSocket, cleanup

CONNECTED → TERMINATED
  Trigger: User stops server
  Action: Close socket, release camera, cleanup
```

### 3.2 Client State Machine

```
States:
- IDLE: Role not selected
- DISCONNECTED: In client mode, not connected
- CONNECTING: Connection attempt in progress
- CONNECTED: Connected to server, ready for commands
- SENDING_COMMAND: Command sent, waiting for response
- ERROR: Connection/command error

Transitions:

IDLE → DISCONNECTED
  Trigger: User selects "Become Client"
  Action: Show connection screen

DISCONNECTED → CONNECTING
  Trigger: User presses "Connect"
  Action: Initiate socket connection

CONNECTING → CONNECTED
  Trigger: ACCEPT received from server
  Action: Enable controls, update UI

CONNECTING → ERROR
  Trigger: Connection timeout/refused
  Action: Show error, enable retry

ERROR → CONNECTING
  Trigger: User retries connection
  Action: Attempt connection again

CONNECTED → SENDING_COMMAND
  Trigger: User presses control button
  Action: Send command via socket

SENDING_COMMAND → CONNECTED
  Trigger: Response received from server
  Action: Display result, ready for next command

SENDING_COMMAND → ERROR
  Trigger: No response/socket error
  Action: Show error, attempt reconnection

CONNECTED → DISCONNECTED
  Trigger: User presses "Disconnect"
  Action: Close socket, reset UI

CONNECTED → ERROR
  Trigger: Connection lost unexpectedly
  Action: Show error, reset to disconnected
```

---

## 4. SCREEN TRANSITIONS

### 4.1 Screen Hierarchy

```
RoleSelectionActivity (Entry Point)
    ↓
    ├─→ ServerActivity (Server Mode)
    │       ↓
    │   ServerCameraActivity (Camera with command listening)
    │
    └─→ ClientConnectionActivity (Client Mode)
            ↓
        ClientControlActivity (Remote control interface)
```

### 4.2 Detailed Screen Specifications

#### Screen 1: RoleSelectionActivity
**Purpose**: Entry point for mode selection

**UI Components**:
- App Title/Logo
- Button: "Become Server" (Primary action)
- Button: "Become Client" (Secondary action)
- Info text: Brief description of each mode

**Actions**:
- On "Become Server" click:
  - Launch ServerActivity
  - Pass FLAG_ACTIVITY_NEW_TASK
- On "Become Client" click:
  - Launch ClientConnectionActivity
  - Pass FLAG_ACTIVITY_NEW_TASK

---

#### Screen 2: ServerActivity
**Purpose**: Display server IP and manage incoming connections

**UI Components**:
- TextView: "Server Mode"
- TextView: "Your IP Address:" (label)
- EditText: [IP Address] (read-only, selectable for copy)
- Button: "Copy IP" (copies to clipboard)
- TextView: Status indicator
  - "Server running on port 2000"
  - "Waiting for client connection..."
  - "Client connected from [Client IP]"
- Button: "Stop Server" (returns to role selection)
- RecyclerView: Connection log (optional)

**Lifecycle**:
- `onCreate()`:
  - Start ServerSocket immediately
  - Fetch and display local IP
  - Begin accept() loop in background thread
- `onDestroy()`:
  - Close ServerSocket
  - Clean up threads

**State Indicators**:
- Status color: Gray (waiting) → Green (connected)
- Ripple effect on successful connection

---

#### Screen 3: ServerCameraActivity
**Purpose**: Camera preview with remote command execution

**UI Components**:
- SurfaceView/PreviewView: Camera preview (full screen)
- Overlay UI:
  - Top bar:
    - TextView: "Remote Control Active"
    - TextView: Connected client IP
    - Button: "Stop Sharing"
  - Bottom bar:
    - Indicator: Recording status (red dot when recording)
    - TextView: Last command executed
- Toast/Snackbar: Command execution notifications

**Behavior**:
- Camera remains active continuously
- Background thread listens for commands
- Commands execute on main thread (for UI safety)
- Visual feedback for each command:
  - Photo: Screen flash effect
  - Video start: Red recording indicator
  - Video stop: Indicator disappears
  - Camera flip: Brief preview freeze during switch

**Lifecycle**:
- `onCreate()`:
  - Initialize CameraX
  - Start preview
  - Continue socket listening from ServerActivity
- `onPause()`:
  - Pause camera if app backgrounded (optional: keep running)
- `onDestroy()`:
  - Release camera
  - Close client socket
  - Return ServerSocket to listening mode

---

#### Screen 4: ClientConnectionActivity
**Purpose**: Connect to server

**UI Components**:
- TextView: "Client Mode"
- LinearLayout (horizontal):
  - EditText: Server IP input
    - Hint: "192.168.1.100"
    - Input type: IP address
  - Button: "Connect" (initially) / "Disconnect" (when connected)
- TextView: Status message
  - Default: "Disconnected"
  - Connecting: "⏳ Connecting to [IP]..."
  - Success: "✅ Connected successfully!"
  - Error: "❌ Connection failed: [reason]"
- Button: "Back" (returns to role selection)

**Behavior**:
- IP validation on input
- Connect button disabled if IP invalid
- Connection timeout: 10 seconds
- On successful connection:
  - Change button to "Disconnect"
  - Disable IP input
  - Auto-navigate to ClientControlActivity after 1s
- On failure:
  - Show error message
  - Keep IP input enabled
  - Allow retry

**State Management**:
- Store connection state in ViewModel
- Preserve state on rotation

---

#### Screen 5: ClientControlActivity
**Purpose**: Remote camera control interface

**UI Components**:
- Top section (Connection Info):
  - TextView: "Connected to: [Server IP]"
  - Button: "Disconnect"
- Middle section (Status):
  - TextView: Real-time status updates
    - "Ready"
    - "Taking photo..."
    - "Recording..."
    - "Stopped recording"
- Bottom section (Controls):
  - Grid layout (2x2):
    - ImageButton: 📷 Take Photo
    - ImageButton: 🎥 Record Video / ⏹️ Stop Recording (toggle)
    - ImageButton: 🔄 Switch Camera
    - ImageButton: 🖼️ View Gallery (future feature)

**Behavior**:
- All buttons disabled initially
- Enable after connection confirmed
- Button states:
  - Take Photo: One-shot action
    - Press → Send command → Show "Taking photo..."
    - On response → Show "Photo captured!"
    - Flash animation on success
  - Record Video: Toggle behavior
    - First press → Change to Stop icon, send START_RECORD
    - Status: "Recording... ⏺️"
    - Second press → Change back to Record icon, send STOP_RECORD
    - Status: "Stopped recording"
  - Switch Camera: One-shot action
    - Press → Send command → Show "Switching camera..."
- Visual feedback:
  - Button press: Ripple effect
  - Processing: Disable button + show progress
  - Success: Re-enable button
  - Error: Show toast, re-enable button

**Connection Loss Handling**:
- Automatic detection via socket exception
- Show dialog: "Connection lost. Reconnect?"
- Options: "Retry" / "Go Back"
- On "Go Back": Return to ClientConnectionActivity

---

## 5. HIGH-LEVEL ARCHITECTURE

### 5.1 Component Layers

```
┌─────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                      │
│  ┌──────────────────┐              ┌──────────────────┐    │
│  │  SERVER VIEWS    │              │  CLIENT VIEWS    │    │
│  │  - Role Select   │              │  - Role Select   │    │
│  │  - Server Screen │              │  - Connection    │    │
│  │  - Camera Screen │              │  - Control Panel │    │
│  └────────┬─────────┘              └─────────┬────────┘    │
└───────────┼────────────────────────────────────┼─────────────┘
            │                                    │
┌───────────┼────────────────────────────────────┼─────────────┐
│           │       VIEWMODEL / PRESENTER        │             │
│  ┌────────▼─────────┐              ┌──────────▼─────────┐  │
│  │ ServerViewModel  │              │ ClientViewModel    │  │
│  │ - Connection     │              │ - Connection       │  │
│  │   state          │              │   state            │  │
│  │ - Command queue  │              │ - Command history  │  │
│  └────────┬─────────┘              └──────────┬─────────┘  │
└───────────┼────────────────────────────────────┼─────────────┘
            │                                    │
┌───────────┼────────────────────────────────────┼─────────────┐
│           │          BUSINESS LOGIC LAYER      │             │
│  ┌────────▼─────────┐              ┌──────────▼─────────┐  │
│  │ CommandHandler   │              │ CommandSender      │  │
│  │ - Parse commands │              │ - Send commands    │  │
│  │ - Execute actions│              │ - Handle responses │  │
│  │ - Send responses │              │                    │  │
│  └────────┬─────────┘              └──────────┬─────────┘  │
│           │                                    │             │
│  ┌────────▼─────────┐                        │             │
│  │ CameraController │                        │             │
│  │ - CameraX API    │                        │             │
│  │ - Photo capture  │                        │             │
│  │ - Video recording│                        │             │
│  │ - Camera switch  │                        │             │
│  └──────────────────┘                        │             │
└───────────┼────────────────────────────────────┼─────────────┘
            │                                    │
┌───────────┼────────────────────────────────────┼─────────────┐
│           │        NETWORK / SOCKET LAYER      │             │
│  ┌────────▼─────────┐              ┌──────────▼─────────┐  │
│  │ ServerSocket     │◄────────────►│ ClientSocket       │  │
│  │ - Accept conn.   │   TCP/IP     │ - Connect          │  │
│  │ - Read commands  │   Port 2000  │ - Send commands    │  │
│  │ - Write responses│              │ - Read responses   │  │
│  └──────────────────┘              └────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Class Structure

#### Server Side Classes

```kotlin
// Network Layer
class CameraSocketServer(
    port: Int = 2000,
    private val commandHandler: CommandHandler
) {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isRunning: Boolean = false
    
    fun start()
    fun stop()
    fun acceptConnection(): Boolean
    fun listenForCommands()
    fun sendResponse(message: String)
    fun getLocalIpAddress(): String
}

// Business Logic
class ServerCommandHandler(
    private val cameraController: CameraController
) {
    fun handleCommand(command: String): String {
        return when (command) {
            "TAKE_PHOTO" -> {
                cameraController.takePhoto()
                "ACK:PHOTO_CAPTURED"
            }
            "START_RECORD" -> {
                cameraController.startRecording()
                "ACK:RECORDING_STARTED"
            }
            "STOP_RECORD" -> {
                cameraController.stopRecording()
                "ACK:RECORDING_STOPPED"
            }
            "SWITCH_CAMERA" -> {
                cameraController.flipCamera()
                "ACK:CAMERA_SWITCHED"
            }
            else -> "ERROR:UNKNOWN_COMMAND"
        }
    }
}

// Camera Layer
class CameraController(
    private val context: Context,
    private val previewView: PreviewView
) {
    private var camera: Camera? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    
    fun startCamera()
    fun stopCamera()
    fun takePhoto(): Boolean
    fun startRecording(): Boolean
    fun stopRecording(): Boolean
    fun flipCamera(): Boolean
}

// ViewModel
class ServerViewModel : ViewModel() {
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _lastCommand = MutableLiveData<String>()
    val lastCommand: LiveData<String> = _lastCommand
    
    fun startServer()
    fun stopServer()
    fun onCommandReceived(command: String)
}
```

#### Client Side Classes

```kotlin
// Network Layer
class CameraSocketClient(
    private val serverIp: String,
    private val port: Int = 2000
) {
    private var socket: Socket? = null
    private var isConnected: Boolean = false
    
    fun connect(): Boolean
    fun disconnect()
    fun sendCommand(command: String): String?
    fun isConnected(): Boolean
}

// Business Logic
class ClientCommandSender(
    private val socketClient: CameraSocketClient,
    private val responseHandler: ResponseHandler
) {
    fun takePhoto() {
        val response = socketClient.sendCommand("TAKE_PHOTO")
        responseHandler.handleResponse(response)
    }
    
    fun startRecording() {
        val response = socketClient.sendCommand("START_RECORD")
        responseHandler.handleResponse(response)
    }
    
    fun stopRecording() {
        val response = socketClient.sendCommand("STOP_RECORD")
        responseHandler.handleResponse(response)
    }
    
    fun switchCamera() {
        val response = socketClient.sendCommand("SWITCH_CAMERA")
        responseHandler.handleResponse(response)
    }
}

interface ResponseHandler {
    fun handleResponse(response: String?)
}

// ViewModel
class ClientViewModel : ViewModel() {
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage
    
    private val _isRecording = MutableLiveData<Boolean>(false)
    val isRecording: LiveData<Boolean> = _isRecording
    
    fun connect(serverIp: String)
    fun disconnect()
    fun sendCommand(commandType: CommandType)
}

enum class CommandType {
    TAKE_PHOTO,
    START_RECORD,
    STOP_RECORD,
    SWITCH_CAMERA
}
```

#### Shared Classes

```kotlin
// Connection state
sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

// Protocol constants
object Protocol {
    const val PORT = 2000
    const val TIMEOUT_MS = 10000
    
    // Commands
    const val CMD_TAKE_PHOTO = "TAKE_PHOTO"
    const val CMD_START_RECORD = "START_RECORD"
    const val CMD_STOP_RECORD = "STOP_RECORD"
    const val CMD_SWITCH_CAMERA = "SWITCH_CAMERA"
    const val CMD_GET_STATUS = "GET_STATUS"
    
    // Responses
    const val ACK_PREFIX = "ACK:"
    const val ERROR_PREFIX = "ERROR:"
    const val ACCEPT = "ACCEPT"
}
```

---

## 6. COMMUNICATION PROTOCOL

### 6.1 Connection Handshake

```
CLIENT                          SERVER
  │                               │
  ├──── TCP Connect ────────────►│
  │                               │
  │                        [Validate]
  │                               │
  │◄───── "ACCEPT" ──────────────┤
  │                               │
  ├──── "CLIENT_READY" ─────────►│
  │                               │
  │◄───── "ACK:READY" ───────────┤
  │                               │
[Connection Established]     [Begin Command Loop]
```

### 6.2 Command-Response Protocol

**General Format**:
```
Request:  [COMMAND]\n
Response: [STATUS]:[MESSAGE]\n
```

**Command Types**:

1. **Take Photo**
   ```
   Client → Server: "TAKE_PHOTO\n"
   Server → Client: "ACK:PHOTO_CAPTURED\n"
                     or "ERROR:CAMERA_BUSY\n"
   ```

2. **Start Recording**
   ```
   Client → Server: "START_RECORD\n"
   Server → Client: "ACK:RECORDING_STARTED\n"
                     or "ERROR:ALREADY_RECORDING\n"
   ```

3. **Stop Recording**
   ```
   Client → Server: "STOP_RECORD\n"
   Server → Client: "ACK:RECORDING_STOPPED\n"
                     or "ERROR:NOT_RECORDING\n"
   ```

4. **Switch Camera**
   ```
   Client → Server: "SWITCH_CAMERA\n"
   Server → Client: "ACK:CAMERA_SWITCHED\n"
                     or "ERROR:SWITCH_FAILED\n"
   ```

5. **Status Query**
   ```
   Client → Server: "GET_STATUS\n"
   Server → Client: "STATUS:IDLE\n"
                     or "STATUS:RECORDING\n"
                     or "STATUS:PROCESSING\n"
   ```

### 6.3 Error Handling

**Error Response Format**:
```
ERROR:[ERROR_CODE]:[DESCRIPTION]
```

**Error Codes**:
- `UNKNOWN_COMMAND`: Command not recognized
- `CAMERA_BUSY`: Camera currently processing
- `ALREADY_RECORDING`: Cannot start recording (already in progress)
- `NOT_RECORDING`: Cannot stop recording (not recording)
- `SWITCH_FAILED`: Camera flip failed
- `PERMISSION_DENIED`: Camera/storage permissions not granted
- `STORAGE_FULL`: Insufficient storage space

**Connection Errors**:
- Timeout: No response within 10 seconds
- Connection Lost: Socket closed unexpectedly
- Connection Refused: Server not reachable

---

## 7. THREAD & CONCURRENCY MANAGEMENT

### 7.1 Server Threading Model

```
┌─────────────────────────────────────────────────┐
│                   MAIN THREAD                   │
│  - UI updates                                   │
│  - Camera operations (via CameraX callbacks)   │
│  - Command execution                            │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│              BACKGROUND THREADS                 │
│                                                 │
│  Thread 1: ServerSocket.accept()               │
│  - Blocking accept() loop                      │
│  - Creates new socket for each client          │
│                                                 │
│  Thread 2: Command Listener                    │
│  - BufferedReader.readLine()                   │
│  - Parses commands                             │
│  - Posts to main thread via Handler            │
│                                                 │
│  Thread 3: Response Writer                     │
│  - Writes responses to client socket           │
│  - Flushes output stream                       │
└─────────────────────────────────────────────────┘
```

### 7.2 Client Threading Model

```
┌─────────────────────────────────────────────────┐
│                   MAIN THREAD                   │
│  - UI updates                                   │
│  - Button click handling                        │
│  - Status display                               │
└────────────┬────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────┐
│              BACKGROUND THREADS                 │
│                                                 │
│  Thread 1: Connection Thread                   │
│  - Socket.connect()                            │
│  - Posts connection result to main thread      │
│                                                 │
│  Thread 2: Command Sender                      │
│  - Sends commands via PrintWriter              │
│  - Waits for response                          │
│  - Posts response to main thread               │
│                                                 │
│  Thread 3: Response Listener (optional)        │
│  - Listens for unsolicited messages            │
│  - e.g., "RECORDING_STOPPED" if storage full   │
└─────────────────────────────────────────────────┘
```

### 7.3 Thread Safety Guidelines

- Use `ExecutorService` for thread pools
- Use `Handler` for main thread posting
- Synchronize socket access
- Use `AtomicBoolean` for connection state
- Use LiveData/Flow for UI updates

---

## 8. LIFECYCLE MANAGEMENT

### 8.1 Server Activity Lifecycle

```
onCreate():
  - Initialize ServerSocket
  - Fetch local IP
  - Start accept() thread
  - Display IP to user

onStart():
  - Resume listening if paused

onResume():
  - Update UI with current connection state

onPause():
  - Keep ServerSocket running (background service)
  - Or pause if user navigates away

onStop():
  - Option 1: Keep server running (foreground service)
  - Option 2: Notify client and pause

onDestroy():
  - Close ServerSocket
  - Close client socket
  - Release camera resources
  - Terminate all threads
```

### 8.2 Camera Activity Lifecycle

```
onCreate():
  - Initialize CameraX
  - Bind preview to PreviewView
  - Bind ImageCapture use case
  - Bind VideoCapture use case
  - Continue socket listening

onResume():
  - Ensure camera is active
  - Resume command listening

onPause():
  - Pause camera if app backgrounded
  - Or keep running with foreground service notification

onDestroy():
  - Unbind all camera use cases
  - Release camera
  - Close socket connection
  - Return ServerSocket to listening mode
```

### 8.3 Client Activity Lifecycle

```
onCreate():
  - Restore connection state
  - Initialize UI

onStart():
  - Check if still connected
  - Update button states

onResume():
  - Resume listening for responses

onPause():
  - Keep socket connection alive

onStop():
  - Maintain connection for quick resume

onDestroy():
  - Only close socket if user explicitly disconnects
  - Or if activity finishing permanently

onConfigurationChanged():
  - Retain socket connection
  - Update UI orientation
```

---

## 9. PERMISSIONS & SECURITY

### 9.1 Required Permissions

**AndroidManifest.xml**:
```xml
<!-- Camera access -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Audio for video recording -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Storage for saving photos/videos -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />

<!-- Network access -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

<!-- For foreground service (keeping server alive) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### 9.2 Runtime Permission Handling

**Server Side**:
- Request `CAMERA` and `RECORD_AUDIO` on first launch
- Show rationale if denied
- Handle permission denial gracefully

**Client Side**:
- Only requires `INTERNET` (granted by default)

### 9.3 Security Considerations

1. **Authentication** (Optional):
   - Implement password/PIN for server access
   - Client must send authentication token after connection
   - Server validates before accepting commands

2. **Encryption** (Recommended):
   - Use SSL/TLS for socket communication
   - Encrypt sensitive data

3. **Command Validation**:
   - Whitelist allowed commands
   - Sanitize input to prevent injection attacks

4. **Network Security**:
   - Use `network_security_config.xml`
   - Allow cleartext traffic only for local networks

5. **Rate Limiting**:
   - Limit command frequency to prevent abuse
   - e.g., Max 10 commands per second

---

## 10. ERROR HANDLING & RECOVERY

### 10.1 Connection Errors

| Error | Server Behavior | Client Behavior |
|-------|----------------|-----------------|
| Connection Timeout | Log warning, return to listening | Show error, enable retry |
| Connection Refused | N/A | Show "Server not found" |
| Connection Lost | Release camera, return to listening | Show dialog, offer reconnect |
| Invalid Command | Send ERROR response | Show error toast |

### 10.2 Camera Errors

| Error | Behavior |
|-------|----------|
| Camera Access Denied | Send ERROR:PERMISSION_DENIED to client |
| Camera In Use | Send ERROR:CAMERA_BUSY, retry after 2s |
| Storage Full | Send ERROR:STORAGE_FULL, stop recording |
| Capture Failed | Send ERROR:CAPTURE_FAILED, retry once |

### 10.3 Recovery Strategies

1. **Auto-Reconnect (Client)**:
   - Retry connection 3 times with exponential backoff
   - 2s, 4s, 8s delays

2. **Graceful Degradation**:
   - If video recording fails, fallback to photo mode
   - If camera flip fails, log and continue

3. **State Persistence**:
   - Save connection state in ViewModel
   - Restore on configuration change

---

## 11. TESTING STRATEGY

### 11.1 Unit Tests

- Socket connection/disconnection
- Command parsing
- Response generation
- State machine transitions

### 11.2 Integration Tests

- End-to-end command flow
- Camera operations
- Thread synchronization

### 11.3 UI Tests

- Role selection
- Server IP display
- Client connection flow
- Button enable/disable states

### 11.4 Manual Testing Scenarios

1. **Happy Path**:
   - Server starts → Client connects → Take photo → Video recording → Disconnect

2. **Error Scenarios**:
   - Wrong IP address
   - Server not running
   - Connection lost during recording
   - Rapid command spamming

3. **Edge Cases**:
   - Screen rotation during connection
   - App backgrounding while recording
   - Multiple connection attempts

---

## 12. FUTURE ENHANCEMENTS

1. **Image Streaming**:
   - Send live camera feed to client
   - Use H.264 encoding

2. **Multiple Clients**:
   - Support multiple simultaneous controllers
   - Implement command queuing

3. **Cloud Relay**:
   - Use Firebase or similar for internet connectivity
   - No local network requirement

4. **Advanced Controls**:
   - Zoom, flash, exposure
   - Face detection mode

5. **Gallery Sync**:
   - Auto-transfer captured media to client device

---

## 13. IMPLEMENTATION CHECKLIST

### Phase 1: Basic Structure
- [ ] Create RoleSelectionActivity
- [ ] Create ServerActivity with IP display
- [ ] Create ClientConnectionActivity
- [ ] Implement basic socket connection

### Phase 2: Server Implementation
- [ ] ServerSocket setup
- [ ] IP address fetching
- [ ] Accept connection logic
- [ ] Command listener thread
- [ ] Transition to camera screen

### Phase 3: Client Implementation
- [ ] Socket connection with timeout
- [ ] Connection status UI
- [ ] Control button layout
- [ ] Command sending logic

### Phase 4: Camera Integration
- [ ] CameraX initialization
- [ ] Photo capture
- [ ] Video recording
- [ ] Camera flip

### Phase 5: Protocol Implementation
- [ ] Define command constants
- [ ] Command parser
- [ ] Response generator
- [ ] Error handling

### Phase 6: UI/UX Polish
- [ ] Status indicators
- [ ] Loading animations
- [ ] Error messages
- [ ] Visual feedback

### Phase 7: Testing
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing
- [ ] Bug fixes

### Phase 8: Documentation
- [ ] User guide
- [ ] API documentation
- [ ] Setup instructions

---

## 14. RECOMMENDED LIBRARIES

```gradle
dependencies {
    // CameraX
    implementation "androidx.camera:camera-camera2:1.3.0"
    implementation "androidx.camera:camera-lifecycle:1.3.0"
    implementation "androidx.camera:camera-view:1.3.0"
    
    // Kotlin Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    
    // ViewModel & LiveData
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.6.2"
    
    // Network (optional, for better socket management)
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    
    // Logging
    implementation "com.jakewharton.timber:timber:5.0.1"
}
```

---

## CONCLUSION

This architecture provides a robust, scalable foundation for your dual-mode Android camera control app. Key strengths:

✅ **Clear Separation of Concerns**: Network, Camera, and UI layers are independent  
✅ **Thread Safety**: Proper concurrency management  
✅ **Error Handling**: Comprehensive error scenarios covered  
✅ **User Experience**: Smooth transitions and status feedback  
✅ **Extensibility**: Easy to add new commands or features  

The design prioritizes real-world feasibility while maintaining code quality and user experience. Each component can be implemented and tested independently, facilitating parallel development.

