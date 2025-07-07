# Sistema de Mensajes - ProyectAppMovil

## Funcionalidad Implementada

Se ha implementado un sistema completo de mensajes entre padre e hijo que permite la comunicación en tiempo real.

## Componentes del Sistema

### 1. Clase Message
```kotlin
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val senderRole: String = "" // "padre" o "hijo"
)
```

### 2. MessagesAdapter
- Adaptador para mostrar mensajes en RecyclerView
- Diferencia entre mensajes enviados y recibidos
- Alineación automática según el rol del usuario

### 3. MessagesFragment
- Fragmento reutilizable para mensajes
- Manejo de tiempo real con Firestore
- Determinación automática del rol del usuario

### 4. MessagesActivity
- Actividad dedicada para mensajes desde el menú del padre
- Carga el fragmento de mensajes

## Estructura de Firestore

### Colección: messages
```json
{
  "id": "auto_generated",
  "senderId": "user_id",
  "senderName": "Papá/Hijo",
  "receiverId": "target_user_id",
  "content": "Contenido del mensaje",
  "timestamp": "timestamp",
  "isRead": false,
  "senderRole": "padre/hijo",
  "chatId": "unique_chat_id"
}
```

## Funcionalidades

### Para el Padre:
- ✅ Acceso desde el menú lateral ("Mensajes")
- ✅ Envío de mensajes al hijo
- ✅ Visualización en tiempo real
- ✅ Interfaz completa de chat

### Para el Hijo:
- ✅ Visualización integrada en la pantalla principal
- ✅ Envío de respuestas al padre
- ✅ Actualización en tiempo real
- ✅ Área de chat compacta

## Características Técnicas

### Tiempo Real
- Usa Firestore SnapshotListener
- Actualización automática de mensajes
- Scroll automático al último mensaje

### UI/UX
- Mensajes enviados: Fondo azul, alineados a la derecha
- Mensajes recibidos: Fondo gris, alineados a la izquierda
- Timestamp en cada mensaje
- Nombre del remitente

### Manejo de Errores
- Verificación de conexión
- Mensajes de error para el usuario
- Logging para debugging

## Archivos Creados/Modificados

### Nuevos Archivos:
- `Message.kt` - Clase de datos para mensajes
- `MessagesAdapter.kt` - Adaptador para RecyclerView
- `MessagesFragment.kt` - Fragmento de mensajes
- `MessagesActivity.kt` - Actividad de mensajes
- `item_message.xml` - Layout para mensaje individual
- `fragment_messages.xml` - Layout del fragmento
- `activity_messages.xml` - Layout de la actividad
- `message_sent_background.xml` - Fondo para mensajes enviados
- `message_received_background.xml` - Fondo para mensajes recibidos

### Archivos Modificados:
- `activity_pantalla_hijo.xml` - Agregado contenedor de mensajes
- `PantallaHijoActivity.kt` - Carga del fragmento de mensajes
- `nav_menu.xml` - Agregada opción de mensajes
- `MenuTutorActivity.kt` - Manejo de navegación a mensajes

## Flujo de Funcionamiento

### 1. Padre Envía Mensaje:
1. Padre abre "Mensajes" desde el menú
2. Escribe mensaje y presiona enviar
3. Se guarda en Firestore con chatId único
4. Hijo recibe actualización en tiempo real

### 2. Hijo Recibe y Responde:
1. Hijo ve mensaje en su pantalla principal
2. Escribe respuesta en el área de chat
3. Presiona enviar
4. Padre recibe actualización en tiempo real

### 3. ChatId Único:
- Se crea automáticamente basado en los IDs de usuario
- Formato: "user1_user2" (ordenados alfabéticamente)
- Permite múltiples relaciones padre-hijo

## Beneficios

1. **Comunicación Directa**: Padre e hijo pueden comunicarse fácilmente
2. **Tiempo Real**: Mensajes instantáneos sin recargar
3. **Interfaz Intuitiva**: Diseño similar a apps de mensajería populares
4. **Escalable**: Fácil agregar más funcionalidades (emojis, archivos, etc.)
5. **Seguro**: Solo usuarios autorizados pueden comunicarse

## Próximos Pasos Sugeridos

1. **Notificaciones Push**: Alertas cuando llegan mensajes nuevos
2. **Emojis**: Soporte para emojis en los mensajes
3. **Archivos**: Envío de imágenes o documentos
4. **Estado de Lectura**: Indicador de mensajes leídos
5. **Historial**: Búsqueda y filtrado de mensajes antiguos
6. **Grupos**: Chat grupal para familias con múltiples hijos

## Consideraciones de Seguridad

- Solo usuarios autenticados pueden enviar mensajes
- Validación de relación padre-hijo antes de permitir comunicación
- Sanitización de contenido de mensajes
- Logs de actividad para auditoría 