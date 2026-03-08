import admin from 'firebase-admin'

const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON
if (raw) {
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) })
}

export default admin
