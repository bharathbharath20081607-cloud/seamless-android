@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.seamless.android.identity.SecureIdentityStateManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MessageEntity::class, RelayMessageEntity::class, PendingNostrMessage::class,
        TorMessageEntity::class,
        PrivateGroupEntity::class, GroupMemberEntity::class, GroupMessageEntity::class,
        ForumEntity::class, ForumMessageEntity::class,
        SyncStateEntity::class,
        MailboxMessageEntity::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun relayDao(): RelayDao
    abstract fun pendingNostrMessageDao(): PendingNostrMessageDao
    abstract fun torMessageDao(): TorMessageDao
    abstract fun groupDao(): GroupDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun mailboxMessageDao(): MailboxMessageDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "seamless_database"
        private const val DB_KEY_PREF = "seamless_db_encryption_key"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN vectorClock TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create indices for better query performance
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_channel ON messages(channel)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_senderPeerID ON messages(senderPeerID)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_timestamp ON messages(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_isPrivate_senderPeerID ON messages(isPrivate, senderPeerID)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create pending_nostr_messages table for offline-first message reliability
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_nostr_messages (
                        messageID TEXT NOT NULL PRIMARY KEY,
                        recipientPeerID TEXT NOT NULL,
                        recipientNickname TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastAttemptTime INTEGER
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create tor_messages table for Tor P2P message persistence
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tor_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        contactId TEXT NOT NULL,
                        contactOnionAddress TEXT NOT NULL,
                        contactNickname TEXT NOT NULL,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isFromMe INTEGER NOT NULL,
                        deliveryState TEXT NOT NULL,
                        readAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tor_messages_contactOnionAddress ON tor_messages(contactOnionAddress)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tor_messages_timestamp ON tor_messages(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tor_messages_deliveryState ON tor_messages(deliveryState)")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Private Groups
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS private_groups (
                        groupId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        creatorOnionAddress TEXT NOT NULL,
                        creatorNickname TEXT NOT NULL,
                        isCreatedByMe INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_private_groups_createdAt ON private_groups(createdAt)")

                // Group Members
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        groupId TEXT NOT NULL,
                        memberOnionAddress TEXT NOT NULL,
                        memberNickname TEXT NOT NULL,
                        joinedAt INTEGER NOT NULL,
                        PRIMARY KEY(groupId, memberOnionAddress)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_groupId ON group_members(groupId)")

                // Group Messages
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        groupId TEXT NOT NULL,
                        senderOnionAddress TEXT NOT NULL,
                        senderNickname TEXT NOT NULL,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isFromMe INTEGER NOT NULL,
                        deliveryState TEXT NOT NULL DEFAULT 'SENT'
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_group_messages_groupId ON group_messages(groupId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_group_messages_timestamp ON group_messages(timestamp)")

                // Forums
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS forums (
                        forumId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        creatorOnionAddress TEXT NOT NULL,
                        creatorNickname TEXT NOT NULL,
                        isCreatedByMe INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_forums_createdAt ON forums(createdAt)")

                // Forum Messages
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS forum_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        forumId TEXT NOT NULL,
                        parentMessageId TEXT,
                        senderOnionAddress TEXT NOT NULL,
                        senderNickname TEXT NOT NULL,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isFromMe INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_forum_messages_forumId ON forum_messages(forumId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_forum_messages_parentMessageId ON forum_messages(parentMessageId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_forum_messages_timestamp ON forum_messages(timestamp)")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_states (
                        contactId TEXT NOT NULL PRIMARY KEY,
                        lastSyncedTimestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Persistent Mailbox storage — messages survive app restart
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mailbox_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        recipientId TEXT NOT NULL,
                        senderOnion TEXT NOT NULL,
                        encryptedPayload BLOB NOT NULL,
                        timestamp INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mailbox_messages_recipientId ON mailbox_messages(recipientId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mailbox_messages_timestamp ON mailbox_messages(timestamp)")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add delivery route tracking to tor_messages
                database.execSQL("ALTER TABLE tor_messages ADD COLUMN deliveryRoute TEXT NOT NULL DEFAULT 'DIRECT'")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_nostr_messages_createdAt ON pending_nostr_messages(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_relay_messages_recipientId_timestamp ON relay_messages(recipientId, timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_states_lastSyncedTimestamp ON sync_states(lastSyncedTimestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_channel_timestamp ON messages(channel, timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tor_messages_isFromMe_readAt_contactOnionAddress ON tor_messages(isFromMe, readAt, contactOnionAddress)")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tor_messages ADD COLUMN attachmentPath TEXT")
                database.execSQL("ALTER TABLE tor_messages ADD COLUMN attachmentMimeType TEXT")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE group_messages ADD COLUMN attachmentPath TEXT")
                database.execSQL("ALTER TABLE group_messages ADD COLUMN attachmentMimeType TEXT")
            }
        }

        /**
         * Get or create a 256-bit database encryption key stored in EncryptedSharedPreferences.
         */
        private fun getOrCreateDbKey(context: Context): ByteArray {
            val stateManager = SecureIdentityStateManager(context)
            val existingKey = stateManager.getSecureValue(DB_KEY_PREF)
            if (existingKey != null) {
                return android.util.Base64.decode(existingKey, android.util.Base64.NO_WRAP)
            }

            // Generate new 256-bit key
            val key = ByteArray(32)
            java.security.SecureRandom().nextBytes(key)
            stateManager.storeSecureValue(
                DB_KEY_PREF,
                android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
            )
            return key
        }

        /**
         * If an old unencrypted database exists, migrate it to encrypted format.
         * This runs before Room opens the database.
         */
        private fun migrateUnencryptedIfNeeded(context: Context, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return // Fresh install — nothing to migrate

            // Quick probe: try opening with empty key — if it succeeds, DB is unencrypted
            try {
                val testDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                testDb.close()
            } catch (_: Exception) {
                // Not a plain SQLite file — already encrypted or corrupt, skip migration
                return
            }

            // Database IS unencrypted — encrypt it with sqlcipher_export
            Log.d(TAG, "Migrating unencrypted database to SQLCipher...")
            val encryptedFile = context.getDatabasePath("${DB_NAME}_encrypted")
            try {
                // Load SQLCipher native libs
                System.loadLibrary("sqlcipher")

                val sourceDb = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, "", null,
                    net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE, null, null
                )
                val passphraseStr = passphrase.joinToString("") { "%02x".format(it) }
                sourceDb.rawExecSQL("ATTACH DATABASE '${encryptedFile.absolutePath}' AS encrypted KEY x'$passphraseStr'")
                sourceDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                sourceDb.rawExecSQL("DETACH DATABASE encrypted")
                sourceDb.close()

                // Swap files
                dbFile.delete()
                encryptedFile.renameTo(dbFile)
                Log.d(TAG, "Database migration to SQLCipher complete")
            } catch (e: Exception) {
                Log.e(TAG, "Database encryption migration failed: ${e.message}")
                // Clean up partial encrypted file
                encryptedFile.delete()
                // Leave unencrypted DB in place — Room will still open it
                // On next app start it will retry
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Load SQLCipher native library BEFORE any database operations
                System.loadLibrary("sqlcipher")

                val passphrase = getOrCreateDbKey(context)

                // Migrate existing unencrypted database if needed
                migrateUnencryptedIfNeeded(context, passphrase)

                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
