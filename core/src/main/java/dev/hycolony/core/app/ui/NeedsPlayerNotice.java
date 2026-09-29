package dev.hycolony.core.app.ui;

import dev.hycolony.core.request.model.Deliverable;

/**
 * A request only a player can provide, announced in chat: "{requester} ({job}) needs: {requestable}".
 * {@code requesterName} is the citizen's name, or the building's display name for the building's own requests;
 * {@code jobId} is empty without a job. The UI names the job and the requestable in the player's language.
 */
public record NeedsPlayerNotice(String requesterName, String jobId, Deliverable requestable) {}
