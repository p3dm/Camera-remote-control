# Camera Relay Server

A WebSocket-based relay server that enables remote camera control over the internet. This server allows the Camera Remote Control Android app to work globally without being on the same local network.

## Overview

The relay server uses a PIN-based room system to pair camera and controller devices. It routes messages between a camera phone (SERVER) and a controller phone (CLIENT) through WebSocket connections.

## Features

- **WebSocket Relay**: Real-time message routing between devices
- **PIN-Based Rooms**: Secure 4-digit PIN system for device pairing
- **Device Types**: Support for SERVER (camera phone) and CLIENT (controller phone)
- **Auto Cleanup**: Automatic room cleanup on disconnect
- **Health Monitoring**: Built-in health check endpoint
- **Cloud Ready**: Configurable port via environment variable

## Development

### Prerequisites

- Java 17 or higher
- Gradle 8.5 or higher (or use included Gradle wrapper)

### Running Locally

```bash
# Using Gradle wrapper (recommended)
./gradlew run

# Or using installed Gradle
gradle run
```

The server will start on port 8080 by default. You can override this with the `PORT` environment variable:

```bash
PORT=3000 ./gradlew run
```

### Building

Build a standalone JAR file:

```bash
./gradlew shadowJar
```

The JAR will be created at `build/libs/camera-relay-server.jar`

Run the JAR:

```bash
java -jar build/libs/camera-relay-server.jar
```

## Deployment

The relay server can be deployed to various cloud platforms:

### Railway.app (Recommended)

1. Create a new project on [Railway.app](https://railway.app)
2. Connect your GitHub repository
3. Railway will automatically detect the Gradle project
4. Set build command: `cd relay-server && ./gradlew shadowJar`
5. Set start command: `cd relay-server && java -jar build/libs/camera-relay-server.jar`
6. Deploy!

Railway will automatically set the `PORT` environment variable.

### Render.com

1. Create a new Web Service on [Render.com](https://render.com)
2. Connect your GitHub repository
3. Set the following:
   - **Build Command**: `cd relay-server && ./gradlew shadowJar`
   - **Start Command**: `cd relay-server && java -jar build/libs/camera-relay-server.jar`
4. Deploy!

### Heroku

1. Install [Heroku CLI](https://devcenter.heroku.com/articles/heroku-cli)
2. Create a new app:
   ```bash
   heroku create your-app-name
   ```
3. Deploy:
   ```bash
   git subtree push --prefix relay-server heroku main
   ```

The `Procfile` is already configured for Heroku deployment.

### Docker / Google Cloud Run

Build the Docker image:

```bash
cd relay-server
docker build -t camera-relay-server .
```

Run locally with Docker:

```bash
docker run -p 8080:8080 camera-relay-server
```

Deploy to Google Cloud Run:

```bash
gcloud run deploy camera-relay-server \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

## Environment Variables

- `PORT` - Server port (default: 8080)

## API Documentation

### Endpoints

#### WebSocket Endpoint
- **URL**: `ws://your-server/camera-relay`
- **Protocol**: WebSocket

#### Health Check
- **URL**: `GET /health`
- **Response**: `OK`

#### Status Page
- **URL**: `GET /`
- **Response**: Server status and active room statistics

### WebSocket Messages

All messages are JSON formatted.

#### Message Types

**CREATE_ROOM / JOIN_ROOM**
```json
{
  "type": "CREATE_ROOM",
  "pin": "1234",
  "deviceType": "SERVER"
}
```
- `pin`: 4-digit room PIN
- `deviceType`: Either "SERVER" (camera) or "CLIENT" (controller)

**COMMAND**
```json
{
  "type": "COMMAND",
  "command": "TAKE_PHOTO"
}
```
Sent from CLIENT to SERVER. Supported commands:
- `TAKE_PHOTO`
- `START_RECORDING`
- `STOP_RECORDING`
- `SWITCH_CAMERA`

**RESPONSE**
```json
{
  "type": "RESPONSE",
  "message": "Photo taken successfully"
}
```
Sent from SERVER to CLIENT.

**Server Responses:**
- `ROOM_CREATED`: Confirms room creation
- `CONNECTED`: Confirms client joined room
- `CLIENT_CONNECTED`: Notifies server that client joined
- `CLIENT_DISCONNECTED`: Notifies when client leaves
- `SERVER_DISCONNECTED`: Notifies when server leaves
- `ERROR`: Error message

### Example Flow

1. **Server (Camera Phone) creates room:**
   ```json
   Send: {"type": "CREATE_ROOM", "pin": "1234", "deviceType": "SERVER"}
   Receive: {"type": "ROOM_CREATED", "pin": "1234"}
   ```

2. **Client (Controller Phone) joins room:**
   ```json
   Send: {"type": "JOIN_ROOM", "pin": "1234", "deviceType": "CLIENT"}
   Receive: {"type": "CONNECTED", "pin": "1234"}
   ```
   
   Server also receives: `{"type": "CLIENT_CONNECTED"}`

3. **Client sends command:**
   ```json
   Send: {"type": "COMMAND", "command": "TAKE_PHOTO"}
   ```
   
   Server receives: `{"type": "COMMAND", "command": "TAKE_PHOTO"}`

4. **Server sends response:**
   ```json
   Send: {"type": "RESPONSE", "message": "Photo taken"}
   ```
   
   Client receives: `{"type": "RESPONSE", "message": "Photo taken"}`

## Testing

### Using wscat

Install wscat:
```bash
npm install -g wscat
```

Test the server:
```bash
# Connect to server
wscat -c ws://localhost:8080/camera-relay

# Send a message (after connection)
{"type": "CREATE_ROOM", "pin": "1234", "deviceType": "SERVER"}
```

### Using curl

Check health:
```bash
curl http://localhost:8080/health
```

Check status:
```bash
curl http://localhost:8080/
```

## Architecture

- **RoomManager**: Manages rooms and session mappings (thread-safe)
- **CameraSession**: Represents a WebSocket connection with device type
- **Message**: Serializable data class for all message types
- **WebSocket Handler**: Handles connection lifecycle and message routing

## Logging

The server uses Logback for logging. Logs include:
- Connection events
- Room creation/join events
- Message routing
- Disconnections
- Errors

Log format: `HH:mm:ss.SSS [thread] LEVEL logger - message`

## License

Same as the main Camera Remote Control project.
