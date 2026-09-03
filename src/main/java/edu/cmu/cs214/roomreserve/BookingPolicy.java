package edu.cmu.cs214.roomreserve;

import java.util.List;

/**
 * The booking rules: business hours, maximum length, and no overlapping bookings.
 */
public class BookingPolicy {

    private static final long OPENING_MINUTE = 8 * 60;
    private static final long CLOSING_MINUTE = 20 * 60;
    private static final long MAX_LENGTH_MINUTES = 4 * 60;

    /**
     * Checks a requested interval against the rules and the bookings a room already has
     * that day. Returns null when the request is acceptable, otherwise a message naming
     * the first rule it breaks.
     */
    public String validate(long startMinute, long endMinute, List<long[]> existingSlots) {
        if (endMinute <= startMinute) {
            return "end must be after start";
        }
        if (startMinute < OPENING_MINUTE || endMinute > CLOSING_MINUTE) {
            return "bookings must fall between 08:00 and 20:00";
        }
        if (endMinute - startMinute > MAX_LENGTH_MINUTES) {
            return "bookings may not run longer than 4 hours";
        }
        for (long[] slot : existingSlots) {
            if (overlaps(startMinute, endMinute, slot[0], slot[1])) {
                return "requested time overlaps a booking that already exists";
            }
        }
        return null;
    }

    public boolean isWithinBusinessHours(long startMinute, long endMinute) {
        return startMinute >= OPENING_MINUTE && endMinute <= CLOSING_MINUTE;
    }

    public boolean isWithinMaxLength(long startMinute, long endMinute) {
        return endMinute - startMinute <= MAX_LENGTH_MINUTES;
    }

    private boolean overlaps(long startA, long endA, long startB, long endB) {
        return startA < endB && startB < endA;
    }
}
