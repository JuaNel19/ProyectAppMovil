# Sistema de Mensajes Actualizado - ProyectAppMovil

## Funcionalidad Implementada

Se ha implementado un sistema de mensajes donde el **padre puede enviar mensajes desde la pantalla principal** y el **hijo puede verlos en su pantalla**.

## Flujo de Funcionamiento

### Para el Padre:
1. **Pantalla Principal**: En la pantalla de tiempo de uso
2. **Seleccionar Hijo**: Elegir el hijo desde el dropdown
3. **Escribir Mensaje**: En el EditText "Escribe un mensaje para tu hijo..."
4. **Enviar**: Presionar el botón "Enviar"
5. **Ver Historial**: Los mensajes recientes aparecen en la lista inferior

### Para el Hijo:
1. **Pantalla Principal**: En la parte inferior de su pantalla
2. **Ver Mensajes**: Los mensajes del padre aparecen automáticamente
3. **Responder**: Puede escribir y enviar respuestas
4. **Tiempo Real**: Actualización automática de mensajes

## Componentes del Sistema

### 1. Clase Message (Actualizada)
```kotlin
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val senderRole: String = "", // "padre" o "hijo"
    val chatId: String = ""
)
```

### 2. TiempoUsoFragment (Padre)
- **EditText**: Para escribir mensajes
- **Button**: Para enviar mensajes
- **RecyclerView**: Para mostrar mensajes recientes
- **Validaciones**: Verifica que se seleccione un hijo

### 3. PantallaHijoActivity (Hijo)
- **MessagesFragment**: Integrado en la pantalla principal
- **Área de Chat**: Compacta en la parte inferior
- **Tiempo Real**: Actualización automática

## Estructura de Firestore

### Colección: messages
```json
{
  "id": "auto_generated",
  "senderId": "parent_user_id",
  "senderName": "Papá",
  "receiverId": "child_user_id",
  "content": "¡Hola hijo! ¿Cómo estás?",
  "timestamp": "timestamp",
  "isRead": false,
  "senderRole": "padre",
  "chatId": "parent_id_child_id"
}
```

## Características Técnicas

### UI/UX
- **Padre**: Interfaz integrada en la pantalla de tiempo de uso
- **Hijo**: Chat compacto en la parte inferior de su pantalla
- **Mensajes Enviados**: Fondo azul, alineados a la derecha
- **Mensajes Recibidos**: Fondo gris, alineados a la izquierda

### Tiempo Real
- Firestore SnapshotListener para actualizaciones automáticas
- Scroll automático al último mensaje
- ChatId único para cada par padre-hijo

### Validaciones
- Verificación de hijo seleccionado
- Validación de mensaje no vacío
- Manejo de errores de conexión

## Archivos Modificados

### Nuevos Archivos:
- `Message.kt` - Clase de datos con chatId
- `MessagesAdapter.kt` - Adaptador para RecyclerView
- `MessagesFragment.kt` - Fragmento para el hijo
- `item_message.xml` - Layout para mensaje individual
- `fragment_messages.xml` - Layout del fragmento
- `message_sent_background.xml` - Fondo para mensajes enviados
- `message_received_background.xml` - Fondo para mensajes recibidos

### Archivos Modificados:
- `fragment_tiempo_uso.xml` - Agregado área de mensajes
- `TiempoUsoFragment.kt` - Funcionalidad de envío de mensajes
- `activity_pantalla_hijo.xml` - Contenedor de mensajes
- `PantallaHijoActivity.kt` - Carga del fragmento de mensajes

## Funcionalidades Implementadas

### ✅ Padre:
- Envío de mensajes desde pantalla principal
- Selección de hijo antes de enviar
- Visualización de mensajes recientes
- Validaciones de entrada

### ✅ Hijo:
- Visualización de mensajes en tiempo real
- Respuesta a mensajes del padre
- Interfaz integrada en pantalla principal
- Actualización automática

## Beneficios

1. **Comunicación Directa**: Padre e hijo pueden comunicarse fácilmente
2. **Interfaz Intuitiva**: Integrada en las pantallas principales
3. **Tiempo Real**: Mensajes instantáneos
4. **Validaciones**: Previene errores de usuario
5. **Escalable**: Fácil agregar más funcionalidades

## Próximos Pasos Sugeridos

1. **Notificaciones Push**: Alertas cuando llegan mensajes nuevos
2. **Emojis**: Soporte para emojis en los mensajes
3. **Archivos**: Envío de imágenes o documentos
4. **Estado de Lectura**: Indicador de mensajes leídos
5. **Historial Completo**: Ver todos los mensajes antiguos
6. **Múltiples Hijos**: Chat grupal para familias

## Consideraciones de Seguridad

- Solo usuarios autenticados pueden enviar mensajes
- Validación de relación padre-hijo
- Sanitización de contenido de mensajes
- Logs de actividad para auditoría 