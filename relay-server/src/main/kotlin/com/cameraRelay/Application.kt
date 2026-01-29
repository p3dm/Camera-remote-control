package com.cameraRelay

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("CameraRelay")

@Serializable
data class Message(
    val type: String,
    val pin: String? = null,
    val deviceType: String? = null,
    val command: String? = null,
    val message: String? = null
)

data class Room(
    var server: CameraSession? = null,
    var client: CameraSession? = null
)

data class CameraSession(
    val session: DefaultWebSocketSession,
    val deviceType: String
)

object RoomManager {
    private val rooms = ConcurrentHashMap<String, Room>()
    private val sessionToRoom = ConcurrentHashMap<DefaultWebSocketSession, String>()
    private val sessionToDeviceType = ConcurrentHashMap<DefaultWebSocketSession, String>()

    suspend fun createOrJoinRoom(session: DefaultWebSocketSession, pin: String, deviceType: String): String {
        val room = rooms.getOrPut(pin) { Room() }
        
        return when (deviceType) {
            "SERVER" -> {
                if (room.server != null) {
                    "ERROR: Room already has a server"
                } else {
                    room.server = CameraSession(session, deviceType)
                    sessionToRoom[session] = pin
                    sessionToDeviceType[session] = deviceType
                    logger.info("Server created/joined room: $pin")
                    
                    // Notify server that room is created
                    val response = Message(type = "ROOM_CREATED", pin = pin)
                    session.send(Frame.Text(Json.encodeToString(response)))
                    
                    // If client already in room, notify server
                    if (room.client != null) {
                        val clientConnected = Message(type = "CLIENT_CONNECTED")
                        session.send(Frame.Text(Json.encodeToString(clientConnected)))
                    }
                    "SUCCESS"
                }
            }
            "CLIENT" -> {
                if (room.client != null) {
                    "ERROR: Room already has a client"
                } else if (room.server == null) {
                    "ERROR: No server in room. Server must create room first."
                } else {
                    room.client = CameraSession(session, deviceType)
                    sessionToRoom[session] = pin
                    sessionToDeviceType[session] = deviceType
                    logger.info("Client joined room: $pin")
                    
                    // Notify client they're connected
                    val response = Message(type = "CONNECTED", pin = pin)
                    session.send(Frame.Text(Json.encodeToString(response)))
                    
                    // Notify server that client has connected
                    room.server?.session?.send(Frame.Text(Json.encodeToString(Message(type = "CLIENT_CONNECTED"))))
                    "SUCCESS"
                }
            }
            else -> "ERROR: Invalid device type. Use SERVER or CLIENT"
        }
    }

    suspend fun sendCommand(session: DefaultWebSocketSession, message: Message) {
        val pin = sessionToRoom[session] ?: run {
            logger.warn("Session not in any room")
            return
        }
        
        val room = rooms[pin] ?: run {
            logger.warn("Room $pin not found")
            return
        }
        
        val deviceType = sessionToDeviceType[session]
        
        when (deviceType) {
            "CLIENT" -> {
                // Client sending command to server
                room.server?.session?.send(Frame.Text(Json.encodeToString(message)))
                logger.info("Command sent from client to server in room $pin: ${message.command}")
            }
            "SERVER" -> {
                // Server sending response to client
                room.client?.session?.send(Frame.Text(Json.encodeToString(message)))
                logger.info("Response sent from server to client in room $pin")
            }
        }
    }

    suspend fun removeSession(session: DefaultWebSocketSession) {
        val pin = sessionToRoom.remove(session) ?: return
        val deviceType = sessionToDeviceType.remove(session)
        
        val room = rooms[pin] ?: return
        
        when (deviceType) {
            "SERVER" -> {
                room.server = null
                // Notify client that server disconnected
                room.client?.session?.send(Frame.Text(Json.encodeToString(Message(type = "SERVER_DISCONNECTED"))))
                logger.info("Server disconnected from room $pin")
            }
            "CLIENT" -> {
                room.client = null
                // Notify server that client disconnected
                room.server?.session?.send(Frame.Text(Json.encodeToString(Message(type = "CLIENT_DISCONNECTED"))))
                logger.info("Client disconnected from room $pin")
            }
        }
        
        // Clean up empty rooms
        if (room.server == null && room.client == null) {
            rooms.remove(pin)
            logger.info("Room $pin removed (empty)")
        }
    }

    fun getRoomStats(): Map<String, Any> {
        return mapOf(
            "totalRooms" to rooms.size,
            "activeConnections" to sessionToRoom.size,
            "rooms" to rooms.mapValues { (_, room) ->
                mapOf(
                    "hasServer" to (room.server != null),
                    "hasClient" to (room.client != null)
                )
            }
        )
    }
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            val stats = RoomManager.getRoomStats()
            call.respondText(
                """
                Camera Relay Server
                ===================
                Status: Running
                Active Rooms: ${stats["totalRooms"]}
                Active Connections: ${stats["activeConnections"]}
                
                WebSocket Endpoint: /camera-relay
                Health Check: /health
                """.trimIndent()
            )
        }

        get("/health") {
            call.respondText("OK")
        }

        webSocket("/camera-relay") {
            logger.info("New WebSocket connection established")
            
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        logger.debug("Received message: $text")
                        
                        try {
                            val message = Json.decodeFromString<Message>(text)
                            
                            when (message.type) {
                                "CREATE_ROOM", "JOIN_ROOM" -> {
                                    val pin = message.pin
                                    val deviceType = message.deviceType
                                    
                                    if (pin == null || deviceType == null) {
                                        send(Frame.Text(Json.encodeToString(
                                            Message(type = "ERROR", message = "PIN and deviceType are required")
                                        )))
                                        continue
                                    }
                                    
                                    if (!pin.matches(Regex("\\d{4}"))) {
                                        send(Frame.Text(Json.encodeToString(
                                            Message(type = "ERROR", message = "PIN must be 4 digits")
                                        )))
                                        continue
                                    }
                                    
                                    val result = RoomManager.createOrJoinRoom(this, pin, deviceType)
                                    if (result.startsWith("ERROR")) {
                                        send(Frame.Text(Json.encodeToString(
                                            Message(type = "ERROR", message = result)
                                        )))
                                    }
                                }
                                
                                "COMMAND", "RESPONSE" -> {
                                    RoomManager.sendCommand(this, message)
                                }
                                
                                else -> {
                                    logger.warn("Unknown message type: ${message.type}")
                                    send(Frame.Text(Json.encodeToString(
                                        Message(type = "ERROR", message = "Unknown message type: ${message.type}")
                                    )))
                                }
                            }
                        } catch (e: SerializationException) {
                            logger.error("Failed to parse message: $text", e)
                            send(Frame.Text(Json.encodeToString(
                                Message(type = "ERROR", message = "Invalid JSON format")
                            )))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("WebSocket error", e)
            } finally {
                RoomManager.removeSession(this)
                logger.info("WebSocket connection closed")
            }
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    logger.info("Starting Camera Relay Server on port $port")
    
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}
