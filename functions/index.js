const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendNotificationOnMessage = functions.firestore
    .document("messages/{userId}/inbox/{messageId}")
    .onCreate(async (snapshot, context) => {

        try {

            const messageData = snapshot.data();

            const userId = context.params.userId;

            // Get employee document
            const userDoc = await admin.firestore()
                .collection("users")
                .document(userId)
                .get();

            if (!userDoc.exists) {

                console.log("User not found");

                return null;
            }

            const token = userDoc.data().fcmToken;

            if (!token) {

                console.log("FCM token missing");

                return null;
            }

            // Notification Payload
            const payload = {

                notification: {
                    title: "📩 New Message",
                    body: messageData.text || "You received a new message"
                },

                data: {
                    title: "📩 New Message",
                    body: messageData.text || ""
                }
            };

            // Send Notification
            await admin.messaging().sendToDevice(
                token,
                payload
            );

            console.log("Notification Sent Successfully");

            return null;

        } catch (error) {

            console.log("ERROR:", error);

            return null;
        }
    });