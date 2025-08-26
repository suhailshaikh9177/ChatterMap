from firebase_functions import firestore_fn
from firebase_admin import initialize_app, firestore, messaging

initialize_app()

@firestore_fn.on_document_created("chats/{chatId}/messages/{messageId}")
def send_notification_on_new_message(event: firestore_fn.Event[firestore_fn.Change]) -> None:
    """Triggers when a new message is created and sends a notification."""
    
    if event.data is None:
        print("No data associated with the event.")
        return

    message_data = event.data.to_dict()
    receiver_id = message_data.get("receiverId")
    sender_id = message_data.get("senderId")
    message_text = message_data.get("text")

    if not receiver_id or not sender_id:
        print(f"Missing senderId or receiverId in message.")
        return

    db = firestore.client()

    try:
        sender_doc = db.collection("users").document(sender_id).get()
        sender_name = sender_doc.to_dict().get("username", "Someone")

        receiver_doc = db.collection("users").document(receiver_id).get()
        fcm_token = receiver_doc.to_dict().get("fcmToken")

        if not fcm_token:
            print(f"FCM token not found for receiverId: {receiver_id}")
            return

        notification_payload = messaging.Message(
            notification=messaging.Notification(
                title=f"New message from {sender_name}",
                body=message_text,
            ),
            token=fcm_token,
        )

        messaging.send(notification_payload)
        print(f"Successfully sent notification to user: {receiver_id}")

    except Exception as e:
        print(f"Error sending notification: {e}")