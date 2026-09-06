package com.tracksnatcher.app

/**
 * The signed-in user's identity for session/snatch credits. In production this is derived
 * from the linked streaming account; the scaffold uses a fixed local identity.
 */
object LocalIdentity {
    const val ID = "me"
    const val NAME = "You"
}
