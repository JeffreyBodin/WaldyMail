package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {
  @Query("SELECT * FROM emails ORDER BY sentAtMillis DESC")
  fun observeAllEmails(): Flow<List<EmailEntity>>

  @Query("SELECT * FROM emails WHERE id = :id LIMIT 1")
  suspend fun getEmailById(id: String): EmailEntity?

  @Query("SELECT COUNT(*) FROM emails")
  suspend fun countEmails(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEmail(email: EmailEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEmails(emails: List<EmailEntity>)

  @Query("UPDATE emails SET read = :read, updatedAtMillis = :updatedAtMillis WHERE id = :id")
  suspend fun updateRead(id: String, read: Boolean, updatedAtMillis: Long)

  @Query("UPDATE emails SET starred = :starred, updatedAtMillis = :updatedAtMillis WHERE id = :id")
  suspend fun updateStarred(id: String, starred: Boolean, updatedAtMillis: Long)

  @Query("UPDATE emails SET folder = :folder, updatedAtMillis = :updatedAtMillis WHERE id = :id")
  suspend fun updateFolder(id: String, folder: String, updatedAtMillis: Long)

  @Query("DELETE FROM emails WHERE id = :id")
  suspend fun deleteEmailById(id: String)

  @Query("SELECT * FROM contacts ORDER BY LOWER(name) ASC")
  fun observeAllContacts(): Flow<List<ContactEntity>>

  @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
  suspend fun getContactById(id: String): ContactEntity?

  @Query("SELECT COUNT(*) FROM contacts")
  suspend fun countContacts(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertContact(contact: ContactEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertContacts(contacts: List<ContactEntity>)

  @Query(
    "UPDATE contacts SET name = :name, email = :email, updatedAtMillis = :updatedAtMillis WHERE id = :id"
  )
  suspend fun updateContact(id: String, name: String, email: String, updatedAtMillis: Long)

  @Query("DELETE FROM contacts WHERE id = :id")
  suspend fun deleteContactById(id: String)
}
