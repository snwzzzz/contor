-- A thread is the messages of one conversation in one mailbox, oldest first; the inbox list also needs per-conversation
-- stats (message count, last reply) for a page of emails, and the reply drafts check for newer messages. All of them
-- look up (account_id, conversation_id) and read received_at.
create index ix_email_message_account_conversation on "email_message" (account_id, conversation_id, received_at);
