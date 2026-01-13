package com.orienteerfeed.ofeed_sidroid_connector;

import java.io.Serializable;
import java.util.HashMap;

/**
 * This class holds XML ids, and some event data.
 * Its content is saved to file by {@link SerializableManager}.
 */
public class XmlIds implements Serializable {
    private static final long serialVersionUID = 1966833320334041092L;

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    /**
     * Current event data.
     */
    private String eventName, eventDateTime;

    /**
     * Map of person to XML id.
     * Key = hashed value of person's name and SI card.
     * Value = XML id for this person.
     */
    private HashMap<String, Integer> personHash2Id;

    /**
     * The latest assigned XML id, or zero if no id has been assigned yet.
     */
    private int latestAssignedXmlId;

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    /**
     * Clear current event and initialize a new fresh event.
     */
    void setCurrentEvent(String eventName, String eventDateTime) {
        this.eventName = eventName;
        this.eventDateTime = eventDateTime;
        personHash2Id = new HashMap<>();
        latestAssignedXmlId = 0;
    }

    /**
     * Check if the given event name and date/time match the current event data.
     */
    boolean isCurrentEvent(String name, String dateTime) {
        if (eventName == null || eventDateTime == null) return false;
        return name.equals(eventName) && dateTime.equals(eventDateTime);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isEmpty() {
        return personHash2Id == null || personHash2Id.isEmpty();
    }

    /**
     * Get XML id for the given hash. If the hash does not exist, a new entry is created
     * and its id is returned.
     */
    String getXmlId(String hash) {
        Integer id = personHash2Id.get(hash);
        if (id != null) {
            return String.valueOf(id);
        } else {
            latestAssignedXmlId++;
            personHash2Id.put(hash, latestAssignedXmlId);
            return String.valueOf(latestAssignedXmlId);
        }
    }
}
