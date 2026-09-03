package edu.cmu.cs214.roomreserve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps bookings in memory for the life of the process.
 */
public class InMemoryStore {

    private final Map<String, List<long[]>> slotsByRoomDate = new HashMap<>();
    private final Map<String, String> bookerBySlot = new HashMap<>();

    public boolean addSlot(String roomId, String date, long start, long end, String user) {
        String key = roomId + "|" + date;
        List<long[]> slots = slotsByRoomDate.get(key);
        if (slots == null) {
            slots = new ArrayList<>();
            slotsByRoomDate.put(key, slots);
        }
        for (long[] slot : slots) {
            if (slot[0] == start && slot[1] == end) {
                return false;
            }
        }
        slots.add(new long[] { start, end });
        bookerBySlot.put(roomId + "|" + date + "|" + start + "|" + end, user);
        return true;
    }

    public boolean removeSlot(String roomId, String date, long start, long end) {
        List<long[]> slots = slotsByRoomDate.get(roomId + "|" + date);
        if (slots == null) {
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            long[] slot = slots.get(i);
            if (slot[0] == start && slot[1] == end) {
                slots.remove(i);
                bookerBySlot.remove(roomId + "|" + date + "|" + start + "|" + end);
                return true;
            }
        }
        return false;
    }

    public List<long[]> slotsFor(String roomId, String date) {
        List<long[]> slots = slotsByRoomDate.get(roomId + "|" + date);
        if (slots == null) {
            return new ArrayList<>();
        }
        return slots;
    }

    public String bookerFor(String roomId, String date, long start, long end) {
        return bookerBySlot.get(roomId + "|" + date + "|" + start + "|" + end);
    }

    public boolean hasSlot(String roomId, String date, long start, long end) {
        List<long[]> slots = slotsByRoomDate.get(roomId + "|" + date);
        if (slots == null) {
            return false;
        }
        for (long[] slot : slots) {
            if (slot[0] == start && slot[1] == end) {
                return true;
            }
        }
        return false;
    }

    public int bookingCount() {
        int count = 0;
        for (List<long[]> slots : slotsByRoomDate.values()) {
            count += slots.size();
        }
        return count;
    }
}
